package io.github.theonionsarewatching.shtigletz

import android.content.Context

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
}
