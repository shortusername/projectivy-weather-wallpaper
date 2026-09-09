package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Color
import android.graphics.ColorMatrix
import java.util.Calendar

/**
 * Optional holiday theming.
 *
 * A colour grade, not an overlay. A translucent wash over a saturated sky is
 * simply overwhelmed by it; grading shifts the actual pixel values the way a
 * film LUT does, so a blue sky genuinely becomes an autumn one.
 *
 * Off by default, and deliberately so. This runs on someone's television
 * whether or not they mark a given day.
 *
 * The list skews Western and American, for a boring reason: these are the
 * occasions computable from arithmetic alone. Lunar New Year, Diwali, Eid and
 * others follow lunisolar calendars that need lookup tables rather than a
 * formula, so they're absent rather than deprioritised. Adding them means
 * shipping a date table, which is a reasonable thing to do later.
 */
object HolidayThemes {

    data class Theme(
        val id: String,
        val label: String,
        /** Pulls toward greyscale before the channel mix. */
        val saturation: Float,
        /** Per-channel multipliers, RGB. */
        val mul: Triple<Float, Float, Float>,
        /** Per-channel offsets, RGB, in 0-255 terms. */
        val add: Triple<Float, Float, Float>,
        /** Location label colour, so the accent isn't only in the grade. */
        val accent: Int,
        /** Adds drifting flecks, regardless of the actual weather. */
        val flecks: Boolean = false
    )

    /**
     * Windows are a few days wide rather than single dates: a wallpaper refreshes
     * every fifteen minutes, and a theme that appears for one day and vanishes
     * reads as a glitch rather than an occasion.
     */
    fun current(): Theme? {
        if (!PreferencesManager.holidayThemes) return null
        val (year, month, day) = effectiveDate()

        // Fixed dates
        if ((month == 12 && day >= 29) || (month == 1 && day <= 2)) return NEW_YEAR
        if (month == 2 && day in 12..15) return VALENTINES
        if (month == 3 && day in 15..18) return ST_PATRICKS
        if (month == 7 && day in 2..5) return INDEPENDENCE
        if (month == 10 && day >= 24) return HALLOWEEN
        if (month == 12 && day in 18..26) return CHRISTMAS

        // Moving feasts
        val (em, ed) = easter(year)
        if (withinDays(month, day, em, ed, before = 3, after = 1)) return EASTER

        val (tm, td) = usThanksgiving(year)
        if (withinDays(month, day, tm, td, before = 2, after = 1)) return THANKSGIVING

        return null
    }

    // ------------------------------------------------------------- palettes

    private val NEW_YEAR = Theme(
        "newyear", "New Year", 0.28f,
        Triple(1.02f, 0.90f, 1.34f), Triple(8f, 0f, 30f),
        Color.parseColor("#F6DE82"), flecks = true
    )
    private val VALENTINES = Theme(
        "valentines", "Valentine's Day", 0.32f,
        Triple(1.30f, 0.66f, 0.84f), Triple(30f, -16f, 4f),
        Color.parseColor("#FFA8C4")
    )
    private val ST_PATRICKS = Theme(
        "stpatricks", "St Patrick's Day", 0.34f,
        Triple(0.68f, 1.14f, 0.72f), Triple(-22f, 10f, -18f),
        Color.parseColor("#A0E896")
    )
    private val EASTER = Theme(
        "easter", "Easter", 0.50f,
        Triple(1.16f, 1.14f, 0.86f), Triple(26f, 24f, 4f),
        Color.parseColor("#FAD696")
    )
    private val INDEPENDENCE = Theme(
        "independence", "Independence Day", 0.38f,
        Triple(0.80f, 0.86f, 1.34f), Triple(-12f, -6f, 26f),
        Color.parseColor("#FF7878")
    )
    private val HALLOWEEN = Theme(
        // Blue is crushed hard here. A milder mix lands pink rather than orange,
        // because the lower half of most scenes is near-white and any surviving
        // blue channel pastels it.
        "halloween", "Halloween", 0.26f,
        Triple(1.34f, 0.72f, 0.34f), Triple(26f, -16f, -26f),
        Color.parseColor("#FF9228")
    )
    private val THANKSGIVING = Theme(
        "thanksgiving", "Thanksgiving", 0.42f,
        Triple(1.22f, 0.92f, 0.52f), Triple(26f, 4f, -24f),
        Color.parseColor("#F0BA6E")
    )
    private val CHRISTMAS = Theme(
        "christmas", "Christmas", 0.32f,
        Triple(1.10f, 0.96f, 0.74f), Triple(-10f, 12f, -16f),
        Color.parseColor("#E8C470"), flecks = true
    )

    /** Every theme, for previewing and for the settings summary. */
    val all: List<Theme> = listOf(
        NEW_YEAR, VALENTINES, ST_PATRICKS, EASTER,
        INDEPENDENCE, HALLOWEEN, THANKSGIVING, CHRISTMAS
    )

    // ------------------------------------------------------------ date maths

    /** Anonymous Gregorian computus. Verified against known dates 2026-2028. */
    private fun easter(year: Int): Pair<Int, Int> {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return month to day
    }

    /** Fourth Thursday in November. */
    private fun usThanksgiving(year: Int): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply {
            clear(); set(year, Calendar.NOVEMBER, 1)
        }
        var count = 0
        while (true) {
            if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.THURSDAY) {
                count++
                if (count == 4) return 11 to cal.get(Calendar.DAY_OF_MONTH)
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    /** Day-of-year comparison, so windows can straddle a month boundary. */
    private fun withinDays(
        month: Int, day: Int,
        targetMonth: Int, targetDay: Int,
        before: Int, after: Int
    ): Boolean {
        val now = dayOfYear(month, day)
        val target = dayOfYear(targetMonth, targetDay)
        return now >= target - before && now <= target + after
    }

    private fun dayOfYear(month: Int, day: Int): Int {
        val cumulative = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        return cumulative[(month - 1).coerceIn(0, 11)] + day
    }

    /**
     * Year, month and day, honouring a test override.
     *
     * A date-gated feature that can only be exercised on the day it fires is one
     * you discover is broken too late to fix. Every theme is reachable at any
     * time of year with:
     *
     *   adb shell am start -n <appId>/.SettingsActivity \
     *     --es forceDate "12-24" --ez close true
     *
     * Pass an empty string to clear it.
     */
    private fun effectiveDate(): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val forced = PreferencesManager.forceDate
        if (forced.isNotBlank()) {
            runCatching {
                val parts = forced.split("-")
                val m = parts[0].toInt()
                val d = parts[1].toInt()
                if (m in 1..12 && d in 1..31) return Triple(year, m, d)
            }
        }
        return Triple(year, cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    /** The grade as a ColorMatrix: desaturate, then scale and offset. */
    fun matrixFor(theme: Theme): ColorMatrix {
        val m = ColorMatrix().apply { setSaturation(theme.saturation) }
        val (mr, mg, mb) = theme.mul
        val (ar, ag, ab) = theme.add
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    mr, 0f, 0f, 0f, ar,
                    0f, mg, 0f, 0f, ag,
                    0f, 0f, mb, 0f, ab,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        return m
    }
}
