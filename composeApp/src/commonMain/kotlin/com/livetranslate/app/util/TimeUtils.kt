package com.livetranslate.app.util

/**
 * 7 days in milliseconds: 7 * 24 * 60 * 60 * 1000 = 604,800,000 ms.
 */
const val RETENTION_PERIOD_MILLIS: Long = 7L * 24 * 60 * 60 * 1000L

/**
 * Returns current epoch timestamp in milliseconds.
 */
expect fun currentTimeMillis(): Long

/**
 * Formats epoch millis into a readable timestamp string (e.g. "14:30" or "MM/dd HH:mm").
 */
expect fun formatTimestamp(epochMillis: Long): String
