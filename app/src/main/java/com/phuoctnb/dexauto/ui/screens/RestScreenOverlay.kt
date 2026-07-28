package com.phuoctnb.dexauto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phuoctnb.dexauto.R
import com.phuoctnb.dexauto.ui.components.DateTimeSection

@Composable
fun RestScreenOverlay(
    nowMillis: Long,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(onDismiss) {
                detectTapGestures(onDoubleTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DateTimeSection(
                nowMillis = nowMillis,
                dateFontSize = 38.sp,
                dateLineHeight = 44.sp,
                timeFontSize = 52.sp,
                textColor = Color.White.copy(alpha = 0.68f)
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.rest_screen_dismiss_hint),
                color = Color.White.copy(alpha = 0.28f),
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}
