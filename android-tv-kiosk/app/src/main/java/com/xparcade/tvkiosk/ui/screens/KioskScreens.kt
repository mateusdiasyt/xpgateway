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
import com.xparcade.tvkiosk.data.local.StationPreset
import com.xparcade.tvkiosk.integration.hdmi.HdmiInputInfo
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
                        Color(0xFF070707),
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
                        colors = listOf(Color(0x22FF005C), Color.Transparent),
                        center = Offset(1420f, 180f),
                        radius = 920f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1EFFFFFF), Color.Transparent),
                        center = Offset(240f, 960f),
                        radius = 860f
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
fun InitialSetupScreen(
    stationPresets: List<StationPreset>,
    onSelectStation: (StationPreset) -> Unit
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
                    .fillMaxWidth(0.42f)
                    .height(104.dp)
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "CONFIGURACAO INICIAL",
                color = Color(0xFFC8CBD3),
                fontSize = 15.sp,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Qual TV esta recebendo este APK?",
                color = XpWhite,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))

            HeroPanel(modifier = Modifier.fillMaxWidth(0.72f)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Escolha pelo controle remoto. Esta selecao fica salva nesta TV.",
                        color = Color(0xFFDDE2ED),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        stationPresets.forEach { preset ->
                            Button(
                                onClick = { onSelectStation(preset) },
                                colors = ButtonDefaults.buttonColors(containerColor = XpMagenta),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(86.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        preset.stationName,
                                        color = XpWhite,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        preset.stationId.uppercase(),
                                        color = Color(0xFFEDEFF5),
                                        fontSize = 13.sp,
                                        letterSpacing = 1.4.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreparationScreen(
    stationName: String,
    remainingSeconds: Long
) {
    NeonBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeroPanel(modifier = Modifier.fillMaxWidth(0.7f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("PREPARE-SE", color = XpYellow, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    StationBadge(stationName = stationName)
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "O tempo vendido comeca quando a contagem zerar.",
                        color = Color(0xFFE1E5EF),
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        remainingSeconds.coerceAtLeast(0).toString(),
                        color = XpWhite,
                        fontSize = 120.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "segundos para sentar e pegar o controle",
                        color = Color(0xFFB8C0CF),
                        fontSize = 21.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LockScreen(stationName: String, backendOnline: Boolean, waitingMessage: String, lastPaymentSummary: String?) {
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

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "COMO JOGAR",
                color = Color(0xFFC8CBD3),
                fontSize = 14.sp,
                letterSpacing = 2.8.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Para jogar aqui é necessário ir até o caixa realizar o pagamento.",
                color = XpWhite,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 42.sp
            )

            Spacer(modifier = Modifier.height(26.dp))

            HeroPanel(modifier = Modifier.fillMaxWidth(0.76f)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "PASSO A PASSO",
                        color = Color(0xFF9EA5B2),
                        fontSize = 14.sp,
                        letterSpacing = 2.2.sp
                    )
                    Text("1. Vá até o caixa.", color = XpWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("2. Informe a estação ${stationName.uppercase()}.", color = XpWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("3. Após o pagamento, a liberação será automática.", color = XpWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = XpMagenta, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (backendOnline) waitingMessage else "Sem conexão com servidor. Chame um atendente.",
                            color = if (backendOnline) Color(0xFFDDE2ED) else XpMagenta,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            if (!lastPaymentSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(lastPaymentSummary, color = Color(0xFF8E95A3), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SessionActiveScreen(
    stationName: String,
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
                    Text("HDMI LIBERADA", color = XpYellow, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    StationBadge(stationName = stationName)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Jogue pela entrada HDMI. O tempo fica sendo controlado no PDV.",
                        color = Color(0xFFE1E5EF),
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 31.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Se esta tela ainda aparecer, altere a fonte para HDMI pelo controle.",
                        color = Color(0xFFB8C0CF),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
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
    onEndSession: () -> Unit,
    hdmiInputs: List<HdmiInputInfo>,
    hdmiStatusMessage: String?,
    onRefreshHdmiInputs: () -> Unit,
    onTestHdmiInput: (String) -> Unit,
    onReturnToKiosk: () -> Unit
) {
    var stationName by remember { mutableStateOf(currentConfig.stationName) }
    var stationId by remember { mutableStateOf(currentConfig.stationId) }
    var deviceKey by remember { mutableStateOf(currentConfig.deviceKey) }
    var backendUrl by remember { mutableStateOf(currentConfig.backendUrl) }
    var adminPin by remember { mutableStateOf(currentConfig.adminPin) }
    var autoStartApp by remember { mutableStateOf(currentConfig.autoStartApp) }
    var hdmiSwitchEnabled by remember { mutableStateOf(currentConfig.hdmiSwitchEnabled) }
    var consoleInputId by remember { mutableStateOf(currentConfig.consoleInputId) }
    var forceMinutes by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                val merged = currentConfig.copy(
                    stationName = stationName,
                    stationId = stationId,
                    deviceKey = deviceKey,
                    backendUrl = backendUrl,
                    adminPin = adminPin,
                    unlockMode = "PDV_ONLY",
                    autoStartApp = autoStartApp,
                    hdmiSwitchEnabled = hdmiSwitchEnabled,
                    consoleInputId = consoleInputId
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
                Text(
                    "Modo PDV: informe a URL do xp-pdv e o codigo desta TV.",
                    color = XpWhite,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = backendUrl, onValueChange = { backendUrl = it }, label = { Text("URL do PDV") })
                OutlinedTextField(value = stationName, onValueChange = { stationName = it }, label = { Text("Nome da estacao") })
                OutlinedTextField(value = stationId, onValueChange = { stationId = it }, label = { Text("Station ID") })
                OutlinedTextField(value = deviceKey, onValueChange = { deviceKey = it }, label = { Text("Device key (TV)") })
                OutlinedTextField(value = adminPin, onValueChange = { adminPin = it }, label = { Text("PIN admin") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Iniciar automatico no boot", color = XpWhite)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = autoStartApp, onCheckedChange = { autoStartApp = it })
                }

                Spacer(modifier = Modifier.height(16.dp))
                HeroPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("HDMI / CEC experimental", color = XpYellow, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "O APK tenta abrir a entrada HDMI pelo sistema da TV. Se a TV nao expor as entradas, sera necessario trocar pelo controle ou usar hardware externo.",
                            color = Color(0xFFDDE2ED),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Trocar para HDMI ao liberar", color = XpWhite)
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(checked = hdmiSwitchEnabled, onCheckedChange = { hdmiSwitchEnabled = it })
                        }

                        OutlinedTextField(
                            value = consoleInputId,
                            onValueChange = { consoleInputId = it },
                            label = { Text("ID da entrada do console") },
                            supportingText = { Text("Use uma entrada detectada abaixo ou cole o ID manualmente.") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (hdmiInputs.isEmpty()) {
                            Text(
                                "Nenhuma HDMI detectada. Clique em recarregar ou teste na TV real.",
                                color = XpMagenta,
                                fontSize = 13.sp
                            )
                        } else {
                            hdmiInputs.forEach { input ->
                                Button(
                                    onClick = { consoleInputId = input.id },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (consoleInputId == input.id) XpMagenta else Color(0xFF252525)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                                        Text(input.label, color = XpWhite, fontWeight = FontWeight.Bold)
                                        Text(input.id, color = Color(0xFFB8C0CF), fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = onRefreshHdmiInputs, modifier = Modifier.weight(1f)) {
                                Text("Recarregar HDMI")
                            }
                            Button(onClick = { onTestHdmiInput(consoleInputId) }, modifier = Modifier.weight(1f)) {
                                Text("Testar HDMI")
                            }
                            TextButton(onClick = onReturnToKiosk, modifier = Modifier.weight(1f)) {
                                Text("Voltar bloqueio", color = XpYellow)
                            }
                        }

                        if (!hdmiStatusMessage.isNullOrBlank()) {
                            Text(hdmiStatusMessage, color = Color(0xFFDDE2ED), fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(value = forceMinutes, onValueChange = { forceMinutes = it }, label = { Text("Liberacao local de teste (min)") })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onTestConnection) {
                        Text("Testar conexao")
                    }
                    Button(onClick = { onForceUnlock(forceMinutes.toIntOrNull() ?: 30) }) {
                        Text("Liberar teste")
                    }
                    TextButton(onClick = onEndSession) {
                        Text("Encerrar sessao", color = XpMagenta)
                    }
                }
            }
        }
    )
}


