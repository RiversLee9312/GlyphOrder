package dev.glyphorder.app.font

import dev.glyphorder.app.UiError

/** A library item owns its faces; combinations reference the item, never a weight file. */
data class FontLibraryFamily(val id: String, val name: String, val members: List<String>, val bundled: Boolean) {
    fun faces(fonts: List<FontInfo>) = members.mapNotNull { id -> fonts.find { it.id == id } }
}

object FontLibrary {
    private fun regularFirst(font: FontInfo) = (if (font.italic) 1000 else 0) + kotlin.math.abs(font.weight - 400)

    fun validateFaces(fonts: List<FontInfo>) {
        fonts.distinctBy { it.id }.groupBy { it.family ?: it.id }.values.forEach { faces ->
            require(faces.distinctBy { it.weight to it.italic }.size == faces.size &&
                faces.groupBy { it.italic }.values.none { group -> group.size > 1 && group.any { face -> face.axes.any { it.tag == "wght" } } }) {
                UiError.key("error_font_family_conflict")
            }
        }
    }

    fun defaults(fonts: List<FontInfo>, isBundled: (FontInfo) -> Boolean): List<FontLibraryFamily> {
        val builtins = fonts.filter { isBundled(it) && !it.hidden }.map { root ->
            val group = if (root.preset == "noto-sans") "noto" else root.preset
            val extra = fonts.filter { it.hidden && group != null && it.fallbackGroup == group &&
                (group != "noto" || it.name.replace(" ", "").startsWith("NotoSans")) }
                .sortedByDescending { it.coverage.sumOf { range -> range.last.toLong() - range.first + 1 } }
            val faces = (FontSelection.family(root, fonts) + extra).distinctBy { it.id }
            FontLibraryFamily(root.id, root.name, listOf(root.id) + faces.map { it.id }.filter { it != root.id }, true)
        }
        val imported = fonts.filterNot(isBundled).groupBy { it.family ?: it.id }.values.map { faces ->
            val primary = faces.minWith(compareBy<FontInfo>(::regularFirst).thenBy { it.id })
            FontLibraryFamily(primary.id, primary.family ?: primary.name, faces.sortedBy(::regularFirst).map { it.id }, false)
        }
        return builtins + imported
    }

    fun expand(ids: List<String>, library: List<FontLibraryFamily>, fonts: List<FontInfo>): List<String> =
        ids.mapNotNull { id ->
            val family = library.find { it.id == id }
            if (family == null) id else family.faces(fonts).firstOrNull()?.id
        }.distinct()

    fun components(library: List<FontLibraryFamily>, fonts: List<FontInfo>): Map<String, List<String>> =
        library.mapNotNull { family ->
            val faces = family.faces(fonts).distinctBy { it.family ?: it.id }
            faces.firstOrNull()?.let { it.id to faces.drop(1).map(FontInfo::id) }
        }.toMap()
}
