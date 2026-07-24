package com.example

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import android.provider.Settings
import java.util.Calendar

object NetworkStatsHelper {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageStatsPermission(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun getUsageStats(context: Context) {
        if (!hasUsageStatsPermission(context)) return

        val networkStatsManager = try {
            context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
        } catch (e: Throwable) {
            null
        } ?: return


        val dailyUsage = getUsage(networkStatsManager, getStartOfDay(), System.currentTimeMillis())
        SpeedTracker.rawDailyUsage.value = dailyUsage
        val monthlyUsage = getUsage(networkStatsManager, getStartOfMonth(), System.currentTimeMillis())
        SpeedTracker.rawMonthlyUsage.value = monthlyUsage

        SpeedTracker.dailyUsage.value = dailyUsage - SpeedTracker.dailyOffset.value
        SpeedTracker.monthlyUsage.value = monthlyUsage - SpeedTracker.monthlyOffset.value
    }

    private fun getUsage(networkStatsManager: NetworkStatsManager, start: Long, end: Long): Long {
        var totalUsage = 0L
        try {
            // Wi-Fi usage
            val wifiBucket = networkStatsManager.querySummaryForDevice(
                NetworkCapabilities.TRANSPORT_WIFI, null, start, end
            )
            totalUsage += wifiBucket.rxBytes + wifiBucket.txBytes

            // Mobile data usage
            val mobileBucket = networkStatsManager.querySummaryForDevice(
                NetworkCapabilities.TRANSPORT_CELLULAR, null, start, end
            )
            totalUsage += mobileBucket.rxBytes + mobileBucket.txBytes
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return totalUsage
    }

    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getStartOfMonth(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
