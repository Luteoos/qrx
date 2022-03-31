package io.github.luteoos.qrx.utils

import android.content.res.TypedArray
import androidx.core.content.res.use

inline fun TypedArray.useWithTryCatch(block: (TypedArray) -> Unit) {
    this.use {
        try {
            block(it)
        } catch (e: Exception) {
            println(e)
        }
    }
}
