package com.example

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsManager {
    val DAILY_LIMIT_MB = intPreferencesKey("daily_limit_mb")
    val ICON_STYLE = stringPreferencesKey("icon_style")
    val GAUGE_COLOR = stringPreferencesKey("gauge_color")
    val DATA_OFFSET_DAILY = longPreferencesKey("data_offset_daily")
    val DATA_OFFSET_MONTHLY = longPreferencesKey("data_offset_monthly")

    fun getDailyLimit(context: Context): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            preferences[DAILY_LIMIT_MB] ?: 0 // 0 means no limit
        }
    }

    suspend fun setDailyLimit(context: Context, limit: Int) {
        context.dataStore.edit { preferences ->
            preferences[DAILY_LIMIT_MB] = limit
        }
    }

    fun getIconStyle(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[ICON_STYLE] ?: "default"
        }
    }

    suspend fun setIconStyle(context: Context, style: String) {
        context.dataStore.edit { preferences ->
            preferences[ICON_STYLE] = style
        }
    }

    fun getGaugeColor(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[GAUGE_COLOR] ?: "Primary"
        }
    }

    suspend fun setGaugeColor(context: Context, colorName: String) {
        context.dataStore.edit { preferences ->
            preferences[GAUGE_COLOR] = colorName
        }
    }

    fun getDataOffsets(context: Context): Flow<Pair<Long, Long>> {
        return context.dataStore.data.map { preferences ->
            val daily = preferences[DATA_OFFSET_DAILY] ?: 0L
            val monthly = preferences[DATA_OFFSET_MONTHLY] ?: 0L
            Pair(daily, monthly)
        }
    }

    suspend fun setDataOffsets(context: Context, dailyOffset: Long, monthlyOffset: Long) {
        context.dataStore.edit { preferences ->
            preferences[DATA_OFFSET_DAILY] = dailyOffset
            preferences[DATA_OFFSET_MONTHLY] = monthlyOffset
        }
    }
}
