package io.github.theonionsarewatching.shtigletz.input

import android.view.KeyEvent

/**
 * Every hardware key is translated into one of these internal actions via the
 * active [KeyProfile]. Navigation actions are re-dispatched as canonical
 * KEYCODE_DPAD_* events so Android's normal focus system handles them, which
 * means even non-standard hardware keys drive standard focus traversal.
 * App actions are delivered to the current Activity via BaseActivity.onAppAction.
 */
enum class Action {
    UP, DOWN, LEFT, RIGHT, SELECT, BACK,
    COMPOSE, REPLY, DELETE, ARCHIVE,
    NEXT_MESSAGE, PREV_MESSAGE, OPEN_FOLDERS, MARK_READ, MENU;

    /** Canonical Android keycode for navigation actions; null for app actions. */
    fun canonicalKeyCode(): Int? = when (this) {
        UP -> KeyEvent.KEYCODE_DPAD_UP
        DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        SELECT -> KeyEvent.KEYCODE_DPAD_CENTER
        BACK -> KeyEvent.KEYCODE_BACK
        else -> null
    }

    val isNavigation: Boolean get() = canonicalKeyCode() != null
}
