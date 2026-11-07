@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package dev.glyphorder.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import dev.glyphorder.app.*
import dev.glyphorder.app.R
import dev.glyphorder.app.font.*
import dev.glyphorder.app.module.*
import android.icu.util.ULocale
import java.text.NumberFormat

@Composable
fun Symbol(@DrawableRes id: Int, description: String? = null, modifier: Modifier = Modifier) {
    Icon(painterResource(id), contentDescription = description, modifier = modifier.size(24.dp))
}

@Composable
private fun formattedNumber(value: Number, fractionDigits: Int = 0): String =
    NumberFormat.getNumberInstance(ComposeLocale.current.platformLocale).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }.format(value)

@Composable
private fun fontWeights(font: FontInfo, fonts: List<FontInfo>): String {
    val formatter = NumberFormat.getIntegerInstance(ComposeLocale.current.platformLocale)
    return FontSelection.family(font, fonts).map { it.weight }.distinct().joinToString(" / ") { formatter.format(it) }
}

@Composable
internal fun styleName(group: String): String = stringResource(when (group) {
    "sans" -> R.string.style_sans; "serif" -> R.string.style_serif; "mono" -> R.string.style_mono
    "google" -> R.string.style_google; "casual" -> R.string.style_casual; "cursive" -> R.string.style_cursive
    else -> R.string.style_other
})

@Composable
private fun bytes(size: Long): String {
    val number = NumberFormat.getNumberInstance(ComposeLocale.current.platformLocale).apply {
        minimumFractionDigits = if (size >= 1024 * 1024) 1 else 0
        maximumFractionDigits = if (size >= 1024 * 1024) 1 else 0
    }
    return if (size >= 1024 * 1024) stringResource(R.string.size_megabytes, number.format(size / 1048576.0))
    else stringResource(R.string.size_kilobytes, number.format(size / 1024))
}

