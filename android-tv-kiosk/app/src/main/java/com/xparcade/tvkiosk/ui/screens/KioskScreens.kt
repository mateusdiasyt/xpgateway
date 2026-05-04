package com.xparcade.tvkiosk.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xparcade.tvkiosk.data.local.AppConfig
import com.xparcade.tvkiosk.R
import com.xparcade.tvkiosk.domain.model.CreatePaymentResponse
import com.xparcade.tvkiosk.domain.model.PricingOption
import com.xparcade.tvkiosk.ui.components.NeonSelectableCard
import com.xparcade.tvkiosk.ui.components.QrCodePanel
import com.xparcade.tvkiosk.ui.theme.XpBlack
import com.xparcade.tvkiosk.ui.theme.XpDarkGray
import com.xparcade.tvkiosk.ui.theme.XpMagenta
import com.xparcade.tvkiosk.ui.theme.XpWhite
import com.xparcade.tvkiosk.ui.theme.XpYellow

@Composable
fun NeonBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF050505),
                        Color(0xFF0B0B0B),
                        Color(0xFF111111)
                    )
                )
            )
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        content()
    }
}

@Composable
fun LockScreen(
    stationName: String,
    options: List<PricingOption>,
    backendOnline: Boolean,
    lastPaymentSummary: String?,
    onSelect: (PricingOption) -> Unit
) {
    NeonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.xp_logo_mark),
                contentDescription = "XP Arcade Logo",
                modifier = Modifier.height(72.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "XP ARCADE & BAR",
                color = XpYellow,
                fontSize = 52.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = stationName,
                color = XpMagenta,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Escolha seu tempo de jogo",
                color = XpWhite,
                fontSize = 38.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f)) {
                items(options) { option ->
                    val line = if (option.isCustom) {
                        "${option.durationMinutes} MIN PERSONALIZADO - R$ ${"%.2f".format(option.amount)}"
                    } else {
                        "${option.label} - R$ ${"%.2f".format(option.amount)}"
                    }
                    NeonSelectableCard(
                        title = line,
                        subtitle = "Pressione OK para gerar Pix",
                        onClick = { onSelect(option) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (backendOnline) {
                    "Pagamento via Pix • Liberação automática"
                } else {
                    "Sem conexão. Chame um atendente."
                },
                color = if (backendOnline) XpWhite else XpMagenta,
                fontSize = 20.sp
            )
            if (!lastPaymentSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(lastPaymentSummary, color = Color(0xFFBBBBBB), fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun PaymentScreen(
    option: PricingOption,
    payment: CreatePaymentResponse,
    waitingMessage: String,
    onMockConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    NeonBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.xp_logo_mark),
                contentDescription = "XP Arcade Logo",
                modifier = Modifier.height(72.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("Pagamento Pix", color = XpYellow, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${option.label} - R$ ${"%.2f".format(option.amount)}",
                color = XpWhite,
                fontSize = 30.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))

            QrCodePanel(qrCodeDataUrl = payment.qrCode, modifier = Modifier.weight(1f))

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Escaneie o QR Code com o app do seu banco",
                color = XpWhite,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = payment.pixCopiaECola,
                color = XpMagenta,
                fontSize = 16.sp,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(waitingMessage, color = XpYellow, fontSize = 24.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(
                    onClick = onMockConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = XpMagenta)
                ) {
                    Text("Simular pagamento")
                }
                TextButton(onClick = onCancel) {
                    Text("Cancelar", color = XpWhite)
                }
            }
        }
    }
}

@Composable
fun SessionActiveScreen(
    stationName: String,
    remainingText: String,
    warning: String?
) {
    NeonBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TV LIBERADA", color = XpYellow, fontSize = 58.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stationName, color = XpMagenta, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Tempo restante", color = XpWhite, fontSize = 28.sp)
            Text(remainingText, color = XpWhite, fontSize = 88.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Use o console na HDMI durante este período.",
                color = Color(0xFFDDDDDD),
                fontSize = 20.sp
            )

            if (!warning.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .background(XpDarkGray, shape = MaterialTheme.shapes.medium)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(warning, color = XpMagenta, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    NeonBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Erro", color = XpMagenta, fontSize = 56.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(14.dp))
            Text(message, color = XpWhite, fontSize = 24.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(18.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = XpYellow)) {
                Text("Tentar novamente", color = XpBlack)
            }
        }
    }
}

