package dev.glyphorder.app.font

/** Groups actual named families. Android aliases continue to reference their original family. */
object FontStyles {
    val groups = listOf("sans", "serif", "mono", "google", "casual", "cursive", "other")

    fun chain(plan: FontPlan, group: String): List<String> =
        plan.styleChains[group] ?: listOfNotNull(plan.styleFonts[group])

    fun explicit(plan: FontPlan) = plan.styleMode && plan.styleChains.isNotEmpty()

    fun group(name: String): String = when {
        name.startsWith("casual", true) -> "casual"
        name.startsWith("cursive", true) -> "cursive"
        name.contains("google-sans", true) || name.startsWith("variable-", true) || name.startsWith("product-sans", true) -> "google"
        name.contains("mono", true) -> "mono"
        name.contains("sans", true) || name.startsWith("roboto", true) -> "sans"
        name.contains("serif", true) -> "serif"
        else -> "other"
    }

    fun assignments(plan: FontPlan, targets: Collection<String>): Map<String, String> =
        if (plan.styleMode) targets.mapNotNull { target -> chain(plan, group(target)).firstOrNull()?.let { target to it } }.toMap()
        else plan.primary?.let { id -> targets.filter { plan.global || it in plan.targets }.associateWith { id } }.orEmpty()

    fun configured(plan: FontPlan) = if (explicit(plan)) groups.any { chain(plan, it).isNotEmpty() }
        else (if (plan.styleMode) plan.styleFonts.isNotEmpty() else plan.primary != null) ||
            plan.fallbacks.isNotEmpty() || plan.universal.isNotEmpty()

    fun preview(plan: FontPlan, group: String): FontPlan = if (explicit(plan)) {
        val ids = chain(plan, group)
        plan.copy(primary = ids.firstOrNull(), orderedFallbacks = ids.drop(1), styleMode = false,
            automaticFallback = false, fallbacks = emptyMap(), universal = emptyList(), styleChains = emptyMap())
    } else plan.copy(primary = if (plan.styleMode) plan.styleFonts[group] else plan.primary, styleMode = false)

    fun systemFamily(group: String): String = when (group) {
        "serif" -> "serif"; "mono" -> "monospace"; "google" -> "google-sans"
        "casual" -> "casual"; "cursive" -> "cursive"; else -> "sans-serif"
    }
}
