package com.vasu.assistant.ui.overlay

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.vasu.assistant.MainActivity
import com.vasu.assistant.avatar.AvatarManager
import com.vasu.assistant.core.settings.VasuSettings
import com.vasu.assistant.ui.theme.VasuIdle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * FIX-9 (spec Phase 7.1–7.3): the floating assistant orb.
 *
 * - Drawn over other apps via `TYPE_APPLICATION_OVERLAY` (SYSTEM_ALERT_WINDOW
 *   must be granted before [start]; the service refuses to run without it).
 * - Drag to move; releasing snaps the orb to the nearest horizontal edge.
 * - Tap opens the existing quick voice panel ([AssistantOverlayActivity]).
 * - Color + pulse follow [AvatarManager] (listening cyan, thinking amber,
 *   speaking purple, idle grey); with animations off it stays static.
 * - Foreground service (specialUse) so the window survives the launcher and
 *   other apps; stopping the service removes the window (no leak).
 */
@AndroidEntryPoint
class FloatingOrbService : Service() {

    @Inject lateinit var avatarManager: AvatarManager
    @Inject lateinit var settings: VasuSettings

    private lateinit var windowManager: WindowManager
    private var orbView: FloatingOrbView? = null
    private var orbParams: WindowManager.LayoutParams? = null
    private var snapAnimator: ValueAnimator? = null
    private var lastActive = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var initialX = 0
    private var initialY = 0
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var dragging = false
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForegroundSafely()
        addOrb()
        observeAvatar()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // ACTION_START (or a sticky restart): make sure the notification and
        // the window are up. Both calls are idempotent.
        startForegroundSafely()
        addOrb()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        removeOrb()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // --------------------------------------------------------------- orb window

    private fun addOrb() {
        if (orbView != null) return
        val sizePx = orbSizePx()
        val (screenWidth, screenHeight) = screenSize()
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = FloatingOrbGeometry.snapX(screenWidth - sizePx, screenWidth, sizePx)
            y = (screenHeight * 0.45f).roundToInt()
        }
        val view = FloatingOrbView(this)
        view.setOrbColor(VasuIdle.toArgb())
        view.setOnTouchListener { _, event -> handleTouch(event) }
        try {
            windowManager.addView(view, params)
        } catch (t: Throwable) {
            Log.e(TAG, "Cannot add orb window: ${t.message}", t)
            stopSelf()
            return
        }
        orbView = view
        orbParams = params
    }

    private fun removeOrb() {
        snapAnimator?.cancel()
        snapAnimator = null
        val view = orbView ?: return
        orbView = null
        orbParams = null
        try {
            windowManager.removeViewImmediate(view)
        } catch (t: Throwable) {
            Log.w(TAG, "Orb remove failed: ${t.message}")
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        val params = orbParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                initialX = params.x
                initialY = params.y
                touchDownX = event.rawX
                touchDownY = event.rawY
                dragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - touchDownX).roundToInt()
                val dy = (event.rawY - touchDownY).roundToInt()
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                if (dragging) {
                    val (screenWidth, screenHeight) = screenSize()
                    val size = orbSizePx()
                    params.x = FloatingOrbGeometry.clamp(initialX + dx, screenWidth, size)
                    params.y = FloatingOrbGeometry.clamp(initialY + dy, screenHeight, size)
                    safeUpdate(params)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging && event.actionMasked == MotionEvent.ACTION_UP) {
                    AssistantOverlayActivity.launch(this)
                } else if (dragging) {
                    snapToEdge(params)
                }
                return true
            }
        }
        return false
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val (screenWidth, _) = screenSize()
        val targetX = FloatingOrbGeometry.snapX(params.x, screenWidth, orbSizePx())
        if (targetX == params.x) return
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = SNAP_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                params.x = it.animatedValue as Int
                safeUpdate(params)
            }
            start()
        }
    }

    private fun safeUpdate(params: WindowManager.LayoutParams) {
        val view = orbView ?: return
        try {
            windowManager.updateViewLayout(view, params)
        } catch (t: Throwable) {
            Log.w(TAG, "Orb move failed: ${t.message}")
        }
    }

    // ------------------------------------------------------------------- state

    private fun observeAvatar() {
        scope.launch {
            avatarManager.orbColorHex.collect { hex ->
                orbView?.setOrbColor(parseColorSafe(hex))
            }
        }
        scope.launch {
            avatarManager.orbAnimation.collect { animation ->
                lastActive = animation != "idle_motion"
                applyMotion()
            }
        }
        scope.launch {
            settings.animationsEnabled.collect { applyMotion() }
        }
    }

    private fun applyMotion() {
        orbView?.setActive(active = lastActive, animated = settings.animationsEnabled.value)
    }

    private fun parseColorSafe(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (_: Throwable) {
        VasuIdle.toArgb()
    }

    // ------------------------------------------------------------- notification

    private fun startForegroundSafely() {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        } catch (t: Throwable) {
            // ForegroundServiceStartNotAllowedException / SecurityException on
            // strict devices — the orb window still works while we survive.
            Log.e(TAG, "startForeground failed: ${t.message}", t)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingOrbService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VASU orb active")
            .setContentText("Tap the orb to talk")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentIntent)
            .addAction(0, "Hide orb", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VASU Floating Orb",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the floating orb on top of other apps"
        }
        manager.createNotificationChannel(channel)
    }

    // ------------------------------------------------------------------ sizing

    private fun orbSizePx(): Int = (ORB_WINDOW_DP * resources.displayMetrics.density).roundToInt()

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }

    companion object {
        private const val TAG = "FloatingOrbService"
        private const val CHANNEL_ID = "vasu_floating_orb"
        private const val NOTIFICATION_ID = 4021
        private const val ORB_WINDOW_DP = 72
        private const val SNAP_DURATION_MS = 160L

        const val ACTION_START = "com.vasu.assistant.action.SHOW_FLOATING_ORB"
        const val ACTION_STOP = "com.vasu.assistant.action.HIDE_FLOATING_ORB"

        /** Returns false (and does nothing) when the overlay permission is missing. */
        fun start(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Overlay permission not granted; floating orb not started")
                return false
            }
            val intent = Intent(context, FloatingOrbService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
            return true
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOrbService::class.java))
        }
    }
}

