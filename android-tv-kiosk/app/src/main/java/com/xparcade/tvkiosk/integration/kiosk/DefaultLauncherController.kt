package com.xparcade.tvkiosk.integration.kiosk

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

data class LauncherSettingsResult(
    val success: Boolean,
    val message: String
)

class DefaultLauncherController(private val context: Context) {

    fun isDefaultLauncher(): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }

        val resolved = context.packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )

        return resolved?.activityInfo?.packageName == context.packageName
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

    private fun Intent.canResolve(): Boolean {
        return resolveActivity(context.packageManager) != null
    }

    private companion object {
        const val ACTION_HOME_SETTINGS_FALLBACK = "android.settings.HOME_SETTINGS"
        const val ACTION_MANAGE_DEFAULT_APPS_FALLBACK = "android.settings.MANAGE_DEFAULT_APPS_SETTINGS"
    }
}