@Composable
fun GlyphApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var route by rememberSaveable { mutableStateOf("") }
    var legalTitle by rememberSaveable { mutableStateOf("") }
    var legalAssets by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailFamilyId by rememberSaveable { mutableStateOf<String?>(null) }
    var importFamilyId by rememberSaveable { mutableStateOf<String?>(null) }
    var licenseId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val motion = MaterialTheme.motionScheme
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm) {
        lifecycle.currentStateFlow.collect { current ->
            if (current == Lifecycle.State.RESUMED) {
                vm.state.first { !it.busy }
                if (lifecycle.currentState == Lifecycle.State.RESUMED && vm.shouldInspectOnResume()) vm.inspect(announce = false)
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.import(it) }
    val importFaces = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) vm.import(it, importFamilyId)
        importFamilyId = null
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { if (it != null) vm.export(it) }
    LaunchedEffect(state.message, state.error) {
        (state.error ?: state.message)?.let { snackbar.showSnackbar(it); vm.clearNotice() }
    }
    val navigateBack = { route = when {
        route.startsWith("preview:") -> route.removePrefix("preview:").takeUnless { it == "apply" }.orEmpty()
        route == "legal-document" -> "licenses"
        route in setOf("licenses", "privacy") -> "about"
        else -> ""
    } }
    BackHandler(route.isNotEmpty(), onBack = navigateBack)
    val titles = listOf(stringResource(R.string.font_library), stringResource(R.string.font_combination), stringResource(R.string.tab_apply))
    val appBarScroll = key(tab, route) { TopAppBarDefaults.exitUntilCollapsedScrollBehavior() }
    Scaffold(
        modifier = Modifier.nestedScroll(appBarScroll.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(scrollBehavior = appBarScroll, title = { Text(when { route.startsWith("preview:") -> stringResource(R.string.tab_preview); route.startsWith("family:") -> stringResource(R.string.font_family); route.startsWith("combination:") -> stringResource(R.string.font_combination); route == "about" -> stringResource(R.string.title_about); route == "licenses" -> stringResource(R.string.title_open_source); route == "privacy" -> stringResource(R.string.title_privacy); route == "legal-document" -> legalTitle; route == "targets" -> stringResource(R.string.title_targets); else -> titles[tab] }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { if (route.isNotEmpty()) IconButton(onClick = navigateBack) { Symbol(R.drawable.ic_arrow_back, stringResource(R.string.accessibility_back)) } },
                actions = {
                    if (route.isEmpty()) IconButton(onClick = { route = "about" }) { Symbol(R.drawable.ic_info, stringResource(R.string.title_about)) }
                    IconButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        })
                    }) { Symbol(R.drawable.ic_language, stringResource(R.string.accessibility_change_language)) }
                    if (tab == 2 && route.isEmpty()) IconButton(enabled = !state.busy, onClick = vm::inspect) { Symbol(R.drawable.ic_refresh, stringResource(R.string.accessibility_inspect_device)) }
                })
        },
        bottomBar = {
            if (route.isEmpty()) NavigationBar {
                listOf(R.drawable.ic_font_download, R.drawable.ic_layers, R.drawable.ic_tune).forEachIndexed { index, icon ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Symbol(icon) }, label = { Text(titles[index]) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(targetState = if (route.isNotEmpty()) route else "tab$tab", label = "page",
                transitionSpec = {
                    val targetTab = targetState.removePrefix("tab").toIntOrNull()
                    val initialTab = initialState.removePrefix("tab").toIntOrNull()
                    val direction = if (targetTab != null && initialTab != null) {
                        if (targetTab > initialTab) 1 else -1
                    } else if (targetTab != null) -1 else 1
                    ((fadeIn(motion.fastEffectsSpec()) + slideInHorizontally(motion.defaultSpatialSpec<IntOffset>()) { it / 12 * direction })
                        togetherWith (fadeOut(motion.fastEffectsSpec()) + slideOutHorizontally(motion.defaultSpatialSpec<IntOffset>()) { -it / 12 * direction })).using(null)
                }, modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) { page ->
                if (page.startsWith("preview:")) {
                    val source = page.removePrefix("preview:")
                    val previewPlan = when {
                        source == "apply" -> CombinationPlan.resolve(state.plan, state.combinations, state.library, state.fonts)
                        source.startsWith("family:") -> FontCombination("", "", listOf(source.substringAfter(':'))).preview(state.plan, state.library, state.fonts)
                        else -> state.combinations.find { it.id == source.substringAfter(':') }?.preview(state.plan, state.library, state.fonts) ?: FontPlan(automaticFallback = false)
                    }
                    FullPreviewPage(page, state, vm, previewPlan, source == "apply" && previewPlan.styleMode)
                }
                else if (page.startsWith("combination:")) state.combinations.find { it.id == page.substringAfter(':') }?.let { combination ->
                    CombinationEditor(combination, state, vm, { index -> picker = "combinationFont:${combination.id}:$index" },
                        { confirm = "removeCombination:${combination.id}" }, { route = "preview:combination:${combination.id}" })
                }
                else if (page.startsWith("family:")) state.library.find { it.id == page.substringAfter(':') }?.let { family ->
                    FamilyEditor(family, state, vm, { face -> detailFamilyId = family.id; detailId = face },
                        { importFamilyId = family.id; importFaces.launch(arrayOf("*/*")) }, { confirm = "removeFamily:${family.id}" },
                        { route = "preview:family:${family.id}" })
                }
                else when (page) {
                    "about" -> AboutPage({ route = it }, { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL))) })
                    "licenses" -> LicensesPage { title, assets -> legalTitle = title; legalAssets = ArrayList(assets); route = "legal-document" }
                    "privacy" -> LegalTextPage(listOf("legal/project/PRIVACY.md"))
                    "legal-document" -> LegalTextPage(legalAssets)
                    "targets" -> TargetsPage(state, vm)
                    "tab0" -> FontsPage(state, vm, { import.launch(arrayOf("*/*")) }, { route = "family:$it" })
                    "tab1" -> CombinationsPage(state, vm.store.directory, { route = "combination:${vm.createCombination()}" }, { route = "combination:$it" })
                    else -> ApplyPage(state, vm, { route = "targets" }, { picker = "chooseCombination:$it" }, { confirm = it },
                        { export.launch("glyphorder-fonts.zip") }, { route = "preview:apply" })
                }
            }
            if (state.busy) Surface(tonalElevation = 6.dp, shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(36.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.busy_processing), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    picker?.let { key ->
        if (key.startsWith("chooseCombination:")) {
            val group = key.substringAfter(':').takeUnless { it == "single" }
            CombinationPicker(state, if (group == null) state.plan.combinationId else state.plan.styleCombinations[group],
                { picker = null }, { id -> vm.chooseCombination(group, id); picker = null })
        } else if (key.startsWith("combinationFont:")) {
            val parts = key.split(':'); val index = parts[2].toInt()
            val combination = state.combinations.find { it.id == parts[1] }
            if (combination != null) FontPicker(stringResource(R.string.font_library), state, vm, combination.fonts.getOrNull(index),
                { picker = null }, { id -> if (id != null) vm.combinationFont(combination.id, index, id); picker = null },
                excluded = combination.fonts.filterIndexed { i, _ -> i != index }.toSet())
        }
    }
    detailId?.let { id -> state.fonts.find { it.id == id }?.let { font ->
        val family = state.library.find { it.id == detailFamilyId }
        FontDetail(font, state, vm, { detailId = null }, { confirm = "deleteFace:$detailFamilyId:$id" }, { detailId = null; licenseId = id },
            canRemove = family != null && !family.bundled && family.members.size > 1)
    } }
    licenseId?.let { id -> state.fonts.find { it.id == id }?.let { font ->
        val license = remember(id) { vm.store.license(font)?.second.orEmpty() }
        ModalBottomSheet(onDismissRequest = { licenseId = null }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(font.name, style = MaterialTheme.typography.headlineSmall)
                Text(license, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(24.dp))
            }
        }
    } }
    confirm?.let { action ->
        val title = when { action.startsWith("removeCombination:") -> stringResource(R.string.action_remove); action == "global" -> stringResource(R.string.action_apply_configured); action == "install" -> stringResource(R.string.dialog_install_title); action == "disable" -> stringResource(R.string.dialog_restore_title); action == "reboot" -> stringResource(R.string.dialog_reboot_title); else -> stringResource(R.string.dialog_remove_title) }
        val text = when {
            action.startsWith("removeFamily:") -> state.library.find { it.id == action.substringAfter(':') }?.name.orEmpty()
            action.startsWith("deleteFace:") -> state.fonts.find { it.id == action.substringAfterLast(':') }?.name.orEmpty()
            action.startsWith("removeCombination:") -> state.combinations.find { it.id == action.substringAfter(':') }?.name.orEmpty()
            action == "global" -> stringResource(R.string.apply_configured_description)
            action == "install" -> stringResource(R.string.dialog_install_body)
            action == "disable" -> stringResource(R.string.dialog_restore_body)
            action == "reboot" -> stringResource(R.string.dialog_reboot_body)
            else -> stringResource(R.string.dialog_remove_body)
        }
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(title) }, text = { Text(text) },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                when (action) {
                    "global" -> vm.applyConfigured(); "install" -> vm.install(); "disable" -> vm.disable(); "reboot" -> vm.reboot()
                    else -> if (action.startsWith("removeCombination:")) { vm.removeCombination(action.substringAfter(':')); route = "" }
                        else if (action.startsWith("removeFamily:")) { vm.removeFamily(action.substringAfter(':')); route = "" }
                        else if (action.startsWith("deleteFace:")) { val parts = action.split(':'); vm.removeFace(parts[1], parts[2]); detailId = null }
                        else { state.fonts.find { it.id == action.removePrefix("delete:") }?.let(vm::remove); detailId = null }
                }; confirm = null
            }) { Text(when (action) { "global", "install" -> stringResource(R.string.action_install); "reboot" -> stringResource(R.string.action_reboot); "disable" -> stringResource(R.string.action_disable); else -> stringResource(R.string.action_remove) }) } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}

