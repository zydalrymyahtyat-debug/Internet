package com.example

import kotlinx.coroutines.flow.MutableStateFlow

object SpeedTracker {
    val currentDownloadSpeed = MutableStateFlow(0L)
    val currentUploadSpeed = MutableStateFlow(0L)

    // Usage in bytes for current day and month to display in UI
    val rawDailyUsage = MutableStateFlow(0L)
    val rawMonthlyUsage = MutableStateFlow(0L)
    val dailyOffset = MutableStateFlow(0L)
    val monthlyOffset = MutableStateFlow(0L)
    val dailyUsage = MutableStateFlow(0L)
    val monthlyUsage = MutableStateFlow(0L)
}
