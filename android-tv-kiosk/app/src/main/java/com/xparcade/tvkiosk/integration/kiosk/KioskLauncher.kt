package com.xparcade.tvkiosk.integration.kiosk

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.xparcade.tvkiosk.app.MainActivity

object KioskLauncher {

    fun bringToFront(context: Context): Boolean {
        val appContext = context.applicationContext
        val launchIntent = buildLaunchIntent(appContext)

        val movedTask = runCatching {
            appContext
                .getSystemService(ActivityManager::class.java)
                ?.appTasks
                ?.firstOrNull()
                ?.moveToFront()
            true
        }.getOrDefault(false)

        val startedDirectly = runCatching {
            appContext.startActivity(launchIntent)
            true
        }.getOrDefault(false)

        val startedFromPendingIntent = runCatching {
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                KIOSK_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            pendingIntent.send(
                appContext,
                0,
                null,
                null,
                null,
                null,
                buildBackgroundStartOptions()
            )
            true
        }.getOrDefault(false)

        return movedTask || startedDirectly || startedFromPendingIntent
    }

    fun buildLaunchIntent(context: Context): Intent {
        val packageManager = context.packageManager
        val launcherIntent = packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
            ?: packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)

        return launcherIntent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }

    private fun buildBackgroundStartOptions(): android.os.Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return null
        }

        return ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            .toBundle()
    }

    private const val KIOSK_REQUEST_CODE = 4210
}
