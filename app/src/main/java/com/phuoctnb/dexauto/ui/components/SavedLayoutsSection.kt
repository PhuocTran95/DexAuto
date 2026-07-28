package com.phuoctnb.dexauto.ui.components

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.phuoctnb.dexauto.R
import com.phuoctnb.dexauto.data.LaunchableApp
import com.phuoctnb.dexauto.data.LayoutType
import com.phuoctnb.dexauto.data.SavedLayout

@Composable
fun SavedLayoutsSection(
    savedLayouts: List<SavedLayout>,
    installedApps: List<LaunchableApp>,
    onLaunch: (SavedLayout) -> Unit,
    onDelete: (SavedLayout) -> Unit,
    horizontal: Boolean,
    modifier: Modifier = Modifier
) {
    val appsByPackage = remember(installedApps) { installedApps.associateBy { it.packageName } }
    val scrollState = rememberScrollState()
    var deleteLayoutId by remember { mutableStateOf<String?>(null) }
    val canScrollBackward = scrollState.value > 0
    val canScrollForward = scrollState.value < scrollState.maxValue

    if (horizontal) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScrollButton(
                direction = ScrollButtonDirection.Left,
                enabled = canScrollBackward,
                onClick = { scrollState.dispatchRawDelta(-112f) }
            )
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                savedLayouts.forEach { layout ->
                    SavedLayoutCell(
                        layout = layout,
                        appsByPackage = appsByPackage,
                        onLaunch = { selectedLayout ->
                            if (deleteLayoutId != null) {
                                deleteLayoutId = null
                            } else {
                                onLaunch(selectedLayout)
                            }
                        },
                        onDelete = {
                            onDelete(layout)
                            deleteLayoutId = null
                        },
                        showDelete = deleteLayoutId == layout.id,
                        onShowDelete = { deleteLayoutId = layout.id },
                        modifier = Modifier.width(96.dp).fillMaxHeight()
                    )
                }
            }
            ScrollButton(
                direction = ScrollButtonDirection.Right,
                enabled = canScrollForward,
                onClick = { scrollState.dispatchRawDelta(112f) }
            )
        }
    } else {
        Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ScrollButton(
                direction = ScrollButtonDirection.Up,
                enabled = canScrollBackward,
                onClick = { scrollState.dispatchRawDelta(-88f) }
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                savedLayouts.forEach { layout ->
                    SavedLayoutCell(
                        layout = layout,
                        appsByPackage = appsByPackage,
                        onLaunch = { selectedLayout ->
                            if (deleteLayoutId != null) {
                                deleteLayoutId = null
                            } else {
                                onLaunch(selectedLayout)
                            }
                        },
                        onDelete = {
                            onDelete(layout)
                            deleteLayoutId = null
                        },
                        showDelete = deleteLayoutId == layout.id,
                        onShowDelete = { deleteLayoutId = layout.id },
                        modifier = Modifier.fillMaxWidth().height(76.dp)
                    )
                }
            }
            ScrollButton(
                direction = ScrollButtonDirection.Down,
                enabled = canScrollForward,
                onClick = { scrollState.dispatchRawDelta(88f) }
            )
        }
    }
}

