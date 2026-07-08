package io.github.theonionsarewatching.shtigletz.ui

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import io.github.theonionsarewatching.shtigletz.Settings

/** Applies the user's text-size setting (fontScale) to the whole screen. */
open class ScaledActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        val scale = Settings.textScale(newBase)
        if (scale == 1f) {
            super.attachBaseContext(newBase)
            return
        }
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = scale
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
}
