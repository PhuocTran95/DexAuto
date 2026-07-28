package com.phuoctnb.dexauto.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phuoctnb.dexauto.R

@Composable
fun SettingsHeader(
    onBack: () -> Unit,
    horizontalPanel: Boolean,
    modifier: Modifier = Modifier
) {
    if (horizontalPanel) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsBackButton(onBack)
            Text(stringResource(R.string.settings_header_system), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SettingsBackButton(onBack)
            Text(stringResource(R.string.settings_header_system), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingsBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        modifier = Modifier
            .size(width = 44.dp, height = 44.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.content_desc_back),
            tint = Color(0xFF8CC7FF),
            modifier = Modifier.size(30.dp)
        )
    }
}
