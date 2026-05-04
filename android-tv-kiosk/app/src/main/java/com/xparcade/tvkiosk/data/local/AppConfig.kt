package com.xparcade.tvkiosk.data.local

import com.xparcade.tvkiosk.BuildConfig

data class AppConfig(
    val backendUrl: String = BuildConfig.DEFAULT_BACKEND_URL,
    val stationId: String = BuildConfig.DEFAULT_STATION_ID,
    val stationName: String = BuildConfig.DEFAULT_STATION_NAME,
    val stationToken: String = BuildConfig.DEFAULT_STATION_TOKEN,
    val adminPin: String = "1234",
    val autoStartApp: Boolean = true,
    val price30: Double = 15.0,
    val price60: Double = 25.0,
    val price120: Double = 45.0,
    val customEnabled: Boolean = false,
    val customDurationMinutes: Int = 90,
    val customPrice: Double = 35.0,
    val adminApiKey: String = "change-me-admin-key"
)
