package co.samco.mend4.core.util

import co.samco.mend4.core.AppProperties
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object LogUtils {
    fun addHeaderToLogText(
        logText: String,
        platformHeader: String,
        version: String,
        newLine: String
    ): String {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat(AppProperties.LOG_DATE_FORMAT, Locale.ENGLISH)
        val time = sdf.format(cal.time)
        return addHeaderToLogText(logText, platformHeader, time, version, newLine)
    }

    fun addHeaderToLogText(
        logText: String,
        platformHeader: String,
        time: String,
        version: String,
        newLine: String
    ): String {
        return StringBuilder().apply {
            append(time)
            append(String.format(AppProperties.LOG_HEADER, version, platformHeader))
            append(newLine)
            append(logText)
        }.toString()
    }
}