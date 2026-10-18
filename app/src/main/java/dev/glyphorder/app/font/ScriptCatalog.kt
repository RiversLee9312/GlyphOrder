package dev.glyphorder.app.font

import android.icu.lang.UScript
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.icu.text.Collator
import android.icu.util.ULocale

data class ScriptChoice(val tag: String, val label: String, val search: String)

/** ISO 15924 scripts from this Android release's ICU, including supplementary-plane scripts. */
object ScriptCatalog {
    /** Kept as a property for callers that use the platform's current default locale. */
    val all: List<ScriptChoice> get() = all(ULocale.getDefault())

    fun all(locale: ULocale): List<ScriptChoice> {
        val languageTags = listOf("zh-Hans", "zh-Hant", "ja", "ko").map { tag ->
            val language = ULocale(tag)
            val name = language.getDisplayName(locale).ifBlank { tag }
            val english = language.getDisplayName(ULocale.ENGLISH).ifBlank { tag }
            ScriptChoice(tag, name, "$name $english $tag CJK")
        }
        val scripts = (0..UCharacter.getIntPropertyMaxValue(UProperty.SCRIPT)).mapNotNull { code ->
            runCatching {
                val short = UScript.getShortName(code)
                if (short in setOf("Zyyy", "Zinh", "Zzzz", "Zsym", "Zsye") || !short.matches(Regex("[A-Z][a-z]{3}"))) null
                else {
                    val script = ULocale("und_$short")
                    val display = script.getDisplayScript(locale).ifBlank { UScript.getName(code) }
                    val english = script.getDisplayScript(ULocale.ENGLISH).ifBlank { UScript.getName(code) }
                    ScriptChoice("und-$short", display, "$display $english ${UScript.getName(code)} $short und-$short")
                }
            }.getOrNull()
        }
        val extra = listOf("Zsym", "Zmth").map { short ->
            val script = ULocale("und_$short")
            val display = script.getDisplayScript(locale).ifBlank { script.getDisplayName(locale) }
            ScriptChoice("und-$short", display, "$display ${script.getDisplayScript(ULocale.ENGLISH)} $short und-$short")
        }
        val collator = Collator.getInstance(locale)
        return (languageTags + scripts + extra).distinctBy { it.tag }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun name(tag: String): String = name(tag, ULocale.getDefault())
    fun name(tag: String, locale: ULocale): String = all(locale).find { it.tag == tag }?.label ?: tag
}
