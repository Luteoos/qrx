package io.github.luteoos.qrx.utils

import android.util.Size
import com.google.mlkit.vision.barcode.common.Barcode

open class BarcodeWrapper(
    val barcodeList: List<Barcode>,
    val analyzerDimension: Size
)
