package dev.glyphorder.app.font

import org.junit.Assert.*
import org.junit.Test

class FontLibraryTest {
    private fun face(id: String, family: String? = null, weight: Int = 400) =
        FontInfo(id, "$id.ttf", id, id, weight, false, emptyList(), 100, emptySet(), family = family)

    @Test fun importedWeightsAreOneFamilyWithRegularAsItsRepresentative() {
        val regular = face("Regular", "Example")
        val bold = face("Bold", "Example", 700)
        val italic = face("Italic", "Example").copy(italic = true)
        val result = FontLibrary.defaults(listOf(bold, italic, regular)) { false }.single()
        assertEquals("Example", result.name)
        assertEquals("Regular", result.id)
        assertEquals(setOf("Regular", "Bold", "Italic"), result.members.toSet())
    }

    @Test fun aBuiltInFamilyContainsItsOwnScriptsWithoutSelectingAnotherDesign() {
        val sans = face("sans").copy(name = "Noto Sans", preset = "noto-sans")
        val arabic = face("arabic").copy(name = "NotoSansArabic", hidden = true, fallbackGroup = "noto")
        val khitan = face("khitan").copy(name = "NotoSerifKhitanSmallScript", hidden = true, fallbackGroup = "noto")
        val library = FontLibrary.defaults(listOf(sans, arabic, khitan)) { true }
        assertEquals(listOf("Noto Sans"), library.map { it.name })
        assertEquals(listOf("sans", "arabic"), library.single().members)
    }

    @Test fun scriptFacesStayInsideTheExplicitFamilyPosition() {
        val rubik = face("rubik")
        val mi = face("mi")
        val arabic = face("mi-ar").copy(fallbackTags = setOf("und-Arab"))
        val fonts = listOf(rubik, mi, arabic)
        val library = listOf(FontLibraryFamily("r", "R", listOf("rubik"), true),
            FontLibraryFamily("m", "M", listOf("mi", "mi-ar"), true))
        val plan = FontCombination("c", "C", listOf("r", "m")).preview(FontPlan(), library, fonts)
        assertEquals(listOf("rubik", "mi", "mi-ar"), FontSelection.orderedFaces(plan, fonts, "und-Arab").map { it.id })
        assertEquals(listOf("rubik", "mi"), FontSelection.orderedFaces(plan, fonts, "zh-Hans").map { it.id })
        assertEquals(setOf("rubik", "mi", "mi-ar"), FontSelection.selected(plan, fonts).map { it.id }.toSet())
        assertFalse(plan.automaticFallback)
    }

    @Test fun removingAnOriginalRepresentativeDoesNotBreakCombinationReferences() {
        val bold = face("bold", "Example", 700)
        val library = listOf(FontLibraryFamily("stable-family-id", "Example", listOf("bold"), false))
        val plan = FontCombination("c", "C", listOf("stable-family-id")).preview(FontPlan(), library, listOf(bold))
        assertEquals("bold", plan.primary)
    }

    @Test fun duplicateStylesAndMixedVariableStaticWeightsAreRejectedBeforeGeneration() {
        val regular = face("regular", "Example")
        assertThrows(IllegalArgumentException::class.java) { FontLibrary.validateFaces(listOf(regular, regular.copy(id = "duplicate"))) }
        val variable = regular.copy(axes = listOf(FontAxis("wght", 100f, 400f, 900f)))
        assertThrows(IllegalArgumentException::class.java) { FontLibrary.validateFaces(listOf(variable, face("bold", "Example", 700))) }
        FontLibrary.validateFaces(listOf(regular, face("bold", "Example", 700), regular.copy(id = "italic", italic = true)))
    }
}
