package dev.glyphorder.app.ui

import android.graphics.Typeface
import android.graphics.Paint
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.icu.lang.UScript
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.icu.text.BreakIterator
import android.os.LocaleList
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.widget.TextView
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import dev.glyphorder.app.font.FontInfo
import dev.glyphorder.app.font.FontPlan
import dev.glyphorder.app.font.FontSelection
import java.io.File
import java.util.Locale

fun fontFamily(info: FontInfo, directory: File, axes: Map<String, Float>, weight: Int, fonts: List<FontInfo> = listOf(info)): FontFamily {
    fun build(face: FontInfo): Font {
        val variation = face.axes.joinToString(",") { axis ->
            val v = (if (axis.tag == "wght") weight.toFloat() else axes[axis.tag] ?: axis.default).coerceIn(axis.min, axis.max)
            "'${axis.tag}' $v"
        }
        return Font.Builder(face.file(directory)).apply {
            if (variation.isNotEmpty()) setFontVariationSettings(variation)
            setWeight(if (face.axes.any { it.tag == "wght" }) weight.coerceIn(1, 1000) else face.weight)
            setSlant(if (face.italic) FontStyle.FONT_SLANT_ITALIC else FontStyle.FONT_SLANT_UPRIGHT)
        }.build()
    }
    val faces = FontSelection.family(info, fonts)
    return FontFamily.Builder(build(faces.first())).apply { faces.drop(1).forEach { addFont(build(it)) } }.build()
}

/** Per-script typefaces keep unrelated glyphs in fallback fonts from taking over other scripts. */
fun previewSpans(text: String, fonts: List<FontInfo>, plan: FontPlan, directory: File, weight: Int, language: String,
                 systemFamily: String = "sans-serif"): SpannableString {
    val primary = fonts.find { it.id == plan.primary }
    val dependencies = FontSelection.dependencies(plan, fonts)
    val routes = FontSelection.routes(plan, fonts)
    val cache = mutableMapOf<String, Typeface>()
    val familyCache = mutableMapOf<String, FontFamily>()
    fun face(tag: String, codepoints: IntArray): Typeface {
        val fallback = plan.fallbacks[tag]?.let { id -> fonts.find { it.id == id } }
        val candidates = (routes[tag].orEmpty() + dependencies).distinctBy { it.id }
        val supplement = candidates.firstOrNull { FontSelection.covers(it, codepoints) }
        val selected = listOfNotNull(primary, fallback, supplement).distinctBy { it.id }
        return cache.getOrPut(selected.joinToString("|") { it.id }) {
            if (selected.isEmpty()) Typeface.create(Typeface.create(systemFamily, Typeface.NORMAL), weight, false)
            else {
                val families = selected.map { info -> familyCache.getOrPut(info.id) {
                    fontFamily(info, directory, plan.axes[info.id].orEmpty(), weight, fonts)
                } }
                Typeface.CustomFallbackBuilder(families.first()).apply {
                    families.drop(1).forEach(::addCustomFallback)
                    setSystemFallback(systemFamily)
                    setStyle(FontStyle(weight, FontStyle.FONT_SLANT_UPRIGHT))
                }.build()
            }
        }
    }
    val universal = plan.universal.mapNotNull { id -> fonts.find { it.id == id } }
        .map { info -> Typeface.CustomFallbackBuilder(fontFamily(info, directory, plan.axes[info.id].orEmpty(), weight, fonts)).build() }
    val result = SpannableString(text)
    val paint = Paint()
    val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val emojiFace = Typeface.create("emoji", Typeface.NORMAL)
    var index = boundaries.first()
    var end = boundaries.next()
    var runStart = index
    var runFace: Typeface? = null
    var lastScript: String? = null
    while (end != BreakIterator.DONE) {
        val cluster = text.substring(index, end)
        val codepoints = cluster.codePoints().toArray()
        val cp = codepoints.firstOrNull { UScript.getScript(it) !in setOf(UScript.COMMON, UScript.INHERITED, UScript.UNKNOWN) }
            ?: codepoints.first()
        val rawScript = UScript.getShortName(UScript.getScript(cp))
        val script = if (rawScript in setOf("Zyyy", "Zinh") && Character.getType(cp) != Character.MATH_SYMBOL.toInt())
            lastScript ?: rawScript else rawScript
        if (rawScript !in setOf("Zyyy", "Zinh", "Zzzz")) lastScript = rawScript
        val tag = when (script) {
            "Hani", "Hans", "Hant", "Bopo" -> if (language in setOf("zh-Hans", "zh-Hant", "ja", "ko")) language else "zh-Hans"
            "Hira", "Kana" -> "ja"
            "Hang" -> "ko"
            "Zyyy" -> if (Character.getType(cp) == Character.MATH_SYMBOL.toInt()) "und-Zmth" else "und-Zsym"
            else -> "und-$script"
        }
        val emoji = codepoints.any { UCharacter.hasBinaryProperty(it, UProperty.EMOJI_PRESENTATION) || it == 0xfe0f || it == 0x20e3 }
        var chosen = if (emoji) emojiFace else face(tag, codepoints)
        paint.typeface = chosen
        if (!emoji && !paint.hasGlyph(cluster)) {
            universal.firstOrNull { candidate -> paint.typeface = candidate; paint.hasGlyph(cluster) }?.let { chosen = it }
        }
        // One span per contiguous face run, never one per letter. Arabic joining,
        // Indic conjuncts and kerning need an uninterrupted shaping context.
        if (runFace !== chosen) {
            runFace?.let { result.setSpan(TypefaceSpan(it), runStart, index, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            runStart = index
            runFace = chosen
        }
        index = end
        end = boundaries.next()
    }
    runFace?.let { result.setSpan(TypefaceSpan(it), runStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    return result
}

@Composable
fun PreviewText(text: String, fonts: List<FontInfo>, plan: FontPlan, directory: File,
                size: Float, weight: Int, color: Color, modifier: Modifier = Modifier, language: String = "zh-Hans",
                systemFamily: String = "sans-serif") {
    val content = remember(text, fonts, plan, weight, language, systemFamily) {
        runCatching { previewSpans(text, fonts, plan, directory, weight, language, systemFamily) }.getOrElse { SpannableString(text) }
    }
    AndroidView(factory = { context -> TextView(context).apply {
        includeFontPadding = false
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        setLineSpacing(0f, 1.28f)
    } },
        modifier = modifier, update = { view ->
            view.text = content; view.textSize = size; view.setTextColor(color.toArgb())
            view.textLocales = LocaleList.forLanguageTags(language)
        })
}
