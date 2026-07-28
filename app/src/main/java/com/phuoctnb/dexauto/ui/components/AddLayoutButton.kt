package com.phuoctnb.dexauto.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.phuoctnb.dexauto.R

@Composable
fun AddLayoutButton(
    showPopup: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(50.dp)
            .background(if (pressed) Color(0x338CC7FF) else Color.Transparent, RoundedCornerShape(7.dp))
            .border(1.dp, Color(0xFF52606D), RoundedCornerShape(7.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (showPopup) Icons.Default.Close else Icons.Default.Add,
            contentDescription = stringResource(
                if (showPopup) R.string.content_desc_close_add_layout else R.string.content_desc_add_layout
            ),
            modifier = Modifier.size(24.dp),
            tint = Color.White
        )
    }
}

@Preview(widthDp = 100, heightDp = 52, backgroundColor = 0xFF151A20, showBackground = true)
@Composable
private fun AddLayoutButtonPreview() {
    AddLayoutButton(showPopup = false, onToggle = {})
}
