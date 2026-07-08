package io.github.theonionsarewatching.shtigletz

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import io.github.theonionsarewatching.shtigletz.notify.Notifier

class DMailApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Settings.nightMode(this))
        Notifier.init(this)
    }
}
