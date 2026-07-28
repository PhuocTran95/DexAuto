package com.phuoctnb.dexauto.system

import com.phuoctnb.dexauto.data.PanelPosition

object PanelGeometry {
    data class Bounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
    }

    fun panelBounds(
        safeBounds: Bounds,
        position: PanelPosition,
        collapsed: Boolean,
        panelSize: Int,
        collapsedTabShort: Int,
        collapsedTabLong: Int
    ): Bounds {
        if (!collapsed) {
            return when (position) {
                PanelPosition.Left ->
                    Bounds(safeBounds.left, safeBounds.top, safeBounds.left + panelSize, safeBounds.bottom)
                PanelPosition.Right ->
                    Bounds(safeBounds.right - panelSize, safeBounds.top, safeBounds.right, safeBounds.bottom)
                PanelPosition.Top ->
                    Bounds(safeBounds.left, safeBounds.top, safeBounds.right, safeBounds.top + panelSize)
                PanelPosition.Bottom ->
                    Bounds(safeBounds.left, safeBounds.bottom - panelSize, safeBounds.right, safeBounds.bottom)
            }
        }

        val centerX = safeBounds.centerX
        val centerY = safeBounds.centerY
        return when (position) {
            PanelPosition.Left -> Bounds(
                safeBounds.left,
                centerY - collapsedTabLong / 2,
                safeBounds.left + collapsedTabShort,
                centerY + collapsedTabLong / 2
            )
            PanelPosition.Right -> Bounds(
                safeBounds.right - collapsedTabShort,
                centerY - collapsedTabLong / 2,
                safeBounds.right,
                centerY + collapsedTabLong / 2
            )
            PanelPosition.Top -> Bounds(
                centerX - collapsedTabLong / 2,
                safeBounds.top,
                centerX + collapsedTabLong / 2,
                safeBounds.top + collapsedTabShort
            )
            PanelPosition.Bottom -> Bounds(
                centerX - collapsedTabLong / 2,
                safeBounds.bottom - collapsedTabShort,
                centerX + collapsedTabLong / 2,
                safeBounds.bottom
            )
        }
    }

    fun popupBounds(
        safeBounds: Bounds,
        panelBounds: Bounds,
        position: PanelPosition,
        popupSize: Int
    ): Bounds {
        val left = when (position) {
            PanelPosition.Left -> panelBounds.right
            PanelPosition.Right -> panelBounds.left - popupSize
            PanelPosition.Top,
            PanelPosition.Bottom -> safeBounds.left
        }.coerceIn(safeBounds.left, safeBounds.right - popupSize)
        val top = when (position) {
            PanelPosition.Top -> panelBounds.bottom
            PanelPosition.Bottom -> panelBounds.top - popupSize
            PanelPosition.Left,
            PanelPosition.Right -> panelBounds.top
        }.coerceIn(safeBounds.top, safeBounds.bottom - popupSize)
        return Bounds(left, top, left + popupSize, top + popupSize)
    }
}
