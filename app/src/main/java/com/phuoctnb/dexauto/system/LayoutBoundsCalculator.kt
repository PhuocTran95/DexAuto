package com.phuoctnb.dexauto.system

import android.graphics.Rect
import android.view.WindowInsets
import android.view.WindowMetrics
import com.phuoctnb.dexauto.data.LayoutType
import com.phuoctnb.dexauto.data.PanelPosition
import com.phuoctnb.dexauto.data.defaultRatiosFor

class LayoutBoundsCalculator {
    fun calculateWorkArea(
        maximumMetrics: WindowMetrics,
        currentWindowBounds: Rect,
        panelBounds: Rect?,
        panelPosition: PanelPosition
    ): Rect {
        val displayBounds = maximumMetrics.bounds
        val safeDisplayBounds = displayBounds.withoutSystemDecorInsets(maximumMetrics.windowInsets)
        panelBounds?.let { return workAreaFromPanelBounds(safeDisplayBounds, it, panelPosition) }

        val dexAutoWindowed = when (panelPosition) {
            PanelPosition.Left,
            PanelPosition.Right -> currentWindowBounds.width() < displayBounds.width() * FULLSCREEN_THRESHOLD
            PanelPosition.Top,
            PanelPosition.Bottom -> currentWindowBounds.height() < displayBounds.height() * FULLSCREEN_THRESHOLD
        }

        return if (dexAutoWindowed) {
            workAreaFromPanelBounds(safeDisplayBounds, currentWindowBounds, panelPosition)
        } else {
            fallbackWorkArea(safeDisplayBounds, panelPosition)
        }
    }

    fun boundsFor(
        type: LayoutType,
        index: Int,
        workArea: Rect,
        savedRatios: List<Float>,
        internalGapPx: Int = 0
    ): Rect {
        val ratios = savedRatios.ifEmpty { defaultRatiosFor(type) }.map { it.coerceIn(0.15f, 0.85f) }
        fun ratio(position: Int, fallback: Float): Float = ratios.getOrNull(position) ?: fallback
        val left = workArea.left
        val top = workArea.top
        val width = workArea.width()
        val height = workArea.height()

        val bounds = when (type) {
            LayoutType.Full -> Rect(left, top, left + width, top + height)
            LayoutType.SplitTwo -> {
                val split = ratio(0, 0.5f)
                if (index == 0) proportionalRect(left, top, width, height, 0f, 0f, split, 1f)
                else proportionalRect(left, top, width, height, split, 0f, 1f, 1f)
            }
            LayoutType.SplitTwoRows -> {
                val split = ratio(0, 0.5f)
                if (index == 0) proportionalRect(left, top, width, height, 0f, 0f, 1f, split)
                else proportionalRect(left, top, width, height, 0f, split, 1f, 1f)
            }
            LayoutType.SplitThree -> {
                val first = ratio(0, 1f / 3f)
                val second = ratio(1, 2f / 3f).coerceAtLeast(first + 0.1f).coerceAtMost(0.9f)
                when (index) {
                    0 -> proportionalRect(left, top, width, height, 0f, 0f, first, 1f)
                    1 -> proportionalRect(left, top, width, height, first, 0f, second, 1f)
                    else -> proportionalRect(left, top, width, height, second, 0f, 1f, 1f)
                }
            }
            LayoutType.TwoTopOneBottom -> {
                val x = ratio(0, 0.5f)
                val y = ratio(1, 0.5f)
                when (index) {
                    0 -> proportionalRect(left, top, width, height, 0f, 0f, x, y)
                    1 -> proportionalRect(left, top, width, height, x, 0f, 1f, y)
                    else -> proportionalRect(left, top, width, height, 0f, y, 1f, 1f)
                }
            }
            LayoutType.OneTopTwoBottom -> {
                val x = ratio(0, 0.5f)
                val y = ratio(1, 0.5f)
                when (index) {
                    0 -> proportionalRect(left, top, width, height, 0f, 0f, 1f, y)
                    1 -> proportionalRect(left, top, width, height, 0f, y, x, 1f)
                    else -> proportionalRect(left, top, width, height, x, y, 1f, 1f)
                }
            }
            LayoutType.OneLeftTwoRight -> {
                val x = ratio(0, 0.5f)
                val y = ratio(1, 0.5f)
                when (index) {
                    0 -> proportionalRect(left, top, width, height, 0f, 0f, x, 1f)
                    1 -> proportionalRect(left, top, width, height, x, 0f, 1f, y)
                    else -> proportionalRect(left, top, width, height, x, y, 1f, 1f)
                }
            }
            LayoutType.TwoLeftOneRight -> {
                val x = ratio(0, 0.5f)
                val y = ratio(1, 0.5f)
                when (index) {
                    0 -> proportionalRect(left, top, width, height, 0f, 0f, x, y)
                    1 -> proportionalRect(left, top, width, height, 0f, y, x, 1f)
                    else -> proportionalRect(left, top, width, height, x, 0f, 1f, 1f)
                }
            }
            LayoutType.SplitFourColumns -> {
                val r0 = ratio(0, 0.25f)
                val r1 = ratio(1, 0.5f).coerceAtLeast(r0 + 0.08f).coerceAtMost(0.86f)
                val r2 = ratio(2, 0.75f).coerceAtLeast(r1 + 0.08f).coerceAtMost(0.92f)
                when (index) {
                    0 -> proportionalRect(left, top, width, height, 0f, 0f, r0, 1f)
                    1 -> proportionalRect(left, top, width, height, r0, 0f, r1, 1f)
                    2 -> proportionalRect(left, top, width, height, r1, 0f, r2, 1f)
                    else -> proportionalRect(left, top, width, height, r2, 0f, 1f, 1f)
                }
            }
            LayoutType.GridFour -> {
                val topX = ratio(0, 0.5f)
                val bottomX = ratio(1, 0.5f)
                val leftY = ratio(2, 0.5f)
                val rightY = ratio(3, 0.5f)
                when (index) {
                    0 -> proportionalRect(left, top, width, height, 0f, 0f, topX, leftY)
                    1 -> proportionalRect(left, top, width, height, topX, 0f, 1f, rightY)
                    2 -> proportionalRect(left, top, width, height, 0f, leftY, bottomX, 1f)
                    else -> proportionalRect(left, top, width, height, bottomX, rightY, 1f, 1f)
                }
            }
        }
        return bounds.withAppPadding(workArea, internalGapPx)
    }

