package dev.glyphorder.app

import android.app.Application
import com.topjohnwu.superuser.Shell

class GlyphApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(20))
    }
}
