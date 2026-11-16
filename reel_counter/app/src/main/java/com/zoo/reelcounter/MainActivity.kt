package com.zoo.reelcounter

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var store: CountStore
    private lateinit var todayCount: TextView
    private lateinit var limitText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var perApp: TextView
    private lateinit var chart: BarChartView
    private lateinit var setupCard: View
    private lateinit var serviceStatus: TextView
    private lateinit var setupText: View
    private lateinit var enableBtn: View
    private lateinit var restrictedHint: View
    private lateinit var appInfoBtn: View
    private lateinit var limitValue: TextView
    private lateinit var overlaySwitch: Switch

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = CountStore(this)

        todayCount = findViewById(R.id.todayCount)
        limitText = findViewById(R.id.limitText)
        progress = findViewById(R.id.progress)
        perApp = findViewById(R.id.perApp)
        chart = findViewById(R.id.chart)
        setupCard = findViewById(R.id.setupCard)
        serviceStatus = findViewById(R.id.serviceStatus)
        setupText = findViewById(R.id.setupText)
        enableBtn = findViewById(R.id.enableBtn)
        restrictedHint = findViewById(R.id.restrictedHint)
        appInfoBtn = findViewById(R.id.appInfoBtn)
        limitValue = findViewById(R.id.limitValue)
        overlaySwitch = findViewById(R.id.overlaySwitch)

        applySystemBarPadding(findViewById(R.id.root))

        enableBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        appInfoBtn.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        }
        findViewById<Button>(R.id.minusBtn).setOnClickListener { store.limit = store.limit - 5 }
        findViewById<Button>(R.id.plusBtn).setOnClickListener { store.limit = store.limit + 5 }
        findViewById<Button>(R.id.resetBtn).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset today's count?")
                .setMessage("Today's reel count goes back to 0. Earlier days are kept.")
                .setPositiveButton("Reset") { _, _ -> store.resetToday() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        store.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        refresh()
    }

    override fun onPause() {
        store.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onPause()
    }

    private fun refresh() {
        val total = store.total()
        val limit = store.limit
        todayCount.text = total.toString()
        if (total >= limit) {
            limitText.text = "You've reached your daily limit of $limit"
            limitText.setTextColor(getColor(R.color.bad))
        } else {
            limitText.text = "reels watched · ${limit - total} left of your $limit limit"
            limitText.setTextColor(getColor(R.color.muted))
        }
        progress.progress = (total * 100 / limit).coerceAtMost(100)

        val parts = ReelCounterService.TRACKED_APPS.mapNotNull { (pkg, name) ->
            store.perApp(pkg).takeIf { it > 0 }?.let { "$name $it" }
        }
        perApp.text = if (parts.isEmpty()) "No reels counted yet today." else parts.joinToString("  ·  ")

        chart.setData(store.lastDays(7), limit)
        limitValue.text = limit.toString()

        overlaySwitch.setOnCheckedChangeListener(null)
        overlaySwitch.isChecked = store.overlayEnabled
        overlaySwitch.setOnCheckedChangeListener { _, on -> store.overlayEnabled = on }

        val on = isServiceEnabled()
        serviceStatus.text = if (on) "Counting is ON" else "Counting is OFF"
        serviceStatus.setTextColor(getColor(if (on) R.color.ok else R.color.bad))
        val setupVisibility = if (on) View.GONE else View.VISIBLE
        listOf(setupText, enableBtn, restrictedHint, appInfoBtn).forEach { it.visibility = setupVisibility }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, ReelCounterService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** Android 15 draws apps edge to edge; keep content clear of the status and navigation bars. */
    private fun applySystemBarPadding(root: View) {
        val baseTop = root.paddingTop
        val baseBottom = root.paddingBottom
        root.setOnApplyWindowInsetsListener { v, insets ->
            val (top, bottom) = if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                bars.top to bars.bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop to insets.systemWindowInsetBottom
            }
            v.setPadding(v.paddingLeft, baseTop + top, v.paddingRight, baseBottom + bottom)
            insets
        }
    }
}
