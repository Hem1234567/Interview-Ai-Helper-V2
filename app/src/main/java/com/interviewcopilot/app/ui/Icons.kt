package com.interviewcopilot.app.ui

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * SF Symbols-style icons: 24 x 24 grid, rounded 1.8 stroke, drawn here so no icon pack is needed.
 * They are tinted at the call site, so the base colour below never shows.
 */
object Sym {
    private fun icon(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in paths) {
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Pastel.IconBase),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val mic = icon("mic", "M12 3a3 3 0 0 1 3 3v6a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z", "M5 11a7 7 0 0 0 14 0", "M12 18v3")
    val stop = icon("stop", "M7 7h10v10H7z")
    val sparkles = icon("sparkles", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z", "M19 16v4", "M17 18h4")
    val chevronLeft = icon("chevronLeft", "M15 5l-7 7 7 7")
    val chevronRight = icon("chevronRight", "M9 5l7 7-7 7")
    val chevronDown = icon("chevronDown", "M5 9l7 7 7-7")
    val gear = icon(
        "gear",
        "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z",
        "M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    )
    val trash = icon("trash", "M4 7h16", "M10 11v6", "M14 11v6", "M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12", "M9 7V4h6v3")
    val download = icon("download", "M12 4v11", "M7 11l5 5 5-5", "M5 20h14")
    val pause = icon("pause", "M8 5v14", "M16 5v14")
    val play = icon("play", "M8 5l11 7-11 7z")
    val check = icon("check", "M5 12.5l4.5 4.5L19 7")
    val checkCircle = icon("checkCircle", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M8 12.5l3 3 5-6")
    val xmark = icon("xmark", "M6 6l12 12", "M18 6L6 18")
    val copy = icon("copy", "M9 9h10v11H9z", "M5 15V4h10")
    val share = icon("share", "M12 3v12", "M7 8l5-5 5 5", "M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6")
    val star = icon("star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z")
    val clock = icon("clock", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M12 7v5l3 2")
    val person = icon("person", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M4 21a8 8 0 0 1 16 0")
    val briefcase = icon("briefcase", "M3 8h18v12H3z", "M9 8V5h6v3", "M3 13h18")
    val book = icon("book", "M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z", "M4 19V5")
    val cpu = icon("cpu", "M7 7h10v10H7z", "M10 3v4", "M14 3v4", "M10 17v4", "M14 17v4", "M3 10h4", "M3 14h4", "M17 10h4", "M17 14h4")
    val globe = icon("globe", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M3 12h18", "M12 3c3 3 3 15 0 18", "M12 3c-3 3-3 15 0 18")
    val waveform = icon("waveform", "M4 10v4", "M8 7v10", "M12 4v16", "M16 7v10", "M20 10v4")
    val arrowUp = icon("arrowUp", "M12 19V5", "M6 11l6-6 6 6")
    val info = icon("info", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M12 11v5", "M12 8h.01")
    val warning = icon("warning", "M12 3l10 18H2z", "M12 10v5", "M12 18h.01")
}
