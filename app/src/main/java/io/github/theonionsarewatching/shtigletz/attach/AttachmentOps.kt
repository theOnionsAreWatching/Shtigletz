package io.github.theonionsarewatching.shtigletz.attach

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import io.github.theonionsarewatching.shtigletz.FlavorConfig
import io.github.theonionsarewatching.shtigletz.mail.BodyExtractor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Attachment plumbing for Plus/Pro. Every entry point requires
 * FlavorConfig.ATTACHMENTS; Kosher builds cannot reach any of it.
 * Files are handed off to other apps — D-Mail never renders them.
 */
object AttachmentOps {

    fun metasToJson(metas: List<BodyExtractor.AttachmentMeta>): String? {
        if (metas.isEmpty()) return null
        val arr = JSONArray()
        for (m in metas) arr.put(
            JSONObject().put("i", m.index).put("n", m.name).put("m", m.mime).put("s", m.sizeBytes)
        )
        return arr.toString()
    }

    fun metasFromJson(json: String?): List<BodyExtractor.AttachmentMeta> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BodyExtractor.AttachmentMeta(
                    index = o.getInt("i"),
                    name = o.optString("n", "attachment"),
                    mime = o.optString("m", "application/octet-stream"),
                    sizeBytes = o.optInt("s", -1)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun sanitize(name: String): String =
        name.replace(Regex("""[/\\:*?"<>|]"""), "_").take(120).ifBlank { "attachment" }

    fun cacheFile(context: Context, uid: Long, name: String): File =
        File(context.cacheDir, "attachments/$uid/${sanitize(name)}")

    fun sizeLabel(bytes: Int): String = when {
        bytes < 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }

    /** Hand the file to another app to view. */
    fun open(context: Context, file: File, mime: String): Boolean {
        require(FlavorConfig.ATTACHMENTS)
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, file.name))
            true
        }.getOrDefault(false)
    }

    /** Copy to the device Downloads collection. Returns a human-readable location, or null. */
    fun saveToDownloads(context: Context, file: File, name: String, mime: String): String? {
        require(FlavorConfig.ATTACHMENTS)
        val clean = sanitize(name)
        return runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, clean)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv
                ) ?: return null
                context.contentResolver.openOutputStream(uri).use { out ->
                    file.inputStream().use { it.copyTo(out!!) }
                }
                "Downloads/$clean"
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
                val dest = File(dir, clean)
                file.copyTo(dest, overwrite = true)
                dest.absolutePath
            }
        }.getOrNull()
    }
}
