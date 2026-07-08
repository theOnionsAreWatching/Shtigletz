package io.github.theonionsarewatching.shtigletz.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.contacts.ContactPhotos
import io.github.theonionsarewatching.shtigletz.db.MailDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val scope: CoroutineScope,
    private val onOpen: (position: Int) -> Unit,
    private val onLongPress: (position: Int) -> Unit
) : RecyclerView.Adapter<MessageAdapter.Holder>() {

    private val items = ArrayList<MailDb.CachedMessage>()

    fun submit(list: List<MailDb.CachedMessage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): MailDb.CachedMessage? = items.getOrNull(position)

    fun uidList(): LongArray = LongArray(items.size) { items[it].uid }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val unreadDot: View = v.findViewById(R.id.unreadDot)
        val avatar: ImageView = v.findViewById(R.id.avatar)
        val monogram: TextView = v.findViewById(R.id.monogram)
        val from: TextView = v.findViewById(R.id.fromText)
        val subject: TextView = v.findViewById(R.id.subjectText)
        val date: TextView = v.findViewById(R.id.dateText)
        val star: TextView = v.findViewById(R.id.starFlag)
        val attachment: TextView = v.findViewById(R.id.attachmentFlag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return Holder(v)
    }

    override fun getItemCount(): Int = items.size

    /** Follows the system 12/24-hour setting; shows the year once a message
     *  is more than a year old (and drops the time, which no longer matters). */
    private fun formatDate(context: android.content.Context, millis: Long): String {
        if (millis <= 0) return ""
        val overAYear = System.currentTimeMillis() - millis > 365L * 24 * 60 * 60 * 1000
        val pattern = when {
            overAYear -> "MMM d, yyyy"
            android.text.format.DateFormat.is24HourFormat(context) -> "MMM d, HH:mm"
            else -> "MMM d, h:mm a"
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val e = items[position]
        holder.from.text = e.fromName
        holder.subject.text = e.subject
        holder.date.text = formatDate(holder.itemView.context, e.dateMillis)

        // Unread must be unmistakable: dot + bold + full brightness.
        // Read rows are clearly dimmed.
        holder.unreadDot.visibility = if (e.seen) View.INVISIBLE else View.VISIBLE
        val style = if (e.seen) Typeface.NORMAL else Typeface.BOLD
        holder.from.setTypeface(null, style)
        holder.subject.setTypeface(null, style)
        val alpha = if (e.seen) 0.55f else 1f
        holder.from.alpha = alpha
        holder.subject.alpha = alpha
        holder.date.alpha = alpha

        holder.star.visibility = if (e.flagged) View.VISIBLE else View.GONE
        // Indicator only: the attachment itself is never downloaded.
        holder.attachment.visibility = if (e.hasAttachment) View.VISIBLE else View.GONE

        // Avatar: local contact photo or monogram. The only image source in the app.
        holder.monogram.text = (e.fromName.trim().firstOrNull()?.uppercase() ?: "?")
        holder.monogram.visibility = View.VISIBLE
        holder.avatar.setImageBitmap(null)
        val email = e.fromEmail
        val ctx = holder.itemView.context.applicationContext
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { ContactPhotos.lookup(ctx, email) }
            if (holder.bindingAdapterPosition == position && bmp != null) {
                holder.avatar.setImageBitmap(bmp)
                holder.monogram.visibility = View.GONE
            }
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onOpen(pos)
        }
        // Long-press (touch, or hold SELECT on a dpad) opens the actions menu.
        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onLongPress(pos)
            true
        }
    }
}
