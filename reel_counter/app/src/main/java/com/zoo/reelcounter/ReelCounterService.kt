package com.zoo.reelcounter

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlin.math.abs

/**
 * Notices when you swipe to a new reel in short-video apps and counts it.
 *
 * How a swipe is detected: reel players are full-screen vertical pagers. When one comes to rest,
 * it sends a "view scrolled" event where exactly one item is visible (fromIndex == toIndex).
 * A change of that index means a new reel. Normal feeds show several items at once, so they don't
 * match. For apps that don't report indexes, a full-screen-height scroll distance counts as one reel.
 *
 * Nothing on screen is read or stored, only the count.
 */
class ReelCounterService : AccessibilityService() {

    companion object {
        val TRACKED_APPS = linkedMapOf(
            "com.instagram.android" to "Instagram",
            "com.google.android.youtube" to "YouTube",
            "com.facebook.katana" to "Facebook",
            "com.snapchat.android" to "Snapchat",
            "in.mohalla.video" to "Moj",
            "com.eterno.shortvideos" to "Josh",
            "com.zhiliaoapp.musically" to "TikTok",
        )
        private const val MIN_GAP_MS = 350L          // ignore duplicate events for one swipe
        private const val DELTA_RESET_MS = 1500L     // forget partial scroll distance after a pause
    }

    private lateinit var store: CountStore
    private var overlay: CounterOverlay? = null
    private var currentPkg: String? = null
    private val lastIndex = HashMap<String, Int>()
    private var lastCountAt = 0L
    private var deltaSum = 0
    private var lastDeltaAt = 0L
    private var imePackages: Set<String> = emptySet()
    private val rect = Rect()

    // Held as a field: SharedPreferences keeps only weak references to listeners.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when {
            key == CountStore.KEY_OVERLAY -> updateOverlayVisibility()
            key == null || key == CountStore.KEY_LIMIT || key.startsWith("t_") ->
                overlay?.update(store.total(), store.limit, bump = false)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = CountStore(this)
        overlay = CounterOverlay(this, store)
        imePackages = getSystemService(InputMethodManager::class.java)
            ?.inputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
        store.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onAppInFront(pkg)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (pkg in TRACKED_APPS) onScrolled(event, pkg)
        }
    }

    private fun onAppInFront(pkg: String) {
        // Ignore our own bubble, the status bar/notification shade and keyboards.
        if (pkg == packageName || pkg == "com.android.systemui" || pkg in imePackages) return
        if (pkg != currentPkg) {
            currentPkg = pkg
            deltaSum = 0
            updateOverlayVisibility()
        }
    }

    private fun updateOverlayVisibility() {
        val o = overlay ?: return
        if (store.overlayEnabled && currentPkg in TRACKED_APPS) o.show() else o.hide()
    }

    private fun onScrolled(event: AccessibilityEvent, pkg: String) {
        if (currentPkg != pkg) onAppInFront(pkg)

        val screenH = resources.displayMetrics.heightPixels
        val source = event.source ?: return
        source.getBoundsInScreen(rect)
        // Reel players fill (almost) the whole screen; skip smaller lists like comments or stories rows.
        if (rect.height() < screenH * 0.6f) return

        val from = event.fromIndex
        val to = event.toIndex
        if (from >= 0 && to >= 0) {
            if (from != to) return                       // mid-swipe, or a feed showing several items
            val key = pkg + "#" + (source.viewIdResourceName ?: "")
            val prev = lastIndex.put(key, from)
            if (prev != from) countReel(pkg)
        } else if (Build.VERSION.SDK_INT >= 28) {
            val dy = event.scrollDeltaY
            if (dy == 0 || dy == -1) return
            val now = SystemClock.uptimeMillis()
            if (now - lastDeltaAt > DELTA_RESET_MS) deltaSum = 0
            lastDeltaAt = now
            deltaSum += abs(dy)
            if (deltaSum >= rect.height() * 0.55f) {
                deltaSum = 0
                countReel(pkg)
            }
        }
    }

    private fun countReel(pkg: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastCountAt < MIN_GAP_MS) return
        lastCountAt = now

        val total = store.increment(pkg)
        val limit = store.limit
        overlay?.update(total, limit, bump = true)
        if (total == limit) onLimitReached(limit)
    }

    private fun onLimitReached(limit: Int) {
        Toast.makeText(this, "You've watched $limit reels today. Time for a break?", Toast.LENGTH_LONG).show()
        @Suppress("DEPRECATION")
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (::store.isInitialized) store.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        overlay?.remove()
        overlay = null
        super.onDestroy()
    }
}
