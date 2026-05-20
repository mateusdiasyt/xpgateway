package com.xparcade.tvkiosk.integration.kiosk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.xparcade.tvkiosk.service.XpAccessibilityGuardService

data class AccessibilityGuardStatus(
    val isEnabled: Boolean,
    val diagnostics: List<String>
)

data class AccessibilitySettingsResult(
    val success: Boolean,
    val message: String
)

class AccessibilityGuardController(private val context: Context) {

    fun getStatus(): AccessibilityGuardStatus {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val expectedComponent = ComponentName(context, XpAccessibilityGuardService::class.java)
        val expectedFull = expectedComponent.flattenToString()
        val expectedShort = expectedComponent.flattenToShortString()
        val serviceList = enabledServices
            .split(':')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val isEnabled = serviceList.any {
            it.equals(expectedFull, ignoreCase = true) ||
                it.equals(expectedShort, ignoreCase = true)
        }

        return AccessibilityGuardStatus(
            isEnabled = isEnabled,
            diagnostics = listOf(
                "Guardiao esperado: $expectedShort",
                "Acessibilidade ativa: ${if (isEnabled) "sim" else "nao"}",
                "Servicos ativos: ${serviceList.joinToString().ifBlank { "nenhum" }}"
            )
        )
    }

    fun openAccessibilitySettings(): AccessibilitySettingsResult {
        return runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            AccessibilitySettingsResult(
                success = true,
                message = "Tela de Acessibilidade aberta. Ative o servico XP Arcade Guardiao."
            )
        }.getOrElse {
            AccessibilitySettingsResult(
                success = false,
                message = "Nao foi possivel abrir Acessibilidade. Abra manualmente em Configuracoes > Acessibilidade."
            )
        }
    }
}
