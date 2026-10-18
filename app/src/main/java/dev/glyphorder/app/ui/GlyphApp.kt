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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
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
private fun styleName(group: String): String = stringResource(when (group) {
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
    var licenseId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm) {
        // Request su only once the activity is visibly resumed and library loading has finished.
        lifecycle.currentStateFlow.first { it == Lifecycle.State.RESUMED }
        vm.state.first { !it.busy }
        if (vm.state.value.device == null) vm.inspect(announce = false)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.import(it) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { if (it != null) vm.export(it) }
    LaunchedEffect(state.message, state.error) {
        (state.error ?: state.message)?.let { snackbar.showSnackbar(it); vm.clearNotice() }
    }
    val navigateBack = { route = when (route) { "legal-document" -> "licenses"; "licenses", "privacy" -> "about"; else -> "" } }
    BackHandler(route.isNotEmpty(), onBack = navigateBack)
    val titles = listOf(stringResource(R.string.tab_fonts), stringResource(R.string.tab_preview), stringResource(R.string.tab_apply))
    Scaffold(
        topBar = {
            MediumTopAppBar(title = { Text(when (route) { "about" -> stringResource(R.string.title_about); "licenses" -> stringResource(R.string.title_open_source); "privacy" -> stringResource(R.string.title_privacy); "legal-document" -> legalTitle; "fallback" -> stringResource(R.string.title_fallback); "targets" -> stringResource(R.string.title_targets); else -> titles[tab] }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { if (route.isNotEmpty()) IconButton(onClick = navigateBack) { Symbol(R.drawable.ic_arrow_back, stringResource(R.string.accessibility_back)) } },
                actions = {
                    if (route.isEmpty()) IconButton(onClick = { route = "about" }) { Symbol(R.drawable.ic_info, stringResource(R.string.title_about)) }
                    IconButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        })
                    }) { Symbol(R.drawable.ic_language, stringResource(R.string.accessibility_change_language)) }
                    if (tab == 0 && route.isEmpty()) IconButton(enabled = !state.busy, onClick = { import.launch(arrayOf("*/*")) }) { Symbol(R.drawable.ic_add, stringResource(R.string.accessibility_import_font)) }
                    if (tab == 2 && route.isEmpty()) IconButton(enabled = !state.busy, onClick = vm::inspect) { Symbol(R.drawable.ic_refresh, stringResource(R.string.accessibility_inspect_device)) }
                })
        },
        bottomBar = {
            if (route.isEmpty()) NavigationBar {
                listOf(R.drawable.ic_font_download, R.drawable.ic_text_fields, R.drawable.ic_layers).forEachIndexed { index, icon ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Symbol(icon) }, label = { Text(titles[index]) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(targetState = if (route.isNotEmpty()) route else "tab$tab", label = "page", modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) { page ->
                when (page) {
                    "about" -> AboutPage({ route = it }, { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL))) })
                    "licenses" -> LicensesPage { title, assets -> legalTitle = title; legalAssets = ArrayList(assets); route = "legal-document" }
                    "privacy" -> LegalTextPage(listOf("legal/project/PRIVACY.md"))
                    "legal-document" -> LegalTextPage(legalAssets)
                    "fallback" -> FallbackPage(state, vm, { picker = it })
                    "targets" -> TargetsPage(state, vm)
                    "tab0" -> FontsPage(state, vm, { import.launch(arrayOf("*/*")) }, { picker = "primary" }, { route = "fallback" }, { detailId = it })
                    "tab1" -> PreviewPage(state, vm)
                    else -> ApplyPage(state, vm, { route = "targets" }, { picker = "style:$it" }, { confirm = it }, { export.launch("glyphorder-fonts.zip") })
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
        val selected = when { key == "primary" -> state.plan.primary; key == "universal" -> null
            key.startsWith("style:") -> state.plan.styleFonts[key.removePrefix("style:")]; else -> state.plan.fallbacks[key] }
        val scriptLocale = ULocale.forLocale(ComposeLocale.current.platformLocale)
        FontPicker(if (key.startsWith("style:")) styleName(key.removePrefix("style:")) else if (key == "primary") stringResource(R.string.title_primary_font_picker) else if (key == "universal") stringResource(R.string.title_add_fallback_font) else ScriptCatalog.name(key, scriptLocale), state, vm,
            selected, { picker = null }, { id ->
                when { key == "primary" -> vm.primary(id); key == "universal" -> if (id != null) vm.universal(id)
                    key.startsWith("style:") -> vm.styleFont(key.removePrefix("style:"), id); else -> vm.fallback(key, id) }
                picker = null
            }, excluded = if (key == "universal") state.plan.universal.toSet() else emptySet(), showSystem = key != "universal", showPresetDefault = key != "primary" && !key.startsWith("style:"))
    }
    detailId?.let { id -> state.fonts.find { it.id == id }?.let { font ->
        FontDetail(font, state, vm, { detailId = null }, { confirm = "delete:$id" }, { detailId = null; licenseId = id })
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
        val title = when { action == "global" -> stringResource(R.string.action_apply_configured); action == "install" -> stringResource(R.string.dialog_install_title); action == "disable" -> stringResource(R.string.dialog_restore_title); action == "reboot" -> stringResource(R.string.dialog_reboot_title); else -> stringResource(R.string.dialog_remove_title) }
        val text = when {
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
                    else -> { state.fonts.find { it.id == action.removePrefix("delete:") }?.let(vm::remove); detailId = null }
                }; confirm = null
            }) { Text(when (action) { "global", "install" -> stringResource(R.string.action_install); "reboot" -> stringResource(R.string.action_reboot); "disable" -> stringResource(R.string.action_disable); else -> stringResource(R.string.action_remove) }) } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}

@Composable
private fun FontsPage(state: AppState, vm: AppViewModel, onImport: () -> Unit, onPrimary: () -> Unit, onFallback: () -> Unit, onDetail: (String) -> Unit) {
    val primary = state.fonts.find { it.id == state.plan.primary }
    val scripts = pluralStringResource(R.plurals.script_count, state.plan.fallbacks.size, state.plan.fallbacks.size)
    val universal = pluralStringResource(R.plurals.universal_fallback_count, state.plan.universal.size, state.plan.universal.size)
    val locale = ULocale.forLocale(ComposeLocale.current.platformLocale)
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Surface(onClick = onPrimary, enabled = !state.busy, shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.primary_font), style = MaterialTheme.typography.labelLarge)
                        Symbol(R.drawable.ic_chevron_right)
                    }
                    Spacer(Modifier.height(24.dp))
                    PreviewText(stringResource(R.string.preview_glyph_sample), state.fonts, state.plan.copy(fallbacks = emptyMap(), universal = emptyList()), vm.store.directory,
                        64f, 400, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                    Text(primary?.name ?: stringResource(R.string.system_default), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (primary != null) Text(if (primary.variable) stringResource(R.string.font_variable) else stringResource(R.string.font_static_weight, fontWeights(primary, state.fonts)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item {
            Surface(onClick = onFallback, enabled = !state.busy, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                SettingRow(R.drawable.ic_layers, stringResource(R.string.fallback_fonts), if (FontSelection.automatic(state.plan, state.fonts)) stringResource(R.string.automatic_fallback_summary) else if (state.plan.fallbacks.isEmpty() && state.plan.universal.isEmpty()) stringResource(R.string.system_default) else stringResource(R.string.fallback_summary, scripts, universal))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.font_library), modifier = Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.titleLarge)
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onImport, enabled = !state.busy) { Symbol(R.drawable.ic_add); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_import)) }
            }
        }
        if (state.fonts.isEmpty()) item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.font_library_empty), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.font_files_supported), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(shapes = ButtonDefaults.shapes(), onClick = onImport, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_choose_file)) }
                }
            }
        }
        items(state.fonts.filterNot { it.hidden }, key = { it.id }) { font ->
            Surface(onClick = { onDetail(font.id) }, enabled = !state.busy, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(font.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        if (font.id == state.plan.primary) Symbol(R.drawable.ic_check_circle, stringResource(R.string.font_selected_primary)) else Symbol(R.drawable.ic_chevron_right)
                    }
                    PreviewText(if ("Hani" in font.scripts) stringResource(R.string.preview_cjk_sample) else stringResource(R.string.preview_latin_sample), listOf(font), FontPlan(primary = font.id, axes = state.plan.axes), vm.store.directory, 26f, 400,
                        MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth())
                    Text(listOf(if (vm.store.isBundled(font)) stringResource(R.string.font_builtin) else "", if (font.variable) stringResource(R.string.font_variable_short) else fontWeights(font, state.fonts), fontScriptNames(font.scripts, locale), bytes(font.bytes)).filter { it.isNotEmpty() }.joinToString(" · "),
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
private fun FallbackPage(state: AppState, vm: AppViewModel, picker: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var configuredOnly by rememberSaveable { mutableStateOf(false) }
    val scriptLocale = ULocale.forLocale(ComposeLocale.current.platformLocale)
    val allScripts = remember(scriptLocale) { ScriptCatalog.all(scriptLocale) }
    val automatic = remember(state.plan, state.fonts) { FontSelection.routes(state.plan, state.fonts) }
    val configuredTags = state.plan.fallbacks.keys + automatic.keys
    val scripts = remember(query, configuredOnly, configuredTags, allScripts) {
        allScripts.filter { (query.isBlank() || (it.search + it.label + it.tag).contains(query.trim(), true)) && (!configuredOnly || it.tag in configuredTags) }
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ListItem(headlineContent = { Text(stringResource(R.string.automatic_fallback_title)) },
                supportingContent = { Text(stringResource(R.string.automatic_fallback_description)) },
                trailingContent = { Switch(checked = state.plan.automaticFallback, onCheckedChange = vm::automaticFallback, enabled = !state.busy) })
            TextButton(shapes = ButtonDefaults.shapes(), onClick = vm::defaultFallbacks, enabled = !state.busy) {
                Text(stringResource(R.string.action_reset_fallbacks))
            }
        }
        item {
            Text(stringResource(R.string.universal_fallback_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.universal_fallback_description), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        }
        items(state.plan.universal, key = { "u$it" }) { id ->
            val index = state.plan.universal.indexOf(id)
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.fonts.find { it.id == id }?.name ?: stringResource(R.string.font_removed), Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.bodyLarge)
                    IconButton(enabled = index > 0 && !state.busy, onClick = { vm.moveUniversal(index, -1) }) { Symbol(R.drawable.ic_arrow_upward, stringResource(R.string.action_move_up)) }
                    IconButton(enabled = index < state.plan.universal.lastIndex && !state.busy, onClick = { vm.moveUniversal(index, 1) }) { Symbol(R.drawable.ic_arrow_downward, stringResource(R.string.action_move_down)) }
                    IconButton(enabled = !state.busy, onClick = { vm.universal(id) }) { Symbol(R.drawable.ic_close, stringResource(R.string.action_remove_fallback)) }
                }
            }
        }
        item { OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { picker("universal") }, enabled = state.fonts.isNotEmpty() && !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Symbol(R.drawable.ic_add); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_add_font))
        } }
        item {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.script_fallback_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.script_fallback_description), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(stringResource(R.string.search_language_or_script)) }, leadingIcon = { Symbol(R.drawable.ic_search) }, shape = RoundedCornerShape(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FilterChip(selected = !configuredOnly, onClick = { configuredOnly = false }, label = { Text(stringResource(R.string.chip_all_scripts, allScripts.size)) })
                FilterChip(selected = configuredOnly, onClick = { configuredOnly = true }, label = { Text(stringResource(R.string.chip_configured_scripts, allScripts.count { it.tag in configuredTags })) })
            }
        }
        items(scripts, key = { it.tag }) { script ->
            Surface(onClick = { picker(script.tag) }, enabled = !state.busy, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(script.label, style = MaterialTheme.typography.titleMedium)
                        Text(state.fonts.find { it.id == state.plan.fallbacks[script.tag] }?.name
                            ?: automatic[script.tag]?.firstOrNull()?.name
                            ?: stringResource(R.string.system_default), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(script.tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp)); Symbol(R.drawable.ic_chevron_right)
                }
            }
        }
        if (scripts.isEmpty()) item { Text(stringResource(R.string.no_matching_scripts), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text(stringResource(R.string.unspecified_script_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp)) }
    }
}

