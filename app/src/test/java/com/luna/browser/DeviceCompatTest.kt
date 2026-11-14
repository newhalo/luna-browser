package com.luna.browser

import com.luna.browser.data.DeviceProfile
import com.luna.browser.ui.theme.canvasDensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules that adapt Luna to very different TVs. */
class DeviceCompatTest {
    @Test fun canvasIs960DpOnEveryCommonTvResolution() {
        // pixels per dp such that the long side is 960 dp
        assertEquals(1.3333f, canvasDensity(1280f, 720f), 0.001f)   // 720p
        assertEquals(2f, canvasDensity(1920f, 1080f), 0.001f)       // 1080p
        assertEquals(4f, canvasDensity(3840f, 2160f), 0.001f)       // 4K
        assertEquals(2.6667f, canvasDensity(2560f, 1440f), 0.001f)  // 1440p
    }

    @Test fun rotationAndTinyOrHugeValuesAreSafe() {
        assertEquals(canvasDensity(1920f, 1080f), canvasDensity(1080f, 1920f), 0f)
        assertTrue(canvasDensity(0f, 0f) >= 0.5f)
        assertTrue(canvasDensity(100000f, 100000f) <= 8f)
    }

    @Test fun lowEndDetection() {
        assertTrue(DeviceProfile.isLowEnd(1_000_000_000L, false))      // 1 GB TV
        assertTrue(DeviceProfile.isLowEnd(1_400_000_000L, false))      // 1.5 GB TV reports ~1.4 GB
        assertTrue(DeviceProfile.isLowEnd(4_000_000_000L, true))       // flagged low-RAM by the OS
        assertFalse(DeviceProfile.isLowEnd(2_490_000_000L, false))     // the Xiaomi test TV
        assertFalse(DeviceProfile.isLowEnd(0L, false))                 // unknown: assume normal
    }
}
