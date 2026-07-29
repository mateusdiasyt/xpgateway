package com.xparcade.tvkiosk.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xparcade.tvkiosk.BuildConfig
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
import com.xparcade.tvkiosk.integration.kiosk.AccessibilityGuardController
import com.xparcade.tvkiosk.integration.kiosk.DefaultLauncherController
import com.xparcade.tvkiosk.integration.kiosk.KioskLauncher
import com.xparcade.tvkiosk.integration.overlay.TimerOverlayManager
import com.xparcade.tvkiosk.service.SessionGuardService
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

class KioskViewModel(application: Application) : AndroidViewModel(application) {
    private val fixedDurationMinutes = 20

    private val preferencesRepository = PreferencesRepository(application.applicationContext)
    private val backendRepository = BackendRepository()
    private val hdmiInputController = HdmiInputController(application.applicationContext)
    private val defaultLauncherController = DefaultLauncherController(application.applicationContext)
    private val accessibilityGuardController = AccessibilityGuardController(application.applicationContext)
    private val updateDownloadClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _uiState = MutableStateFlow(KioskUiState())
    val uiState: StateFlow<KioskUiState> = _uiState.asStateFlow()

    private var config: AppConfig = AppConfig()
    private var countdownJob: Job? = null
    private var preparationJob: Job? = null
    private var pdvPollJob: Job? = null
    private var activeMonitorJob: Job? = null
    private var appUpdateMonitorJob: Job? = null
    private var loadedDisplayConfigVersion = -1

