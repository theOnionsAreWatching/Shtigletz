package io.github.theonionsarewatching.shtigletz.mail

import javax.mail.Multipart
import javax.mail.Part

/**
 * KOSHER ENFORCEMENT — NETWORK LAYER. Compiled in; there is no toggle.
 *
 * Walks a message's MIME tree and reads ONLY text/plain and text/html parts.
 * With Jakarta Mail over IMAP, calling getContent() on a Multipart fetches the
 * BODYSTRUCTURE (metadata only), and calling getContent() on a specific text
 * part issues a FETCH for that section only. Because we NEVER call
 * getContent()/getInputStream() on any other part, attachment bytes — including
 * inline cid: images — are never requested from the server and never reach the
 * device. Attachments are not "hidden"; they are simply never downloaded.
 *
 * Do not weaken this class. Any code path that reads a non-text part's content
 * breaks the app's core guarantee.
 */
object BodyExtractor {

    data class Extracted(
        var plain: String? = null,
        var html: String? = null,
        /** Number of non-text parts that were present and deliberately not fetched. */
        var blockedParts: Int = 0
    )

    fun extract(part: Part): Extracted {
        val out = Extracted()
        runCatching { walk(part, out) }
        return out
    }

    private fun walk(part: Part, out: Extracted) {
        val disposition = runCatching { part.disposition }.getOrNull()
        val isAttachment = disposition != null && disposition.equals(Part.ATTACHMENT, ignoreCase = true)

        when {
            part.isMimeType("multipart/*") -> {
                // Structure only — this does not pull part bodies over IMAP.
                val mp = part.content as? Multipart ?: return
                for (i in 0 until mp.count) {
                    runCatching { walk(mp.getBodyPart(i), out) }
                }
            }
            part.isMimeType("text/plain") && !isAttachment -> {
                if (out.plain == null) out.plain = part.content as? String
            }
            part.isMimeType("text/html") && !isAttachment -> {
                if (out.html == null) out.html = part.content as? String
            }
            else -> {
                // Attachments, images, message/rfc822, application/*, cid: inline
                // parts, calendar payloads, S/MIME blobs, everything else:
                // counted and NEVER fetched.
                out.blockedParts++
            }
        }
    }
}
