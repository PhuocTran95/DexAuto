package com.phuoctnb.dexauto.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

@Composable
fun AppIcon(icon: Drawable, contentDescription: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(icon) { icon.toBitmap(width = 96, height = 96).asImageBitmap() }
    Image(bitmap = bitmap, contentDescription = contentDescription, modifier = modifier)
}
