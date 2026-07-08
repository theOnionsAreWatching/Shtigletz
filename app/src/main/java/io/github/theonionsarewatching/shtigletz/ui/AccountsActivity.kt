package io.github.theonionsarewatching.shtigletz.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.db.MailDb
import io.github.theonionsarewatching.shtigletz.mail.ImapService
import io.github.theonionsarewatching.shtigletz.mail.MailAccount
import io.github.theonionsarewatching.shtigletz.security.AccountStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Account picker: every signed-in account with its unread count. */
class AccountsActivity : SoftKeyActivity() {

    private data class Row(val account: MailAccount, var unread: Int, var live: Boolean)

    private val rows = ArrayList<Row>()
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)
        val list = findViewById<RecyclerView>(R.id.accountList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = Adapter()
        list.adapter = adapter

        findViewById<Button>(R.id.addAccountButton).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Soft keys (when enabled).
        setSoftKeys(
            getString(R.string.add_account), { startActivity(Intent(this, SetupActivity::class.java)) },
            getString(R.string.settings), { startActivity(Intent(this, SettingsActivity::class.java)) }
        )
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val accounts = AccountStore.list(this)
        if (accounts.isEmpty()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        val db = MailDb.get(this)
        rows.clear()
        for (a in accounts) rows.add(Row(a, db.unreadCount(a.id, "INBOX"), live = false))
        adapter.notifyDataSetChanged()
        findViewById<RecyclerView>(R.id.accountList).post {
            findViewById<RecyclerView>(R.id.accountList).getChildAt(0)?.requestFocus()
        }
        // Refresh counts from the server in the background; cached counts stay on failure.
        for (row in rows) {
            lifecycleScope.launch {
                val live = withContext(Dispatchers.IO) {
                    runCatching { ImapService(row.account, "INBOX").unreadCount() }.getOrNull()
                }
                if (live != null) {
                    row.unread = live
                    row.live = true
                    adapter.notifyItemChanged(rows.indexOf(row))
                }
            }
        }
    }

    private fun open(account: MailAccount) {
        startActivity(
            Intent(this, MessageListActivity::class.java)
                .putExtra(MessageListActivity.EXTRA_ACCOUNT_ID, account.id)
        )
    }

    private fun longPress(account: MailAccount) {
        val items = arrayOf(
            getString(R.string.account_move_up),
            getString(R.string.account_move_down),
            getString(R.string.account_edit),
            getString(R.string.account_remove)
        )
        AlertDialog.Builder(this)
            .setTitle(account.email)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> { AccountStore.move(this, account.id, -1); reload() }
                    1 -> { AccountStore.move(this, account.id, +1); reload() }
                    2 -> startActivity(
                        Intent(this, SetupActivity::class.java)
                            .putExtra(SetupActivity.EXTRA_ACCOUNT_ID, account.id)
                    )
                    3 -> AlertDialog.Builder(this)
                        .setMessage(getString(R.string.confirm_remove_account, account.email))
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            AccountStore.remove(this, account.id)
                            MailDb.get(this).clearAccount(account.id)
                            reload()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            .show()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {
        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.accountName)
            val email: TextView = v.findViewById(R.id.accountEmail)
            val unread: TextView = v.findViewById(R.id.accountUnread)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_account, parent, false))

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            holder.name.text = row.account.displayName.ifBlank { row.account.email }
            holder.email.text = row.account.email
            holder.unread.text = resources.getQuantityString(
                R.plurals.unread_count, row.unread, row.unread
            )
            holder.unread.alpha = if (row.live) 1f else 0.6f
            holder.itemView.setOnClickListener { open(row.account) }
            holder.itemView.setOnLongClickListener { longPress(row.account); true }
        }
    }
}
