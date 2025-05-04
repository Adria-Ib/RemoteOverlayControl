package com.project.remoteoverlaycontrol

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var audioManager: AudioManager

    private val TAG = "OverlayService"
    private val TOLERANCE_PX = 30

    private object ButtonCoords {
        const val LEFT_X = 120f
        const val LEFT_Y = 840f
        const val RIGHT_X = 1673f
        const val RIGHT_Y = 840f
        const val UP_X = 180f
        const val UP_Y = 600f
        const val DOWN_X = 180f
        const val DOWN_Y = 251f
        const val CAMERA_X = 510f
        const val CAMERA_Y = 836f
        const val OK_X = 480f
        const val OK_Y = 56f
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        startForegroundService()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        overlayView = View(this)
        overlayView.setBackgroundColor(0x00000000) // Fully transparent

        setupListeners()

        try {
            windowManager.addView(overlayView, layoutParams)
            Log.d(TAG, "Overlay view added")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view", e)
            stopSelf()
        }
    }

    private fun setupListeners() {
        overlayView.setOnGenericMotionListener { _, event ->
            if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
                when (event.action) {
                    MotionEvent.ACTION_BUTTON_PRESS -> {
                        Log.d(TAG, "Primary Button Press Detected at X=${event.x}, Y=${event.y}")

                        val action = mapCoordinatesToAction(event.x, event.y)

                        if (action != RemoteAction.UNKNOWN) {
                            Log.i(TAG, "Action recognized: $action")
                            performAction(action)
                        } else {
                            Log.w(TAG, "Button press coordinates not mapped: X=${event.x}, Y=${event.y}")
                        }
                        return@setOnGenericMotionListener true
                    }

                    MotionEvent.ACTION_BUTTON_RELEASE -> {
                        Log.d(TAG, "Button Release Detected.")
                        // Do nothing, just consume the event
                        return@setOnGenericMotionListener true
                    }

                    else -> return@setOnGenericMotionListener true
                }
            }
            false
        }

        overlayView.setOnTouchListener { _, event ->
            Log.d(TAG, "onTouchEvent: ${MotionEvent.actionToString(event.action)}, Source: ${event.getToolType(0)}")
            true
        }
    }

    private enum class RemoteAction {
        VOLUME_UP, VOLUME_DOWN, REWIND, FAST_FORWARD, PLAY_PAUSE, CLOSE_OVERLAY, UNKNOWN
    }

    private fun mapCoordinatesToAction(x: Float, y: Float): RemoteAction {
        return when {
            coordsMatch(x, y, ButtonCoords.UP_X, ButtonCoords.UP_Y) -> RemoteAction.VOLUME_UP
            coordsMatch(x, y, ButtonCoords.DOWN_X, ButtonCoords.DOWN_Y) -> RemoteAction.VOLUME_DOWN
            coordsMatch(x, y, ButtonCoords.LEFT_X, ButtonCoords.LEFT_Y) -> RemoteAction.REWIND
            coordsMatch(x, y, ButtonCoords.RIGHT_X, ButtonCoords.RIGHT_Y) -> RemoteAction.FAST_FORWARD
            coordsMatch(x, y, ButtonCoords.OK_X, ButtonCoords.OK_Y) -> RemoteAction.PLAY_PAUSE
            coordsMatch(x, y, ButtonCoords.CAMERA_X, ButtonCoords.CAMERA_Y) -> RemoteAction.CLOSE_OVERLAY
            else -> RemoteAction.UNKNOWN
        }
    }

    private fun coordsMatch(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        return abs(x1 - x2) < TOLERANCE_PX && abs(y1 - y2) < TOLERANCE_PX
    }

    private fun performAction(action: RemoteAction) {
        when (action) {
            RemoteAction.VOLUME_UP -> performVolumeUp()
            RemoteAction.VOLUME_DOWN -> performVolumeDown()
            RemoteAction.REWIND -> performMediaKey(KeyEvent.KEYCODE_MEDIA_REWIND)
            RemoteAction.FAST_FORWARD -> performMediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            RemoteAction.PLAY_PAUSE -> performMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            RemoteAction.CLOSE_OVERLAY -> {
                Log.i(TAG, "Closing overlay by remote command")
                Toast.makeText(this, "Overlay closed", Toast.LENGTH_SHORT).show()
                stopSelf()
            }
            RemoteAction.UNKNOWN -> Log.w(TAG, "Attempted to perform UNKNOWN action")
        }
    }

    private fun performVolumeUp() {
        Log.d(TAG, "Adjusting Volume Up")
        try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for MODIFY_AUDIO_SETTINGS", e)
            Toast.makeText(this, "Volume permission denied", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting volume up", e)
        }
    }

    private fun performVolumeDown() {
        Log.d(TAG, "Adjusting Volume Down")
        try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for MODIFY_AUDIO_SETTINGS", e)
            Toast.makeText(this, "Volume permission denied", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting volume down", e)
        }
    }

    private fun performMediaKey(keyCode: Int) {
        Log.d(TAG, "Dispatching Media Key: $keyCode")
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        try {
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
            Log.d(TAG, "Media Key Dispatched Successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching media key event $keyCode", e)
            Toast.makeText(this, "Error sending media command", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startForegroundService() {
        val channelId = "overlay_channel_01"
        val channelName = "Overlay Control Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            chan.description = "Notification channel for the overlay control service"
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Remote Overlay Active")
            .setContentText("Listening for remote button presses...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        try {
            startForeground(101, notificationBuilder.build())
            Log.d(TAG, "Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        if (::overlayView.isInitialized && overlayView.windowToken != null) {
            try {
                windowManager.removeView(overlayView)
                Log.d(TAG, "Overlay view removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}