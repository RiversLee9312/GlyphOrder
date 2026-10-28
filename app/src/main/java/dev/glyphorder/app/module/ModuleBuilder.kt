package dev.glyphorder.app.module

import dev.glyphorder.app.R
import dev.glyphorder.app.UiError
import android.content.Context
import android.os.Build
import dev.glyphorder.app.font.FontInfo
import dev.glyphorder.app.font.FontPlan
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile
import dev.glyphorder.app.BuildConfig

data class BuiltModule(val file: File, val hash: String, val build: SystemBuild, val configHashes: Map<String, String>, val maxFamilies: Int = 0)

class ModuleBuilder(private val context: Context) {
    fun build(snapshot: DeviceSnapshot, requested: FontPlan, fonts: List<FontInfo>, directory: File): BuiltModule {
        val plan = dev.glyphorder.app.font.FontSelection.forDevice(requested, snapshot.targets)
        require(dev.glyphorder.app.font.FontStyles.configured(plan)) { UiError.key("error_select_font") }
        require(snapshot.conflicts.isEmpty()) { UiError.key("error_conflicting_modules", snapshot.conflicts.joinToString()) }
        if (plan.primary != null || plan.styleMode) require(plan.targets.isNotEmpty() && snapshot.targets.containsAll(plan.targets)) { UiError.key("error_select_existing_family") }
        val ids = ((if (plan.styleMode) plan.styleFonts.values else listOfNotNull(plan.primary)) + plan.fallbacks.values + plan.universal).toSet()
        ids.forEach { id -> require(fonts.any { it.id == id }) { UiError.key("error_selected_font_removed") } }
        val updated = snapshot.configs.mapValues { (path, xml) ->
            FontConfig.patch(xml, path.endsWith("font_fallback.xml"), plan, fonts, path.startsWith("/product/"), snapshot.fontCoverage, snapshot.targets.toSet())
        }
        val maxFamilies = ModuleSafety.validate(updated)
        val referenced = updated.values.flatMap { FontConfig.files(it) }.toSet()
        val selected = dev.glyphorder.app.font.FontSelection.selected(plan, fonts).filter { "GlyphOrder-${it.fileName}" in referenced }
        require(selected.sumOf { it.bytes } <= 384L * 1024 * 1024) { UiError.key("error_total_fonts_large") }
        val output = File(context.filesDir, "exports").apply { mkdirs() }
        val zip = File(output, "glyphorder-${System.currentTimeMillis()}.zip")
        try {
            ZipOutputStream(zip.outputStream().buffered()).use { stream ->
                fun text(path: String, content: String) {
                    stream.putNextEntry(ZipEntry(path)); stream.write(content.toByteArray()); stream.closeEntry()
                }
                text("module.prop", "id=$MODULE_ID\nname=${context.getString(R.string.module_display_name)}\nversion=${BuildConfig.VERSION_NAME}\nversionCode=${BuildConfig.VERSION_CODE}\nauthor=GlyphOrder\ndescription=${context.getString(R.string.module_description)}\n")
                // Keep the preloader in the generated module. A separate FontLoader
                // installation must not be required after each font configuration.
                ZipFile(context.applicationInfo.sourceDir).use { apk ->
                    val abis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                    val libraries = abis.mapNotNull { abi ->
                        apk.getEntry("lib/$abi/libglyphorder_preload.so")?.let { abi to it }
                    }
                    check(libraries.any { it.first == Build.SUPPORTED_ABIS.first() }) { UiError.key("error_module_integrity") }
                    libraries.forEach { (abi, entry) ->
                        stream.putNextEntry(ZipEntry("zygisk/$abi.so"))
                        apk.getInputStream(entry).use { it.copyTo(stream) }; stream.closeEntry()
                    }
                    text("licenses/Zygisk-API.txt", context.assets.open("legal/Zygisk-API.txt").bufferedReader().use { it.readText() })
                }
                text("baseline.json", snapshot.build.manifest().toString())
                snapshot.configs.forEach { (path, xml) -> text("baseline/${path.removePrefix("/")}", xml) }
                updated.forEach { (path, xml) -> text(DeviceRepository.overlayPath(path), xml) }
                val licenses = linkedMapOf<String, ByteArray>()
                val fontStore = dev.glyphorder.app.font.FontStore(context)
                selected.forEach { info ->
                    val font = info.file(directory)
                    require(dev.glyphorder.app.font.SfntReader.read(font).id == info.id) { UiError.key("error_font_integrity") }
                    stream.putNextEntry(ZipEntry("system/fonts/GlyphOrder-${info.fileName}"))
                    font.inputStream().use { it.copyTo(stream) }; stream.closeEntry()
                    // Product customization resolves relative names against /product/fonts.
                    if (updated["/product/etc/fonts_customization.xml"]?.let(FontConfig::files)?.contains("GlyphOrder-${info.fileName}") == true) {
                        stream.putNextEntry(ZipEntry("system/product/fonts/GlyphOrder-${info.fileName}"))
                        font.inputStream().use { it.copyTo(stream) }; stream.closeEntry()
                    }
                    licenses.putAll(fontStore.licenseFiles(info))
                }
                licenses.forEach { (name, license) ->
                    stream.putNextEntry(ZipEntry("licenses/$name")); stream.write(license); stream.closeEntry()
                }
                val script = buildString {
                    append("#!/system/bin/sh\n")
                    append("[ \"\$API\" -ge 34 ] || abort ${Root.quote(context.getString(R.string.module_error_android_version))}\n")
                    append("[ \"\$(getprop ro.build.fingerprint)\" = ${Root.quote(snapshot.fingerprint)} ] || abort ${Root.quote(context.getString(R.string.module_error_device_changed))}\n")
                    append("[ \"\$(getprop ro.build.version.sdk)\" = ${Root.quote(snapshot.sdk.toString())} ] || abort ${Root.quote(context.getString(R.string.module_error_device_changed))}\n")
                    append("[ \"\$(getprop ro.build.version.incremental)\" = ${Root.quote(snapshot.incremental)} ] || abort ${Root.quote(context.getString(R.string.module_error_device_changed))}\n")
                    append("set_perm_recursive \"\$MODPATH\" 0 0 0755 0644\n")
                    updated.forEach { (path, _) ->
                        append("set_perm \"\$MODPATH/${DeviceRepository.overlayPath(path)}\" 0 0 0644 ${Root.quote(snapshot.contexts.getValue(path))}\n")
                    }
                    append("set_perm \"\$MODPATH/service.sh\" 0 0 0755\n")
                    append("ui_print ${Root.quote(context.getString(R.string.module_install_print))}\n")
                }
                text("customize.sh", script)
                text("service.sh", "#!/system/bin/sh\nMODDIR=\${0%/*}\ncat /proc/sys/kernel/random/boot_id > \"\$MODDIR/mounted-boot-id\"\n")
                val recovery = listOf(
                    context.getString(R.string.recovery_file_title),
                    context.getString(R.string.recovery_file_adb, MODULE_ID),
                    context.getString(R.string.recovery_file_update, MODULE_ID),
                    context.getString(R.string.recovery_file_do_not_edit)
                ).joinToString("\n")
                text("RECOVERY.txt", "$recovery\n")
            }
        } catch (e: Exception) { zip.delete(); throw e }
        val hash = zip.inputStream().use { input ->
            val md = java.security.MessageDigest.getInstance("SHA-256"); val bytes = ByteArray(65536)
            while (true) { val n = input.read(bytes); if (n < 0) break; md.update(bytes, 0, n) }
            md.digest().joinToString("") { "%02x".format(it) }
        }
        // Bound cache growth while retaining the current generated ZIP.
        output.listFiles().orEmpty().filter { it.extension == "zip" }.sortedByDescending { it.lastModified() }.drop(3).forEach(File::delete)
        return BuiltModule(zip, hash, snapshot.build, snapshot.configs.mapValues { Root.hash(it.value) }, maxFamilies)
    }
    fun install(module: BuiltModule) : String {
        check(module.build.matches(SystemBuild.current())) { UiError.key("error_device_changed") }
        val actual = module.file.inputStream().use { input ->
            val md = java.security.MessageDigest.getInstance("SHA-256"); val b = ByteArray(65536)
            while (true) { val n = input.read(b); if (n < 0) break; md.update(b, 0, n) }
            md.digest().joinToString("") { "%02x".format(it) }
        }
        check(actual == module.hash) { UiError.key("error_module_integrity") }
        ModuleSafety.validateArchive(module.file, module.configHashes.keys)
        val fresh = DeviceRepository().inspect()
        check(fresh.conflicts.isEmpty()) { UiError.key("error_conflict_detected") }
        check(fresh.configs.mapValues { Root.hash(it.value) } == module.configHashes) { UiError.key("error_config_changed") }
        val manager = RootManager.detect()
        if (manager.backend == RootBackend.KERNEL_SU) {
            check(Root.exists("/data/adb/metamodule/metamount.sh") &&
                !Root.exists("/data/adb/metamodule/disable") && !Root.exists("/data/adb/metamodule/remove")) {
                UiError.key("error_root_command", "mount", "KernelSU: meta-overlayfs")
            }
        }
        val output = Root.run(manager.installCommand(module.file.absolutePath))
        val installedPath = if (Root.exists("/data/adb/modules_update/$MODULE_ID/module.prop")) "/data/adb/modules_update/$MODULE_ID" else "/data/adb/modules/$MODULE_ID"
        check(Root.exists("$installedPath/module.prop")) { UiError.key("error_magisk_module_missing") }
        check(SystemBuild.fromManifest(JSONObject(Root.read("$installedPath/baseline.json"))).matches(module.build)) { UiError.key("error_installed_module_integrity") }
        // Installing is an explicit request to use this module at the next boot, including after recovery.
        Root.run("rm -f /data/adb/modules/$MODULE_ID/disable /data/adb/modules_update/$MODULE_ID/disable")
        val backup = File(context.filesDir, "backup").apply { mkdirs() }
        File(backup, "last-installed.zip").takeIf { it.exists() }?.copyTo(File(backup, "previous.zip"), overwrite = true)
        module.file.copyTo(File(backup, "last-installed.zip"), overwrite = true)
        return output
    }
}
