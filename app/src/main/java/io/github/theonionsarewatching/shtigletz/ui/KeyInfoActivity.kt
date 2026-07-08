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
import io.github.theonionsarewatching.shtigletz.input.SoftKeys

/**
 * Soft-key report for this device, built from the keys the user already
 * learned via Settings → Soft keys → Custom. Display + Copy/Share only —
 * no capturing happens here.
 */
class KeyInfoActivity : ScaledActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val codes = SoftKeys.customCodes(this)
        if (codes == null) {
            // The Settings entry is hidden until keys are set; guard anyway.
            Toast.makeText(this, R.string.keyinfo_not_set, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(R.layout.activity_key_info)

        val report = report(codes.first, codes.second)
        findViewById<TextView>(R.id.reportText).text = report

        findViewById<Button>(R.id.copyButton).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("dmail-softkey-report", report))
            Toast.makeText(this, R.string.keyinfo_copied, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.shareButton).setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "D-Mail soft-key report: ${Build.MODEL}")
                putExtra(Intent.EXTRA_TEXT, report)
            }
            runCatching {
                startActivity(Intent.createChooser(intent, getString(R.string.keyinfo_share)))
            }.onFailure {
                Toast.makeText(this, R.string.keyinfo_no_share, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun keyName(code: Int): String =
        KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ')

    private fun report(left: Int, right: Int): String =
        "D-Mail soft-key report\n" +
        "Manufacturer: ${Build.MANUFACTURER}\n" +
        "Model: ${Build.MODEL}\n" +
        "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
        "Left soft key: code $left (${keyName(left)})\n" +
        "Right soft key: code $right (${keyName(right)})\n" +
        "XML: <profile model=\"${Build.MODEL}\" left=\"$left\" right=\"$right\" />"
}
