package com.zoo.reelcounter

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The floating "🧠 45" bubble. It is an accessibility overlay window, so it needs no
 * "display over other apps" permission. Drag it to move it; the spot is remembered.
 */
class CounterOverlay(private val service: AccessibilityService, private val store: CountStore) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private lateinit var countText: TextView
    private lateinit var bg: GradientDrawable

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun dp(v: Float) = (v * service.resources.displayMetrics.density).roundToInt()

    fun show() {
        (root ?: create()).visibility = View.VISIBLE
    }

    fun hide() {
        root?.visibility = View.GONE
    }

    fun update(total: Int, limit: Int, bump: Boolean) {
        val v = root ?: return
        countText.text = total.toString()
        val over = total >= limit
        bg.setColor(service.getColor(if (over) R.color.bad else R.color.pill))
        countText.setTextColor(if (over) 0xFFFFFFFF.toInt() else service.getColor(R.color.pill_text))
        if (bump && v.visibility == View.VISIBLE) {
            v.animate().cancel()
            v.animate().scaleX(1.22f).scaleY(1.22f).setDuration(110).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }.start()
        }
    }

    fun remove() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun create(): LinearLayout {
        bg = GradientDrawable().apply { cornerRadius = dp(24f).toFloat() }
        val brain = ImageView(service).apply {
            setImageResource(R.drawable.ic_brain)
            layoutParams = LinearLayout.LayoutParams(dp(28f), dp(28f))
        }
        countText = TextView(service).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.DEFAULT_BOLD
            fontFeatureSettings = "tnum"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(6f) }
        }
        val v = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(6f), dp(16f), dp(6f))
            background = bg
            elevation = dp(4f).toFloat()
            contentDescription = "Reels watched today"
            addView(brain)
            addView(countText)
        }

        val screenW = service.resources.displayMetrics.widthPixels
        params.x = store.overlayX.takeIf { it >= 0 } ?: (screenW - dp(130f))
        params.y = store.overlayY.takeIf { it >= 0 } ?: dp(90f)

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startX + (e.rawX - downX)).roundToInt().coerceAtLeast(0)
                    params.y = (startY + (e.rawY - downY)).roundToInt().coerceAtLeast(0)
                    runCatching { wm.updateViewLayout(v, params) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (abs(e.rawX - downX) > 4 || abs(e.rawY - downY) > 4) {
                        store.overlayX = params.x
                        store.overlayY = params.y
                    }
                }
            }
            true
        }

        wm.addView(v, params)
        root = v
        update(store.total(), store.limit, bump = false)
        return v
    }
}