    private fun Rect.withAppPadding(workArea: Rect, requestedGapPx: Int): Rect {
        val gapPx = requestedGapPx.coerceAtLeast(0)
        if (gapPx == 0) return this

        val spacedLeft = left + gapPx
        val spacedTop = top + gapPx
        val spacedRight = right - gapPx
        val spacedBottom = bottom - gapPx
        return Rect(
            spacedLeft.coerceAtMost(spacedRight - 1),
            spacedTop.coerceAtMost(spacedBottom - 1),
            spacedRight,
            spacedBottom
        )
    }

    private fun workAreaFromPanelBounds(safeDisplayBounds: Rect, appBounds: Rect, panelPosition: PanelPosition): Rect {
        return when (panelPosition) {
            PanelPosition.Left -> Rect(appBounds.right.coerceIn(safeDisplayBounds.left + 1, safeDisplayBounds.right - 1), safeDisplayBounds.top, safeDisplayBounds.right, safeDisplayBounds.bottom)
            PanelPosition.Right -> Rect(safeDisplayBounds.left, safeDisplayBounds.top, appBounds.left.coerceIn(safeDisplayBounds.left + 1, safeDisplayBounds.right - 1), safeDisplayBounds.bottom)
            PanelPosition.Top -> Rect(safeDisplayBounds.left, appBounds.bottom.coerceIn(safeDisplayBounds.top + 1, safeDisplayBounds.bottom - 1), safeDisplayBounds.right, safeDisplayBounds.bottom)
            PanelPosition.Bottom -> Rect(safeDisplayBounds.left, safeDisplayBounds.top, safeDisplayBounds.right, appBounds.top.coerceIn(safeDisplayBounds.top + 1, safeDisplayBounds.bottom - 1))
        }
    }

    private fun fallbackWorkArea(safeDisplayBounds: Rect, panelPosition: PanelPosition): Rect {
        return when (panelPosition) {
            PanelPosition.Left -> Rect(safeDisplayBounds.left + FALLBACK_PANEL_SIZE_PX, safeDisplayBounds.top, safeDisplayBounds.right, safeDisplayBounds.bottom)
            PanelPosition.Right -> Rect(safeDisplayBounds.left, safeDisplayBounds.top, safeDisplayBounds.right - FALLBACK_PANEL_SIZE_PX, safeDisplayBounds.bottom)
            PanelPosition.Top -> Rect(safeDisplayBounds.left, safeDisplayBounds.top + FALLBACK_PANEL_SIZE_PX, safeDisplayBounds.right, safeDisplayBounds.bottom)
            PanelPosition.Bottom -> Rect(safeDisplayBounds.left, safeDisplayBounds.top, safeDisplayBounds.right, safeDisplayBounds.bottom - FALLBACK_PANEL_SIZE_PX)
        }
    }

    private fun Rect.withoutSystemDecorInsets(windowInsets: WindowInsets): Rect {
        val insets = windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        return Rect(left + insets.left, top + insets.top, right - insets.right, bottom - insets.bottom)
    }

    private fun proportionalRect(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        startXRatio: Float,
        startYRatio: Float,
        endXRatio: Float,
        endYRatio: Float
    ): Rect {
        return Rect(
            left + (width * startXRatio).toInt(),
            top + (height * startYRatio).toInt(),
            left + (width * endXRatio).toInt(),
            top + (height * endYRatio).toInt()
        )
    }

    private companion object {
        const val FALLBACK_PANEL_SIZE_PX = 100
        const val FULLSCREEN_THRESHOLD = 0.85f
    }
}
