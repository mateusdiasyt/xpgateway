package com.xparcade.tvkiosk.domain.model

data class StationConfigResponse(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val pricingOptions: List<PricingOptionResponse>
)

data class PricingOptionResponse(
    val label: String,
    val durationMinutes: Int,
    val amount: Double
)

data class LastPaymentWrapper(
    val data: LastPaymentResponse?
)

data class LastPaymentResponse(
    val paymentId: String,
    val status: String,
    val sessionId: String,
    val amount: Double,
    val createdAt: String,
    val stationId: String
)

data class TvStatusResponse(
    val stationId: String? = null,
    val status: String = "LOCKED",
    val saleId: String? = null,
    val planCode: String? = null,
    val unlockedUntil: String? = null,
    val releasedUntil: String? = null,
    val serviceStartsAt: String? = null,
    val preparationEndsAt: String? = null,
    val preparationRemainingSeconds: Long? = null,
    val remainingSeconds: Long = 0,
    val serverTime: String = "",
    val requiresPairing: Boolean = false,
    val pairingUrl: String? = null,
    val adminPin: String? = null,
    val displayConfigVersion: Int = 1,
    val message: String? = null
)

data class TvDisplayGameResponse(
    val id: String = "",
    val title: String = "",
    val imageDataUrl: String = ""
)

data class TvDisplayTrailerResponse(
    val id: String = "",
    val title: String = "",
    val youtubeUrl: String = "",
    val youtubeVideoId: String = ""
)

data class TvDisplayConfigurationResponse(
    val games: List<TvDisplayGameResponse> = emptyList(),
    val trailers: List<TvDisplayTrailerResponse> = emptyList()
)

data class TvDisplaySnapshotResponse(
    val deviceId: String = "",
    val deviceLabel: String = "",
    val stationId: String = "",
    val tenantName: String = "Mendoza PDV",
    val tenantLogoDataUrl: String? = null,
    val displayConfigVersion: Int = 1,
    val displayConfig: TvDisplayConfigurationResponse = TvDisplayConfigurationResponse()
)

data class PairTvDeviceRequest(
    val pairingCode: String,
    val deviceName: String? = null,
    val appVersionCode: Int,
    val appVersionName: String
)

data class PairTvDeviceResponse(
    val status: String = "ERROR",
    val deviceToken: String? = null,
    val stationId: String? = null,
    val label: String? = null,
    val tenantSlug: String? = null,
    val tenantName: String? = null,
    val adminPin: String? = null,
    val message: String? = null
)