@Composable
private fun FontsPage(state: AppState, vm: AppViewModel, onImport: () -> Unit, onDetail: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onImport, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Symbol(R.drawable.ic_add); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_import))
            }
        }
        items(state.library, key = { it.id }) { family ->
            Surface(onClick = { onDetail(family.id) }, enabled = !state.busy, shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(family.name.ifBlank { stringResource(R.string.font_family) }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        Symbol(R.drawable.ic_chevron_right)
                    }
                    PreviewText(stringResource(R.string.preview_glyphs_sample), state.fonts,
                        FontCombination("", "", listOf(family.id)).preview(state.plan, state.library, state.fonts),
                        vm.store.directory, 26f, 400, MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth(), previewLanguage())
                    Text(listOf(if (family.bundled) stringResource(R.string.font_builtin) else "",
                        bytes(family.faces(state.fonts).sumOf { it.bytes })).filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun fontScriptNames(scripts: Set<String>, locale: ULocale): String = scripts.sorted().joinToString(" · ") { short ->
    ScriptCatalog.name("und-$short", locale)
}

@Composable
private fun SettingRow(@DrawableRes icon: Int, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Symbol(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Symbol(R.drawable.ic_chevron_right)
    }
}

@Composable
private fun FontPicker(title: String, state: AppState, vm: AppViewModel, selected: String?, dismiss: () -> Unit,
                       choose: (String?) -> Unit, excluded: Set<String> = emptySet()) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    val available = state.library.filter { it.id !in excluded && (query.isBlank() || it.name.contains(query.trim(), true)) }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp)) }
            item {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_fonts)) }, leadingIcon = { Symbol(R.drawable.ic_search) },
                    shape = MaterialTheme.shapes.extraLarge)
            }
            items(available, key = { it.id }) { family ->
                Surface(onClick = { choose(family.id) }, shape = MaterialTheme.shapes.large,
                    color = if (selected == family.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(family.name.ifBlank { stringResource(R.string.font_family) }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            if (selected == family.id) Symbol(R.drawable.ic_check)
                        }
                        PreviewText(stringResource(R.string.preview_glyphs_sample), state.fonts,
                            FontCombination("", "", listOf(family.id)).preview(state.plan, state.library, state.fonts),
                            vm.store.directory, 24f, 400, MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth(), previewLanguage())
                    }
                }
            }
        }
    }
}

