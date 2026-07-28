package com.example

import android.graphics.drawable.Drawable
import kotlinx.coroutines.flow.MutableStateFlow

data class AppUsageInfo(
    val uid: Int,
    val appName: String,
    val packageName: String,
    val icon: Drawable?,
    val currentSpeedBytesPerSec: Long,
    val totalBytes: Long
)

object SpeedTracker {
    val currentDownloadSpeed = MutableStateFlow(0L)
    val currentUploadSpeed = MutableStateFlow(0L)

    // Per App Usage stats
    val appUsageList = MutableStateFlow<List<AppUsageInfo>>(emptyList())

    // Usage in bytes for current day and month to display in UI
    val rawDailyUsage = MutableStateFlow(0L)
    val rawMonthlyUsage = MutableStateFlow(0L)
    val dailyOffset = MutableStateFlow(0L)
    val monthlyOffset = MutableStateFlow(0L)
    val dailyUsage = MutableStateFlow(0L)
    val monthlyUsage = MutableStateFlow(0L)
}
