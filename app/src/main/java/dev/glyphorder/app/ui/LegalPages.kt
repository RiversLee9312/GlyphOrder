package dev.glyphorder.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.glyphorder.app.BuildConfig
import dev.glyphorder.app.R
import org.json.JSONArray

internal const val PROJECT_URL = "https://github.com/RiversLee9312/GlyphOrder"

@Composable
internal fun AboutPage(navigate: (String) -> Unit, openSource: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
            Text("GlyphOrder · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyLarge)
        }
        item { LegalLink(stringResource(R.string.title_open_source), "Apache-2.0 · SIL OFL · MiSans", { navigate("licenses") }) }
        item { LegalLink(stringResource(R.string.title_privacy), stringResource(R.string.privacy_summary), { navigate("privacy") }) }
        item { LegalLink(stringResource(R.string.action_source_code), PROJECT_URL, openSource) }
    }
}

@Composable
private fun LegalLink(title: String, summary: String, click: () -> Unit) {
    OutlinedCard(onClick = click, modifier = Modifier.fillMaxWidth()) {
        ListItem(headlineContent = { Text(title) }, supportingContent = { Text(summary) },
            trailingContent = { Symbol(R.drawable.ic_chevron_right) })
    }
}

private data class LibraryNotice(val coordinate: String, val license: String, val assets: List<String>, val source: String)

@Composable
internal fun LicensesPage(open: (String, List<String>) -> Unit) {
    val context = LocalContext.current
    val libraries = remember {
        val array = JSONArray(context.assets.open("legal/libraries.json").bufferedReader().use { it.readText() })
        List(array.length()) { index -> array.getJSONObject(index).let { item ->
            LibraryNotice(item.getString("coordinate"), item.getString("license"),
                listOfNotNull(item.getString("licenseAsset"), item.optString("noticeAsset").takeIf { it.isNotEmpty() }), item.getString("source"))
        } }
    }
    val fontLicenses = remember {
        context.assets.list("licenses").orEmpty().filter { it.endsWith(".txt") }.sorted()
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.license_scope), style = MaterialTheme.typography.bodyLarge) }
        item { LegalLink("GlyphOrder · Apache-2.0", "Copyright 2026 RiversLee9312 and contributors") {
            open("GlyphOrder · Apache-2.0", listOf("legal/project/NOTICE", "legal/project/LICENSE"))
        } }
        item { LegalLink(stringResource(R.string.title_notices), "GlyphOrder") {
            open(context.getString(R.string.title_notices), listOf("legal/project/THIRD_PARTY_NOTICES.md"))
        } }
        item { Text(stringResource(R.string.title_font_licenses), style = MaterialTheme.typography.titleLarge) }
        item { Text(stringResource(R.string.misans_notice), style = MaterialTheme.typography.bodyMedium) }
        items(fontLicenses, key = { it }) { filename ->
            LegalLink(filename.removeSuffix(".txt"), if (filename.startsWith("MiSans")) "Xiaomi · MiSans" else "SIL Open Font License 1.1") {
                open(filename.removeSuffix(".txt"), listOf("licenses/$filename"))
            }
        }
        item { LegalLink("Material Symbols Rounded", "Google · Apache-2.0") {
            open("Material Symbols Rounded", listOf("legal/Material-Symbols.txt"))
        } }
        item { Text(stringResource(R.string.title_software_licenses), style = MaterialTheme.typography.titleLarge) }
        items(libraries, key = { it.coordinate }) { library ->
            LegalLink(library.coordinate, library.license) {
                open(library.coordinate, library.assets)
            }
        }
    }
}

@Composable
internal fun LegalTextPage(assets: List<String>) {
    val context = LocalContext.current
    val paragraphs = remember(assets) {
        assets.flatMap { asset ->
            context.assets.open(asset).bufferedReader().use { it.readText() }.replace("\r\n", "\n").split(Regex("\n[ \\t]*\n"))
        }
    }
    // Separate paragraphs avoid a huge single text layout for aggregated notices.
    SelectionContainer {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(paragraphs) { Text(it.trim(), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
