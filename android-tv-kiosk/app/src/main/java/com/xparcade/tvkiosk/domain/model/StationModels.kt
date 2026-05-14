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

data class LiveSessionWrapper(
    val data: LiveSessionResponse?
)

data class LiveSessionResponse(
    val sessionId: String,
    val status: String,
    val durationMinutes: Int,
    val paidAt: String?,
    val startedAt: String?,
    val expiresAt: String?,
    val source: String?
)
