package dev.glyphorder.app.font

import org.junit.Assert.*
import org.junit.Test

class CombinationPlanTest {
    private fun font(id: String) = FontInfo(id, "$id.ttf", id, id, 400, false, emptyList(), 100, emptySet())
    private val fonts = listOf(font("Rubik"), font("MiSans"), font("Serif"), font("Mono"),
        font("hidden").copy(hidden = true, fallbackGroup = "noto", fallbackTags = setOf("und-Hani")))

    @Test fun freshInstallationHasNoPredefinedCombinationsOrAssignments() {
        val (plan, catalog) = CombinationPlan.migrate(FontPlan(), fonts)
        assertTrue(catalog.isEmpty())
        assertNull(plan.combinationId)
        assertTrue(plan.styleCombinations.isEmpty())
        assertNull(CombinationPlan.resolve(plan, catalog).primary)
    }

    @Test fun migrationRetainsExistingSelectionsWithoutCopyingGlobalFallbacksToEveryStyle() {
        val original = FontPlan(primary = "Rubik", universal = listOf("MiSans"), styleMode = true,
            styleFonts = mapOf("sans" to "Rubik", "serif" to "Serif"))
        val (plan, catalog) = CombinationPlan.migrate(original, fonts)
        assertEquals(listOf("Rubik", "MiSans"), catalog.single { it.id == plan.combinationId }.fonts)
        assertEquals(listOf("Rubik"), catalog.single { it.id == plan.styleCombinations["sans"] }.fonts)
        assertEquals(listOf("Serif"), catalog.single { it.id == plan.styleCombinations["serif"] }.fonts)
        assertNull(plan.styleCombinations["mono"])
        assertTrue(catalog.all { "hidden" !in it.fonts })
    }

    @Test fun singleModeAndPreviewUseTheSameExplicitOrder() {
        val combination = FontCombination("one", "My fonts", listOf("Rubik", "MiSans"))
        val raw = FontPlan(combinationId = "one", universal = listOf("hidden"), automaticFallback = true)
        val resolved = CombinationPlan.resolve(raw, listOf(combination))
        assertEquals(combination.preview(raw), resolved)
        assertEquals("Rubik", resolved.primary)
        assertEquals(listOf("MiSans"), resolved.orderedFallbacks)
        assertFalse(resolved.automaticFallback)
        assertTrue(resolved.universal.isEmpty())
        assertEquals(listOf("Rubik", "MiSans"), FontSelection.selected(resolved, fonts).map { it.id })
    }

    @Test fun editingAReusedCombinationUpdatesOnlyTheStylesThatSelectedIt() {
        val shared = FontCombination("shared", "Shared", listOf("Rubik", "MiSans"))
        val serif = FontCombination("serif", "Serif", listOf("Serif"))
        val plan = FontPlan(styleMode = true, styleCombinations = mapOf("sans" to "shared", "mono" to "shared", "serif" to "serif"))
        val resolved = CombinationPlan.resolve(plan, listOf(shared.copy(fonts = listOf("MiSans", "Rubik")), serif))
        assertEquals(listOf("MiSans", "Rubik"), FontStyles.chain(resolved, "sans"))
        assertEquals(listOf("MiSans", "Rubik"), FontStyles.chain(resolved, "mono"))
        assertEquals(listOf("Serif"), FontStyles.chain(resolved, "serif"))
        assertTrue(FontStyles.chain(resolved, "google").isEmpty())
        assertTrue(FontSelection.dependencies(FontStyles.preview(resolved, "sans"), fonts).isEmpty())
    }

    @Test fun missingOrEmptyCombinationsKeepSystemDefaultsInsteadOfChoosingAnotherFont() {
        val raw = FontPlan(styleMode = true, styleFonts = mapOf("sans" to "Rubik"), styleCombinations = mapOf("sans" to "gone"))
        val resolved = CombinationPlan.resolve(raw, emptyList())
        assertFalse(FontStyles.configured(resolved))
        assertTrue(FontStyles.assignments(resolved, listOf("sans-serif", "serif")).isEmpty())
        assertNull(CombinationPlan.resolve(raw.copy(styleMode = false), emptyList()).primary)
    }
}
