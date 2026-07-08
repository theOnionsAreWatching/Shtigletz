package io.github.theonionsarewatching.shtigletz.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

/** Message list for one account + one folder. Renders the offline cache
 *  instantly, then syncs from the server when reachable. */
class MessageListActivity : ScaledActivity() {

    companion object {
        const val EXTRA_ACCOUNT_ID = "accountId"
        const val EXTRA_FOLDER = "folder"
    }

    private lateinit var account: MailAccount
    private var folder: String = "INBOX"
    private lateinit var db: MailDb
    private lateinit var adapter: MessageAdapter
    private lateinit var list: RecyclerView
    private lateinit var status: TextView
    private lateinit var titleView: TextView
    private lateinit var loadOlder: Button

    /** Lowest sequence number fetched this session; null until a successful refresh. */
    private var windowStart: Int? = null
    private var loading = false

    private val autoRefreshHandler = Handler(Looper.getMainLooper())
    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            refresh(silent = true)
            scheduleAutoRefresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loaded = AccountStore.get(this, intent.getStringExtra(EXTRA_ACCOUNT_ID))
            ?: AccountStore.list(this).firstOrNull()
        if (loaded == null) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        account = loaded
        folder = intent.getStringExtra(EXTRA_FOLDER) ?: "INBOX"
        db = MailDb.get(this)

        setContentView(R.layout.activity_message_list)
        titleView = findViewById(R.id.listTitle)
        status = findViewById(R.id.listStatus)
        loadOlder = findViewById(R.id.loadOlderButton)
        list = findViewById(R.id.messageList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter(
            lifecycleScope,
            onOpen = { position -> openMessage(position) },
            onLongPress = { position -> showActions(position) }
        )
        list.adapter = adapter
        updateTitle()

        findViewById<Button>(R.id.composeButton).setOnClickListener { compose() }
        findViewById<Button>(R.id.refreshButton).setOnClickListener { refresh(silent = false) }
        findViewById<Button>(R.id.foldersButton).setOnClickListener { pickFolder() }
        findViewById<Button>(R.id.moreButton).setOnClickListener { moreMenu() }
        loadOlder.setOnClickListener { loadOlderPage() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 1)
        }

