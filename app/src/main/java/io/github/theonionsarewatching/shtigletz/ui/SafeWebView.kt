package io.github.theonionsarewatching.shtigletz.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.theonionsarewatching.shtigletz.Settings
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
 *  - File/content access disabled.
 *
 * LINK POLICY: raw URLs are never shown in the body (they turn long emails
 * into walls of text). Depending on settings, links either display as a
 * tappable "[link]" — tapping fires [onLinkClicked] with the URL so the
 * Activity can show it in a dialog — or are stripped entirely. Nothing is
 * ever navigated to or loaded.
 *
 * Do not weaken this class.
 */
class SafeWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    /** Set by the hosting Activity; receives the URL of a tapped [link]. */
    var onLinkClicked: ((String) -> Unit)? = null

    /** Reading position 0–100 while the body scrolls. */
    var onScrollPercent: ((Int) -> Unit)? = null

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val denom = computeVerticalScrollRange() - computeVerticalScrollExtent()
        val pct = if (denom <= 0) 100 else (computeVerticalScrollOffset() * 100 / denom).coerceIn(0, 100)
        onScrollPercent?.invoke(pct)
    }

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

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Never navigate; hand the URL to the Activity to display as text.
                val url = request.url?.toString()
                if (!url.isNullOrBlank()) onLinkClicked?.invoke(url)
                return true
            }
        }
    }

    fun showEmail(plain: String?, html: String?, showLinks: Boolean) {
        // Message bodies follow the app's text-size setting.
        settings.textZoom = (Settings.textScale(context) * 100).toInt()
        loadDataWithBaseURL(null, buildDocument(plain, html, showLinks), "text/html", "utf-8", null)
    }

    private fun buildDocument(plain: String?, html: String?, showLinks: Boolean): String {
        val csp = "<meta http-equiv=\"Content-Security-Policy\" " +
                "content=\"default-src 'none'; style-src 'unsafe-inline'\">"
        val viewport = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        val style = "<style>" +
                "body{font-family:sans-serif;font-size:16px;margin:8px;word-wrap:break-word;}" +
                "img{display:none !important;}" +
                "pre{white-space:pre-wrap;font-family:sans-serif;}" +
                "a{color:#3D8BFD;text-decoration:underline;}" +
                "blockquote{border-left:3px solid #888;margin-left:4px;padding-left:8px;color:#666;}" +
                "table{max-width:100%;}" +
                "</style>"
        // Prefer plain text; fall back to sanitized HTML.
        val body = when {
            !plain.isNullOrBlank() -> processPlain(plain, showLinks)
            !html.isNullOrBlank() -> processHtml(stripActiveContent(html), showLinks)
            else -> "<i>(empty message)</i>"
        }
        return "<html><head>$csp$viewport$style</head><body>$body</body></html>"
    }

    // ---- Link policy ----

    private val urlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""")

    private fun processPlain(plain: String, showLinks: Boolean): String {
        val escaped = escape(plain)
        val processed = urlRegex.replace(escaped) { m ->
            if (showLinks) {
                val raw = m.groupValues[1]
                val href = if (raw.startsWith("www.", true)) "http://$raw" else raw
                "<a href=\"${attr(href)}\">[link]</a>"
            } else ""
        }
        return "<pre>$processed</pre>"
    }

    private fun processHtml(html: String, showLinks: Boolean): String {
        // 1. Anchors: keep short human-readable labels, collapse URL-ish or
        //    overlong labels to [link]; strip entirely in hide mode.
        val anchorRegex = Regex("(?is)<a\\b[^>]*?href\\s*=\\s*([\"'])(.*?)\\1[^>]*>(.*?)</a>")
        var out = anchorRegex.replace(html) { m ->
            val href = m.groupValues[2].trim()
            val innerText = m.groupValues[3].replace(Regex("<[^>]+>"), "").trim()
            val labelIsUrl = urlRegex.containsMatchIn(innerText)
            if (!showLinks) {
                if (innerText.isNotBlank() && !labelIsUrl) innerText else ""
            } else {
                val label =
                    if (innerText.isBlank() || labelIsUrl || innerText.length > 60) "[link]"
                    else escape(innerText)
                "<a href=\"${attr(href)}\">$label</a>"
            }
        }
        // 2. Bare URLs in text (not inside attributes — those are preceded by a quote or '=').
        out = Regex("""(?i)(?<!["'=])\b(https?://[^\s<>"']+)""").replace(out) { m ->
            if (showLinks) "<a href=\"${attr(m.groupValues[1])}\">[link]</a>" else ""
        }
        return out
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun attr(s: String): String = s.replace("\"", "&quot;")

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
