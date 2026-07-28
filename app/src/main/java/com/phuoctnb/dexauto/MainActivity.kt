package com.phuoctnb.dexauto

import android.annotation.SuppressLint
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.phuoctnb.dexauto.system.PanelOverlayService
import com.phuoctnb.dexauto.system.PanelOverlayBounds
import com.phuoctnb.dexauto.system.DexDisplaySelector
import com.phuoctnb.dexauto.data.DexAutoRepository
import com.phuoctnb.dexauto.ui.theme.DexAutoTheme
import com.phuoctnb.dexauto.ui.theme.PanelBackground

class MainActivity : ComponentActivity() {
    private var permissionRefresh by mutableIntStateOf(0)
    private var panelRunning by mutableStateOf(false)
    private var lastResumedOnDexDisplay = false
    private val panelStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PanelOverlayService.ACTION_PANEL_RUNNING_CHANGED -> {
                    panelRunning = intent.getBooleanExtra(
                        PanelOverlayService.EXTRA_PANEL_RUNNING,
                        PanelOverlayService.isPanelRunning
                    )
                }

                PanelOverlayService.ACTION_DEX_EXITED -> finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastResumedOnDexDisplay = savedInstanceState?.getBoolean(
            STATE_LAST_RESUMED_ON_DEX_DISPLAY,
            false
        ) ?: false
        enableEdgeToEdge()
        setContent {
            DexAutoTheme {
                PermissionGate(
                    refreshKey = permissionRefresh,
                    panelRunning = panelRunning,
                    onTogglePanel = ::togglePanelService
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionRefresh++
        panelRunning = PanelOverlayService.isPanelRunning
        val activityDisplay = display
        val activityDisplayId = activityDisplay?.displayId ?: Display.DEFAULT_DISPLAY
        val activityOnDexDisplay = activityDisplay != null &&
            DexDisplaySelector.isDexDesktopDisplay(this, activityDisplayId)
        val returnedToPhoneAfterDex = lastResumedOnDexDisplay && !activityOnDexDisplay
        lastResumedOnDexDisplay = activityOnDexDisplay
        if (returnedToPhoneAfterDex) {
            finishAndRemoveTask()
            return
        }

        val panelOnDifferentDexDisplay = activityOnDexDisplay &&
            PanelOverlayBounds.displayId != activityDisplayId
        if (
            activityOnDexDisplay &&
            Settings.canDrawOverlays(this) &&
            !DexAutoRepository(this).loadPanelAutoStartSuppressed() &&
            (!panelRunning || panelOnDifferentDexDisplay)
        ) {
            startPanelAndHideLauncher()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_LAST_RESUMED_ON_DEX_DISPLAY, lastResumedOnDexDisplay)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            panelStateReceiver,
            IntentFilter().apply {
                addAction(PanelOverlayService.ACTION_PANEL_RUNNING_CHANGED)
                addAction(PanelOverlayService.ACTION_DEX_EXITED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        panelRunning = PanelOverlayService.isPanelRunning
    }

    override fun onStop() {
        runCatching { unregisterReceiver(panelStateReceiver) }
        super.onStop()
    }

    private fun togglePanelService() {
        if (panelRunning) {
            stopPanelService()
        } else {
            startPanelAndHideLauncher()
        }
    }

    private fun startPanelAndHideLauncher() {
        DexAutoRepository(this).savePanelAutoStartSuppressed(false)
        Log.i(TAG, "Starting panel service on DeX/external display")
        ContextCompat.startForegroundService(
            this,
            Intent(this, PanelOverlayService::class.java)
                .putExtra(PanelOverlayService.EXTRA_REQUIRE_EXTERNAL_DISPLAY, true)
                .putExtra(PanelOverlayService.EXTRA_MANUAL_START, true)
        )
    }

    private fun stopPanelService() {
        DexAutoRepository(this).savePanelAutoStartSuppressed(true)
        stopService(Intent(this, PanelOverlayService::class.java))
        panelRunning = false
        Toast.makeText(this, getString(R.string.toast_panel_stop_requested), Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TAG = "DexAutoMain"
        const val STATE_LAST_RESUMED_ON_DEX_DISPLAY = "last_resumed_on_dex_display"
    }
}

@Composable
@SuppressLint("BatteryLife")
private fun PermissionGate(
    refreshKey: Int,
    panelRunning: Boolean,
    onTogglePanel: () -> Unit
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val overlayGranted = Settings.canDrawOverlays(context)
    val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val ignoringBatteryOptimizations = context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true
    val requiredReady = overlayGranted

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .widthIn(min = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = PanelBackground,
                contentColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp).padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.permission_intro))

                PermissionRow(
                    title = stringResource(R.string.permission_overlay_title),
                    description = if (overlayGranted) {
                        stringResource(R.string.permission_overlay_granted)
                    } else {
                        stringResource(R.string.permission_overlay_denied)
                    },
                    granted = overlayGranted,
                    actionLabel = stringResource(R.string.permission_open_settings),
                    onAction = {
                        runCatching {
                            context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                "package:${context.packageName}".toUri()
                            )
                            )
                        }
                    }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionRow(
                        title = stringResource(R.string.permission_notification_title),
                        description = if (notificationGranted) {
                            stringResource(R.string.permission_notification_granted)
                        } else {
                            stringResource(R.string.permission_notification_denied)
                        },
                        granted = notificationGranted,
                        actionLabel = stringResource(R.string.permission_request),
                        onAction = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                }

                PermissionRow(
                    title = stringResource(R.string.permission_battery_title),
                    description = if (ignoringBatteryOptimizations) {
                        stringResource(R.string.permission_battery_granted)
                    } else {
                        stringResource(R.string.permission_battery_denied)
                    },
                    granted = ignoringBatteryOptimizations,
                    actionLabel = stringResource(R.string.permission_request),
                    onAction = {
                        runCatching {
                            context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                "package:${context.packageName}".toUri()
                            )
                            )
                        }
                    }
                )

                Spacer(Modifier.height(4.dp))

                Row {
                    Button(
                        enabled = requiredReady || panelRunning,
                        onClick = onTogglePanel
                    ) {
                        Text(
                            stringResource(
                                if (panelRunning) {
                                    R.string.permission_hide_panel
                                } else {
                                    R.string.permission_show_panel
                                }
                            )
                        )
                    }
                }

                if (!requiredReady) {
                    Text(
                        text = stringResource(R.string.permission_overlay_required),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = stringResource(R.string.author),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    refreshKey.hashCode()
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (granted) {
            Text(
                stringResource(R.string.permission_ok),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        } else {
            OutlinedButton(
                onClick = onAction,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(actionLabel)
            }
        }
    }
}
