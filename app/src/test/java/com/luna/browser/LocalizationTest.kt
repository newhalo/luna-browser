package com.luna.browser

import com.luna.browser.data.Languages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the translations: a missing key or a wrong placeholder in any language would crash the
 * app at run time (or make a release build fail lint), so it fails here, in CI, instead.
 */
class LocalizationTest {
    private val res = File("src/main/res")

    private class Entry(val text: String, val plural: Map<String, String>?)

    private fun load(dir: String): Map<String, Entry> {
        val file = File(res, "$dir/strings.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = LinkedHashMap<String, Entry>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            val name = e.getAttribute("name")
            if (e.getAttribute("translatable") == "false") continue
            when (e.tagName) {
                "string" -> out[name] = Entry(e.textContent, null)
                "plurals" -> {
                    val items = e.getElementsByTagName("item")
                    val map = (0 until items.length).associate {
                        val item = items.item(it) as Element
                        item.getAttribute("quantity") to item.textContent
                    }
                    out[name] = Entry("", map)
                }
            }
        }
        return out
    }

    private val placeholder = Regex("%(\\d+\\$)?[sd]")
    private fun placeholders(s: String) = placeholder.findAll(s).map { it.value }.sorted().toList()

    private fun translationDirs() =
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!.map { it.name }.sorted()

    @Test fun everyLanguageHasExactlyTheDefaultKeys() {
        val base = load("values")
        for (dir in translationDirs()) {
            val t = load(dir)
            assertEquals("$dir: missing keys", emptySet<String>(), base.keys - t.keys)
            assertEquals("$dir: unknown keys", emptySet<String>(), t.keys - base.keys)
        }
    }

    @Test fun placeholdersMatchTheDefault() {
        val base = load("values")
        for (dir in translationDirs()) {
            val t = load(dir)
            for ((k, e) in base) {
                val tr = t.getValue(k)
                if (e.plural != null) {
                    for ((q, text) in tr.plural!!) assertEquals("$dir/$k[$q]", listOf("%d"), placeholders(text))
                } else {
                    assertEquals("$dir/$k placeholders", placeholders(e.text), placeholders(tr.text))
                }
            }
        }
    }

    @Test fun pluralsHaveTheQuantitiesTheirLanguageNeeds() {
        val base = load("values")
        for (dir in translationDirs()) {
            val t = load(dir)
            val needed = when (dir.removePrefix("values-")) {
                "ru" -> setOf("one", "few", "many", "other")
                "ja", "ko", "zh", "vi", "id" -> setOf("other")
                else -> setOf("one", "other")
            }
            for ((k, e) in base) if (e.plural != null) {
                assertTrue("$dir/$k needs $needed", t.getValue(k).plural!!.keys.containsAll(needed))
            }
        }
    }

    @Test fun noUnescapedApostrophesOrEmptyValues() {
        for (dir in listOf("values") + translationDirs()) {
            val raw = File(res, "$dir/strings.xml").readText()
            Regex("<string[^>]*>([^<]*)</string>").findAll(raw).forEach { m ->
                val v = m.groupValues[1]
                assertTrue("$dir: empty string ${m.value}", v.isNotBlank())
                // aapt rejects a bare ' unless the whole value is in double quotes or it is escaped
                if (!v.startsWith("\"")) assertTrue("$dir: unescaped apostrophe in $v", Regex("(?<!\\\\)'").find(v) == null)
            }
        }
    }

    @Test fun languageListMatchesResourcesAndLocaleConfig() {
        val fromRes = (listOf("en") + translationDirs().map { it.removePrefix("values-") }).toSet()
        assertEquals("Languages (data/Store.kt) vs res/values-*", fromRes, Languages.map { it.first }.toSet())
        val cfg = File(res, "xml/locales_config.xml").readText()
        val fromCfg = Regex("android:name=\"([^\"]+)\"").findAll(cfg).map { it.groupValues[1] }.toSet()
        assertEquals("locales_config.xml vs res/values-*", fromRes, fromCfg)
    }
}
