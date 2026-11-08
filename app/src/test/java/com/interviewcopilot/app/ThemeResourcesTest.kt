package com.interviewcopilot.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The window colours in res/ must equal the ones in Theme.kt, so there is one source of truth. */
class ThemeResourcesTest {
    private fun module(): File = listOf(File("."), File("app")).first { File(it, "src/main/res").exists() }

    private fun xmlColor(path: String): String =
        Regex("""name="window_background">(#[0-9A-Fa-f]{8})<""").find(File(module(), path).readText())!!.groupValues[1]

    private fun themeConst(name: String): String =
        Regex(
            """const val $name = "(#[0-9A-Fa-f]{8})"""",
        ).find(File(module(), "src/main/java/com/interviewcopilot/app/ui/Theme.kt").readText())!!.groupValues[1]

    @Test
    fun lightWindowColourMatchesTheme() {
        assertEquals(themeConst("BACKGROUND_HEX"), xmlColor("src/main/res/values/colors.xml"))
    }

    @Test
    fun darkWindowColourMatchesTheme() {
        assertEquals(themeConst("DARK_BACKGROUND_HEX"), xmlColor("src/main/res/values-night/colors.xml"))
    }
}
