package io.github.theonionsarewatching.shtigletz.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.theonionsarewatching.shtigletz.FlavorConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal in-app update check against GitHub Releases. User-invoked only
 * (Settings → Check for updates); nothing runs in the background.
 */
object UpdateChecker {

    /** GitHub "owner/repo" this app's releases live in. */
    private const val REPO = "theOnionsAreWatching/Shtigletz"

    data class Latest(
        val tag: String,        // e.g. "v0.8.0"
        val htmlUrl: String,    // release page
        val apkUrl: String?     // this flavor's APK asset, when found
    )

    private fun assetLabel(): String = when (FlavorConfig.NAME) {
        "kosher" -> "Kosher"
        "plus" -> "Plus"
        "pro" -> "Pro"
        else -> "Max"
    }

    /** Fetch the latest release; null on any failure. Call on IO. */
    fun fetchLatest(): Latest? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("https://api.github.com/repos/$REPO/releases/latest")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "D-Mail")
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name")
            if (tag.isBlank()) return null
            var apk: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                val want = "d-mail-${assetLabel().lowercase()}"
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name").lowercase()
                    if (name.startsWith(want) && name.endsWith(".apk")) {
                        apk = a.optString("browser_download_url").ifBlank { null }
                        break
                    }
                }
            }
            Latest(tag, json.optString("html_url"), apk)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /** True when [remoteTag] (e.g. "v0.8.1") is newer than [localVersion] ("0.8.0"). */
    fun isNewer(remoteTag: String, localVersion: String): Boolean {
        fun nums(s: String) = s.trim().removePrefix("v").removePrefix("V")
            .split('.', '-').mapNotNull { it.toIntOrNull() }
        val r = nums(remoteTag)
        val l = nums(localVersion)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Download the APK to app-external storage. Call on IO. */
    fun downloadApk(context: Context, url: String): File? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "D-Mail")
            if (conn.responseCode !in 200..299) return null
            val dest = File(context.getExternalFilesDir(null), "update.apk")
            conn.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (dest.length() > 0) dest else null
        } catch (e: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /** Hand the downloaded APK to the system installer. */
    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
