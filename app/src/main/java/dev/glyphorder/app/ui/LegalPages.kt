@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package dev.glyphorder.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.glyphorder.app.BuildConfig
import dev.glyphorder.app.R
import org.json.JSONArray

internal const val PROJECT_URL = "https://github.com/RiversLee9312/GlyphOrder"

@Composable
internal fun AboutPage(navigate: (String) -> Unit, openSource: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(72.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Text("GlyphOrder · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LegalLink(stringResource(R.string.title_open_source), index = 0, count = 3, click = { navigate("licenses") })
                LegalLink(stringResource(R.string.title_privacy), index = 1, count = 3, click = { navigate("privacy") })
                LegalLink(stringResource(R.string.action_source_code), "GitHub", index = 2, count = 3, click = openSource)
            }
        }
    }
}

@Composable
private fun LegalLink(title: String, summary: String? = null, index: Int = 0, count: Int = 1, click: () -> Unit) {
    SegmentedListItem(onClick = click, shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = Modifier.fillMaxWidth(),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Symbol(R.drawable.ic_chevron_right) }) { Text(title) }
}

private data class LibraryNotice(val coordinate: String, val license: String, val assets: List<String>)

@Composable
internal fun LicensesPage(open: (String, List<String>) -> Unit) {
    val context = LocalContext.current
    val libraries = remember {
        val array = JSONArray(context.assets.open("legal/libraries.json").bufferedReader().use { it.readText() })
        List(array.length()) { index -> array.getJSONObject(index).let { item ->
            LibraryNotice(item.getString("coordinate"), item.getString("license"),
                listOfNotNull(item.getString("licenseAsset"), item.optString("noticeAsset").takeIf { it.isNotEmpty() }))
        } }
    }
    val fontLicenses = remember {
        context.assets.list("licenses").orEmpty().filter { it.endsWith(".txt") }.sorted()
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            LegalLink("GlyphOrder", "Apache-2.0", index = 0, count = 2) {
                open("GlyphOrder · Apache-2.0", listOf("legal/project/NOTICE", "legal/project/LICENSE"))
            }
        }
        item {
            LegalLink(stringResource(R.string.title_notices), index = 1, count = 2) {
                open(context.getString(R.string.title_notices), listOf("legal/project/THIRD_PARTY_NOTICES.md"))
            }
        }
        item { LegalSection(stringResource(R.string.title_font_licenses)) }
        itemsIndexed(fontLicenses, key = { _, filename -> filename }) { index, filename ->
            val title = when (filename) {
                "MiSans-license.txt" -> "MiSans / MiSans Global"
                "Noto-OFL.txt" -> "Noto"
                "NotoCJK-OFL.txt" -> "Noto CJK"
                "NotoSerif-OFL.txt" -> "Noto Serif"
                "Rubik-OFL.txt" -> "Rubik"
                "Sarasa-OFL.txt" -> "Sarasa Fixed"
                else -> filename.removeSuffix(".txt")
            }
            LegalLink(title, if (filename.startsWith("MiSans")) stringResource(R.string.misans_notice) else "SIL Open Font License 1.1",
                index = index, count = fontLicenses.size) {
                open(title, listOf("licenses/$filename"))
            }
        }
        item { LegalSection(stringResource(R.string.title_artwork_licenses)) }
        item { LegalLink("Material Symbols Rounded", "Google · Apache-2.0") {
            open("Material Symbols Rounded", listOf("legal/Material-Symbols.txt"))
        } }
        item { LegalSection(stringResource(R.string.title_software_licenses)) }
        itemsIndexed(libraries, key = { _, library -> library.coordinate }) { index, library ->
            LegalLink(library.coordinate, library.license, index = index, count = libraries.size) {
                open(library.coordinate, library.assets)
            }
        }
    }
}

@Composable
private fun LegalSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
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
