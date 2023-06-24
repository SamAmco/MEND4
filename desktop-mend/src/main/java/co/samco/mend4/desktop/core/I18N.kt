package co.samco.mend4.desktop.core

import java.util.Locale
import java.util.ResourceBundle

class I18N(language: String, country: String) {
    private val strings = ResourceBundle.getBundle(
        "strings",
        Locale.Builder().setLanguage(language).setRegion(country).build()
    )

    fun getf(name: String, vararg args: Any?): String =
        String.format(strings.getString(name), *args)

    operator fun get(name: String): String = strings.getString(name)

    fun getNewLine(num: Int): String = newLine.repeat(num)

    val newLine: String = System.getProperty("line.separator")
}