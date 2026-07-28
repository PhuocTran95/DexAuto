package com.phuoctnb.dexauto.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phuoctnb.dexauto.R
import com.phuoctnb.dexauto.data.PanelPosition
import com.phuoctnb.dexauto.data.PrivilegedBackend

@Composable
fun SettingsScreen(
    enabled: Boolean,
    level: String,
    backend: PrivilegedBackend,
    panelPosition: PanelPosition,
    preserveDexLayoutEnabled: Boolean,
    horizontalPanel: Boolean,
    onBack: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onLevelChanged: (String) -> Unit,
    onBackendSelected: (PrivilegedBackend) -> Unit,
    onPanelPositionSelected: (PanelPosition) -> Unit,
    onPreserveDexLayoutChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.background(Color(0xFF151A20))) {
        if (horizontalPanel) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsHeader(
                    onBack,
                    horizontalPanel = true,
                    modifier = Modifier.wrapContentWidth()
                )
                Row(
                    Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = stringResource(R.string.settings_panel_position), color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    PanelPositionSelector(
                        panelPosition,
                        onPanelPositionSelected,
                        Modifier
                            .width(120.dp)
                            .fillMaxHeight()
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = stringResource(R.string.settings_config), color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    BackendToggles(
                        backend = backend,
                        horizontalPanel = true,
                        onBackendSelected = onBackendSelected,
                        modifier = Modifier.width(196.dp)
                    )
                    BatterySettings(
                        enabled,
                        onEnabledChanged,
                        onLevelChanged,
                        modifier = Modifier.width(126.dp)
                    )
                    SettingSwitchCard(
                        labelRes = R.string.settings_preserve_dex_layout,
                        checked = preserveDexLayoutEnabled,
                        onCheckedChange = onPreserveDexLayoutChanged,
                        modifier = Modifier.width(150.dp)
                    )
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SettingsHeader(onBack, horizontalPanel = false, modifier = Modifier.fillMaxWidth())
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = stringResource(R.string.settings_panel_position), color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    PanelPositionSelector(
                        panelPosition,
                        onPanelPositionSelected,
                        Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = stringResource(R.string.settings_config), color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    BackendToggles(
                        backend = backend,
                        horizontalPanel = false,
                        onBackendSelected = onBackendSelected,
                        modifier = Modifier.fillMaxWidth()
                    )
                    BatterySettings(
                        enabled,
                        onEnabledChanged,
                        onLevelChanged,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SettingSwitchCard(
                        labelRes = R.string.settings_preserve_dex_layout,
                        checked = preserveDexLayoutEnabled,
                        onCheckedChange = onPreserveDexLayoutChanged,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 100, heightDp = 520, backgroundColor = 0xFF151A20, showBackground = true)
@Composable
private fun SettingsScreenVerticalPreview() {
    SettingsScreen(
        enabled = true,
        level = "100",
        backend = PrivilegedBackend.Shizuku,
        panelPosition = PanelPosition.Left,
        preserveDexLayoutEnabled = true,
        horizontalPanel = false,
        onBack = {},
        onEnabledChanged = {},
        onLevelChanged = {},
        onBackendSelected = {},
        onPanelPositionSelected = {},
        onPreserveDexLayoutChanged = {}
    )
}

@Preview(widthDp = 620, heightDp = 100, backgroundColor = 0xFF151A20, showBackground = true)
@Composable
private fun SettingsScreenHorizontalPreview() {
    SettingsScreen(
        enabled = true,
        level = "80",
        backend = PrivilegedBackend.Root,
        panelPosition = PanelPosition.Top,
        preserveDexLayoutEnabled = true,
        horizontalPanel = true,
        onBack = {},
        onEnabledChanged = {},
        onLevelChanged = {},
        onBackendSelected = {},
        onPanelPositionSelected = {},
        onPreserveDexLayoutChanged = {}
    )
}
