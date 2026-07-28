package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.TrafficStats
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.*
import kotlin.math.max

class SpeedMonitorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var lastRxBytes = TrafficStats.getTotalRxBytes()
    private var lastTxBytes = TrafficStats.getTotalTxBytes()
    private var lastAppBytes = mutableMapOf<Int, Long>()


    private var connectivityManager: ConnectivityManager? = null
    private var isNetworkConnected = true

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isNetworkConnected = true
            updateNotification(SpeedTracker.currentDownloadSpeed.value, SpeedTracker.currentUploadSpeed.value)
        }

        override fun onLost(network: Network) {
            isNetworkConnected = false
            // Clear notification by passing 0s or we can actually stop foreground here if preferred.
            // For now, we update it to show 0/disconnected.
            updateNotification(0L, 0L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification(0L, 0L))

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager?.registerDefaultNetworkCallback(networkCallback)

        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startMonitoring() {
        val packageManager = packageManager

        scope.launch {
            while (isActive) {
                delay(1000)
                val currentRxBytes = TrafficStats.getTotalRxBytes()
                val currentTxBytes = TrafficStats.getTotalTxBytes()

                val rxSpeed = max(0L, currentRxBytes - lastRxBytes)
                val txSpeed = max(0L, currentTxBytes - lastTxBytes)

                lastRxBytes = currentRxBytes
                lastTxBytes = currentTxBytes

                SpeedTracker.currentDownloadSpeed.value = rxSpeed
                SpeedTracker.currentUploadSpeed.value = txSpeed

                // Track per app stats
                val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                val currentAppUsageList = mutableListOf<AppUsageInfo>()

                for (appInfo in installedApps) {
                    val uid = appInfo.uid
                    val uidRx = TrafficStats.getUidRxBytes(uid)
                    val uidTx = TrafficStats.getUidTxBytes(uid)

                    if (uidRx > 0 || uidTx > 0) {
                        val totalBytesForUid = uidRx + uidTx
                        val lastBytesForUid = lastAppBytes[uid] ?: 0L
                        val speedBytesPerSec = max(0L, totalBytesForUid - lastBytesForUid)

                        lastAppBytes[uid] = totalBytesForUid

                        if (speedBytesPerSec > 0 || totalBytesForUid > 0) {
                            val appName = packageManager.getApplicationLabel(appInfo).toString()
                            // Note: Retrieving icon might be heavy to do every second for many apps.
                            // We do it here for simplicity, but in a real app, it should be cached.
                            var icon: Drawable? = null
                            try {
                                icon = packageManager.getApplicationIcon(appInfo)
                            } catch (e: Exception) {}

                            currentAppUsageList.add(
                                AppUsageInfo(
                                    uid = uid,
                                    appName = appName,
                                    packageName = appInfo.packageName,
                                    icon = icon,
                                    currentSpeedBytesPerSec = speedBytesPerSec,
                                    totalBytes = totalBytesForUid
                                )
                            )
                        }
                    }
                }

                // Sort descending by speed
                SpeedTracker.appUsageList.value = currentAppUsageList.sortedByDescending { it.currentSpeedBytesPerSec }

                updateNotification(rxSpeed, txSpeed)
            }
        }
    }

    private fun updateNotification(rxSpeed: Long, txSpeed: Long) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, createNotification(rxSpeed, txSpeed))
    }

    private fun createNotification(rxSpeed: Long, txSpeed: Long): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

                if (!isNetworkConnected) {
            return NotificationCompat.Builder(this, "speed_channel")
                .setContentTitle("لا يوجد اتصال بالإنترنت")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build()
        }

        val totalSpeed = rxSpeed + txSpeed
        val speedParts = formatSpeedShort(totalSpeed)
        val iconBitmap = createTextBitmap(speedParts.first, speedParts.second)
        val smallIcon = IconCompat.createWithBitmap(iconBitmap)

        return NotificationCompat.Builder(this, "speed_channel")
            .setContentTitle("سرعة الإنترنت")
            .setContentText("تحميل ${formatSpeed(rxSpeed)} | رفع ${formatSpeed(txSpeed)}")
            .setSmallIcon(smallIcon)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun createTextBitmap(number: String, unit: String): Bitmap {
        val width = 100
        val height = 100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paintNumber = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 55f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val paintUnit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 35f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        // Draw number
        val numberY = height / 2f + 5f // Slightly above center
        canvas.drawText(number, width / 2f, numberY, paintNumber)

        // Draw unit
        val unitY = height - 5f // Bottom edge
        canvas.drawText(unit, width / 2f, unitY, paintUnit)

        return bitmap
    }

    private fun formatSpeedShort(bytes: Long): Pair<String, String> {
        return when {
            bytes < 1024 -> Pair("$bytes", "B")
            bytes < 1024 * 1024 -> Pair("${bytes / 1024}", "K")
            else -> Pair(String.format("%.1f", bytes / (1024f * 1024f)), "M")
        }
    }

    private fun formatSpeed(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B/s"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB/s"
            else -> String.format("%.1f MB/s", bytes / (1024f * 1024f))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "speed_channel",
                "مراقب السرعة",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "يعرض سرعة الإنترنت الحالية"
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager?.unregisterNetworkCallback(networkCallback)
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}