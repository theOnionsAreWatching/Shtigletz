package io.github.theonionsarewatching.shtigletz.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.theonionsarewatching.shtigletz.FlavorConfig
import io.github.theonionsarewatching.shtigletz.Settings
import io.github.theonionsarewatching.shtigletz.mail.MailText
import java.io.ByteArrayInputStream

/**
 * KOSHER ENFORCEMENT — RENDER LAYER (kosher/plus flavors). Compiled in per flavor.
 *
 * Default state: a WebView that cannot make any network request. JavaScript
 * off, network loads blocked, every resource request intercepted with an
 * empty response, navigation swallowed, file/content access off. Bodies are
 * converted to plain text in the app's own theme — no colors, no buttons.
 *
 * D-Mail Pro adds two opt-in render modes, gated on FlavorConfig.IMAGES and
 * only ever activated by an explicit user action:
 *  - TEXT_IMG: pure text + [image] placeholders; a tapped image is fetched
 *    by the app and re-rendered inline as a data: URI (no WebView network).
 *  - HTML: sanitized original HTML; network image loading is enabled ONLY
 *    when the user presses "Load images" (networkImages = true).
 * In kosher/plus builds FlavorConfig.IMAGES is false and every request is
 * forced back to TEXT with the network fully blocked. Do not weaken this.
 */
class SafeWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    enum class RenderMode { TEXT, TEXT_IMG, HTML }

    /** Tapped element href: "http…", "mailto:…", "tel:…", or "dimg:<n>". */
    var onLinkClicked: ((String) -> Unit)? = null

    /** Reading position 0–100 while the body scrolls. */
    var onScrollPercent: ((Int) -> Unit)? = null

    /** When true (Pro HTML mode, after "Load images"), requests pass through. */
    private var allowNetwork = false

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val denom = computeVerticalScrollRange() - computeVerticalScrollExtent()
        val pct = if (denom <= 0) 100 else (computeVerticalScrollOffset() * 100 / denom).coerceIn(0, 100)
        onScrollPercent?.invoke(pct)
    }

    init {
        @SuppressLint("SetJavaScriptEnabled")
        settings.javaScriptEnabled = false
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

        // Some devices force-darken WebView content and end up inverting our
        // explicit colors (reported: black text in dark mode). We theme the
        // content ourselves, so turn algorithmic/force dark OFF everywhere.
        if (Build.VERSION.SDK_INT in 29..32) {
            @Suppress("DEPRECATION")
            settings.forceDark = WebSettings.FORCE_DARK_OFF
        }

        lockNetwork()

        isFocusable = true
        isFocusableInTouchMode = true

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                // Pro HTML mode with images explicitly loaded: let requests through.
                if (allowNetwork && FlavorConfig.IMAGES) return null
                // Otherwise: no resource request of any scheme is ever allowed out.
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url?.toString()
                if (!url.isNullOrBlank()) onLinkClicked?.invoke(url)
                return true // never navigate
            }
        }
    }

    private fun lockNetwork() {
        allowNetwork = false
        settings.blockNetworkLoads = true
        settings.blockNetworkImage = true
        settings.loadsImagesAutomatically = false
    }

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    /** Neutral placeholder (e.g. "Loading…"). */
    fun showPlaceholder(text: String) {
        lockNetwork()
        settings.textZoom = (Settings.textScale(context) * 100).toInt()
        setBackgroundColor(if (isNight()) Color.parseColor("#121212") else Color.WHITE)
        val doc = "<html><head>$headMeta<style>${baseCss()}</style></head>" +
                "<body><i>${escape(text)}</i></body></html>"
        loadDataWithBaseURL(null, doc, "text/html", "utf-8", null)
    }

    /**
     * Render a message. Returns the image sources found (TEXT_IMG mode only;
     * empty otherwise) so the Activity can fetch them on demand.
     *
     * @param loadedImages TEXT_IMG: image index -> data: URI, rendered inline.
     * @param cidImages    HTML: Content-ID -> data: URI substitution.
     * @param networkImages HTML only: user pressed "Load images".
     */
    fun showEmail(
        plain: String?,
        html: String?,
        showLinks: Boolean,
        mode: RenderMode = RenderMode.TEXT,
        loadedImages: Map<Int, String> = emptyMap(),
        cidImages: Map<String, String> = emptyMap(),
        networkImages: Boolean = false
    ): List<String> {
        val effMode = if (FlavorConfig.IMAGES) mode else RenderMode.TEXT
        settings.textZoom = (Settings.textScale(context) * 100).toInt()

        // Data URIs need image rendering on; network stays locked unless the
        // Pro user explicitly loaded images in HTML mode.
        lockNetwork()
        if (effMode != RenderMode.TEXT) {
            settings.loadsImagesAutomatically = true
        }
        if (effMode == RenderMode.HTML && networkImages && FlavorConfig.IMAGES) {
            allowNetwork = true
            settings.blockNetworkLoads = false
            settings.blockNetworkImage = false
        }

        val imageSrcs = ArrayList<String>()
        val doc: String = when {
            effMode == RenderMode.HTML && !html.isNullOrBlank() -> {
                setBackgroundColor(Color.WHITE) // HTML emails assume a light canvas
                buildHtmlDoc(html, cidImages)
            }
            else -> {
                setBackgroundColor(if (isNight()) Color.parseColor("#121212") else Color.WHITE)
                buildTextDoc(plain, html, showLinks, effMode == RenderMode.TEXT_IMG, imageSrcs, loadedImages)
            }
        }
        loadDataWithBaseURL(null, doc, "text/html", "utf-8", null)
        return imageSrcs
    }

    // ---- Documents ----

    private val headMeta =
        "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<meta name=\"color-scheme\" content=\"light dark\">"

    private val htmlHeadMeta =
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<meta name=\"color-scheme\" content=\"light\">"

    private fun baseCss(): String {
        val night = isNight()
        val bg = if (night) "#121212" else "#FFFFFF"
        val fg = if (night) "#E4E4E4" else "#1A1A1A"
        val link = if (night) "#8AB8FF" else "#1A66CC"
        return "body{background:$bg;color:$fg;font-family:sans-serif;font-size:16px;margin:8px;}" +
                "pre{white-space:pre-wrap;word-wrap:break-word;font-family:sans-serif;margin:0;}" +
                "a{color:$link;text-decoration:underline;}" +
                "img{max-width:100%;height:auto;}"
    }

    private fun buildTextDoc(
        plain: String?,
        html: String?,
        showLinks: Boolean,
        withImages: Boolean,
        imageSrcs: MutableList<String>,
        loadedImages: Map<Int, String>
    ): String {
        val targets = ArrayList<Target>()
        val text = when {
            !plain.isNullOrBlank() -> plain
            !html.isNullOrBlank() ->
                htmlToText(stripActiveContent(html), showLinks, targets, if (withImages) imageSrcs else null)
            else -> null
        }
        val body =
            if (text.isNullOrBlank() && targets.isEmpty()) "<i>(empty message)</i>"
            else "<pre>${renderText(text ?: "", showLinks, Settings.tapContacts(context), targets, loadedImages)}</pre>"
        return "<html><head>$headMeta<style>${baseCss()}</style></head><body>$body</body></html>"
    }

    /** Pro HTML mode: sanitized original markup; cid: images substituted with data URIs. */
    private fun buildHtmlDoc(html: String, cidImages: Map<String, String>): String {
        var out = stripActiveContent(html)
        if (cidImages.isNotEmpty()) {
            out = Regex("(?i)(src\\s*=\\s*([\"']))cid:([^\"']+)\\2").replace(out) { m ->
                val cid = m.groupValues[3].trim()
                val data = cidImages[cid] ?: cidImages[cid.lowercase()]
                if (data != null) "src=\"${attr(data)}\"" else m.value
            }
        }
        val style = "<style>img{max-width:100%;height:auto;}body{margin:8px;}</style>"
        return "<html><head>$htmlHeadMeta$style</head><body>$out</body></html>"
    }

    // ---- Text pipeline ----

    private data class Target(
        val href: String,
        val label: String,
        val imageIndex: Int = -1
    )

    private val urlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""")
    private val emailRegex = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val phoneRegex = Regex("""(?<!\d)\+?\d[\d\s().\-]{7,}\d(?!\d)""")
    private val imgRegex = Regex("(?is)<img\\b[^>]*?src\\s*=\\s*([\"'])(.*?)\\1[^>]*>")

    private fun token(i: Int) = "\u0002$i\u0003"

    private fun htmlToText(
        html: String,
        showLinks: Boolean,
        targets: MutableList<Target>,
        imageSrcs: MutableList<String>?
    ): String {
        var work = html
        if (imageSrcs != null && FlavorConfig.IMAGES) {
            work = imgRegex.replace(work) { m ->
                val src = m.groupValues[2].trim()
                if (src.isBlank() || src.startsWith("data:", true)) "" else {
                    val idx = imageSrcs.size
                    imageSrcs.add(src)
                    targets.add(Target("dimg:$idx", "[image]", imageIndex = idx))
                    " ${token(targets.size - 1)} "
                }
            }
        }
        val anchorRegex = Regex("(?is)<a\\b[^>]*?href\\s*=\\s*([\"'])(.*?)\\1[^>]*>(.*?)</a>")
        work = anchorRegex.replace(work) { m ->
            val href = m.groupValues[2].trim()
            val inner = m.groupValues[3]
            // keep any image tokens already inside the anchor
            val innerTokens = Regex("\u0002\\d+\u0003").findAll(inner).joinToString(" ") { it.value }
            val label = inner.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
            val labelUseless = label.isBlank() || label.length > 60 || urlRegex.containsMatchIn(label)
            val kept = if (!showLinks || href.isBlank() || href.startsWith("#") ||
                href.startsWith("javascript:", true)
            ) {
                if (labelUseless) "" else label
            } else {
                targets.add(Target(href, "[link]"))
                val marker = token(targets.size - 1)
                if (labelUseless) marker else "$label $marker"
            }
            if (innerTokens.isBlank()) kept else "$kept $innerTokens"
        }
        return MailText.htmlToPlain(work)
    }

    private fun renderText(
        text: String,
        showLinks: Boolean,
        tappable: Boolean,
        targets: MutableList<Target>,
        loadedImages: Map<Int, String>
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
            val data = if (t.imageIndex >= 0) loadedImages[t.imageIndex] else null
            if (data != null) {
                "<img src=\"${attr(data)}\"/>"
            } else {
                "<a href=\"${attr(t.href)}\">${escape(t.label)}</a>"
            }
        }
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun attr(s: String): String = s.replace("\"", "&quot;")

    private fun stripActiveContent(html: String): String =
        html.replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("(?is)<script[^>]*>"), "")
            .replace(Regex("(?is)<iframe.*?</iframe>"), "")
            .replace(Regex("(?is)<object.*?</object>"), "")
            .replace(Regex("(?is)<embed[^>]*>"), "")
            .replace(Regex("(?is)\\son\\w+\\s*=\\s*\"[^\"]*\""), "")
            .replace(Regex("(?is)\\son\\w+\\s*=\\s*'[^']*'"), "")
}
