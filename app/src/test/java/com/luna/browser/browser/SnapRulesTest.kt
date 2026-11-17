package com.luna.browser.browser

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapRulesTest {
    private val w = 1920
    private val h = 1080
    private fun r(l: Int, t: Int, rr: Int, b: Int, fixed: Int = 0) = intArrayOf(l, t, rr, b, fixed)
    private fun pick(list: List<IntArray>, x: Int, y: Int, dir: Int) =
        SnapRules.pick(list, x.toFloat(), y.toFloat(), dir, w, h, 2f)

    /** A row of episode buttons, 100 px wide with 20 px gaps, and a second row below it. */
    private val row1 = (0 until 8).map { r(200 + it * 120, 500, 300 + it * 120, 560) }
    private val row2 = (0 until 8).map { r(200 + it * 120, 580, 300 + it * 120, 640) }

    @Test fun rightAndLeftStepOneEpisode() {
        val list = row1 + row2
        assertArrayEquals(row1[3], pick(list, 490, 530, SnapRules.RIGHT))
        assertArrayEquals(row1[1], pick(list, 490, 530, SnapRules.LEFT))
    }

    @Test fun downStaysInTheSameColumn() {
        assertArrayEquals(row2[2], pick(row1 + row2, 490, 530, SnapRules.DOWN))
    }

    @Test fun theEndOfARowDoesNotWrapToTheNextRow() {
        // Nothing more to the right on this row: the caller scrolls the row or nudges the pointer.
        assertNull(pick(row1 + row2, row1[7][0] + 50, 530, SnapRules.RIGHT))
    }

    @Test fun farAwayLinksAreOutOfReach() {
        // A footer link 800 px below and a header link in the far corner: neither is "near".
        val list = listOf(r(400, 50, 500, 90), r(900, 1040, 1000, 1075), r(1700, 100, 1800, 140))
        assertNull(pick(list, 960, 300, SnapRules.DOWN))
        assertNull(pick(list, 960, 600, SnapRules.UP))
        assertNull(pick(list, 960, 600, SnapRules.RIGHT))
    }

    @Test fun aNearbyLinkIsReachedFromEmptySpace() {
        val link = r(900, 500, 1100, 540)
        assertArrayEquals(link, pick(listOf(link), 1000, 300, SnapRules.DOWN))
    }

    @Test fun theSameRowWinsOverACloserItemSlightlyOffIt() {
        val aligned = r(800, 500, 900, 560)
        val offRow = r(620, 600, 700, 640) // closer along the arrow, but on another line
        assertArrayEquals(aligned, pick(listOf(r(400, 500, 500, 560), aligned, offRow), 450, 530, SnapRules.RIGHT))
    }

    @Test fun edgesNotCentresDecideAlongTheArrow() {
        // A wide banner whose left edge is right next to the pointer's item beats a small link further right.
        val banner = r(520, 480, 1500, 580)
        val small = r(700, 510, 760, 550)
        val from = r(400, 510, 500, 550)
        assertArrayEquals(banner, pick(listOf(from, banner, small), 450, 530, SnapRules.RIGHT))
    }

    @Test fun floatingButtonsOnlyFromTheirOwnColumn() {
        val toTop = r(1800, 950, 1880, 1030, fixed = 1)
        assertNull(pick(listOf(toTop), 1700, 700, SnapRules.DOWN))
        assertArrayEquals(toTop, pick(listOf(toTop), 1840, 700, SnapRules.DOWN))
    }

    @Test fun downStaysInItsColumnInsteadOfZigzagging() {
        // Article text on the left, an info box on the right: Down from a text link must not hop into the box.
        val textLink = r(450, 300, 850, 340)
        val boxLink = r(1150, 450, 1360, 490)
        assertNull(pick(listOf(textLink, boxLink), 650, 320, SnapRules.DOWN))
    }

    @Test fun originIsTheSmallestItemUnderThePointer() {
        val card = r(100, 100, 600, 400)
        val button = r(200, 200, 300, 250)
        assertArrayEquals(floatArrayOf(200f, 200f, 300f, 250f), SnapRules.origin(listOf(card, button), 250f, 220f), 0f)
        assertArrayEquals(floatArrayOf(50f, 60f, 50f, 60f), SnapRules.origin(listOf(card), 50f, 60f), 0f)
    }
}
