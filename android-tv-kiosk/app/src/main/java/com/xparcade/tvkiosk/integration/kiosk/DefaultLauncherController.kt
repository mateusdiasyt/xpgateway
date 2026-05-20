package com.xparcade.tvkiosk.integration.kiosk

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings

data class LauncherSettingsResult(
    val success: Boolean,
    val message: String
)

data class LauncherStatus(
    val isDefault: Boolean,
    val resolvedPackage: String?,
    val isPreferredActivity: Boolean,
    val homeCandidates: List<String>,
    val diagnostics: List<String>
)

class DefaultLauncherController(private val context: Context) {

    fun isDefaultLauncher(): Boolean {
        return getLauncherStatus().isDefault
    }

    fun getLauncherStatus(): LauncherStatus {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }

        val resolved = context.packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        val resolvedPackage = resolved?.activityInfo?.packageName
        val isPreferredActivity = isPreferredHomeActivity()
        val homeCandidates = context.packageManager
            .queryIntentActivities(homeIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .sorted()
        val isResolvedToThisApp = resolvedPackage == context.packageName

        val diagnostics = listOf(
            "App atual: ${context.packageName}",
            "Home resolvido: ${resolvedPackage ?: "nao informado"}",
            "Preferencia registrada para XP: ${if (isPreferredActivity) "sim" else "nao"}",
            "Candidatos HOME: ${homeCandidates.joinToString().ifBlank { "nenhum" }}",
            "Conclusao: ${if (isResolvedToThisApp) "Home abre o XP Arcade" else "Home ainda abre outro launcher"}"
        )

        return LauncherStatus(
            isDefault = isResolvedToThisApp,
            resolvedPackage = resolvedPackage,
            isPreferredActivity = isPreferredActivity,
            homeCandidates = homeCandidates,
            diagnostics = diagnostics
        )
    }

    fun openDefaultLauncherSettings(): LauncherSettingsResult {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(ACTION_HOME_SETTINGS_FALLBACK),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(ACTION_MANAGE_DEFAULT_APPS_FALLBACK),
            Intent(Settings.ACTION_APPLICATION_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        val intent = candidates.firstOrNull { it.canResolve() }
            ?: return LauncherSettingsResult(
                success = false,
                message = "Esta TV nao expos uma tela de app padrao. Abra Configuracoes > Apps > Tela inicial manualmente."
            )

        return try {
            context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            LauncherSettingsResult(
                success = true,
                message = "Tela de configuracao aberta. Escolha XP Arcade como app de Tela inicial e marque Sempre."
            )
        } catch (_: ActivityNotFoundException) {
            LauncherSettingsResult(
                success = false,
                message = "Nao foi possivel abrir a tela de launcher padrao nesta TV."
            )
        } catch (error: Throwable) {
            LauncherSettingsResult(
                success = false,
                message = "Falha ao abrir configuracao: ${error.message.orEmpty()}"
            )
        }
    }

    fun testHomeButton(): LauncherSettingsResult {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(homeIntent)
            LauncherSettingsResult(
                success = true,
                message = "Comando Home enviado. Se o XP Arcade for padrao, a tela deve continuar/voltar para o bloqueio."
            )
        } catch (error: Throwable) {
            LauncherSettingsResult(
                success = false,
                message = "Nao foi possivel testar o Home: ${error.message.orEmpty()}"
            )
        }
    }

    private fun isPreferredHomeActivity(): Boolean {
        return runCatching {
            val filters = mutableListOf<IntentFilter>()
            val activities = mutableListOf<ComponentName>()
            context.packageManager.getPreferredActivities(filters, activities, context.packageName)
            activities.any { it.packageName == context.packageName }
        }.getOrDefault(false)
    }

    private fun Intent.canResolve(): Boolean {
        return resolveActivity(context.packageManager) != null
    }

    private companion object {
        const val ACTION_HOME_SETTINGS_FALLBACK = "android.settings.HOME_SETTINGS"
        const val ACTION_MANAGE_DEFAULT_APPS_FALLBACK = "android.settings.MANAGE_DEFAULT_APPS_SETTINGS"
    }
}
