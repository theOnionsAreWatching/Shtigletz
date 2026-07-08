package io.github.theonionsarewatching.shtigletz.mail

/** Crude but safe HTML→plain-text conversion, used for quoting in reply/forward. */
object MailText {
    fun htmlToPlain(html: String): String = html
        .replace(Regex("(?is)<style.*?</style>"), "")
        .replace(Regex("(?is)<script.*?</script>"), "")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>"), "\n\n")
        .replace(Regex("(?i)</div>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .lines().joinToString("\n") { it.trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
