package io.github.theonionsarewatching.shtigletz.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.theonionsarewatching.shtigletz.Settings
import io.github.theonionsarewatching.shtigletz.mail.MailText
import java.io.ByteArrayInputStream

/**
 * KOSHER ENFORCEMENT — RENDER LAYER. Compiled in; there is no toggle.
 *
 * A WebView that cannot make any network request:
 *  - JavaScript disabled.
 *  - blockNetworkLoads + images-off at the settings level.
 *  - shouldInterceptRequest returns an EMPTY response for EVERY resource
 *    request (belt-and-suspenders: catches CSS, fonts, favicons, and
 *    read-receipt tracking pixels).
 *  - Navigation is swallowed; content is loaded with a null base URL.
 *  - File/content access disabled.
 *
 * PURE-TEXT RENDERING: emails are never rendered as HTML. HTML bodies are
 * converted to plain text — no colors, no backgrounds, no buttons, no
 * layout — and shown in the app's own theme (light or dark). The only
 * interactive elements are ones this class creates itself:
 *  - [link] placeholders (tap → URL shown as text, never navigated)
 *  - email addresses and phone numbers (tap → copy dialog), optional
 *
 * Do not weaken this class.
 */
class SafeWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    /** Set by the hosting Activity; receives the href of a tapped element
     *  ("http…", "mailto:…", or "tel:…"). Never navigated. */
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
                // Never navigate; hand the value to the Activity to display.
                val url = request.url?.toString()
                if (!url.isNullOrBlank()) onLinkClicked?.invoke(url)
                return true
            }
        }
    }

    /** Neutral placeholder (e.g. "Loading…") — used while a body is being fetched. */
    fun showPlaceholder(text: String) {
        settings.textZoom = (Settings.textScale(context) * 100).toInt()
        val doc = "<html><head>$viewportMeta<style>${baseCss()}</style></head>" +
                "<body><i>${escape(text)}</i></body></html>"
        loadDataWithBaseURL(null, doc, "text/html", "utf-8", null)
    }

    fun showEmail(plain: String?, html: String?, showLinks: Boolean) {
        settings.textZoom = (Settings.textScale(context) * 100).toInt()
        loadDataWithBaseURL(null, buildDocument(plain, html, showLinks), "text/html", "utf-8", null)
    }

    // ---- Pure-text pipeline ----

    private data class Target(val href: String, val label: String)

    private fun buildDocument(plain: String?, html: String?, showLinks: Boolean): String {
        val tappable = Settings.tapContacts(context)
        val targets = ArrayList<Target>()
        val text = when {
            !plain.isNullOrBlank() -> plain
            !html.isNullOrBlank() -> htmlToText(stripActiveContent(html), showLinks, targets)
            else -> null
        }
        val body =
            if (text.isNullOrBlank()) "<i>(empty message)</i>"
            else "<pre>${renderText(text, showLinks, tappable, targets)}</pre>"
        return "<html><head>$viewportMeta<style>${baseCss()}</style></head><body>$body</body></html>"
    }

    private val viewportMeta =
        "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"

    /** The app's own theme colors — the email's design never applies. */
    private fun baseCss(): String {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val bg = if (night) "#121212" else "#FFFFFF"
        val fg = if (night) "#E4E4E4" else "#1A1A1A"
        val link = if (night) "#8AB8FF" else "#1A66CC"
        return "body{background:$bg;color:$fg;font-family:sans-serif;font-size:16px;margin:8px;}" +
                "pre{white-space:pre-wrap;word-wrap:break-word;font-family:sans-serif;margin:0;}" +
                "a{color:$link;text-decoration:underline;}"
    }

    private val urlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""")
    private val emailRegex = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val phoneRegex = Regex("""(?<!\d)\+?\d[\d\s().\-]{7,}\d(?!\d)""")

    private fun token(i: Int) = "\u0002$i\u0003"

    /**
     * HTML → plain text, preserving link destinations as tokens.
     * Anchors become "label ⟦token⟧" (or just the label when links are
     * hidden); everything else — colors, tables, buttons, layout — is
     * discarded and only the text survives.
     */
    private fun htmlToText(html: String, showLinks: Boolean, targets: MutableList<Target>): String {
        val anchorRegex = Regex("(?is)<a\\b[^>]*?href\\s*=\\s*([\"'])(.*?)\\1[^>]*>(.*?)</a>")
        val withMarkers = anchorRegex.replace(html) { m ->
            val href = m.groupValues[2].trim()
            val label = m.groupValues[3].replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ").trim()
            val labelUseless = label.isBlank() || label.length > 60 || urlRegex.containsMatchIn(label)
            if (!showLinks || href.isBlank() || href.startsWith("#") ||
                href.startsWith("javascript:", true)
            ) {
                if (labelUseless) "" else label
            } else {
                targets.add(Target(href, "[link]"))
                val marker = token(targets.size - 1)
                if (labelUseless) marker else "$label $marker"
            }
        }
        return MailText.htmlToPlain(withMarkers)
    }

    /** Escape, then tokenize URLs / emails / phones, then emit anchors. */
    private fun renderText(
        text: String,
        showLinks: Boolean,
        tappable: Boolean,
        targets: MutableList<Target>
    ): String {
        var out = escape(text)

        out = urlRegex.replace(out) { m ->
            if (!showLinks) "" else {
                val raw = m.groupValues[1]
                val href = if (raw.startsWith("www.", true)) "http://$raw" else raw
                targets.add(Target(href, "[link]"))
                token(targets.size - 1)
            }
        }

        if (tappable) {
            out = emailRegex.replace(out) { m ->
                targets.add(Target("mailto:${m.value}", m.value))
                token(targets.size - 1)
            }
            out = phoneRegex.replace(out) { m ->
                val digits = m.value.filter { it.isDigit() }
                if (digits.length !in 9..15) m.value
                else {
                    targets.add(Target("tel:${m.value.trim()}", m.value.trim()))
                    token(targets.size - 1)
                }
            }
        }

        return Regex("\u0002(\\d+)\u0003").replace(out) { m ->
            val t = targets[m.groupValues[1].toInt()]
            "<a href=\"${attr(t.href)}\">${escape(t.label)}</a>"
        }
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun attr(s: String): String = s.replace("\"", "&quot;")

    /** Defense in depth before conversion. */
    private fun stripActiveContent(html: String): String =
        html.replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("(?is)<script[^>]*>"), "")
            .replace(Regex("(?is)<style.*?</style>"), "")
            .replace(Regex("(?is)<iframe.*?</iframe>"), "")
            .replace(Regex("(?is)<object.*?</object>"), "")
            .replace(Regex("(?is)<embed[^>]*>"), "")
            .replace(Regex("(?is)\\son\\w+\\s*=\\s*\"[^\"]*\""), "")
            .replace(Regex("(?is)\\son\\w+\\s*=\\s*'[^']*'"), "")
}
