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
    private var pollJob: Job? = null
    private var countdownJob: Job? = null
    private var autoPaymentJob: Job? = null
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
            config = preferencesRepository.getConfig()

            _uiState.update {
                it.copy(
                    stationName = config.stationName,
                    unlockMode = config.unlockMode,
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

            config = preferencesRepository.getConfig()

            val online = backendRepository.healthCheck(config)
            var pricing = defaultOptions(config)
            var lastPayment: String? = null

            if (online) {
                runCatching {
                    val stationConfig = backendRepository.getStationConfig(config)
                    pricing = stationConfig.pricingOptions.map {
                        PricingOption(
                            label = it.label,
                            durationMinutes = it.durationMinutes,
                            amount = it.amount,
                            isCustom = false
                        )
                    }.filter { it.durationMinutes == fixedDurationMinutes }
                    lastPayment = backendRepository.getLastPaymentSummary(config)

                    if (pricing.isEmpty()) {
                        pricing = defaultOptions(config)
                    }
                }.onFailure {
                    pricing = defaultOptions(config)
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    stationName = config.stationName,
                    pricingOptions = pricing,
                    backendOnline = online,
                    lastPaymentSummary = lastPayment,
                    unlockMode = config.unlockMode,
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
        when (UnlockMode.normalize(config.unlockMode)) {
            UnlockMode.PDV_ONLY -> {
                stopAutoPayment()
                startPdvPolling()
                _uiState.update {
                    it.copy(
                        payment = null,
                        selectedOption = null,
                        appState = AppState.SELECTING_TIME,
                        paymentStatusMessage = "Aguardando liberacao pelo PDV..."
                    )
                }
            }

            UnlockMode.HYBRID -> {
                startPdvPolling()
                ensureAutoPayment()
            }

            else -> {
                stopPdvPolling()
                ensureAutoPayment()
            }
        }
    }

    private fun stopAutoPayment() {
        autoPaymentJob?.cancel()
        autoPaymentJob = null
    }

    private fun stopPdvPolling() {
        pdvPollJob?.cancel()
        pdvPollJob = null
    }

    private fun startPdvPolling() {
        if (pdvPollJob?.isActive == true) return

        pdvPollJob = viewModelScope.launch {
            while (true) {
                val result = runCatching { backendRepository.getLiveSession(config) }

                result.onSuccess { liveSession ->
                    if (liveSession != null && (liveSession.status.equals("PAID", true) || liveSession.status.equals("ACTIVE", true))) {
                        val expiresAt = parseIsoToMillis(liveSession.expiresAt)
                            ?: (System.currentTimeMillis() + liveSession.durationMinutes * 60_000L)

                        if (expiresAt > System.currentTimeMillis()) {
                            val active = ActiveSession(
                                sessionId = liveSession.sessionId,
                                expiresAtEpochMillis = expiresAt,
                                durationMinutes = liveSession.durationMinutes,
                                source = liveSession.source?.lowercase() ?: "pdv"
                            )
                            activateSession(active)
                            return@launch
                        }
                    } else {
                        val mode = UnlockMode.normalize(config.unlockMode)
                        if (mode == UnlockMode.PDV_ONLY || uiState.value.appState != AppState.PAYMENT_PENDING) {
                            _uiState.update {
                                it.copy(
                                    paymentStatusMessage = if (mode == UnlockMode.PDV_ONLY) {
                                        "Aguardando liberacao pelo PDV..."
                                    } else {
                                        it.paymentStatusMessage
                                    }
                                )
                            }
                        }
                    }
                }.onFailure {
                    if (UnlockMode.normalize(config.unlockMode) == UnlockMode.PDV_ONLY) {
                        _uiState.update {
                            it.copy(paymentStatusMessage = "Sem conexao com backend. Aguardando PDV...")
                        }
                    }
                }

                delay(3000)
            }
        }
    }

    private fun ensureAutoPayment() {
        if (UnlockMode.normalize(config.unlockMode) == UnlockMode.PDV_ONLY) return

        val state = uiState.value
        if (state.activeSession != null) return
        if (state.appState == AppState.PAYMENT_PENDING && state.payment != null) return
        if (autoPaymentJob?.isActive == true) return

        autoPaymentJob = viewModelScope.launch {
            if (!uiState.value.backendOnline) {
                _uiState.update {
                    it.copy(
                        appState = AppState.SELECTING_TIME,
                        paymentStatusMessage = "Sem conexao. Tentando reconectar..."
                    )
                }
                delay(4000)
                refreshStationData()
                return@launch
            }

            val option = uiState.value.pricingOptions.firstOrNull() ?: fixedOption(config)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    selectedOption = option,
                    appState = AppState.SELECTING_TIME,
                    paymentStatusMessage = "Gerando Pix..."
                )
            }

            runCatching {
                backendRepository.createPayment(config, option.durationMinutes, option.amount)
            }.onSuccess { payment ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedOption = option,
                        payment = payment,
                        appState = AppState.PAYMENT_PENDING,
                        paymentStatusMessage = "Escaneie e pague para liberar automaticamente."
                    )
                }
                startPaymentPolling(payment.sessionId)
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        appState = AppState.SELECTING_TIME,
                        payment = null,
                        selectedOption = null,
                        paymentStatusMessage = "Nao foi possivel gerar o Pix. Tentando novamente..."
                    )
                }
                delay(3500)
                refreshStationData()
            }
        }
    }

    fun onSelectPricing(option: PricingOption) {
        if (UnlockMode.normalize(config.unlockMode) == UnlockMode.PDV_ONLY) {
            _uiState.update {
                it.copy(
                    appState = AppState.SELECTING_TIME,
                    paymentStatusMessage = "Modo PDV ativo. Libere a estacao pelo sistema de vendas."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    selectedOption = option,
                    errorMessage = null
                )
            }

            runCatching {
                backendRepository.createPayment(config, option.durationMinutes, option.amount)
            }.onSuccess { payment ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedOption = option,
                        payment = payment,
                        appState = AppState.PAYMENT_PENDING,
                        paymentStatusMessage = "Aguardando pagamento..."
                    )
                }
                startPaymentPolling(payment.sessionId)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        appState = AppState.ERROR,
                        errorMessage = error.message ?: "Não foi possível gerar o Pix."
                    )
                }
            }
        }
    }

    fun cancelPayment() {
        pollJob?.cancel()
        _uiState.update {
            it.copy(
                payment = null,
                selectedOption = null,
                appState = AppState.SELECTING_TIME,
                paymentStatusMessage = "Aguardando pagamento..."
            )
        }
        ensureUnlockFlow()
    }

    fun confirmMockPayment() {
        val payment = uiState.value.payment ?: return

        viewModelScope.launch {
            runCatching {
                backendRepository.confirmMockPayment(config, payment.paymentId)
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(paymentStatusMessage = "Pagamento confirmado. Liberando TV...")
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(paymentStatusMessage = "Falha ao confirmar mock: ${error.message}")
                }
            }
        }
    }

    private fun startPaymentPolling(sessionId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val result = runCatching {
                    backendRepository.getSessionStatus(config, sessionId)
                }

                result.onSuccess { status ->
                    when (status.status.uppercase()) {
                        "PENDING" -> {
                            _uiState.update { it.copy(paymentStatusMessage = "Aguardando pagamento...") }
                        }

                        "PAID", "ACTIVE" -> {
                            val expiresAt = parseIsoToMillis(status.expiresAt)
                                ?: (System.currentTimeMillis() + status.durationMinutes * 60_000L)
                            val active = ActiveSession(
                                sessionId = status.sessionId,
                                expiresAtEpochMillis = expiresAt,
                                durationMinutes = status.durationMinutes,
                                source = "payment"
                            )
                            activateSession(active)
                            return@launch
                        }

                        "EXPIRED", "CANCELLED" -> {
                            _uiState.update {
                                it.copy(
                                    paymentStatusMessage = "Pagamento expirado. Gerando um novo Pix...",
                                    appState = AppState.SELECTING_TIME,
                                    payment = null,
                                    selectedOption = null
                                )
                            }
                            delay(1200)
                            refreshStationData()
                            return@launch
                        }

                        else -> {
                            _uiState.update {
                                it.copy(
                                    paymentStatusMessage = "Status desconhecido: ${status.status}"
                                )
                            }
                        }
                    }
                }.onFailure {
                    _uiState.update {
                        it.copy(paymentStatusMessage = "Sem conexao. Tentando reconectar...")
                    }
                    delay(2000)
                    refreshStationData()
                    return@launch
                }

                delay(4000)
            }
        }
    }

    private fun activateSession(session: ActiveSession) {
        viewModelScope.launch {
            pollJob?.cancel()
            stopAutoPayment()
            stopPdvPolling()
            warningFiveShown = false
            warningOneShown = false
            preferencesRepository.saveActiveSession(session)

            _uiState.update {
                it.copy(
                    appState = AppState.SESSION_ACTIVE,
                    activeSession = session,
                    payment = null,
                    paymentStatusMessage = "Pagamento aprovado",
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
                    appState = AppState.SELECTING_TIME
                )
            }
        }
    }

    fun saveAdminConfig(newConfig: AppConfig) {
        viewModelScope.launch {
            preferencesRepository.saveConfig(newConfig)
            config = newConfig
            _uiState.update {
                it.copy(
                    stationName = newConfig.stationName,
                    unlockMode = UnlockMode.normalize(newConfig.unlockMode),
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
                it.copy(adminPinError = "PIN inválido")
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

