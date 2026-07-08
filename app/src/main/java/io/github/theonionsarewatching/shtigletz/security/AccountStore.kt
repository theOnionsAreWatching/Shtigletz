package io.github.theonionsarewatching.shtigletz.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.theonionsarewatching.shtigletz.mail.MailAccount
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Multi-account storage. Accounts (including passwords) live ONLY in
 * EncryptedSharedPreferences backed by the Android Keystore — never plaintext,
 * never logged.
 */
object AccountStore {

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "dmail_accounts",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun list(context: Context): List<MailAccount> {
        migrateLegacySingleAccount(context)
        val raw = prefs(context).getString("accounts", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> fromJson(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    fun get(context: Context, id: String?): MailAccount? =
        id?.let { key -> list(context).firstOrNull { it.id == key } }

    /** Insert or replace by id, preserving list order (order = display order). */
    fun save(context: Context, account: MailAccount) {
        val current = list(context).toMutableList()
        val idx = current.indexOfFirst { it.id == account.id }
        if (idx >= 0) current[idx] = account else current.add(account)
        write(context, current)
    }

    /** Move an account up (delta=-1) or down (delta=+1) in the display order. */
    fun move(context: Context, id: String, delta: Int) {
        val current = list(context).toMutableList()
        val i = current.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= current.size) return
        val tmp = current[i]; current[i] = current[j]; current[j] = tmp
        write(context, current)
    }

    fun remove(context: Context, id: String) {
        write(context, list(context).filter { it.id != id })
    }

    private fun write(context: Context, accounts: List<MailAccount>) {
        val arr = JSONArray()
        for (a in accounts) arr.put(toJson(a))
        prefs(context).edit().putString("accounts", arr.toString()).apply()
    }

    private fun toJson(a: MailAccount) = JSONObject().apply {
        put("id", a.id)
        put("displayName", a.displayName)
        put("email", a.email)
        put("password", a.password)
        put("imapHost", a.imapHost)
        put("imapPort", a.imapPort)
        put("imapSsl", a.imapSsl)
        put("smtpHost", a.smtpHost)
        put("smtpPort", a.smtpPort)
        put("smtpSsl", a.smtpSsl)
    }

    private fun fromJson(o: JSONObject): MailAccount? = runCatching {
        MailAccount(
            id = o.getString("id"),
            displayName = o.optString("displayName", ""),
            email = o.getString("email"),
            password = o.getString("password"),
            imapHost = o.getString("imapHost"),
            imapPort = o.optInt("imapPort", 993),
            imapSsl = o.optBoolean("imapSsl", true),
            smtpHost = o.getString("smtpHost"),
            smtpPort = o.optInt("smtpPort", 465),
            smtpSsl = o.optBoolean("smtpSsl", true)
        )
    }.getOrNull()

    /** One-time import from the v0.1 single-account store. */
    private fun migrateLegacySingleAccount(context: Context) {
        val p = prefs(context)
        if (p.contains("accounts") || p.getBoolean("legacy_migrated", false)) return
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val old = EncryptedSharedPreferences.create(
                context, "dmail_credentials", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val email = old.getString("email", null)
            val password = old.getString("password", null)
            val imapHost = old.getString("imapHost", null)
            val smtpHost = old.getString("smtpHost", null)
            if (email != null && password != null && imapHost != null && smtpHost != null) {
                val account = MailAccount(
                    id = newId(),
                    displayName = old.getString("displayName", "") ?: "",
                    email = email,
                    password = password,
                    imapHost = imapHost,
                    imapPort = old.getInt("imapPort", 993),
                    imapSsl = old.getBoolean("imapSsl", true),
                    smtpHost = smtpHost,
                    smtpPort = old.getInt("smtpPort", 465),
                    smtpSsl = old.getBoolean("smtpSsl", true)
                )
                write(context, listOf(account))
                old.edit().clear().apply()
            }
        }
        p.edit().putBoolean("legacy_migrated", true).apply()
    }
}
