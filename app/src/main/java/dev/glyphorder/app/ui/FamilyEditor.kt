@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package dev.glyphorder.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.glyphorder.app.AppState
import dev.glyphorder.app.AppViewModel
import dev.glyphorder.app.R
import dev.glyphorder.app.font.FontCombination
import dev.glyphorder.app.font.FontLibraryFamily
import java.text.NumberFormat

@Composable
internal fun FamilyEditor(family: FontLibraryFamily, state: AppState, vm: AppViewModel,
                          face: (String) -> Unit, add: () -> Unit, remove: () -> Unit, preview: () -> Unit) {
    val initial = stringResource(R.string.preview_glyphs_sample)
    val plan = FontCombination("preview", "", listOf(family.id)).preview(state.plan, state.library, state.fonts)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(family.name, { vm.renameFamily(family.id, it) }, enabled = !state.busy,
                label = { Text(stringResource(R.string.combination_name)) }, singleLine = true,
                shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth())
        }
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PreviewText(initial, state.fonts, plan, vm.store.directory, 32f, 400,
                        MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth(), previewLanguage())
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = preview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Symbol(R.drawable.ic_text_fields); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.tab_preview))
                    }
                }
            }
        }
        item { Text(stringResource(R.string.font_family_faces), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        items(family.faces(state.fonts), key = { it.id }) { font ->
            Surface(onClick = { face(font.id) }, enabled = !state.busy, shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                ListItem(headlineContent = { Text(font.name) },
                    supportingContent = { Text(listOf(font.weight.toString(), stringResource(if (font.italic) R.string.face_italic else R.string.face_upright)).joinToString(" · ")) },
                    trailingContent = { Symbol(R.drawable.ic_chevron_right) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
            }
        }
        if (!family.bundled) {
            item {
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = add, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Symbol(R.drawable.ic_add); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_add_faces))
                }
            }
            item {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = remove, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_remove_family), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
