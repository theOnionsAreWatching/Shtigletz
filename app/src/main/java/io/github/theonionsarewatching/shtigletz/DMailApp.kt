package io.github.theonionsarewatching.shtigletz

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class DMailApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Settings.nightMode(this))
    }
}
