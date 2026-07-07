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

class ImapService(private val account: MailAccount) {

    data class Envelope(
        val uid: Long,
        val fromName: String,
        val fromEmail: String,
        val subject: String,
        val dateMillis: Long,
        val seen: Boolean,
        val hasAttachment: Boolean
    )

    data class FullMessage(
        val envelope: Envelope,
        val body: BodyExtractor.Extracted
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
        return Session.getInstance(props)
    }

    private fun <T> withInbox(readWrite: Boolean = false, block: (IMAPFolder) -> T): T {
        val store = session().getStore("imap")
        store.connect(account.imapHost, account.email, account.password)
        try {
            val folder = store.getFolder("INBOX") as IMAPFolder
            folder.open(if (readWrite) Folder.READ_WRITE else Folder.READ_ONLY)
            try {
                return block(folder)
            } finally {
                runCatching { folder.close(false) }
            }
        } finally {
            runCatching { store.close() }
        }
    }

    /** Connect + open INBOX and disconnect. Used by setup validation. */
    fun testConnection() {
        withInbox { /* connection + folder open is the test */ }
    }

    /**
     * Newest [limit] envelopes, envelope/flags/structure metadata only.
     * No message bodies and no attachment bytes are fetched here.
     */
    fun fetchEnvelopes(limit: Int = 50): List<Envelope> = withInbox { folder ->
        val total = folder.messageCount
        if (total <= 0) return@withInbox emptyList()
        val start = maxOf(1, total - limit + 1)
        val msgs = folder.getMessages(start, total)

        val fp = FetchProfile()
        fp.add(FetchProfile.Item.ENVELOPE)
        fp.add(FetchProfile.Item.FLAGS)
        fp.add(FetchProfile.Item.CONTENT_INFO) // BODYSTRUCTURE metadata only
        fp.add(UIDFolder.FetchProfileItem.UID)
        folder.fetch(msgs, fp)

        msgs.map { m -> toEnvelope(folder, m) }.reversed()
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
            // Heuristic from BODYSTRUCTURE only — indicates presence, nothing is downloaded.
            hasAttachment = runCatching { m.isMimeType("multipart/mixed") }.getOrDefault(false)
        )
    }

    /**
     * Fetch one message's TEXT parts only (see BodyExtractor for the guarantee)
     * and mark it seen.
     */
    fun fetchMessage(uid: Long): FullMessage? = withInbox(readWrite = true) { folder ->
        val m = folder.getMessageByUID(uid) ?: return@withInbox null
        val body = BodyExtractor.extract(m)
        runCatching { folder.setFlags(arrayOf(m), Flags(Flags.Flag.SEEN), true) }
        FullMessage(toEnvelope(folder, m), body)
    }

    fun deleteMessage(uid: Long): Boolean = withInbox(readWrite = true) { folder ->
        val m = folder.getMessageByUID(uid) ?: return@withInbox false
        folder.setFlags(arrayOf(m), Flags(Flags.Flag.DELETED), true)
        folder.expunge()
        true
    }
}
