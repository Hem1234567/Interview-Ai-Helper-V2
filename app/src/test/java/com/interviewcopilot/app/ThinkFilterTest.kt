package com.interviewcopilot.app

import com.interviewcopilot.app.core.ThinkFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkFilterTest {
    @Test
    fun plainTextPassesThrough() {
        val f = ThinkFilter()
        assertEquals("Hello world", f.push("Hello ").let { f.push("world") })
        assertFalse(f.thinking)
    }

    @Test
    fun thinkBlockIsHiddenAcrossChunks() {
        val f = ThinkFilter()
        f.push("<thi")
        assertEquals("", f.visible)
        f.push("nk>secret reasoning")
        assertTrue(f.thinking)
        assertEquals("", f.visible)
        f.push("</think>\n\nThe answer is 4.")
        assertFalse(f.thinking)
        assertEquals("The answer is 4.", f.visible)
    }

    @Test
    fun partialOpeningTagNeverFlashes() {
        val f = ThinkFilter()
        f.push("Answer <th")
        assertEquals("Answer", f.visible.trimEnd())
    }
}
