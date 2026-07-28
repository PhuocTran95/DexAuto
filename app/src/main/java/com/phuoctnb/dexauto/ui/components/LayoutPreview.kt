package com.phuoctnb.dexauto.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.phuoctnb.dexauto.R
import com.phuoctnb.dexauto.data.LaunchableApp
import com.phuoctnb.dexauto.data.LayoutType
import com.phuoctnb.dexauto.data.defaultRatiosFor

@Composable
fun LayoutTypeButton(type: LayoutType, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 86.dp, height = 64.dp)
            .background(if (selected) Color(0xFF23384B) else Color(0xFF111820), RoundedCornerShape(6.dp))
            .border(1.dp, if (selected) Color(0xFF8CC7FF) else Color(0xFF46515C), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val stroke = Stroke(width = 2f)
            val w = size.width
            val h = size.height
            when (type) {
                LayoutType.Full -> drawRect(Color.White, style = stroke)
                LayoutType.SplitTwo -> {
                    drawRect(Color.White, style = stroke)
                    drawLine(Color.White, Offset(w / 2, 0f), Offset(w / 2, h), strokeWidth = 2f)
                }
                LayoutType.SplitTwoRows -> {
                    drawRect(Color.White, style = stroke)
                    drawLine(Color.White, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 2f)
                }
                LayoutType.SplitThree -> {
                    drawRect(Color.White, style = stroke)
                    drawLine(Color.White, Offset(w / 3, 0f), Offset(w / 3, h), strokeWidth = 2f)
                    drawLine(Color.White, Offset(w * 2 / 3, 0f), Offset(w * 2 / 3, h), strokeWidth = 2f)
                }
                LayoutType.TwoTopOneBottom -> {
                    drawRect(Color.White, style = stroke)
                    drawLine(Color.White, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 2f)
                    drawLine(Color.White, Offset(w / 2, 0f), Offset(w / 2, h / 2), strokeWidth = 2f)
                }
                LayoutType.OneTopTwoBottom -> {
                    drawRect(Color.White, style = stroke)
                    drawLine(Color.White, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 2f)
                    drawLine(Color.White, Offset(w / 2, h / 2), Offset(w / 2, h), strokeWidth = 2f)
                }
                LayoutType.OneLeftTwoRight -> {
                    drawRect(Color.White, style = stroke)
                    drawLine(Color.White, Offset(w / 2, 0f), Offset(w / 2, h), strokeWidth = 2f)
                    drawLine(Color.White, Offset(w / 2, h / 2), Offset(w, h / 2), strokeWidth = 2f)
                }
                LayoutType.TwoLeftOneRight -> {
                    drawRect(Color.White, style = stroke)
                    drawLine(Color.White, Offset(w / 2, 0f), Offset(w / 2, h), strokeWidth = 2f)
                    drawLine(Color.White, Offset(0f, h / 2), Offset(w / 2, h / 2), strokeWidth = 2f)
                }
                LayoutType.SplitFourColumns -> {
                    drawRect(Color.White, style = stroke)
                    drawLine(Color.White, Offset(w / 4, 0f), Offset(w / 4, h), strokeWidth = 2f)
                    drawLine(Color.White, Offset(w / 2, 0f), Offset(w / 2, h), strokeWidth = 2f)
                    drawLine(Color.White, Offset(w * 3 / 4, 0f), Offset(w * 3 / 4, h), strokeWidth = 2f)
                }
                LayoutType.GridFour -> {
                    drawRect(Color.White, style = stroke)
                    drawLine(Color.White, Offset(w / 2, 0f), Offset(w / 2, h), strokeWidth = 2f)
                    drawLine(Color.White, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 2f)
                }
            }
        }
    }
}

