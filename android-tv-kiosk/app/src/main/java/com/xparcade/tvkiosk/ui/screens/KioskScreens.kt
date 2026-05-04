package com.xparcade.tvkiosk.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.xparcade.tvkiosk.R
import com.xparcade.tvkiosk.data.local.AppConfig
import com.xparcade.tvkiosk.domain.model.CreatePaymentResponse
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
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF040404),
                        Color(0xFF090909),
                        Color(0xFF101010)
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
    durationMinutes: Int,
    amount: Double,
    backendOnline: Boolean,
    waitingMessage: String,
    lastPaymentSummary: String?
) {
    NeonBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.xp_logo_transparent),
                contentDescription = "XP Arcade Logo",
                modifier = Modifier
                    .fillMaxWidth(0.56f)
                    .height(124.dp)
            )
            Text(
                text = stationName,
                color = XpMagenta,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(30.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .background(Color(0x99111111), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0x66FF005C), RoundedCornerShape(20.dp))
                    .padding(horizontal = 26.dp, vertical = 20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("LIBERACAO AUTOMATICA", color = XpWhite, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("$durationMinutes MIN", color = XpYellow, fontSize = 48.sp, fontWeight = FontWeight.Black)
                    Text("R$ ${"%.2f".format(amount)}", color = XpWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(18.dp))
                    CircularProgressIndicator(color = XpMagenta, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(waitingMessage, color = XpWhite, fontSize = 18.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (backendOnline) "Preparando QR Pix..." else "Sem conexao. Tentando reconectar.",
                        color = if (backendOnline) Color(0xFFBBBBBB) else XpMagenta,
                        fontSize = 14.sp
                    )
                }
            }

            if (!lastPaymentSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(lastPaymentSummary, color = Color(0xFF9B9B9B), fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun PaymentScreen(
    stationName: String,
    payment: CreatePaymentResponse,
    waitingMessage: String
) {
    NeonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Image(
                        painter = painterResource(id = R.drawable.xp_logo_transparent),
                        contentDescription = "XP Arcade Logo",
                        modifier = Modifier.height(72.dp)
                    )
                    Text(stationName, color = XpMagenta, fontSize = 16.sp)
                }
                Text("PIX", color = XpWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(0.46f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Escaneie para liberar", color = XpWhite, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${payment.durationMinutes} min • R$ ${"%.2f".format(payment.amount)}",
                        color = XpYellow,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text("Pagamento aprovado libera automaticamente.", color = Color(0xFFB8B8B8), fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xA8111111), RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0x55FFD000), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(waitingMessage, color = XpWhite, fontSize = 15.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(0.54f)
                        .background(Color(0xCC0D0D0D), RoundedCornerShape(22.dp))
                        .border(2.dp, XpMagenta, RoundedCornerShape(22.dp))
                        .padding(16.dp)
                ) {
                    QrCodePanel(qrCodeDataUrl = payment.qrCode, modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = payment.pixCopiaECola.take(90) + if (payment.pixCopiaECola.length > 90) "..." else "",
                color = Color(0xFFA0A0A0),
                fontSize = 13.sp,
                maxLines = 1
            )
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
                "Use o console na HDMI durante este periodo.",
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

                OutlinedTextField(value = stationName, onValueChange = { stationName = it }, label = { Text("Nome da estacao") })
                OutlinedTextField(value = stationId, onValueChange = { stationId = it }, label = { Text("Station ID") })
                OutlinedTextField(value = stationToken, onValueChange = { stationToken = it }, label = { Text("Station token") })
                OutlinedTextField(value = backendUrl, onValueChange = { backendUrl = it }, label = { Text("Backend URL") })
                OutlinedTextField(value = adminApiKey, onValueChange = { adminApiKey = it }, label = { Text("Admin API key") })
                OutlinedTextField(value = adminPin, onValueChange = { adminPin = it }, label = { Text("PIN admin") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Iniciar automatico no boot", color = XpWhite)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = autoStartApp, onCheckedChange = { autoStartApp = it })
                }
                OutlinedTextField(value = price20, onValueChange = { price20 = it }, label = { Text("Preco 20 min") })

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tempo personalizado", color = XpWhite)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = customEnabled, onCheckedChange = { customEnabled = it })
                }
                OutlinedTextField(value = customDuration, onValueChange = { customDuration = it }, label = { Text("Duracao custom (min)") })
                OutlinedTextField(value = customPrice, onValueChange = { customPrice = it }, label = { Text("Preco custom") })

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(value = forceMinutes, onValueChange = { forceMinutes = it }, label = { Text("Forcar liberacao (min)") })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onTestConnection) {
                        Text("Testar conexao")
                    }
                    Button(onClick = { onForceUnlock(forceMinutes.toIntOrNull() ?: 30) }) {
                        Text("Forcar liberacao")
                    }
                    TextButton(onClick = onEndSession) {
                        Text("Encerrar sessao", color = XpMagenta)
                    }
                }
            }
        }
    )
}

