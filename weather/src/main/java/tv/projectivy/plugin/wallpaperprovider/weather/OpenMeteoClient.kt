package tv.projectivy.plugin.wallpaperprovider.weather

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Open-Meteo client. No API key, no third-party HTTP dependency:
 * HttpURLConnection + org.json are both in the Android platform.
 *
 * Everything is fetched in one request regardless of which panels are enabled.
 * The extra fields cost nothing meaningful and it keeps us to a single call per
 * refresh even when the user toggles panels on later.
 */
object OpenMeteoClient {

    private const val TAG = "OpenMeteoClient"
    private const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"
    private const val TIMEOUT_MS = 8_000

    data class HourEntry(
        val label: String,
        val temperature: Double,
        val weatherCode: Int,
        val precipChance: Int,
        val isDay: Boolean
    )

    data class DayEntry(
        val label: String,
        val high: Double,
        val low: Double,
        val weatherCode: Int
    )

    data class Conditions(
        val temperature: Double,
        val apparentTemperature: Double,
        val weatherCode: Int,
        val isDay: Boolean,
        val windSpeed: Double,
        val windDirection: Int,
        val high: Double,
        val low: Double,
        val unitSuffix: String,
        val humidity: Int,
        val pressure: Double,
        val visibility: Double,
        val dewPoint: Double,
        val uvIndexMax: Double,
        val sunrise: String,
        val sunset: String,
        val metric: Boolean,
        val hourly: List<HourEntry>,
        val daily: List<DayEntry>
    )

    fun fetch(lat: Double, lon: Double, metric: Boolean): Conditions? {
        val tempUnit = if (metric) "celsius" else "fahrenheit"
        val windUnit = if (metric) "kmh" else "mph"
        val url = "$ENDPOINT?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,weather_code,is_day," +
                "wind_speed_10m,wind_direction_10m,relative_humidity_2m," +
                "surface_pressure,visibility,dew_point_2m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,weather_code," +
                "sunrise,sunset,uv_index_max" +
                "&temperature_unit=$tempUnit&wind_speed_unit=$windUnit" +
                "&forecast_days=6&timezone=auto"

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${conn.responseCode}")
                return null
            }
            val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val current = root.getJSONObject("current")
            val daily = root.getJSONObject("daily")
            val hourly = root.optJSONObject("hourly")
            val nowIso = current.optString("time")