    private val secretSequence = listOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_CENTER
    )

    private val keyBuffer = ArrayDeque<Int>()

    private var warningFiveShown = false
    private var warningOneShown = false

    init {
        bootstrap()
        startAppUpdateMonitor()
        refreshHdmiInputs()
        refreshLauncherStatus()
        refreshAccessibilityGuardStatus()
        refreshTimerOverlayStatus()
    }

    fun shouldBlockBack(): Boolean {
        return uiState.value.requiredAppUpdate != null || uiState.value.appState != AppState.ADMIN_MODE
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

            if (requiresPairing(config)) {
                enterPairingMode("Digite o codigo gerado no painel para vincular esta TV ao PDV.")
                return@launch
            }

            _uiState.update {
                it.copy(
                    stationName = config.stationName.ifBlank { "Mendoza PDV" },
                    stationPresets = emptyList(),
                    unlockMode = UnlockMode.PDV_ONLY,
                    paymentStatusMessage = "Aguardando liberacao pelo caixa...",
                    appState = AppState.IDLE
                )
            }

            restoreOrResetSession()
            refreshStationData()
        }
    }

    private fun requiresPairing(current: AppConfig): Boolean {
        return !current.isConfigured || current.deviceKey.isBlank() || current.stationId.isBlank()
    }

    private fun normalizeRemoteAdminPin(value: String?): String? {
        val pin = value?.filter { it.isDigit() }?.take(8).orEmpty()
        return pin.takeIf { it.length >= 4 }
    }

    private suspend fun syncAdminPin(remoteAdminPin: String?) {
        val nextPin = normalizeRemoteAdminPin(remoteAdminPin) ?: return
        if (nextPin == config.adminPin) return

        val normalized = config.copy(adminPin = nextPin)
        preferencesRepository.saveConfig(normalized)
        config = normalized
    }

    private suspend fun syncTvDisplay(remoteVersion: Int) {
        if (remoteVersion == loadedDisplayConfigVersion) return

        val display = runCatching {
            backendRepository.getTvDisplay(config)
        }.getOrNull() ?: return

        loadedDisplayConfigVersion = display.displayConfigVersion
        _uiState.update {
            it.copy(tvDisplay = display)
        }
    }

    private suspend fun enterPairingMode(message: String? = null) {
        stopPdvPolling()
        stopActiveSessionMonitor()
        stopPreparationCountdown()
        stopSessionGuard()
        preferencesRepository.clearPairing()
        config = preferencesRepository.getConfig().copy(unlockMode = UnlockMode.PDV_ONLY)
        loadedDisplayConfigVersion = -1
        _uiState.update {
            it.copy(
                isLoading = false,
                isPairing = false,
                stationName = "Mendoza PDV",
                stationPresets = emptyList(),
                pricingOptions = emptyList(),
                selectedOption = null,
                payment = null,
                activeSession = null,
                remainingSeconds = 0,
                preparationRemainingSeconds = 0,
                appState = AppState.PAIRING_REQUIRED,
                pairingMessage = message ?: "Pareamento obrigatorio. Gere um codigo no painel e digite aqui.",
                paymentStatusMessage = "Pareamento obrigatorio.",
                tvDisplay = com.xparcade.tvkiosk.domain.model.TvDisplaySnapshotResponse()
            )
        }
    }

    private fun startAppUpdateMonitor() {
        if (appUpdateMonitorJob?.isActive == true) return

        appUpdateMonitorJob = viewModelScope.launch {
            delay(1500)
            while (true) {
                checkForAppUpdate(force = false)
                delay(60_000)
            }
        }
    }

    fun checkForAppUpdate(force: Boolean) {
        viewModelScope.launch {
            val latest = runCatching { backendRepository.getLatestAppUpdate(config) }.getOrNull()

            if (latest == null) {
                if (force) {
                    _uiState.update { it.copy(appUpdateStatusMessage = "Nao foi possivel verificar agora.") }
                }
                return@launch
            }

            if (latest.versionCode > BuildConfig.VERSION_CODE && latest.required) {
                bringKioskToFront()
                _uiState.update {
                    it.copy(
                        requiredAppUpdate = latest,
                        appUpdateStatusMessage = "Nova versao disponivel: ${latest.versionName}.",
                        isDownloadingAppUpdate = false
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    requiredAppUpdate = null,
                    appUpdateStatusMessage = if (force) "App ja esta atualizado." else it.appUpdateStatusMessage,
                    isDownloadingAppUpdate = false
                )
            }
        }
    }

    fun installRequiredAppUpdate() {
        val update = uiState.value.requiredAppUpdate ?: return

        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                _uiState.update {
                    it.copy(appUpdateStatusMessage = "Permita instalar apps desconhecidos e volte para atualizar.")
                }
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(settingsIntent) }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isDownloadingAppUpdate = true,
                    appUpdateStatusMessage = "Baixando APK..."
                )
            }

            val apkFile = runCatching { downloadUpdateApk(update.apkUrl) }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isDownloadingAppUpdate = false,
                        appUpdateStatusMessage = "Falha ao baixar: ${error.message ?: error::class.java.simpleName}"
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isDownloadingAppUpdate = false,
                    appUpdateStatusMessage = "Abrindo instalador..."
                )
            }

            runCatching { openApkInstaller(apkFile) }.onFailure { error ->
                _uiState.update {
                    it.copy(appUpdateStatusMessage = "Falha ao abrir instalador: ${error.message ?: error::class.java.simpleName}")
                }
            }
        }
    }

    private suspend fun downloadUpdateApk(apkUrl: String): File = withContext(Dispatchers.IO) {
        val context = getApplication<Application>().applicationContext
        val updateDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val target = File(updateDir, "xp-tv-update.apk")
        val request = Request.Builder().url(apkUrl).build()
        val response = updateDownloadClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code}")
        }

        val body = response.body ?: throw IllegalStateException("Arquivo vazio")
        target.outputStream().use { output ->
            body.byteStream().use { input -> input.copyTo(output) }
        }
        target
    }

    private fun openApkInstaller(apkFile: File) {
        val context = getApplication<Application>().applicationContext
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun selectInitialStation(preset: StationPreset, hdmiSwitchEnabled: Boolean, consoleInputId: String) {
        viewModelScope.launch {
            val normalized = config.copy(
                isConfigured = true,
                stationId = preset.stationId,
                stationName = preset.stationName,
                unlockMode = UnlockMode.PDV_ONLY,
                hdmiSwitchEnabled = hdmiSwitchEnabled && consoleInputId.isNotBlank(),
                consoleInputId = consoleInputId.trim()
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

    fun updatePairingCode(value: String) {
        val onlyDigits = value.filter { it.isDigit() }.take(8)
        _uiState.update { it.copy(pairingCode = onlyDigits, pairingMessage = null) }
    }

    fun submitPairingCode() {
        viewModelScope.launch {
            val code = uiState.value.pairingCode.filter { it.isDigit() }
            if (code.length < 4) {
                _uiState.update { it.copy(pairingMessage = "Digite o codigo de pareamento completo.") }
                return@launch
            }

            _uiState.update { it.copy(isPairing = true, pairingMessage = "Pareando TV...") }
            runCatching { backendRepository.pairTvDevice(config, code, null) }
                .onSuccess { paired ->
                    val deviceToken = paired.deviceToken.orEmpty()
                    val stationId = paired.stationId.orEmpty()
                    if (deviceToken.isBlank() || stationId.isBlank()) {
                        _uiState.update {
                            it.copy(
                                isPairing = false,
                                pairingMessage = "O codigo foi aceito, mas o servidor nao retornou o token da TV. Gere outro codigo."
                            )
                        }
                        return@onSuccess
                    }

                    val normalized = config.copy(
                        isConfigured = true,
                        stationId = stationId,
                        stationName = paired.label ?: stationId,
                        stationToken = "",
                        deviceKey = deviceToken,
                        adminPin = normalizeRemoteAdminPin(paired.adminPin) ?: config.adminPin,
                        unlockMode = UnlockMode.PDV_ONLY
                    )
                    preferencesRepository.saveConfig(normalized)
                    config = normalized
                    _uiState.update {
                        it.copy(
                            isPairing = false,
                            pairingCode = "",
                            pairingMessage = "TV pareada com sucesso. Carregando controle...",
                            stationName = normalized.stationName,
                            appState = AppState.IDLE
                        )
                    }
                    restoreOrResetSession()
                    refreshStationData()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isPairing = false,
                            pairingMessage = error.message ?: "Nao foi possivel parear. Confira o codigo e tente novamente."
                        )
                    }
                }
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

            if (requiresPairing(config)) {
                enterPairingMode("Esta TV precisa ser pareada com este PDV antes de continuar.")
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
            refreshLauncherStatus()
            refreshAccessibilityGuardStatus()
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
        if (requiresPairing(config)) {
            viewModelScope.launch { enterPairingMode("Esta TV precisa ser pareada com este PDV antes de continuar.") }
            return
        }
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
                try {
                    val tvStatus = backendRepository.getTvStatus(config)
                    syncAdminPin(tvStatus.adminPin)
                    syncTvDisplay(tvStatus.displayConfigVersion)

                    if (tvStatus.requiresPairing || tvStatus.status.equals("PAIRING_REQUIRED", true)) {
                        enterPairingMode(tvStatus.message ?: "Esta TV precisa ser pareada novamente.")
                        return@launch
                    }

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
                                it.copy(paymentStatusMessage = "PDV retornou sem tempo restante. Aguardando nova liberacao...")
                            }
                        }
                    } else {
                        val active = uiState.value.activeSession
                        if (active != null && active.source == "pdv") {
                            handleRemoteSessionEnded("Tempo encerrado pelo caixa.")
                        }
                        _uiState.update {
                            it.copy(paymentStatusMessage = "Aguardando liberacao pelo caixa...")
                        }
                    }
                } catch (error: Throwable) {
                    val httpError = error as? HttpException
                    if (httpError?.code() == 401 || httpError?.code() == 403) {
                        enterPairingMode("Esta TV precisa ser pareada novamente com o PDV.")
                        return@launch
                    }
                    _uiState.update {
                        it.copy(paymentStatusMessage = "Sem conexao com o PDV. Tentando novamente...")
                    }
                }

                delay(5_000)
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
            if (session.source == "pdv" || session.source.startsWith("manual")) {
                startSessionGuard(session)
                if (session.source != "manual-local") {
                    startActiveSessionMonitor(session)
                }
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
                    syncAdminPin(tvStatus.adminPin)

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

    fun refreshLauncherStatus() {
        val status = defaultLauncherController.getLauncherStatus()
        _uiState.update {
            it.copy(
                isDefaultLauncher = status.isDefault,
                launcherStatusMessage = if (status.isDefault) {
                    "Home confirmado: o botao Home abre o XP Arcade."
                } else {
                    "A TV ainda reporta Home como ${status.resolvedPackage ?: "nao definido"}. O XP pode estar marcado, mas o Google TV continua interceptando o Home."
                },
                launcherDiagnostics = status.diagnostics
            )
        }
    }

    fun refreshAccessibilityGuardStatus() {
        val status = accessibilityGuardController.getStatus()
        _uiState.update {
            it.copy(
                isAccessibilityGuardEnabled = status.isEnabled,
                accessibilityGuardMessage = if (status.isEnabled) {
                    "Guardiao ativo: quando a TV estiver bloqueada, o XP tenta voltar sozinho."
                } else {
                    "Guardiao desligado: ative a Acessibilidade para reforcar o bloqueio."
                },
                accessibilityGuardDiagnostics = status.diagnostics
            )
        }
    }

    fun refreshTimerOverlayStatus() {
        val context = getApplication<Application>().applicationContext
        val canDraw = TimerOverlayManager.canDrawOverlays(context)
        _uiState.update {
            it.copy(
                canDrawTimerOverlay = canDraw,
                timerOverlayStatusMessage = if (canDraw) {
                    "Tempo sobre o jogo liberado."
                } else {
                    "Permita sobrepor a outros apps para exibir o tempo no jogo."
                }
            )
        }
    }

    fun openTimerOverlaySettings() {
        val context = getApplication<Application>().applicationContext
        runCatching {
            context.startActivity(TimerOverlayManager.buildSettingsIntent(context))
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    timerOverlayStatusMessage = "Nao foi possivel abrir a permissao: ${error.message ?: error::class.java.simpleName}"
                )
            }
        }
    }

    fun openDefaultLauncherSettings() {
        val result = defaultLauncherController.openDefaultLauncherSettings()
        _uiState.update {
            it.copy(
                launcherStatusMessage = result.message,
                launcherDiagnostics = it.launcherDiagnostics + "Acao: abrir configuracao de launcher."
            )
        }
    }

    fun openAccessibilitySettings() {
        viewModelScope.launch {
            preferencesRepository.allowGuardianSetupFor(GUARDIAN_SETUP_GRACE_MS)
            val result = accessibilityGuardController.openAccessibilitySettings()
            val status = accessibilityGuardController.getStatus()

            _uiState.update {
                it.copy(
                    accessibilityGuardMessage = result.message,
                    isAccessibilityGuardEnabled = status.isEnabled,
                    accessibilityGuardDiagnostics = status.diagnostics + "Acao: abrir tela de Acessibilidade."
                )
            }
        }
    }

    fun testHomeLauncher() {
        val result = defaultLauncherController.testHomeButton()
        _uiState.update {
            it.copy(
                launcherStatusMessage = result.message,
                launcherDiagnostics = it.launcherDiagnostics + "Acao: testar comando Home pelo APK."
            )
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
                    isAdminPinPromptVisible = false,
                    adminPinError = null,
                    isAdminDialogVisible = true,
                    appState = AppState.ADMIN_MODE
                )
            }
            refreshHdmiInputs()
            refreshLauncherStatus()
            refreshAccessibilityGuardStatus()
        }
    }

    fun showAdminPinPrompt() {
        _uiState.update {
            it.copy(
                isAdminPinPromptVisible = true,
                adminPinError = null
            )
        }
    }

    fun submitAdminPin(pin: String) {
        if (pin.filter { it.isDigit() } == config.adminPin) {
            _uiState.update {
                it.copy(
                    isAdminPinPromptVisible = false,
                    adminPinError = null,
                    isAdminDialogVisible = true,
                    appState = AppState.ADMIN_MODE
                )
            }
            refreshHdmiInputs()
            refreshLauncherStatus()
            refreshAccessibilityGuardStatus()
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
        KioskLauncher.bringToFront(context)
    }

    private companion object {
        private const val GUARDIAN_SETUP_GRACE_MS = 600_000L
    }
}
