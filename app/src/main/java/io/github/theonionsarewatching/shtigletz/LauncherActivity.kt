package io.github.theonionsarewatching.shtigletz

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.theonionsarewatching.shtigletz.security.AccountStore
import io.github.theonionsarewatching.shtigletz.ui.AccountsActivity
import io.github.theonionsarewatching.shtigletz.ui.MessageListActivity
import io.github.theonionsarewatching.shtigletz.ui.SetupActivity

class LauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accounts = AccountStore.list(this)
        val intent = when {
            accounts.isEmpty() -> Intent(this, SetupActivity::class.java)
            accounts.size == 1 -> Intent(this, MessageListActivity::class.java)
                .putExtra(MessageListActivity.EXTRA_ACCOUNT_ID, accounts[0].id)
            else -> Intent(this, AccountsActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}
