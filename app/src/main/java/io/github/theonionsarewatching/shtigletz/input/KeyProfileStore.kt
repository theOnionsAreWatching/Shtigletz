package io.github.theonionsarewatching.shtigletz.input

import android.content.Context
import android.os.Build

/**
 * Loads the active key profile for this device. Priority:
 *   1. A profile saved on-device via calibration (SharedPreferences).
 *   2. A preset bundled in assets/keyprofiles/{MANUFACTURER}_{MODEL}.json.
 *   3. assets/keyprofiles/default.json.
 *   4. Hard-coded standard dpad mapping.
 *
 * Profiles are keyed by manufacturer+model so a calibrated profile from one
 * unit can be exported and imported on identical units.
 */
class KeyProfileStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("keyprofiles", Context.MODE_PRIVATE)

    val deviceKey: String =
        (Build.MANUFACTURER + "_" + Build.MODEL).replace(Regex("[^A-Za-z0-9._-]"), "_")

    var profile: KeyProfile = load()
        private set

    private fun load(): KeyProfile {
        prefs.getString(PREF_PREFIX + deviceKey, null)?.let { saved ->
            runCatching { return KeyProfile.fromJson(saved) }
        }
        runCatching {
            context.assets.open("keyprofiles/$deviceKey.json").bufferedReader().use {
                return KeyProfile.fromJson(it.readText())
            }
        }
        runCatching {
            context.assets.open("keyprofiles/default.json").bufferedReader().use {
                return KeyProfile.fromJson(it.readText())
            }
        }
        return KeyProfile.standardDefault()
    }

    fun save(p: KeyProfile) {
        prefs.edit().putString(PREF_PREFIX + deviceKey, p.toJson()).apply()
        profile = p
    }

    fun reset() {
        prefs.edit().remove(PREF_PREFIX + deviceKey).apply()
        profile = load()
    }

    fun reload() {
        profile = load()
    }

    companion object {
        private const val PREF_PREFIX = "profile_"
    }
}
