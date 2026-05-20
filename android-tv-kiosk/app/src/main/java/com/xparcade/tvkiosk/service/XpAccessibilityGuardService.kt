package com.xparcade.tvkiosk.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.xparcade.tvkiosk.data.local.AppConfig
import com.xparcade.tvkiosk.data.local.PreferencesRepository
import com.xparcade.tvkiosk.data.repository.BackendRepository
import com.xparcade.tvkiosk.integration.kiosk.KioskLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class XpAccessibilityGuardService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var preferencesRepository: PreferencesRepository
    private val backendRepository = BackendRepository()

    @Volatile
    private var foregroundPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = PreferencesRepository(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            monitorKioskLock()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString()
        if (!packageName.isNullOrBlank()) {
            foregroundPackage = packageName
        }

        scope.launch {
            if (shouldBringKioskToFront()) {
                bringKioskToFront()
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun monitorKioskLock() {
        while (true) {
            if (shouldBringKioskToFront()) {
                bringKioskToFront()
            }
            delay(GUARD_TICK_MS)
        }
    }

    private suspend fun shouldBringKioskToFront(): Boolean {
        val currentPackage = foregroundPackage
        if (currentPackage == packageName) {
            return false
        }

        if (isSetupGraceActive()) {
            return false
        }

        val config = preferencesRepository.getConfig()
        val activeSession = preferencesRepository.getActiveSession()
        val now = System.currentTimeMillis()

        if (!config.isConfigured) {
            return true
        }

        if (activeSession == null) {
            return !hasRemoteGameplayActive(config)
        }

        if (activeSession.expiresAtEpochMillis <= now) {
            preferencesRepository.clearActiveSession()
            return true
        }

        val remoteActive = queryRemoteGameplayActive(config)
        if (remoteActive == false) {
            preferencesRepository.clearActiveSession()
            return true
        }

        return false
    }

    private suspend fun hasRemoteGameplayActive(config: AppConfig): Boolean {
        return queryRemoteGameplayActive(config) == true
    }

    private suspend fun queryRemoteGameplayActive(config: AppConfig): Boolean? {
        return runCatching {
            val status = backendRepository.getTvStatus(config)
            val isPreparing = status.status.equals("PREPARING", ignoreCase = true)
            val isActive = status.status.equals("ACTIVE", ignoreCase = true) ||
                status.status.equals("UNLOCKED", ignoreCase = true) ||
                status.status.equals("RELEASED", ignoreCase = true)

            isPreparing || (isActive && status.remainingSeconds > 0)
        }.getOrNull()
    }

    private suspend fun isSetupGraceActive(): Boolean {
        return preferencesRepository.getGuardianSetupGraceUntil() > System.currentTimeMillis()
    }

    private fun bringKioskToFront() {
        KioskLauncher.bringToFront(applicationContext)
    }

    companion object {
        private const val GUARD_TICK_MS = 2500L
    }
}
