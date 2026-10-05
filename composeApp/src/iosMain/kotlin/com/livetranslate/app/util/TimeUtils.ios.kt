package com.livetranslate.app.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}

actual fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val date = NSDate(timeIntervalSince1970 = epochMillis / 1000.0)
    val formatter = NSDateFormatter()
    formatter.dateFormat = "MMM d, HH:mm"
    return formatter.stringFromDate(date)
}
