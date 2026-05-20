package com.xparcade.tvkiosk.integration.hdmi

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager

data class HdmiInputInfo(
    val id: String,
    val label: String
)

data class HdmiSwitchResult(
    val success: Boolean,
    val message: String
)

class HdmiInputController(private val context: Context) {

    fun listInputs(): List<HdmiInputInfo> {
        return runCatching {
            val manager = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
            manager.tvInputList
                .filter { it.isPassthroughInput }
                .map { input ->
                    HdmiInputInfo(
                        id = input.id,
                        label = input.loadLabel(context).toString().ifBlank { input.id }
                    )
                }
                .sortedBy { it.label.lowercase() }
        }.getOrElse {
            emptyList()
        }
    }

    fun openInput(inputId: String): HdmiSwitchResult {
        val normalizedInputId = inputId.trim()
        if (normalizedInputId.isBlank()) {
            return HdmiSwitchResult(
                success = false,
                message = "Nenhuma entrada HDMI foi configurada."
            )
        }

        return try {
            val uri = TvContract.buildChannelUriForPassthroughInput(normalizedInputId)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            HdmiSwitchResult(
                success = true,
                message = "Comando enviado para abrir a entrada HDMI configurada."
            )
        } catch (_: ActivityNotFoundException) {
            HdmiSwitchResult(
                success = false,
                message = "Esta TV nao encontrou um app de sistema para abrir entradas HDMI."
            )
        } catch (error: SecurityException) {
            HdmiSwitchResult(
                success = false,
                message = "A TV bloqueou o controle de HDMI para apps externos: ${error.message.orEmpty()}"
            )
        } catch (error: Throwable) {
            HdmiSwitchResult(
                success = false,
                message = "Nao foi possivel abrir a HDMI: ${error.message.orEmpty()}"
            )
        }
    }
}
