package io.github.theonionsarewatching.shtigletz.mail

import io.github.theonionsarewatching.shtigletz.FlavorConfig
import java.net.HttpURLConnection
import java.net.URL

/**
 * Remote image download for D-Mail Pro. Only ever invoked by an explicit user
 * action ("load image"/"load all images"); nothing here runs automatically.
 * Hard-gated on FlavorConfig.IMAGES — Kosher and Plus cannot reach the network
 * for message content through any path.
 */
object HttpFetcher {

    fun getImage(url: String, maxBytes: Int = 5_000_000): Pair<ByteArray, String>? {
        require(FlavorConfig.IMAGES) { "images are not available in this flavor" }
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "image/*")
            if (conn.responseCode !in 200..299) return null
            val mime = (conn.contentType ?: "image/*").substringBefore(';').trim().lowercase()
            if (!mime.startsWith("image/")) return null
            val bytes = conn.inputStream.use { input ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) return null
                    buf.write(chunk, 0, n)
                }
                buf.toByteArray()
            }
            bytes to mime
        } catch (e: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
