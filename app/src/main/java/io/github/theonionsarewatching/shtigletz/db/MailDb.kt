package io.github.theonionsarewatching.shtigletz.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.theonionsarewatching.shtigletz.mail.ImapService

/**
 * Offline message cache. Plain SQLite (no annotation processing) so the CI
 * build stays simple and deterministic. Envelopes are cached on every refresh;
 * bodies are cached when a message is opened or prefetched. The list and any
 * cached message render fully offline.
 *
 * Note: bodies stored here are the SAME text-only content produced by
 * BodyExtractor — attachments were never fetched, so nothing non-kosher can
 * end up in this cache.
 */
class MailDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "dmail.db", null, 1) {

    data class CachedMessage(
        val accountId: String,
        val folder: String,
        val uid: Long,
        val fromName: String,
        val fromEmail: String,
        val subject: String,
        val dateMillis: Long,
        val seen: Boolean,
        val flagged: Boolean,
        val hasAttachment: Boolean,
        val bodyPlain: String?,
        val bodyHtml: String?,
        val blockedParts: Int,
        val bodyFetched: Boolean
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE messages(
                accountId TEXT NOT NULL,
                folder TEXT NOT NULL,
                uid INTEGER NOT NULL,
                fromName TEXT NOT NULL DEFAULT '',
                fromEmail TEXT NOT NULL DEFAULT '',
                subject TEXT NOT NULL DEFAULT '',
                dateMillis INTEGER NOT NULL DEFAULT 0,
                seen INTEGER NOT NULL DEFAULT 0,
                flagged INTEGER NOT NULL DEFAULT 0,
                hasAttachment INTEGER NOT NULL DEFAULT 0,
                bodyPlain TEXT,
                bodyHtml TEXT,
                blockedParts INTEGER NOT NULL DEFAULT 0,
                bodyFetched INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(accountId, folder, uid))"""
        )
        db.execSQL("CREATE INDEX idx_messages_list ON messages(accountId, folder, dateMillis DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 — nothing to migrate yet.
    }

    /** Upsert envelope fields, preserving any cached body. */
    fun upsertEnvelope(accountId: String, folder: String, e: ImapService.Envelope) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("fromName", e.fromName)
            put("fromEmail", e.fromEmail)
            put("subject", e.subject)
            put("dateMillis", e.dateMillis)
            put("seen", if (e.seen) 1 else 0)
            put("flagged", if (e.flagged) 1 else 0)
            put("hasAttachment", if (e.hasAttachment) 1 else 0)
        }
        val updated = db.update(
            "messages", cv,
            "accountId=? AND folder=? AND uid=?",
            arrayOf(accountId, folder, e.uid.toString())
        )
        if (updated == 0) {
            cv.put("accountId", accountId)
            cv.put("folder", folder)
            cv.put("uid", e.uid)
            db.insertWithOnConflict("messages", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun saveBody(accountId: String, folder: String, uid: Long, plain: String?, html: String?, blockedParts: Int) {
        val cv = ContentValues().apply {
            put("bodyPlain", plain)
            put("bodyHtml", html)
            put("blockedParts", blockedParts)
            put("bodyFetched", 1)
        }
        writableDatabase.update(
            "messages", cv,
            "accountId=? AND folder=? AND uid=?",
            arrayOf(accountId, folder, uid.toString())
        )
    }

    fun messages(accountId: String, folder: String, unreadFirst: Boolean = false): List<CachedMessage> {
        val out = ArrayList<CachedMessage>()
        val order = if (unreadFirst) "seen ASC, dateMillis DESC" else "dateMillis DESC"
        readableDatabase.query(
            "messages", null,
            "accountId=? AND folder=?",
            arrayOf(accountId, folder),
            null, null, order
        ).use { c -> while (c.moveToNext()) out.add(read(c)) }
        return out
    }

    fun message(accountId: String, folder: String, uid: Long): CachedMessage? {
        readableDatabase.query(
            "messages", null,
            "accountId=? AND folder=? AND uid=?",
            arrayOf(accountId, folder, uid.toString()),
            null, null, null
        ).use { c -> return if (c.moveToFirst()) read(c) else null }
    }

    fun setSeen(accountId: String, folder: String, uid: Long, seen: Boolean) {
        val cv = ContentValues().apply { put("seen", if (seen) 1 else 0) }
        writableDatabase.update(
            "messages", cv, "accountId=? AND folder=? AND uid=?",
            arrayOf(accountId, folder, uid.toString())
        )
    }

    fun setFlagged(accountId: String, folder: String, uid: Long, flagged: Boolean) {
        val cv = ContentValues().apply { put("flagged", if (flagged) 1 else 0) }
        writableDatabase.update(
            "messages", cv, "accountId=? AND folder=? AND uid=?",
            arrayOf(accountId, folder, uid.toString())
        )
    }

    /** Remove a local row (after server delete, or after a move — the target
     *  folder assigns a new UID, so the next refresh of that folder re-caches it). */
    fun delete(accountId: String, folder: String, uid: Long) {
        writableDatabase.delete(
            "messages", "accountId=? AND folder=? AND uid=?",
            arrayOf(accountId, folder, uid.toString())
        )
    }

    fun unreadCount(accountId: String, folder: String = "INBOX"): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE accountId=? AND folder=? AND seen=0",
            arrayOf(accountId, folder)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun clearAccount(accountId: String) {
        writableDatabase.delete("messages", "accountId=?", arrayOf(accountId))
    }

    private fun read(c: Cursor): CachedMessage = CachedMessage(
        accountId = c.getString(c.getColumnIndexOrThrow("accountId")),
        folder = c.getString(c.getColumnIndexOrThrow("folder")),
        uid = c.getLong(c.getColumnIndexOrThrow("uid")),
        fromName = c.getString(c.getColumnIndexOrThrow("fromName")),
        fromEmail = c.getString(c.getColumnIndexOrThrow("fromEmail")),
        subject = c.getString(c.getColumnIndexOrThrow("subject")),
        dateMillis = c.getLong(c.getColumnIndexOrThrow("dateMillis")),
        seen = c.getInt(c.getColumnIndexOrThrow("seen")) == 1,
        flagged = c.getInt(c.getColumnIndexOrThrow("flagged")) == 1,
        hasAttachment = c.getInt(c.getColumnIndexOrThrow("hasAttachment")) == 1,
        bodyPlain = c.getString(c.getColumnIndexOrThrow("bodyPlain")),
        bodyHtml = c.getString(c.getColumnIndexOrThrow("bodyHtml")),
        blockedParts = c.getInt(c.getColumnIndexOrThrow("blockedParts")),
        bodyFetched = c.getInt(c.getColumnIndexOrThrow("bodyFetched")) == 1
    )

    companion object {
        @Volatile private var instance: MailDb? = null
        fun get(context: Context): MailDb =
            instance ?: synchronized(this) {
                instance ?: MailDb(context).also { instance = it }
            }
    }
}
