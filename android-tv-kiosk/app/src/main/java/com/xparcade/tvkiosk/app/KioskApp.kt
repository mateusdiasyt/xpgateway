package com.xparcade.tvkiosk.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.xparcade.tvkiosk.domain.state.AppState
import com.xparcade.tvkiosk.ui.screens.AdminDialog
import com.xparcade.tvkiosk.ui.screens.AppUpdateRequiredScreen
import com.xparcade.tvkiosk.ui.screens.ErrorScreen
import com.xparcade.tvkiosk.ui.screens.InitialSetupScreen
import com.xparcade.tvkiosk.ui.screens.LockScreen
import com.xparcade.tvkiosk.ui.screens.PairingRequiredScreen
import com.xparcade.tvkiosk.ui.screens.PreparationScreen
import com.xparcade.tvkiosk.ui.screens.SessionActiveScreen

@Composable
fun KioskApp(viewModel: KioskViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    when (uiState.appState) {
        AppState.PAIRING_REQUIRED -> {
            PairingRequiredScreen(
                pairingCode = uiState.pairingCode,
                pairingMessage = uiState.pairingMessage,
                isPairing = uiState.isPairing,
                onCodeChange = { viewModel.updatePairingCode(it) },
                onPair = { viewModel.submitPairingCode() },
                onCheckAgain = { viewModel.refreshStationData() },
                onConfigureDevice = { viewModel.showAdminPinPrompt() }
            )
        }

        AppState.INITIAL_SETUP -> {
            InitialSetupScreen(
                stationPresets = uiState.stationPresets,
                isDefaultLauncher = uiState.isDefaultLauncher,
                launcherStatusMessage = uiState.launcherStatusMessage,
                launcherDiagnostics = uiState.launcherDiagnostics,
                isAccessibilityGuardEnabled = uiState.isAccessibilityGuardEnabled,
                accessibilityGuardMessage = uiState.accessibilityGuardMessage,
                accessibilityGuardDiagnostics = uiState.accessibilityGuardDiagnostics,
                hdmiInputs = uiState.hdmiInputs,
                hdmiStatusMessage = uiState.hdmiStatusMessage,
                onOpenLauncherSettings = { viewModel.openDefaultLauncherSettings() },
                onRefreshLauncherStatus = { viewModel.refreshLauncherStatus() },
                onTestHomeLauncher = { viewModel.testHomeLauncher() },
                onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                onRefreshAccessibilityGuardStatus = { viewModel.refreshAccessibilityGuardStatus() },
                onRefreshHdmiInputs = { viewModel.refreshHdmiInputs() },
                onTestHdmiInput = { viewModel.testHdmiInput(it) },
                onSelectStation = { preset, hdmiSwitchEnabled, consoleInputId ->
                    viewModel.selectInitialStation(preset, hdmiSwitchEnabled, consoleInputId)
                }
            )
        }

        AppState.SESSION_PREPARING -> {
            PreparationScreen(
                stationName = uiState.stationName,
                remainingSeconds = uiState.preparationRemainingSeconds
            )
        }

        AppState.SESSION_ACTIVE,
        AppState.SESSION_WARNING,
        AppState.PAYMENT_PAID -> {
            SessionActiveScreen(
                stationName = uiState.stationName,
                warning = uiState.warningMessage,
                onConfigureDevice = { viewModel.showAdminPinPrompt() }
            )
        }

        AppState.ERROR -> {
            ErrorScreen(
                message = uiState.errorMessage ?: "Sistema temporariamente indisponível.",
                onRetry = { viewModel.retryFromError() }
            )
        }

        else -> {
            LockScreen(
                stationName = uiState.stationName,
                backendOnline = uiState.backendOnline,
                waitingMessage = uiState.paymentStatusMessage,
                lastPaymentSummary = uiState.lastPaymentSummary,
                onConfigureDevice = { viewModel.showAdminPinPrompt() }
            )
        }
    }

    if (uiState.isAdminPinPromptVisible) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissAdminPinPrompt() },
            title = { Text("Acesso administrativo") },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("Digite o PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = {
                        if (!uiState.adminPinError.isNullOrBlank()) {
                            Text(uiState.adminPinError!!)
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.submitAdminPin(pin) }) {
                    Text("Entrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAdminPinPrompt() }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (uiState.isAdminDialogVisible) {
        AdminDialog(
            currentConfig = viewModel.currentConfigSnapshot(),
            onDismiss = { viewModel.dismissAdminDialog() },
            onSave = { viewModel.saveAdminConfig(it) },
            onTestConnection = { viewModel.refreshStationData() },
            onForceUnlock = { viewModel.forceUnlockFromAdmin(it) },
            onEndSession = { viewModel.endCurrentSessionFromAdmin() },
            hdmiInputs = uiState.hdmiInputs,
            hdmiStatusMessage = uiState.hdmiStatusMessage,
            onRefreshHdmiInputs = { viewModel.refreshHdmiInputs() },
            onTestHdmiInput = { viewModel.testHdmiInput(it) },
            onReturnToKiosk = { viewModel.returnToKioskFromAdmin() },
            isDefaultLauncher = uiState.isDefaultLauncher,
            launcherStatusMessage = uiState.launcherStatusMessage,
            launcherDiagnostics = uiState.launcherDiagnostics,
            isAccessibilityGuardEnabled = uiState.isAccessibilityGuardEnabled,
            accessibilityGuardMessage = uiState.accessibilityGuardMessage,
            accessibilityGuardDiagnostics = uiState.accessibilityGuardDiagnostics,
            canDrawTimerOverlay = uiState.canDrawTimerOverlay,
            timerOverlayStatusMessage = uiState.timerOverlayStatusMessage,
            onOpenLauncherSettings = { viewModel.openDefaultLauncherSettings() },
            onRefreshLauncherStatus = { viewModel.refreshLauncherStatus() },
            onTestHomeLauncher = { viewModel.testHomeLauncher() },
            onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
            onRefreshAccessibilityGuardStatus = { viewModel.refreshAccessibilityGuardStatus() },
            onOpenTimerOverlaySettings = { viewModel.openTimerOverlaySettings() },
            onRefreshTimerOverlayStatus = { viewModel.refreshTimerOverlayStatus() }
        )
    }

    uiState.requiredAppUpdate?.let { update ->
        AppUpdateRequiredScreen(
            update = update,
            statusMessage = uiState.appUpdateStatusMessage,
            isDownloading = uiState.isDownloadingAppUpdate,
            onInstall = { viewModel.installRequiredAppUpdate() },
            onCheckAgain = { viewModel.checkForAppUpdate(force = true) }
        )
    }
}
