package io.github.theonionsarewatching.shtigletz.mail

import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

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

    /** Plain-text send only. The app composes no HTML and attaches nothing, by design. */
    fun send(to: String, cc: String, subject: String, body: String) {
        val msg = MimeMessage(session())
        msg.setFrom(InternetAddress(account.email, account.displayName.ifBlank { account.email }))
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to.trim()))
        if (cc.isNotBlank()) {
            msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc.trim()))
        }
        msg.subject = subject
        msg.setText(body, "utf-8")
        Transport.send(msg)
    }
}
