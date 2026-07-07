package io.github.theonionsarewatching.shtigletz

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.theonionsarewatching.shtigletz.security.CredentialStore
import io.github.theonionsarewatching.shtigletz.ui.InboxActivity
import io.github.theonionsarewatching.shtigletz.ui.SetupActivity

class LauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = if (CredentialStore.load(this) != null) {
            InboxActivity::class.java
        } else {
            SetupActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }
}
