package io.github.theonionsarewatching.shtigletz.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.theonionsarewatching.shtigletz.mail.MailAccount

/**
 * Credentials are stored ONLY in EncryptedSharedPreferences backed by the
 * Android Keystore. They are never written in plaintext and never logged.
 */
object CredentialStore {

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "dmail_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(context: Context, a: MailAccount) {
        prefs(context).edit()
            .putString("displayName", a.displayName)
            .putString("email", a.email)
            .putString("password", a.password)
            .putString("imapHost", a.imapHost)
            .putInt("imapPort", a.imapPort)
            .putBoolean("imapSsl", a.imapSsl)
            .putString("smtpHost", a.smtpHost)
            .putInt("smtpPort", a.smtpPort)
            .putBoolean("smtpSsl", a.smtpSsl)
            .apply()
    }

    fun load(context: Context): MailAccount? {
        val p = prefs(context)
        val email = p.getString("email", null) ?: return null
        val password = p.getString("password", null) ?: return null
        val imapHost = p.getString("imapHost", null) ?: return null
        val smtpHost = p.getString("smtpHost", null) ?: return null
        return MailAccount(
            displayName = p.getString("displayName", "") ?: "",
            email = email,
            password = password,
            imapHost = imapHost,
            imapPort = p.getInt("imapPort", 993),
            imapSsl = p.getBoolean("imapSsl", true),
            smtpHost = smtpHost,
            smtpPort = p.getInt("smtpPort", 465),
            smtpSsl = p.getBoolean("smtpSsl", true)
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
