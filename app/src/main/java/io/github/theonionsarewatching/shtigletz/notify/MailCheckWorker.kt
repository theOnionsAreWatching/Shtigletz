package io.github.theonionsarewatching.shtigletz.notify

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.theonionsarewatching.shtigletz.Settings
import io.github.theonionsarewatching.shtigletz.db.MailDb
import io.github.theonionsarewatching.shtigletz.mail.ImapService
import io.github.theonionsarewatching.shtigletz.mail.MailAccount
import io.github.theonionsarewatching.shtigletz.security.AccountStore

/**
 * Periodic background check for new mail. Envelope metadata only — the same
 * kosher IMAP layer; no bodies, no attachments.
 *
 * Per-account state ("highest UID seen") lives in the notify_state prefs so
 * enabling notifications never floods with old mail: the first pass just
 * records the current newest UID silently.
 */
class MailCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        if (Settings.notifyMode(ctx) == "off") return Result.success()
        for (account in AccountStore.list(ctx)) {
            if (!Settings.accountNotifiable(ctx, account.id)) continue
            runCatching { check(ctx, account) }
        }
        return Result.success()
    }

    private fun check(ctx: Context, account: MailAccount) {
        val state = ctx.getSharedPreferences("notify_state", Context.MODE_PRIVATE)
        val key = "lastUid_${account.id}"
        val lastUid = state.getLong(key, 0L)

        val page = ImapService(account, "INBOX").fetchPage(null, 10)
        if (page.envelopes.isEmpty()) return
        val maxUid = page.envelopes.maxOf { it.uid }

        // Cache envelopes so the app opens fresh from the notification.
        val db = MailDb.get(ctx)
        for (e in page.envelopes) db.upsertEnvelope(account.id, "INBOX", e)

        if (lastUid == 0L) {
            // First run after enabling: set the baseline, don't notify old mail.
            state.edit().putLong(key, maxUid).apply()
            return
        }

        val fresh = page.envelopes.filter { it.uid > lastUid && !it.seen }
        state.edit().putLong(key, maxOf(lastUid, maxUid)).apply()
        if (fresh.isNotEmpty()) Notifier.notifyNewMail(ctx, account, fresh)
    }
}