@Composable
fun AdminDialog(
    currentConfig: AppConfig,
    onDismiss: () -> Unit,
    onSave: (AppConfig) -> Unit,
    onTestConnection: () -> Unit,
    onForceUnlock: (Int) -> Unit,
    onEndSession: () -> Unit
) {
    var stationName by remember { mutableStateOf(currentConfig.stationName) }
    var stationId by remember { mutableStateOf(currentConfig.stationId) }
    var stationToken by remember { mutableStateOf(currentConfig.stationToken) }
    var backendUrl by remember { mutableStateOf(currentConfig.backendUrl) }
    var adminPin by remember { mutableStateOf(currentConfig.adminPin) }
    var adminApiKey by remember { mutableStateOf(currentConfig.adminApiKey) }
    var autoStartApp by remember { mutableStateOf(currentConfig.autoStartApp) }
    var price20 by remember { mutableStateOf(currentConfig.price20.toString()) }
    var customEnabled by remember { mutableStateOf(currentConfig.customEnabled) }
    var customDuration by remember { mutableStateOf(currentConfig.customDurationMinutes.toString()) }
    var customPrice by remember { mutableStateOf(currentConfig.customPrice.toString()) }
    var forceMinutes by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                val merged = currentConfig.copy(
                    stationName = stationName,
                    stationId = stationId,
                    stationToken = stationToken,
                    backendUrl = backendUrl,
                    adminPin = adminPin,
                    adminApiKey = adminApiKey,
                    autoStartApp = autoStartApp,
                    price20 = price20.toDoubleOrNull() ?: currentConfig.price20,
                    customEnabled = customEnabled,
                    customDurationMinutes = customDuration.toIntOrNull() ?: currentConfig.customDurationMinutes,
                    customPrice = customPrice.toDoubleOrNull() ?: currentConfig.customPrice
                )
                onSave(merged)
            }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
        containerColor = XpDarkGray,
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Admin local", color = XpYellow, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = stationName, onValueChange = { stationName = it }, label = { Text("Nome da estação") })
                OutlinedTextField(value = stationId, onValueChange = { stationId = it }, label = { Text("Station ID") })
                OutlinedTextField(value = stationToken, onValueChange = { stationToken = it }, label = { Text("Station token") })
                OutlinedTextField(value = backendUrl, onValueChange = { backendUrl = it }, label = { Text("Backend URL") })
                OutlinedTextField(value = adminApiKey, onValueChange = { adminApiKey = it }, label = { Text("Admin API key") })
                OutlinedTextField(value = adminPin, onValueChange = { adminPin = it }, label = { Text("PIN admin") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Iniciar automático no boot", color = XpWhite)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = autoStartApp, onCheckedChange = { autoStartApp = it })
                }
                OutlinedTextField(value = price20, onValueChange = { price20 = it }, label = { Text("Pre\u00E7o 20 min") })

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tempo personalizado", color = XpWhite)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = customEnabled, onCheckedChange = { customEnabled = it })
                }
                OutlinedTextField(value = customDuration, onValueChange = { customDuration = it }, label = { Text("Duração custom (min)") })
                OutlinedTextField(value = customPrice, onValueChange = { customPrice = it }, label = { Text("Preço custom") })

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(value = forceMinutes, onValueChange = { forceMinutes = it }, label = { Text("Forçar liberação (min)") })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onTestConnection) {
                        Text("Testar conexão")
                    }
                    Button(onClick = { onForceUnlock(forceMinutes.toIntOrNull() ?: 30) }) {
                        Text("Forçar liberação")
                    }
                    TextButton(onClick = onEndSession) {
                        Text("Encerrar sessão", color = XpMagenta)
                    }
                }
            }
        }
    )
}

