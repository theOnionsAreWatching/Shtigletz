package io.github.theonionsarewatching.shtigletz.mail

data class MailAccount(
    val id: String,             // stable UUID; DB rows and intents reference this
    val displayName: String,
    val email: String,          // also used as the IMAP/SMTP username
    val password: String,       // app password recommended
    val imapHost: String,
    val imapPort: Int,
    val imapSsl: Boolean,       // true = implicit SSL/TLS, false = STARTTLS
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSsl: Boolean        // true = implicit SSL/TLS, false = STARTTLS
)