@Composable
private fun SavedLayoutCell(
    layout: SavedLayout,
    appsByPackage: Map<String, LaunchableApp>,
    onLaunch: (SavedLayout) -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean,
    onShowDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF202832))
    ) {
        Box(Modifier.fillMaxSize().padding(5.dp)) {
            LayoutPreview(
                type = layout.type,
                packages = layout.packages,
                appsByPackage = appsByPackage,
                ratios = layout.ratios,
                compact = true,
                fixedHeight = false,
                showOuterBorder = false,
                modifier = Modifier.fillMaxSize(),
                onSlotClicked = null
            )
            if (pressed) {
                Box(Modifier.fillMaxSize().background(Color(0x338CC7FF), RoundedCornerShape(6.dp)))
            }
            if (showDelete) {
                SavedLayoutTouchLayer(
                    layout = layout,
                    onTap = onLaunch,
                    onLongPress = onShowDelete,
                    onPressedChanged = { pressed = it }
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(38.dp)
                        .background(Color(0xFFFFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.content_desc_delete_layout),
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                SavedLayoutTouchLayer(
                    layout = layout,
                    onTap = onLaunch,
                    onLongPress = onShowDelete,
                    onPressedChanged = { pressed = it }
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SavedLayoutTouchLayer(
    layout: SavedLayout,
    onTap: (SavedLayout) -> Unit,
    onLongPress: () -> Unit,
    onPressedChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    val touchSlop = remember(context) { ViewConfiguration.get(context).scaledTouchSlop }
    val longPressTimeout = remember { ViewConfiguration.getLongPressTimeout().toLong() }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    var downX by remember { mutableFloatStateOf(0f) }
    var downY by remember { mutableFloatStateOf(0f) }
    var moved by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }
    val longPressRunnable = remember(layout.id) {
        Runnable {
            longPressed = true
            onPressedChanged(false)
            currentOnLongPress()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        moved = false
                        longPressed = false
                        handler.removeCallbacks(longPressRunnable)
                        handler.postDelayed(longPressRunnable, longPressTimeout)
                        onPressedChanged(true)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if ((dx * dx + dy * dy) > touchSlop * touchSlop) {
                            moved = true
                            handler.removeCallbacks(longPressRunnable)
                            onPressedChanged(false)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(longPressRunnable)
                        onPressedChanged(false)
                        if (!moved && !longPressed) {
                            currentOnTap(layout)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPressRunnable)
                        onPressedChanged(false)
                        true
                    }
                    else -> false
                }
            }
    )
}

private enum class ScrollButtonDirection {
    Left,
    Right,
    Up,
    Down
}

@Composable
private fun ScrollButton(
    direction: ScrollButtonDirection,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = when (direction) {
            ScrollButtonDirection.Left, ScrollButtonDirection.Right -> Modifier.size(width = 34.dp, height = 64.dp)
            ScrollButtonDirection.Up, ScrollButtonDirection.Down -> Modifier.size(width = 64.dp, height = 30.dp)
        }
            .background(
                if (enabled && pressed) Color(0x338CC7FF) else Color.Transparent,
                RoundedCornerShape(7.dp)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (direction) {
                ScrollButtonDirection.Left -> Icons.Default.ChevronLeft
                ScrollButtonDirection.Right -> Icons.Default.ChevronRight
                ScrollButtonDirection.Up -> Icons.Default.KeyboardArrowUp
                ScrollButtonDirection.Down -> Icons.Default.KeyboardArrowDown
            },
            contentDescription = when (direction) {
                ScrollButtonDirection.Left -> stringResource(R.string.content_desc_scroll_left)
                ScrollButtonDirection.Right -> stringResource(R.string.content_desc_scroll_right)
                ScrollButtonDirection.Up -> stringResource(R.string.content_desc_scroll_up)
                ScrollButtonDirection.Down -> stringResource(R.string.content_desc_scroll_down)
            },
            tint = if (enabled) Color(0xFF8CC7FF) else Color(0xFF52606D),
            modifier = Modifier.size(30.dp)
        )
    }
}

@Preview(widthDp = 100, heightDp = 320, backgroundColor = 0xFF151A20, showBackground = true)
@Composable
private fun SavedLayoutsSectionVerticalPreview() {
    SavedLayoutsSection(
        savedLayouts = previewSavedLayouts(),
        installedApps = emptyList(),
        onLaunch = {},
        onDelete = {},
        horizontal = false,
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(widthDp = 420, heightDp = 100, backgroundColor = 0xFF151A20, showBackground = true)
@Composable
private fun SavedLayoutsSectionHorizontalPreview() {
    SavedLayoutsSection(
        savedLayouts = previewSavedLayouts(),
        installedApps = emptyList(),
        onLaunch = {},
        onDelete = {},
        horizontal = true,
        modifier = Modifier.fillMaxSize()
    )
}

private fun previewSavedLayouts(): List<SavedLayout> {
    return listOf(
        SavedLayout("1", LayoutType.GridFour, List(4) { "" }),
        SavedLayout("2", LayoutType.OneLeftTwoRight, List(3) { "" }),
        SavedLayout("3", LayoutType.SplitTwoRows, List(2) { "" }),
        SavedLayout("4", LayoutType.SplitFourColumns, List(4) { "" })
    )
}
