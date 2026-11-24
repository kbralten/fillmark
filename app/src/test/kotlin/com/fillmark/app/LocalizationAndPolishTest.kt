package com.fillmark.app

import com.fillmark.app.core.DrinkType
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.data.local.ThemeMode
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.domain.DailyGraphMode
import com.fillmark.app.ui.analytics.AnalyticsInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationAndPolishTest {

    private fun resolveResDir(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate app res directory from working dir ${File(".").absolutePath}")
    }

    private fun parseStringsXml(file: File): Pair<Set<String>, Set<String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = mutableSetOf<String>()
        val plurals = mutableSetOf<String>()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val el = stringNodes.item(i)
            val attrs = el.attributes
            val name = attrs?.getNamedItem("name")?.nodeValue ?: continue
            val translatable = attrs.getNamedItem("translatable")?.nodeValue
            if (translatable != "false") {
                strings.add(name)
            }
        }

        val pluralNodes = doc.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val name = pluralNodes.item(i).attributes?.getNamedItem("name")?.nodeValue ?: continue
            plurals.add(name)
        }

        return strings to plurals
    }

    @Test
    fun `locales_config and translated string resources are valid and complete`() {
        val resDir = resolveResDir()
        val localesConfig = File(resDir, "xml/locales_config.xml")
        assertTrue("Missing xml/locales_config.xml", localesConfig.isFile)

        val localesDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(localesConfig)
        val localeNodes = localesDoc.getElementsByTagName("locale")
        val configuredLocales = (0 until localeNodes.length).mapNotNull { idx ->
            localeNodes.item(idx).attributes?.getNamedItem("android:name")?.nodeValue
        }.toSet()
        assertEquals(setOf("en", "es", "fr", "de"), configuredLocales)

        val baseFile = File(resDir, "values/strings.xml")
        assertTrue("Missing values/strings.xml", baseFile.isFile)
        val (baseStrings, basePlurals) = parseStringsXml(baseFile)
        assertTrue("Expected translatable strings in values/strings.xml", baseStrings.isNotEmpty())
        assertTrue(
            "Expected plurals in values/strings.xml",
            basePlurals.containsAll(
                listOf(
                    "analytics_days_met_subtitle",
                    "analytics_barchart_a11y",
                    "settings_exported_csv",
                    "logs_count",
                    "presets_count"
                )
            )
        )

        for (lang in listOf("es", "fr", "de")) {
            val langFile = File(resDir, "values-$lang/strings.xml")
            assertTrue("Missing values-$lang/strings.xml", langFile.isFile)
            val (langStrings, langPlurals) = parseStringsXml(langFile)
            assertEquals("Missing strings in values-$lang: ${baseStrings - langStrings}", emptySet<String>(), baseStrings - langStrings)
            assertEquals("Extra strings in values-$lang: ${langStrings - baseStrings}", emptySet<String>(), langStrings - baseStrings)
            assertEquals("Missing plurals in values-$lang: ${basePlurals - langPlurals}", emptySet<String>(), basePlurals - langPlurals)
        }
    }

    @Test
    fun `enums exposing UI labels all provide string resource ids`() {
        DrinkType.entries.forEach { assertNotEquals("DrinkType.$it missing labelRes", 0, it.labelRes) }
        PresetIcon.entries.forEach { assertNotEquals("PresetIcon.$it missing labelRes", 0, it.labelRes) }
        ThemeMode.entries.forEach { assertNotEquals("ThemeMode.$it missing labelRes", 0, it.labelRes) }
        AnalyticsInterval.entries.forEach { assertNotEquals("AnalyticsInterval.$it missing labelRes", 0, it.labelRes) }
        DailyGraphMode.entries.forEach { assertNotEquals("DailyGraphMode.$it missing labelRes", 0, it.labelRes) }
    }

    @Test
    fun `turkish locale does not corrupt DrinkType lookup or displayTitle`() {
        val prev = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val milk = HydrationRecordEntity(
                timestamp = 1_000_000L,
                volumeMl = 250.0,
                drinkType = "milk"
            )
            val juice = HydrationRecordEntity(
                timestamp = 1_000_000L,
                volumeMl = 250.0,
                drinkType = "JUICE"
            )
            // Previously lowercase()/uppercase() without Locale.ROOT could fail on Turkish I/i
            assertEquals(DrinkType.MILK, DrinkType.fromId("milk"))
            assertEquals(DrinkType.JUICE, DrinkType.fromId("juice"))
            assertEquals("Milk", milk.displayTitle())
            assertEquals("Juice", juice.displayTitle())
        } finally {
            Locale.setDefault(prev)
        }
    }
}
