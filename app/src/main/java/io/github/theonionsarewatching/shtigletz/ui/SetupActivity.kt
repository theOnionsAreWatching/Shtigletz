package io.github.theonionsarewatching.shtigletz.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
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
import io.github.theonionsarewatching.shtigletz.security.AccountStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Add a new account (EXTRA_ACCOUNT_ID absent) or edit one (present).
 * Known providers pre-fill the server settings so only name, email, and
 * app password are needed; "Custom" exposes the full form.
 */
class SetupActivity : SoftKeyActivity() {

    companion object {
        const val EXTRA_ACCOUNT_ID = "accountId"
    }

    private data class Preset(
        val label: String,
        val imapHost: String, val imapPort: Int, val imapSsl: Boolean,
        val smtpHost: String, val smtpPort: Int, val smtpSsl: Boolean,
        val hintRes: Int
    )

    private val presets = listOf(
        Preset("Gmail / Google Workspace",
            "imap.gmail.com", 993, true, "smtp.gmail.com", 465, true,
            R.string.hint_provider_gmail),
        Preset("Outlook / Hotmail / Office 365",
            "outlook.office365.com", 993, true, "smtp.office365.com", 587, false,
            R.string.hint_provider_outlook),
        Preset("Yahoo Mail",
            "imap.mail.yahoo.com", 993, true, "smtp.mail.yahoo.com", 465, true,
            R.string.hint_provider_yahoo),
        Preset("iCloud Mail",
            "imap.mail.me.com", 993, true, "smtp.mail.me.com", 587, false,
            R.string.hint_provider_icloud),
        Preset("AOL Mail",
            "imap.aol.com", 993, true, "smtp.aol.com", 465, true,
            R.string.hint_provider_aol)
    )
    // presets.size == index of "Custom" in the spinner

    private lateinit var status: TextView
    private lateinit var providerHint: TextView
    private lateinit var serverSection: View
    private lateinit var provider: Spinner
    private var editingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        io.github.theonionsarewatching.shtigletz.input.SoftKeys.maybeOfferSetup(this)
        findViewById<android.widget.TextView>(R.id.setupNote).setText(
            when {
                !io.github.theonionsarewatching.shtigletz.FlavorConfig.ATTACHMENTS -> R.string.setup_note
                !io.github.theonionsarewatching.shtigletz.FlavorConfig.IMAGES -> R.string.setup_note_plus
                else -> R.string.setup_note_pro
            }
        )

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
        provider = findViewById(R.id.providerSpinner)
        providerHint = findViewById(R.id.providerHint)
        serverSection = findViewById(R.id.serverSection)
        status = findViewById(R.id.setupStatus)

        val securityOptions = listOf(getString(R.string.security_ssl), getString(R.string.security_starttls))
        val secAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, securityOptions)
        secAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        imapSecurity.adapter = secAdapter
        smtpSecurity.adapter = secAdapter

        val providerLabels = presets.map { it.label } + getString(R.string.provider_custom)
        val provAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, providerLabels)
        provAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        provider.adapter = provAdapter
        provider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyProviderUi(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Edit mode: prefill; select the matching provider, else Custom.
        editingId = intent.getStringExtra(EXTRA_ACCOUNT_ID)
        AccountStore.get(this, editingId)?.let { a ->
            displayName.setText(a.displayName)
            email.setText(a.email)
            password.setText(a.password)
            imapHost.setText(a.imapHost)
            imapPort.setText(a.imapPort.toString())
            imapSecurity.setSelection(if (a.imapSsl) 0 else 1)
            smtpHost.setText(a.smtpHost)
            smtpPort.setText(a.smtpPort.toString())
            smtpSecurity.setSelection(if (a.smtpSsl) 0 else 1)
            val match = presets.indexOfFirst {
                it.imapHost.equals(a.imapHost, true) && it.smtpHost.equals(a.smtpHost, true)
            }
            provider.setSelection(if (match >= 0) match else presets.size)
        }

        // Soft key (when enabled).
        setSoftKeys(getString(R.string.setup_save), { saveButton.performClick() })

        saveButton.setOnClickListener {
            val pos = provider.selectedItemPosition
            val preset = presets.getOrNull(pos) // null = Custom
            val account = MailAccount(
                id = editingId ?: AccountStore.newId(),
                displayName = displayName.text.toString().trim(),
                email = email.text.toString().trim(),
                password = password.text.toString(),
                imapHost = preset?.imapHost ?: imapHost.text.toString().trim(),
                imapPort = preset?.imapPort ?: (imapPort.text.toString().trim().toIntOrNull() ?: 993),
                imapSsl = preset?.imapSsl ?: (imapSecurity.selectedItemPosition == 0),
                smtpHost = preset?.smtpHost ?: smtpHost.text.toString().trim(),
                smtpPort = preset?.smtpPort ?: (smtpPort.text.toString().trim().toIntOrNull() ?: 465),
                smtpSsl = preset?.smtpSsl ?: (smtpSecurity.selectedItemPosition == 0)
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

    private fun applyProviderUi(position: Int) {
        val preset = presets.getOrNull(position)
        if (preset == null) { // Custom
            serverSection.visibility = View.VISIBLE
            providerHint.visibility = View.GONE
        } else {
            serverSection.visibility = View.GONE
            providerHint.visibility = View.VISIBLE
            providerHint.text = getString(preset.hintRes)
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
                AccountStore.save(this@SetupActivity, account)
                if (isTaskRoot) {
                    startActivity(
                        Intent(this@SetupActivity, MessageListActivity::class.java)
                            .putExtra(MessageListActivity.EXTRA_ACCOUNT_ID, account.id)
                    )
                }
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
