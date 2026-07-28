package com.phuoctnb.dexauto.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.phuoctnb.dexauto.data.LaunchableApp
import com.phuoctnb.dexauto.data.LayoutType
import com.phuoctnb.dexauto.data.defaultRatiosFor
import kotlin.math.roundToInt

@Composable
fun AdjustableLayoutPreview(
    type: LayoutType,
    packages: List<String>,
    appsByPackage: Map<String, LaunchableApp>,
    ratios: List<Float>,
    onRatiosChanged: (List<Float>) -> Unit,
    onSlotClicked: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        Box(Modifier.fillMaxSize()) {
            LayoutPreview(
                type = type,
                packages = packages,
                appsByPackage = appsByPackage,
                ratios = ratios,
                fixedHeight = false,
                showOuterBorder = false,
                onSlotClicked = onSlotClicked,
                modifier = Modifier.fillMaxSize()
            )
            LayoutRatioDragHandles(
                type = type,
                ratios = ratios,
                widthPx = widthPx,
                heightPx = heightPx,
                onRatiosChanged = onRatiosChanged
            )
        }
    }
}

@Composable
private fun LayoutRatioDragHandles(
    type: LayoutType,
    ratios: List<Float>,
    widthPx: Float,
    heightPx: Float,
    onRatiosChanged: (List<Float>) -> Unit
) {
    val safeRatios = ratios.ifEmpty { defaultRatiosFor(type) }.toMutableList()
    val currentRatios by rememberUpdatedState(safeRatios)
    val currentOnRatiosChanged by rememberUpdatedState(onRatiosChanged)

    dragHandlesFor(type, safeRatios).forEach { handle ->
        RatioHandle(
            handle = handle,
            widthPx = widthPx,
            heightPx = heightPx,
            currentRatios = { currentRatios },
            onRatiosChanged = currentOnRatiosChanged
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun RatioHandle(
    handle: HandleSpec,
    widthPx: Float,
    heightPx: Float,
    currentRatios: () -> List<Float>,
    onRatiosChanged: (List<Float>) -> Unit
) {
    var dragStartRatios by remember { mutableStateOf(emptyList<Float>()) }
    var startRawX by remember { mutableFloatStateOf(0f) }
    var startRawY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (widthPx * handle.xRatio).roundToInt() - 28,
                    (heightPx * handle.yRatio).roundToInt() - 28
                )
            }
            .size(56.dp)
            .background(Color(0x338CC7FF), CircleShape)
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartRatios = currentRatios()
                        startRawX = event.rawX
                        startRawY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val next = dragStartRatios.toMutableList()
                        val delta = if (handle.vertical) {
                            (event.rawX - startRawX) / widthPx
                        } else {
                            (event.rawY - startRawY) / heightPx
                        }
                        next[handle.ratioIndex] = (next.getOrElse(handle.ratioIndex) { 0.5f } + delta)
                            .coerceIn(0.15f, 0.85f)
                        onRatiosChanged(next)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(24.dp)
                .background(Color(0xFF8CC7FF), CircleShape)
        )
    }
}

private fun dragHandlesFor(type: LayoutType, ratios: List<Float>): List<HandleSpec> {
    return when (type) {
        LayoutType.Full -> emptyList()
        LayoutType.SplitTwo -> listOf(HandleSpec(0, true, ratios.getOrElse(0) { 0.5f }, 0.5f))
        LayoutType.SplitTwoRows -> listOf(HandleSpec(0, false, 0.5f, ratios.getOrElse(0) { 0.5f }))
        LayoutType.SplitThree -> listOf(
            HandleSpec(0, true, ratios.getOrElse(0) { 1f / 3f }, 0.5f),
            HandleSpec(1, true, ratios.getOrElse(1) { 2f / 3f }, 0.5f)
        )
        LayoutType.SplitFourColumns -> listOf(
            HandleSpec(0, true, ratios.getOrElse(0) { 0.25f }, 0.5f),
            HandleSpec(1, true, ratios.getOrElse(1) { 0.5f }, 0.5f),
            HandleSpec(2, true, ratios.getOrElse(2) { 0.75f }, 0.5f)
        )
        LayoutType.GridFour -> {
            val topX = ratios.getOrElse(0) { 0.5f }
            val bottomX = ratios.getOrElse(1) { 0.5f }
            val leftY = ratios.getOrElse(2) { 0.5f }
            val rightY = ratios.getOrElse(3) { 0.5f }
            listOf(
                HandleSpec(0, true, topX, leftY / 2f),
                HandleSpec(1, true, bottomX, leftY + (1f - leftY) / 2f),
                HandleSpec(2, false, topX / 2f, leftY),
                HandleSpec(3, false, topX + (1f - topX) / 2f, rightY)
            )
        }
        LayoutType.TwoTopOneBottom -> {
            val x = ratios.getOrElse(0) { 0.5f }
            val y = ratios.getOrElse(1) { 0.5f }
            listOf(HandleSpec(0, true, x, y / 2f), HandleSpec(1, false, 0.5f, y))
        }
        LayoutType.OneTopTwoBottom -> {
            val x = ratios.getOrElse(0) { 0.5f }
            val y = ratios.getOrElse(1) { 0.5f }
            listOf(HandleSpec(0, true, x, y + (1f - y) / 2f), HandleSpec(1, false, 0.5f, y))
        }
        LayoutType.OneLeftTwoRight -> {
            val x = ratios.getOrElse(0) { 0.5f }
            val y = ratios.getOrElse(1) { 0.5f }
            listOf(HandleSpec(0, true, x, 0.5f), HandleSpec(1, false, x + (1f - x) / 2f, y))
        }
        LayoutType.TwoLeftOneRight -> {
            val x = ratios.getOrElse(0) { 0.5f }
            val y = ratios.getOrElse(1) { 0.5f }
            listOf(HandleSpec(0, true, x, 0.5f), HandleSpec(1, false, x / 2f, y))
        }
    }
}

private data class HandleSpec(
    val ratioIndex: Int,
    val vertical: Boolean,
    val xRatio: Float,
    val yRatio: Float
)

@Preview(widthDp = 360, heightDp = 260, backgroundColor = 0xFF202832, showBackground = true)
@Composable
private fun AdjustableLayoutPreviewPreview() {
    AdjustableLayoutPreview(
        type = LayoutType.OneLeftTwoRight,
        packages = List(3) { "" },
        appsByPackage = emptyMap(),
        ratios = listOf(0.45f, 0.5f),
        onRatiosChanged = {},
        onSlotClicked = {}
    )
}
