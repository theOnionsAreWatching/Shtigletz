package io.github.theonionsarewatching.shtigletz.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.mail.MailAccount
import io.github.theonionsarewatching.shtigletz.mail.SmtpService
import io.github.theonionsarewatching.shtigletz.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComposeActivity : BaseActivity() {

    companion object {
        const val EXTRA_TO = "to"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_BODY = "body"
    }

    private lateinit var account: MailAccount

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loaded = CredentialStore.load(this)
        if (loaded == null) { finish(); return }
        account = loaded

        setContentView(R.layout.activity_compose)
        val to = findViewById<EditText>(R.id.composeTo)
        val cc = findViewById<EditText>(R.id.composeCc)
        val subject = findViewById<EditText>(R.id.composeSubject)
        val body = findViewById<EditText>(R.id.composeBody)
        val send = findViewById<Button>(R.id.sendButton)

        to.setText(intent.getStringExtra(EXTRA_TO) ?: "")
        subject.setText(intent.getStringExtra(EXTRA_SUBJECT) ?: "")
        body.setText(intent.getStringExtra(EXTRA_BODY) ?: "")

        send.setOnClickListener {
            val toValue = to.text.toString().trim()
            if (toValue.isBlank()) {
                Toast.makeText(this, R.string.compose_missing_to, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            send.isEnabled = false
            lifecycleScope.launch {
                val error: String? = withContext(Dispatchers.IO) {
                    try {
                        SmtpService(account).send(
                            to = toValue,
                            cc = cc.text.toString(),
                            subject = subject.text.toString(),
                            body = body.text.toString()
                        )
                        null
                    } catch (e: Exception) {
                        (e.message ?: e.javaClass.simpleName).take(200)
                    }
                }
                if (error == null) {
                    Toast.makeText(this@ComposeActivity, R.string.compose_sent, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    send.isEnabled = true
                    Toast.makeText(
                        this@ComposeActivity,
                        getString(R.string.compose_failed, error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
