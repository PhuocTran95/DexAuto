package com.phuoctnb.dexauto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.phuoctnb.dexauto.R
import com.phuoctnb.dexauto.data.MainUiState
import com.phuoctnb.dexauto.data.PanelPosition
import com.phuoctnb.dexauto.data.PrivilegedBackend
import com.phuoctnb.dexauto.data.SavedLayout
import com.phuoctnb.dexauto.ui.components.AddLayoutButton
import com.phuoctnb.dexauto.ui.components.DateTimeSection
import com.phuoctnb.dexauto.ui.components.HeaderSection
import com.phuoctnb.dexauto.ui.components.PanelIconButton
import com.phuoctnb.dexauto.ui.components.SavedLayoutsSection
import com.phuoctnb.dexauto.ui.settings.SettingsScreen
import com.phuoctnb.dexauto.ui.theme.PanelBackground

@Composable
fun DexAutoScreen(
    state: MainUiState,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onStopPanel: () -> Unit,
    onOpenRestScreen: () -> Unit,
    onPaymentPopupChanged: (Boolean) -> Unit,
    onPanelCollapsedChanged: (Boolean) -> Unit,
    onLayoutPopupChanged: (Boolean) -> Unit,
    onLaunchLayout: (SavedLayout) -> Unit,
    onDeleteLayout: (SavedLayout) -> Unit,
    onBatteryFixChanged: (Boolean) -> Unit,
    onBatteryLevelChanged: (String) -> Unit,
    onPrivilegedBackendSelected: (PrivilegedBackend) -> Unit,
    onPanelPositionSelected: (PanelPosition) -> Unit,
    onPreserveDexLayoutChanged: (Boolean) -> Unit
) {
    val horizontalPanel =
        state.panelPosition == PanelPosition.Top || state.panelPosition == PanelPosition.Bottom

    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = if (state.panelCollapsed) Color.Transparent else PanelBackground
    ) {
        if (state.panelCollapsed) {
            CollapsedPanelTab(
                panelPosition = state.panelPosition,
                onExpand = { onPanelCollapsedChanged(false) }
            )
        } else if (state.showSettings) {
            SettingsScreen(
                enabled = state.batteryFixEnabled,
                level = state.batteryLevelText,
                backend = state.privilegedBackend,
                panelPosition = state.panelPosition,
                preserveDexLayoutEnabled = state.preserveDexLayoutEnabled,
                horizontalPanel = horizontalPanel,
                onBack = onCloseSettings,
                onEnabledChanged = onBatteryFixChanged,
                onLevelChanged = onBatteryLevelChanged,
                onBackendSelected = onPrivilegedBackendSelected,
                onPanelPositionSelected = onPanelPositionSelected,
                onPreserveDexLayoutChanged = onPreserveDexLayoutChanged
            )
        } else {
            PanelScaffold(
                state = state,
                horizontalPanel = horizontalPanel,
                onOpenSettings = onOpenSettings,
                onStopPanel = onStopPanel,
                onOpenRestScreen = onOpenRestScreen,
                onPaymentPopupChanged = onPaymentPopupChanged,
                onPanelCollapsedChanged = onPanelCollapsedChanged,
                onLayoutPopupChanged = onLayoutPopupChanged,
                onLaunchLayout = onLaunchLayout,
                onDeleteLayout = onDeleteLayout
            )
        }
    }
}

@Composable
private fun PanelScaffold(
    state: MainUiState,
    horizontalPanel: Boolean,
    onOpenSettings: () -> Unit,
    onStopPanel: () -> Unit,
    onOpenRestScreen: () -> Unit,
    onPaymentPopupChanged: (Boolean) -> Unit,
    onPanelCollapsedChanged: (Boolean) -> Unit,
    onLayoutPopupChanged: (Boolean) -> Unit,
    onLaunchLayout: (SavedLayout) -> Unit,
    onDeleteLayout: (SavedLayout) -> Unit
) {
    val modifier = if (horizontalPanel) {
        Modifier
            .fillMaxWidth()
            .height(100.dp)
    } else {
        Modifier
            .width(100.dp)
            .fillMaxHeight()
    }
    PanelContent(
        state,
        horizontalPanel,
        onOpenSettings,
        onStopPanel,
        onOpenRestScreen,
        onPaymentPopupChanged,
        onPanelCollapsedChanged,
        onLayoutPopupChanged,
        onLaunchLayout,
        onDeleteLayout,
        modifier
    )
}

