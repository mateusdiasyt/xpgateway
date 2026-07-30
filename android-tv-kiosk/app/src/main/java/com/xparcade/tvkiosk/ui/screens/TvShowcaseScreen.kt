package com.xparcade.tvkiosk.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xparcade.tvkiosk.R
import com.xparcade.tvkiosk.domain.model.TvDisplayGameResponse
import com.xparcade.tvkiosk.domain.model.TvDisplaySnapshotResponse
import com.xparcade.tvkiosk.ui.components.YouTubeTrailerPlayer
import com.xparcade.tvkiosk.ui.theme.XpWhite
import com.xparcade.tvkiosk.ui.theme.XpYellow
import kotlinx.coroutines.delay

private val ShowcaseBackground = Color(0xFF000000)
private val ShowcasePanel = Color(0xFF080808)
private val ShowcasePanelRaised = Color(0xFF0D0D0D)
private val ShowcaseBorder = Color(0xFF2A2A2A)
private val ShowcaseMuted = Color(0xFFA4A4A4)
private val ShowcaseOnline = Color(0xFF57E6A5)

@Composable
fun TvShowcaseScreen(
    stationName: String,
    backendOnline: Boolean,
    waitingMessage: String,
    display: TvDisplaySnapshotResponse,
    onConfigureDevice: () -> Unit
) {
    val games = display.displayConfig.games
    var selectedGameIndex by remember(games) { mutableIntStateOf(0) }
    var playbackKey by remember(games) { mutableIntStateOf(0) }
    var currentSeconds by remember { mutableDoubleStateOf(0.0) }
    var durationSeconds by remember { mutableDoubleStateOf(0.0) }
    val selectedGame = games.getOrNull(selectedGameIndex)

    fun selectNextGame() {
        if (games.isEmpty()) return

        selectedGameIndex = (selectedGameIndex + 1) % games.size
        playbackKey += 1
        currentSeconds = 0.0
        durationSeconds = 0.0
    }

    LaunchedEffect(games) {
        selectedGameIndex = 0
        playbackKey = 0
        currentSeconds = 0.0
        durationSeconds = 0.0
    }

    LaunchedEffect(selectedGame?.id, selectedGame?.youtubeVideoId, games.size) {
        if (selectedGame != null && selectedGame.youtubeVideoId.isBlank() && games.size > 1) {
            delay(9_000)
            selectNextGame()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ShowcaseBackground)
    ) {
        ShowcaseHeader(
            tenantName = display.tenantName,
            tenantLogoDataUrl = display.tenantLogoDataUrl,
            stationName = display.deviceLabel.ifBlank { stationName },
            backendOnline = backendOnline,
            onConfigureDevice = onConfigureDevice
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                GameSliderPanel(
                    modifier = Modifier
                        .weight(0.31f)
                        .fillMaxHeight(),
                    games = games,
                    selectedIndex = selectedGameIndex
                )

                TrailerPanel(
                    modifier = Modifier
                        .weight(0.69f)
                        .fillMaxHeight(),
                    videoId = selectedGame?.youtubeVideoId,
                    playbackKey = playbackKey,
                    position = selectedGameIndex,
                    count = games.size,
                    currentSeconds = currentSeconds,
                    durationSeconds = durationSeconds,
                    onProgress = { current, duration ->
                        currentSeconds = current
                        durationSeconds = duration
                    },
                    onEnded = ::selectNextGame
                )
            }

            CashierCallout(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                stationName = display.deviceLabel.ifBlank { stationName },
                backendOnline = backendOnline,
                waitingMessage = waitingMessage
            )
        }
    }
}

