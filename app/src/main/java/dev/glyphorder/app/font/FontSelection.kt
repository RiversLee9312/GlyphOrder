package dev.glyphorder.app.font

/** Shared expansion for both native preview and generated system font configuration. */
object FontSelection {
    const val DEFAULT_FALLBACK_GROUP = "noto"
    fun forDevice(plan: FontPlan, targets: List<String>): FontPlan =
        if (plan.styleMode) plan.copy(targets = FontStyles.assignments(plan, targets).keys, global = false)
        else if (plan.global) plan.copy(targets = targets.toSet()) else plan

    fun family(info: FontInfo, fonts: List<FontInfo>): List<FontInfo> =
        (if (info.family == null) listOf(info) else fonts.filter { it.family == info.family })
            .sortedWith(compareBy<FontInfo> { it.weight }.thenBy { it.italic })

    fun automatic(plan: FontPlan, fonts: List<FontInfo>): Boolean =
        plan.automaticFallback && fonts.any { it.id == plan.primary || (plan.styleMode && it.id in plan.styleFonts.values) }

    fun defaultFallbacks(plan: FontPlan): FontPlan =
        plan.copy(automaticFallback = true, fallbacks = emptyMap(), universal = emptyList())

    fun dependencies(plan: FontPlan, fonts: List<FontInfo>): List<FontInfo> {
        if (!automatic(plan, fonts)) return emptyList()
        val primary = fonts.find { it.id == plan.primary }
        val preset = primary?.preset
        var covered = primary?.let { CmapCoverage.intersection(family(it, fonts).map(FontInfo::coverage)) }.orEmpty()
        val preferred = fonts.filter { it.hidden && it.fallbackGroup == preset && preset in setOf("noto-serif", "misans-global") }
            .distinctBy { it.family ?: it.id }.filter { font ->
                val faces = family(font, fonts)
                val possible = CmapCoverage.union(faces.map(FontInfo::coverage))
                if (possible.isNotEmpty() && CmapCoverage.contains(covered, possible)) false
                else {
                    covered = CmapCoverage.union(listOf(covered, CmapCoverage.intersection(faces.map(FontInfo::coverage))))
                    true
                }
            }
        return (preferred + fonts.filter {
            it.fallbackGroup in setOf(DEFAULT_FALLBACK_GROUP, "sarasa") && it.id != plan.primary
        }).distinctBy { it.family ?: it.id }
    }

    fun routes(plan: FontPlan, fonts: List<FontInfo>): Map<String, List<FontInfo>> {
        val dependencies = dependencies(plan, fonts)
        return dependencies.flatMap { it.fallbackTags }.distinct().associateWith { tag ->
            dependencies.filter { tag in it.fallbackTags }
        }
    }

    fun selected(plan: FontPlan, fonts: List<FontInfo>): List<FontInfo> {
        if (plan.styleMode) return plan.styleFonts.values.distinct().flatMap { id ->
            selected(plan.copy(primary = id, styleMode = false), fonts)
        }.plus((plan.fallbacks.values + plan.universal).mapNotNull { id -> fonts.find { it.id == id } }
            .flatMap { family(it, fonts) }).distinctBy { it.id }
        val ids = listOfNotNull(plan.primary) + plan.fallbacks.values + plan.universal
        val direct = ids.map { id -> requireNotNull(fonts.find { it.id == id }) }
        return (direct.flatMap { family(it, fonts) } + dependencies(plan, fonts).flatMap { family(it, fonts) }).distinctBy { it.id }
    }

    /** Metadata is generated from the unmodified font cmap, including supplementary planes. */
    fun covers(info: FontInfo, codepoints: IntArray): Boolean = codepoints.all { cp ->
        cp == 0x200c || cp == 0x200d || cp in 0xfe00..0xfe0f || cp in 0xe0100..0xe01ef ||
            info.coverage.any { cp in it }
    }
}
