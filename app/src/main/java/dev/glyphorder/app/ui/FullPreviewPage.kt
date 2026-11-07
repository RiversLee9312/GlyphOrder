@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package dev.glyphorder.app.ui

import android.icu.util.ULocale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.glyphorder.app.AppState
import dev.glyphorder.app.AppViewModel
import dev.glyphorder.app.R
import dev.glyphorder.app.font.FontPlan
import dev.glyphorder.app.font.FontStyles
import java.text.NumberFormat

@Composable
internal fun previewLanguage(): String {
    val locale = ULocale.addLikelySubtags(ULocale.forLocale(ComposeLocale.current.platformLocale))
    return if (locale.language == "zh") "zh-${locale.script}" else locale.language
}

@Composable
internal fun FullPreviewPage(identity: String, state: AppState, vm: AppViewModel, plan: FontPlan, composition: Boolean) {
    val initialText = stringResource(R.string.preview_default_text)
    val initialLanguage = previewLanguage()
    var text by rememberSaveable(identity) { mutableStateOf(initialText) }
    var size by rememberSaveable(identity) { mutableFloatStateOf(20f) }
    var weight by rememberSaveable(identity) { mutableFloatStateOf(400f) }
    var language by rememberSaveable(identity) { mutableStateOf(initialLanguage) }
    var selectedSample by rememberSaveable(identity) { mutableStateOf<String?>(null) }
    var style by rememberSaveable(identity) { mutableStateOf("sans") }
    val locale = ULocale.forLocale(ComposeLocale.current.platformLocale)
    val number = NumberFormat.getIntegerInstance(ComposeLocale.current.platformLocale)
    val sizeLabel = stringResource(R.string.preview_size)
    val weightLabel = stringResource(R.string.preview_weight)
    val resolved = if (composition) FontStyles.preview(plan, style) else plan
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (composition) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontStyles.groups.forEach { group ->
                FilterChip(selected = style == group, onClick = { style = group }, label = { Text(styleName(group)) })
            }
        }
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Box(Modifier.fillMaxWidth().heightIn(min = 260.dp).padding(24.dp)) {
                PreviewText(text.ifEmpty { " " }, state.fonts, resolved, vm.store.directory, size, weight.toInt(),
                    MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth(), language,
                    systemFamily = if (composition) FontStyles.systemFamily(style) else "sans-serif")
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewSamples.all.forEach { sample ->
                FilterChip(selected = selectedSample == sample.key, onClick = {
                    selectedSample = sample.key; language = sample.language; text = sample.text
                }, label = { Text(sample.label(locale).ifBlank { stringResource(R.string.preview_sample_mixed) }, maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(sizeLabel); Text(stringResource(R.string.preview_size_value, number.format(size.toInt())))
                }
                Slider(size, { size = it }, valueRange = 16f..64f, modifier = Modifier.semantics { contentDescription = sizeLabel })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(weightLabel); Text(number.format(weight.toInt())) }
                Slider(weight, { weight = it }, valueRange = 100f..900f, steps = 7, modifier = Modifier.semantics { contentDescription = weightLabel })
            }
        }
        OutlinedTextField(text, { text = it.take(4096); selectedSample = null }, label = { Text(stringResource(R.string.preview_text_label)) },
            modifier = Modifier.fillMaxWidth(), minLines = 3, shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
    }
}
