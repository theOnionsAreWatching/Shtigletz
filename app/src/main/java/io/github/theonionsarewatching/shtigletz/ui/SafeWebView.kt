package io.github.theonionsarewatching.shtigletz.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * KOSHER ENFORCEMENT — RENDER LAYER. Compiled in; there is no toggle.
 *
 * A WebView that cannot make any network request:
 *  - JavaScript disabled.
 *  - blockNetworkLoads + images-off at the settings level.
 *  - shouldInterceptRequest returns an EMPTY response for EVERY resource
 *    request (belt-and-suspenders: catches CSS, fonts, favicons, and
 *    read-receipt tracking pixels — blocking remote images kills email
 *    tracking as a side effect).
 *  - Navigation is swallowed; content is loaded with a null base URL.
 *  - A CSP meta tag forbids all external sources.
 *  - File/content access disabled so nothing local can be exfiltrated or read.
 *
 * Do not weaken this class. Every lock here exists to guarantee that viewing
 * mail produces zero outbound requests.
 */
class SafeWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    init {
        @SuppressLint("SetJavaScriptEnabled")
        settings.javaScriptEnabled = false
        settings.blockNetworkLoads = true
        settings.blockNetworkImage = true
        settings.loadsImagesAutomatically = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setGeolocationEnabled(false)

        isFocusable = true
        isFocusableInTouchMode = true

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse {
                // No resource request of any scheme is ever allowed out.
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
        }
    }

    fun showEmail(plain: String?, html: String?) {
        loadDataWithBaseURL(null, buildDocument(plain, html), "text/html", "utf-8", null)
    }

    private fun buildDocument(plain: String?, html: String?): String {
        val csp = "<meta http-equiv=\"Content-Security-Policy\" " +
                "content=\"default-src 'none'; style-src 'unsafe-inline'\">"
        val viewport = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        val style = "<style>" +
                "body{font-family:sans-serif;font-size:16px;margin:8px;word-wrap:break-word;}" +
                "img{display:none !important;}" +
                "pre{white-space:pre-wrap;font-family:sans-serif;}" +
                "blockquote{border-left:3px solid #888;margin-left:4px;padding-left:8px;color:#666;}" +
                "table{max-width:100%;}" +
                "</style>"
        // Prefer plain text; fall back to sanitized HTML.
        val body = when {
            !plain.isNullOrBlank() -> "<pre>" + escape(plain) + "</pre>"
            !html.isNullOrBlank() -> stripActiveContent(html)
            else -> "<i>(empty message)</i>"
        }
        return "<html><head>$csp$viewport$style</head><body>$body</body></html>"
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Defense in depth on top of JS-off + request blocking. */
    private fun stripActiveContent(html: String): String =
        html.replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("(?is)<script[^>]*>"), "")
            .replace(Regex("(?is)<iframe.*?</iframe>"), "")
            .replace(Regex("(?is)<object.*?</object>"), "")
            .replace(Regex("(?is)<embed[^>]*>"), "")
            .replace(Regex("(?is)\\son\\w+\\s*=\\s*\"[^\"]*\""), "")
            .replace(Regex("(?is)\\son\\w+\\s*=\\s*'[^']*'"), "")
}
