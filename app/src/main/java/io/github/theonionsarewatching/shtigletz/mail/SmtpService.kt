package io.github.theonionsarewatching.shtigletz.mail

import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class SmtpService(private val account: MailAccount) {

    private fun session(): Session {
        val props = Properties()
        props["mail.transport.protocol"] = "smtp"
        props["mail.smtp.host"] = account.smtpHost
        props["mail.smtp.port"] = account.smtpPort.toString()
        props["mail.smtp.auth"] = "true"
        if (account.smtpSsl) {
            props["mail.smtp.ssl.enable"] = "true"
        } else {
            props["mail.smtp.starttls.enable"] = "true"
            props["mail.smtp.starttls.required"] = "true"
        }
        props["mail.smtp.connectiontimeout"] = "15000"
        props["mail.smtp.timeout"] = "30000"
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(account.email, account.password)
        })
    }

    fun testConnection() {
        val t = session().getTransport("smtp")
        t.connect(account.smtpHost, account.email, account.password)
        t.close()
    }

    data class Attachment(val file: java.io.File, val name: String, val mime: String)

    /**
     * Plain-text body always (the app composes no HTML by design).
     * Attachments are only ever passed in the Plus/Pro flavors — the compose
     * UI that collects them is flavor-gated.
     */
    fun send(
        to: String,
        cc: String,
        subject: String,
        body: String,
        attachments: List<Attachment> = emptyList()
    ) {
        val msg = MimeMessage(session())
        // The account's display name is for the APP's UI only. Outgoing mail
        // uses the bare address: a From name that doesn't match the provider's
        // records (e.g. the real Google account name) trips spam filters.
        msg.setFrom(InternetAddress(account.email))
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to.trim()))
        if (cc.isNotBlank()) {
            msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc.trim()))
        }
        msg.subject = subject
        if (attachments.isEmpty()) {
            msg.setText(body, "utf-8")
        } else {
            val multipart = MimeMultipart()
            val textPart = MimeBodyPart()
            textPart.setText(body, "utf-8")
            multipart.addBodyPart(textPart)
            for (a in attachments) {
                val p = MimeBodyPart()
                p.dataHandler = DataHandler(FileDataSource(a.file))
                p.fileName = a.name
                p.setHeader("Content-Type", a.mime)
                multipart.addBodyPart(p)
            }
            msg.setContent(multipart)
        }
        Transport.send(msg)
    }
}
