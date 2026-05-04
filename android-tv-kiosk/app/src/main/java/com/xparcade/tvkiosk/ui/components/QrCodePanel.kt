package com.xparcade.tvkiosk.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xparcade.tvkiosk.ui.theme.XpDarkGray
import com.xparcade.tvkiosk.ui.theme.XpWhite
import com.xparcade.tvkiosk.ui.theme.XpYellow

@Composable
fun QrCodePanel(qrCodeDataUrl: String, modifier: Modifier = Modifier) {
    val base64Part = qrCodeDataUrl.substringAfter("base64,", "")
    if (base64Part.isBlank()) {
        Text("QR inválido", color = XpWhite)
        return
    }

    val bytes = Base64.decode(base64Part, Base64.DEFAULT)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    Box(
        modifier = modifier
            .background(XpDarkGray, RoundedCornerShape(20.dp))
            .border(2.dp, XpYellow, RoundedCornerShape(20.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            Text("Erro ao renderizar QR", color = XpWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR Code Pix",
                modifier = Modifier.fillMaxWidth(0.72f)
            )
        }
    }
}
