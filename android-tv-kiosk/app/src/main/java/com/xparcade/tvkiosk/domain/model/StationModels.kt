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
    val stationId: String,
    val status: String,
    val saleId: String?,
    val planCode: String?,
    val unlockedUntil: String?,
    val remainingSeconds: Long,
    val serverTime: String
)
