package com.zoo.reelcounter

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Daily reel counts and settings, kept in SharedPreferences on the device. */
class CountStore(context: Context) {

    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("reel_counter", Context.MODE_PRIVATE)

    private fun key(date: LocalDate) = "t_$date"
    private fun appKey(date: LocalDate, pkg: String) = "a_${date}_$pkg"

    fun total(date: LocalDate = LocalDate.now()): Int = prefs.getInt(key(date), 0)

    fun perApp(pkg: String, date: LocalDate = LocalDate.now()): Int = prefs.getInt(appKey(date, pkg), 0)

    /** Adds one reel for [pkg] today and returns today's new total. */
    fun increment(pkg: String): Int {
        val d = LocalDate.now()
        val t = total(d) + 1
        prefs.edit()
            .putInt(key(d), t)
            .putInt(appKey(d, pkg), perApp(pkg, d) + 1)
            .apply()
        return t
    }

    fun resetToday() {
        val d = LocalDate.now()
        val e = prefs.edit().remove(key(d))
        ReelCounterService.TRACKED_APPS.keys.forEach { e.remove(appKey(d, it)) }
        e.apply()
    }

    /** Oldest first; the last entry is today. */
    fun lastDays(n: Int): List<Pair<String, Int>> {
        val today = LocalDate.now()
        return (n - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val label = if (back == 0) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            label to total(d)
        }
    }

    var limit: Int
        get() = prefs.getInt(KEY_LIMIT, 50)
        set(v) = prefs.edit().putInt(KEY_LIMIT, v.coerceIn(5, 500)).apply()

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY, true)
        set(v) = prefs.edit().putBoolean(KEY_OVERLAY, v).apply()

    /** Saved bubble position; -1 means "use the default spot". */
    var overlayX: Int
        get() = prefs.getInt("overlay_x", -1)
        set(v) = prefs.edit().putInt("overlay_x", v).apply()
    var overlayY: Int
        get() = prefs.getInt("overlay_y", -1)
        set(v) = prefs.edit().putInt("overlay_y", v).apply()

    companion object {
        const val KEY_LIMIT = "limit"
        const val KEY_OVERLAY = "overlay"
    }
}
