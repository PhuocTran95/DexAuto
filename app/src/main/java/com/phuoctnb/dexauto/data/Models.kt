package com.phuoctnb.dexauto.data

import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import com.phuoctnb.dexauto.R

enum class LayoutType(val slotCount: Int) {
    Full(1),
    SplitTwo(2),
    SplitTwoRows(2),
    SplitThree(3),
    TwoTopOneBottom(3),
    OneTopTwoBottom(3),
    OneLeftTwoRight(3),
    TwoLeftOneRight(3),
    SplitFourColumns(4),
    GridFour(4)
}

enum class PrivilegedBackend {
    None,
    Root,
    Shizuku,
    RootAndShizuku
}

val PrivilegedBackend.rootEnabled: Boolean
    get() = this == PrivilegedBackend.Root || this == PrivilegedBackend.RootAndShizuku

val PrivilegedBackend.shizukuEnabled: Boolean
    get() = this == PrivilegedBackend.Shizuku || this == PrivilegedBackend.RootAndShizuku

fun PrivilegedBackend.withRootEnabled(enabled: Boolean): PrivilegedBackend {
    return when {
        enabled && shizukuEnabled -> PrivilegedBackend.RootAndShizuku
        enabled -> PrivilegedBackend.Root
        shizukuEnabled -> PrivilegedBackend.Shizuku
        else -> PrivilegedBackend.None
    }
}

fun PrivilegedBackend.withShizukuEnabled(enabled: Boolean): PrivilegedBackend {
    return when {
        enabled && rootEnabled -> PrivilegedBackend.RootAndShizuku
        enabled -> PrivilegedBackend.Shizuku
        rootEnabled -> PrivilegedBackend.Root
        else -> PrivilegedBackend.None
    }
}

enum class PanelPosition(@StringRes val labelRes: Int) {
    Left(R.string.panel_position_left),
    Right(R.string.panel_position_right),
    Top(R.string.panel_position_top),
    Bottom(R.string.panel_position_bottom)
}

data class SavedLayout(
    val id: String,
    val type: LayoutType,
    val packages: List<String>,
    val ratios: List<Float> = defaultRatiosFor(type)
)

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

data class LunarDate(
    val day: Int,
    val month: Int,
    val yearName: String,
    val isLeap: Boolean
)

data class MainUiState(
    val nowMillis: Long = System.currentTimeMillis(),
    val showSettings: Boolean = false,
    val showLayoutPopup: Boolean = false,
    val showPaymentPopup: Boolean = false,
    val currentLayoutType: LayoutType? = null,
    val draftSlots: List<String> = emptyList(),
    val draftRatios: List<Float> = emptyList(),
    val savedLayouts: List<SavedLayout> = emptyList(),
    val installedApps: List<LaunchableApp> = emptyList(),
    val choosingSlot: Int? = null,
    val batteryFixEnabled: Boolean = true,
    val batteryLevelText: String = "100",
    val privilegedBackend: PrivilegedBackend = PrivilegedBackend.None,
    val panelPosition: PanelPosition = PanelPosition.Left,
    val preserveDexLayoutEnabled: Boolean = false,
    val paymentBankCode: String = "",
    val paymentAccountNumber: String = "",
    val panelCollapsed: Boolean = false
)

fun defaultRatiosFor(type: LayoutType): List<Float> {
    return when (type) {
        LayoutType.Full -> emptyList()
        LayoutType.SplitTwo, LayoutType.SplitTwoRows -> listOf(0.5f)
        LayoutType.SplitThree -> listOf(1f / 3f, 2f / 3f)
        LayoutType.SplitFourColumns -> listOf(0.25f, 0.5f, 0.75f)
        LayoutType.GridFour -> listOf(0.5f, 0.5f, 0.5f, 0.5f)
        LayoutType.TwoTopOneBottom,
        LayoutType.OneTopTwoBottom,
        LayoutType.OneLeftTwoRight,
        LayoutType.TwoLeftOneRight -> listOf(0.5f, 0.5f)
    }
}
