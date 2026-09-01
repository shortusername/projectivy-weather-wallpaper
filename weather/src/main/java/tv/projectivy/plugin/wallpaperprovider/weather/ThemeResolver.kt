package tv.projectivy.plugin.wallpaperprovider.weather

import java.util.Calendar

/**
 * Which visual phase the wallpaper should render in.
 *
 * Open-Meteo's is_day flag is binary and flips exactly at sunrise and sunset,
 * which makes for an abrupt change on screen. Sunrise and sunset times are
 * already fetched, so the window either side of them gets its own treatment.
 */
object ThemeResolver {

    /** Minutes either side of sunrise/sunset treated as twilight. */
    private const val TWILIGHT_WINDOW = 40

    enum class Phase {
        DAWN, DAY, DUSK, NIGHT;

        /** For anything that only distinguishes light from dark. */
        val isDay: Boolean get() = this == DAY || this == DAWN

        /** Asset key fragment used by wallpaper packs. */
        val key: String
            get() = when (this) {
                DAWN -> "dawn"
                DAY -> "day"
                DUSK -> "dusk"
                NIGHT -> "night"
            }
    }

    fun resolve(c: OpenMeteoClient.Conditions): Phase {
        return when (PreferencesManager.themeMode) {
            PreferencesManager.THEME_DAY -> Phase.DAY
            PreferencesManager.THEME_NIGHT -> Phase.NIGHT
            else -> auto(c)
        }
    }

    private fun auto(c: OpenMeteoClient.Conditions): Phase {
        val now = nowMinutes()
        val sunrise = minutesOf(c.sunrise)
        val sunset = minutesOf(c.sunset)

        // Fall back to the API's own flag when we can't read the sun times.
        if (sunrise == null || sunset == null) {
            return if (c.isDay) Phase.DAY else Phase.NIGHT
        }

        if (kotlin.math.abs(now - sunrise) <= TWILIGHT_WINDOW) return Phase.DAWN
        if (kotlin.math.abs(now - sunset) <= TWILIGHT_WINDOW) return Phase.DUSK

        // Outside the twilight windows, trust the API rather than comparing
        // times: it handles polar day and night, where sunrise and sunset are
        // meaningless or absent.
        return if (c.isDay) Phase.DAY else Phase.NIGHT
    }

    private fun nowMinutes(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** "2026-08-31T06:20" -> minutes since local midnight. */
    private fun minutesOf(iso: String): Int? = try {
        if (iso.length < 16) null
        else iso.substring(11, 13).toInt() * 60 + iso.substring(14, 16).toInt()
    } catch (_: Exception) {
        null
    }
}
