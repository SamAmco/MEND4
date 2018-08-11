package co.samco.mend4.core.util;

import co.samco.mend4.core.AppProperties;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class LogUtils {
    public static String addHeaderToLogText(String logText, String platformHeader, String version, String newLine) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(AppProperties.LOG_DATE_FORMAT, Locale.ENGLISH);
        String time = sdf.format(cal.getTime());
        return addHeaderToLogText(logText, platformHeader, time, version, newLine);
    }

    public static String addHeaderToLogText(String logText, String platformHeader, String time, String version, String newLine) {
        StringBuilder sb = new StringBuilder();
        sb.append(time);
        sb.append(String.format(AppProperties.LOG_HEADER, version, platformHeader));
        sb.append(newLine);
        sb.append(logText);
        return sb.toString();
    }
}
