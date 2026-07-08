package io.github.theonionsarewatching.shtigletz

/** D-Mail Max: like Pro, but user-approved remote images are loaded by the
 *  WebView itself (native <img> loading) instead of app-fetched data URIs —
 *  for devices whose WebView struggles with large data URIs. Embedded cid:
 *  images still come over IMAP. Still nothing loads without a user action. */
object FlavorConfig {
    const val NAME = "gefilte"
    const val ATTACHMENTS = true
    const val IMAGES = true
    const val WEBVIEW_IMAGES = true
}
