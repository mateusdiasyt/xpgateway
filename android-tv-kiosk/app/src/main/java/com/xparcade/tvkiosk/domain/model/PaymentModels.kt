package com.xparcade.tvkiosk.domain.model

data class CreatePaymentRequest(
    val stationId: String,
    val durationMinutes: Int,
    val amount: Double? = null
)

data class CreatePaymentResponse(
    val sessionId: String,
    val paymentId: String,
    val qrCode: String,
    val pixCopiaECola: String,
    val status: String,
    val amount: Double,
    val durationMinutes: Int
)

data class SessionStatusResponse(
    val sessionId: String,
    val status: String,
    val durationMinutes: Int,
    val paidAt: String?,
    val startedAt: String?,
    val expiresAt: String?
)

data class ForceUnlockRequest(
    val durationMinutes: Int
)
