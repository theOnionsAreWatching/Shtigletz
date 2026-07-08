package io.github.theonionsarewatching.shtigletz.mail

import io.github.theonionsarewatching.shtigletz.FlavorConfig
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.internet.MimeUtility

/**
 * KOSHER ENFORCEMENT — NETWORK LAYER (kosher flavor). Compiled in per flavor.
 *
 * Walks a message's MIME tree and reads ONLY text/plain and text/html parts.
 * With Jakarta Mail over IMAP, calling getContent() on a Multipart fetches the
 * BODYSTRUCTURE (metadata only), and calling getContent() on a specific text
 * part issues a FETCH for that section only. Non-text parts are counted and
 * their names/sizes recorded from BODYSTRUCTURE — metadata only; their BYTES
 * are never requested unless a fetch function below is called, and those
 * functions hard-require the flavor capability:
 *  - findBlockedPart: FlavorConfig.ATTACHMENTS (Plus, Pro)
 *  - findCidPart:     FlavorConfig.IMAGES      (Pro)
 * In the Kosher flavor both constants are false, so no code path can read a
 * non-text part's content. Do not weaken this class.
 */
object BodyExtractor {

    data class AttachmentMeta(
        val index: Int,        // stable position in traversal order
        val name: String,
        val mime: String,
        val sizeBytes: Int     // -1 when the server didn't report it
    )

    data class Extracted(
        var plain: String? = null,
        var html: String? = null,
        /** Number of non-text parts present and not fetched. */
        var blockedParts: Int = 0,
        /** Metadata (names/types/sizes) of those parts — from BODYSTRUCTURE only. */
        val attachments: MutableList<AttachmentMeta> = mutableListOf()
    )

    fun extract(part: Part): Extracted {
        val out = Extracted()
        runCatching { traverse(part, out, seekIndex = -1, seekCid = null, counter = intArrayOf(0)) }
        return out
    }

    /** The Nth blocked part (bytes will be read by the caller). Plus/Pro only. */
    fun findBlockedPart(root: Part, index: Int): Part? {
        require(FlavorConfig.ATTACHMENTS) { "attachments are not available in this flavor" }
        return runCatching {
            traverse(root, out = null, seekIndex = index, seekCid = null, counter = intArrayOf(0))
        }.getOrNull()
    }

    /** The blocked part with the given Content-ID. Pro only. */
    fun findCidPart(root: Part, cid: String): Part? {
        require(FlavorConfig.IMAGES) { "images are not available in this flavor" }
        val want = cid.trim().removePrefix("<").removeSuffix(">")
        return runCatching {
            traverse(root, out = null, seekIndex = -1, seekCid = want, counter = intArrayOf(0))
        }.getOrNull()
    }

    /**
     * One traversal, three uses: fill [out] with text bodies + metadata,
     * and/or return the blocked part matching [seekIndex] or [seekCid].
     * Blocked-part numbering is identical in every mode by construction.
     */
    private fun traverse(part: Part, out: Extracted?, seekIndex: Int, seekCid: String?, counter: IntArray): Part? {
        val disposition = runCatching { part.disposition }.getOrNull()
        val isAttachment = disposition != null && disposition.equals(Part.ATTACHMENT, ignoreCase = true)

        when {
            part.isMimeType("multipart/*") -> {
                // Structure only — this does not pull part bodies over IMAP.
                val mp = part.content as? Multipart ?: return null
                for (i in 0 until mp.count) {
                    val hit = runCatching {
                        traverse(mp.getBodyPart(i), out, seekIndex, seekCid, counter)
                    }.getOrNull()
                    if (hit != null) return hit
                }
            }
            part.isMimeType("text/plain") && !isAttachment -> {
                if (out != null && out.plain == null) out.plain = part.content as? String
            }
            part.isMimeType("text/html") && !isAttachment -> {
                if (out != null && out.html == null) out.html = part.content as? String
            }
            else -> {
                val idx = counter[0]++
                if (out != null) {
                    out.blockedParts++
                    out.attachments.add(
                        AttachmentMeta(
                            index = idx,
                            name = runCatching { part.fileName?.let { MimeUtility.decodeText(it) } }
                                .getOrNull() ?: "part-$idx",
                            mime = runCatching { part.contentType.substringBefore(';').trim().lowercase() }
                                .getOrDefault("application/octet-stream"),
                            sizeBytes = runCatching { part.size }.getOrDefault(-1)
                        )
                    )
                }
                if (seekIndex == idx) return part
                if (seekCid != null) {
                    val partCid = runCatching { part.getHeader("Content-ID")?.firstOrNull() }
                        .getOrNull()?.trim()?.removePrefix("<")?.removeSuffix(">")
                    if (partCid != null && partCid.equals(seekCid, ignoreCase = true)) return part
                }
            }
        }
        return null
    }
}
