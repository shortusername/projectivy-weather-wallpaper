package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Color

/**
 * Plain-language advice inferred from data already fetched.
 *
 * No new network calls and no new dependencies — this is pure inference over
 * the forecast the plugin already has. That's most of the appeal: it costs
 * nothing to run and nothing to break, while being the difference between a
 * display that recites numbers and one that tells you something useful.
 *
 * Thresholds are worked in Celsius and km/h internally, since the stored
 * conditions are in whatever units the user chose.
 *
 * Rules were checked against live data across eleven locations from Yakutsk to
 * Singapore, which is how the thresholds ended up where they are: Wellington at
 * 34 km/h correctly produces nothing, Phoenix produces three advisories at once.
 */
object Advisories {

    enum class Kind { SAFETY, INFO }

    data class Advisory(val kind: Kind, val text: String) {
        val colour: Int
            get() = when (kind) {
                Kind.SAFETY -> Color.parseColor("#F0B45C")
                Kind.INFO -> Color.parseColor("#C8D4E0")
            }
    }

    /**
     * Everything applicable, most important first.
     *
     * The renderer shows only the first. Returning the whole list keeps the
     * ordering decision here, where the thresholds live, rather than in the
     * drawing code.
     */
    fun forConditions(c: OpenMeteoClient.Conditions): List<Advisory> {
        val out = mutableListOf<Advisory>()

        val tempC = toCelsius(c.temperature, c.metric)
        val apparentC = toCelsius(c.apparentTemperature, c.metric)
        val dewC = toCelsius(c.dewPoint, c.metric)
        val windKmh = toKmh(c.windSpeed, c.metric)
        val hour = currentHour()

        val chances = c.hourly.map { it.precipChance }
        val maxChance = chances.maxOrNull() ?: 0
        // Overnight minimum belongs to tomorrow's daily entry, since today's
        // already happened at dawn. daily[0] is tomorrow — the list starts at 1.
        val overnightLowC = c.daily.firstOrNull()?.let { toCelsius(it.low, c.metric) }

        // ---------------------------------------------------------- safety

        val freezingCode = c.weatherCode in setOf(56, 57, 66, 67)
        if (freezingCode || (!tempC.isNaN() && tempC in -2.0..2.0 && maxChance >= 50)) {
            out.add(Advisory(Kind.SAFETY, "Ice possible \u2014 take care on the roads"))
        }

        // Only from mid-afternoon: "tonight" is meaningless at breakfast.
        if (overnightLowC != null && !overnightLowC.isNaN() && hour >= 15) {
            when {
                overnightLowC <= 0.0 -> out.add(Advisory(Kind.SAFETY, "Hard freeze tonight"))
                overnightLowC <= 2.0 -> out.add(Advisory(Kind.SAFETY, "Frost likely tonight"))
            }
        }

        if (!c.uvIndexMax.isNaN()) {
            when {
                c.uvIndexMax >= 8 -> out.add(
                    Advisory(Kind.SAFETY, "Very high UV today \u00B7 index ${round(c.uvIndexMax)}")
                )
                // Pointless to warn about UV once the sun is going down.
                c.uvIndexMax >= 6 && hour < 16 -> out.add(
                    Advisory(Kind.INFO, "High UV today \u00B7 index ${round(c.uvIndexMax)}")
                )
            }
        }

        when {
            windKmh >= 50 -> out.add(
                Advisory(Kind.SAFETY, "Very windy \u00B7 ${windSpeedLabel(c)}")
            )
            windKmh >= 35 -> out.add(Advisory(Kind.INFO, "Windy today"))
        }

        when {
            apparentC >= 34 -> out.add(
                Advisory(Kind.SAFETY, "Feels oppressive \u2014 stay hydrated")
            )
            apparentC <= -12 -> out.add(
                Advisory(Kind.SAFETY, "Bitterly cold \u2014 cover exposed skin")
            )
        }

        // ----------------------------------------------------- convenience

        // A dry stretch is only worth mentioning if rain follows it.
        if (maxChance >= 40) {
            val dryHours = chances.takeWhile { it < 20 }.size
            if (dryHours >= 3) {
                c.hourly.getOrNull(dryHours - 1)?.label?.let { until ->
                    out.add(Advisory(Kind.INFO, "Dry until about $until"))
                }
            }
        }

        if (c.humidity in 0..54 && windKmh >= 10 && maxChance < 20 && tempC >= 14) {
            out.add(Advisory(Kind.INFO, "Good drying day"))
        }

        if (!dewC.isNaN()) {
            when {
                dewC >= 20 -> out.add(Advisory(Kind.INFO, "Muggy"))
                dewC >= 18 -> out.add(Advisory(Kind.INFO, "Humid"))
            }
        }

        return out
    }

    /** The single most important advisory, or null. */
    fun top(c: OpenMeteoClient.Conditions): Advisory? = forConditions(c).firstOrNull()

    // ------------------------------------------------------------- helpers

    private fun toCelsius(v: Double, metric: Boolean): Double =
        if (v.isNaN()) v else if (metric) v else (v - 32.0) * 5.0 / 9.0

    private fun toKmh(v: Double, metric: Boolean): Double =
        if (v.isNaN()) 0.0 else if (metric) v else v * 1.609344

    /** Wind quoted back in the user's own units. */
    private fun windSpeedLabel(c: OpenMeteoClient.Conditions): String =
        "${round(c.windSpeed)} ${if (c.metric) "km/h" else "mph"}"

    private fun round(v: Double): Int = Math.round(v).toInt()

    private fun currentHour(): Int =
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
}
