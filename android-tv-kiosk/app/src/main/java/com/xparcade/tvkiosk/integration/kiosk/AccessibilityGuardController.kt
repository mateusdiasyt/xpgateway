package com.xparcade.tvkiosk.integration.kiosk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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
        val candidates = listOf(
            SettingsCandidate(
                label = "Configuracoes gerais",
                intent = Intent(Settings.ACTION_SETTINGS),
                message = "Configuracoes abertas. Entre em Sistema > Acessibilidade > XP Arcade Guardiao e ative."
            ),
            SettingsCandidate(
                label = "Detalhes do app",
                intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ),
                message = "Detalhes do app abertos. Se nao houver Acessibilidade aqui, volte e procure Sistema > Acessibilidade > XP Arcade Guardiao."
            )
        )

        for (candidate in candidates) {
            val intent = candidate.intent.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val opened = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)

            if (opened) {
                return AccessibilitySettingsResult(
                    success = true,
                    message = "${candidate.message} Atalho usado: ${candidate.label}."
                )
            }
        }

        return AccessibilitySettingsResult(
            success = false,
            message = "Esta TV bloqueou os atalhos de configuracao. Abra manualmente: Configuracoes > Sistema > Acessibilidade > XP Arcade Guardiao."
        )
    }

    private data class SettingsCandidate(
        val label: String,
        val intent: Intent,
        val message: String
    )
}
