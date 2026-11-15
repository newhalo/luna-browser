package com.luna.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {
    private fun p(id: String, name: String = "n$id", c: Int = 0) = Profile(id, name, c)

    @Test fun theDefaultProfileKeepsTheHistoricKeys() {
        assertEquals("history", ProfileRules.key(ProfileRules.DEFAULT, "history"))
        assertEquals("tabs", ProfileRules.key("0", "tabs"))
        assertEquals("p2.history", ProfileRules.key("2", "history"))
        assertEquals("pguest.favorites", ProfileRules.key(ProfileRules.GUEST, "favorites"))
    }

    @Test fun profilesNeverShareAKey() {
        val keys = listOf("0", "1", "2", "guest").flatMap { id -> ProfileRules.dataKeys.map { ProfileRules.key(id, it) } }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun namesAreCleaned() {
        assertEquals("Lan", ProfileRules.cleanName("  Lan  "))
        assertEquals("Ba Nội", ProfileRules.cleanName("Ba\n   Nội"))
        assertEquals("", ProfileRules.cleanName("\u0000\u0007"))
        assertEquals(ProfileRules.MAX_NAME, ProfileRules.cleanName("x".repeat(100)).length)
    }

    @Test fun idsAndLimits() {
        assertNull("limit reached", ProfileRules.nextId((0..4).map { p(it.toString()) }))
        assertEquals("1", ProfileRules.nextId(listOf(p("0"))))
        assertEquals("2", ProfileRules.nextId(listOf(p("0"), p("1"))))
        assertEquals("1", ProfileRules.nextId(listOf(p("0"), p("2")))) // a freed id is reused
        assertTrue(ProfileRules.isValidId("7"))
        assertTrue(ProfileRules.isValidId("guest"))
        assertFalse(ProfileRules.isValidId("../x"))
        assertFalse(ProfileRules.isValidId("10"))
        assertFalse(ProfileRules.isValidId(""))
    }

    @Test fun newProfilesTakeFreeColours() {
        assertEquals(1, ProfileRules.nextColor(listOf(p("0", c = 0))))
        assertEquals(0, ProfileRules.nextColor(listOf(p("1", c = 1))))
        val all = ProfileRules.colors.indices.map { p(it.toString().take(1), c = it) }
        assertTrue(ProfileRules.nextColor(all) in ProfileRules.colors.indices)
    }

    @Test fun initialsHandleAnyScript() {
        assertEquals("L", ProfileRules.initial("lan"))
        assertEquals("Đ", ProfileRules.initial("đức"))
        assertEquals("中", ProfileRules.initial("中文"))
        assertEquals("?", ProfileRules.initial(""))
        assertEquals("A", ProfileRules.initial("  - A"))
    }

    @Test fun roundTrip() {
        val list = listOf(p("0", "", 0), p("1", "Mẹ", 3), p("2", "Bố", 5))
        assertEquals(list, ProfileRules.parse(ProfileRules.serialize(list)))
    }

    @Test fun parsingIsDefensive() {
        assertEquals(listOf(p("0", "", 0)), ProfileRules.parse(null))
        assertEquals(listOf(p("0", "", 0)), ProfileRules.parse("not json"))
        assertEquals(listOf(p("0", "", 0)), ProfileRules.parse("[]"))
        // default is always there and first; duplicates, the guest id, bad ids and overflow are dropped
        val messy = """[{"i":"2","n":"B","c":99},{"i":"2","n":"dup","c":1},{"i":"guest","n":"g"},{"i":"x/y","n":"bad"},{"i":"1","n":"A","c":-4}]"""
        val out = ProfileRules.parse(messy)
        assertEquals(listOf("0", "2", "1"), out.map { it.id })
        assertEquals(ProfileRules.colors.size - 1, out[1].color)
        assertEquals(0, out[2].color)
        val many = (1..9).joinToString(",", "[", "]") { """{"i":"$it","n":"x$it","c":0}""" }
        assertEquals(ProfileRules.MAX, ProfileRules.parse(many).size)
        assertEquals("0", ProfileRules.parse(many).first().id)
    }
}
