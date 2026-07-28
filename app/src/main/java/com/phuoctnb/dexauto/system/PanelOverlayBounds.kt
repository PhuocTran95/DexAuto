package com.phuoctnb.dexauto.system

import android.graphics.Rect

object PanelOverlayBounds {
    @Volatile
    var bounds: Rect? = null

    @Volatile
    var displayId: Int? = null
}
