package com.xparcade.tvkiosk.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xparcade.tvkiosk.ui.theme.XpDarkGray
import com.xparcade.tvkiosk.ui.theme.XpMagenta
import com.xparcade.tvkiosk.ui.theme.XpWhite
import com.xparcade.tvkiosk.ui.theme.XpYellow

@Composable
fun NeonSelectableCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.04f else 1.0f, label = "focusScale")

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(width = if (focused) 3.dp else 1.dp, color = if (focused) XpYellow else XpMagenta),
        colors = CardDefaults.cardColors(containerColor = XpDarkGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0x33000000),
                            Color(0x22FF005C)
                        )
                    )
                )
                .border(1.dp, Color(0x33FFD000), RoundedCornerShape(18.dp))
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = title,
                fontSize = 30.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = XpYellow
            )
            Text(
                text = subtitle,
                fontSize = 24.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = XpWhite
            )
        }
    }
}
