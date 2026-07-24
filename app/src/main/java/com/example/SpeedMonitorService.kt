package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification(0L, 0L))
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startMonitoring() {
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

        val totalSpeed = rxSpeed + txSpeed
        val speedParts = formatSpeedShort(totalSpeed)
        val iconBitmap = createTextBitmap(speedParts.first, speedParts.second)
        val smallIcon = IconCompat.createWithBitmap(iconBitmap)

        return NotificationCompat.Builder(this, "speed_channel")
            .setContentTitle("سرعة الإنترنت")
            .setContentText("↓ ${formatSpeed(rxSpeed)} | ↑ ${formatSpeed(txSpeed)}")
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
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}