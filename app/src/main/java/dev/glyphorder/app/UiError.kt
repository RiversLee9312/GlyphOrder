package dev.glyphorder.app

import android.content.Context

/** Stable message identifiers let platform-independent code report localized UI errors. */
object UiError {
    private const val PREFIX = "__UI_ERR__|"
    private const val SEPARATOR = "|"

    fun key(name: String, vararg arguments: Any): String = buildString {
        append(PREFIX).append(name)
        arguments.forEach { append(SEPARATOR).append(it) }
    }

    fun resolve(context: Context, message: String?): String? {
        if (message == null || !message.startsWith(PREFIX)) return null
        val parts = message.removePrefix(PREFIX).split(SEPARATOR)
        val resource = when (parts.firstOrNull()) {
            "error_font_extension" -> R.string.error_font_extension
            "error_font_too_large" -> R.string.error_font_too_large
            "error_file_read" -> R.string.error_file_read
            "error_font_save" -> R.string.error_font_save
            "error_font_remove" -> R.string.error_font_remove
            "error_font_size_invalid" -> R.string.error_font_size_invalid
            "error_font_single_face" -> R.string.error_font_single_face
            "error_font_directory_corrupt", "error_font_table_bounds", "error_font_required_data",
            "error_font_metadata_large", "error_font_name_table", "error_font_name_bounds",
            "error_font_axis_table", "error_font_axes_bounds", "error_font_axis_tag",
            "error_font_axis_range", "error_font_cmap" -> R.string.error_font_data_invalid
            "error_root_permission" -> R.string.error_root_permission
            "error_system_updated" -> R.string.error_system_updated
            "error_selinux_label_missing" -> R.string.error_selinux_label_missing
            "error_selinux_label_unsupported" -> R.string.error_selinux_label_unsupported
            "error_font_config_conflict" -> R.string.error_font_config_conflict
            "error_system_font_config_missing" -> R.string.error_system_font_config_missing
            "error_android15_config_missing" -> R.string.error_android15_config_missing
            "error_module_not_installed" -> R.string.error_module_not_installed
            "error_xml_declaration" -> R.string.error_xml_declaration
            "error_font_config_format" -> R.string.error_font_config_format
            "error_language_script_unsupported" -> R.string.error_language_script_unsupported
            "error_fallback_font_removed" -> R.string.error_fallback_font_removed
            "error_universal_font_removed" -> R.string.error_universal_font_removed
            "error_emoji_config_validation" -> R.string.error_emoji_config_validation
            "error_select_font" -> R.string.error_select_font
            "error_conflicting_modules" -> R.string.error_conflicting_modules
            "error_select_existing_family" -> R.string.error_select_existing_family
            "error_selected_font_removed" -> R.string.error_selected_font_removed
            "error_total_fonts_large" -> R.string.error_total_fonts_large
            "error_font_family_limit" -> R.string.error_font_family_limit
            "error_font_integrity" -> R.string.error_font_integrity
            "error_device_changed" -> R.string.error_device_changed
            "error_conflict_detected" -> R.string.error_conflict_detected
            "error_config_changed" -> R.string.error_config_changed
            "error_module_integrity" -> R.string.error_module_integrity
            "error_magisk_module_missing" -> R.string.error_magisk_module_missing
            "error_installed_module_integrity" -> R.string.error_installed_module_integrity
            "error_root_command" -> R.string.error_root_command
            "error_module_not_generated" -> R.string.error_module_not_generated
            "error_module_save" -> R.string.error_module_save
            else -> return null
        }
        return context.getString(resource, *parts.drop(1).toTypedArray())
    }
}
