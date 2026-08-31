package tv.projectivy.plugin.wallpaperprovider.weather

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Open-Meteo client. No API key, no third-party HTTP dependency:
 * HttpURLConnection + org.json are both in the Android platform.
 */
object OpenMeteoClient {

    private const val TAG = "OpenMeteoClient"
    private const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"
    private const val TIMEOUT_MS = 8_000

    data class Conditions(
        val temperature: Double,
        val apparentTemperature: Double,
        val weatherCode: Int,
        val isDay: Boolean,
        val windSpeed: Double,
        val high: Double,
        val low: Double,
        val unitSuffix: String
    )

    fun fetch(lat: Double, lon: Double, metric: Boolean): Conditions? {
        val tempUnit = if (metric) "celsius" else "fahrenheit"
        val windUnit = if (metric) "kmh" else "mph"
        val url = "$ENDPOINT?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&temperature_unit=$tempUnit&wind_speed_unit=$windUnit&forecast_days=1&timezone=auto"

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

            Conditions(
                temperature = current.getDouble("temperature_2m"),
                apparentTemperature = current.getDouble("apparent_temperature"),
                weatherCode = current.getInt("weather_code"),
                isDay = current.getInt("is_day") == 1,
                windSpeed = current.getDouble("wind_speed_10m"),
                high = daily.getJSONArray("temperature_2m_max").getDouble(0),
                low = daily.getJSONArray("temperature_2m_min").getDouble(0),
                unitSuffix = if (metric) "°C" else "°F"
            )
        } catch (e: Exception) {
            // Never let a network blip crash the launcher's binder call.
            Log.w(TAG, "Fetch failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
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
        else -> "—"
    }

    /** Coarse bucket used to pick the background gradient. */
    fun bucket(code: Int): String = when (code) {
        0, 1 -> "clear"
        2, 3, 45, 48 -> "cloud"
        in 51..67, in 80..82 -> "rain"
        in 71..77, 85, 86 -> "snow"
        in 95..99 -> "storm"
        else -> "cloud"
    }
}
