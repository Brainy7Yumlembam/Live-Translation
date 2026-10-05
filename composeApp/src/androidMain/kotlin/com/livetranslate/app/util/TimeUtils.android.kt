package com.livetranslate.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}
