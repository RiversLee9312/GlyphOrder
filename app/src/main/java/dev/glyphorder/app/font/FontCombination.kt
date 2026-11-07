package dev.glyphorder.app.font

import java.util.UUID

data class FontCombination(val id: String, val name: String, val fonts: List<String>) {
    fun preview(plan: FontPlan, library: List<FontLibraryFamily> = emptyList(), faces: List<FontInfo> = emptyList()): FontPlan {
        val chain = FontLibrary.expand(fonts, library, faces)
        return plan.copy(primary = chain.firstOrNull(), orderedFallbacks = chain.drop(1),
        styleMode = false, styleChains = emptyMap(), automaticFallback = false,
        fallbacks = emptyMap(), universal = emptyList(), familyComponents = FontLibrary.components(library, faces))
    }
}

object CombinationPlan {
    fun resolve(plan: FontPlan, combinations: List<FontCombination>, library: List<FontLibraryFamily> = emptyList(), fonts: List<FontInfo> = emptyList()): FontPlan {
        val catalog = combinations.associateBy { it.id }
        if (!plan.styleMode) {
            val combination = plan.combinationId?.let(catalog::get)
            return (combination ?: FontCombination("", "", emptyList())).preview(plan, library, fonts)
        }
        val chains = FontStyles.groups.associateWith { group ->
            FontLibrary.expand(plan.styleCombinations[group]?.let(catalog::get)?.fonts.orEmpty(), library, fonts)
        }
        return plan.copy(styleChains = chains, familyComponents = FontLibrary.components(library, fonts), styleFonts = chains.mapNotNull { (group, ids) ->
            ids.firstOrNull()?.let { group to it }
        }.toMap(), automaticFallback = false, fallbacks = emptyMap(), universal = emptyList())
    }

    /** Keep existing choices as visible combinations. Never invent a fallback. */
    fun migrate(plan: FontPlan, fonts: List<FontInfo>): Pair<FontPlan, List<FontCombination>> {
        val combinations = mutableListOf<FontCombination>()
        fun retain(ids: List<String>): String? {
            val chain = ids.distinct()
            if (chain.isEmpty()) return null
            combinations.find { it.fonts == chain }?.let { return it.id }
            val id = UUID.nameUUIDFromBytes(chain.joinToString("\u0000").toByteArray()).toString()
            val name = chain.map { font -> fonts.find { it.id == font }?.name ?: font.take(12) }.joinToString(" + ")
            combinations += FontCombination(id, name, chain)
            return id
        }
        val single = retain(listOfNotNull(plan.primary) + plan.universal)
        val styles = FontStyles.groups.mapNotNull { group -> retain(FontStyles.chain(plan, group))?.let { group to it } }.toMap()
        return plan.copy(combinationId = single, styleCombinations = styles) to combinations
    }
}
