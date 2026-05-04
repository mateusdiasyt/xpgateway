package com.xparcade.tvkiosk.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xparcade.tvkiosk.domain.model.ActiveSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "xp_tv_kiosk_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val backendUrl = stringPreferencesKey("backend_url")
        val stationId = stringPreferencesKey("station_id")
        val stationName = stringPreferencesKey("station_name")
        val stationToken = stringPreferencesKey("station_token")
        val adminPin = stringPreferencesKey("admin_pin")
        val adminApiKey = stringPreferencesKey("admin_api_key")
        val autoStartApp = booleanPreferencesKey("auto_start_app")
        val price30 = doublePreferencesKey("price_30")
        val price60 = doublePreferencesKey("price_60")
        val price120 = doublePreferencesKey("price_120")
        val customEnabled = booleanPreferencesKey("custom_enabled")
        val customDuration = intPreferencesKey("custom_duration")
        val customPrice = doublePreferencesKey("custom_price")

        val activeSessionId = stringPreferencesKey("active_session_id")
        val activeSessionExpiresAt = longPreferencesKey("active_session_expires_at")
        val activeSessionDuration = intPreferencesKey("active_session_duration")
        val activeSessionSource = stringPreferencesKey("active_session_source")
    }

    val configFlow: Flow<AppConfig> = context.dataStore.data.map { prefs ->
        AppConfig(
            backendUrl = prefs[Keys.backendUrl] ?: AppConfig().backendUrl,
            stationId = prefs[Keys.stationId] ?: AppConfig().stationId,
            stationName = prefs[Keys.stationName] ?: AppConfig().stationName,
            stationToken = prefs[Keys.stationToken] ?: AppConfig().stationToken,
            adminPin = prefs[Keys.adminPin] ?: AppConfig().adminPin,
            autoStartApp = prefs[Keys.autoStartApp] ?: AppConfig().autoStartApp,
            price30 = prefs[Keys.price30] ?: AppConfig().price30,
            price60 = prefs[Keys.price60] ?: AppConfig().price60,
            price120 = prefs[Keys.price120] ?: AppConfig().price120,
            customEnabled = prefs[Keys.customEnabled] ?: AppConfig().customEnabled,
            customDurationMinutes = prefs[Keys.customDuration] ?: AppConfig().customDurationMinutes,
            customPrice = prefs[Keys.customPrice] ?: AppConfig().customPrice,
            adminApiKey = prefs[Keys.adminApiKey] ?: AppConfig().adminApiKey
        )
    }

    suspend fun getConfig(): AppConfig = configFlow.first()

    suspend fun saveConfig(config: AppConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.backendUrl] = config.backendUrl
            prefs[Keys.stationId] = config.stationId
            prefs[Keys.stationName] = config.stationName
            prefs[Keys.stationToken] = config.stationToken
            prefs[Keys.adminPin] = config.adminPin
            prefs[Keys.autoStartApp] = config.autoStartApp
            prefs[Keys.price30] = config.price30
            prefs[Keys.price60] = config.price60
            prefs[Keys.price120] = config.price120
            prefs[Keys.customEnabled] = config.customEnabled
            prefs[Keys.customDuration] = config.customDurationMinutes
            prefs[Keys.customPrice] = config.customPrice
            prefs[Keys.adminApiKey] = config.adminApiKey
        }
    }

    suspend fun saveActiveSession(activeSession: ActiveSession) {
        context.dataStore.edit { prefs ->
            prefs[Keys.activeSessionId] = activeSession.sessionId
            prefs[Keys.activeSessionExpiresAt] = activeSession.expiresAtEpochMillis
            prefs[Keys.activeSessionDuration] = activeSession.durationMinutes
            prefs[Keys.activeSessionSource] = activeSession.source
        }
    }

    suspend fun clearActiveSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.activeSessionId)
            prefs.remove(Keys.activeSessionExpiresAt)
            prefs.remove(Keys.activeSessionDuration)
            prefs.remove(Keys.activeSessionSource)
        }
    }

    suspend fun getActiveSession(): ActiveSession? {
        val prefs: Preferences = context.dataStore.data.first()
        val sessionId = prefs[Keys.activeSessionId] ?: return null
        val expiresAt = prefs[Keys.activeSessionExpiresAt] ?: return null
        val duration = prefs[Keys.activeSessionDuration] ?: return null
        val source = prefs[Keys.activeSessionSource] ?: "payment"

        return ActiveSession(
            sessionId = sessionId,
            expiresAtEpochMillis = expiresAt,
            durationMinutes = duration,
            source = source
        )
    }
}