@Composable
private fun FontPicker(title: String, state: AppState, vm: AppViewModel, selected: String?, dismiss: () -> Unit,
                       choose: (String?) -> Unit, excluded: Set<String> = emptySet(), showSystem: Boolean = true, showPresetDefault: Boolean = false) {
    ModalBottomSheet(onDismissRequest = dismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp)) }
            if (showSystem) item {
                Surface(onClick = { choose(null) }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (showPresetDefault && FontSelection.automatic(state.plan, state.fonts))
                            stringResource(R.string.automatic_fallback_summary) else stringResource(R.string.system_default), Modifier.weight(1f)); if (selected == null) Symbol(R.drawable.ic_check)
                    }
                }
            }
            items(state.fonts.filter { !it.hidden && it.id !in excluded }, key = { it.id }) { font ->
                Surface(onClick = { choose(font.id) }, shape = RoundedCornerShape(20.dp), color = if (selected == font.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(font.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            if (selected == font.id) Symbol(R.drawable.ic_check)
                        }
                        PreviewText(stringResource(R.string.preview_glyphs_sample), state.fonts, state.plan.copy(primary = font.id, styleMode = false, fallbacks = emptyMap(), universal = emptyList()), vm.store.directory, 24f, 400,
                            MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth())
                        val locale = ULocale.forLocale(ComposeLocale.current.platformLocale)
                        Text("${if (font.variable) stringResource(R.string.font_variable_short) else stringResource(R.string.font_static_short)} · ${fontScriptNames(font.scripts, locale)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.fonts.isEmpty()) item { Text(stringResource(R.string.font_picker_empty), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun FontDetail(font: FontInfo, state: AppState, vm: AppViewModel, dismiss: () -> Unit, remove: () -> Unit, viewLicense: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(font.name, style = MaterialTheme.typography.headlineSmall)
            Text("${bytes(font.bytes)} · ${font.postScriptName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.plan.automaticFallback) Text(stringResource(R.string.automatic_fallback_description),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PreviewText(stringResource(R.string.preview_glyphs_sample), state.fonts, state.plan.copy(primary = font.id, styleMode = false, fallbacks = emptyMap(), universal = emptyList()), vm.store.directory, 40f,
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
            Button(shapes = ButtonDefaults.shapes(), onClick = { vm.primary(font.id); dismiss() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.action_set_primary_font)) }
            if (vm.store.isBundled(font)) TextButton(shapes = ButtonDefaults.shapes(), onClick = viewLicense, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_view_license)) }
            else TextButton(shapes = ButtonDefaults.shapes(), onClick = remove, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Symbol(R.drawable.ic_delete); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_remove_font)) }
        }
    }
}

