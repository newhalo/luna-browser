package com.luna.browser.data

import android.app.ActivityManager
import android.content.Context

/**
 * Detects TVs with little memory (entry-level Android TV / Android Go TV, often 1 GB) so that heavy
 * features start switched off there: animated background, big filter lists. The viewer can still turn
 * everything on in Settings.
 */
object DeviceProfile {
    /** Below this much total RAM a TV is treated as low-end. */
    const val LOW_MEMORY_BYTES = 1_500_000_000L

    fun isLowEnd(totalMemBytes: Long, lowRamFlag: Boolean): Boolean =
        lowRamFlag || (totalMemBytes in 1 until LOW_MEMORY_BYTES)

    fun isLowEnd(ctx: Context): Boolean = runCatching {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        isLowEnd(info.totalMem, am.isLowRamDevice)
    }.getOrDefault(false)
}
