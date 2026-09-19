package com.odiousapps.mx3launcher

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log

/**
 * Persistent foreground service whose only job is catching screen-on events
 * and forcing MX3 Launcher back to the foreground, including when its own
 * Activity/Compose UI isn't alive.
 *
 * Needs to be a foreground service, not a receiver tied to MainActivity's
 * lifecycle: Android can kill MX3 Launcher's process under memory pressure
 * while the screen is off (symptom: the system's Google launcher shows on
 * wake instead), leaving no Activity to host a lifecycle-bound receiver. A
 * foreground service survives that.
 *
 * SCREEN_ON can't be a static manifest receiver (unlike BOOT_COMPLETED, see
 * BootReceiver.kt) — it must be registered dynamically via
 * Context.registerReceiver(), hence needing a persistent component to host it.
 */
class ScreenWakeGuardService : Service() {

    companion object {
        private const val TAG = "ScreenWakeGuardService"
        private const val PERSISTENT_CHANNEL_ID = "mx3launcher_wake_guard"
        private const val WAKE_CHANNEL_ID = "mx3launcher_wake_trigger"
        private const val PERSISTENT_NOTIFICATION_ID = 1
        private const val WAKE_NOTIFICATION_ID = 2
    }

    private var screenOnReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannelsIfNeeded()
        startForeground(PERSISTENT_NOTIFICATION_ID, buildPersistentNotification())
        registerScreenOnReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: restart if killed, since staying alive is the whole point.
        return START_STICKY
    }

    private fun registerScreenOnReceiver() {
        if (screenOnReceiver != null) return // already registered, avoid double-registration
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON) {
                    Log.i(TAG, "Screen on — bringing MX3 Launcher to front")
                    bringLauncherToFront()
                }
            }
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        screenOnReceiver = receiver
    }

    /**
     * Tries a direct startActivity() first — cheap, and works fine in a
     * lot of real-world cases despite the theoretical background-launch
     * restriction risk. Falls back to a full-screen-intent notification,
     * the Android-sanctioned mechanism for forcing an activity to the
     * foreground from a background trigger (the same one alarm and
     * incoming-call apps use), which is specifically exempted from those
     * restrictions when delivered this way.
     *
     * Needs android.permission.USE_FULL_SCREEN_INTENT declared in the
     * manifest. On Android 14+ this permission is no longer
     * auto-granted for most apps the way it used to be. If the
     * fallback doesn't work either, check Settings -> Apps -> MX3
     * Launcher -> "Full screen notifications" (naming varies by OEM) and
     * confirm it's actually granted.
     */
    @SuppressLint("FullScreenIntentPolicy")
    private fun bringLauncherToFront() {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }

        try {
            startActivity(activityIntent)
            return
        } catch (e: Exception) {
            Log.w(TAG, "Direct startActivity() failed, falling back to full-screen intent", e)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(this, WAKE_CHANNEL_ID)
            .setContentTitle("MX3 Launcher")
            .setContentText("Returning to launcher")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)?.notify(WAKE_NOTIFICATION_ID, notification)
    }

    private fun createChannelsIfNeeded() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                PERSISTENT_CHANNEL_ID,
                "Launcher wake guard (ongoing)",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                WAKE_CHANNEL_ID,
                "Launcher wake guard (trigger)",
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    private fun buildPersistentNotification(): Notification {
        return Notification.Builder(this, PERSISTENT_CHANNEL_ID)
            .setContentTitle("MX3 Launcher active")
            .setContentText("Keeping the launcher ready after standby")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        screenOnReceiver?.let { unregisterReceiver(it) }
        screenOnReceiver = null
        super.onDestroy()
    }
}
