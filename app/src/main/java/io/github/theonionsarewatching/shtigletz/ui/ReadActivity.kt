package io.github.theonionsarewatching.shtigletz.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.Settings
import io.github.theonionsarewatching.shtigletz.db.MailDb
import io.github.theonionsarewatching.shtigletz.mail.ImapService
import io.github.theonionsarewatching.shtigletz.mail.MailAccount
import io.github.theonionsarewatching.shtigletz.mail.MailText
import io.github.theonionsarewatching.shtigletz.security.AccountStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReadActivity : SoftKeyActivity() {

    companion object {
        const val EXTRA_ACCOUNT_ID = "accountId"
        const val EXTRA_FOLDER = "folder"
        const val EXTRA_UIDS = "uids"
        const val EXTRA_INDEX = "index"
    }

    private lateinit var account: MailAccount
    private var folder: String = "INBOX"
    private lateinit var db: MailDb
    private lateinit var web: SafeWebView
    private lateinit var fromText: TextView
    private lateinit var subjectText: TextView
    private lateinit var metaText: TextView

    private var uids: LongArray = LongArray(0)
    private var index: Int = 0
    private val dateFmt = SimpleDateFormat("EEE, MMM d yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loaded = AccountStore.get(this, intent.getStringExtra(EXTRA_ACCOUNT_ID))
        if (loaded == null) { finish(); return }
        account = loaded
        folder = intent.getStringExtra(EXTRA_FOLDER) ?: "INBOX"
        db = MailDb.get(this)

        setContentView(R.layout.activity_read)
        web = findViewById(R.id.bodyView)
        fromText = findViewById(R.id.readFrom)
        subjectText = findViewById(R.id.readSubject)
        metaText = findViewById(R.id.readMeta)

        // Tapped [link]s never navigate — the URL is shown as text.
        web.onLinkClicked = { url -> showLinkDialog(url) }

        // Reading-position bar fills as the body scrolls.
        val progress = findViewById<android.widget.ProgressBar>(R.id.readProgress)
        web.onScrollPercent = { pct -> progress.progress = pct }

        uids = intent.getLongArrayExtra(EXTRA_UIDS) ?: LongArray(0)
        index = intent.getIntExtra(EXTRA_INDEX, 0)

        findViewById<Button>(R.id.replyButton).setOnClickListener { reply() }
        findViewById<Button>(R.id.forwardButton).setOnClickListener { forward() }
        findViewById<Button>(R.id.deleteButton).setOnClickListener { deleteCurrent() }
        findViewById<Button>(R.id.prevButton).setOnClickListener { move(-1) }
        findViewById<Button>(R.id.nextButton).setOnClickListener { move(+1) }

        // Soft keys (when enabled).
        setSoftKeys(
            getString(R.string.read_reply), { reply() },
            getString(R.string.read_next_full), { move(+1) }
        )

        load()
    }

    private fun current(): MailDb.CachedMessage? =
        if (index in uids.indices) db.message(account.id, folder, uids[index]) else null

    private fun load() {
        if (index !in uids.indices) { finish(); return }
        val uid = uids[index]
        val cached = db.message(account.id, folder, uid)

        if (cached != null && cached.bodyFetched) {
            show(cached)
            if (!cached.seen) markSeen(uid)
            return
        }

        // Not cached: fetch text parts from the server.
        metaText.text = getString(R.string.read_loading)
        web.showEmail(null, null, true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ImapService(account, folder).fetchMessage(uid, markSeen = true)?.also { full ->
                        db.upsertEnvelope(account.id, folder, full.envelope)
                        db.saveBody(account.id, folder, uid, full.body.plain, full.body.html, full.body.blockedParts)
                        db.setSeen(account.id, folder, uid, true)
                    }
                }
            }
            val msg = db.message(account.id, folder, uid)
            if (result.isFailure || msg == null || !msg.bodyFetched) {
                metaText.text = getString(R.string.read_offline)
                msg?.let {
                    fromText.text = it.fromName
                    subjectText.text = it.subject
                }
                return@launch
            }
            show(msg)
        }
    }

    private fun show(msg: MailDb.CachedMessage) {
        fromText.text = msg.fromName
        subjectText.text = msg.subject
        val date = if (msg.dateMillis > 0) dateFmt.format(Date(msg.dateMillis)) else ""
        metaText.text = if (msg.blockedParts > 0) {
            getString(R.string.read_meta_blocked, date, msg.blockedParts)
        } else date
        findViewById<android.widget.ProgressBar>(R.id.readProgress).progress = 0
        web.showEmail(msg.bodyPlain, msg.bodyHtml, Settings.showLinks(this))
        // Dpad up/down scrolls the body natively once the WebView has focus.
        web.requestFocus()
    }

    private fun markSeen(uid: Long) {
        db.setSeen(account.id, folder, uid, true)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { ImapService(account, folder).setSeen(uid, true) }
        }
    }

    private fun showLinkDialog(url: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.link_dialog_title)
            .setMessage(url)
            .setPositiveButton(R.string.link_copy) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("link", url))
                Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.link_close, null)
            .show()
    }

    private fun move(delta: Int) {
        val newIndex = index + delta
        if (newIndex in uids.indices) {
            index = newIndex
            load()
        } else {
            Toast.makeText(this, R.string.read_no_more, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bodyText(msg: MailDb.CachedMessage): String =
        msg.bodyPlain ?: msg.bodyHtml?.let { MailText.htmlToPlain(it) } ?: ""

    private fun reply() {
        val msg = current() ?: return
        val quoted = bodyText(msg).lines().joinToString("\n") { "> $it" }
        startActivity(
            Intent(this, ComposeActivity::class.java)
                .putExtra(ComposeActivity.EXTRA_ACCOUNT_ID, account.id)
                .putExtra(ComposeActivity.EXTRA_TO, msg.fromEmail)
                .putExtra(
                    ComposeActivity.EXTRA_SUBJECT,
                    if (msg.subject.startsWith("Re:", true)) msg.subject else "Re: ${msg.subject}"
                )
                .putExtra(ComposeActivity.EXTRA_BODY, "\n\n$quoted")
        )
    }

    private fun forward() {
        val msg = current() ?: return
        val header = getString(R.string.forward_header, msg.fromName, msg.fromEmail, msg.subject)
        startActivity(
            Intent(this, ComposeActivity::class.java)
                .putExtra(ComposeActivity.EXTRA_ACCOUNT_ID, account.id)
                .putExtra(
                    ComposeActivity.EXTRA_SUBJECT,
                    if (msg.subject.startsWith("Fwd:", true)) msg.subject else "Fwd: ${msg.subject}"
                )
                .putExtra(ComposeActivity.EXTRA_BODY, "\n\n$header\n\n${bodyText(msg)}")
        )
    }

    /** Delete = move to Trash when the server has one, else confirm permanent delete. */
    private fun deleteCurrent() {
        if (index !in uids.indices) return
        val uid = uids[index]
        lifecycleScope.launch {
            val trash = withContext(Dispatchers.IO) {
                runCatching { ImapService(account).findTrashFolder() }.getOrNull()
            }
            if (trash != null && trash != folder) {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { ImapService(account, folder).moveTo(uid, trash) }.getOrDefault(false)
                }
                if (ok) {
                    db.delete(account.id, folder, uid)
                    Toast.makeText(this@ReadActivity, getString(R.string.moved_fmt, trash), Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@ReadActivity, R.string.action_failed, Toast.LENGTH_SHORT).show()
                }
            } else {
                AlertDialog.Builder(this@ReadActivity)
                    .setMessage(R.string.confirm_delete_perm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                runCatching { ImapService(account, folder).deletePermanent(uid) }.getOrDefault(false)
                            }
                            if (ok) {
                                db.delete(account.id, folder, uid)
                                Toast.makeText(this@ReadActivity, R.string.deleted, Toast.LENGTH_SHORT).show()
                                finish()
                            } else {
                                Toast.makeText(this@ReadActivity, R.string.action_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }
}
