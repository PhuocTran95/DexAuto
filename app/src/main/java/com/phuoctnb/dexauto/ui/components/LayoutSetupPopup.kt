package com.phuoctnb.dexauto.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phuoctnb.dexauto.R
import com.phuoctnb.dexauto.data.LaunchableApp
import com.phuoctnb.dexauto.data.LayoutType
import com.phuoctnb.dexauto.data.PanelPosition

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutSetupPopup(
    showPopup: Boolean,
    panelPosition: PanelPosition,
    layoutType: LayoutType?,
    draftSlots: List<String>,
    draftRatios: List<Float>,
    installedApps: List<LaunchableApp>,
    onLayoutTypeSelected: (LayoutType) -> Unit,
    onSlotClicked: (Int) -> Unit,
    onResetDraft: () -> Unit,
    onSave: () -> Boolean,
    onDismissAppPicker: () -> Unit,
    onSelectApp: (LaunchableApp) -> Unit,
    onRatiosChanged: (List<Float>) -> Unit,
    choosingSlot: Int?,
    onInputFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!showPopup) return

    val context = LocalContext.current
    val appsByPackage = remember(installedApps) { installedApps.associateBy { it.packageName } }
    var page by remember(showPopup) { mutableStateOf(LayoutPopupPage.LayoutTypes) }
    var appSearch by remember(showPopup) { mutableStateOf("") }
    val appListScrollState = rememberScrollState()
    val visibleApps = remember(installedApps, appSearch) {
        val query = appSearch.trim().lowercase()
        if (query.isEmpty()) {
            installedApps
        } else {
            installedApps.filter { app ->
                app.label.lowercase().contains(query) || app.packageName.lowercase().contains(query)
            }
        }
    }

    LaunchedEffect(layoutType, choosingSlot) {
        page = when {
            choosingSlot != null -> LayoutPopupPage.Apps
            layoutType != null -> LayoutPopupPage.Slots
            else -> LayoutPopupPage.LayoutTypes
        }
    }

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PopupHeader(
                title = when (page) {
                    LayoutPopupPage.LayoutTypes -> stringResource(R.string.layout_popup_choose_layout)
                    LayoutPopupPage.Slots -> stringResource(R.string.layout_popup_choose_app)
                    LayoutPopupPage.Apps -> stringResource(R.string.layout_popup_apps)
                },
                searchValue = if (page == LayoutPopupPage.Apps) appSearch else null,
                onSearchChanged = { appSearch = it },
                onInputFocusChanged = onInputFocusChanged,
                showBack = page != LayoutPopupPage.LayoutTypes,
                onBack = {
                    when (page) {
                        LayoutPopupPage.Apps -> {
                            onDismissAppPicker()
                            page = LayoutPopupPage.Slots
                        }
                        LayoutPopupPage.Slots -> {
                            onResetDraft()
                            page = LayoutPopupPage.LayoutTypes
                        }
                        LayoutPopupPage.LayoutTypes -> Unit
                    }
                }
            )

            when (page) {
                LayoutPopupPage.LayoutTypes -> LayoutTypesPage(
                    selectedType = layoutType,
                    onLayoutTypeSelected = {
                        onLayoutTypeSelected(it)
                        page = LayoutPopupPage.Slots
                    }
                )

                LayoutPopupPage.Slots -> {
                    if (layoutType != null) {
                        LayoutSlotsPage(
                            type = layoutType,
                            draftSlots = draftSlots,
                            draftRatios = draftRatios,
                            appsByPackage = appsByPackage,
                            onRatiosChanged = onRatiosChanged,
                            onSlotClicked = onSlotClicked,
                            onSave = {
                                if (!onSave()) {
                                    Toast.makeText(context, context.getString(R.string.toast_layout_incomplete), Toast.LENGTH_SHORT).show()
                                } else {
                                    page = LayoutPopupPage.LayoutTypes
                                }
                            }
                        )
                    }
                }

                LayoutPopupPage.Apps -> AppsPickerPage(
                    apps = visibleApps,
                    scrollState = appListScrollState,
                    onSelectApp = {
                        onSelectApp(it)
                        page = LayoutPopupPage.Slots
                    }
                )
            }
        }
    }

}

