package com.interviewcopilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Colours live in Theme.kt only. This keeps it that way. */
class HardcodedColorTest {
    private fun sourceRoot(): File = listOf("src/main/java", "app/src/main/java").map { File(it) }.first { it.exists() }

    private val literal = Regex("""\bColor\(\s*0x""")
    private val named = Regex("""\bColor\.(White|Black|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|DarkGray|LightGray)\b""")

    @Test
    fun noColourLiteralsOutsideTheme() {
        val offenders = sourceRoot().walkTopDown()
            .filter { it.extension == "kt" && it.name != "Theme.kt" }
            .flatMap { f ->
                f.readLines().withIndex().filter { (_, l) -> literal.containsMatchIn(l) || named.containsMatchIn(l) }.map { "${f.name}:${it.index + 1}" }
            }
            .toList()
        assertEquals("Hard-coded colours found: $offenders", 0, offenders.size)
    }

    @Test
    fun themeDefinesTheSpecPalette() {
        val theme = File(sourceRoot(), "com/interviewcopilot/app/ui/Theme.kt").readText()
        listOf("F5F5FA", "FFFFFF", "A5B4FC", "C4B5FD", "FBCFE8", "BBF7D0", "FDE68A", "1C1C1E", "8E8E93").forEach {
            assertTrue("Palette colour $it missing", theme.contains("0xFF$it"))
        }
    }

    @Test
    fun noMaterialAnywhere() {
        val offenders = sourceRoot().walkTopDown().filter { it.extension == "kt" }
            .filter { f -> f.readText().contains("androidx.compose.material") }.map { it.name }.toList()
        assertEquals("Material imports found: $offenders", 0, offenders.size)
    }
}
