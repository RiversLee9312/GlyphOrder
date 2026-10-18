package dev.glyphorder.app.font

import org.junit.Assert.*
import org.junit.Test

class FontSelectionTest {
    private val base = FontInfo("primary", "primary.ttf", "Imported", "Imported", 400, false, emptyList(), 100, emptySet())
    private val arabic = base.copy(id = "arabic", fileName = "arabic.ttf", hidden = true,
        fallbackGroup = "noto", fallbackTags = setOf("und-Arab"), coverage = listOf(0x600..0x6ff))
    private val cjk = arabic.copy(id = "cjk", fileName = "cjk.otf", fallbackTags = setOf("zh-Hans", "ja", "ko"))

    @Test fun styleAssignmentsClassifyLosClockAndMonoFamiliesBeforeSans() {
        val names = listOf("sans-serif", "roboto-flex", "source-sans-pro", "serif", "serif-monospace", "monospace", "google-sans-flex", "variable-title-medium", "cursive")
        val plan = FontSelection.forDevice(FontPlan(primary = "ignored", global = true, styleMode = true,
            styleFonts = mapOf("sans" to "a", "mono" to "m", "google" to "g")), names)
        assertFalse(plan.global)
        assertEquals(names.toSet() - setOf("serif", "cursive"), plan.targets)
        assertEquals("m", FontStyles.assignments(plan, names)["serif-monospace"])
        assertEquals("g", FontStyles.assignments(plan, names)["variable-title-medium"])
        assertNull(FontStyles.preview(plan, "serif").primary)
    }

    @Test fun casualAndCursiveAreIndependentFromEachOtherAndOtherFamilies() {
        val names = listOf("sans-serif", "casual", "cursive", "unknown-rom-family")
        assertEquals("casual", FontStyles.group("casual"))
        assertEquals("cursive", FontStyles.group("cursive"))
        val plan = FontSelection.forDevice(FontPlan(styleMode = true, styleFonts = mapOf("casual" to "a", "cursive" to "b")), names)
        assertEquals(mapOf("casual" to "a", "cursive" to "b"), FontStyles.assignments(plan, names))
        assertEquals(setOf("casual", "cursive"), plan.targets)
        assertEquals("a", FontStyles.preview(plan, "casual").primary)
        assertEquals("b", FontStyles.preview(plan, "cursive").primary)
        val cleared = plan.copy(styleFonts = plan.styleFonts - "casual")
        assertEquals(mapOf("cursive" to "b"), FontStyles.assignments(cleared, names))
        assertEquals(mapOf("unknown-rom-family" to "a"), FontStyles.assignments(plan.copy(styleFonts = mapOf("other" to "a")), names))
    }

    @Test fun serifSupplementsPrecedeSharedPoolAndAllWeightCompanionsArePackaged() {
        val primary = base.copy(preset = "noto-serif")
        val regular = arabic.copy(id = "serif-arabic", family = "serif-arabic", fallbackGroup = "noto-serif")
        val bold = regular.copy(id = "serif-arabic-bold", weight = 700)
        val plan = FontPlan(primary = primary.id)
        val fonts = listOf(primary, arabic, regular, bold)
        assertEquals(listOf(regular, arabic), FontSelection.dependencies(plan, fonts))
        assertTrue(bold in FontSelection.selected(plan, fonts))
        val mixed = FontPlan(styleMode = true, styleFonts = mapOf("serif" to primary.id))
        assertTrue(bold in FontSelection.selected(mixed, fonts))
        assertTrue(FontSelection.dependencies(plan.copy(automaticFallback = false), fonts).isEmpty())
    }

    @Test fun allBuiltinsAndAnImportReceiveTheSameDefaultPoolWithoutPresetMetadata() {
        listOf("MiSans", "Rubik", "Noto Serif", "Sarasa Fixed", "My own font").forEach { name ->
            val primary = base.copy(name = name)
            val fonts = listOf(primary, arabic, cjk)
            val plan = FontPlan(primary = primary.id)
            assertTrue(FontSelection.automatic(plan, fonts))
            assertEquals(listOf("arabic", "cjk"), FontSelection.dependencies(plan, fonts).map { it.id })
            assertEquals(setOf("und-Arab", "zh-Hans", "ja", "ko"), FontSelection.routes(plan, fonts).keys)
        }
    }

    @Test fun systemDefaultAndRemovedPrimaryNeverActivateThePool() {
        val fonts = listOf(base, arabic, cjk)
        assertTrue(FontSelection.dependencies(FontPlan(), fonts).isEmpty())
        assertTrue(FontSelection.dependencies(FontPlan(primary = "removed"), fonts).isEmpty())
    }

    @Test fun disablingAutomaticKeepsManualSelectionsAndFamilyCompanions() {
        val bold = base.copy(id = "bold", family = "custom", weight = 700)
        val primary = base.copy(family = "custom")
        val plan = FontPlan(primary = primary.id, automaticFallback = false,
            fallbacks = mapOf("und-Arab" to arabic.id), universal = listOf(cjk.id))
        val fonts = listOf(primary, bold, arabic, cjk)
        assertTrue(FontSelection.dependencies(plan, fonts).isEmpty())
        assertEquals(setOf(primary.id, bold.id, arabic.id, cjk.id), FontSelection.selected(plan, fonts).map { it.id }.toSet())
    }

    @Test fun restoringDefaultDoesNotChangePrimaryAxesOrGlobalScope() {
        val plan = FontPlan(primary = base.id, axes = mapOf(base.id to mapOf("wght" to 600f)), global = true,
            targets = setOf("serif"), automaticFallback = false, fallbacks = mapOf("ja" to cjk.id), universal = listOf(arabic.id))
        val reset = FontSelection.defaultFallbacks(plan)
        assertTrue(reset.automaticFallback); assertTrue(reset.fallbacks.isEmpty()); assertTrue(reset.universal.isEmpty())
        assertEquals(plan.primary, reset.primary); assertEquals(plan.axes, reset.axes)
        assertEquals(plan.targets, reset.targets); assertTrue(reset.global)
    }

    @Test fun poolDoesNotIncludePrimaryAgainAndAcceptsLegacyMetadata() {
        val legacy = arabic.copy(fallbackGroup = "sarasa")
        assertEquals(listOf(legacy), FontSelection.dependencies(FontPlan(primary = base.id), listOf(base, legacy)))
        assertTrue(FontSelection.dependencies(FontPlan(primary = arabic.id), listOf(arabic)).isEmpty())
    }
}
