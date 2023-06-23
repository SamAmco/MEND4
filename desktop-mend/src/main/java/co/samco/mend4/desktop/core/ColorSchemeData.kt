package co.samco.mend4.desktop.core

import java.awt.Color
import java.awt.Font

object ColorSchemeData {
    val consoleFont: Font by lazy { Font(Font.MONOSPACED, Font.PLAIN, 12) }
    val entryFont: Font by lazy { Font(Font.MONOSPACED, Font.PLAIN, 16) }
    val disabledTextColor: Color by lazy { Color(56, 64, 72) }
    val commandTextColor: Color by lazy { Color(0, 0, 0) }
    val entryTextColor: Color by lazy { Color(0, 0, 0) }
    val color1: Color by lazy { Color(112, 128, 144) }
    val color2: Color by lazy { Color(144, 238, 144) }
    val buttonColor1: Color by lazy { Color(168, 192, 216) }
    val buttonColor2: Color by lazy { Color(69, 103, 135) }
}