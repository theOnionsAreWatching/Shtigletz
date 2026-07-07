package io.github.theonionsarewatching.shtigletz.input

import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * A per-device key mapping profile.
 *
 * Physical keys on non-standard hardware do not reliably emit KEYCODE_DPAD_*.
 * Some emit numeric or vendor keycodes, and the same physical key can differ
 * across models. A profile records (scanCode, keyCode) -> Action pairs learned
 * during calibration. Resolution order:
 *   1. exact scanCode + keyCode match (scanCode disambiguates keys that share
 *      a keycode on the same device)
 *   2. keyCode-only match (used by the shipped default profile and as fallback)
 */
class KeyProfile(
    val name: String,
    val mappings: List<Mapping>
) {
    data class Mapping(val keyCode: Int, val scanCode: Int, val action: Action)

    fun resolve(event: KeyEvent): Action? {
        if (event.scanCode != 0) {
            mappings.firstOrNull { it.scanCode != 0 && it.scanCode == event.scanCode && it.keyCode == event.keyCode }
                ?.let { return it.action }
        }
        return mappings.firstOrNull { it.keyCode == event.keyCode }?.action
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (m in mappings) {
            arr.put(JSONObject().apply {
                put("keyCode", m.keyCode)
                put("scanCode", m.scanCode)
                put("action", m.action.name)
            })
        }
        return JSONObject().apply {
            put("name", name)
            put("mappings", arr)
        }.toString(2)
    }

    companion object {
        fun fromJson(json: String): KeyProfile {
            val o = JSONObject(json)
            val arr = o.getJSONArray("mappings")
            val list = ArrayList<Mapping>(arr.length())
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val action = runCatching { Action.valueOf(m.getString("action")) }.getOrNull() ?: continue
                list.add(Mapping(m.getInt("keyCode"), m.optInt("scanCode", 0), action))
            }
            return KeyProfile(o.optString("name", "imported"), list)
        }

        /** Standard Android dpad hardware. Used when no saved or bundled profile exists. */
        fun standardDefault(): KeyProfile = KeyProfile(
            "standard-default",
            listOf(
                Mapping(KeyEvent.KEYCODE_DPAD_UP, 0, Action.UP),
                Mapping(KeyEvent.KEYCODE_DPAD_DOWN, 0, Action.DOWN),
                Mapping(KeyEvent.KEYCODE_DPAD_LEFT, 0, Action.LEFT),
                Mapping(KeyEvent.KEYCODE_DPAD_RIGHT, 0, Action.RIGHT),
                Mapping(KeyEvent.KEYCODE_DPAD_CENTER, 0, Action.SELECT),
                Mapping(KeyEvent.KEYCODE_ENTER, 0, Action.SELECT),
                Mapping(KeyEvent.KEYCODE_BACK, 0, Action.BACK),
                Mapping(KeyEvent.KEYCODE_MENU, 0, Action.MENU)
            )
        )
    }
}
