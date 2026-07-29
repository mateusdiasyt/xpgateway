package com.xparcade.tvkiosk.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xparcade.tvkiosk.R
import com.xparcade.tvkiosk.domain.model.TvDisplayGameResponse
import com.xparcade.tvkiosk.domain.model.TvDisplaySnapshotResponse
import com.xparcade.tvkiosk.ui.components.YouTubeTrailerPlayer
import com.xparcade.tvkiosk.ui.theme.XpBlack
import com.xparcade.tvkiosk.ui.theme.XpWhite
import com.xparcade.tvkiosk.ui.theme.XpYellow
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color

private val ShowcaseBackground = Color(0xFF05070B)
private val ShowcasePanel = Color(0xFF0B1018)
private val ShowcaseBorder = Color(0xFF263142)
private val ShowcaseMuted = Color(0xFF9AA7B9)
private val ShowcaseOnline = Color(0xFF57E6A5)

@Composable
fun TvShowcaseScreen(
    stationName: String,
    backendOnline: Boolean,
    waitingMessage: String,
    display: TvDisplaySnapshotResponse,
    onConfigureDevice: () -> Unit
) {
    val trailers = display.displayConfig.trailers.filter { it.youtubeVideoId.isNotBlank() }
    val gamePages = display.displayConfig.games.chunked(6).ifEmpty { listOf(emptyList()) }
    var trailerIndex by remember(trailers) { mutableIntStateOf(0) }
    var trailerPlaybackKey by remember(trailers) { mutableIntStateOf(0) }
    var gamePageIndex by remember(gamePages) { mutableIntStateOf(0) }
    var trailerCurrentSeconds by remember { mutableDoubleStateOf(0.0) }
    var trailerDurationSeconds by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(gamePages.size) {
        gamePageIndex = 0
        while (gamePages.size > 1) {
            delay(9_000)
            gamePageIndex = (gamePageIndex + 1) % gamePages.size
        }
    }

    LaunchedEffect(trailers) {
        trailerIndex = 0
        trailerPlaybackKey = 0
        trailerCurrentSeconds = 0.0
        trailerDurationSeconds = 0.0
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.58f),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                TrailerPanel(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight(),
                    trailerTitle = trailers.getOrNull(trailerIndex)?.title,
                    trailerVideoId = trailers.getOrNull(trailerIndex)?.youtubeVideoId,
                    trailerPlaybackKey = trailerPlaybackKey,
                    trailerPosition = trailerIndex,
                    trailerCount = trailers.size,
                    nextTrailerTitle = trailers.getOrNull((trailerIndex + 1).modOrZero(trailers.size))?.title,
                    currentSeconds = trailerCurrentSeconds,
                    durationSeconds = trailerDurationSeconds,
                    onProgress = { current, duration ->
                        trailerCurrentSeconds = current
                        trailerDurationSeconds = duration
                    },
                    onEnded = {
                        if (trailers.isNotEmpty()) {
                            trailerIndex = (trailerIndex + 1) % trailers.size
                            trailerPlaybackKey += 1
                            trailerCurrentSeconds = 0.0
                            trailerDurationSeconds = 0.0
                        }
                    }
                )

                CashierCallout(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight(),
                    stationName = display.deviceLabel.ifBlank { stationName },
                    backendOnline = backendOnline,
                    waitingMessage = waitingMessage
                )
            }

            GamesPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f),
                games = gamePages[gamePageIndex],
                page = gamePageIndex,
                pageCount = gamePages.size
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
            .background(Color(0xFF080B10))
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
                maxLines = 1
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
private fun TrailerPanel(
    modifier: Modifier,
    trailerTitle: String?,
    trailerVideoId: String?,
    trailerPlaybackKey: Int,
    trailerPosition: Int,
    trailerCount: Int,
    nextTrailerTitle: String?,
    currentSeconds: Double,
    durationSeconds: Double,
    onProgress: (Double, Double) -> Unit,
    onEnded: () -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(ShowcasePanel)
                .border(1.dp, ShowcaseBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!trailerVideoId.isNullOrBlank()) {
                YouTubeTrailerPlayer(
                    videoId = trailerVideoId,
                    playbackKey = trailerPlaybackKey,
                    modifier = Modifier.fillMaxSize(),
                    onEnded = onEnded,
                    onProgress = onProgress
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "TRAILERS EM BREVE",
                        color = XpYellow,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Configure os links desta TV na aba App.",
                        color = ShowcaseMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(9.dp))
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
            trackColor = Color(0xFF1B2330)
        )
        Spacer(modifier = Modifier.height(7.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (trailerCount > 0) {
                    "TRAILER ${trailerPosition + 1} DE $trailerCount"
                } else {
                    "PLAYLIST VAZIA"
                },
                color = XpYellow,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = trailerTitle.orEmpty(),
                color = XpWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!nextTrailerTitle.isNullOrBlank() && trailerCount > 1) {
                Text(
                    text = "PRÓXIMO: ${nextTrailerTitle.uppercase()}",
                    color = ShowcaseMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CashierCallout(
    modifier: Modifier,
    stationName: String,
    backendOnline: Boolean,
    waitingMessage: String
) {
    Box(
        modifier = modifier
            .border(1.dp, ShowcaseBorder, RoundedCornerShape(12.dp))
            .background(ShowcasePanel, RoundedCornerShape(12.dp))
            .padding(horizontal = 28.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(58.dp)
                    .height(5.dp)
                    .background(XpYellow, RoundedCornerShape(50))
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "VÁ ATÉ O CAIXA",
                color = XpYellow,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "PARA LIBERAR O\n${stationName.uppercase()}",
                color = XpWhite,
                fontSize = 42.sp,
                lineHeight = 45.sp,
                fontWeight = FontWeight.Black,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (backendOnline) "A liberação acontece automaticamente." else waitingMessage,
                color = if (backendOnline) ShowcaseMuted else Color(0xFFFF8D98),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun GamesPanel(
    modifier: Modifier,
    games: List<TvDisplayGameResponse>,
    page: Int,
    pageCount: Int
) {
    Column(
        modifier = modifier
            .border(1.dp, ShowcaseBorder, RoundedCornerShape(12.dp))
            .background(ShowcasePanel, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 13.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "JOGOS DISPONÍVEIS",
                color = XpWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(3.dp)
                    .background(XpYellow, RoundedCornerShape(50))
            )
            Spacer(modifier = Modifier.weight(1f))
            if (pageCount > 1) {
                Text(
                    text = "${page + 1}/$pageCount",
                    color = ShowcaseMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (games.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "CATÁLOGO EM ATUALIZAÇÃO",
                    color = ShowcaseMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(
                    12.dp,
                    Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                games.forEach { game ->
                    GameCover(
                        game = game,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(2f / 3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GameCover(game: TvDisplayGameResponse, modifier: Modifier = Modifier) {
    val coverImage = remember(game.imageDataUrl) {
        decodeDataUrlImage(game.imageDataUrl)
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF111722))
            .border(1.dp, Color(0xFF303B4C), RoundedCornerShape(10.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF080B10)),
            contentAlignment = Alignment.Center
        ) {
            if (coverImage != null) {
                Image(
                    bitmap = coverImage,
                    contentDescription = "Capa de ${game.title}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            } else {
                Text(
                    text = "SEM CAPA",
                    color = ShowcaseMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = game.title.uppercase(),
            color = XpWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp)
        )
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

private fun Int.modOrZero(divisor: Int): Int = if (divisor > 0) this % divisor else 0