@Composable
private fun ShowcaseHeader(
    tenantName: String,
    tenantLogoDataUrl: String?,
    stationName: String,
    backendOnline: Boolean,
    onConfigureDevice: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .background(Color(0xFF050505))
            .border(width = 1.dp, color = ShowcaseBorder)
            .padding(horizontal = 28.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = tenantLogoDataUrl,
            contentDescription = "Logo de $tenantName",
            placeholder = painterResource(R.drawable.mendoza_logo_fallback),
            error = painterResource(R.drawable.mendoza_logo_fallback),
            fallback = painterResource(R.drawable.mendoza_logo_fallback),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(172.dp)
                .fillMaxHeight()
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tenantName.uppercase(),
                color = XpWhite,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stationName.uppercase(),
                color = ShowcaseMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (backendOnline) ShowcaseOnline else Color(0xFFFF5F6D),
                        RoundedCornerShape(50)
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (backendOnline) "CONECTADO AO CAIXA" else "RECONECTANDO",
                color = if (backendOnline) ShowcaseOnline else Color(0xFFFF8D98),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(18.dp))
            OutlinedButton(
                onClick = onConfigureDevice,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = XpYellow),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6C5A17)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("CONFIGURAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GameSliderPanel(
    modifier: Modifier,
    games: List<TvDisplayGameResponse>,
    selectedIndex: Int
) {
    Column(
        modifier = modifier
            .border(1.dp, ShowcaseBorder, RoundedCornerShape(12.dp))
            .background(ShowcasePanel, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "JOGOS DISPONÍVEIS",
                color = XpWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.weight(1f))
            if (games.isNotEmpty()) {
                Text(
                    text = "${selectedIndex + 1}/${games.size}",
                    color = XpYellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Crossfade(
            targetState = games.getOrNull(selectedIndex),
            label = "game-cover",
            modifier = Modifier.weight(1f)
        ) { game ->
            if (game == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, ShowcaseBorder, RoundedCornerShape(10.dp))
                        .background(Color(0xFF050505), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CATÁLOGO EM ATUALIZAÇÃO",
                        color = ShowcaseMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                GameCover(
                    game = game,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (games.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                games.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(if (index == selectedIndex) 24.dp else 8.dp)
                            .height(4.dp)
                            .background(
                                if (index == selectedIndex) XpYellow else Color(0xFF3A3A3A),
                                RoundedCornerShape(50)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TrailerPanel(
    modifier: Modifier,
    videoId: String?,
    playbackKey: Int,
    position: Int,
    count: Int,
    currentSeconds: Double,
    durationSeconds: Double,
    onProgress: (Double, Double) -> Unit,
    onEnded: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ShowcaseBorder, RoundedCornerShape(12.dp))
            .background(ShowcasePanel, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            val targetRatio = 16f / 9f
            val availableRatio = maxWidth.value / maxHeight.value
            val playerWidth = if (availableRatio > targetRatio) {
                maxHeight * targetRatio
            } else {
                maxWidth
            }
            val playerHeight = if (availableRatio > targetRatio) {
                maxHeight
            } else {
                maxWidth / targetRatio
            }

            Box(
                modifier = Modifier
                    .width(playerWidth)
                    .height(playerHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
                    .border(1.dp, ShowcaseBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!videoId.isNullOrBlank()) {
                    YouTubeTrailerPlayer(
                        videoId = videoId,
                        playbackKey = playbackKey,
                        modifier = Modifier.fillMaxSize(),
                        onEnded = onEnded,
                        onProgress = onProgress
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TRAILER NÃO CONFIGURADO",
                            color = XpYellow,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Adicione o link junto da capa na aba App.",
                            color = ShowcaseMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = {
                if (durationSeconds > 0) {
                    (currentSeconds / durationSeconds).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50)),
            color = XpYellow,
            trackColor = Color(0xFF292929)
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = if (count > 0) "TRAILER ${position + 1} DE $count" else "SEM TRAILER",
            color = XpYellow,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun CashierCallout(
    modifier: Modifier,
    stationName: String,
    backendOnline: Boolean,
    waitingMessage: String
) {
    Row(
        modifier = modifier
            .border(1.dp, Color(0xFF5B4B12), RoundedCornerShape(12.dp))
            .background(ShowcasePanelRaised, RoundedCornerShape(12.dp))
            .padding(horizontal = 26.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(XpYellow, RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(22.dp))
        Text(
            text = "VÁ ATÉ O CAIXA",
            color = XpYellow,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = "PARA LIBERAR O ${stationName.uppercase()}",
            color = XpWhite,
            fontSize = 35.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = if (backendOnline) "LIBERAÇÃO AUTOMÁTICA" else waitingMessage.uppercase(),
            color = if (backendOnline) ShowcaseOnline else Color(0xFFFF8D98),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GameCover(game: TvDisplayGameResponse, modifier: Modifier = Modifier) {
    val coverImage = remember(game.imageDataUrl) {
        decodeDataUrlImage(game.imageDataUrl)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black)
            .border(1.dp, ShowcaseBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (coverImage != null) {
            Image(
                bitmap = coverImage,
                contentDescription = "Capa de ${game.title}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "SEM CAPA",
                color = ShowcaseMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun decodeDataUrlImage(value: String): ImageBitmap? {
    val separator = value.indexOf(',')
    if (!value.startsWith("data:image/") || separator < 0) return null

    return runCatching {
        val bytes = Base64.decode(value.substring(separator + 1), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
