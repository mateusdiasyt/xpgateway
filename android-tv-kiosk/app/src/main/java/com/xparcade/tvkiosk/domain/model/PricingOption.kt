package com.xparcade.tvkiosk.domain.model

data class PricingOption(
    val label: String,
    val durationMinutes: Int,
    val amount: Double,
    val isCustom: Boolean = false
)
