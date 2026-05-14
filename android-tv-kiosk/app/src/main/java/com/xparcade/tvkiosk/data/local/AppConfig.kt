package com.xparcade.tvkiosk.data.local

import com.xparcade.tvkiosk.BuildConfig

object UnlockMode {
    const val PIX_ONLY = "PIX_ONLY"
    const val PDV_ONLY = "PDV_ONLY"
    const val HYBRID = "HYBRID"

    private val allowed = setOf(PIX_ONLY, PDV_ONLY, HYBRID)

    fun normalize(raw: String?): String {
        val candidate = raw?.trim()?.uppercase() ?: HYBRID
        return if (candidate in allowed) candidate else HYBRID
    }
}

data class AppConfig(
    val backendUrl: String = BuildConfig.DEFAULT_BACKEND_URL,
    val stationId: String = BuildConfig.DEFAULT_STATION_ID,
    val stationName: String = BuildConfig.DEFAULT_STATION_NAME,
    val stationToken: String = BuildConfig.DEFAULT_STATION_TOKEN,
    val adminPin: String = "1234",
    val autoStartApp: Boolean = true,
    val price20: Double = 15.0,
    val customEnabled: Boolean = false,
    val customDurationMinutes: Int = 90,
    val customPrice: Double = 35.0,
    val adminApiKey: String = "change-me-admin-key",
    val unlockMode: String = UnlockMode.HYBRID
)