@Composable
private fun FontDetail(font: FontInfo, state: AppState, vm: AppViewModel, dismiss: () -> Unit, remove: () -> Unit, viewLicense: () -> Unit, canRemove: Boolean) {
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(font.name, style = MaterialTheme.typography.headlineSmall)
            Text("${bytes(font.bytes)} · ${font.postScriptName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PreviewText(stringResource(R.string.preview_glyphs_sample), state.fonts, state.plan.copy(primary = font.id, styleMode = false, fallbacks = emptyMap(), universal = emptyList(), orderedFallbacks = emptyList(), automaticFallback = false), vm.store.directory, 40f,
                state.plan.axes[font.id]?.get("wght")?.toInt() ?: 400, MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth())
            if (font.axes.isNotEmpty()) {
                Text(stringResource(R.string.font_axes_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.font_axes_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                font.axes.forEach { axis ->
                    val value = state.plan.axes[font.id]?.get(axis.tag)
                        ?: (if (axis.tag == "wght") 400f.coerceIn(axis.min, axis.max) else axis.default)
                    val axisDescription = stringResource(R.string.font_axis_accessibility, axis.tag)
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(axis.tag, style = MaterialTheme.typography.labelLarge)
                            Text(formattedNumber(value, 1), style = MaterialTheme.typography.labelLarge)
                        }
                        if (axis.min < axis.max) Slider(value, { vm.axis(font.id, axis.tag, it) }, valueRange = axis.min..axis.max, enabled = !state.busy,
                            modifier = Modifier.semantics { contentDescription = axisDescription })
                    }
                }
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { vm.resetAxes(font.id) }) { Text(stringResource(R.string.action_reset_axes)) }
            } else Text(stringResource(R.string.font_static_weight_detail, fontWeights(font, state.fonts)), style = MaterialTheme.typography.bodyMedium)
            if (vm.store.isBundled(font)) TextButton(shapes = ButtonDefaults.shapes(), onClick = viewLicense, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_view_license)) }
            else if (canRemove) TextButton(shapes = ButtonDefaults.shapes(), onClick = remove, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Symbol(R.drawable.ic_delete); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_remove_font)) }
        }
    }
}

