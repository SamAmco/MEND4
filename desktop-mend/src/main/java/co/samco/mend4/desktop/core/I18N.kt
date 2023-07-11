package co.samco.mend4.desktop.core

import java.util.Locale
import java.util.ResourceBundle

interface I18N {
    fun getf(name: String, vararg args: Any?): String

    operator fun get(name: String): String

    fun getNewLine(num: Int): String

    val newLine: String
}

class I18NImpl(language: String, country: String) : I18N {
    private val strings = ResourceBundle.getBundle(
        "strings",
        Locale.Builder().setLanguage(language).setRegion(country).build()
    )

    override fun getf(name: String, vararg args: Any?): String =
        String.format(strings.getString(name), *args)

    override operator fun get(name: String): String = strings.getString(name)

    override fun getNewLine(num: Int): String = newLine.repeat(num)

    override val newLine: String = System.getProperty("line.separator")
}