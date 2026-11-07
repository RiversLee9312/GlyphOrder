package dev.glyphorder.app.module

import dev.glyphorder.app.UiError

data class FontBaseline(val path: String, val build: SystemBuild, val disabled: Boolean,
                        val mountedThisBoot: Boolean) {
    companion object {
        /** A metamodule may update its mounted image before the framework restarts. */
        fun acceptsConfig(current: String, original: String, installed: String?, stagedOriginal: String?, staged: String?): Boolean =
            current == original || current == installed || (stagedOriginal == original && current == staged)

        fun select(current: SystemBuild, active: FontBaseline?, staged: FontBaseline?): String? {
            val activeMatches = active?.build?.matches(current) == true
            val stagedMatches = staged?.build?.matches(current) == true
            // A new staged ZIP cannot make an already-mounted, unverified XML safe to read.
            check(active == null || activeMatches || !active.mountedThisBoot) {
                UiError.key("error_system_updated")
            }
            check(staged == null || stagedMatches || staged.disabled) {
                UiError.key("error_system_updated")
            }
            // Installation clears disable before reboot. At that point the new staged
            // baseline is authoritative while the old active directory awaits replacement.
            check(active == null || activeMatches || active.disabled || stagedMatches) {
                UiError.key("error_system_updated")
            }
            return when {
                activeMatches -> active!!.path
                stagedMatches -> staged!!.path
                else -> null
            }
        }
    }
}