/** Pure geometry for the orb window; unit-tested on the JVM. */
internal object FloatingOrbGeometry {

    /** Clamps a drag position so the orb stays fully on screen. */
    fun clamp(position: Int, screenSize: Int, orbSize: Int): Int =
        position.coerceIn(0, (screenSize - orbSize).coerceAtLeast(0))

    /** Snaps X to the nearest horizontal edge (0 or right-aligned max). */
    fun snapX(x: Int, screenWidth: Int, orbSize: Int): Int {
        val max = (screenWidth - orbSize).coerceAtLeast(0)
        val clamped = x.coerceIn(0, max)
        return if (clamped + orbSize / 2 <= screenWidth / 2) 0 else max
    }
}

/**
 * The orb itself: a glowing circle painted with the current voice-state color,
 * with an optional pulse while active (disabled when animations are off).
 */
private class FloatingOrbView(context: Context) : View(context) {

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var orbColor = 0x64748B.toInt()
    private var pulse: ValueAnimator? = null
    private var pulseScale = 1f

    fun setOrbColor(color: Int) {
        orbColor = color
        invalidate()
    }

    fun setActive(active: Boolean, animated: Boolean) {
        if (active && animated) {
            startPulse()
        } else {
            stopPulse()
        }
        invalidate()
    }

    private fun startPulse() {
        if (pulse != null) return
        pulse = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PULSE_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                pulseScale = 1f + 0.12f * it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        pulseScale = 1f
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPulse()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val density = resources.displayMetrics.density
        val coreRadius = 26f * density
        val glowRadius = 34f * density

        canvas.save()
        canvas.scale(pulseScale, pulseScale, cx, cy)

        glowPaint.shader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(withAlpha(orbColor, 0x59), 0x00000000),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        corePaint.shader = RadialGradient(
            cx, cy, coreRadius,
            intArrayOf(lighten(orbColor), orbColor),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreRadius, corePaint)

        canvas.restore()
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun lighten(color: Int): Int {
        val r = (Color.red(color) * 0.7 + 255 * 0.3).toInt()
        val g = (Color.green(color) * 0.7 + 255 * 0.3).toInt()
        val b = (Color.blue(color) * 0.7 + 255 * 0.3).toInt()
        return Color.rgb(r, g, b)
    }

    private companion object {
        const val PULSE_DURATION_MS = 900L
    }
}
