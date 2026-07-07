package io.github.theonionsarewatching.shtigletz.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import io.github.theonionsarewatching.shtigletz.input.Action
import io.github.theonionsarewatching.shtigletz.input.KeyProfileStore

/**
 * All activities extend this. It intercepts every hardware key event and
 * resolves it against the active per-device key profile:
 *
 *  - Navigation actions (UP/DOWN/LEFT/RIGHT/SELECT/BACK) are re-dispatched as
 *    canonical KEYCODE_DPAD_* / KEYCODE_BACK events, so Android's built-in
 *    focus traversal drives the UI even when the physical keys emit
 *    non-standard codes.
 *  - App actions (COMPOSE, REPLY, DELETE, ...) are delivered to the current
 *    Activity via [onAppAction].
 *  - [onRawKey] lets CalibrationActivity capture raw events before mapping.
 */
abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var keyProfiles: KeyProfileStore
    private var swallowUpForKeyCode = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keyProfiles = KeyProfileStore(this)
    }

    override fun onResume() {
        super.onResume()
        // Pick up any profile saved by CalibrationActivity.
        keyProfiles.reload()
    }

    /** Return true to consume the raw event before any mapping (calibration). */
    protected open fun onRawKey(event: KeyEvent): Boolean = false

    /** Handle an app-level action. Return true if consumed. */
    protected open fun onAppAction(action: Action): Boolean = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (onRawKey(event)) return true

        val action = keyProfiles.profile.resolve(event)
            ?: return super.dispatchKeyEvent(event)

        val canonical = action.canonicalKeyCode()
        if (canonical != null) {
            // Navigation: normalize to the canonical keycode and let the
            // standard focus system handle it.
            return if (event.keyCode == canonical) {
                super.dispatchKeyEvent(event)
            } else {
                val translated = KeyEvent(
                    event.downTime, event.eventTime, event.action,
                    canonical, event.repeatCount, event.metaState
                )
                super.dispatchKeyEvent(translated)
            }
        }

        // App action: fire on key-down, swallow the matching key-up.
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (onAppAction(action)) {
                    swallowUpForKeyCode = event.keyCode
                    true
                } else {
                    super.dispatchKeyEvent(event)
                }
            }
            KeyEvent.ACTION_UP -> {
                if (event.keyCode == swallowUpForKeyCode) {
                    swallowUpForKeyCode = -1
                    true
                } else {
                    super.dispatchKeyEvent(event)
                }
            }
            else -> super.dispatchKeyEvent(event)
        }
    }
}
