package co.maxasif.reins.presentation.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Backs [FontSizeState], [ExtraKeysState], [SwipeAutocorrectState], and [SwipeSessionSwitchState]
 * with a plain SharedPreferences file so settings survive process death. Previously each of those
 * was process-lifetime-only state, which reset every slider/toggle/reorder back to defaults any
 * time Android killed the app in the background - most visibly after a long idle stretch (e.g. an
 * overnight-connected session), where the process is almost always gone by morning.
 *
 * [init] must run once before any of those objects' UI reads/writes - called from
 * `ReinsApplication.onCreate`, which runs before `MainActivity` ever composes Settings.
 */
object SettingsPreferences {
    private const val FILE_NAME = "reins_settings"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val loaded = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        prefs = loaded
        FontSizeState.restore(loaded)
        ExtraKeysState.restore(loaded)
        SwipeAutocorrectState.restore(loaded)
        SwipeSessionSwitchState.restore(loaded)
    }

    internal fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs?.edit()?.apply(block)?.apply()
    }
}
