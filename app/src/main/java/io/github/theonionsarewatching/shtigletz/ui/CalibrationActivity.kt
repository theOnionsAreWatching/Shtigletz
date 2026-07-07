package io.github.theonionsarewatching.shtigletz.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.input.Action
import io.github.theonionsarewatching.shtigletz.input.KeyProfile

/**
 * "Learn keys" mode. For each action it prompts for a key press and records
 * keyCode + scanCode from the raw event, producing a per-device profile that
 * is saved keyed by manufacturer+model. Profiles can be exported (share as
 * JSON) and imported (paste JSON) so identical units can reuse a calibration.
 *
 * While capture is active, ALL raw keys are consumed (that is the point), so
 * the on-screen buttons are touch targets. Volume and power keys are ignored.
 */
class CalibrationActivity : BaseActivity() {

    private val order = Action.values().toList()
    private var step = 0
    private var capturing = false
    private val captured = LinkedHashMap<Action, KeyProfile.Mapping>()

    private lateinit var prompt: TextView
    private lateinit var progress: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)
        prompt = findViewById(R.id.calibrationPrompt)
        progress = findViewById(R.id.calibrationProgress)

        findViewById<Button>(R.id.startButton).setOnClickListener { start() }
        findViewById<Button>(R.id.skipButton).setOnClickListener { if (capturing) advance() }
        findViewById<Button>(R.id.resetButton).setOnClickListener {
            keyProfiles.reset()
            Toast.makeText(this, R.string.calibration_reset_done, Toast.LENGTH_SHORT).show()
            updateUi()
        }
        findViewById<Button>(R.id.exportButton).setOnClickListener { exportProfile() }
        findViewById<Button>(R.id.importButton).setOnClickListener { importProfile() }

        updateUi()
    }

    private fun start() {
        captured.clear()
        step = 0
        capturing = true
        updateUi()
    }

    private fun advance() {
        step++
        if (step >= order.size) finishCapture()
        updateUi()
    }

    private fun finishCapture() {
        capturing = false
        if (captured.isNotEmpty()) {
            val profile = KeyProfile(keyProfiles.deviceKey, captured.values.toList())
            keyProfiles.save(profile)
            Toast.makeText(this, R.string.calibration_saved, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateUi() {
        if (capturing && step < order.size) {
            prompt.text = getString(R.string.calibration_press_key, order[step].name)
        } else {
            prompt.text = getString(
                R.string.calibration_idle,
                keyProfiles.profile.name,
                keyProfiles.deviceKey
            )
        }
        progress.text = if (capturing) {
            getString(R.string.calibration_progress, step + 1, order.size)
        } else ""
    }

    override fun onRawKey(event: KeyEvent): Boolean {
        if (!capturing) return false
        // Don't let system keys get bound accidentally.
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_POWER -> return false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (step >= order.size) return true

        val duplicate = captured.values.any {
            it.keyCode == event.keyCode && it.scanCode == event.scanCode
        }
        if (duplicate) {
            Toast.makeText(this, R.string.calibration_duplicate, Toast.LENGTH_SHORT).show()
            return true
        }
        captured[order[step]] = KeyProfile.Mapping(event.keyCode, event.scanCode, order[step])
        advance()
        return true
    }

    private fun exportProfile() {
        val json = keyProfiles.profile.toJson()
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_SUBJECT, "D-Mail key profile: ${keyProfiles.deviceKey}")
                    .putExtra(Intent.EXTRA_TEXT, json),
                getString(R.string.calibration_export)
            )
        )
    }

    private fun importProfile() {
        val input = EditText(this)
        input.hint = getString(R.string.calibration_import_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.calibration_import)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                runCatching { KeyProfile.fromJson(input.text.toString()) }
                    .onSuccess {
                        keyProfiles.save(it)
                        Toast.makeText(this, R.string.calibration_saved, Toast.LENGTH_SHORT).show()
                        updateUi()
                    }
                    .onFailure {
                        Toast.makeText(this, R.string.calibration_import_bad, Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
