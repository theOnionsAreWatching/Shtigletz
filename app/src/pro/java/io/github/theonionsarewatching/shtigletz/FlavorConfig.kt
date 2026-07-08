package io.github.theonionsarewatching.shtigletz

/** D-Mail Pro: attachments + on-demand images. Images are fetched by the
 *  app itself and inlined as data: URIs — the WebView never touches the
 *  network directly except in HTML mode after "Load images". */
object FlavorConfig {
    const val NAME = "pro"
    const val ATTACHMENTS = true
    const val IMAGES = true
    const val WEBVIEW_IMAGES = false
}
