package dev.glyphorder.app.module

import android.os.Build
import dev.glyphorder.app.UiError
import com.topjohnwu.superuser.Shell
import org.json.JSONObject
import java.security.MessageDigest

const val MODULE_ID = "glyphorder_fonts"
val CONFIG_PATHS = FontPaths.all
data class DeviceSnapshot(
    val fingerprint: String, val sdk: Int, val rootManager: String,
    val configs: Map<String, String>, val contexts: Map<String, String>,
    val targets: List<String>, val currentFamily: String,
    val installed: Boolean, val disabled: Boolean, val pending: Boolean,
    val conflicts: List<String>, val bootId: String,
    val fontCoverage: Map<String, List<IntRange>> = emptyMap(),
    val incremental: String = "", val bootSession: String = bootId, val vendorFontOverride: Boolean = false,
    val platform: FontPlatform = FontPlatform.AOSP
) {
    val build get() = SystemBuild(fingerprint, sdk, incremental)
}

object Root {
    // Modern Magisk may expose its binaries only in /debug_ramdisk after boot.
    // The app/ADB PATH need not include it. Keep this local to our shell process.
    private const val PATH_SETUP = "export PATH=/debug_ramdisk:/sbin:\$PATH"
    init {
        Shell.setDefaultBuilder(Shell.Builder.create().setCommands("/system/bin/sh", "-c", "$PATH_SETUP; exec su"))
    }
    fun shell(): Shell {
        val cached = Shell.getCachedShell()
        if (cached != null && !cached.isRoot) cached.close()
        return Shell.getShell()
    }
    fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"
    fun run(command: String): String {
        val result = Shell.cmd("$PATH_SETUP\n$command").exec()
        check(result.isSuccess) { UiError.key("error_root_command", result.code.toString(),
            (result.err + result.out).joinToString("\n").takeLast(4000).ifBlank { "—" }) }
        return result.out.joinToString("\n")
    }
    fun read(path: String) = run("cat ${quote(path)}")
    fun exists(path: String) = Shell.cmd("test -f ${quote(path)}").exec().isSuccess
    fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

class DeviceRepository {
    fun inspect(): DeviceSnapshot {
        // libsu caches an unprivileged shell after denial or timeout. A user-initiated retry
        // must discard it, otherwise Magisk never receives another authorization request.
        check(Root.shell().isRoot) { UiError.key("error_root_permission") }
        val manager = RootManager.detect()
        val platform = FontPlatform.identify(Root.run("getprop ro.lineage.version").trim(),
            Root.run("getprop ro.build.version.oplus.api").trim())
        val active = "/data/adb/modules/$MODULE_ID"
        val staged = "/data/adb/modules_update/$MODULE_ID"
        val installed = Root.exists("$active/module.prop")
        val disabled = installed && Root.exists("$active/disable")
        val bootId = Root.read("/proc/sys/kernel/random/boot_id")
        fun baseline(path: String): FontBaseline? {
            if (!Root.exists("$path/baseline.json")) return null
            return FontBaseline(path, SystemBuild.fromManifest(JSONObject(Root.read("$path/baseline.json"))),
                Root.exists("$path/disable"),
                Root.exists("$path/mounted-boot-id") && Root.read("$path/mounted-boot-id") == bootId)
        }
        val stagedBaseline = baseline(staged)
        val module = FontBaseline.select(SystemBuild.current(), baseline(active), stagedBaseline)
        val verifiedStage = stagedBaseline?.takeIf { it.build.matches(SystemBuild.current()) && !it.disabled }?.path
        val configs = linkedMapOf<String, String>(); val contexts = linkedMapOf<String, String>()
        val runtimeConfigs = linkedMapOf<String, String>()
        platform.configurationPaths.forEach { path ->
            if (!Root.exists(path)) return@forEach
            val current = Root.read(path)
            runtimeConfigs[path] = current
            val context = Root.run("ls -Zd ${Root.quote(path)}").split(Regex("\\s+"))
                .firstOrNull { it.startsWith("u:object_r:") } ?: error(UiError.key("error_selinux_label_missing"))
            require(context.matches(Regex("u:object_r:[a-zA-Z0-9_]+:s0"))) { UiError.key("error_selinux_label_unsupported") }
            contexts[path] = context
            val activeBackup = "$module/baseline/${path.substringAfter('/')}"
            val stagedBackup = "$verifiedStage/baseline/${path.substringAfter('/')}"
            val backup = if (module != null && Root.exists(activeBackup)) activeBackup
                else if (verifiedStage != null && Root.exists(stagedBackup)) stagedBackup else null
            val moduleFile = "$module/${overlayPath(path)}"
            if (backup != null) {
                val original = Root.read(backup)
                val stagedOriginal = if (verifiedStage != null && Root.exists(stagedBackup)) Root.read(stagedBackup) else null
                val stagedFile = "$verifiedStage/${overlayPath(path)}"
                // Reject another overlay changing the same config after our module was mounted.
                check(FontBaseline.acceptsConfig(current, original,
                    if (module != null && Root.exists(moduleFile)) Root.read(moduleFile) else null,
                    stagedOriginal, if (verifiedStage != null && Root.exists(stagedFile)) Root.read(stagedFile) else null)) {
                    UiError.key("error_font_config_conflict")
                }
                configs[path] = original
            } else configs[path] = current
            FontConfig.parse(configs.getValue(path))
        }
        require(configs.containsKey("/system/etc/fonts.xml")) { UiError.key("error_system_font_config_missing") }
        require(Build.VERSION.SDK_INT < 35 || FontPaths.MODERN in configs || FontPaths.COLOR_OS in configs) { UiError.key("error_android15_config_missing") }
        val overlays = CONFIG_PATHS.joinToString(" ") { Root.quote(overlayPath(it)) }
        val conflicts = Root.run("for d in /data/adb/modules/*; do [ -d \"\$d\" ] || continue; [ -f \"\$d/disable\" ] && continue; [ -f \"\$d/remove\" ] && continue; [ \"\${d##*/}\" = '$MODULE_ID' ] && continue; for f in $overlays; do if [ -f \"\$d/\$f\" ]; then echo \"\${d##*/}\"; break; fi; done; done").lines().filter { it.isNotBlank() }
        val families = FontPaths.targets(configs, FontPaths.active(configs)).toList()
        val runtime = runtimeConfigs.getValue(FontPaths.active(runtimeConfigs))
        val first = FontConfig.files(runtime).first()
        val coverage = linkedMapOf<String, List<IntRange>>()
        android.graphics.fonts.SystemFonts.getAvailableFonts().forEach { font ->
            val key = "${font.file?.absolutePath}#${font.ttcIndex}"
            if (key !in coverage) runCatching { dev.glyphorder.app.font.CmapCoverage.read(font.buffer, font.ttcIndex) }
                .onSuccess { coverage[key] = it }
        }
        // With our module active, SystemFonts enumerates the current configuration;
        // it can omit stock faces referenced only by the saved baseline. Read those
        // actual files as well so regeneration has the same verified information.
        configs.forEach { (path, xml) ->
            val nodes = FontConfig.parse(xml).getElementsByTagName("font")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as org.w3c.dom.Element
                val name = node.textContent.trim()
                val filename = if (name.startsWith('/')) name else
                    FontPaths.fontDirectory(path) + name
                val index = node.getAttribute("index").toIntOrNull() ?: 0
                val key = "$filename#$index"
                if (key !in coverage) runCatching {
                    java.io.FileInputStream(filename).use { input ->
                        val channel = input.channel
                        require(channel.size() in 1..128L * 1024 * 1024)
                        dev.glyphorder.app.font.CmapCoverage.read(
                            channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, channel.size()), index)
                    }
                }.onSuccess { coverage[key] = it }
            }
        }
        return DeviceSnapshot(Build.FINGERPRINT, Build.VERSION.SDK_INT, manager.displayName, configs, contexts,
            families, first, installed, disabled, Root.exists("$staged/module.prop") || Root.exists("$active/update"), conflicts,
            bootId, coverage, Build.VERSION.INCREMENTAL, "$bootId:${Root.run("pidof system_server").trim()}",
            platform == FontPlatform.COLOR_OS && FontPaths.COLOR_OS in configs &&
                ((Root.run("settings get system font_variation_settings").trim().toIntOrNull() ?: 0) ushr 12 and 15) != 0, platform)
    }
    fun disable() {
        val active = Root.exists("/data/adb/modules/$MODULE_ID/module.prop")
        val staged = Root.exists("/data/adb/modules_update/$MODULE_ID/module.prop")
        check(active || staged) { UiError.key("error_module_not_installed") }
        if (active) Root.run("touch /data/adb/modules/$MODULE_ID/disable")
        if (Root.exists("/data/adb/modules_update/$MODULE_ID/module.prop")) Root.run("touch /data/adb/modules_update/$MODULE_ID/disable")
    }
    fun reboot() { Root.run(RootManager.detect().rebootCommand()) }
    companion object {
        fun overlayPath(path: String) = FontPaths.overlay(path)
    }
}
