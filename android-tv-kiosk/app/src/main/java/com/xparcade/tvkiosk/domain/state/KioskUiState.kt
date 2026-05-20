package com.xparcade.tvkiosk.domain.state

import com.xparcade.tvkiosk.domain.model.ActiveSession
import com.xparcade.tvkiosk.domain.model.CreatePaymentResponse
import com.xparcade.tvkiosk.domain.model.PricingOption
import com.xparcade.tvkiosk.data.local.StationPreset
import com.xparcade.tvkiosk.integration.hdmi.HdmiInputInfo

data class KioskUiState(
    val appState: AppState = AppState.IDLE,
    val stationName: String = "TV",
    val pricingOptions: List<PricingOption> = emptyList(),
    val selectedOption: PricingOption? = null,
    val payment: CreatePaymentResponse? = null,
    val activeSession: ActiveSession? = null,
    val remainingSeconds: Long = 0,
    val preparationRemainingSeconds: Long = 0,
    val stationPresets: List<StationPreset> = emptyList(),
    val warningMessage: String? = null,
    val paymentStatusMessage: String = "Aguardando liberacao pelo caixa...",
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val isAdminPinPromptVisible: Boolean = false,
    val adminPinError: String? = null,
    val isAdminDialogVisible: Boolean = false,
    val backendOnline: Boolean = true,
    val lastPaymentSummary: String? = null,
    val unlockMode: String = "HYBRID",
    val hdmiInputs: List<HdmiInputInfo> = emptyList(),
    val hdmiStatusMessage: String? = null,
    val isDefaultLauncher: Boolean = false,
    val launcherStatusMessage: String? = null,
    val launcherDiagnostics: List<String> = emptyList()
)
