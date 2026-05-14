package com.xparcade.tvkiosk.domain.state

enum class AppState {
    IDLE,
    INITIAL_SETUP,
    SELECTING_TIME,
    PAYMENT_PENDING,
    PAYMENT_PAID,
    SESSION_PREPARING,
    SESSION_ACTIVE,
    SESSION_WARNING,
    SESSION_EXPIRED,
    ADMIN_MODE,
    ERROR
}
