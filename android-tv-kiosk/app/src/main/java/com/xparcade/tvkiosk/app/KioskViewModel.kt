package com.xparcade.tvkiosk.app

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xparcade.tvkiosk.data.local.AppConfig
import com.xparcade.tvkiosk.data.local.PreferencesRepository
import com.xparcade.tvkiosk.data.local.StationPreset
import com.xparcade.tvkiosk.data.local.StationPresets
import com.xparcade.tvkiosk.data.local.UnlockMode
import com.xparcade.tvkiosk.data.repository.BackendRepository
import com.xparcade.tvkiosk.domain.model.ActiveSession
import com.xparcade.tvkiosk.domain.model.PricingOption
import com.xparcade.tvkiosk.domain.state.AppState
import com.xparcade.tvkiosk.domain.state.KioskUiState
import com.xparcade.tvkiosk.integration.hdmi.HdmiInputController
import com.xparcade.tvkiosk.service.SessionGuardService
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
    private val hdmiInputController = HdmiInputController(application.applicationContext)

    private val _uiState = MutableStateFlow(KioskUiState())
    val uiState: StateFlow<KioskUiState> = _uiState.asStateFlow()

    private var config: AppConfig = AppConfig()
    private var countdownJob: Job? = null
    private var preparationJob: Job? = null
    private var pdvPollJob: Job? = null
    private var activeMonitorJob: Job? = null

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

            if (!config.isConfigured) {
                _uiState.update {
                    it.copy(
                        stationName = "XP Arcade",
                        stationPresets = StationPresets.all,
                        unlockMode = UnlockMode.PDV_ONLY,
                        paymentStatusMessage = "Selecione a TV para iniciar.",
                        appState = AppState.INITIAL_SETUP
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    stationName = config.stationName,
                    stationPresets = StationPresets.all,
                    unlockMode = UnlockMode.PDV_ONLY,
                    paymentStatusMessage = "Aguardando liberacao pelo caixa...",
                    appState = AppState.IDLE
                )
            }

            restoreOrResetSession()
            refreshStationData()
        }
    }

    fun selectInitialStation(preset: StationPreset) {
        viewModelScope.launch {
            val normalized = config.copy(
                isConfigured = true,
                stationId = preset.stationId,
                stationName = preset.stationName,
                unlockMode = UnlockMode.PDV_ONLY
            )
            preferencesRepository.saveConfig(normalized)
            config = normalized
            _uiState.update {
                it.copy(
                    stationName = normalized.stationName,
                    stationPresets = StationPresets.all,
                    appState = AppState.IDLE,
                    paymentStatusMessage = "Aguardando liberacao pelo caixa..."
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

            if (!config.isConfigured) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        stationName = "XP Arcade",
                        stationPresets = StationPresets.all,
                        appState = AppState.INITIAL_SETUP,
                        paymentStatusMessage = "Selecione a TV para iniciar."
                    )
                }
                return@launch
            }

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

            refreshHdmiInputs()
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

    private fun stopActiveSessionMonitor() {
        activeMonitorJob?.cancel()
        activeMonitorJob = null
    }

    private fun stopPreparationCountdown() {
        preparationJob?.cancel()
        preparationJob = null
    }

    private fun startPdvPolling() {
        if (pdvPollJob?.isActive == true) return

        pdvPollJob = viewModelScope.launch {
            while (true) {
                val result = runCatching { backendRepository.getTvStatus(config) }

                result.onSuccess { tvStatus ->
                    val isPreparingStatus = tvStatus.status.equals("PREPARING", true)
                    val isActiveStatus =
                        tvStatus.status.equals("ACTIVE", true) ||
                            tvStatus.status.equals("UNLOCKED", true) ||
                            tvStatus.status.equals("RELEASED", true)

                    if (isPreparingStatus) {
                        val localNow = System.currentTimeMillis()
                        val preparationRemainingMillis = tvStatus.preparationRemainingSeconds
                            ?.coerceAtLeast(0)
                            ?.times(1000L)
                        val serviceStartsAt = preparationRemainingMillis?.let { localNow + it }
                            ?: parseIsoToMillis(tvStatus.serviceStartsAt)
                            ?: parseIsoToMillis(tvStatus.preparationEndsAt)
                            ?: (localNow + tvStatus.preparationRemainingSeconds.orZero().coerceAtLeast(0) * 1000L)
                        val serviceDurationSeconds = tvStatus.remainingSeconds.coerceAtLeast(60)
                        val unlockedUntil = serviceStartsAt + serviceDurationSeconds * 1000L
                        val serverUnlockedUntil = parseIsoToMillis(tvStatus.unlockedUntil)
                            ?: parseIsoToMillis(tvStatus.releasedUntil)

                        if (serverUnlockedUntil != null && serviceStartsAt > localNow) {
                            startPreparationCountdown(
                                sessionId = tvStatus.saleId ?: "pdv-${System.currentTimeMillis()}",
                                startsAtEpochMillis = serviceStartsAt,
                                expiresAtEpochMillis = unlockedUntil,
                                durationMinutes = ((serviceDurationSeconds + 59) / 60).toInt().coerceAtLeast(1)
                            )
                            return@launch
                        }
                    } else if (isActiveStatus) {
                        val remaining = tvStatus.remainingSeconds.coerceAtLeast(0)
                        val expiresAt = System.currentTimeMillis() + remaining * 1000L
                        val durationMinutes = ((remaining + 59) / 60).toInt().coerceAtLeast(1)

                        if (remaining > 0) {
                            val active = ActiveSession(
                                sessionId = tvStatus.saleId ?: "pdv-${System.currentTimeMillis()}",
                                expiresAtEpochMillis = expiresAt,
                                durationMinutes = durationMinutes,
                                source = "pdv"
                            )
                            activateSession(active)
                            return@launch
                        } else {
                            _uiState.update {
                                it.copy(paymentStatusMessage = "PDV retornou ACTIVE, mas o tempo ja esta zerado.")
                            }
                        }
                    } else {
                        _uiState.update {
                            it.copy(paymentStatusMessage = "Status PDV: ${tvStatus.status}. Aguardando liberacao pelo caixa...")
                        }
                    }
                }.onFailure {
                    val message = it.message?.takeIf { text -> text.isNotBlank() } ?: it::class.java.simpleName
                    _uiState.update {
                        it.copy(paymentStatusMessage = "Falha ao consultar PDV: $message. Chame um atendente.")
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
            stopPreparationCountdown()
            stopActiveSessionMonitor()
            warningFiveShown = false
            warningOneShown = false
            preferencesRepository.saveActiveSession(session)

            _uiState.update {
                it.copy(
                    appState = AppState.SESSION_ACTIVE,
                    activeSession = session,
                    preparationRemainingSeconds = 0,
                    payment = null,
                    paymentStatusMessage = "Sessao ativa via caixa",
                    warningMessage = null
                )
            }

            startCountdown(session)
            if (session.source == "pdv" || session.source == "manual") {
                startSessionGuard(session)
                startActiveSessionMonitor(session)
            }
        }
    }

    fun openConsoleInputForActiveSession() {
        val currentConfig = config
        if (!currentConfig.hdmiSwitchEnabled) {
            _uiState.update {
                it.copy(hdmiStatusMessage = "Troca automatica de HDMI desativada.")
            }
            return
        }

        val result = hdmiInputController.openInput(currentConfig.consoleInputId)
        _uiState.update {
            it.copy(hdmiStatusMessage = result.message)
        }
    }

    private fun startSessionGuard(session: ActiveSession) {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, SessionGuardService::class.java).apply {
            putExtra(SessionGuardService.EXTRA_EXPIRES_AT, session.expiresAtEpochMillis)
            putExtra(SessionGuardService.EXTRA_BACKEND_URL, config.backendUrl)
            putExtra(SessionGuardService.EXTRA_STATION_ID, config.stationId)
            putExtra(SessionGuardService.EXTRA_STATION_NAME, config.stationName)
            putExtra(SessionGuardService.EXTRA_STATION_TOKEN, config.stationToken)
            putExtra(SessionGuardService.EXTRA_DEVICE_KEY, config.deviceKey)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopSessionGuard() {
        val context = getApplication<Application>().applicationContext
        context.stopService(Intent(context, SessionGuardService::class.java))
    }

    private fun startActiveSessionMonitor(session: ActiveSession) {
        activeMonitorJob?.cancel()
        activeMonitorJob = viewModelScope.launch {
            delay(5000)

            while (true) {
                val result = runCatching { backendRepository.getTvStatus(config) }

                result.onSuccess { tvStatus ->
                    val isPreparingStatus = tvStatus.status.equals("PREPARING", true)
                    val isActiveStatus =
                        tvStatus.status.equals("ACTIVE", true) ||
                            tvStatus.status.equals("UNLOCKED", true) ||
                            tvStatus.status.equals("RELEASED", true)

                    if (!isPreparingStatus && !isActiveStatus) {
                        handleRemoteSessionEnded("Tempo encerrado pelo caixa.")
                        return@launch
                    }

                    if (isActiveStatus && tvStatus.remainingSeconds <= 0) {
                        handleRemoteSessionEnded("Tempo encerrado pelo caixa.")
                        return@launch
                    }

                    val remoteSessionId = tvStatus.saleId
                    if (!remoteSessionId.isNullOrBlank() && remoteSessionId != session.sessionId) {
                        val remoteExpiresAt = parseIsoToMillis(tvStatus.unlockedUntil)
                            ?: parseIsoToMillis(tvStatus.releasedUntil)
                            ?: (System.currentTimeMillis() + tvStatus.remainingSeconds.coerceAtLeast(1) * 1000L)

                        activateSession(
                            ActiveSession(
                                sessionId = remoteSessionId,
                                expiresAtEpochMillis = remoteExpiresAt,
                                durationMinutes = ((tvStatus.remainingSeconds.coerceAtLeast(1) + 59) / 60).toInt().coerceAtLeast(1),
                                source = "pdv"
                            )
                        )
                        return@launch
                    }
                }

                delay(5000)
            }
        }
    }

    private suspend fun handleRemoteSessionEnded(message: String) {
        activeMonitorJob = null
        stopSessionGuard()
        preferencesRepository.clearActiveSession()
        countdownJob?.cancel()
        stopPreparationCountdown()
        bringKioskToFront()
        _uiState.update {
            it.copy(
                activeSession = null,
                remainingSeconds = 0,
                preparationRemainingSeconds = 0,
                warningMessage = null,
                appState = AppState.SELECTING_TIME,
                paymentStatusMessage = message
            )
        }
        ensureUnlockFlow()
        _uiState.update { it.copy(paymentStatusMessage = message) }
    }

    private fun startPreparationCountdown(
        sessionId: String,
        startsAtEpochMillis: Long,
        expiresAtEpochMillis: Long,
        durationMinutes: Int
    ) {
        if (uiState.value.appState == AppState.SESSION_PREPARING && preparationJob?.isActive == true) {
            return
        }

        preparationJob?.cancel()
        preparationJob = viewModelScope.launch {
            stopPdvPolling()

            while (true) {
                val remaining = ((startsAtEpochMillis - System.currentTimeMillis() + 999L) / 1000L).coerceAtLeast(0)

                _uiState.update {
                    it.copy(
                        appState = AppState.SESSION_PREPARING,
                        preparationRemainingSeconds = remaining,
                        activeSession = null,
                        paymentStatusMessage = "Prepare-se para jogar..."
                    )
                }

                if (remaining <= 0) {
                    activateSession(
                        ActiveSession(
                            sessionId = sessionId,
                            expiresAtEpochMillis = expiresAtEpochMillis,
                            durationMinutes = durationMinutes,
                            source = "pdv"
                        )
                    )
                    return@launch
                }

                delay(1000)
            }
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
                    stopSessionGuard()
                    stopActiveSessionMonitor()
                    bringKioskToFront()
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
            stopSessionGuard()
            countdownJob?.cancel()
            stopPreparationCountdown()
            stopActiveSessionMonitor()
            _uiState.update {
                it.copy(
                    activeSession = null,
                    remainingSeconds = 0,
                    preparationRemainingSeconds = 0,
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
            val preset = StationPresets.find(newConfig.stationId)
            val normalized = newConfig.copy(
                isConfigured = true,
                stationId = newConfig.stationId.trim().lowercase(),
                stationName = preset?.stationName ?: newConfig.stationName,
                unlockMode = UnlockMode.PDV_ONLY
            )
            preferencesRepository.saveConfig(normalized)
            config = normalized
            _uiState.update {
                it.copy(
                    stationName = normalized.stationName,
                    stationPresets = StationPresets.all,
                    unlockMode = UnlockMode.PDV_ONLY,
                    isAdminDialogVisible = false,
                    appState = if (it.activeSession == null) AppState.SELECTING_TIME else it.appState
                )
            }
            refreshStationData()
        }
    }

    fun refreshHdmiInputs() {
        viewModelScope.launch {
            val inputs = hdmiInputController.listInputs()
            _uiState.update {
                it.copy(
                    hdmiInputs = inputs,
                    hdmiStatusMessage = if (inputs.isEmpty()) {
                        "Nenhuma entrada HDMI foi exposta pelo sistema da TV."
                    } else {
                        "${inputs.size} entrada(s) HDMI detectada(s)."
                    }
                )
            }
        }
    }

    fun testHdmiInput(inputId: String) {
        viewModelScope.launch {
            val result = hdmiInputController.openInput(inputId)
            _uiState.update {
                it.copy(
                    hdmiStatusMessage = if (result.success) {
                        "${result.message} O teste volta ao bloqueio em 5 segundos."
                    } else {
                        result.message
                    }
                )
            }
            if (result.success) {
                delay(5000)
                bringKioskToFront()
            }
        }
    }

    fun returnToKioskFromAdmin() {
        bringKioskToFront()
        _uiState.update {
            it.copy(hdmiStatusMessage = "Comando enviado para voltar ao bloqueio do XP Arcade.")
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
            refreshHdmiInputs()
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
                appState = when {
                    !config.isConfigured -> AppState.INITIAL_SETUP
                    it.activeSession == null -> AppState.SELECTING_TIME
                    else -> AppState.SESSION_ACTIVE
                }
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

    private fun Long?.orZero(): Long = this ?: 0L

    private fun bringKioskToFront() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        runCatching {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            activityManager?.appTasks?.firstOrNull()?.moveToFront()
        }

        runCatching {
            context.startActivity(intent)
        }
    }
}
