package dev.glyphorder.app.font

/** Groups actual named families. Android aliases continue to reference their original family. */
object FontStyles {
    val groups = listOf("sans", "serif", "mono", "google", "casual", "cursive", "other")

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
        if (plan.styleMode) targets.mapNotNull { target -> plan.styleFonts[group(target)]?.let { target to it } }.toMap()
        else plan.primary?.let { id -> targets.filter { plan.global || it in plan.targets }.associateWith { id } }.orEmpty()

    fun configured(plan: FontPlan) = (if (plan.styleMode) plan.styleFonts.isNotEmpty() else plan.primary != null) ||
        plan.fallbacks.isNotEmpty() || plan.universal.isNotEmpty()

    fun preview(plan: FontPlan, group: String): FontPlan = plan.copy(
        primary = if (plan.styleMode) plan.styleFonts[group] else plan.primary, styleMode = false)

    fun systemFamily(group: String): String = when (group) {
        "serif" -> "serif"; "mono" -> "monospace"; "google" -> "google-sans"
        "casual" -> "casual"; "cursive" -> "cursive"; else -> "sans-serif"
    }
}
