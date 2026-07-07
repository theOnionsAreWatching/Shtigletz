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
import io.github.theonionsarewatching.shtigletz.mail.ImapService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val scope: CoroutineScope,
    private val onOpen: (position: Int) -> Unit
) : RecyclerView.Adapter<MessageAdapter.Holder>() {

    private val items = ArrayList<ImapService.Envelope>()
    private val dateFmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    fun submit(list: List<ImapService.Envelope>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): ImapService.Envelope? = items.getOrNull(position)

    fun uidList(): LongArray = LongArray(items.size) { items[it].uid }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: ImageView = v.findViewById(R.id.avatar)
        val monogram: TextView = v.findViewById(R.id.monogram)
        val from: TextView = v.findViewById(R.id.fromText)
        val subject: TextView = v.findViewById(R.id.subjectText)
        val date: TextView = v.findViewById(R.id.dateText)
        val attachment: TextView = v.findViewById(R.id.attachmentFlag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return Holder(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val e = items[position]
        holder.from.text = e.fromName
        holder.subject.text = e.subject
        holder.date.text = if (e.dateMillis > 0) dateFmt.format(Date(e.dateMillis)) else ""
        val style = if (e.seen) Typeface.NORMAL else Typeface.BOLD
        holder.from.setTypeface(null, style)
        holder.subject.setTypeface(null, style)
        // Indicator only: the attachment itself is never downloaded.
        holder.attachment.visibility = if (e.hasAttachment) View.VISIBLE else View.GONE

        // Avatar: local contact photo or monogram. The only image source in the app.
        holder.monogram.text = e.fromName.trim().firstOrNull()?.uppercase() ?: "?"
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
    }
}
