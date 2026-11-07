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
    val log: String = "", val recoveryAvailable: Boolean = false,
    val combinations: List<FontCombination> = emptyList(),
    val library: List<FontLibraryFamily> = emptyList()
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val store = FontStore(application)
    private val devices = DeviceRepository()
    private val builder = ModuleBuilder(application)
    private val status = application.getSharedPreferences("module_status", 0)
    private val mutable = MutableStateFlow(AppState(plan = store.load()))
    val state = mutable.asStateFlow()
    private var settingsRefreshPending = false
    init { operation {
        store.ensureBundledFonts()
        val available = store.all()
        val (saved, combinations) = store.combinations(available)
        copy(fonts = available, plan = saved, combinations = combinations, library = store.library(available))
    } }
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
    fun settingsUnavailable() { mutable.value = mutable.value.copy(error = getApplication<Application>().getString(R.string.error_settings_unavailable)) }
    fun settingsOpened() { settingsRefreshPending = true }
    fun shouldInspectOnResume(): Boolean {
        val needed = mutable.value.device == null || settingsRefreshPending
        settingsRefreshPending = false
        return needed
    }
    fun import(uris: List<Uri>, familyId: String? = null) = operation {
        val count = store.importFamilies(uris, familyId)
        val available = store.all()
        copy(fonts = available, library = store.library(available),
            message = getApplication<Application>().resources.getQuantityString(R.plurals.notice_imported_fonts, count, count), module = null)
    }
    fun renameFamily(id: String, name: String) {
        if (mutable.value.busy) return
        store.renameFamily(id, name, mutable.value.fonts)
        mutable.value = mutable.value.copy(library = store.library(mutable.value.fonts), module = null)
    }
    fun removeFamily(id: String) = operation {
        val family = library.single { it.id == id }
        store.deleteFamily(family, fonts)
        val catalog = combinations.map { it.copy(fonts = it.fonts - id) }
        store.saveCombinations(catalog)
        val available = store.all()
        copy(fonts = available, library = store.library(available), combinations = catalog, module = null)
    }
    fun removeFace(familyId: String, faceId: String) = operation {
        store.removeFamilyFace(library.single { it.id == familyId }, fonts.single { it.id == faceId }, fonts)
        val available = store.all()
        copy(fonts = available, library = store.library(available), module = null)
    }
    private fun change(plan: FontPlan) {
        if (mutable.value.busy) return
        store.save(plan)
        mutable.value = mutable.value.copy(plan = plan, module = null)
    }
    private fun combinations(value: List<FontCombination>) {
        if (mutable.value.busy) return
        store.saveCombinations(value)
        mutable.value = mutable.value.copy(combinations = value, module = null)
    }
    fun createCombination(): String {
        val id = java.util.UUID.randomUUID().toString()
        val name = getApplication<Application>().getString(R.string.font_combination) + " " + (mutable.value.combinations.size + 1)
        combinations(mutable.value.combinations + FontCombination(id, name, emptyList()))
        return id
    }
    fun renameCombination(id: String, name: String) { combinations(mutable.value.combinations.map { if (it.id == id) it.copy(name = name) else it }) }
    fun combinationFonts(id: String, fonts: List<String>) { combinations(mutable.value.combinations.map { if (it.id == id) it.copy(fonts = fonts.distinct()) else it }) }
    fun combinationFont(id: String, index: Int, font: String) {
        val current = mutable.value.combinations.find { it.id == id } ?: return
        val ids = current.fonts.toMutableList()
        if (index in ids.indices) ids[index] = font else ids.add(font)
        combinationFonts(id, ids)
    }
    fun moveCombinationFont(id: String, index: Int, direction: Int) {
        val ids = mutable.value.combinations.find { it.id == id }?.fonts?.toMutableList() ?: return
        val target = index + direction
        if (index !in ids.indices || target !in ids.indices) return
        ids.add(target, ids.removeAt(index)); combinationFonts(id, ids)
    }
    fun removeCombinationFont(id: String, index: Int) {
        val ids = mutable.value.combinations.find { it.id == id }?.fonts?.toMutableList() ?: return
        if (index !in ids.indices) return
        ids.removeAt(index); combinationFonts(id, ids)
    }
    fun removeCombination(id: String) {
        combinations(mutable.value.combinations.filterNot { it.id == id })
        val plan = mutable.value.plan
        change(plan.copy(combinationId = plan.combinationId.takeUnless { it == id }, styleCombinations = plan.styleCombinations.filterValues { it != id }))
    }
    fun chooseCombination(group: String?, id: String?) {
        val plan = mutable.value.plan
        change(if (group == null) plan.copy(combinationId = id) else plan.copy(styleCombinations = plan.styleCombinations.toMutableMap().apply {
            if (id == null) remove(group) else put(group, id)
        }))
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
        change(mutable.value.plan.copy(styleMode = enabled))
    }
    fun styleFont(group: String, id: String?) {
        require(group in FontStyles.groups)
        val p = mutable.value.plan
        val chain = FontStyles.chain(p, group)
        styleChain(group, if (id == null) emptyList() else listOf(id) + chain.drop(1).filter { it != id })
    }
    fun styleChain(group: String, ids: List<String>) {
        require(group in FontStyles.groups)
        val p = mutable.value.plan
        val chain = ids.distinct()
        change(p.copy(styleChains = p.styleChains + (group to chain), styleFonts = p.styleFonts.toMutableMap().apply {
            if (chain.isEmpty()) remove(group) else put(group, chain.first())
        }))
    }
    fun chainFont(group: String, index: Int, id: String) {
        val chain = FontStyles.chain(mutable.value.plan, group).toMutableList()
        if (index in chain.indices) chain[index] = id else chain.add(id)
        styleChain(group, chain)
    }
    fun moveChainFont(group: String, index: Int, direction: Int) {
        val chain = FontStyles.chain(mutable.value.plan, group).toMutableList()
        val target = index + direction
        if (index !in chain.indices || target !in chain.indices) return
        chain.add(target, chain.removeAt(index)); styleChain(group, chain)
    }
    fun removeChainFont(group: String, index: Int) {
        val chain = FontStyles.chain(mutable.value.plan, group).toMutableList()
        if (index !in chain.indices) return
        chain.removeAt(index); styleChain(group, chain)
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
            styleFonts = plan.styleFonts.filterValues { it != font.id },
            styleChains = plan.styleChains.mapValues { (_, ids) -> ids - font.id })
        val catalog = combinations.map { it.copy(fonts = it.fonts - font.id) }
        store.save(next); store.saveCombinations(catalog)
        copy(fonts = store.all(), plan = next, combinations = catalog, module = null, message = getApplication<Application>().getString(R.string.font_removed))
    }
    fun inspect() = inspect(announce = true)
    fun inspect(announce: Boolean) = operation {
        val snapshot = devices.inspect()
        copy(device = snapshot, message = if (announce) getApplication<Application>().getString(R.string.notice_device_checked) else null, installedNow = status.getString("pendingBoot", null) == snapshot.bootSession,
            recoveryAvailable = (snapshot.installed || snapshot.pending) && !snapshot.disabled)
    }
    fun build() = operation {
        val snapshot = devices.inspect()
        val module = builder.build(snapshot, CombinationPlan.resolve(plan, combinations, library, fonts), fonts, store.directory)
        copy(device = snapshot, module = module, message = getApplication<Application>().getString(R.string.module_generated),
            log = getApplication<Application>().getString(R.string.module_build_log, module.file.name))
    }
    fun install() = operation {
        val archive = module ?: error(UiError.key("error_module_not_generated"))
        val output = builder.install(archive)
        val snapshot = devices.inspect()
        status.edit().putString("pendingBoot", snapshot.bootSession).apply()
        copy(device = snapshot, installedNow = true, message = getApplication<Application>().getString(R.string.notice_install_complete), log = output)
    }
    fun applyConfigured() = operation {
        val resolved = CombinationPlan.resolve(plan, combinations, library, fonts)
        require(FontStyles.configured(resolved)) { UiError.key("error_select_font") }
        val snapshot = devices.inspect()
        val effectivePlan = FontSelection.forDevice(resolved, snapshot.targets)
        val archive = builder.build(snapshot, effectivePlan, fonts, store.directory)
        val output = builder.install(archive)
        val savedPlan = plan.copy(targets = effectivePlan.targets)
        store.save(savedPlan)
        status.edit().putString("pendingBoot", snapshot.bootSession).apply()
        copy(plan = savedPlan, device = devices.inspect(), module = archive, installedNow = true,
            message = getApplication<Application>().getString(R.string.notice_install_complete), log = output)
    }
    fun disable() = operation {
        devices.disable()
        val bootId = Root.read("/proc/sys/kernel/random/boot_id")
        status.edit().putString("pendingBoot", "$bootId:${Root.run("pidof system_server").trim()}").apply()
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
