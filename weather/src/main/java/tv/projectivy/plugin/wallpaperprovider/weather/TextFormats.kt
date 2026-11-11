package tv.projectivy.plugin.wallpaperprovider.weather

import android.content.Context
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Text that appears on the wallpaper and depends on language or on the user's
 * 12/24-hour choice: clock times, hour and weekday labels, dates.
 *
 * These used to be built with hardcoded English ("3 PM", "Mon", "EEE d MMM"),
 * which meant a translated settings menu sat next to an English wallpaper and
 * the 12/24-hour setting only reached the clock. Everything here asks the
 * platform for the device locale's own pattern instead, so translators don't
 * need to supply anything for it: AM/PM markers, weekday names, and day/month
 * ordering all come out right for the language.
 */
object TextFormats {

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    /**
     * The user's 12/24-hour choice. A null context (never expected in
     * practice) falls back to 12-hour, which is what the app showed before the
     * setting existed.
     */
    fun use24Hour(context: Context?): Boolean {
        if (context == null) return false
        return when (PreferencesManager.clockHours) {
            PreferencesManager.CLOCK_HOURS_12 -> false
            PreferencesManager.CLOCK_HOURS_24 -> true
            // The system setting is right for most people, but a TV's locale is
            // often wrong for the household using it, hence the override above.
            else -> android.text.format.DateFormat.is24HourFormat(context)
        }
    }

    /**
     * Resource text when a context is available, otherwise the caller's own
     * English fallback. The fallback is a finished string (built with a Kotlin
     * template), not a format pattern, so no positional placeholders ever appear
     * in Kotlin source, where an unescaped dollar sign is read as string
     * interpolation.
     */
    fun tr(context: Context?, resId: Int, fallback: String, vararg args: Any): String =
        if (context != null) context.getString(resId, *args) else fallback

    /**
     * Newer Android versions put a narrow no-break space (U+202F) before "PM" in
     * 12-hour patterns. Some fonts have no glyph for it and draw a box, so fold
     * those spaces into ordinary ones.
     */
    private fun tidy(text: String): String = text.replace('\u202F', ' ').replace('\u00A0', ' ')

    private fun formatter(skeleton: String, zone: TimeZone): SimpleDateFormat {
        val locale = Locale.getDefault()
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
        return SimpleDateFormat(pattern, locale).apply { timeZone = zone }
    }

    private fun clockOf(hour: Int, minute: Int): java.util.Date {
        val cal = Calendar.getInstance(UTC)
        cal.clear()
        cal.set(2000, Calendar.JANUARY, 1, hour, minute)
        return cal.time
    }

    /** A time of day: "3:07 PM" or "15:07", in the locale's own style. */
    fun time(hour: Int, minute: Int, is24: Boolean): String =
        tidy(formatter(if (is24) "Hm" else "hm", UTC).format(clockOf(hour, minute)))

    /** Whole-hour label for the hourly strip: "3 PM" or "15:00". */
    fun hourLabel(hour: Int, is24: Boolean): String =
        tidy(formatter(if (is24) "Hm" else "h", UTC).format(clockOf(hour, 0)))

    /** A time from Open-Meteo's local ISO form ("2026-11-09T06:41"), or "". */
    fun timeFromIso(iso: String, is24: Boolean): String = try {
        time(iso.substring(11, 13).toInt(), iso.substring(14, 16).toInt(), is24)
    } catch (_: Exception) {
        ""
    }

    /** Short weekday name for an ISO date ("2026-11-09"), or "" if unparseable. */
    fun weekday(isoDate: String): String = try {
        val cal = Calendar.getInstance(UTC)
        cal.clear()
        cal.set(
            isoDate.substring(0, 4).toInt(),
            isoDate.substring(5, 7).toInt() - 1,
            isoDate.substring(8, 10).toInt()
        )
        tidy(formatter("EEE", UTC).format(cal.time))
    } catch (_: Exception) {
        ""
    }

    /**
     * Weekday, day and month in the locale's usual order. The formatter takes
     * the calendar's own zone: the clock's calendar is the device's, while one
     * from locationNow() is UTC with the location's offset already applied, and
     * formatting either in the wrong zone shifts the date.
     */
    fun dateText(cal: Calendar): String =
        tidy(formatter("EEEdMMM", cal.timeZone).format(cal.time))

    /** The locale's AM or PM marker for a calendar's current half-day. */
    fun amPm(cal: Calendar): String =
        tidy(DateFormatSymbols.getInstance(Locale.getDefault()).amPmStrings[cal.get(Calendar.AM_PM)])
}
