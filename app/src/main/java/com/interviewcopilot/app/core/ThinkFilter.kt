package com.interviewcopilot.app.core

/**
 * Streaming filter that hides a reasoning model's `<think> ... </think>` block, so only the
 * final answer is shown. Feed raw pieces with [push]; read [visible] and [thinking].
 */
class ThinkFilter {
    private val raw = StringBuilder()

    /** True while the model is still inside an unfinished think block. */
    var thinking: Boolean = false
        private set

    var visible: String = ""
        private set

    fun push(piece: String): String {
        raw.append(piece)
        recompute()
        return visible
    }

    fun reset() {
        raw.setLength(0)
        thinking = false
        visible = ""
    }

    private fun recompute() {
        val text = raw.toString()
        val out = StringBuilder()
        var i = 0
        var inThink = false
        while (i < text.length) {
            if (!inThink && text.startsWith(OPEN, i)) {
                inThink = true
                i += OPEN.length
            } else if (inThink && text.startsWith(CLOSE, i)) {
                inThink = false
                i += CLOSE.length
            } else {
                if (!inThink) out.append(text[i])
                i++
            }
        }
        // A tag that is only partially received must not flash on screen.
        if (!inThink) {
            val tail = partialTagLength(out)
            if (tail > 0) out.setLength(out.length - tail)
        }
        thinking = inThink
        visible = out.toString().trimStart()
    }

    private fun partialTagLength(sb: CharSequence): Int {
        for (len in minOf(OPEN.length - 1, sb.length) downTo 1) {
            if (OPEN.startsWith(sb.subSequence(sb.length - len, sb.length).toString())) return len
        }
        return 0
    }

    private companion object {
        const val OPEN = "<think>"
        const val CLOSE = "</think>"
    }
}