        renderCache(focusFirst = true)
        refresh(silent = adapter.itemCount > 0)
    }

    override fun onResume() {
        super.onResume()
        renderCache(focusFirst = false)
        scheduleAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
    }

    private fun scheduleAutoRefresh() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
        val minutes = Settings.autoRefreshMinutes(this)
        if (minutes > 0) {
            autoRefreshHandler.postDelayed(autoRefreshRunnable, minutes * 60_000L)
        }
    }

    private fun updateTitle() {
        val who = account.displayName.ifBlank { account.email.substringBefore("@") }
        titleView.text = getString(R.string.list_title_fmt, who, folder)
    }

    private fun renderCache(focusFirst: Boolean) {
        adapter.submit(db.messages(account.id, folder, Settings.sortUnreadFirst(this)))
        if (focusFirst) list.post { list.getChildAt(0)?.requestFocus() }
    }

    private fun refresh(silent: Boolean) {
        if (loading) return
        loading = true
        if (!silent) showStatus(getString(R.string.list_loading))
        lifecycleScope.launch {
            val pageSize = Settings.pageSize(this@MessageListActivity)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val svc = ImapService(account, folder)
                    val page = svc.fetchPage(null, pageSize)
                    for (e in page.envelopes) db.upsertEnvelope(account.id, folder, e)
                    prefetchBodies(svc, page.envelopes)
                    page
                }
            }
            loading = false
            result.onSuccess { page ->
                windowStart = page.windowStart
                loadOlder.visibility = if (page.windowStart > 1) View.VISIBLE else View.GONE
                renderCache(focusFirst = false)
                if (adapter.itemCount == 0) showStatus(getString(R.string.list_empty))
                else hideStatus()
            }.onFailure {
                if (adapter.itemCount > 0) showStatus(getString(R.string.list_offline_cached))
                else showStatus(getString(R.string.list_error, it.message ?: it.javaClass.simpleName))
            }
        }
    }

    private fun loadOlderPage() {
        val before = windowStart ?: return
        if (loading || before <= 1) return
        loading = true
        showStatus(getString(R.string.list_loading))
        lifecycleScope.launch {
            val pageSize = Settings.pageSize(this@MessageListActivity)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val page = ImapService(account, folder).fetchPage(before, pageSize)
                    for (e in page.envelopes) db.upsertEnvelope(account.id, folder, e)
                    page
                }
            }
            loading = false
            result.onSuccess { page ->
                windowStart = page.windowStart
                loadOlder.visibility = if (page.windowStart > 1) View.VISIBLE else View.GONE
                renderCache(focusFirst = false)
                hideStatus()
            }.onFailure {
                showStatus(getString(R.string.list_error, it.message ?: it.javaClass.simpleName))
            }
        }
    }

    /** Cache newest bodies so they read offline. Uses PEEK — does not mark seen. */
    private fun prefetchBodies(svc: ImapService, envelopes: List<ImapService.Envelope>) {
        val limit = Settings.prefetchBodies(this)
        if (limit <= 0) return
        var done = 0
        for (e in envelopes) {
            if (done >= limit) break
            val cached = db.message(account.id, folder, e.uid)
            if (cached?.bodyFetched == true) continue
            runCatching {
                svc.fetchMessage(e.uid, markSeen = false)?.let { full ->
                    db.saveBody(
                        account.id, folder, e.uid,
                        full.body.plain, full.body.html, full.body.blockedParts
                    )
                }
            }
            done++
        }
    }

    private fun openMessage(position: Int) {
        val msg = adapter.getItem(position) ?: return
        startActivity(
            Intent(this, ReadActivity::class.java)
                .putExtra(ReadActivity.EXTRA_ACCOUNT_ID, account.id)
                .putExtra(ReadActivity.EXTRA_FOLDER, folder)
                .putExtra(ReadActivity.EXTRA_UIDS, adapter.uidList())
                .putExtra(ReadActivity.EXTRA_INDEX, position)
        )
    }

    private fun compose() {
        startActivity(
            Intent(this, ComposeActivity::class.java)
                .putExtra(ComposeActivity.EXTRA_ACCOUNT_ID, account.id)
        )
    }

    // ---- Folder picker ----

    private fun pickFolder() {
        showStatus(getString(R.string.folders_loading))
        lifecycleScope.launch {
            val folders = withContext(Dispatchers.IO) {
                runCatching { ImapService(account).listFolders() }.getOrNull()
            }
            hideStatus()
            if (folders.isNullOrEmpty()) {
                Toast.makeText(this@MessageListActivity, R.string.folders_error, Toast.LENGTH_SHORT).show()
                return@launch
            }
            AlertDialog.Builder(this@MessageListActivity)
                .setTitle(R.string.folders_title)
                .setItems(folders.toTypedArray()) { _, which ->
                    folder = folders[which]
                    windowStart = null
                    loadOlder.visibility = View.GONE
                    updateTitle()
                    renderCache(focusFirst = true)
                    refresh(silent = false)
                }
                .show()
        }
    }

    // ---- More menu ----

    private fun moreMenu() {
        val items = arrayOf(getString(R.string.menu_accounts), getString(R.string.menu_settings))
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, AccountsActivity::class.java))
                    1 -> startActivity(Intent(this, SettingsActivity::class.java))
                }
            }
            .show()
    }

    // ---- Long-press message actions ----

    private fun showActions(position: Int) {
        val msg = adapter.getItem(position) ?: return
        val items = arrayOf(
            if (msg.seen) getString(R.string.action_mark_unread) else getString(R.string.action_mark_read),
            if (msg.flagged) getString(R.string.action_unstar) else getString(R.string.action_star),
            getString(R.string.action_reply),
            getString(R.string.action_forward),
            getString(R.string.action_move),
            getString(R.string.action_trash),
            getString(R.string.action_delete_perm)
        )
        AlertDialog.Builder(this)
            .setTitle(msg.subject.ifBlank { msg.fromName })
            .setItems(items) { _, which ->
                when (which) {
                    0 -> toggleSeen(msg.uid, !msg.seen)
                    1 -> toggleFlag(msg.uid, !msg.flagged)
                    2 -> replyTo(msg.uid)
                    3 -> forward(msg.uid)
                    4 -> moveDialog(msg.uid)
                    5 -> moveToTrash(msg.uid)
                    6 -> confirmDeletePermanent(msg.uid)
                }
            }
            .show()
    }

    private fun toggleSeen(uid: Long, seen: Boolean) {
        db.setSeen(account.id, folder, uid, seen)
        renderCache(focusFirst = false)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { ImapService(account, folder).setSeen(uid, seen) }
        }
    }

    private fun toggleFlag(uid: Long, flagged: Boolean) {
        db.setFlagged(account.id, folder, uid, flagged)
        renderCache(focusFirst = false)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { ImapService(account, folder).setFlagged(uid, flagged) }
        }
    }

    /** Body from cache or network, then hand off to the composer builder. */
    private fun withBody(uid: Long, block: (MailDb.CachedMessage) -> Unit) {
        val cached = db.message(account.id, folder, uid)
        if (cached != null && cached.bodyFetched) {
            block(cached)
            return
        }
        showStatus(getString(R.string.list_loading))
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    ImapService(account, folder).fetchMessage(uid, markSeen = false)?.let { full ->
                        db.saveBody(account.id, folder, uid, full.body.plain, full.body.html, full.body.blockedParts)
                    }
                }
            }
            hideStatus()
            db.message(account.id, folder, uid)?.let(block)
        }
    }

    private fun bodyText(msg: MailDb.CachedMessage): String =
        msg.bodyPlain ?: msg.bodyHtml?.let { MailText.htmlToPlain(it) } ?: ""

    private fun replyTo(uid: Long) {
        withBody(uid) { msg ->
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
    }

    private fun forward(uid: Long) {
        withBody(uid) { msg ->
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
    }

    private fun moveDialog(uid: Long) {
        lifecycleScope.launch {
            val folders = withContext(Dispatchers.IO) {
                runCatching { ImapService(account).listFolders() }.getOrNull()
            }
            val targets = folders?.filter { it != folder }
            if (targets.isNullOrEmpty()) {
                Toast.makeText(this@MessageListActivity, R.string.folders_error, Toast.LENGTH_SHORT).show()
                return@launch
            }
            AlertDialog.Builder(this@MessageListActivity)
                .setTitle(R.string.action_move)
                .setItems(targets.toTypedArray()) { _, which -> doMove(uid, targets[which]) }
                .show()
        }
    }

    private fun doMove(uid: Long, target: String) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ImapService(account, folder).moveTo(uid, target) }.getOrDefault(false)
            }
            if (ok) {
                db.delete(account.id, folder, uid)
                renderCache(focusFirst = false)
                Toast.makeText(this@MessageListActivity, getString(R.string.moved_fmt, target), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MessageListActivity, R.string.action_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun moveToTrash(uid: Long) {
        lifecycleScope.launch {
            val trash = withContext(Dispatchers.IO) {
                runCatching { ImapService(account).findTrashFolder() }.getOrNull()
            }
            if (trash != null && trash != folder) doMove(uid, trash)
            else confirmDeletePermanent(uid)
        }
    }

    private fun confirmDeletePermanent(uid: Long) {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete_perm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching { ImapService(account, folder).deletePermanent(uid) }.getOrDefault(false)
                    }
                    if (ok) {
                        db.delete(account.id, folder, uid)
                        renderCache(focusFirst = false)
                        Toast.makeText(this@MessageListActivity, R.string.deleted, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MessageListActivity, R.string.action_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showStatus(text: String) {
        status.visibility = View.VISIBLE
        status.text = text
    }

    private fun hideStatus() {
        status.visibility = View.GONE
    }
}