@Composable
private fun PreviewPage(state: AppState, vm: AppViewModel) {
    val defaultPreviewText = PreviewSamples.all.first().text
    var text by rememberSaveable { mutableStateOf(defaultPreviewText) }
    var size by rememberSaveable { mutableFloatStateOf(30f) }
    var weight by rememberSaveable { mutableFloatStateOf(400f) }
    var language by rememberSaveable { mutableStateOf("und") }
    var previewStyle by rememberSaveable { mutableStateOf("sans") }
    val sizeDescription = stringResource(R.string.preview_size)
    val weightDescription = stringResource(R.string.preview_weight)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (state.plan.styleMode) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontStyles.groups.forEach { group -> FilterChip(selected = previewStyle == group,
                onClick = { previewStyle = group }, label = { Text(styleName(group)) }) }
        }
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Box(Modifier.fillMaxWidth().heightIn(min = 260.dp).padding(24.dp)) {
                PreviewText(text.ifEmpty { " " }, state.fonts, FontStyles.preview(state.plan, previewStyle), vm.store.directory, size, weight.toInt(), MaterialTheme.colorScheme.onSurface,
                    Modifier.fillMaxWidth(), language, systemFamily = if (state.plan.styleMode) FontStyles.systemFamily(previewStyle) else "sans-serif")
            }
        }
        val sampleLocale = ULocale.forLocale(ComposeLocale.current.platformLocale)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            PreviewSamples.all.forEach { sample ->
                val label = sample.label(sampleLocale).ifBlank { stringResource(R.string.preview_sample_mixed) }
                FilterChip(selected = language == sample.language, onClick = {
                    language = sample.language
                    text = sample.text
                }, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.preview_size)); Text(stringResource(R.string.preview_size_value, formattedNumber(size.toInt()))) }
                Slider(size, { size = it }, valueRange = 16f..64f, modifier = Modifier.semantics { contentDescription = sizeDescription })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.preview_weight)); Text(formattedNumber(weight.toInt())) }
                Slider(weight, { weight = it }, valueRange = 100f..900f, steps = 7, modifier = Modifier.semantics { contentDescription = weightDescription })
            }
        }
        OutlinedTextField(text, { text = it.take(4096) }, label = { Text(stringResource(R.string.preview_text_label)) }, modifier = Modifier.fillMaxWidth(), minLines = 3,
            shape = RoundedCornerShape(24.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        Text(stringResource(R.string.preview_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun ApplyPage(state: AppState, vm: AppViewModel, targets: () -> Unit, chooseStyle: (String) -> Unit, confirm: (String) -> Unit, export: () -> Unit) {
    var showLog by rememberSaveable { mutableStateOf(false) }
    val device = state.device
    val scripts = pluralStringResource(R.plurals.script_count, state.plan.fallbacks.size, state.plan.fallbacks.size)
    val universal = pluralStringResource(R.plurals.universal_fallback_count, state.plan.universal.size, state.plan.universal.size)
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Symbol(if (device != null) R.drawable.ic_check_circle else R.drawable.ic_info)
                    Text(if (device != null) stringResource(R.string.device_ready) else stringResource(R.string.device_check), style = MaterialTheme.typography.titleLarge)
                }
                if (device != null) {
                    val androidVersion = when (device.sdk) { 36 -> 16; 35 -> 15; else -> 14 }
                    Text(stringResource(R.string.device_version, formattedNumber(androidVersion), device.magisk.substringBefore(':')), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.device_current_family, "sans-serif", device.currentFamily), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(stringResource(R.string.device_root_required), style = MaterialTheme.typography.bodyMedium)
                    Button(shapes = ButtonDefaults.shapes(), onClick = vm::inspect, enabled = !state.busy) { Text(stringResource(R.string.action_check)) }
                }
            }
        }
        if (device?.conflicts?.isNotEmpty() == true) Text(stringResource(R.string.device_conflicts, device.conflicts.joinToString()), color = MaterialTheme.colorScheme.error)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.application_mode), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !state.plan.styleMode, onClick = { vm.styleMode(false) }, enabled = !state.busy,
                        label = { Text(stringResource(R.string.mode_single)) })
                    FilterChip(selected = state.plan.styleMode, onClick = { vm.styleMode(true) }, enabled = !state.busy,
                        label = { Text(stringResource(R.string.mode_styles)) })
                }
                if (state.plan.styleMode) {
                    Text(stringResource(R.string.style_mix_description), style = MaterialTheme.typography.bodySmall)
                    FontStyles.groups.forEach { group ->
                        Surface(onClick = { chooseStyle(group) }, enabled = !state.busy, shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(styleName(group), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                    Symbol(R.drawable.ic_chevron_right)
                                }
                                Text(state.fonts.find { it.id == state.plan.styleFonts[group] }?.name ?: stringResource(R.string.system_default),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                device?.targets?.filter { FontStyles.group(it) == group }?.let { names ->
                                    Text(names.take(3).joinToString().let { if (names.size > 3) "$it …" else it }.ifEmpty { stringResource(R.string.style_unavailable) },
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.clickable(enabled = !state.busy, onClick = targets)) {
                        SettingRow(R.drawable.ic_tune, stringResource(R.string.action_replacement_scope),
                            if (state.plan.global || (device != null && state.plan.targets == device.targets.toSet())) stringResource(R.string.scope_global)
                            else state.plan.targets.take(3).joinToString().let { if (state.plan.targets.size > 3) "$it …" else it }.ifEmpty { stringResource(R.string.targets_not_selected) })
                    }
                }
            }
        }
        Text(stringResource(R.string.apply_configured_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth()) {
                SummaryRow(stringResource(R.string.summary_primary_font), if (state.plan.styleMode) stringResource(R.string.mode_styles) else state.fonts.find { it.id == state.plan.primary }?.name ?: stringResource(R.string.system_default))
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SummaryRow(stringResource(R.string.summary_fallback_fonts), if (FontSelection.automatic(state.plan, state.fonts))
                    stringResource(R.string.automatic_fallback_summary) else stringResource(R.string.fallback_summary, scripts, universal))
                if (!state.plan.styleMode && state.plan.primary != null && device != null) {
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.clickable(enabled = !state.busy, onClick = targets)) { SettingRow(R.drawable.ic_tune, stringResource(R.string.action_replacement_scope), if (state.plan.global || state.plan.targets == device.targets.toSet()) stringResource(R.string.scope_global) else state.plan.targets.take(3).joinToString().let { if (state.plan.targets.size > 3) "$it …" else it }.ifBlank { stringResource(R.string.targets_not_selected) }) }
                }
            }
        }
        Text(stringResource(R.string.apply_reboot_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val configured = FontStyles.configured(state.plan)
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
            if (showLog) Text(state.log, style = MaterialTheme.typography.bodySmall, modifier = Modifier.animateContentSize())
        }
        Text(stringResource(R.string.recovery_instructions, stringResource(R.string.recovery_shell_command)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
        Button(shapes = ButtonDefaults.shapes(), onClick = { confirm("global") },
            enabled = FontStyles.configured(state.plan) && !state.busy && device?.conflicts.isNullOrEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(64.dp)) {
            Symbol(R.drawable.ic_font_download); Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.action_apply_configured), style = MaterialTheme.typography.titleMedium)
        }
    }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(60.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
    }
}
