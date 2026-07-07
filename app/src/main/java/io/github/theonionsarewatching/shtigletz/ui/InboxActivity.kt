package io.github.theonionsarewatching.shtigletz.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.input.Action
import io.github.theonionsarewatching.shtigletz.mail.ImapService
import io.github.theonionsarewatching.shtigletz.mail.MailAccount
import io.github.theonionsarewatching.shtigletz.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InboxActivity : BaseActivity() {

    private lateinit var account: MailAccount
    private lateinit var adapter: MessageAdapter
    private lateinit var list: RecyclerView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loaded = CredentialStore.load(this)
        if (loaded == null) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        account = loaded

        setContentView(R.layout.activity_inbox)
        status = findViewById(R.id.inboxStatus)
        list = findViewById(R.id.messageList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter(lifecycleScope) { position -> openMessage(position) }
        list.adapter = adapter

        findViewById<Button>(R.id.composeButton).setOnClickListener {
            startActivity(Intent(this, ComposeActivity::class.java))
        }
        findViewById<Button>(R.id.refreshButton).setOnClickListener { refresh() }
        findViewById<Button>(R.id.keysButton).setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        findViewById<Button>(R.id.accountButton).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 1)
        }

        refresh()
    }

    private fun refresh() {
        status.visibility = View.VISIBLE
        status.text = getString(R.string.inbox_loading)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ImapService(account).fetchEnvelopes(50) }
            }
            result.onSuccess { envelopes ->
                adapter.submit(envelopes)
                if (envelopes.isEmpty()) {
                    status.text = getString(R.string.inbox_empty)
                } else {
                    status.visibility = View.GONE
                    list.post { list.getChildAt(0)?.requestFocus() }
                }
            }.onFailure { e ->
                status.text = getString(R.string.inbox_error, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun openMessage(position: Int) {
        val env = adapter.getItem(position) ?: return
        val intent = Intent(this, ReadActivity::class.java)
            .putExtra(ReadActivity.EXTRA_UIDS, adapter.uidList())
            .putExtra(ReadActivity.EXTRA_INDEX, position)
            .putExtra(ReadActivity.EXTRA_SUBJECT, env.subject)
        startActivity(intent)
    }

    private fun focusedPosition(): Int {
        val focused = list.focusedChild ?: return RecyclerView.NO_POSITION
        return list.getChildAdapterPosition(focused)
    }

    override fun onAppAction(action: Action): Boolean = when (action) {
        Action.COMPOSE -> {
            startActivity(Intent(this, ComposeActivity::class.java)); true
        }
        Action.MENU -> {
            refresh(); true
        }
        Action.DELETE -> {
            val pos = focusedPosition()
            val env = if (pos != RecyclerView.NO_POSITION) adapter.getItem(pos) else null
            if (env != null) {
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.confirm_delete, env.subject))
                    .setPositiveButton(android.R.string.ok) { _, _ -> delete(env.uid) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            env != null
        }
        else -> false
    }

    private fun delete(uid: Long) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ImapService(account).deleteMessage(uid) }.getOrDefault(false)
            }
            Toast.makeText(
                this@InboxActivity,
                if (ok) R.string.deleted else R.string.delete_failed,
                Toast.LENGTH_SHORT
            ).show()
            if (ok) refresh()
        }
    }
}
