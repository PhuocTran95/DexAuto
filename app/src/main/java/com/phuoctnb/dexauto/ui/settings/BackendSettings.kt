package com.phuoctnb.dexauto.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phuoctnb.dexauto.R
import com.phuoctnb.dexauto.data.PrivilegedBackend
import com.phuoctnb.dexauto.data.rootEnabled
import com.phuoctnb.dexauto.data.shizukuEnabled
import com.phuoctnb.dexauto.data.withRootEnabled
import com.phuoctnb.dexauto.data.withShizukuEnabled

@Composable
fun BackendToggles(
    backend: PrivilegedBackend,
    horizontalPanel: Boolean,
    onBackendSelected: (PrivilegedBackend) -> Unit,
    modifier: Modifier = Modifier
) {
    val onRootChanged: (Boolean) -> Unit = { checked ->
        onBackendSelected(backend.withRootEnabled(checked))
    }
    val onShizukuChanged: (Boolean) -> Unit = { checked ->
        onBackendSelected(backend.withShizukuEnabled(checked))
    }

    if (horizontalPanel) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BackendSwitchCard(
                labelRes = R.string.settings_root,
                checked = backend.rootEnabled,
                onCheckedChange = onRootChanged,
                modifier = Modifier.weight(1f)
            )
            BackendSwitchCard(
                labelRes = R.string.settings_shizuku,
                checked = backend.shizukuEnabled,
                onCheckedChange = onShizukuChanged,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BackendSwitchCard(
                labelRes = R.string.settings_root,
                checked = backend.rootEnabled,
                onCheckedChange = onRootChanged
            )
            BackendSwitchCard(
                labelRes = R.string.settings_shizuku,
                checked = backend.shizukuEnabled,
                onCheckedChange = onShizukuChanged
            )
        }
    }
}

@Composable
private fun BackendSwitchCard(
    labelRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingSwitchCard(
        labelRes = labelRes,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.fillMaxWidth()
    )
}
