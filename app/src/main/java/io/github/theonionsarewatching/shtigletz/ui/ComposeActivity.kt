package io.github.theonionsarewatching.shtigletz.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import io.github.theonionsarewatching.shtigletz.FlavorConfig
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.attach.AttachmentOps
import io.github.theonionsarewatching.shtigletz.mail.MailAccount
import io.github.theonionsarewatching.shtigletz.mail.SmtpService
import io.github.theonionsarewatching.shtigletz.security.AccountStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ComposeActivity : SoftKeyActivity() {

    companion object {
        const val EXTRA_ACCOUNT_ID = "accountId"
        const val EXTRA_TO = "to"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_BODY = "body"
        private const val MAX_TOTAL_BYTES = 20L * 1024 * 1024
    }

    private data class Attached(val name: String, val mime: String, val file: File, val size: Long)

    private lateinit var account: MailAccount
    private val attached = ArrayList<Attached>()
    private lateinit var attachedLabel: TextView

    /** System document picker — no storage permissions, dpad-friendly. */
    private val picker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> uris?.forEach { importUri(it) }; refreshAttachedLabel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loaded = AccountStore.get(this, intent.getStringExtra(EXTRA_ACCOUNT_ID))
            ?: AccountStore.list(this).firstOrNull()
        if (loaded == null) { finish(); return }
        account = loaded

        setContentView(R.layout.activity_compose)
        findViewById<TextView>(R.id.composeTitle).text =
            getString(R.string.compose_title_fmt, account.email)
        val to = findViewById<EditText>(R.id.composeTo)
        val cc = findViewById<EditText>(R.id.composeCc)
        val subject = findViewById<EditText>(R.id.composeSubject)
        val body = findViewById<EditText>(R.id.composeBody)
        val send = findViewById<Button>(R.id.sendButton)
        val attach = findViewById<Button>(R.id.attachButton)
        attachedLabel = findViewById(R.id.attachedLabel)

        to.setText(intent.getStringExtra(EXTRA_TO) ?: "")
        subject.setText(intent.getStringExtra(EXTRA_SUBJECT) ?: "")
        body.setText(intent.getStringExtra(EXTRA_BODY) ?: "")

        if (FlavorConfig.ATTACHMENTS) {
            attach.visibility = View.VISIBLE
            attach.setOnClickListener { picker.launch(arrayOf("*/*")) }
            attachedLabel.setOnClickListener { removeDialog() }
            handleShareIntent(body)
        } else {
            attach.visibility = View.GONE
        }
        refreshAttachedLabel()

        // Soft key (when enabled).
        setSoftKeys(getString(R.string.compose_send), { send.performClick() })

        send.setOnClickListener {
            val toValue = to.text.toString().trim()
            if (toValue.isBlank()) {
                Toast.makeText(this, R.string.compose_missing_to, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            send.isEnabled = false
            lifecycleScope.launch {
                val error: String? = withContext(Dispatchers.IO) {
                    try {
                        SmtpService(account).send(
                            to = toValue,
                            cc = cc.text.toString(),
                            subject = subject.text.toString(),
                            body = body.text.toString(),
                            attachments = attached.map {
                                SmtpService.Attachment(it.file, it.name, it.mime)
                            }
                        )
                        null
                    } catch (e: Exception) {
                        (e.message ?: e.javaClass.simpleName).take(200)
                    }
                }
                if (error == null) {
                    Toast.makeText(this@ComposeActivity, R.string.compose_sent, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    send.isEnabled = true
                    Toast.makeText(
                        this@ComposeActivity,
                        getString(R.string.compose_failed, error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /** Arriving via "Share" from a file manager (Plus/Pro only — the intent
     *  filter exists only in those flavors' manifests). */
    private fun handleShareIntent(bodyField: EditText) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                    if (bodyField.text.isNullOrBlank()) bodyField.setText(it)
                }
                @Suppress("DEPRECATION")
                (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { importUri(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    ?.forEach { importUri(it) }
            }
        }
        refreshAttachedLabel()
    }

    /** Copy the picked/shared file into our cache immediately so URI
     *  permissions can't expire before send. */
    private fun importUri(uri: Uri) {
        if (!FlavorConfig.ATTACHMENTS) return
        runCatching {
            var name = "file"
            var size = -1L
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
            val mime = contentResolver.getType(uri) ?: "application/octet-stream"
            val dest = File(cacheDir, "compose/${System.currentTimeMillis()}_${AttachmentOps.sanitize(name)}")
            dest.parentFile?.mkdirs()
            contentResolver.openInputStream(uri).use { input ->
                dest.outputStream().use { output -> input!!.copyTo(output) }
            }
            val actualSize = if (size >= 0) size else dest.length()
            val total = attached.sumOf { it.size } + actualSize
            if (total > MAX_TOTAL_BYTES) {
                dest.delete()
                Toast.makeText(this, R.string.compose_too_big, Toast.LENGTH_LONG).show()
            } else {
                attached.add(Attached(name, mime, dest, actualSize))
            }
        }.onFailure {
            Toast.makeText(this, R.string.compose_attach_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshAttachedLabel() {
        if (!FlavorConfig.ATTACHMENTS || attached.isEmpty()) {
            attachedLabel.visibility = View.GONE
            return
        }
        attachedLabel.visibility = View.VISIBLE
        attachedLabel.text = getString(
            R.string.compose_attached_fmt,
            attached.size,
            attached.joinToString(", ") { it.name }
        )
    }

    private fun removeDialog() {
        if (attached.isEmpty()) return
        val names = attached.map { "${it.name} (${AttachmentOps.sizeLabel(it.size.toInt())})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.compose_remove_title)
            .setItems(names) { _, which ->
                attached[which].file.delete()
                attached.removeAt(which)
                refreshAttachedLabel()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
