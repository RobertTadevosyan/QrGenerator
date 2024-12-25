package com.qrcode.generator

import android.graphics.Color

data class QRConfig(
    val width: Int,
    val height: Int,
    val color: Int = Color.BLACK
)
