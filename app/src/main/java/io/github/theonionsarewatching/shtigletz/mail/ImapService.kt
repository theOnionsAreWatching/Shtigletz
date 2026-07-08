package io.github.theonionsarewatching.shtigletz.mail

import com.sun.mail.imap.IMAPFolder
import java.util.Properties
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress

class ImapService(
    private val account: MailAccount,
    private val folderName: String = "INBOX"
) {

    data class Envelope(
        val uid: Long,
        val fromName: String,
        val fromEmail: String,
        val subject: String,
        val dateMillis: Long,
        val seen: Boolean,
        val flagged: Boolean,
        val hasAttachment: Boolean
    )

    data class FullMessage(
        val envelope: Envelope,
        val body: BodyExtractor.Extracted
    )

    /** One page of envelopes plus the info needed to page further back. */
    data class Page(
        val envelopes: List<Envelope>,
        val totalCount: Int,
        val windowStart: Int   // lowest sequence number fetched; >1 means older mail exists
    )

    private fun session(): Session {
        val props = Properties()
        props["mail.store.protocol"] = "imap"
        props["mail.imap.host"] = account.imapHost
        props["mail.imap.port"] = account.imapPort.toString()
        if (account.imapSsl) {
            props["mail.imap.ssl.enable"] = "true"
        } else {
            props["mail.imap.starttls.enable"] = "true"
            props["mail.imap.starttls.required"] = "true"
        }
        props["mail.imap.connectiontimeout"] = "15000"
        props["mail.imap.timeout"] = "30000"
        props["mail.imap.partialfetch"] = "true"
        // PEEK: reading a body must NOT implicitly mark the message seen —
        // offline prefetch depends on this. Seen is set explicitly.
        props["mail.imap.peek"] = "true"
        return Session.getInstance(props)
    }

    private fun <T> withStore(block: (javax.mail.Store) -> T): T {
        val store = session().getStore("imap")
        store.connect(account.imapHost, account.email, account.password)
        try {
            return block(store)
        } finally {
            runCatching { store.close() }
        }
    }

    private fun <T> withFolder(readWrite: Boolean = false, block: (IMAPFolder) -> T): T =
        withStore { store ->
            val folder = store.getFolder(folderName) as IMAPFolder
            folder.open(if (readWrite) Folder.READ_WRITE else Folder.READ_ONLY)
            try {
                block(folder)
            } finally {
                runCatching { folder.close(false) }
            }
        }

    /** Connect + open the folder and disconnect. Used by setup validation. */
    fun testConnection() {
        withFolder { /* connection + folder open is the test */ }
    }

    /** All selectable folders on the server. */
    fun listFolders(): List<String> = withStore { store ->
        store.defaultFolder.list("*")
            .filter { (it.type and Folder.HOLDS_MESSAGES) != 0 }
            .map { it.fullName }
            .sorted()
    }

    /** Server-side unread count via STATUS (no folder open needed). */
    fun unreadCount(): Int = withStore { store ->
        store.getFolder(folderName).unreadMessageCount
    }

    /**
     * Envelope metadata only — no bodies, no attachment bytes.
     * Pass beforeSeq=null for the newest page; pass the previous Page's
     * windowStart to load the next older page.
     */
    fun fetchPage(beforeSeq: Int?, pageSize: Int): Page = withFolder { folder ->
        val total = folder.messageCount
        val end = (beforeSeq?.minus(1) ?: total)
        if (total <= 0 || end < 1) return@withFolder Page(emptyList(), total, 1)
        val start = maxOf(1, end - pageSize + 1)
        val msgs = folder.getMessages(start, end)

        val fp = FetchProfile()
        fp.add(FetchProfile.Item.ENVELOPE)
        fp.add(FetchProfile.Item.FLAGS)
        fp.add(FetchProfile.Item.CONTENT_INFO) // BODYSTRUCTURE metadata only
        fp.add(UIDFolder.FetchProfileItem.UID)
        folder.fetch(msgs, fp)

        Page(msgs.map { toEnvelope(folder, it) }.reversed(), total, start)
    }

    private fun toEnvelope(folder: IMAPFolder, m: Message): Envelope {
        val from = runCatching { m.from?.firstOrNull() as? InternetAddress }.getOrNull()
        return Envelope(
            uid = folder.getUID(m),
            fromName = from?.personal ?: from?.address ?: "(unknown)",
            fromEmail = from?.address ?: "",
            subject = runCatching { m.subject }.getOrNull() ?: "(no subject)",
            dateMillis = (runCatching { m.receivedDate }.getOrNull()
                ?: runCatching { m.sentDate }.getOrNull())?.time ?: 0L,
            seen = m.isSet(Flags.Flag.SEEN),
            flagged = m.isSet(Flags.Flag.FLAGGED),
            // Heuristic from BODYSTRUCTURE only — indicator, nothing downloaded.
            hasAttachment = runCatching { m.isMimeType("multipart/mixed") }.getOrDefault(false)
        )
    }

    /**
     * Fetch one message's TEXT parts only (see BodyExtractor for the kosher
     * guarantee). mail.imap.peek is on, so this does NOT mark the message
     * seen unless markSeen is true.
     */
    fun fetchMessage(uid: Long, markSeen: Boolean): FullMessage? =
        withFolder(readWrite = markSeen) { folder ->
            val m = folder.getMessageByUID(uid) ?: return@withFolder null
            val body = BodyExtractor.extract(m)
            if (markSeen) {
                runCatching { folder.setFlags(arrayOf(m), Flags(Flags.Flag.SEEN), true) }
            }
            FullMessage(toEnvelope(folder, m), body)
        }

    fun setSeen(uid: Long, seen: Boolean): Boolean = withFolder(readWrite = true) { folder ->
        val m = folder.getMessageByUID(uid) ?: return@withFolder false
        folder.setFlags(arrayOf(m), Flags(Flags.Flag.SEEN), seen)
        true
    }

    fun setFlagged(uid: Long, flagged: Boolean): Boolean = withFolder(readWrite = true) { folder ->
        val m = folder.getMessageByUID(uid) ?: return@withFolder false
        folder.setFlags(arrayOf(m), Flags(Flags.Flag.FLAGGED), flagged)
        true
    }

    fun deletePermanent(uid: Long): Boolean = withFolder(readWrite = true) { folder ->
        val m = folder.getMessageByUID(uid) ?: return@withFolder false
        folder.setFlags(arrayOf(m), Flags(Flags.Flag.DELETED), true)
        folder.expunge()
        true
    }

    /** MOVE if the server supports it, otherwise COPY + delete + expunge. */
    fun moveTo(uid: Long, targetFolder: String): Boolean = withFolder(readWrite = true) { folder ->
        val m = folder.getMessageByUID(uid) ?: return@withFolder false
        val dest = folder.store.getFolder(targetFolder)
        try {
            folder.moveMessages(arrayOf(m), dest)
        } catch (e: Exception) {
            folder.copyMessages(arrayOf(m), dest)
            folder.setFlags(arrayOf(m), Flags(Flags.Flag.DELETED), true)
            folder.expunge()
        }
        true
    }

    /** Download one attachment part to [dest]. Plus/Pro only. */
    fun fetchAttachmentTo(uid: Long, index: Int, dest: java.io.File): Boolean =
        withFolder { folder ->
            val m = folder.getMessageByUID(uid) ?: return@withFolder false
            val part = BodyExtractor.findBlockedPart(m, index) ?: return@withFolder false
            dest.parentFile?.mkdirs()
            part.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            true
        }

    /** Bytes + mime of an embedded cid: image. Pro only. */
    fun fetchEmbeddedByCid(uid: Long, cid: String, maxBytes: Int = 3_000_000): Pair<ByteArray, String>? =
        withFolder { folder ->
            val m = folder.getMessageByUID(uid) ?: return@withFolder null
            val part = BodyExtractor.findCidPart(m, cid) ?: return@withFolder null
            val mime = runCatching { part.contentType.substringBefore(';').trim().lowercase() }
                .getOrDefault("image/*")
            val bytes = part.inputStream.use { it.readBytes() }
            if (bytes.size > maxBytes) null else bytes to mime
        }

    /** Best-effort Trash folder detection. */
    fun findTrashFolder(): String? {
        val names = runCatching { listFolders() }.getOrDefault(emptyList())
        return names.firstOrNull { it.equals("Trash", true) }
            ?: names.firstOrNull { it.substringAfterLast('/').equals("Trash", true) }
            ?: names.firstOrNull { it.contains("trash", true) }
            ?: names.firstOrNull { it.contains("deleted", true) }
    }
}