@Composable
private fun TargetsPage(state: AppState, vm: AppViewModel) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(stringResource(R.string.targets_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp)) }
        item {
            ListItem(headlineContent = { Text(stringResource(R.string.scope_global)) },
                supportingContent = { Text(stringResource(R.string.global_scope_description)) },
                trailingContent = { Switch(checked = state.plan.global, onCheckedChange = vm::global, enabled = !state.busy) })
        }
        if (state.device == null) item {
            Button(onClick = vm::inspect, enabled = !state.busy) { Text(stringResource(R.string.action_check)) }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FontStyles.groups.forEach { group ->
                    val names = state.device?.targets.orEmpty().filter { FontStyles.group(it) == group }
                    FilterChip(selected = names.isNotEmpty() && (state.plan.global || state.plan.targets.containsAll(names)),
                        onClick = { vm.targetGroup(group) }, enabled = !state.busy && names.isNotEmpty(), label = { Text(styleName(group)) })
                }
            }
        }
        items(state.device?.targets.orEmpty(), key = { it }) { target ->
            Surface(onClick = { vm.target(target) }, enabled = !state.busy, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(state.plan.global || target in state.plan.targets, onCheckedChange = { vm.target(target) }, enabled = !state.busy)
                    Spacer(Modifier.width(8.dp)); Text(target, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun ApplyPage(state: AppState, vm: AppViewModel, targets: () -> Unit, chooseStyle: (String) -> Unit, confirm: (String) -> Unit, export: () -> Unit, preview: () -> Unit) {
    var showLog by rememberSaveable { mutableStateOf(false) }
    val device = state.device
    val resolved = remember(state.plan, state.combinations, state.library, state.fonts) { CombinationPlan.resolve(state.plan, state.combinations, state.library, state.fonts) }
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Symbol(if (device != null) R.drawable.ic_check_circle else R.drawable.ic_info)
                    Text(if (device != null) stringResource(R.string.device_ready) else stringResource(R.string.device_check), style = MaterialTheme.typography.titleLarge)
                }
                if (device != null) {
                    val androidVersion = when (device.sdk) { 37 -> 17; 36 -> 16; 35 -> 15; else -> 14 }
                    Text(stringResource(R.string.device_version, formattedNumber(androidVersion), device.rootManager), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.device_root_required), style = MaterialTheme.typography.bodyMedium)
                    Button(shapes = ButtonDefaults.shapes(), onClick = vm::inspect, enabled = !state.busy) { Text(stringResource(R.string.action_check)) }
                }
            }
        }
        if (device?.conflicts?.isNotEmpty() == true) Text(stringResource(R.string.device_conflicts, device.conflicts.joinToString()), color = MaterialTheme.colorScheme.error)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.application_mode), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = preview, enabled = !state.busy) { Text(stringResource(R.string.tab_preview)) }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(R.string.mode_single, R.string.mode_styles).forEachIndexed { index, label ->
                        SegmentedButton(selected = state.plan.styleMode == (index == 1), onClick = { vm.styleMode(index == 1) },
                            enabled = !state.busy, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(stringResource(label)) }
                    }
                }
                if (state.plan.styleMode && device?.vendorFontOverride == true) {
                    Text(stringResource(R.string.system_font_override), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(shapes = ButtonDefaults.shapes(), enabled = !state.busy, onClick = {
                        if (SettingsNavigation.open(context)) vm.settingsOpened() else vm.settingsUnavailable()
                    }) { Text(stringResource(R.string.action_system_font_settings)) }
                }
                if (state.plan.styleMode) {
                    FontStyles.groups.forEach { group ->
                        Surface(onClick = { chooseStyle(group) }, enabled = !state.busy, shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(styleName(group), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                    Symbol(R.drawable.ic_chevron_right)
                                }
                                val combination = state.combinations.find { it.id == state.plan.styleCombinations[group] }
                                Text(combination?.name?.ifBlank { stringResource(R.string.font_combination) } ?: stringResource(R.string.system_default),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    Surface(onClick = { chooseStyle("single") }, enabled = !state.busy, shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer) {
                        val combination = state.combinations.find { it.id == state.plan.combinationId }
                        ListItem(headlineContent = { Text(combination?.name?.ifBlank { stringResource(R.string.font_combination) } ?: stringResource(R.string.system_default)) },
                            supportingContent = combination?.let { { Text(combinationSummary(it, state)) } },
                            trailingContent = { Symbol(R.drawable.ic_chevron_right) }, colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
                    }
                    Box(Modifier.clickable(enabled = !state.busy, onClick = targets)) {
                        SettingRow(R.drawable.ic_tune, stringResource(R.string.action_replacement_scope),
                            if (state.plan.global || (device != null && state.plan.targets == device.targets.toSet())) stringResource(R.string.scope_global)
                            else state.plan.targets.take(3).joinToString().let { if (state.plan.targets.size > 3) "$it …" else it }.ifEmpty { stringResource(R.string.targets_not_selected) })
                    }
                }
            }
        }
        Text(stringResource(R.string.apply_reboot_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val configured = FontStyles.configured(resolved)
        Button(shapes = ButtonDefaults.shapes(), onClick = vm::build, enabled = configured && !state.busy && device?.conflicts.isNullOrEmpty(), modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Symbol(R.drawable.ic_layers); Spacer(Modifier.width(10.dp)); Text(if (state.module != null) stringResource(R.string.action_regenerate_module) else stringResource(R.string.action_generate_module), style = MaterialTheme.typography.titleMedium)
        }
        state.module?.let { module ->
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.module_generated), style = MaterialTheme.typography.titleMedium)
                    Text(bytes(module.file.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(shapes = ButtonDefaults.shapes(), onClick = { confirm("install") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.action_install)) }
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = export, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Symbol(R.drawable.ic_download); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_save_zip))
                    }
                }
            }
        }
        if (state.installedNow || device?.pending == true) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.device_waiting_reboot), style = MaterialTheme.typography.titleMedium)
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { confirm("reboot") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Symbol(R.drawable.ic_restart_alt); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_reboot_device)) }
                }
            }
        }
        if ((device?.installed == true && !device.disabled) || state.recoveryAvailable) OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { confirm("disable") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.action_restore_system_fonts)) }
        if (device?.disabled == true) Text(stringResource(R.string.module_disabled), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.log.isNotBlank()) {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { showLog = !showLog }) { Text(if (showLog) stringResource(R.string.action_collapse_log) else stringResource(R.string.action_view_log)) }
            if (showLog) Text(state.log, style = MaterialTheme.typography.bodySmall, modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()))
        }
    }
    Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
        Button(shapes = ButtonDefaults.shapes(), onClick = { confirm("global") },
            enabled = FontStyles.configured(resolved) && !state.busy && device?.conflicts.isNullOrEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(64.dp)) {
            Symbol(R.drawable.ic_font_download); Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.action_apply_configured), style = MaterialTheme.typography.titleMedium)
        }
    }
    }
}
