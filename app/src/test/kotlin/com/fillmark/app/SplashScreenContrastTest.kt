package com.fillmark.app

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class SplashScreenContrastTest {

    private fun resolveResDir(): File {
        val direct = File("src/main/res")
        if (direct.exists()) return direct
        val fromRoot = File("app/src/main/res")
        if (fromRoot.exists()) return fromRoot
        error("Could not locate app/src/main/res from ${File(".").absolutePath}")
    }

    private fun parseSplashTheme(resDir: File, valuesFolder: String): Pair<String, String> {
        val themesFile = File(resDir, "$valuesFolder/themes.xml")
        assertTrue("Missing ${themesFile.path}", themesFile.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(themesFile)
        val items = doc.getElementsByTagName("item")
        var bgColor: String? = null
        var iconRef: String? = null
        for (i in 0 until items.length) {
            val node = items.item(i)
            val nameAttr = node.attributes?.getNamedItem("name")?.nodeValue
            if (nameAttr == "windowSplashScreenBackground") {
                bgColor = node.textContent.trim()
            } else if (nameAttr == "windowSplashScreenAnimatedIcon") {
                iconRef = node.textContent.trim()
            }
        }
        assertNotNull("windowSplashScreenBackground missing in $valuesFolder/themes.xml", bgColor)
        assertNotNull("windowSplashScreenAnimatedIcon missing in $valuesFolder/themes.xml", iconRef)
        return bgColor!! to iconRef!!
    }

    private fun extractForegroundHexColors(resDir: File, iconRef: String): List<String> {
        val drawableName = iconRef.removePrefix("@drawable/")
        val drawableFile = File(resDir, "drawable/$drawableName.xml")
        assertTrue("Missing drawable ${drawableFile.path}", drawableFile.exists())
        val content = drawableFile.readText()
        val regex = Regex("""android:(?:strokeColor|fillColor)="(#[0-9A-Fa-f]{6,8})"""")
        return regex.findAll(content).map { it.groupValues[1] }.toList()
    }

    private fun parseColorRgb(hex: String): Triple<Int, Int, Int> {
        val clean = hex.removePrefix("#")
        val rgbHex = if (clean.length == 8) clean.substring(2) else clean
        val r = rgbHex.substring(0, 2).toInt(16)
        val g = rgbHex.substring(2, 4).toInt(16)
        val b = rgbHex.substring(4, 6).toInt(16)
        return Triple(r, g, b)
    }

    private fun parseAlpha(hex: String): Double {
        val clean = hex.removePrefix("#")
        return if (clean.length == 8) clean.substring(0, 2).toInt(16) / 255.0 else 1.0
    }

    private fun compositeOver(fgHex: String, bgHex: String): Triple<Int, Int, Int> {
        val alpha = parseAlpha(fgHex)
        val (fr, fg, fb) = parseColorRgb(fgHex)
        val (br, bg, bb) = parseColorRgb(bgHex)
        val r = (fr * alpha + br * (1.0 - alpha)).toInt()
        val g = (fg * alpha + bg * (1.0 - alpha)).toInt()
        val b = (fb * alpha + bb * (1.0 - alpha)).toInt()
        return Triple(r, g, b)
    }

    private fun relativeLuminance(rgb: Triple<Int, Int, Int>): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(rgb.first) + 0.7152 * channel(rgb.second) + 0.0722 * channel(rgb.third)
    }

    private fun contrastRatio(fgHex: String, bgHex: String): Double {
        val fgLum = relativeLuminance(compositeOver(fgHex, bgHex))
        val bgLum = relativeLuminance(parseColorRgb(bgHex))
        val lighter = max(fgLum, bgLum)
        val darker = min(fgLum, bgLum)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun bug6_lightModeSplashIcon_hasHighContrastOnLightBackground() {
        val resDir = resolveResDir()
        val (bgHex, iconRef) = parseSplashTheme(resDir, "values")
        val colors = extractForegroundHexColors(resDir, iconRef)
        assertTrue("Expected splash icon colors", colors.isNotEmpty())

        // Bottle outline, cap, and fillmarks must achieve >= 4.5:1 contrast against #F6F9FB
        val highContrastColors = colors.filter { contrastRatio(it, bgHex) >= 4.5 }
        assertTrue(
            "Light splash icon ($iconRef) on $bgHex lacks >=4.5:1 contrast strokes/fills. Found colors: $colors",
            highContrastColors.size >= 4
        )
    }

    @Test
    fun bug6_darkModeSplashIcon_retainsHighContrastOnDarkBackground() {
        val resDir = resolveResDir()
        val (bgHex, iconRef) = parseSplashTheme(resDir, "values-night")
        val colors = extractForegroundHexColors(resDir, iconRef)
        val highContrastColors = colors.filter { contrastRatio(it, bgHex) >= 4.5 }
        assertTrue(
            "Dark splash icon ($iconRef) on $bgHex lacks >=4.5:1 contrast",
            highContrastColors.size >= 4
        )
    }
}
