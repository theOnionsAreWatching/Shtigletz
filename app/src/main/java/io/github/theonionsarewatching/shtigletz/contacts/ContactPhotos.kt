package io.github.theonionsarewatching.shtigletz.contacts

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Sender avatars come ONLY from the phone's local contacts. This is the single
 * image source in the entire app, and it involves no network access.
 */
object ContactPhotos {

    private val cache = HashMap<String, Bitmap?>()

    /** Call on a background dispatcher. Returns null if no permission, no contact, or no photo. */
    fun lookup(context: Context, email: String): Bitmap? {
        val key = email.lowercase().trim()
        if (key.isBlank()) return null
        if (cache.containsKey(key)) return cache[key]
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        var bmp: Bitmap? = null
        runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Email.CONTENT_LOOKUP_URI,
                Uri.encode(key)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Email.CONTACT_ID),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val contactId = c.getLong(0)
                    val contactUri =
                        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
                    ContactsContract.Contacts.openContactPhotoInputStream(
                        context.contentResolver, contactUri
                    )?.use { stream ->
                        bmp = BitmapFactory.decodeStream(stream)
                    }
                }
            }
        }
        cache[key] = bmp
        return bmp
    }
}
