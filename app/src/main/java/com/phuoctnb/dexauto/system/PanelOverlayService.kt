package com.phuoctnb.dexauto.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.phuoctnb.dexauto.R
import com.phuoctnb.dexauto.data.DexAutoRepository
import com.phuoctnb.dexauto.data.LayoutType
import com.phuoctnb.dexauto.data.MainUiState
import com.phuoctnb.dexauto.data.PanelPosition
import com.phuoctnb.dexauto.data.PrivilegedBackend
import com.phuoctnb.dexauto.data.SavedLayout
import com.phuoctnb.dexauto.data.rootEnabled
import com.phuoctnb.dexauto.data.shizukuEnabled
import com.phuoctnb.dexauto.data.withRootEnabled
import com.phuoctnb.dexauto.data.withShizukuEnabled
import com.phuoctnb.dexauto.payment.PaymentQrConfig
import com.phuoctnb.dexauto.ui.components.LayoutSetupPopup
import com.phuoctnb.dexauto.ui.components.PaymentQrPopup
import com.phuoctnb.dexauto.ui.screens.DexAutoScreen
import com.phuoctnb.dexauto.ui.screens.RestScreenOverlay
import com.phuoctnb.dexauto.ui.theme.DexAutoTheme
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

class PanelOverlayService : LifecycleService(), SavedStateRegistryOwner {
    private val handler = Handler(Looper.getMainLooper())
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private lateinit var windowManager: WindowManager
    private lateinit var displayManager: DisplayManager
    private lateinit var overlayContext: Context
    private lateinit var repository: DexAutoRepository
    private lateinit var commandRunner: PrivilegedCommandRunner
    private lateinit var layoutLauncher: LayoutLauncher
    private lateinit var batteryController: BatteryController
    private lateinit var sessionLayoutController: DexSessionLayoutController
    private var panelView: ComposeView? = null
    private var popupView: ComposeView? = null
    private var restScreenView: ComposeView? = null
    private var popupInputFocused = false
    private var currentDisplayId: Int? = null
    private var panelTransitionRunnable: Runnable? = null
    private var privilegedBackendRequestId = 0
    private var pendingShizukuBackendRequestId: Int? = null
    private var preserveLayoutRequestId = 0
    private var autoStartRetryRunnable: Runnable? = null
    private var panelDrawTimeoutRunnable: Runnable? = null
    private var startupBatteryFixRunnable: Runnable? = null
    private var sessionRestoreRunnable: Runnable? = null
    private var layoutRefreshRunnable: Runnable? = null
    private var layoutRefreshAttempt = 0
    private var overlayDensity = 1f
    private var captureInProgress = false
    private var stopAfterCapture = false
    private var restoreSessionAfterPanelDraw = false
    private val shizukuPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            handleShizukuPermissionResult(requestCode, grantResult)
        }
    private var state by mutableStateOf(MainUiState())

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val clockRunnable = object : Runnable {
        override fun run() {
            state = state.copy(nowMillis = System.currentTimeMillis())
            handler.postDelayed(this, 1_000L)
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) {
            if (displayId == currentDisplayId) {
                scheduleOverlayLayoutRefresh("display removed")
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == currentDisplayId) {
                scheduleOverlayLayoutRefresh("display changed")
            }
        }
    }

    override fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        displayManager = getSystemService(DisplayManager::class.java) ?: run {
            Log.w(TAG, "DisplayManager is unavailable; stopping panel service")
            stopSelf()
            return
        }
        displayManager.registerDisplayListener(displayListener, handler)
        repository = DexAutoRepository(this)
        commandRunner = PrivilegedCommandRunner(this)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener, handler)
        layoutLauncher = LayoutLauncher(this, commandRunner)
        batteryController = BatteryController(this, commandRunner)
        sessionLayoutController = DexSessionLayoutController(
            this,
            repository,
            commandRunner,
            layoutLauncher
        )
        state = loadState()
        restoreShizukuBackendIfAvailable()
        enforceBackendDependentSettings()
        validatePreserveLayoutBackendOnStartup()
        startPanelForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_CAPTURE_LAYOUT_AND_STOP) {
            captureCurrentLayoutAndStop()
            return START_NOT_STICKY
        }
        if (Settings.canDrawOverlays(this)) {
            val targetDisplayId = intent?.getIntExtra(EXTRA_TARGET_DISPLAY_ID, Display.INVALID_DISPLAY)
                ?.takeIf { it != Display.INVALID_DISPLAY }
            val requireExternalDisplay = intent == null ||
                intent.getBooleanExtra(EXTRA_REQUIRE_EXTERNAL_DISPLAY, false)
            val autoStartAttempt = intent?.getIntExtra(EXTRA_AUTOSTART_ATTEMPT, 0) ?: 0
            val manualStart = intent?.getBooleanExtra(EXTRA_MANUAL_START, false) == true
            if (requireExternalDisplay) {
                restoreSessionAfterPanelDraw = true
                showOverlayWhenExternalDisplayReady(autoStartAttempt, manualStart)
            } else {
                cancelAutoStartRetry()
                if (!showOverlay(targetDisplayId, notifyFailures = true)) {
                    stopSelf()
                }
            }
            handler.removeCallbacks(clockRunnable)
            handler.post(clockRunnable)
        } else {
            Toast.makeText(this, getString(R.string.toast_overlay_permission_required), Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scheduleOverlayLayoutRefresh("configuration changed")
    }

    override fun onDestroy() {
        preserveLayoutRequestId++
        privilegedBackendRequestId++
        pendingShizukuBackendRequestId = null
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
        handler.removeCallbacks(clockRunnable)
        panelTransitionRunnable?.let { handler.removeCallbacks(it) }
        panelTransitionRunnable = null
        autoStartRetryRunnable?.let { handler.removeCallbacks(it) }
        autoStartRetryRunnable = null
        panelDrawTimeoutRunnable?.let { handler.removeCallbacks(it) }
        panelDrawTimeoutRunnable = null
        startupBatteryFixRunnable?.let { handler.removeCallbacks(it) }
        startupBatteryFixRunnable = null
        sessionRestoreRunnable?.let { handler.removeCallbacks(it) }
        sessionRestoreRunnable = null
        layoutRefreshRunnable?.let { handler.removeCallbacks(it) }
        layoutRefreshRunnable = null
        if (::displayManager.isInitialized) {
            displayManager.unregisterDisplayListener(displayListener)
        }
        if (::windowManager.isInitialized) {
            removeRestScreenView()
            panelView?.let { runCatching { windowManager.removeView(it) } }
            popupView?.let { runCatching { windowManager.removeView(it) } }
        }
        panelView = null
        popupView = null
        popupInputFocused = false
        setPanelRunning(false)
        PanelOverlayBounds.bounds = null
        PanelOverlayBounds.displayId = null
        super.onDestroy()
    }

    private fun showOverlayWhenExternalDisplayReady(attempt: Int, manualStart: Boolean) {
        val currentPanelDisplay = currentDisplayId
            ?.let { DexDisplaySelector.displayById(this, it) }
            ?.takeIf { DexDisplaySelector.isDexDesktopDisplay(this, it.displayId) }
        if (panelView != null && currentPanelDisplay != null) {
            cancelAutoStartRetry()
            Log.i(
                TAG,
                "Panel already rendered on logical DeX display id=${currentPanelDisplay.displayId}; ignoring auto-start retry"
            )
            return
        }

        val dexState = SamsungDexStateProvider.currentState(this)
        if (dexState != null && (!dexState.enabled || !dexState.dualMode)) {
            Log.i(TAG, "Samsung DeX desktop is not ready: state=$dexState")
            scheduleAutoStartRetry(attempt)
            return
        }
        val targetDisplay = DexDisplaySelector.dexOrExternalDisplay(this)

        if (targetDisplay == null) {
            scheduleAutoStartRetry(attempt)
            return
        }
        if (!manualStart && attempt < MIN_AUTOSTART_SETTLE_ATTEMPTS) {
            scheduleAutoStartRetry(attempt)
            return
        }
        val shown = showOverlay(targetDisplay.displayId, notifyFailures = attempt >= MAX_AUTOSTART_ATTEMPTS)
        if (!shown) {
            scheduleAutoStartRetry(attempt)
        }
    }

    private fun scheduleAutoStartRetry(attempt: Int) {
        if (attempt >= MAX_AUTOSTART_ATTEMPTS) {
            Log.w(TAG, "Auto-start panel failed: DeX display was not ready after $attempt attempts")
            Toast.makeText(this, getString(R.string.toast_no_dex_display), Toast.LENGTH_LONG).show()
            setPanelRunning(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val nextAttempt = attempt + 1
        val delayMs = if (attempt == 0) {
            AUTOSTART_INITIAL_DELAY_MS
        } else {
            AUTOSTART_RETRY_DELAYS_MS.getOrElse(attempt - 1) { AUTOSTART_RETRY_DELAYS_MS.last() }
        }
        autoStartRetryRunnable?.let { handler.removeCallbacks(it) }
        autoStartRetryRunnable = Runnable {
            startService(
                Intent(this, PanelOverlayService::class.java)
                    .putExtra(EXTRA_REQUIRE_EXTERNAL_DISPLAY, true)
                    .putExtra(EXTRA_AUTOSTART_ATTEMPT, nextAttempt)
            )
        }.also { handler.postDelayed(it, delayMs) }
        Log.i(TAG, "Waiting for DeX display before showing panel, attempt=$nextAttempt delayMs=$delayMs")
    }

    private fun cancelAutoStartRetry() {
        autoStartRetryRunnable?.let { handler.removeCallbacks(it) }
        autoStartRetryRunnable = null
    }

    private fun showOverlay(preferredDisplayId: Int? = null, notifyFailures: Boolean): Boolean {
        val targetDisplay = preferredDisplayId
            ?.let { DexDisplaySelector.displayById(this, it) }
            ?.takeIf { DexDisplaySelector.isDexDesktopDisplay(this, it.displayId) }
            ?: DexDisplaySelector.dexOrExternalDisplay(this)
            ?: run {
                if (notifyFailures) {
                    Toast.makeText(this, getString(R.string.toast_no_dex_display), Toast.LENGTH_LONG).show()
                }
                return false
            }

        if (panelView != null && currentDisplayId == targetDisplay.displayId) {
            panelView?.let {
                updateViewLayoutSafely(it, overlayLayoutParams(state.panelPosition, state.panelCollapsed), "panel")
            }
            return true
        }

        removeRestScreenView()
        panelView?.let { runCatching { windowManager.removeView(it) } }
        popupView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        popupView = null
        popupInputFocused = false
        currentDisplayId = targetDisplay.displayId
        overlayContext = createDisplayContext(targetDisplay)
        overlayDensity = overlayContext.resources.displayMetrics.density
        windowManager = overlayContext.getSystemService(WindowManager::class.java) ?: run {
            Log.w(TAG, "WindowManager is unavailable for display id=${targetDisplay.displayId}")
            return false
        }
        PanelOverlayBounds.displayId = targetDisplay.displayId
        Log.i(TAG, "Showing panel on display id=${targetDisplay.displayId}, name=${targetDisplay.name}")

        val composeView = ComposeView(overlayContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@PanelOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PanelOverlayService)
            setContent {
                DexAutoTheme {
                    DexAutoScreen(
                        state = state,
                        onOpenSettings = {
                            popupInputFocused = false
                            state = state.copy(
                                showSettings = true,
                                showLayoutPopup = false,
                                showPaymentPopup = false,
                                choosingSlot = null
                            )
                            updatePanelOverlayLayout()
                            updatePopupOverlay()
                        },
                        onCloseSettings = { state = state.copy(showSettings = false) },
                        onStopPanel = ::stopPanelManually,
                        onOpenRestScreen = ::showRestScreen,
                        onPaymentPopupChanged = ::setPaymentPopupVisible,
                        onPanelCollapsedChanged = ::setPanelCollapsed,
                        onLayoutPopupChanged = ::setLayoutPopupVisible,
                        onLaunchLayout = ::launchSavedLayout,
                        onDeleteLayout = ::deleteLayout,
                        onBatteryFixChanged = ::setBatteryFixEnabled,
                        onBatteryLevelChanged = {
                            val levelText = it.filter(Char::isDigit).take(3)
                            repository.saveBatteryLevel(levelText.toIntOrNull() ?: 100)
                            state = state.copy(batteryLevelText = levelText)
                        },
                        onPrivilegedBackendSelected = ::selectPrivilegedBackend,
                        onPanelPositionSelected = ::selectPanelPosition,
                        onPreserveDexLayoutChanged = ::setPreserveDexLayoutEnabled
                    )
                }
            }
        }
        panelView = composeView
        try {
            confirmPanelAfterFirstDraw(composeView, targetDisplay.displayId)
            windowManager.addView(composeView, overlayLayoutParams(state.panelPosition, state.panelCollapsed))
            updatePopupOverlay()
            return true
        } catch (error: WindowManager.BadTokenException) {
            Log.w(TAG, "Unable to create overlay", error)
            if (notifyFailures) {
                Toast.makeText(this, getString(R.string.toast_overlay_create_failed), Toast.LENGTH_LONG).show()
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Overlay permission rejected by WindowManager", error)
            if (notifyFailures) {
                Toast.makeText(this, getString(R.string.toast_overlay_security_failed), Toast.LENGTH_LONG).show()
            }
        }
        panelView = null
        setPanelRunning(false)
        return false
    }

    private fun selectLayoutType(type: LayoutType) {
        state = if (state.currentLayoutType == type) {
            state.copy(currentLayoutType = null, draftSlots = emptyList(), draftRatios = emptyList(), choosingSlot = null)
        } else {
            state.copy(
                currentLayoutType = type,
                draftSlots = List(type.slotCount) { index -> state.draftSlots.getOrNull(index).orEmpty() },
                draftRatios = com.phuoctnb.dexauto.data.defaultRatiosFor(type),
                choosingSlot = null
            )
        }
    }

    private fun selectAppForSlot(packageName: String) {
        val slot = state.choosingSlot ?: return
        state = state.copy(
            draftSlots = state.draftSlots.toMutableList().also { it[slot] = packageName },
            choosingSlot = null
        )
    }

    private fun resetDraftLayout() {
        state = state.copy(
            currentLayoutType = null,
            draftSlots = emptyList(),
            draftRatios = emptyList(),
            choosingSlot = null
        )
    }

    private fun saveCurrentLayout(): Boolean {
        val type = state.currentLayoutType ?: return false
        if (state.draftSlots.any { it.isBlank() }) return false
        val layout = SavedLayout(
            id = System.currentTimeMillis().toString(),
            type = type,
            packages = state.draftSlots,
            ratios = state.draftRatios.ifEmpty { com.phuoctnb.dexauto.data.defaultRatiosFor(type) }
        )
        val layouts = listOf(layout) + state.savedLayouts
        repository.saveLayouts(layouts)
        state = state.copy(
            savedLayouts = layouts,
            currentLayoutType = null,
            draftSlots = emptyList(),
            draftRatios = emptyList(),
            choosingSlot = null,
            showLayoutPopup = false
        )
        updatePopupOverlay()
        return true
    }

    private fun deleteLayout(layout: SavedLayout) {
        val layouts = state.savedLayouts.filterNot { it.id == layout.id }
        repository.saveLayouts(layouts)
        state = state.copy(savedLayouts = layouts)
    }

    private fun launchSavedLayout(layout: SavedLayout) {
        val snapshot = layoutLauncher.launch(
            layout = layout,
            backend = state.privilegedBackend,
            panelPosition = state.panelPosition
        )
        if (state.preserveDexLayoutEnabled && snapshot != null) {
            repository.saveDexSessionSnapshot(snapshot, pendingRestore = false)
            Log.i(
                TAG,
                "Saved active layout snapshot immediately: " +
                    snapshot.apps.joinToString { "${it.packageName}:${it.bounds.flattenToString()}" }
            )
        }
    }

    private fun selectPrivilegedBackend(backend: PrivilegedBackend) {
        when {
            backend.rootEnabled && !state.privilegedBackend.rootEnabled -> requestRootBackend()
            backend.shizukuEnabled && !state.privilegedBackend.shizukuEnabled -> requestShizukuBackend()
            else -> savePrivilegedBackend(backend)
        }
    }

    private fun requestRootBackend() {
        val requestId = ++privilegedBackendRequestId
        Toast.makeText(this, getString(R.string.toast_root_requesting), Toast.LENGTH_SHORT).show()
        Thread {
            val granted = commandRunner.requestRootPermissionIfNeeded()
            handler.post {
                if (requestId != privilegedBackendRequestId) return@post
                if (granted) {
                    savePrivilegedBackend(state.privilegedBackend.withRootEnabled(true))
                    Toast.makeText(this, getString(R.string.toast_root_enabled), Toast.LENGTH_SHORT).show()
                } else {
                    savePrivilegedBackend(state.privilegedBackend.withRootEnabled(false))
                    Toast.makeText(this, getString(R.string.toast_root_denied), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun requestShizukuBackend() {
        val requestId = ++privilegedBackendRequestId
        pendingShizukuBackendRequestId = requestId
        if (commandRunner.hasBackend(PrivilegedBackend.Shizuku)) {
            savePrivilegedBackend(state.privilegedBackend.withShizukuEnabled(true))
            return
        }
        Toast.makeText(this, getString(R.string.toast_shizuku_connecting), Toast.LENGTH_SHORT).show()
        Thread {
            val binderReady = commandRunner.waitForShizukuBinder()
            handler.post {
                if (
                    requestId != privilegedBackendRequestId ||
                    pendingShizukuBackendRequestId != requestId
                ) {
                    return@post
                }
                when {
                    !binderReady -> failShizukuBackendRequest()
                    commandRunner.hasBackend(PrivilegedBackend.Shizuku) -> {
                        savePrivilegedBackend(state.privilegedBackend.withShizukuEnabled(true))
                    }
                    commandRunner.requestShizukuPermissionIfNeeded() -> Unit
                    else -> failShizukuBackendRequest()
                }
            }
        }.start()
    }

    private fun failShizukuBackendRequest() {
        savePrivilegedBackend(state.privilegedBackend.withShizukuEnabled(false))
        Toast.makeText(
            this,
            getString(R.string.toast_shizuku_permission_required),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun handleShizukuPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode != PrivilegedCommandRunner.SHIZUKU_PERMISSION_REQUEST_CODE) return
        val requestId = pendingShizukuBackendRequestId ?: return
        pendingShizukuBackendRequestId = null
        if (requestId != privilegedBackendRequestId) return
        if (grantResult == PackageManager.PERMISSION_GRANTED && commandRunner.hasBackend(PrivilegedBackend.Shizuku)) {
            savePrivilegedBackend(state.privilegedBackend.withShizukuEnabled(true))
        } else {
            failShizukuBackendRequest()
        }
    }

    private fun savePrivilegedBackend(backend: PrivilegedBackend) {
        privilegedBackendRequestId++
        pendingShizukuBackendRequestId = null
        repository.savePrivilegedBackend(backend)
        state = state.copy(privilegedBackend = backend)
        if (backend == PrivilegedBackend.None) {
            disableBackendDependentSettings()
        }
    }

    private fun setBatteryFixEnabled(enabled: Boolean) {
        val backend = if (enabled) batteryBackendOrNull() else state.privilegedBackend
        if (enabled && backend == null) {
            repository.saveBatteryFixEnabled(false)
            state = state.copy(batteryFixEnabled = false)
            Toast.makeText(
                this,
                getString(R.string.toast_battery_backend_not_selected),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        repository.saveBatteryFixEnabled(enabled)
        state = state.copy(batteryFixEnabled = enabled)
        if (enabled) {
            val resolvedBackend = backend ?: return
            batteryController.applyLevel(
                state.batteryLevelText.toIntOrNull() ?: 100,
                resolvedBackend
            )
        }
    }

    private fun batteryBackendOrNull(): PrivilegedBackend? {
        val currentBackend = state.privilegedBackend
        if (currentBackend != PrivilegedBackend.None) {
            return currentBackend
        }
        if (commandRunner.hasBackend(PrivilegedBackend.Shizuku)) {
            val backend = state.privilegedBackend.withShizukuEnabled(true)
            savePrivilegedBackend(backend)
            return backend
        }
        return null
    }

    private fun restoreShizukuBackendIfAvailable() {
        if (state.privilegedBackend != PrivilegedBackend.None) return
        if (!state.batteryFixEnabled && !state.preserveDexLayoutEnabled) return
        if (!commandRunner.hasBackend(PrivilegedBackend.Shizuku)) return
        val backend = state.privilegedBackend.withShizukuEnabled(true)
        repository.savePrivilegedBackend(backend)
        state = state.copy(privilegedBackend = backend)
    }

    private fun enforceBackendDependentSettings() {
        if (state.privilegedBackend != PrivilegedBackend.None) return
        if (state.batteryFixEnabled) {
            repository.saveBatteryFixEnabled(false)
        }
        if (state.preserveDexLayoutEnabled) {
            repository.savePreserveDexLayoutEnabled(false)
        }
        state = state.copy(
            batteryFixEnabled = false,
            preserveDexLayoutEnabled = false
        )
    }

    private fun disableBackendDependentSettings() {
        if (state.batteryFixEnabled) {
            repository.saveBatteryFixEnabled(false)
        }
        if (state.preserveDexLayoutEnabled) {
            preserveLayoutRequestId++
            persistPreserveLayoutEnabled(false)
            repository.clearDexSessionSnapshot()
        }
        state = state.copy(batteryFixEnabled = false)
    }

    private fun selectPanelPosition(position: PanelPosition) {
        repository.savePanelPosition(position)
        panelTransitionRunnable?.let { handler.removeCallbacks(it) }
        panelTransitionRunnable = null
        panelView?.visibility = View.VISIBLE
        state = state.copy(panelPosition = position, panelCollapsed = false)
        updatePanelOverlayLayout()
        updatePopupOverlay()
    }

    private fun setPreserveDexLayoutEnabled(enabled: Boolean) {
        val requestId = ++preserveLayoutRequestId
        if (!enabled) {
            persistPreserveLayoutEnabled(false)
            repository.clearDexSessionSnapshot()
            return
        }
        if (state.privilegedBackend == PrivilegedBackend.None) {
            persistPreserveLayoutEnabled(false)
            Toast.makeText(
                this,
                getString(R.string.toast_preserve_layout_backend_required),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        persistPreserveLayoutEnabled(false)
        checkAvailableBackendAsync(requestId, notifyIfUnavailable = true)
    }

    private fun validatePreserveLayoutBackendOnStartup() {
        if (!state.preserveDexLayoutEnabled) return
        val requestId = ++preserveLayoutRequestId
        checkAvailableBackendAsync(requestId, notifyIfUnavailable = true)
    }

    private fun checkAvailableBackendAsync(requestId: Int, notifyIfUnavailable: Boolean) {
        val preferredBackend = state.privilegedBackend
        Thread {
            val available = preferredBackend != PrivilegedBackend.None &&
                commandRunner.hasBackend(preferredBackend)
            handler.post {
                if (requestId != preserveLayoutRequestId) return@post
                if (!available) {
                    savePrivilegedBackend(PrivilegedBackend.None)
                    if (notifyIfUnavailable) {
                        Toast.makeText(
                            this,
                            getString(R.string.toast_preserve_layout_backend_required),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    persistPreserveLayoutEnabled(true)
                }
            }
        }.start()
    }

    private fun persistPreserveLayoutEnabled(enabled: Boolean) {
        repository.savePreserveDexLayoutEnabled(enabled)
        state = state.copy(preserveDexLayoutEnabled = enabled)
    }

    private fun setPanelCollapsed(collapsed: Boolean) {
        panelTransitionRunnable?.let { handler.removeCallbacks(it) }
        panelTransitionRunnable = null
        val panel = panelView ?: return
        panel.visibility = View.INVISIBLE
        state = state.copy(
            showLayoutPopup = false,
            showPaymentPopup = false,
            showSettings = false,
            choosingSlot = null
        )
        updatePopupOverlay()
        updateViewLayoutSafely(panel, overlayLayoutParams(state.panelPosition, collapsed), "panel collapse")
        panelTransitionRunnable = Runnable {
            state = state.copy(panelCollapsed = collapsed)
            panel.visibility = View.VISIBLE
            updatePanelOverlayLayout()
            panelTransitionRunnable = null
        }.also { handler.postDelayed(it, PANEL_COLLAPSE_TRANSITION_MS) }
    }

    private fun setLayoutPopupVisible(visible: Boolean) {
        popupInputFocused = false
        state = if (visible) {
            state.copy(showLayoutPopup = true, showPaymentPopup = false)
        } else {
            state.copy(
                showLayoutPopup = false,
                currentLayoutType = null,
                draftSlots = emptyList(),
                draftRatios = emptyList(),
                choosingSlot = null
            )
        }
        updatePopupOverlay()
    }

    private fun setPaymentPopupVisible(visible: Boolean) {
        popupInputFocused = false
        state = if (visible) {
            state.copy(
                showPaymentPopup = true,
                showLayoutPopup = false,
                currentLayoutType = null,
                draftSlots = emptyList(),
                draftRatios = emptyList(),
                choosingSlot = null
            )
        } else {
            state.copy(showPaymentPopup = false)
        }
        updatePopupOverlay()
    }

    private fun updatePanelOverlayLayout() {
        panelView?.let {
            updateViewLayoutSafely(it, overlayLayoutParams(state.panelPosition, state.panelCollapsed), "panel")
        }
    }

    private fun scheduleOverlayLayoutRefresh(reason: String) {
        if (panelView == null || !::windowManager.isInitialized) return
        layoutRefreshRunnable?.let { handler.removeCallbacks(it) }
        layoutRefreshAttempt = 0
        scheduleNextOverlayLayoutRefresh(reason)
    }

    private fun scheduleNextOverlayLayoutRefresh(reason: String) {
        val attempt = layoutRefreshAttempt
        val delayMs = OVERLAY_LAYOUT_REFRESH_DELAYS_MS.getOrElse(attempt) {
            return
        }
        layoutRefreshRunnable = Runnable {
            layoutRefreshRunnable = null
            refreshOverlayLayout("$reason, attempt=${attempt + 1}")
            layoutRefreshAttempt = attempt + 1
            if (panelView != null) {
                scheduleNextOverlayLayoutRefresh(reason)
            }
        }.also { handler.postDelayed(it, delayMs) }
    }

    private fun refreshOverlayLayout(reason: String) {
        val displayId = currentDisplayId ?: return
        val display = DexDisplaySelector.displayById(this, displayId)
        if (display == null) {
            Log.w(TAG, "Skip overlay refresh: display=$displayId is unavailable; reason=$reason")
            return
        }
        overlayDensity = createDisplayContext(display).resources.displayMetrics.density
        runCatching {
            updatePanelOverlayLayout()
            updatePopupOverlay()
            updateRestScreenOverlay()
            panelView?.requestLayout()
            popupView?.requestLayout()
            restScreenView?.requestLayout()
            val metrics = windowManager.maximumWindowMetrics
            val frames = currentDisplayFrames()
            Log.i(
                TAG,
                "Overlay refreshed after $reason: display=$displayId " +
                    "maximumBounds=${metrics.bounds} actualBounds=${frames.displayBounds} " +
                    "safeBounds=${frames.safeBounds} density=$overlayDensity"
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to refresh overlay after $reason", error)
        }
    }

    private fun stopPanel() {
        removeRestScreenView()
        popupView?.let { runCatching { windowManager.removeView(it) } }
        panelView?.let { runCatching { windowManager.removeView(it) } }
        popupView = null
        panelView = null
        setPanelRunning(false)
        PanelOverlayBounds.bounds = null
        PanelOverlayBounds.displayId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopPanelManually() {
        repository.savePanelAutoStartSuppressed(true)
        captureCurrentLayoutAndStop()
    }

    private fun showRestScreen() {
        if (!::windowManager.isInitialized || !::overlayContext.isInitialized || restScreenView != null) return
        setLayoutPopupVisible(false)
        setPaymentPopupVisible(false)
        val composeView = ComposeView(overlayContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@PanelOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PanelOverlayService)
            setContent {
                DexAutoTheme {
                    RestScreenOverlay(
                        nowMillis = state.nowMillis,
                        onDismiss = ::hideRestScreen
                    )
                }
            }
        }
        restScreenView = composeView
        runCatching {
            windowManager.addView(composeView, restScreenLayoutParams())
            composeView.post { enableRestScreenImmersiveMode(composeView) }
        }.onFailure { error ->
            restScreenView = null
            Log.w(TAG, "Unable to show rest screen overlay", error)
            Toast.makeText(
                this,
                getString(R.string.toast_overlay_create_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun hideRestScreen() {
        removeRestScreenView()
    }

    private fun removeRestScreenView() {
        val view = restScreenView ?: return
        restScreenView = null
        restoreSystemBars(view)
        runCatching { windowManager.removeView(view) }
            .onFailure { error -> Log.w(TAG, "Unable to remove rest screen overlay", error) }
    }

    private fun updateRestScreenOverlay() {
        restScreenView?.let { view ->
            if (updateViewLayoutSafely(view, restScreenLayoutParams(), "rest screen")) {
                view.post { enableRestScreenImmersiveMode(view) }
            }
        }
    }

    private fun updateViewLayoutSafely(
        view: View,
        params: WindowManager.LayoutParams,
        label: String
    ): Boolean =
        runCatching {
            windowManager.updateViewLayout(view, params)
            true
        }.getOrElse { error ->
            Log.w(TAG, "Unable to update $label overlay layout", error)
            false
        }

    @Suppress("DEPRECATION")
    private fun enableRestScreenImmersiveMode(view: View) {
        view.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        view.windowInsetsController?.apply {
            systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsets.Type.systemBars())
        }
    }

    private fun restoreSystemBars(view: View) {
        view.windowInsetsController?.show(WindowInsets.Type.systemBars())
    }

    private fun captureCurrentLayoutAndStop() {
        hideRestScreen()
        if (captureInProgress) {
            stopAfterCapture = true
            return
        }
        if (!repository.loadPreserveDexLayoutEnabled()) {
            stopPanel()
            return
        }
        val displayId = currentDisplayId
            ?: PanelOverlayBounds.displayId
            ?: DexDisplaySelector.dexDesktopDisplay(this)?.displayId
        if (displayId == null || displayId == Display.DEFAULT_DISPLAY) {
            repository.clearDexSessionSnapshot()
            stopPanel()
            return
        }
        val workArea = runCatching {
            layoutLauncher.currentWorkArea(displayId, state.panelPosition)
        }.getOrElse {
            repository.clearDexSessionSnapshot()
            stopPanel()
            return
        }
        captureInProgress = true
        stopAfterCapture = true
        sessionLayoutController.captureAndThen(
            displayId = displayId,
            workArea = workArea,
            backend = state.privilegedBackend,
            pendingRestore = true
        ) {
            captureInProgress = false
            stopAfterCapture = false
            stopPanel()
        }
    }

    private fun updatePopupOverlay() {
        if (!::windowManager.isInitialized || !::overlayContext.isInitialized) return
        if ((!state.showLayoutPopup && !state.showPaymentPopup) || state.showSettings) {
            popupView?.let { runCatching { windowManager.removeView(it) } }
            popupView = null
            popupInputFocused = false
            return
        }

        val existingPopup = popupView
        if (existingPopup != null) {
            updateViewLayoutSafely(existingPopup, popupLayoutParams(state.panelPosition), "popup")
            return
        }

        val composeView = ComposeView(overlayContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@PanelOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PanelOverlayService)
            setContent {
                DexAutoTheme {
                    when {
                        state.showLayoutPopup -> LayoutSetupPopup(
                            showPopup = true,
                            panelPosition = state.panelPosition,
                            layoutType = state.currentLayoutType,
                            draftSlots = state.draftSlots,
                            draftRatios = state.draftRatios,
                            installedApps = state.installedApps,
                            onLayoutTypeSelected = ::selectLayoutType,
                            onSlotClicked = { state = state.copy(choosingSlot = it) },
                            onResetDraft = ::resetDraftLayout,
                            onSave = ::saveCurrentLayout,
                            onDismissAppPicker = { state = state.copy(choosingSlot = null) },
                            onSelectApp = { app -> selectAppForSlot(app.packageName) },
                            onRatiosChanged = { state = state.copy(draftRatios = it) },
                            choosingSlot = state.choosingSlot,
                            onInputFocusChanged = ::setPopupInputFocused
                        )
                        state.showPaymentPopup -> PaymentQrPopup(
                            initialConfig = PaymentQrConfig(
                                bankCode = state.paymentBankCode,
                                accountNumber = state.paymentAccountNumber
                            ),
                            onUpdate = ::updatePaymentQrConfig,
                            onInputFocusChanged = ::setPopupInputFocused
                        )
                    }
                }
            }
        }
        popupView = composeView
        runCatching {
            windowManager.addView(
                composeView,
                popupLayoutParams(state.panelPosition, popupInputFocused)
            )
        }
            .onFailure {
                popupView = null
                popupInputFocused = false
                state = state.copy(showLayoutPopup = false, showPaymentPopup = false)
                Toast.makeText(this, getString(R.string.toast_popup_create_failed), Toast.LENGTH_SHORT).show()
            }
    }

    private fun setPopupInputFocused(focused: Boolean) {
        if (popupInputFocused == focused) return
        popupInputFocused = focused
        val view = popupView ?: return
        view.post {
            if (popupView === view && popupInputFocused == focused) {
                updateViewLayoutSafely(
                    view,
                    popupLayoutParams(state.panelPosition, focused),
                    "popup input focus"
                )
            }
        }
    }

    private fun updatePaymentQrConfig(config: PaymentQrConfig) {
        repository.savePaymentQrConfig(config)
        state = state.copy(
            paymentBankCode = config.bankCode,
            paymentAccountNumber = config.accountNumber
        )
    }

    private fun setPanelRunning(running: Boolean) {
        isPanelRunning = running
        if (running) {
            runCatching {
                getSystemService(NotificationManager::class.java)?.notify(
                    NOTIFICATION_ID,
                    createNotification(R.string.notification_panel_text)
                )
            }.onFailure { error ->
                Log.w(TAG, "Unable to update panel notification", error)
            }
        }
        sendBroadcast(
            Intent(ACTION_PANEL_RUNNING_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_PANEL_RUNNING, running)
        )
    }

    private fun loadState(): MainUiState {
        val paymentConfig = repository.loadPaymentQrConfig()
        return MainUiState(
            savedLayouts = repository.loadSavedLayouts(),
            installedApps = repository.loadLaunchableApps(),
            batteryFixEnabled = repository.loadBatteryFixEnabled(),
            batteryLevelText = repository.loadBatteryLevel().toString(),
            privilegedBackend = repository.loadPrivilegedBackend(),
            panelPosition = repository.loadPanelPosition(),
            preserveDexLayoutEnabled = repository.loadPreserveDexLayoutEnabled(),
            paymentBankCode = paymentConfig.bankCode,
            paymentAccountNumber = paymentConfig.accountNumber
        )
    }

    private fun scheduleBatteryFixAfterPanelDraw(view: View, expectedDisplayId: Int) {
        startupBatteryFixRunnable?.let { handler.removeCallbacks(it) }
        startupBatteryFixRunnable = Runnable {
            startupBatteryFixRunnable = null
            if (
                panelView === view &&
                currentDisplayId == expectedDisplayId &&
                isPanelRunning &&
                view.isShown
            ) {
                applyBatteryFixOnStartupIfNeeded()
            }
        }.also { handler.postDelayed(it, STARTUP_BATTERY_FIX_DELAY_MS) }
    }

    private fun applyBatteryFixOnStartupIfNeeded() {
        if (!state.batteryFixEnabled) return
        val backend = batteryBackendOrNull() ?: return
        batteryController.applyLevel(
            state.batteryLevelText.toIntOrNull() ?: 100,
            backend
        )
    }

    private fun overlayLayoutParams(position: PanelPosition, collapsed: Boolean): WindowManager.LayoutParams {
        val bounds = panelBounds(position, collapsed)
        Log.i(TAG, "Panel bounds=$bounds position=$position")
        PanelOverlayBounds.bounds = bounds
        return WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            anchorToScreenEdge(bounds, position)
        }
    }

    private fun panelBounds(position: PanelPosition, collapsed: Boolean): Rect {
        val safeBounds = safeDisplayBounds().toGeometryBounds()
        val verticalPanel = position == PanelPosition.Left || position == PanelPosition.Right
        val panelLimit = if (verticalPanel) {
            safeBounds.right - safeBounds.left
        } else {
            safeBounds.bottom - safeBounds.top
        }
        val collapsedLongLimit = if (verticalPanel) {
            safeBounds.bottom - safeBounds.top
        } else {
            safeBounds.right - safeBounds.left
        }
        return PanelGeometry.panelBounds(
            safeBounds = safeBounds,
            position = position,
            collapsed = collapsed,
            panelSize = dpToPx(PANEL_SIZE_DP).coerceAtMost(panelLimit),
            collapsedTabShort = dpToPx(COLLAPSED_TAB_SHORT_DP).coerceAtMost(panelLimit),
            collapsedTabLong = dpToPx(COLLAPSED_TAB_LONG_DP).coerceAtMost(collapsedLongLimit)
        ).toAndroidRect()
    }

    private fun popupLayoutParams(
        position: PanelPosition,
        inputFocused: Boolean = popupInputFocused
    ): WindowManager.LayoutParams {
        val bounds = popupBounds(position)
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            if (inputFocused) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            anchorToScreenEdge(bounds, position)
        }
    }

    @Suppress("DEPRECATION")
    private fun restScreenLayoutParams(): WindowManager.LayoutParams {
        val displayBounds = windowManager.maximumWindowMetrics.bounds
        return WindowManager.LayoutParams(
            displayBounds.width(),
            displayBounds.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    private fun WindowManager.LayoutParams.anchorToScreenEdge(
        bounds: Rect,
        position: PanelPosition
    ) {
        val safeBounds = currentDisplayFrames().safeBounds
        when (position) {
            PanelPosition.Left,
            PanelPosition.Top -> {
                gravity = Gravity.TOP or Gravity.LEFT
                x = bounds.left - safeBounds.left
                y = bounds.top - safeBounds.top
            }
            PanelPosition.Right -> {
                gravity = Gravity.TOP or Gravity.RIGHT
                x = safeBounds.right - bounds.right
                y = bounds.top - safeBounds.top
            }
            PanelPosition.Bottom -> {
                gravity = Gravity.BOTTOM or Gravity.LEFT
                x = bounds.left - safeBounds.left
                y = safeBounds.bottom - bounds.bottom
            }
        }
    }

    private fun popupBounds(position: PanelPosition): Rect {
        val safeBounds = safeDisplayBounds().toGeometryBounds()
        val verticalPanel = position == PanelPosition.Left || position == PanelPosition.Right
        val panelLimit = if (verticalPanel) {
            safeBounds.right - safeBounds.left
        } else {
            safeBounds.bottom - safeBounds.top
        }
        val popupSize = dpToPx(POPUP_SIZE_DP).coerceAtMost(
            minOf(
                safeBounds.right - safeBounds.left,
                safeBounds.bottom - safeBounds.top
            )
        )
        return PanelGeometry.popupBounds(
            safeBounds = safeBounds,
            panelBounds = PanelGeometry.panelBounds(
                safeBounds = safeBounds,
                position = position,
                collapsed = false,
                panelSize = dpToPx(PANEL_SIZE_DP).coerceAtMost(panelLimit),
                collapsedTabShort = dpToPx(COLLAPSED_TAB_SHORT_DP).coerceAtMost(panelLimit),
                collapsedTabLong = dpToPx(COLLAPSED_TAB_LONG_DP)
            ),
            position = position,
            popupSize = popupSize
        ).toAndroidRect()
    }

    private fun dpToPx(dp: Int): Int = (dp * overlayDensity).roundToInt().coerceAtLeast(1)

    private fun safeDisplayBounds(): Rect {
        return currentDisplayFrames().safeBounds
    }

    private fun currentDisplayFrames(): OverlayDisplayFrames {
        panelView
            ?.takeIf(View::isAttachedToWindow)
            ?.let { view ->
                val visibleFrame = Rect()
                view.getWindowVisibleDisplayFrame(visibleFrame)
                val representsDisplayArea =
                    visibleFrame.width() > view.width || visibleFrame.height() > view.height
                if (
                    visibleFrame.width() > 0 &&
                    visibleFrame.height() > 0 &&
                    representsDisplayArea
                ) {
                    val insets = view.rootWindowInsets?.getInsets(
                        WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                    )
                    if (insets != null) {
                        return OverlayDisplayFrames(
                            displayBounds = Rect(
                                visibleFrame.left - insets.left,
                                visibleFrame.top - insets.top,
                                visibleFrame.right + insets.right,
                                visibleFrame.bottom + insets.bottom
                            ),
                            safeBounds = visibleFrame
                        )
                    }
                }
            }

        val metrics = windowManager.maximumWindowMetrics
        return OverlayDisplayFrames(
            displayBounds = metrics.bounds,
            safeBounds = metrics.bounds.withoutSystemDecorInsets(metrics.windowInsets)
        )
    }

    private fun Rect.withoutSystemDecorInsets(windowInsets: WindowInsets): Rect {
        val insets = windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        return Rect(left + insets.left, top + insets.top, right - insets.right, bottom - insets.bottom)
    }

    private fun Rect.toGeometryBounds() = PanelGeometry.Bounds(left, top, right, bottom)

    private fun PanelGeometry.Bounds.toAndroidRect() = Rect(left, top, right, bottom)

    private data class OverlayDisplayFrames(
        val displayBounds: Rect,
        val safeBounds: Rect
    )

    private fun confirmPanelAfterFirstDraw(view: View, expectedDisplayId: Int) {
        panelDrawTimeoutRunnable?.let { handler.removeCallbacks(it) }
        val drawListener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (view.viewTreeObserver.isAlive) {
                    view.viewTreeObserver.removeOnPreDrawListener(this)
                }
                panelDrawTimeoutRunnable?.let { handler.removeCallbacks(it) }
                panelDrawTimeoutRunnable = null
                if (panelView === view && view.display?.displayId == expectedDisplayId) {
                    Log.i(
                        TAG,
                        "Panel rendered on display id=$expectedDisplayId size=${view.width}x${view.height}"
                    )
                    setPanelRunning(true)
                    scheduleBatteryFixAfterPanelDraw(view, expectedDisplayId)
                    scheduleSessionLayoutRestore(view, expectedDisplayId)
                }
                return true
            }
        }
        view.viewTreeObserver.addOnPreDrawListener(drawListener)
        panelDrawTimeoutRunnable = Runnable {
            if (panelView === view && !isPanelRunning) {
                Log.w(TAG, "Panel did not render on display id=$expectedDisplayId")
                runCatching { windowManager.removeView(view) }
                panelView = null
                currentDisplayId = null
                PanelOverlayBounds.bounds = null
                PanelOverlayBounds.displayId = null
                setPanelRunning(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.also { handler.postDelayed(it, PANEL_DRAW_TIMEOUT_MS) }
    }

    private fun scheduleSessionLayoutRestore(view: View, expectedDisplayId: Int) {
        if (!restoreSessionAfterPanelDraw) return
        sessionRestoreRunnable?.let { handler.removeCallbacks(it) }
        sessionRestoreRunnable = Runnable {
            sessionRestoreRunnable = null
            if (
                restoreSessionAfterPanelDraw &&
                panelView === view &&
                currentDisplayId == expectedDisplayId &&
                expectedDisplayId != Display.DEFAULT_DISPLAY
            ) {
                restoreSessionAfterPanelDraw = false
                sessionLayoutController.restoreIfPending(expectedDisplayId, state.panelPosition)
            }
        }.also { handler.postDelayed(it, SESSION_RESTORE_DELAY_MS) }
    }

    private fun createNotification(textRes: Int) = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.notification_panel_title))
        .setContentText(getString(textRes))
        .setOngoing(true)
        .build()

    private fun startPanelForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.notification_channel_panel), NotificationManager.IMPORTANCE_LOW)
        )
        runCatching {
            startForeground(NOTIFICATION_ID, createNotification(R.string.notification_panel_waiting))
        }.onFailure { error ->
            Log.w(TAG, "Unable to start panel foreground service", error)
            stopSelf()
        }
    }

    companion object {
        const val NOTIFICATION_ID = 2001
        const val NOTIFICATION_CHANNEL_ID = "dex_auto_panel"
        const val PANEL_SIZE_DP = 100
        const val COLLAPSED_TAB_SHORT_DP = 32
        const val COLLAPSED_TAB_LONG_DP = 96
        const val PANEL_COLLAPSE_TRANSITION_MS = 80L
        const val POPUP_SIZE_DP = 400
        const val EXTRA_TARGET_DISPLAY_ID = "com.phuoctnb.dexauto.extra.TARGET_DISPLAY_ID"
        const val EXTRA_REQUIRE_EXTERNAL_DISPLAY = "com.phuoctnb.dexauto.extra.REQUIRE_EXTERNAL_DISPLAY"
        const val EXTRA_AUTOSTART_ATTEMPT = "com.phuoctnb.dexauto.extra.AUTOSTART_ATTEMPT"
        const val EXTRA_MANUAL_START = "com.phuoctnb.dexauto.extra.MANUAL_START"
        const val ACTION_PANEL_RUNNING_CHANGED = "com.phuoctnb.dexauto.action.PANEL_RUNNING_CHANGED"
        const val ACTION_DEX_EXITED = "com.phuoctnb.dexauto.action.DEX_EXITED"
        const val ACTION_CAPTURE_LAYOUT_AND_STOP = "com.phuoctnb.dexauto.action.CAPTURE_LAYOUT_AND_STOP"
        const val EXTRA_PANEL_RUNNING = "com.phuoctnb.dexauto.extra.PANEL_RUNNING"
        const val TAG = "PanelOverlayService"
        private const val MAX_AUTOSTART_ATTEMPTS = 8
        private const val MIN_AUTOSTART_SETTLE_ATTEMPTS = 1
        private const val AUTOSTART_INITIAL_DELAY_MS = 10_000L
        private const val PANEL_DRAW_TIMEOUT_MS = 5_000L
        private const val STARTUP_BATTERY_FIX_DELAY_MS = 500L
        private const val SESSION_RESTORE_DELAY_MS = 1_000L
        private val OVERLAY_LAYOUT_REFRESH_DELAYS_MS =
            longArrayOf(250L, 500L, 1_000L, 2_000L, 4_000L, 6_000L)
        private val AUTOSTART_RETRY_DELAYS_MS = longArrayOf(1_500L, 2_000L, 2_500L, 3_000L, 4_000L, 5_000L, 5_000L)
        @Volatile
        var isPanelRunning: Boolean = false
            private set
    }
}
