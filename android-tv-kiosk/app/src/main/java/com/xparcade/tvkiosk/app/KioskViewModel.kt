package com.xparcade.tvkiosk.app

import android.app.Application
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xparcade.tvkiosk.data.local.AppConfig
import com.xparcade.tvkiosk.data.local.PreferencesRepository
import com.xparcade.tvkiosk.data.local.UnlockMode
import com.xparcade.tvkiosk.data.repository.BackendRepository
import com.xparcade.tvkiosk.domain.model.ActiveSession
import com.xparcade.tvkiosk.domain.model.PricingOption
import com.xparcade.tvkiosk.domain.state.AppState
import com.xparcade.tvkiosk.domain.state.KioskUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

class KioskViewModel(application: Application) : AndroidViewModel(application) {
    private val fixedDurationMinutes = 20

    private val preferencesRepository = PreferencesRepository(application.applicationContext)
    private val backendRepository = BackendRepository()

    private val _uiState = MutableStateFlow(KioskUiState())
    val uiState: StateFlow<KioskUiState> = _uiState.asStateFlow()

    private var config: AppConfig = AppConfig()
    private var countdownJob: Job? = null
    private var pdvPollJob: Job? = null

    private val secretSequence = listOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER
    )

    private val keyBuffer = ArrayDeque<Int>()

    private var warningFiveShown = false
    private var warningOneShown = false

    init {
        bootstrap()
    }

    fun shouldBlockBack(): Boolean {
        return uiState.value.appState != AppState.ADMIN_MODE
    }

    private fun fixedOption(currentConfig: AppConfig): PricingOption {
        return PricingOption(
            label = "20 MIN",
            durationMinutes = fixedDurationMinutes,
            amount = currentConfig.price20
        )
    }

    private fun bootstrap() {
        viewModelScope.launch {
            config = preferencesRepository.getConfig().copy(unlockMode = UnlockMode.PDV_ONLY)

            _uiState.update {
                it.copy(
                    stationName = config.stationName,
                    unlockMode = UnlockMode.PDV_ONLY,
                    paymentStatusMessage = "Aguardando liberacao pelo caixa...",
                    appState = AppState.IDLE
                )
            }

            restoreOrResetSession()
            refreshStationData()
        }
    }

    fun retryFromError() {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null, appState = AppState.IDLE) }
            restoreOrResetSession()
            refreshStationData()
        }
    }

    private fun defaultOptions(currentConfig: AppConfig): List<PricingOption> {
        return listOf(fixedOption(currentConfig))
    }

    fun refreshStationData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            config = preferencesRepository.getConfig().copy(unlockMode = UnlockMode.PDV_ONLY)

            val online = backendRepository.healthCheck(config)
            val pricing = defaultOptions(config)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    stationName = config.stationName,
                    pricingOptions = pricing,
                    backendOnline = online,
                    lastPaymentSummary = null,
                    unlockMode = UnlockMode.PDV_ONLY,
                    appState = if (it.activeSession != null) it.appState else AppState.SELECTING_TIME
                )
            }

            if (uiState.value.activeSession == null) {
                ensureUnlockFlow()
            }
        }
    }

    private fun restoreOrResetSession() {
        viewModelScope.launch {
            val active = preferencesRepository.getActiveSession()
            if (active == null) {
                _uiState.update { state ->
                    if (state.appState == AppState.IDLE) state.copy(appState = AppState.SELECTING_TIME) else state
                }
                return@launch
            }

            if (active.expiresAtEpochMillis <= System.currentTimeMillis()) {
                preferencesRepository.clearActiveSession()
                _uiState.update {
                    it.copy(
                        activeSession = null,
                        remainingSeconds = 0,
                        warningMessage = null,
                        appState = AppState.SESSION_EXPIRED
                    )
                }
                delay(1000)
                _uiState.update { it.copy(appState = AppState.SELECTING_TIME) }
            } else {
                activateSession(active)
            }
        }
    }

    private fun ensureUnlockFlow() {
        startPdvPolling()
        _uiState.update {
            it.copy(
                payment = null,
                selectedOption = null,
                unlockMode = UnlockMode.PDV_ONLY,
                appState = AppState.SELECTING_TIME,
                paymentStatusMessage = "Aguardando liberacao pelo caixa..."
            )
        }
    }

    private fun stopPdvPolling() {
        pdvPollJob?.cancel()
        pdvPollJob = null
    }

    private fun startPdvPolling() {
        if (pdvPollJob?.isActive == true) return

        pdvPollJob = viewModelScope.launch {
            while (true) {
                val result = runCatching { backendRepository.getTvStatus(config) }

                result.onSuccess { tvStatus ->
                    val isActiveStatus =
                        tvStatus.status.equals("ACTIVE", true) ||
                            tvStatus.status.equals("UNLOCKED", true) ||
                            tvStatus.status.equals("RELEASED", true)

                    if (isActiveStatus) {
                        val serverNow = parseIsoToMillis(tvStatus.serverTime) ?: System.currentTimeMillis()
                        val unlockedUntil = parseIsoToMillis(tvStatus.unlockedUntil)
                            ?: parseIsoToMillis(tvStatus.releasedUntil)
                        val expiresAt = unlockedUntil ?: (serverNow + tvStatus.remainingSeconds.coerceAtLeast(0) * 1000L)
                        val remaining = ((expiresAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
                        val durationMinutes = ((remaining + 59) / 60).toInt().coerceAtLeast(1)

                        if (expiresAt > System.currentTimeMillis()) {
                            val active = ActiveSession(
                                sessionId = tvStatus.saleId ?: "pdv-${System.currentTimeMillis()}",
                                expiresAtEpochMillis = expiresAt,
                                durationMinutes = durationMinutes,
                                source = "pdv"
                            )
                            activateSession(active)
                            return@launch
                        }
                    } else {
                        _uiState.update {
                            it.copy(paymentStatusMessage = "Aguardando liberacao pelo caixa...")
                        }
                    }
                }.onFailure {
                    _uiState.update {
                        it.copy(paymentStatusMessage = "Sem conexao com servidor. Chame um atendente.")
                    }
                }

                delay(3000)
            }
        }
    }

    fun onSelectPricing(option: PricingOption) {
        _uiState.update {
            it.copy(
                selectedOption = option,
                appState = AppState.SELECTING_TIME,
                paymentStatusMessage = "Liberacao somente via caixa. Procure o atendente."
            )
        }
    }

    fun cancelPayment() {
        _uiState.update {
            it.copy(
                payment = null,
                selectedOption = null,
                appState = AppState.SELECTING_TIME,
                paymentStatusMessage = "Aguardando liberacao pelo caixa..."
            )
        }
    }

    fun confirmMockPayment() {
        _uiState.update {
            it.copy(paymentStatusMessage = "Modo caixa ativo.")
        }
    }

    private fun activateSession(session: ActiveSession) {
        viewModelScope.launch {
            stopPdvPolling()
            warningFiveShown = false
            warningOneShown = false
            preferencesRepository.saveActiveSession(session)

            _uiState.update {
                it.copy(
                    appState = AppState.SESSION_ACTIVE,
                    activeSession = session,
                    payment = null,
                    paymentStatusMessage = "Sessao ativa via caixa",
                    warningMessage = null
                )
            }

            startCountdown(session)
        }
    }

    private fun startCountdown(session: ActiveSession) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val remainingSeconds = ((session.expiresAtEpochMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
                val warning = buildWarningText(remainingSeconds)

                _uiState.update {
                    it.copy(
                        remainingSeconds = remainingSeconds,
                        warningMessage = warning,
                        appState = if (warning.isNullOrBlank()) AppState.SESSION_ACTIVE else AppState.SESSION_WARNING
                    )
                }

                if (remainingSeconds <= 0) {
                    preferencesRepository.clearActiveSession()
                    _uiState.update {
                        it.copy(
                            activeSession = null,
                            warningMessage = null,
                            appState = AppState.SESSION_EXPIRED
                        )
                    }
                    delay(1200)
                    _uiState.update { it.copy(appState = AppState.SELECTING_TIME) }
                    refreshStationData()
                    return@launch
                }

                delay(1000)
            }
        }
    }

    private fun buildWarningText(remainingSeconds: Long): String? {
        return when {
            remainingSeconds <= 60 && !warningOneShown -> {
                warningOneShown = true
                "Seu tempo termina em 1 minuto"
            }

            remainingSeconds <= 300 && !warningFiveShown -> {
                warningFiveShown = true
                "Seu tempo termina em 5 minutos"
            }

            remainingSeconds <= 60 -> "Seu tempo termina em 1 minuto"
            remainingSeconds <= 300 -> "Seu tempo termina em 5 minutos"
            else -> null
        }
    }

    fun forceUnlockFromAdmin(durationMinutes: Int) {
        viewModelScope.launch {
            val result = runCatching {
                backendRepository.forceUnlock(config, durationMinutes)
            }

            result.onSuccess { payload ->
                val sessionId = payload["sessionId"]?.toString() ?: "manual-${System.currentTimeMillis()}"
                val expiresAtIso = payload["expiresAt"]?.toString()
                val expiresAt = parseIsoToMillis(expiresAtIso) ?: (System.currentTimeMillis() + durationMinutes * 60_000L)
                activateSession(
                    ActiveSession(
                        sessionId = sessionId,
                        expiresAtEpochMillis = expiresAt,
                        durationMinutes = durationMinutes,
                        source = "manual"
                    )
                )
            }.onFailure {
                val fallback = ActiveSession(
                    sessionId = "manual-local-${System.currentTimeMillis()}",
                    expiresAtEpochMillis = System.currentTimeMillis() + durationMinutes * 60_000L,
                    durationMinutes = durationMinutes,
                    source = "manual-local"
                )
                activateSession(fallback)
            }
        }
    }

    fun endCurrentSessionFromAdmin() {
        val active = uiState.value.activeSession ?: return
        viewModelScope.launch {
            runCatching {
                backendRepository.endSession(config, active.sessionId)
            }
            preferencesRepository.clearActiveSession()
            countdownJob?.cancel()
            _uiState.update {
                it.copy(
                    activeSession = null,
                    remainingSeconds = 0,
                    warningMessage = null,
                    appState = AppState.SELECTING_TIME,
                    paymentStatusMessage = "Aguardando liberacao pelo caixa..."
                )
            }
            ensureUnlockFlow()
        }
    }

    fun saveAdminConfig(newConfig: AppConfig) {
        viewModelScope.launch {
            val normalized = newConfig.copy(unlockMode = UnlockMode.PDV_ONLY)
            preferencesRepository.saveConfig(normalized)
            config = normalized
            _uiState.update {
                it.copy(
                    stationName = normalized.stationName,
                    unlockMode = UnlockMode.PDV_ONLY,
                    isAdminDialogVisible = false,
                    appState = if (it.activeSession == null) AppState.SELECTING_TIME else it.appState
                )
            }
            refreshStationData()
        }
    }

    fun onRemoteKeyDown(keyCode: Int) {
        if (keyBuffer.size >= secretSequence.size) {
            keyBuffer.removeFirst()
        }
        keyBuffer.addLast(keyCode)

        if (keyBuffer.size == secretSequence.size && keyBuffer.toList() == secretSequence) {
            keyBuffer.clear()
            _uiState.update {
                it.copy(
                    isAdminPinPromptVisible = true,
                    adminPinError = null
                )
            }
        }
    }

    fun submitAdminPin(pin: String) {
        if (pin == config.adminPin) {
            _uiState.update {
                it.copy(
                    isAdminPinPromptVisible = false,
                    adminPinError = null,
                    isAdminDialogVisible = true,
                    appState = AppState.ADMIN_MODE
                )
            }
        } else {
            _uiState.update {
                it.copy(adminPinError = "PIN invalido")
            }
        }
    }

    fun dismissAdminPinPrompt() {
        _uiState.update { it.copy(isAdminPinPromptVisible = false, adminPinError = null) }
    }

    fun dismissAdminDialog() {
        _uiState.update {
            it.copy(
                isAdminDialogVisible = false,
                appState = if (it.activeSession == null) AppState.SELECTING_TIME else AppState.SESSION_ACTIVE
            )
        }
    }

    fun formattedRemainingTime(): String {
        val total = uiState.value.remainingSeconds.coerceAtLeast(0)
        val minutes = total / 60
        val seconds = total % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    fun currentConfigSnapshot(): AppConfig = config

    private fun parseIsoToMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
    }
}
