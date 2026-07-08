package io.github.theonionsarewatching.shtigletz.ui

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.input.SoftKeys

/**
 * Two-step soft-key capture. Extends ScaledActivity (NOT SoftKeyActivity) so
 * already-configured soft keys aren't intercepted while relearning.
 *
 * Any recognized/standard key (dpad, numbers, letters, volume, back, …) is
 * treated as that normal key and never captured — a hint explains and the
 * key keeps working (dpad still moves focus, back still exits).
 */
class SoftKeyLearnActivity : ScaledActivity() {

    private var leftCode: Int = 0
    private var rightCode: Int = 0
    private lateinit var prompt: TextView
    private lateinit var captured: TextView
    private lateinit var hint: TextView
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_softkey_learn)
        prompt = findViewById(R.id.learnPrompt)
        captured = findViewById(R.id.learnCaptured)
        hint = findViewById(R.id.learnHint)
        saveButton = findViewById(R.id.learnSave)
        saveButton.isEnabled = false
        saveButton.setOnClickListener {
            SoftKeys.setCustomCodes(this, leftCode, rightCode)
            SoftKeys.setMode(this, "custom")
            finish()
        }
        prompt.text = getString(R.string.learn_press_left)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Reserved keys stay 100% normal — show a hint, then let the system
        // handle them (dpad navigates, back exits, volume changes volume…).
        if (SoftKeys.isReserved(keyCode)) {
            if (keyCode != KeyEvent.KEYCODE_BACK &&
                keyCode != KeyEvent.KEYCODE_DPAD_UP &&
                keyCode != KeyEvent.KEYCODE_DPAD_DOWN &&
                keyCode != KeyEvent.KEYCODE_DPAD_LEFT &&
                keyCode != KeyEvent.KEYCODE_DPAD_RIGHT &&
                keyCode != KeyEvent.KEYCODE_DPAD_CENTER &&
                keyCode != KeyEvent.KEYCODE_ENTER
            ) {
                hint.text = getString(R.string.learn_reserved, keyCode)
            }
            return super.onKeyDown(keyCode, event)
        }

        // Non-reserved: capture.
        if (leftCode == 0) {
            leftCode = keyCode
            captured.text = getString(R.string.learn_captured_left, keyCode)
            prompt.text = getString(R.string.learn_press_right)
            hint.text = ""
        } else if (rightCode == 0) {
            if (keyCode == leftCode) {
                hint.text = getString(R.string.learn_same_key)
                return true
            }
            rightCode = keyCode
            captured.text = getString(R.string.learn_captured_both, leftCode, rightCode)
            prompt.text = getString(R.string.learn_done)
            saveButton.isEnabled = true
            saveButton.requestFocus()
        }
        return true
    }
}
