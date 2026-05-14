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
import androidx.compose.ui.geometry.Offset
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
import com.xparcade.tvkiosk.data.local.UnlockMode
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
                        Color(0xFF050505),
                        Color(0xFF0A0D14),
                        Color(0xFF050505)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x66FF005C), Color.Transparent),
                        center = Offset(1480f, 170f),
                        radius = 980f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x40FFD000), Color.Transparent),
                        center = Offset(220f, 1020f),
                        radius = 900f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 28.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun StationBadge(stationName: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0x44FF005C), Color(0x33FFD000))
                ),
                shape = RoundedCornerShape(999.dp)
            )
            .border(1.dp, Color(0x99FF005C), RoundedCornerShape(999.dp))
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text = stationName.uppercase(),
            color = XpWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
private fun HeroPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xDD0B0F17), Color(0xCC0A0A0A))
                ),
                shape = RoundedCornerShape(28.dp)
            )
            .border(1.dp, Color(0x66FF005C), RoundedCornerShape(28.dp))
            .padding(horizontal = 34.dp, vertical = 28.dp)
    ) {
        content()
    }
}

@Composable
fun LockScreen(
    stationName: String,
    durationMinutes: Int,
    amount: Double,
    unlockMode: String,
    backendOnline: Boolean,
    waitingMessage: String,
    lastPaymentSummary: String?
) {
    val waitingAlreadyMentionsOffline = waitingMessage.contains("Sem conexao", ignoreCase = true)
    val modeTag = when (UnlockMode.normalize(unlockMode)) {
        UnlockMode.PDV_ONLY -> "OPERACAO VIA PDV"
        UnlockMode.HYBRID -> "PDV + PIX"
        else -> "PIX AUTOMATICO"
    }
    val modeHeadline = when (UnlockMode.normalize(unlockMode)) {
        UnlockMode.PDV_ONLY -> "Venda no caixa libera automaticamente"
        UnlockMode.HYBRID -> "Pague na TV ou no caixa"
        else -> "Escaneie e jogue sem atendente"
    }

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
                    .fillMaxWidth(0.48f)
                    .height(118.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            StationBadge(stationName = stationName)

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = modeTag,
                color = Color(0xFFC8CBD3),
                fontSize = 15.sp,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = modeHeadline,
                color = XpWhite,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            HeroPanel(modifier = Modifier.fillMaxWidth(0.76f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "PLANO EXPRESS",
                        color = Color(0xFF9EA5B2),
                        fontSize = 14.sp,
                        letterSpacing = 2.2.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("$durationMinutes MIN", color = XpYellow, fontSize = 68.sp, fontWeight = FontWeight.Black)
                    Text("R$ ${"%.2f".format(amount)}", color = XpWhite, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = XpMagenta, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(waitingMessage, color = XpWhite, fontSize = 19.sp)
                    }
                    if (!waitingAlreadyMentionsOffline) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (backendOnline) "Backend online - gerando QR em tempo real" else "Sem conexao - tentando reconectar",
                            color = if (backendOnline) Color(0xFFADB5C6) else XpMagenta,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (!lastPaymentSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(lastPaymentSummary, color = Color(0xFF8E95A3), fontSize = 13.sp)
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
                        modifier = Modifier.height(76.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    StationBadge(stationName = stationName)
                }
                Text("PIX LIVE", color = XpWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroPanel(modifier = Modifier.weight(0.42f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("PAGAMENTO PIX", color = Color(0xFF9EA5B2), fontSize = 14.sp, letterSpacing = 2.2.sp)
                        Text("Liberacao instantanea", color = XpWhite, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
                        Text(
                            "${payment.durationMinutes} MIN  •  R$ ${"%.2f".format(payment.amount)}",
                            color = XpYellow,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Escaneie o QR Code no app do seu banco. Pagou, liberou automaticamente.",
                            color = Color(0xFFB6BECD),
                            fontSize = 16.sp,
                            lineHeight = 21.sp
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x66101319), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0x55FFD000), RoundedCornerShape(16.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(waitingMessage, color = XpWhite, fontSize = 15.sp)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(0.58f)
                        .background(Color(0xCC0D1117), RoundedCornerShape(30.dp))
                        .border(1.dp, Color(0x88FF005C), RoundedCornerShape(30.dp))
                        .padding(18.dp)
                ) {
                    QrCodePanel(qrCodeDataUrl = payment.qrCode, modifier = Modifier.fillMaxWidth())
                }
            }

            HeroPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = payment.pixCopiaECola.take(130) + if (payment.pixCopiaECola.length > 130) "..." else "",
                    color = Color(0xFFA4ADBD),
                    fontSize = 13.sp,
                    maxLines = 1
                )
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
            HeroPanel(modifier = Modifier.fillMaxWidth(0.72f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("TV LIBERADA", color = XpYellow, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    StationBadge(stationName = stationName)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Tempo restante", color = Color(0xFFB8C0CF), fontSize = 22.sp)
                    Text(
                        remainingText,
                        color = XpWhite,
                        fontSize = 88.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Use o console na HDMI durante este periodo.",
                        color = Color(0xFFE1E5EF),
                        fontSize = 20.sp
                    )
                }
            }

            if (!warning.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xAA111111), shape = MaterialTheme.shapes.medium)
                        .border(1.dp, Color(0x88FF005C), MaterialTheme.shapes.medium)
                        .padding(horizontal = 20.dp, vertical = 11.dp)
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
    var unlockMode by remember { mutableStateOf(UnlockMode.normalize(currentConfig.unlockMode)) }
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
                    unlockMode = UnlockMode.normalize(unlockMode),
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
                Spacer(modifier = Modifier.height(8.dp))
                Text("Modo de liberacao", color = XpWhite)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { unlockMode = UnlockMode.PIX_ONLY },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (unlockMode == UnlockMode.PIX_ONLY) XpYellow else XpBlack,
                            contentColor = if (unlockMode == UnlockMode.PIX_ONLY) XpBlack else XpWhite
                        )
                    ) {
                        Text("PIX")
                    }
                    Button(
                        onClick = { unlockMode = UnlockMode.PDV_ONLY },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (unlockMode == UnlockMode.PDV_ONLY) XpYellow else XpBlack,
                            contentColor = if (unlockMode == UnlockMode.PDV_ONLY) XpBlack else XpWhite
                        )
                    ) {
                        Text("PDV")
                    }
                    Button(
                        onClick = { unlockMode = UnlockMode.HYBRID },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (unlockMode == UnlockMode.HYBRID) XpYellow else XpBlack,
                            contentColor = if (unlockMode == UnlockMode.HYBRID) XpBlack else XpWhite
                        )
                    ) {
                        Text("Hibrido")
                    }
                }
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

