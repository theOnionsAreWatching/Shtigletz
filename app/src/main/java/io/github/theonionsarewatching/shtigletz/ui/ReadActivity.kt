package io.github.theonionsarewatching.shtigletz.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.input.Action
import io.github.theonionsarewatching.shtigletz.mail.ImapService
import io.github.theonionsarewatching.shtigletz.mail.MailAccount
import io.github.theonionsarewatching.shtigletz.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReadActivity : BaseActivity() {

    companion object {
        const val EXTRA_UIDS = "uids"
        const val EXTRA_INDEX = "index"
        const val EXTRA_SUBJECT = "subject"
    }

    private lateinit var account: MailAccount
    private lateinit var web: SafeWebView
    private lateinit var fromText: TextView
    private lateinit var subjectText: TextView
    private lateinit var metaText: TextView

    private var uids: LongArray = LongArray(0)
    private var index: Int = 0
    private var current: ImapService.FullMessage? = null
    private val dateFmt = SimpleDateFormat("EEE, MMM d yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loaded = CredentialStore.load(this)
        if (loaded == null) { finish(); return }
        account = loaded

        setContentView(R.layout.activity_read)
        web = findViewById(R.id.bodyView)
        fromText = findViewById(R.id.readFrom)
        subjectText = findViewById(R.id.readSubject)
        metaText = findViewById(R.id.readMeta)

        uids = intent.getLongArrayExtra(EXTRA_UIDS) ?: LongArray(0)
        index = intent.getIntExtra(EXTRA_INDEX, 0)
        subjectText.text = intent.getStringExtra(EXTRA_SUBJECT) ?: ""

        findViewById<Button>(R.id.replyButton).setOnClickListener { reply() }
        findViewById<Button>(R.id.deleteButton).setOnClickListener { deleteCurrent() }
        findViewById<Button>(R.id.prevButton).setOnClickListener { move(-1) }
        findViewById<Button>(R.id.nextButton).setOnClickListener { move(+1) }

        load()
    }

    private fun load() {
        if (index !in uids.indices) { finish(); return }
        val uid = uids[index]
        metaText.text = getString(R.string.read_loading)
        web.showEmail(null, null)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ImapService(account).fetchMessage(uid) }
            }
            val msg = result.getOrNull()
            if (msg == null) {
                metaText.text = getString(
                    R.string.read_error,
                    result.exceptionOrNull()?.message ?: "not found"
                )
                return@launch
            }
            current = msg
            fromText.text = msg.envelope.fromName
            subjectText.text = msg.envelope.subject
            val date = if (msg.envelope.dateMillis > 0) {
                dateFmt.format(Date(msg.envelope.dateMillis))
            } else ""
            metaText.text = if (msg.body.blockedParts > 0) {
                getString(R.string.read_meta_blocked, date, msg.body.blockedParts)
            } else date
            web.showEmail(msg.body.plain, msg.body.html)
            // Dpad up/down scrolls the body natively once the WebView has focus.
            web.requestFocus()
        }
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

    private fun reply() {
        val msg = current ?: return
        val quoted = (msg.body.plain ?: "").lines().joinToString("\n") { "> $it" }
        startActivity(
            Intent(this, ComposeActivity::class.java)
                .putExtra(ComposeActivity.EXTRA_TO, msg.envelope.fromEmail)
                .putExtra(
                    ComposeActivity.EXTRA_SUBJECT,
                    if (msg.envelope.subject.startsWith("Re:", true)) msg.envelope.subject
                    else "Re: " + msg.envelope.subject
                )
                .putExtra(ComposeActivity.EXTRA_BODY, "\n\n$quoted")
        )
    }

    private fun deleteCurrent() {
        if (index !in uids.indices) return
        val uid = uids[index]
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ImapService(account).deleteMessage(uid) }.getOrDefault(false)
            }
            Toast.makeText(
                this@ReadActivity,
                if (ok) R.string.deleted else R.string.delete_failed,
                Toast.LENGTH_SHORT
            ).show()
            if (ok) finish()
        }
    }

    override fun onAppAction(action: Action): Boolean = when (action) {
        Action.REPLY -> { reply(); true }
        Action.DELETE -> { deleteCurrent(); true }
        Action.NEXT_MESSAGE -> { move(+1); true }
        Action.PREV_MESSAGE -> { move(-1); true }
        Action.COMPOSE -> {
            startActivity(Intent(this, ComposeActivity::class.java)); true
        }
        else -> false
    }
}
