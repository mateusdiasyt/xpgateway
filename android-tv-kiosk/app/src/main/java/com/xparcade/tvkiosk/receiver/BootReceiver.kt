package com.xparcade.tvkiosk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xparcade.tvkiosk.app.MainActivity
import com.xparcade.tvkiosk.data.local.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val config = PreferencesRepository(context).getConfig()
                if (config.autoStartApp) {
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(launchIntent)
                }
            }
            pendingResult.finish()
        }
    }
}
