package com.phuoctnb.dexauto.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phuoctnb.dexauto.data.PanelPosition

@Composable
fun PanelPositionSelector(
    selected: PanelPosition,
    onSelect: (PanelPosition) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
    ) {
        PositionButton(PanelPosition.Top, selected, onSelect, Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(0.3f))
        PositionButton(PanelPosition.Bottom, selected, onSelect, Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.3f))
        PositionButton(PanelPosition.Left, selected, onSelect, Modifier.align(Alignment.CenterStart).fillMaxWidth(0.48f).fillMaxHeight(0.3f))
        PositionButton(PanelPosition.Right, selected, onSelect, Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.48f).fillMaxHeight(0.3f))
    }
}

@Composable
private fun PositionButton(
    position: PanelPosition,
    selected: PanelPosition,
    onSelect: (PanelPosition) -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .background(if (selected == position) Color(0xFF315A7D) else Color(0xFF111820), RoundedCornerShape(4.dp))
            .border(1.dp, if (selected == position) Color(0xFF8CC7FF) else Color(0xFF46515C), RoundedCornerShape(4.dp))
            .clickable { onSelect(position) },
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(position.labelRes), color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}
