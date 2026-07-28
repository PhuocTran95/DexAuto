package com.phuoctnb.dexauto.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.phuoctnb.dexauto.R

@Composable
fun BatterySettings(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onLevelChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingSwitchCard(
        labelRes = R.string.settings_fixed_pin,
        checked = enabled,
        onCheckedChange = { checked ->
            if (checked) {
                onLevelChanged("100")
            }
            onEnabledChanged(checked)
        },
        modifier = modifier
    )
}
