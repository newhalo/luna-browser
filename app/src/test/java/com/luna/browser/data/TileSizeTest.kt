package com.luna.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileSizeTest {
    @Test
    fun scalesShrinkInOrder() {
        assertEquals(1f, TileSize.scale(TileSize.LARGE), 0f)
        assertTrue(TileSize.scale(TileSize.MEDIUM) < TileSize.scale(TileSize.LARGE))
        assertTrue(TileSize.scale(TileSize.SMALL) < TileSize.scale(TileSize.MEDIUM))
    }

    @Test
    fun unknownValueFallsBackToLarge() {
        assertEquals(1f, TileSize.scale(null), 0f)
        assertEquals(1f, TileSize.scale("huge"), 0f)
        assertEquals(1f, TileSize.scale(""), 0f)
    }

    @Test
    fun smallerTilesFitMoreColumns() {
        val large = TileSize.columns(860f, 216f * TileSize.scale(TileSize.LARGE), 18f)
        val small = TileSize.columns(860f, 216f * TileSize.scale(TileSize.SMALL), 18f)
        assertTrue(small > large)
    }

    @Test
    fun columnsNeverDropBelowOne() {
        assertEquals(1, TileSize.columns(50f, 216f, 18f))
        assertEquals(1, TileSize.columns(860f, 0f, 18f))
    }
}
