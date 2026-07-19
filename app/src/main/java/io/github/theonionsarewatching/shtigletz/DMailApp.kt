package io.github.theonionsarewatching.shtigletz

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import io.github.theonionsarewatching.shtigletz.notify.Notifier

class DMailApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Settings.nightMode(this))
        Notifier.init(this)
        // Clean up a leftover update APK from a previous install (default on).
        if (Settings.deleteUpdateApk(this)) {
            runCatching { java.io.File(getExternalFilesDir(null), "update.apk").delete() }
        }
    }
}
