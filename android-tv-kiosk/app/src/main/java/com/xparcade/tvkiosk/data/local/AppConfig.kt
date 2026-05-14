package com.xparcade.tvkiosk.data.local

import com.xparcade.tvkiosk.BuildConfig

object UnlockMode {
    const val PDV_ONLY = "PDV_ONLY"
    private val allowed = setOf(PDV_ONLY)

    fun normalize(raw: String?): String {
        val candidate = raw?.trim()?.uppercase() ?: PDV_ONLY
        return if (candidate in allowed) candidate else PDV_ONLY
    }
}

data class AppConfig(
    val backendUrl: String = BuildConfig.DEFAULT_BACKEND_URL,
    val stationId: String = BuildConfig.DEFAULT_STATION_ID,
    val stationName: String = BuildConfig.DEFAULT_STATION_NAME,
    val stationToken: String = BuildConfig.DEFAULT_STATION_TOKEN,
    val deviceKey: String = BuildConfig.DEFAULT_DEVICE_KEY,
    val adminPin: String = "1234",
    val autoStartApp: Boolean = true,
    val price20: Double = 15.0,
    val customEnabled: Boolean = false,
    val customDurationMinutes: Int = 90,
    val customPrice: Double = 35.0,
    val adminApiKey: String = "change-me-admin-key",
    val unlockMode: String = UnlockMode.PDV_ONLY
)
