package dev.glyphorder.app

import android.app.Application
import android.net.Uri
import dev.glyphorder.app.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.glyphorder.app.font.*
import dev.glyphorder.app.module.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppState(
    val fonts: List<FontInfo> = emptyList(), val plan: FontPlan = FontPlan(),
    val device: DeviceSnapshot? = null, val busy: Boolean = false,
    val message: String? = null, val error: String? = null,
    val module: BuiltModule? = null, val installedNow: Boolean = false,
    val log: String = "", val recoveryAvailable: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val store = FontStore(application)
    private val devices = DeviceRepository()
    private val builder = ModuleBuilder(application)
    private val status = application.getSharedPreferences("module_status", 0)
    private val mutable = MutableStateFlow(AppState(plan = store.load()))
    val state = mutable.asStateFlow()
    init { operation { store.ensureBundledFonts(); copy(fonts = store.all()) } }
    private fun operation(block: suspend AppState.() -> AppState) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                val next = withContext(Dispatchers.IO) { mutable.value.block() }
                mutable.value = next.copy(busy = false)
            } catch (e: Exception) {
                val canRecover = withContext(Dispatchers.IO) {
                    runCatching { com.topjohnwu.superuser.Shell.getCachedShell()?.isRoot == true &&
                        (Root.exists("/data/adb/modules/$MODULE_ID/module.prop") || Root.exists("/data/adb/modules_update/$MODULE_ID/module.prop")) }.getOrDefault(false)
                }
                mutable.value = mutable.value.copy(
                    busy = false,
                    error = UiError.resolve(getApplication(), e.message) ?: getApplication<Application>().getString(R.string.error_operation_failed),
                    recoveryAvailable = canRecover, log = e.message.orEmpty().take(4000)
                )
            }
        }
    }
    fun clearNotice() { mutable.value = mutable.value.copy(message = null, error = null) }
    fun import(uris: List<Uri>) = operation {
        var failure: String? = null; var count = 0
        uris.forEach { uri -> runCatching { store.import(uri); count++ }.onFailure { failure = it.message } }
        copy(fonts = store.all(), error = UiError.resolve(getApplication(), failure)?.takeIf { failure != null }
            ?: if (failure != null) getApplication<Application>().getString(R.string.error_operation_failed) else null,
            message = if (count > 0) getApplication<Application>().resources.getQuantityString(R.plurals.notice_imported_fonts, count, count) else null,
            module = null)
    }
    private fun change(plan: FontPlan) {
        if (mutable.value.busy) return
        store.save(plan)
        mutable.value = mutable.value.copy(plan = plan, module = null)
    }
    fun primary(id: String?) = change(mutable.value.plan.copy(primary = id))
    fun automaticFallback(enabled: Boolean) = change(mutable.value.plan.copy(automaticFallback = enabled))
    fun defaultFallbacks() = change(FontSelection.defaultFallbacks(mutable.value.plan))
    fun fallback(tag: String, id: String?) {
        val p = mutable.value.plan
        change(p.copy(fallbacks = p.fallbacks.toMutableMap().apply { if (id == null) remove(tag) else put(tag, id) }))
    }
    fun universal(id: String) {
        val p = mutable.value.plan
        change(p.copy(universal = if (id in p.universal) p.universal - id else p.universal + id))
    }
    fun moveUniversal(index: Int, direction: Int) {
        val p = mutable.value.plan; val list = p.universal.toMutableList(); val target = index + direction
        if (index !in list.indices || target !in list.indices) return
        val id = list.removeAt(index); list.add(target, id); change(p.copy(universal = list))
    }
    fun target(name: String) {
        val p = mutable.value.plan
        val selected = if (p.global) mutable.value.device?.targets?.toSet() ?: p.targets else p.targets
        change(p.copy(targets = if (name in selected) selected - name else selected + name, global = false))
    }
    fun global(enabled: Boolean) = change(mutable.value.plan.copy(global = enabled))
    fun targetGroup(group: String) {
        val p = mutable.value.plan
        val available = mutable.value.device?.targets.orEmpty().filter { FontStyles.group(it) == group }.toSet()
        val selected = if (p.global) mutable.value.device?.targets?.toSet().orEmpty() else p.targets
        change(p.copy(global = false, targets = if (selected.containsAll(available)) selected - available else selected + available))
    }
    fun styleMode(enabled: Boolean) {
        val p = mutable.value.plan
        val groups = if (p.global) FontStyles.groups else p.targets.map(FontStyles::group).distinct()
        change(p.copy(styleMode = enabled, styleFonts = p.styleFonts.ifEmpty {
            p.primary?.let { id -> groups.associateWith { id } }.orEmpty()
        }))
    }
    fun styleFont(group: String, id: String?) {
        require(group in FontStyles.groups)
        val p = mutable.value.plan
        change(p.copy(styleFonts = p.styleFonts.toMutableMap().apply { if (id == null) remove(group) else put(group, id) }))
    }
    fun axis(id: String, tag: String, value: Float) {
        val p = mutable.value.plan
        change(p.copy(axes = p.axes + (id to (p.axes[id].orEmpty() + (tag to value)))))
    }
    fun resetAxes(id: String) { val p = mutable.value.plan; change(p.copy(axes = p.axes - id)) }
    fun remove(font: FontInfo) = operation {
        store.delete(font)
        val next = plan.copy(primary = plan.primary.takeUnless { it == font.id }, fallbacks = plan.fallbacks.filterValues { it != font.id },
            axes = plan.axes - font.id, universal = plan.universal - font.id,
            styleFonts = plan.styleFonts.filterValues { it != font.id })
        store.save(next); copy(fonts = store.all(), plan = next, module = null, message = getApplication<Application>().getString(R.string.font_removed))
    }
    fun inspect() = inspect(announce = true)
    fun inspect(announce: Boolean) = operation {
        val snapshot = devices.inspect()
        copy(device = snapshot, message = if (announce) getApplication<Application>().getString(R.string.notice_device_checked) else null, installedNow = status.getString("pendingBoot", null) == snapshot.bootId,
            recoveryAvailable = (snapshot.installed || snapshot.pending) && !snapshot.disabled)
    }
    fun build() = operation {
        val snapshot = devices.inspect()
        val module = builder.build(snapshot, plan, fonts, store.directory)
        copy(device = snapshot, module = module, message = getApplication<Application>().getString(R.string.module_generated),
            log = getApplication<Application>().getString(R.string.module_build_log, module.file.name))
    }
    fun install() = operation {
        val archive = module ?: error(UiError.key("error_module_not_generated"))
        val output = builder.install(archive)
        val snapshot = devices.inspect()
        status.edit().putString("pendingBoot", snapshot.bootId).apply()
        copy(device = snapshot, installedNow = true, message = getApplication<Application>().getString(R.string.notice_install_complete), log = output)
    }
    fun applyConfigured() = operation {
        require(FontStyles.configured(plan)) { UiError.key("error_select_font") }
        val snapshot = devices.inspect()
        val effectivePlan = FontSelection.forDevice(plan, snapshot.targets)
        val archive = builder.build(snapshot, effectivePlan, fonts, store.directory)
        val output = builder.install(archive)
        store.save(effectivePlan)
        status.edit().putString("pendingBoot", snapshot.bootId).apply()
        copy(plan = effectivePlan, device = devices.inspect(), module = archive, installedNow = true,
            message = getApplication<Application>().getString(R.string.notice_install_complete), log = output)
    }
    fun disable() = operation {
        devices.disable()
        val bootId = Root.read("/proc/sys/kernel/random/boot_id")
        status.edit().putString("pendingBoot", bootId).apply()
        // OTA inspection may intentionally refuse the old XML; disabling must still be possible.
        val snapshot = runCatching { devices.inspect() }.getOrNull() ?: device?.copy(disabled = true)
        copy(device = snapshot, installedNow = true, recoveryAvailable = false,
            message = getApplication<Application>().getString(R.string.notice_module_disabled))
    }
    fun reboot() = operation { devices.reboot(); this }
    fun export(uri: Uri) = operation {
        val file = module?.file ?: error(UiError.key("error_module_not_generated"))
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: error(UiError.key("error_module_save"))
        copy(message = getApplication<Application>().getString(R.string.notice_module_saved))
    }
}
