package io.github.theonionsarewatching.shtigletz.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.mail.ImapService
import io.github.theonionsarewatching.shtigletz.mail.MailAccount
import io.github.theonionsarewatching.shtigletz.mail.SmtpService
import io.github.theonionsarewatching.shtigletz.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SetupActivity : BaseActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val displayName = findViewById<EditText>(R.id.displayName)
        val email = findViewById<EditText>(R.id.email)
        val password = findViewById<EditText>(R.id.password)
        val imapHost = findViewById<EditText>(R.id.imapHost)
        val imapPort = findViewById<EditText>(R.id.imapPort)
        val imapSecurity = findViewById<Spinner>(R.id.imapSecurity)
        val smtpHost = findViewById<EditText>(R.id.smtpHost)
        val smtpPort = findViewById<EditText>(R.id.smtpPort)
        val smtpSecurity = findViewById<Spinner>(R.id.smtpSecurity)
        val saveButton = findViewById<Button>(R.id.saveButton)
        status = findViewById(R.id.setupStatus)

        val securityOptions = listOf(getString(R.string.security_ssl), getString(R.string.security_starttls))
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, securityOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        imapSecurity.adapter = adapter
        smtpSecurity.adapter = adapter

        // Prefill existing account for editing.
        CredentialStore.load(this)?.let { a ->
            displayName.setText(a.displayName)
            email.setText(a.email)
            password.setText(a.password)
            imapHost.setText(a.imapHost)
            imapPort.setText(a.imapPort.toString())
            imapSecurity.setSelection(if (a.imapSsl) 0 else 1)
            smtpHost.setText(a.smtpHost)
            smtpPort.setText(a.smtpPort.toString())
            smtpSecurity.setSelection(if (a.smtpSsl) 0 else 1)
        }

        saveButton.setOnClickListener {
            val account = MailAccount(
                displayName = displayName.text.toString().trim(),
                email = email.text.toString().trim(),
                password = password.text.toString(),
                imapHost = imapHost.text.toString().trim(),
                imapPort = imapPort.text.toString().trim().toIntOrNull() ?: 993,
                imapSsl = imapSecurity.selectedItemPosition == 0,
                smtpHost = smtpHost.text.toString().trim(),
                smtpPort = smtpPort.text.toString().trim().toIntOrNull() ?: 465,
                smtpSsl = smtpSecurity.selectedItemPosition == 0
            )
            if (account.email.isBlank() || account.password.isBlank() ||
                account.imapHost.isBlank() || account.smtpHost.isBlank()
            ) {
                showStatus(getString(R.string.setup_missing_fields))
                return@setOnClickListener
            }
            validateAndSave(account, saveButton)
        }
    }

    private fun validateAndSave(account: MailAccount, saveButton: Button) {
        saveButton.isEnabled = false
        showStatus(getString(R.string.setup_testing))
        lifecycleScope.launch {
            val error: String? = withContext(Dispatchers.IO) {
                try {
                    ImapService(account).testConnection()
                    SmtpService(account).testConnection()
                    null
                } catch (e: Exception) {
                    // Never include credentials in surfaced errors.
                    (e.message ?: e.javaClass.simpleName).take(200)
                }
            }
            saveButton.isEnabled = true
            if (error == null) {
                CredentialStore.save(this@SetupActivity, account)
                startActivity(Intent(this@SetupActivity, InboxActivity::class.java))
                finish()
            } else {
                showStatus(getString(R.string.setup_failed, error))
            }
        }
    }

    private fun showStatus(text: String) {
        status.visibility = View.VISIBLE
        status.text = text
    }
}