@Composable
private fun PanelContent(
    state: MainUiState,
    horizontalPanel: Boolean,
    onOpenSettings: () -> Unit,
    onStopPanel: () -> Unit,
    onOpenRestScreen: () -> Unit,
    onPaymentPopupChanged: (Boolean) -> Unit,
    onPanelCollapsedChanged: (Boolean) -> Unit,
    onLayoutPopupChanged: (Boolean) -> Unit,
    onLaunchLayout: (SavedLayout) -> Unit,
    onDeleteLayout: (SavedLayout) -> Unit,
    modifier: Modifier
) {
    Box(modifier.background(Color(0xFF151A20))) {
        val baseModifier = Modifier
            .fillMaxSize()
            .padding(6.dp)
        if (horizontalPanel) {
            Row(
                baseModifier,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderSection()
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PanelIconButton(
                        Icons.Default.PowerSettingsNew,
                        Color(0xFFFFB4AB),
                        stringResource(R.string.content_desc_power_panel),
                        onStopPanel,
                    )
                    PanelIconButton(
                        Icons.Default.Settings,
                        Color(0xFF8CC7FF),
                        stringResource(R.string.content_desc_settings),
                        onOpenSettings,
                    )
                    PanelIconButton(
                        Icons.Default.Snooze,
                        Color(0xFFC8B8F0),
                        stringResource(R.string.content_desc_rest_screen),
                        onOpenRestScreen,
                    )
                    PanelIconButton(
                        if (state.showPaymentPopup) Icons.Default.Close else Icons.Default.QrCode2,
                        Color(0xFF8FD5A6),
                        stringResource(
                            if (state.showPaymentPopup) {
                                R.string.content_desc_close_payment_qr
                            } else {
                                R.string.content_desc_open_payment_qr
                            }
                        ),
                        { onPaymentPopupChanged(!state.showPaymentPopup) },
                    )
                }
                Box(Modifier.width(150.dp)) {
                    DateTimeSection(state.nowMillis)
                }
                AddLayoutButton(
                    state.showLayoutPopup,
                    { onLayoutPopupChanged(!state.showLayoutPopup) })
                SavedLayoutsSection(
                    savedLayouts = state.savedLayouts,
                    installedApps = state.installedApps,
                    onLaunch = onLaunchLayout,
                    onDelete = onDeleteLayout,
                    horizontal = true,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(end = 10.dp)
                )
            }
        } else {
            Column(
                baseModifier,
                verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.size(10.dp))
                HeaderSection()
                Spacer(Modifier.size(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanelIconButton(
                        Icons.Default.PowerSettingsNew,
                        Color(0xFFFFB4AB),
                        stringResource(R.string.content_desc_power_panel),
                        onStopPanel
                    )
                    PanelIconButton(
                        Icons.Default.Settings,
                        Color(0xFF8CC7FF),
                        stringResource(R.string.content_desc_settings),
                        onOpenSettings
                    )
                    PanelIconButton(
                        Icons.Default.Bedtime,
                        Color(0xFFC8B8F0),
                        stringResource(R.string.content_desc_rest_screen),
                        onOpenRestScreen
                    )
                    PanelIconButton(
                        if (state.showPaymentPopup) Icons.Default.Close else Icons.Default.QrCode2,
                        Color(0xFF8FD5A6),
                        stringResource(
                            if (state.showPaymentPopup) {
                                R.string.content_desc_close_payment_qr
                            } else {
                                R.string.content_desc_open_payment_qr
                            }
                        ),
                        { onPaymentPopupChanged(!state.showPaymentPopup) }
                    )
                }
                Spacer(Modifier.size(10.dp))
                DateTimeSection(state.nowMillis)
                Spacer(Modifier.size(10.dp))
                AddLayoutButton(
                    state.showLayoutPopup,
                    { onLayoutPopupChanged(!state.showLayoutPopup) })
                SavedLayoutsSection(
                    savedLayouts = state.savedLayouts,
                    installedApps = state.installedApps,
                    onLaunch = onLaunchLayout,
                    onDelete = onDeleteLayout,
                    horizontal = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 10.dp)
                )
            }
        }
        CollapsePanelEdgeButton(
            panelPosition = state.panelPosition,
            onCollapse = { onPanelCollapsedChanged(true) }
        )
    }
}

@Composable
private fun BoxScope.CollapsePanelEdgeButton(
    panelPosition: PanelPosition,
    onCollapse: () -> Unit
) {
    val alignment = when (panelPosition) {
        PanelPosition.Left -> Alignment.CenterEnd
        PanelPosition.Right -> Alignment.CenterStart
        PanelPosition.Top -> Alignment.BottomCenter
        PanelPosition.Bottom -> Alignment.TopCenter
    }
    Box(
        modifier = Modifier
            .then(
                when (panelPosition) {
                    PanelPosition.Left, PanelPosition.Right -> Modifier.size(
                        width = 18.dp,
                        height = 48.dp
                    )

                    PanelPosition.Top, PanelPosition.Bottom -> Modifier.size(
                        width = 48.dp,
                        height = 18.dp
                    )
                }
            )
            .align(alignment)
    ) {
        PanelEdgeTabButton(
            panelPosition = panelPosition,
            expanded = true,
            contentDescription = stringResource(R.string.content_desc_collapse_panel),
            onClick = onCollapse
        )
    }
}

@Composable
private fun CollapsedPanelTab(
    panelPosition: PanelPosition,
    onExpand: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        PanelEdgeTabButton(
            panelPosition = panelPosition,
            expanded = false,
            contentDescription = stringResource(R.string.content_desc_expand_panel),
            onClick = onExpand
        )
    }
}

@Composable
private fun PanelEdgeTabButton(
    panelPosition: PanelPosition,
    expanded: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (pressed) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.5f), shape)
            .border(1.dp, Color.Black.copy(alpha = if (pressed) 0.75f else 0.5f), shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (panelPosition) {
                PanelPosition.Left -> if (expanded) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight
                PanelPosition.Right -> if (expanded) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft
                PanelPosition.Top -> if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
                PanelPosition.Bottom -> if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp
            },
            contentDescription = contentDescription,
            tint = Color.Black,
            modifier = Modifier.size(if (expanded) 20.dp else 28.dp)
        )
    }
}
