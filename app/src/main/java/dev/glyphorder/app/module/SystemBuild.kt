package dev.glyphorder.app.module

import android.os.Build
import org.json.JSONObject

/** A ROM may retain its vendor fingerprint across Android releases and weekly builds. */
data class SystemBuild(val fingerprint: String, val sdk: Int, val incremental: String) {
    fun matches(other: SystemBuild): Boolean = fingerprint.isNotBlank() && sdk > 0 &&
        incremental.isNotBlank() && this == other

    fun manifest() = JSONObject().put("fingerprint", fingerprint)
        .put("sdk", sdk).put("incremental", incremental)

    companion object {
        fun current() = SystemBuild(Build.FINGERPRINT, Build.VERSION.SDK_INT, Build.VERSION.INCREMENTAL)
        // A legacy baseline has insufficient evidence to reuse after an OTA.
        fun fromManifest(json: JSONObject) = SystemBuild(json.optString("fingerprint"),
            json.optInt("sdk", -1), json.optString("incremental"))
    }
}
