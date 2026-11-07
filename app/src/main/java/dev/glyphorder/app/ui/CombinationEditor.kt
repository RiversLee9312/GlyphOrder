@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package dev.glyphorder.app.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.glyphorder.app.AppState
import dev.glyphorder.app.AppViewModel
import dev.glyphorder.app.R
import dev.glyphorder.app.font.FontCombination
import java.text.NumberFormat

@Composable
internal fun CombinationEditor(combination: FontCombination, state: AppState, vm: AppViewModel, choose: (Int) -> Unit, removeCombination: () -> Unit, preview: () -> Unit) {
    val group = combination.id
    val ids = combination.fonts
    val motion = MaterialTheme.motionScheme
    val up = stringResource(R.string.action_move_up)
    val down = stringResource(R.string.action_move_down)
    val remove = stringResource(R.string.action_remove)
    val reorder = stringResource(R.string.action_reorder)
    val initialText = stringResource(R.string.preview_glyphs_sample)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(combination.name, { vm.renameCombination(group, it) }, enabled = !state.busy,
                label = { Text(stringResource(R.string.combination_name)) }, singleLine = true,
                shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth())
        }
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PreviewText(initialText, state.fonts, combination.preview(state.plan, state.library, state.fonts), vm.store.directory,
                        32f, 400, MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth(), previewLanguage())
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = preview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Symbol(R.drawable.ic_text_fields); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.tab_preview))
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.chain_description), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
        }
        itemsIndexed(ids, key = { _, id -> id }) { index, id ->
            var menu by remember(id) { mutableStateOf(false) }
            var rowHeight by remember(id) { mutableIntStateOf(1) }
            var drag by remember(id) { mutableFloatStateOf(0f) }
            Surface(onClick = { choose(index) }, enabled = !state.busy,
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().onSizeChanged { rowHeight = it.height.coerceAtLeast(1) }
                    .animateItem(fadeInSpec = motion.fastEffectsSpec(), placementSpec = motion.defaultSpatialSpec(), fadeOutSpec = motion.fastEffectsSpec())
                    .semantics {
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction(up) { vm.moveCombinationFont(group, index, -1); true })
                            if (index < ids.lastIndex) add(CustomAccessibilityAction(down) { vm.moveCombinationFont(group, index, 1); true })
                            add(CustomAccessibilityAction(remove) { vm.removeCombinationFont(group, index); true })
                        }
                    }) {
                Row(Modifier.fillMaxWidth().heightIn(min = 80.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).semantics { contentDescription = reorder }
                        .pointerInput(group, id, state.busy) {
                            if (!state.busy) detectDragGesturesAfterLongPress(
                                onDragStart = { drag = 0f }, onDragEnd = { drag = 0f }, onDragCancel = { drag = 0f }
                            ) { change, amount ->
                                change.consume(); drag += amount.y
                                val current = vm.state.value.combinations.find { it.id == group }?.fonts.orEmpty()
                                val position = current.indexOf(id)
                                val direction = when { drag > rowHeight / 2f -> 1; drag < -rowHeight / 2f -> -1; else -> 0 }
                                if (direction != 0 && position + direction in current.indices) {
                                    vm.moveCombinationFont(group, position, direction); drag -= direction * rowHeight
                                }
                            }
                        }, contentAlignment = Alignment.Center) { Symbol(R.drawable.ic_drag_indicator) }
                    Text(NumberFormat.getIntegerInstance().format(index + 1), modifier = Modifier.width(28.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(state.library.find { it.id == id }?.name ?: stringResource(R.string.font_removed),
                        modifier = Modifier.weight(1f).padding(vertical = 16.dp), style = MaterialTheme.typography.titleMedium)
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !state.busy) { Symbol(R.drawable.ic_more_vert, reorder) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(up) }, enabled = index > 0,
                                leadingIcon = { Symbol(R.drawable.ic_arrow_upward) }, onClick = { menu = false; vm.moveCombinationFont(group, index, -1) })
                            DropdownMenuItem(text = { Text(down) }, enabled = index < ids.lastIndex,
                                leadingIcon = { Symbol(R.drawable.ic_arrow_downward) }, onClick = { menu = false; vm.moveCombinationFont(group, index, 1) })
                            DropdownMenuItem(text = { Text(remove) }, leadingIcon = { Symbol(R.drawable.ic_close) },
                                onClick = { menu = false; vm.removeCombinationFont(group, index) })
                        }
                    }
                }
            }
        }
        item {
            FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { choose(ids.size) }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 56.dp)) {
                Symbol(R.drawable.ic_add); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_add_font))
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                ListItem(headlineContent = { Text(stringResource(R.string.system_default)) },
                    leadingContent = { Symbol(R.drawable.ic_layers) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
            }
        }
        item { TextButton(shapes = ButtonDefaults.shapes(), onClick = removeCombination, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) } }
    }
}
