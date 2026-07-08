package io.github.theonionsarewatching.shtigletz.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import io.github.theonionsarewatching.shtigletz.FlavorConfig
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.Settings
import io.github.theonionsarewatching.shtigletz.attach.AttachmentOps
import io.github.theonionsarewatching.shtigletz.db.MailDb
import io.github.theonionsarewatching.shtigletz.mail.BodyExtractor
import io.github.theonionsarewatching.shtigletz.mail.HttpFetcher
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
    private lateinit var attachmentsLine: TextView
    private lateinit var imagesLine: TextView

    private var uids: LongArray = LongArray(0)
    private var index: Int = 0

    // ---- Pro view state (unused when FlavorConfig.IMAGES is false) ----
    private var viewMode: SafeWebView.RenderMode = SafeWebView.RenderMode.TEXT
    private var imageSrcs: List<String> = emptyList()
    private val loadedImages = HashMap<Int, String>()   // TEXT_IMG: idx -> data URI
    private val cidImages = HashMap<String, String>()   // HTML: cid -> data URI
    private var htmlImagesOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loaded = AccountStore.get(this, intent.getStringExtra(EXTRA_ACCOUNT_ID))
        if (loaded == null) { finish(); return }
        account = loaded
        folder = intent.getStringExtra(EXTRA_FOLDER) ?: "INBOX"
        db = MailDb.get(this)
        viewMode = if (FlavorConfig.IMAGES) defaultViewMode() else SafeWebView.RenderMode.TEXT

        setContentView(R.layout.activity_read)
        web = findViewById(R.id.bodyView)
        fromText = findViewById(R.id.readFrom)
        subjectText = findViewById(R.id.readSubject)
        metaText = findViewById(R.id.readMeta)
        attachmentsLine = findViewById(R.id.readAttachments)
        imagesLine = findViewById(R.id.readImages)

        web.onLinkClicked = { url -> onTapped(url) }
        val progress = findViewById<ProgressBar>(R.id.readProgress)
        web.onScrollPercent = { pct -> progress.progress = pct }

        uids = intent.getLongArrayExtra(EXTRA_UIDS) ?: LongArray(0)
        index = intent.getIntExtra(EXTRA_INDEX, 0)

        findViewById<Button>(R.id.replyButton).setOnClickListener { reply() }
        findViewById<Button>(R.id.forwardButton).setOnClickListener { forward() }
        findViewById<Button>(R.id.deleteButton).setOnClickListener { deleteCurrent() }
        findViewById<Button>(R.id.prevButton).setOnClickListener { move(-1) }
        findViewById<Button>(R.id.nextButton).setOnClickListener { move(+1) }

        attachmentsLine.setOnClickListener { attachmentDialog() }
        imagesLine.setOnClickListener { onImagesLine() }
        if (FlavorConfig.IMAGES) {
            // Touch path for cycling the view (soft key does it too).
            metaText.setOnClickListener { cycleViewMode() }
        }

        load()
    }

    private fun defaultViewMode(): SafeWebView.RenderMode = when (Settings.viewMode(this)) {
        "text" -> SafeWebView.RenderMode.TEXT
        "html" -> SafeWebView.RenderMode.HTML
        else -> SafeWebView.RenderMode.TEXT_IMG
    }

    private fun current(): MailDb.CachedMessage? =
        if (index in uids.indices) db.message(account.id, folder, uids[index]) else null

    private fun load() {
        if (index !in uids.indices) { finish(); return }
        // Per-message image state resets.
        imageSrcs = emptyList()
        loadedImages.clear()
        cidImages.clear()
        htmlImagesOn = false

        val uid = uids[index]
        val cached = db.message(account.id, folder, uid)

        if (cached != null && cached.bodyFetched) {
            show(cached)
            if (!cached.seen) markSeen(uid)
            return
        }

        metaText.text = getString(R.string.read_loading)
        attachmentsLine.visibility = View.GONE
        imagesLine.visibility = View.GONE
        web.showPlaceholder(getString(R.string.read_loading))
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ImapService(account, folder).fetchMessage(uid, markSeen = true)?.also { full ->
                        db.upsertEnvelope(account.id, folder, full.envelope)
                        db.saveBody(
                            account.id, folder, uid,
                            full.body.plain, full.body.html, full.body.blockedParts,
                            AttachmentOps.metasToJson(full.body.attachments)
                        )
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
                web.showPlaceholder(getString(R.string.read_offline))
                updateSoftKeys()
                return@launch
            }
            show(msg)
        }
    }

    private fun dateLine(millis: Long): String {
        if (millis <= 0) return ""
        val is24 = android.text.format.DateFormat.is24HourFormat(this)
        val pattern = if (is24) "EEE, MMM d yyyy, HH:mm" else "EEE, MMM d yyyy, h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
    }

    private fun show(msg: MailDb.CachedMessage) {
        fromText.text = msg.fromName
        subjectText.text = msg.subject
        val date = dateLine(msg.dateMillis)
        metaText.text = when {
            FlavorConfig.IMAGES -> getString(R.string.read_meta_pro_fmt, date, viewModeName())
            FlavorConfig.ATTACHMENTS -> date
            msg.blockedParts > 0 -> getString(R.string.read_meta_blocked, date, msg.blockedParts)
            else -> date
        }
        findViewById<ProgressBar>(R.id.readProgress).progress = 0
        render(msg)
        updateAttachmentsLine(msg)
        updateSoftKeys()
        web.requestFocus()
    }

    private fun render(msg: MailDb.CachedMessage) {
        imageSrcs = web.showEmail(
            msg.bodyPlain, msg.bodyHtml, Settings.showLinks(this),
            mode = viewMode,
            loadedImages = loadedImages,
            cidImages = cidImages,
            networkImages = htmlImagesOn
        )
        updateImagesLine()
    }

    // ---- Attachments (Plus/Pro) ----

    private fun attachments(): List<BodyExtractor.AttachmentMeta> =
        AttachmentOps.metasFromJson(current()?.attachmentsJson)

    private fun updateAttachmentsLine(msg: MailDb.CachedMessage) {
        val metas = AttachmentOps.metasFromJson(msg.attachmentsJson)
        if (!FlavorConfig.ATTACHMENTS || metas.isEmpty()) {
            attachmentsLine.visibility = View.GONE
            return
        }
        attachmentsLine.visibility = View.VISIBLE
        attachmentsLine.text = resources.getQuantityString(
            R.plurals.read_attachments_fmt, metas.size, metas.size
        )
    }

    private fun attachmentDialog() {
        val metas = attachments()
        if (metas.isEmpty()) return
        val names = metas.map { m ->
            val size = AttachmentOps.sizeLabel(m.sizeBytes)
            if (size.isBlank()) m.name else "${m.name} ($size)"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.read_attachments_title)
            .setItems(names) { _, which -> attachmentActions(metas[which]) }
            .setNegativeButton(R.string.link_close, null)
            .show()
    }

    private fun attachmentActions(meta: BodyExtractor.AttachmentMeta) {
        val actions = arrayOf(getString(R.string.att_open), getString(R.string.att_save))
        AlertDialog.Builder(this)
            .setTitle(meta.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> withAttachmentFile(meta) { file ->
                        if (!AttachmentOps.open(this, file, meta.mime)) {
                            Toast.makeText(this, R.string.att_no_app, Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> withAttachmentFile(meta) { file ->
                        val where = AttachmentOps.saveToDownloads(this, file, meta.name, meta.mime)
                        Toast.makeText(
                            this,
                            if (where != null) getString(R.string.att_saved_fmt, where)
                            else getString(R.string.att_save_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Download (once) then act. */
    private fun withAttachmentFile(meta: BodyExtractor.AttachmentMeta, action: (java.io.File) -> Unit) {
        if (index !in uids.indices) return
        val uid = uids[index]
        val dest = AttachmentOps.cacheFile(this, uid, meta.name)
        if (dest.exists() && dest.length() > 0) { action(dest); return }
        Toast.makeText(this, R.string.att_downloading, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ImapService(account, folder).fetchAttachmentTo(uid, meta.index, dest)
                }.getOrDefault(false)
            }
            if (ok) action(dest)
            else Toast.makeText(this@ReadActivity, R.string.att_download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Images (Pro) ----

    private fun viewModeName(): String = when (viewMode) {
        SafeWebView.RenderMode.TEXT -> getString(R.string.view_text)
        SafeWebView.RenderMode.TEXT_IMG -> getString(R.string.view_textimg)
        SafeWebView.RenderMode.HTML -> getString(R.string.view_html)
    }

    private fun cycleViewMode() {
        if (!FlavorConfig.IMAGES) return
        viewMode = when (viewMode) {
            SafeWebView.RenderMode.TEXT -> SafeWebView.RenderMode.TEXT_IMG
            SafeWebView.RenderMode.TEXT_IMG -> SafeWebView.RenderMode.HTML
            SafeWebView.RenderMode.HTML -> SafeWebView.RenderMode.TEXT
        }
        Toast.makeText(this, viewModeName(), Toast.LENGTH_SHORT).show()
        current()?.let { show(it) }
    }

    private fun updateImagesLine() {
        if (!FlavorConfig.IMAGES) { imagesLine.visibility = View.GONE; return }
        when (viewMode) {
            SafeWebView.RenderMode.TEXT_IMG -> {
                if (imageSrcs.isEmpty()) { imagesLine.visibility = View.GONE; return }
                imagesLine.visibility = View.VISIBLE
                val remaining = imageSrcs.indices.count { it !in loadedImages }
                imagesLine.text =
                    if (remaining == 0) getString(R.string.images_all_loaded, imageSrcs.size)
                    else resources.getQuantityString(R.plurals.images_load_all_fmt, remaining, remaining)
            }
            SafeWebView.RenderMode.HTML -> {
                imagesLine.visibility = View.VISIBLE
                imagesLine.text =
                    if (htmlImagesOn) getString(R.string.images_loaded_html)
                    else getString(R.string.images_load_html)
            }
            else -> imagesLine.visibility = View.GONE
        }
    }

    private fun onImagesLine() {
        if (!FlavorConfig.IMAGES) return
        when (viewMode) {
            SafeWebView.RenderMode.TEXT_IMG -> loadAllImages()
            SafeWebView.RenderMode.HTML -> enableHtmlImages()
            else -> {}
        }
    }

    private fun fetchOneImage(src: String, uid: Long): String? {
        val result = if (src.startsWith("cid:", true)) {
            ImapService(account, folder).fetchEmbeddedByCid(uid, src.substringAfter(":"))
        } else {
            HttpFetcher.getImage(src)
        } ?: return null
        return "data:${result.second};base64," +
                Base64.encodeToString(result.first, Base64.NO_WRAP)
    }

    private fun loadImage(idx: Int) {
        if (index !in uids.indices || idx !in imageSrcs.indices || loadedImages.containsKey(idx)) return
        val uid = uids[index]
        val src = imageSrcs[idx]
        Toast.makeText(this, R.string.images_loading, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val dataUri = withContext(Dispatchers.IO) { runCatching { fetchOneImage(src, uid) }.getOrNull() }
            if (dataUri == null) {
                Toast.makeText(this@ReadActivity, R.string.images_failed, Toast.LENGTH_SHORT).show()
            } else {
                loadedImages[idx] = dataUri
                current()?.let { render(it) }
            }
        }
    }

    private fun loadAllImages() {
        if (index !in uids.indices) return
        val uid = uids[index]
        val todo = imageSrcs.indices.filter { it !in loadedImages }
        if (todo.isEmpty()) return
        Toast.makeText(this, R.string.images_loading, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            var failures = 0
            withContext(Dispatchers.IO) {
                for (i in todo) {
                    val dataUri = runCatching { fetchOneImage(imageSrcs[i], uid) }.getOrNull()
                    if (dataUri != null) loadedImages[i] = dataUri else failures++
                }
            }
            current()?.let { render(it) }
            if (failures > 0) {
                Toast.makeText(this@ReadActivity, R.string.images_some_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** HTML mode "Load images": fetch cid parts, then allow network images. */
    private fun enableHtmlImages() {
        if (index !in uids.indices || htmlImagesOn) return
        val uid = uids[index]
        val html = current()?.bodyHtml ?: ""
        val cids = Regex("(?i)src\\s*=\\s*([\"'])cid:([^\"']+)\\1").findAll(html)
            .map { it.groupValues[2].trim() }.distinct().toList()
        Toast.makeText(this, R.string.images_loading, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                for (cid in cids) {
                    runCatching {
                        ImapService(account, folder).fetchEmbeddedByCid(uid, cid)?.let { (bytes, mime) ->
                            cidImages[cid] =
                                "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                        }
                    }
                }
            }
            htmlImagesOn = true
            current()?.let { render(it) }
        }
    }

    // ---- Taps ----

    private fun onTapped(url: String) {
        when {
            url.startsWith("dimg:", true) -> {
                url.substringAfter(":").toIntOrNull()?.let { loadImage(it) }
            }
            url.startsWith("mailto:", true) -> {
                val addr = url.substringAfter(":").substringBefore("?")
                val b = AlertDialog.Builder(this)
                    .setTitle(R.string.email_dialog_title)
                    .setMessage(addr)
                    .setPositiveButton(R.string.link_copy) { _, _ -> copyToClipboard(addr) }
                    .setNegativeButton(R.string.link_close, null)
                b.setNeutralButton(R.string.dialog_compose) { _, _ ->
                    startActivity(
                        Intent(this, ComposeActivity::class.java)
                            .putExtra(ComposeActivity.EXTRA_ACCOUNT_ID, account.id)
                            .putExtra(ComposeActivity.EXTRA_TO, addr)
                    )
                }
                b.show()
            }
            url.startsWith("tel:", true) -> {
                val num = url.substringAfter(":")
                val b = AlertDialog.Builder(this)
                    .setTitle(R.string.phone_dialog_title)
                    .setMessage(num)
                    .setPositiveButton(R.string.link_copy) { _, _ -> copyToClipboard(num) }
                    .setNegativeButton(R.string.link_close, null)
                b.setNeutralButton(R.string.dialog_dial) { _, _ ->
                    runCatching {
                        startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$num")))
                    }
                }
                b.show()
            }
            else -> AlertDialog.Builder(this)
                .setTitle(R.string.link_dialog_title)
                .setMessage(url)
                .setPositiveButton(R.string.link_copy) { _, _ -> copyToClipboard(url) }
                .setNegativeButton(R.string.link_close, null)
                .show()
        }
    }

    private fun copyToClipboard(value: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("dmail", value))
        Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }

    // ---- Soft keys ----

    /** Left = Reply everywhere. Right = View cycle in Pro, read/unread otherwise. */
    private fun updateSoftKeys() {
        if (FlavorConfig.IMAGES) {
            setSoftKeys(
                getString(R.string.read_reply), { reply() },
                getString(R.string.softkey_view), { cycleViewMode() }
            )
        } else {
            val seen = current()?.seen != false
            setSoftKeys(
                getString(R.string.read_reply), { reply() },
                getString(if (seen) R.string.action_mark_unread else R.string.action_mark_read),
                { toggleSeen() }
            )
        }
    }

    private fun toggleSeen() {
        if (index !in uids.indices) return
        val uid = uids[index]
        val newSeen = !(current()?.seen ?: true)
        db.setSeen(account.id, folder, uid, newSeen)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { ImapService(account, folder).setSeen(uid, newSeen) }
        }
        Toast.makeText(
            this,
            if (newSeen) R.string.action_mark_read else R.string.action_mark_unread,
            Toast.LENGTH_SHORT
        ).show()
        updateSoftKeys()
    }

    private fun markSeen(uid: Long) {
        db.setSeen(account.id, folder, uid, true)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { ImapService(account, folder).setSeen(uid, true) }
        }
        updateSoftKeys()
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
