package io.github.theonionsarewatching.shtigletz.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.Settings
import io.github.theonionsarewatching.shtigletz.mail.ImapService
import io.github.theonionsarewatching.shtigletz.mail.MailAccount
import io.github.theonionsarewatching.shtigletz.ui.MessageListActivity
import java.util.concurrent.TimeUnit

object Notifier {

    private const val CHANNEL_ID = "new_mail"
    private const val WORK_NAME = "dmail-mail-check"

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notify_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        ensureScheduled(context)
    }

    /** Schedule or cancel the 15-minute background check to match settings. */
    fun ensureScheduled(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (Settings.notifyMode(context) == "off") {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<MailCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Post one notification per account summarizing its new messages. */
    fun notifyNewMail(context: Context, account: MailAccount, messages: List<ImapService.Envelope>) {
        if (messages.isEmpty()) return
        val showHeader = Settings.notifyShowHeader(context)
        val count = messages.size

        val intent = Intent(context, MessageListActivity::class.java)
            .putExtra(MessageListActivity.EXTRA_ACCOUNT_ID, account.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context, account.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = context.resources.getQuantityString(R.plurals.new_messages, count, count)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(account.email)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)

        if (showHeader) {
            val style = NotificationCompat.InboxStyle()
            for (m in messages.take(5)) style.addLine("${m.fromName} — ${m.subject}")
            style.setSummaryText(account.email)
            builder.setStyle(style)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(account.id.hashCode(), builder.build())
        }
    }
}
