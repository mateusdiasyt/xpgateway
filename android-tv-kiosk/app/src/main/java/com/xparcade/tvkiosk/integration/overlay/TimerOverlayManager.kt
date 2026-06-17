package com.xparcade.tvkiosk.integration.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class TimerOverlayManager(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private var overlayView: TextView? = null

    fun updateRemaining(remainingSeconds: Long) {
        val safeRemaining = remainingSeconds.coerceAtLeast(0)

        if (safeRemaining <= 0 || !canDrawOverlays(appContext)) {
            hide()
            return
        }

        mainHandler.post {
            val view = ensureView()
            view.text = "TEMPO ${formatRemaining(safeRemaining)}"
        }
    }

    fun hide() {
        mainHandler.post {
            val view = overlayView ?: return@post
            runCatching { windowManager?.removeView(view) }
            overlayView = null
        }
    }

    private fun ensureView(): TextView {
        overlayView?.let { return it }

        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f
            setColor(Color.argb(150, 7, 7, 7))
            setStroke(1, Color.argb(90, 255, 255, 255))
        }

        val view = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = false
            alpha = 0.78f
            setPadding(16, 8, 16, 8)
            this.background = background
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 32
            y = 28
        }

        runCatching {
            windowManager?.addView(view, params)
            overlayView = view
        }.onFailure {
            overlayView = null
        }

        return overlayView ?: view
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    companion object {
        fun canDrawOverlays(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        }

        fun buildSettingsIntent(context: Context): Intent {
            return Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        private fun formatRemaining(totalSeconds: Long): String {
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }
    }
}