@Composable
fun LayoutPreview(
    type: LayoutType,
    packages: List<String>,
    appsByPackage: Map<String, LaunchableApp>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    ratios: List<Float> = emptyList(),
    fixedHeight: Boolean = true,
    showOuterBorder: Boolean = true,
    onSlotClicked: ((Int) -> Unit)? = null
) {
    val activeRatios = ratios.ifEmpty { defaultRatiosFor(type) }.map { it.coerceIn(0.15f, 0.85f) }
    fun ratio(index: Int, fallback: Float): Float = activeRatios.getOrNull(index) ?: fallback
    val height = if (compact) 54.dp else 92.dp
    val previewModifier = modifier
        .fillMaxWidth()
        .then(if (fixedHeight) Modifier.height(height) else Modifier.fillMaxHeight())
        .then(
            if (showOuterBorder) {
                Modifier.border(1.dp, Color(0xFF48525E), RoundedCornerShape(7.dp))
            } else {
                Modifier
            }
        )
        .padding(if (showOuterBorder) 3.dp else 0.dp)

    Column(
        modifier = previewModifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        when (type) {
            LayoutType.Full -> PreviewRow(listOf(0), packages, appsByPackage, compact, onSlotClicked, Modifier.weight(1f))
            LayoutType.SplitTwo -> {
                val x = ratio(0, 0.5f)
                PreviewRowWeighted(listOf(0, 1), listOf(x, 1f - x), packages, appsByPackage, compact, onSlotClicked, Modifier.weight(1f))
            }
            LayoutType.SplitTwoRows -> {
                val y = ratio(0, 0.5f)
                PreviewRow(listOf(0), packages, appsByPackage, compact, onSlotClicked, Modifier.weight(y))
                PreviewRow(listOf(1), packages, appsByPackage, compact, onSlotClicked, Modifier.weight(1f - y))
            }
            LayoutType.SplitThree -> {
                val x0 = ratio(0, 1f / 3f)
                val x1 = ratio(1, 2f / 3f).coerceAtLeast(x0 + 0.1f).coerceAtMost(0.9f)
                PreviewRowWeighted(listOf(0, 1, 2), listOf(x0, x1 - x0, 1f - x1), packages, appsByPackage, compact, onSlotClicked, Modifier.weight(1f))
            }
            LayoutType.TwoTopOneBottom -> {
                val x = ratio(0, 0.5f)
                val y = ratio(1, 0.5f)
                PreviewRowWeighted(listOf(0, 1), listOf(x, 1f - x), packages, appsByPackage, compact, onSlotClicked, Modifier.weight(y))
                PreviewRow(listOf(2), packages, appsByPackage, compact, onSlotClicked, Modifier.weight(1f - y))
            }
            LayoutType.OneTopTwoBottom -> {
                val x = ratio(0, 0.5f)
                val y = ratio(1, 0.5f)
                PreviewRow(listOf(0), packages, appsByPackage, compact, onSlotClicked, Modifier.weight(y))
                PreviewRowWeighted(listOf(1, 2), listOf(x, 1f - x), packages, appsByPackage, compact, onSlotClicked, Modifier.weight(1f - y))
            }
            LayoutType.OneLeftTwoRight -> {
                val x = ratio(0, 0.5f)
                val y = ratio(1, 0.5f)
                PreviewColumns(
                    leftIndexes = listOf(0),
                    rightIndexes = listOf(1, 2),
                    weights = listOf(x, 1f - x),
                    rightWeights = listOf(y, 1f - y),
                    packages = packages,
                    appsByPackage = appsByPackage,
                    compact = compact,
                    onSlotClicked = onSlotClicked,
                    modifier = Modifier.weight(1f)
                )
            }
            LayoutType.TwoLeftOneRight -> {
                val x = ratio(0, 0.5f)
                val y = ratio(1, 0.5f)
                PreviewColumns(
                    leftIndexes = listOf(0, 1),
                    rightIndexes = listOf(2),
                    weights = listOf(x, 1f - x),
                    leftWeights = listOf(y, 1f - y),
                    packages = packages,
                    appsByPackage = appsByPackage,
                    compact = compact,
                    onSlotClicked = onSlotClicked,
                    modifier = Modifier.weight(1f)
                )
            }
            LayoutType.SplitFourColumns -> {
                val x0 = ratio(0, 0.25f)
                val x1 = ratio(1, 0.5f).coerceAtLeast(x0 + 0.08f).coerceAtMost(0.86f)
                val x2 = ratio(2, 0.75f).coerceAtLeast(x1 + 0.08f).coerceAtMost(0.92f)
                PreviewRowWeighted(listOf(0, 1, 2, 3), listOf(x0, x1 - x0, x2 - x1, 1f - x2), packages, appsByPackage, compact, onSlotClicked, Modifier.weight(1f))
            }
            LayoutType.GridFour -> {
                GridFourIndependentPreview(
                    ratios = listOf(
                        ratio(0, 0.5f),
                        ratio(1, 0.5f),
                        ratio(2, 0.5f),
                        ratio(3, 0.5f)
                    ),
                    packages = packages,
                    appsByPackage = appsByPackage,
                    compact = compact,
                    onSlotClicked = onSlotClicked,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GridFourIndependentPreview(
    ratios: List<Float>,
    packages: List<String>,
    appsByPackage: Map<String, LaunchableApp>,
    compact: Boolean,
    onSlotClicked: ((Int) -> Unit)?,
    modifier: Modifier
) {
    val topX = ratios.getOrElse(0) { 0.5f }
    val bottomX = ratios.getOrElse(1) { 0.5f }
    val leftY = ratios.getOrElse(2) { 0.5f }
    val rightY = ratios.getOrElse(3) { 0.5f }
    BoxWithConstraints(modifier.fillMaxWidth().fillMaxHeight()) {
        GridFourSlot(0, 0f, 0f, topX, leftY, packages, appsByPackage, compact, onSlotClicked)
        GridFourSlot(1, topX, 0f, 1f, rightY, packages, appsByPackage, compact, onSlotClicked)
        GridFourSlot(2, 0f, leftY, bottomX, 1f, packages, appsByPackage, compact, onSlotClicked)
        GridFourSlot(3, bottomX, rightY, 1f, 1f, packages, appsByPackage, compact, onSlotClicked)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.GridFourSlot(
    index: Int,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    packages: List<String>,
    appsByPackage: Map<String, LaunchableApp>,
    compact: Boolean,
    onSlotClicked: ((Int) -> Unit)?
) {
    PreviewSlot(
        index = index,
        packages = packages,
        appsByPackage = appsByPackage,
        compact = compact,
        onSlotClicked = onSlotClicked,
        modifier = Modifier
            .offset(x = maxWidth * startX, y = maxHeight * startY)
            .width(maxWidth * (endX - startX).coerceAtLeast(0.05f))
            .height(maxHeight * (endY - startY).coerceAtLeast(0.05f))
    )
}

@Composable
private fun PreviewColumns(
    leftIndexes: List<Int>,
    rightIndexes: List<Int>,
    weights: List<Float> = listOf(1f, 1f),
    leftWeights: List<Float>? = null,
    rightWeights: List<Float>? = null,
    packages: List<String>,
    appsByPackage: Map<String, LaunchableApp>,
    compact: Boolean,
    onSlotClicked: ((Int) -> Unit)?,
    modifier: Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        PreviewColumn(leftIndexes, packages, appsByPackage, compact, onSlotClicked, Modifier.weight(weights.getOrElse(0) { 1f }), leftWeights)
        PreviewColumn(rightIndexes, packages, appsByPackage, compact, onSlotClicked, Modifier.weight(weights.getOrElse(1) { 1f }), rightWeights)
    }
}

@Composable
private fun PreviewColumn(
    indexes: List<Int>,
    packages: List<String>,
    appsByPackage: Map<String, LaunchableApp>,
    compact: Boolean,
    onSlotClicked: ((Int) -> Unit)?,
    modifier: Modifier,
    weights: List<Float>? = null
) {
    Column(modifier = modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        indexes.forEachIndexed { weightIndex, index ->
            PreviewSlot(
                index = index,
                packages = packages,
                appsByPackage = appsByPackage,
                compact = compact,
                onSlotClicked = onSlotClicked,
                modifier = Modifier.weight(weights?.getOrNull(weightIndex) ?: 1f)
            )
        }
    }
}

@Composable
private fun PreviewRowWeighted(
    indexes: List<Int>,
    weights: List<Float>,
    packages: List<String>,
    appsByPackage: Map<String, LaunchableApp>,
    compact: Boolean,
    onSlotClicked: ((Int) -> Unit)?,
    modifier: Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        indexes.forEachIndexed { weightIndex, index ->
            PreviewSlot(index, packages, appsByPackage, compact, onSlotClicked, Modifier.weight(weights.getOrNull(weightIndex)?.coerceAtLeast(0.05f) ?: 1f))
        }
    }
}

@Composable
private fun PreviewRow(
    indexes: List<Int>,
    packages: List<String>,
    appsByPackage: Map<String, LaunchableApp>,
    compact: Boolean,
    onSlotClicked: ((Int) -> Unit)?,
    modifier: Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        indexes.forEach { index ->
            PreviewSlot(index, packages, appsByPackage, compact, onSlotClicked, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewSlot(
    index: Int,
    packages: List<String>,
    appsByPackage: Map<String, LaunchableApp>,
    compact: Boolean,
    onSlotClicked: ((Int) -> Unit)?,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color(0xFF111820), RoundedCornerShape(5.dp))
            .border(1.dp, Color(0xFF52606D), RoundedCornerShape(5.dp))
            .clickable(enabled = onSlotClicked != null) { onSlotClicked?.invoke(index) },
        contentAlignment = Alignment.Center
    ) {
        val app = appsByPackage[packages.getOrNull(index).orEmpty()]
        if (app == null) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.content_desc_choose_app),
                tint = Color.White,
                modifier = Modifier.size(if (compact) 18.dp else 24.dp)
            )
        } else {
            AppIcon(app.icon, app.label, Modifier.size(if (compact) 20.dp else 24.dp))
        }
    }
}