@Composable
private fun PopupHeader(
    title: String,
    searchValue: String?,
    onSearchChanged: (String) -> Unit,
    onInputFocusChanged: (Boolean) -> Unit,
    showBack: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(70.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            enabled = showBack,
            modifier = Modifier
                .size(width = 44.dp, height = 44.dp)
        ) {
            if (showBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.content_desc_back),
                    tint = Color(0xFF8CC7FF),
                    modifier = Modifier.size(30.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = stringResource(R.string.content_desc_layout),
                    tint = Color(0xFF8CC7FF),
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            modifier = if (searchValue == null) Modifier.weight(1f) else Modifier
        )
        if (searchValue != null) {
            OutlinedTextField(
                value = searchValue,
                onValueChange = onSearchChanged,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.layout_popup_search_hint)) },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onInputFocusChanged(it.isFocused) }
            )
        } else {
            Box(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayoutTypesPage(
    selectedType: LayoutType?,
    onLayoutTypeSelected: (LayoutType) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LayoutType.entries.forEach { type ->
            LayoutTypeButton(
                type = type,
                selected = selectedType == type,
                onClick = { onLayoutTypeSelected(type) }
            )
        }
    }
}

@Composable
private fun ColumnScope.LayoutSlotsPage(
    type: LayoutType,
    draftSlots: List<String>,
    draftRatios: List<Float>,
    appsByPackage: Map<String, LaunchableApp>,
    onRatiosChanged: (List<Float>) -> Unit,
    onSlotClicked: (Int) -> Unit,
    onSave: () -> Unit
) {
    AdjustableLayoutPreview(
        type = type,
        packages = draftSlots,
        appsByPackage = appsByPackage,
        ratios = draftRatios,
        onRatiosChanged = onRatiosChanged,
        onSlotClicked = onSlotClicked,
        modifier = Modifier.fillMaxWidth().weight(1f)
    )
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.layout_popup_save), fontSize = 12.sp)
    }
}

@Composable
private fun AppsPickerPage(
    apps: List<LaunchableApp>,
    scrollState: androidx.compose.foundation.ScrollState,
    onSelectApp: (LaunchableApp) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(apps) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    scrollState.dispatchRawDelta(-dragAmount.y)
                }
            }
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        apps.forEach { app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clickable { onSelectApp(app) }
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(app.icon, app.label, Modifier.size(24.dp))
                Text(app.label, color = Color.White, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

private enum class LayoutPopupPage {
    LayoutTypes,
    Slots,
    Apps
}

@Preview(widthDp = 400, heightDp = 400, backgroundColor = 0xFF101418, showBackground = true)
@Composable
private fun LayoutSetupPopupTypesPreview() {
    LayoutSetupPopup(
        showPopup = true,
        panelPosition = PanelPosition.Left,
        layoutType = null,
        draftSlots = emptyList(),
        draftRatios = emptyList(),
        installedApps = emptyList(),
        onLayoutTypeSelected = {},
        onSlotClicked = {},
        onResetDraft = {},
        onSave = { false },
        onDismissAppPicker = {},
        onSelectApp = {},
        onRatiosChanged = {},
        choosingSlot = null
    )
}

@Preview(widthDp = 400, heightDp = 400, backgroundColor = 0xFF101418, showBackground = true)
@Composable
private fun LayoutSetupPopupSlotsPreview() {
    LayoutSetupPopup(
        showPopup = true,
        panelPosition = PanelPosition.Left,
        layoutType = LayoutType.GridFour,
        draftSlots = List(4) { "" },
        draftRatios = listOf(0.5f, 0.5f),
        installedApps = emptyList(),
        onLayoutTypeSelected = {},
        onSlotClicked = {},
        onResetDraft = {},
        onSave = { false },
        onDismissAppPicker = {},
        onSelectApp = {},
        onRatiosChanged = {},
        choosingSlot = null
    )
}
