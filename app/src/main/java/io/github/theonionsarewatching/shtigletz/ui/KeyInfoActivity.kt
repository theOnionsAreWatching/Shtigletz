package io.github.theonionsarewatching.shtigletz.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import io.github.theonionsarewatching.shtigletz.R

/**
 * Device + key-code diagnostics for collecting soft-key profiles.
 *
 * Extends ScaledActivity (NOT SoftKeyActivity) so nothing is intercepted —
 * every key press is shown with its raw code. Nothing is consumed, so dpad
 * navigation and Back keep working normally; a key that exits this screen or
 * does something else already has a system function and doesn't need mapping.
 *
 * Produces a paste-ready <profile> line plus a human-readable report the
 * reporter can post to the forum.
 */
class KeyInfoActivity : ScaledActivity() {

    private var lastCode = 0
    private var lastName = ""
    private var leftCode = 0
    private var rightCode = 0

    private lateinit var lastKeyView: TextView
    private lateinit var assignedView: TextView
    private lateinit var reportView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_key_info)

        findViewById<TextView>(R.id.deviceInfo).text = deviceInfo()
        lastKeyView = findViewById(R.id.lastKey)
        assignedView = findViewById(R.id.assignedKeys)
        reportView = findViewById(R.id.reportText)

        findViewById<Button>(R.id.setLeftButton).setOnClickListener {
            if (lastCode == 0) { toast(getString(R.string.keyinfo_press_first)); return@setOnClickListener }
            leftCode = lastCode; refresh()
        }
        findViewById<Button>(R.id.setRightButton).setOnClickListener {
            if (lastCode == 0) { toast(getString(R.string.keyinfo_press_first)); return@setOnClickListener }
            rightCode = lastCode; refresh()
        }
        findViewById<Button>(R.id.copyButton).setOnClickListener { copy() }
        findViewById<Button>(R.id.shareButton).setOnClickListener { share() }

        refresh()
    }

    /** Record every key, but never consume — navigation stays normal. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastCode = event.keyCode
            lastName = keyName(event.keyCode)
            lastKeyView.text = getString(R.string.keyinfo_last_fmt, lastName, event.keyCode)
        }
        return super.dispatchKeyEvent(event)
    }

    private fun keyName(code: Int): String =
        KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ')

    private fun deviceInfo(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}\n" +
        "Model: ${Build.MODEL}\n" +
        "Device: ${Build.DEVICE}   Brand: ${Build.BRAND}\n" +
        "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

    private fun refresh() {
        assignedView.text = getString(
            R.string.keyinfo_assigned_fmt,
            if (leftCode == 0) "—" else "${keyName(leftCode)} ($leftCode)",
            if (rightCode == 0) "—" else "${keyName(rightCode)} ($rightCode)"
        )
        reportView.text = report()
    }

    private fun profileLine(): String =
        "<profile model=\"${Build.MODEL}\" left=\"$leftCode\" right=\"$rightCode\" />"

    private fun report(): String = buildString {
        append("D-Mail soft-key report\n")
        append("Manufacturer: ${Build.MANUFACTURER}\n")
        append("Model: ${Build.MODEL}\n")
        append("Device: ${Build.DEVICE}\n")
        append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        append("Left soft key: ")
        append(if (leftCode == 0) "(not set)\n" else "code $leftCode (${keyName(leftCode)})\n")
        append("Right soft key: ")
        append(if (rightCode == 0) "(not set)\n" else "code $rightCode (${keyName(rightCode)})\n")
        append("XML: ")
        append(if (leftCode == 0 || rightCode == 0) "(set both keys first)" else profileLine())
    }

    private fun copy() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("dmail-softkey-report", report()))
        toast(getString(R.string.keyinfo_copied))
    }

    private fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "D-Mail soft-key report: ${Build.MODEL}")
            putExtra(Intent.EXTRA_TEXT, report())
        }
        runCatching {
            startActivity(Intent.createChooser(intent, getString(R.string.keyinfo_share)))
        }.onFailure { toast(getString(R.string.keyinfo_no_share)) }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
