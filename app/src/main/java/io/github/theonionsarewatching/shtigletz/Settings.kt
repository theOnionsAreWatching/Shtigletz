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
