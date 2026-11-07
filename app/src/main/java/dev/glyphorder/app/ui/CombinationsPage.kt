@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package dev.glyphorder.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.glyphorder.app.AppState
import dev.glyphorder.app.R
import dev.glyphorder.app.font.FontCombination

internal fun combinationSummary(combination: FontCombination, state: AppState): String =
    combination.fonts.mapNotNull { id -> state.library.find { it.id == id }?.name }.joinToString(" → ")

@Composable
internal fun CombinationsPage(state: AppState, directory: java.io.File, create: () -> Unit, edit: (String) -> Unit) {
    val motion = MaterialTheme.motionScheme
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = create, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Symbol(R.drawable.ic_add); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_create_combination))
            }
        }
        items(state.combinations, key = { it.id }) { combination ->
            Surface(onClick = { edit(combination.id) }, enabled = !state.busy, shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.animateItem(fadeInSpec = motion.fastEffectsSpec(), placementSpec = motion.defaultSpatialSpec(), fadeOutSpec = motion.fastEffectsSpec())) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(combination.name.ifBlank { stringResource(R.string.font_combination) }, Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Symbol(R.drawable.ic_chevron_right)
                    }
                    Text(combinationSummary(combination, state).ifEmpty { stringResource(R.string.system_default) },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PreviewText(stringResource(R.string.preview_glyphs_sample), state.fonts, combination.preview(state.plan, state.library, state.fonts),
                        directory, 24f, 400,
                        MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth(), previewLanguage())
                }
            }
        }
    }
}

@Composable
internal fun CombinationPicker(state: AppState, selected: String?, dismiss: () -> Unit, choose: (String?) -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.font_combination), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp)) }
            item {
                Surface(onClick = { choose(null) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    ListItem(headlineContent = { Text(stringResource(R.string.system_default)) }, trailingContent = {
                        if (selected == null) Symbol(R.drawable.ic_check)
                    }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
                }
            }
            items(state.combinations, key = { it.id }) { combination ->
                Surface(onClick = { choose(combination.id) }, enabled = combination.fonts.isNotEmpty(),
                    shape = MaterialTheme.shapes.large, color = if (selected == combination.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                    ListItem(headlineContent = { Text(combination.name.ifBlank { stringResource(R.string.font_combination) }) },
                        supportingContent = { Text(combinationSummary(combination, state).ifBlank { stringResource(R.string.system_default) }) },
                        trailingContent = { if (selected == combination.id) Symbol(R.drawable.ic_check) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
                }
            }
            if (state.combinations.isEmpty()) item { Text(stringResource(R.string.combinations_empty), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp)) }
        }
    }
}
