package com.xparcade.tvkiosk.domain.model

data class ActiveSession(
    val sessionId: String,
    val expiresAtEpochMillis: Long,
    val durationMinutes: Int,
    val source: String
)
