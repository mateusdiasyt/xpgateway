package com.xparcade.tvkiosk.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.xparcade.tvkiosk.R
import com.xparcade.tvkiosk.app.MainActivity
import com.xparcade.tvkiosk.data.local.AppConfig
import com.xparcade.tvkiosk.data.repository.BackendRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SessionGuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val backendRepository = BackendRepository()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val expiresAt = intent?.getLongExtra(EXTRA_EXPIRES_AT, 0L) ?: 0L
        val config = AppConfig(
            isConfigured = true,
            backendUrl = intent?.getStringExtra(EXTRA_BACKEND_URL).orEmpty(),
            stationId = intent?.getStringExtra(EXTRA_STATION_ID).orEmpty(),
            stationName = intent?.getStringExtra(EXTRA_STATION_NAME).orEmpty(),
            stationToken = intent?.getStringExtra(EXTRA_STATION_TOKEN).orEmpty(),
            deviceKey = intent?.getStringExtra(EXTRA_DEVICE_KEY).orEmpty()
        )

        if (expiresAt <= System.currentTimeMillis() || config.backendUrl.isBlank() || config.stationId.isBlank()) {
            openKioskAndStop()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(config.stationName.ifBlank { config.stationId }))
        scope.launch {
            monitorSession(expiresAt, config)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun monitorSession(expiresAt: Long, config: AppConfig) {
        var nextServerCheckAt = 0L

        while (true) {
            val now = System.currentTimeMillis()

            if (now >= expiresAt) {
                openKioskAndStop()
                return
            }

            if (now >= nextServerCheckAt) {
                val remoteStatus = runCatching { backendRepository.getTvStatus(config) }.getOrNull()
                val isRemoteActive = remoteStatus?.status.equals("ACTIVE", true) ||
                    remoteStatus?.status.equals("UNLOCKED", true) ||
                    remoteStatus?.status.equals("RELEASED", true) ||
                    remoteStatus?.status.equals("PREPARING", true)

                if (remoteStatus != null && (!isRemoteActive || remoteStatus.remainingSeconds <= 0)) {
                    openKioskAndStop()
                    return
                }

                nextServerCheckAt = now + SERVER_CHECK_INTERVAL_MS
            }

            delay(TIMER_TICK_MS)
        }
    }

    private fun openKioskAndStop() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        runCatching {
            getSystemService(ActivityManager::class.java)?.appTasks?.firstOrNull()?.moveToFront()
        }

        runCatching {
            startActivity(intent)
        }

        stopSelf()
    }

    private fun buildNotification(stationName: String): Notification {
        ensureNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("XP Arcade")
                .setContentText("$stationName liberada. O bloqueio volta no fim do tempo.")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("XP Arcade")
                .setContentText("$stationName liberada. O bloqueio volta no fim do tempo.")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Controle de sessao",
            NotificationManager.IMPORTANCE_LOW
        )
        manager?.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "xp_session_guard"
        private const val NOTIFICATION_ID = 1042
        private const val TIMER_TICK_MS = 1000L
        private const val SERVER_CHECK_INTERVAL_MS = 5000L

        const val EXTRA_EXPIRES_AT = "expiresAt"
        const val EXTRA_BACKEND_URL = "backendUrl"
        const val EXTRA_STATION_ID = "stationId"
        const val EXTRA_STATION_NAME = "stationName"
        const val EXTRA_STATION_TOKEN = "stationToken"
        const val EXTRA_DEVICE_KEY = "deviceKey"
    }
}
