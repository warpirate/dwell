package com.dwell.app.data.util

/**
 * Wall-clock seam so timestamp-producing code stays unit-testable. Production
 * uses the system clock; tests inject a fixed value.
 */
fun interface NowProvider {
    fun nowMillis(): Long
}
