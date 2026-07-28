package com.phuoctnb.dexauto.data

import android.graphics.Rect

data class DexSessionApp(
    val packageName: String,
    val bounds: Rect
)

data class DexSessionSnapshot(
    val workArea: Rect,
    val apps: List<DexSessionApp>
)
