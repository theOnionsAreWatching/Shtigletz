package io.github.theonionsarewatching.shtigletz

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** App settings (non-sensitive) in plain SharedPreferences. */
object Settings {
    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** How many envelopes each page loads. */
    fun pageSize(ctx: Context): Int = p(ctx).getInt("pageSize", 50)
    fun setPageSize(ctx: Context, v: Int) = p(ctx).edit().putInt("pageSize", v).apply()

    /** In-app auto refresh interval in minutes; 0 = off. */
    fun autoRefreshMinutes(ctx: Context): Int = p(ctx).getInt("autoRefreshMin", 0)
    fun setAutoRefreshMinutes(ctx: Context, v: Int) = p(ctx).edit().putInt("autoRefreshMin", v).apply()

    /** true = show links as tappable [link]; false = strip links entirely. */
    fun showLinks(ctx: Context): Boolean = p(ctx).getBoolean("showLinks", true)
    fun setShowLinks(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("showLinks", v).apply()

    /** How many newest message bodies to prefetch for offline reading; 0 = off. */
    fun prefetchBodies(ctx: Context): Int = p(ctx).getInt("prefetchBodies", 10)
    fun setPrefetchBodies(ctx: Context, v: Int) = p(ctx).edit().putInt("prefetchBodies", v).apply()

    /** Text/app size multiplier applied to every screen and message bodies. */
    fun textScale(ctx: Context): Float = p(ctx).getFloat("textScale", 1f)
    fun setTextScale(ctx: Context, v: Float) = p(ctx).edit().putFloat("textScale", v).apply()

    /** true = unread messages sort to the top; false = pure newest-first. */
    fun sortUnreadFirst(ctx: Context): Boolean = p(ctx).getBoolean("sortUnreadFirst", false)
    fun setSortUnreadFirst(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("sortUnreadFirst", v).apply()

    /** Reading background: "default" (follow theme) or a "#RRGGBB" color.
     *  Applies to text views only — HTML mode keeps its own canvas. */
    fun readBg(ctx: Context): String = p(ctx).getString("readBg", "default") ?: "default"
    fun setReadBg(ctx: Context, v: String) = p(ctx).edit().putString("readBg", v).apply()

    /** Reading font: "sans" | "serif" | "mono". */
    fun readFont(ctx: Context): String = p(ctx).getString("readFont", "sans") ?: "sans"
    fun setReadFont(ctx: Context, v: String) = p(ctx).edit().putString("readFont", v).apply()

    /** Pro/Max: "never" (tap each time) or "always" auto-load images. */
    fun imagesAuto(ctx: Context): String = p(ctx).getString("imagesAuto", "never") ?: "never"
    fun setImagesAuto(ctx: Context, v: String) = p(ctx).edit().putString("imagesAuto", v).apply()

    /** Pro/Max: senders whose images always load. */
    fun imagesSenders(ctx: Context): Set<String> =
        p(ctx).getStringSet("imagesSenders", emptySet()) ?: emptySet()
    fun addImagesSender(ctx: Context, email: String) =
        p(ctx).edit().putStringSet(
            "imagesSenders", imagesSenders(ctx).toMutableSet().apply { add(email.lowercase()) }
        ).apply()

    /** Pro/Max: senders whose mail opens in full HTML view. */
    fun htmlSenders(ctx: Context): Set<String> =
        p(ctx).getStringSet("htmlSenders", emptySet()) ?: emptySet()
    fun addHtmlSender(ctx: Context, email: String) =
        p(ctx).edit().putStringSet(
            "htmlSenders", htmlSenders(ctx).toMutableSet().apply { add(email.lowercase()) }
        ).apply()

    fun clearSenderRules(ctx: Context) =
        p(ctx).edit().remove("imagesSenders").remove("htmlSenders").apply()

    /** Delete the downloaded update APK on next app start (default on). */
    fun deleteUpdateApk(ctx: Context): Boolean = p(ctx).getBoolean("deleteUpdateApk", true)
    fun setDeleteUpdateApk(ctx: Context, v: Boolean) =
        p(ctx).edit().putBoolean("deleteUpdateApk", v).apply()

    /** Pro default message view: "text" | "textimg" | "html". */
    fun viewMode(ctx: Context): String = p(ctx).getString("viewMode", "textimg") ?: "textimg"
    fun setViewMode(ctx: Context, v: String) = p(ctx).edit().putString("viewMode", v).apply()

    /** Per account+folder timestamp of the last successful refresh. */
    fun lastRefresh(ctx: Context, accountId: String, folder: String): Long =
        p(ctx).getLong("lastRefresh_${accountId}_$folder", 0L)
    fun setLastRefresh(ctx: Context, accountId: String, folder: String) =
        p(ctx).edit().putLong("lastRefresh_${accountId}_$folder", System.currentTimeMillis()).apply()

    /** true = email addresses & phone numbers in messages are tappable (copy dialog). */
    fun tapContacts(ctx: Context): Boolean = p(ctx).getBoolean("tapContacts", true)
    fun setTapContacts(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("tapContacts", v).apply()

    /** Notifications: "off" | "all" | "selected". */
    fun notifyMode(ctx: Context): String = p(ctx).getString("notifyMode", "off") ?: "off"
    fun setNotifyMode(ctx: Context, v: String) = p(ctx).edit().putString("notifyMode", v).apply()

    /** Account ids to notify for when mode == "selected". */
    fun notifyAccountIds(ctx: Context): Set<String> =
        p(ctx).getStringSet("notifyAccounts", emptySet()) ?: emptySet()
    fun setNotifyAccountIds(ctx: Context, ids: Set<String>) =
        p(ctx).edit().putStringSet("notifyAccounts", ids.toSet()).apply()

    fun accountNotifiable(ctx: Context, id: String): Boolean = when (notifyMode(ctx)) {
        "all" -> true
        "selected" -> id in notifyAccountIds(ctx)
        else -> false
    }

    /** true = notifications show sender & subject; false = count only. */
    fun notifyShowHeader(ctx: Context): Boolean = p(ctx).getBoolean("notifyShowHeader", true)
    fun setNotifyShowHeader(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("notifyShowHeader", v).apply()

    /** Theme: "system" | "light" | "dark". */
    fun theme(ctx: Context): String = p(ctx).getString("theme", "system") ?: "system"
    fun setTheme(ctx: Context, v: String) = p(ctx).edit().putString("theme", v).apply()
    fun nightMode(ctx: Context): Int = when (theme(ctx)) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}