            Conditions(
                temperature = current.getDouble("temperature_2m"),
                apparentTemperature = current.getDouble("apparent_temperature"),
                weatherCode = current.getInt("weather_code"),
                isDay = current.getInt("is_day") == 1,
                windSpeed = current.getDouble("wind_speed_10m"),
                windDirection = current.optInt("wind_direction_10m", -1),
                high = daily.getJSONArray("temperature_2m_max").getDouble(0),
                low = daily.getJSONArray("temperature_2m_min").getDouble(0),
                unitSuffix = if (metric) "\u00B0C" else "\u00B0F",
                humidity = current.optInt("relative_humidity_2m", -1),
                pressure = current.optDouble("surface_pressure", Double.NaN),
                visibility = current.optDouble("visibility", Double.NaN),
                dewPoint = current.optDouble("dew_point_2m", Double.NaN),
                uvIndexMax = daily.optJSONArray("uv_index_max")?.optDouble(0) ?: Double.NaN,
                sunrise = daily.optJSONArray("sunrise")?.optString(0).orEmpty(),
                sunset = daily.optJSONArray("sunset")?.optString(0).orEmpty(),
                metric = metric,
                hourly = parseHourly(hourly, nowIso, 6),
                daily = parseDaily(daily, 5)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Fetch failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * The next N whole hours after "now".
     *
     * Open-Meteo timestamps are fixed-width local ISO ("2026-08-31T14:00"), so a
     * plain string comparison finds the first future hour with no date parsing
     * or timezone handling at all.
     */
    private fun parseHourly(hourly: JSONObject?, nowIso: String, count: Int): List<HourEntry> {
        if (hourly == null || nowIso.isBlank()) return emptyList()
        return try {
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            val codes = hourly.getJSONArray("weather_code")
            val precip = hourly.optJSONArray("precipitation_probability")
            val isDayArr = hourly.optJSONArray("is_day")

            var start = -1
            for (i in 0 until times.length()) {
                if (times.getString(i) > nowIso) { start = i; break }
            }
            if (start < 0) return emptyList()

            val out = mutableListOf<HourEntry>()
            for (i in start until minOf(start + count, times.length())) {
                out.add(
                    HourEntry(
                        label = formatHour(times.getString(i)),
                        temperature = temps.getDouble(i),
                        weatherCode = codes.getInt(i),
                        precipChance = precip?.optInt(i, 0) ?: 0,
                        isDay = (isDayArr?.optInt(i, 1) ?: 1) == 1
                    )
                )
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "Hourly parse failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseDaily(daily: JSONObject, count: Int): List<DayEntry> = try {
        val times = daily.getJSONArray("time")
        val max = daily.getJSONArray("temperature_2m_max")
        val min = daily.getJSONArray("temperature_2m_min")
        val codes = daily.optJSONArray("weather_code")

        val out = mutableListOf<DayEntry>()
        // Start at 1: index 0 is today, already shown as H/L in the detail line.
        for (i in 1 until minOf(1 + count, times.length())) {
            out.add(
                DayEntry(
                    label = dayOfWeek(times.getString(i)),
                    high = max.getDouble(i),
                    low = min.getDouble(i),
                    weatherCode = codes?.optInt(i, 0) ?: 0
                )
            )
        }
        out
    } catch (e: Exception) {
        Log.w(TAG, "Daily parse failed: ${e.message}")
        emptyList()
    }

    /** "2026-08-31T14:00" -> "2 PM" */
    private fun formatHour(iso: String): String = try {
        val hour = iso.substring(11, 13).toInt()
        when {
            hour == 0 -> "12 AM"
            hour < 12 -> "$hour AM"
            hour == 12 -> "12 PM"
            else -> "${hour - 12} PM"
        }
    } catch (_: Exception) {
        ""
    }

    /**
     * "2026-08-31" -> "Mon", via Zeller's congruence.
     *
     * Sidesteps SimpleDateFormat and the java.time desugaring question, which
     * matters on older TV boxes.
     */
    private fun dayOfWeek(isoDate: String): String = try {
        var year = isoDate.substring(0, 4).toInt()
        var month = isoDate.substring(5, 7).toInt()
        val dayOfMonth = isoDate.substring(8, 10).toInt()
        if (month < 3) { month += 12; year -= 1 }
        val k = year % 100
        val j = year / 100
        val h = (dayOfMonth + (13 * (month + 1)) / 5 + k + k / 4 + j / 4 + 5 * j) % 7
        listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")[h]
    } catch (_: Exception) {
        ""
    }

    /** "2026-08-31T06:20" -> "6:20 AM" */
    fun formatTime(iso: String): String = try {
        val hour = iso.substring(11, 13).toInt()
        val minute = iso.substring(14, 16)
        val suffix = if (hour < 12) "AM" else "PM"
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        "$h12:$minute $suffix"
    } catch (_: Exception) {
        ""
    }

    /** Minutes of daylight left, or null once the sun is down. */
    fun minutesUntil(iso: String, nowIso: String): Int? = try {
        val target = iso.substring(11, 13).toInt() * 60 + iso.substring(14, 16).toInt()
        val now = nowIso.substring(11, 13).toInt() * 60 + nowIso.substring(14, 16).toInt()
        if (target <= now) null else target - now
    } catch (_: Exception) {
        null
    }

    /** Degrees to a 16-point compass label. */
    fun compass(degrees: Int): String {
        if (degrees < 0) return ""
        val points = listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
        )
        return points[(((degrees % 360) + 360) % 360 * 16 / 360) % 16]
    }

    /** WMO weather interpretation codes -> short human label. */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm, hail"
        else -> "\u2014"
    }

    /** Coarse bucket used to pick the background scene or gradient. */
    fun bucket(code: Int): String = when (code) {
        0, 1 -> "clear"
        2, 3, 45, 48 -> "cloud"
        in 51..67, in 80..82 -> "rain"
        in 71..77, 85, 86 -> "snow"
        in 95..99 -> "storm"
        else -> "cloud"
    }
}
