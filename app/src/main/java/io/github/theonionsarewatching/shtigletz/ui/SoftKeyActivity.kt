package io.github.theonionsarewatching.shtigletz.ui

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.input.SoftKeys

/**
 * Adds an optional soft-key hint bar (small labels bottom-left / bottom-right)
 * and dispatches the resolved soft-key codes to per-screen actions. Only the
 * two resolved codes are intercepted — every other key behaves normally.
 */
open class SoftKeyActivity : ScaledActivity() {

    private var leftAction: (() -> Unit)? = null
    private var rightAction: (() -> Unit)? = null
    private var bar: View? = null

    override fun setContentView(layoutResID: Int) {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val content = layoutInflater.inflate(layoutResID, root, false)
        root.addView(
            content,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        val b = layoutInflater.inflate(R.layout.softkey_bar, root, false)
        b.visibility = View.GONE
        root.addView(b)
        bar = b
        super.setContentView(root)
    }

    /** Call after setContentView. Null label+action hides that side. */
    protected fun setSoftKeys(
        leftLabel: String?, left: (() -> Unit)?,
        rightLabel: String? = null, right: (() -> Unit)? = null
    ) {
        leftAction = left
        rightAction = right
        bar?.findViewById<TextView>(R.id.softLeftLabel)?.text = leftLabel ?: ""
        bar?.findViewById<TextView>(R.id.softRightLabel)?.text = rightLabel ?: ""
        updateBar()
    }

    private fun updateBar() {
        val b = bar ?: return
        val enabled = SoftKeys.resolve(this) != null
        b.visibility =
            if (enabled && (leftAction != null || rightAction != null)) View.VISIBLE
            else View.GONE
    }

    override fun onResume() {
        super.onResume()
        updateBar() // picks up settings changes without recreating
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val keys = SoftKeys.resolve(this)
        if (keys != null) {
            if (keyCode == keys.first) leftAction?.let { it(); return true }
            if (keyCode == keys.second) rightAction?.let { it(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
}
