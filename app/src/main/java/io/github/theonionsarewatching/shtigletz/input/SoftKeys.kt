package io.github.theonionsarewatching.shtigletz.input

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import io.github.theonionsarewatching.shtigletz.R
import org.xmlpull.v1.XmlPullParser

/**
 * Optional left/right soft-key support for feature phones.
 *
 * Resolution order (Settings "Soft keys"):
 *  - off    -> disabled
 *  - auto   -> enabled only if this device's Build.MODEL is listed in
 *              res/xml/softkey_profiles.xml (default; unknown devices see nothing)
 *  - custom -> the two key codes the user captured in the learn screen
 *
 * Normal keys are never taken over: the learn screen refuses to capture any
 * standard Android key (dpad, numbers, letters, volume, call, media, …) and
 * only the resolved soft-key codes are ever intercepted at runtime.
 */
object SoftKeys {

    private var profilesLoaded = false
    private var profileCodes: Pair<Int, Int>? = null

    fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun mode(ctx: Context): String = prefs(ctx).getString("softkeyMode", "auto") ?: "auto"
    fun setMode(ctx: Context, v: String) = prefs(ctx).edit().putString("softkeyMode", v).apply()

    fun setCustomCodes(ctx: Context, left: Int, right: Int) =
        prefs(ctx).edit().putInt("softLeftCode", left).putInt("softRightCode", right).apply()

    /** The user-learned codes, or null if custom keys were never set up. */
    fun customCodes(ctx: Context): Pair<Int, Int>? {
        val l = prefs(ctx).getInt("softLeftCode", 0)
        val r = prefs(ctx).getInt("softRightCode", 0)
        return if (l > 0 && r > 0 && l != r) l to r else null
    }

    /** (leftKeyCode, rightKeyCode) or null when soft keys are inactive. */
    fun resolve(ctx: Context): Pair<Int, Int>? = when (mode(ctx)) {
        "off" -> null
        "custom" -> {
            val l = prefs(ctx).getInt("softLeftCode", 0)
            val r = prefs(ctx).getInt("softRightCode", 0)
            if (l > 0 && r > 0 && l != r) l to r else null
        }
        else -> deviceProfile(ctx) // "auto"
    }

    /** Look up this device model in the bundled profile list. */
    private fun deviceProfile(ctx: Context): Pair<Int, Int>? {
        if (!profilesLoaded) {
            profilesLoaded = true
            profileCodes = runCatching { parseProfiles(ctx, Build.MODEL) }.getOrNull()
        }
        return profileCodes
    }

    private fun parseProfiles(ctx: Context, model: String): Pair<Int, Int>? {
        val parser = ctx.resources.getXml(R.xml.softkey_profiles)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "profile") {
                val m = parser.getAttributeValue(null, "model") ?: ""
                if (m.equals(model, ignoreCase = true)) {
                    val left = parser.getAttributeValue(null, "left")?.toIntOrNull() ?: 0
                    val right = parser.getAttributeValue(null, "right")?.toIntOrNull() ?: 0
                    return if (left > 0 && right > 0 && left != right) left to right else null
                }
            }
            event = parser.next()
        }
        return null
    }

    /**
     * One-time first-launch offer: if this device has no bundled profile and
     * the user hasn't configured anything, ask once whether to set up soft
     * keys — with a Skip that never asks again. Devices with a bundled
     * profile (or an existing custom setup) are never prompted.
     */
    fun maybeOfferSetup(activity: android.app.Activity) {
        val p = prefs(activity)
        if (p.getBoolean("softkeyOffered", false)) return
        p.edit().putBoolean("softkeyOffered", true).apply()
        if (mode(activity) != "auto" || resolve(activity) != null) return
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(R.string.softkey_offer_title)
            .setMessage(R.string.softkey_offer_msg)
            .setPositiveButton(R.string.softkey_offer_setup) { _, _ ->
                activity.startActivity(
                    android.content.Intent(
                        activity,
                        io.github.theonionsarewatching.shtigletz.ui.SoftKeyLearnActivity::class.java
                    )
                )
            }
            .setNegativeButton(R.string.softkey_offer_skip, null)
            .show()
    }

    /**
     * True for every standard Android key — these must keep their normal
     * behavior and can never be captured as a soft key. Capturable keys are
     * only: KEYCODE_SOFT_LEFT/RIGHT (1/2), F1–F12, and vendor-specific codes
     * above the standard range.
     */
    fun isReserved(code: Int): Boolean = when (code) {
        KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_SOFT_RIGHT -> false
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> false
        else -> code in 0..231 // all standard Android keys (dpad, numbers, letters, volume, media, …)
    }
}
