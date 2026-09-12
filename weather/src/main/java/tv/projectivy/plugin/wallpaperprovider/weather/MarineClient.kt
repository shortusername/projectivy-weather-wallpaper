package tv.projectivy.plugin.wallpaperprovider.weather

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Wave conditions from Open-Meteo's marine API. Free, keyless, same provider
 * as the forecast.
 *
 * Only meaningful near a large enough body of water. Checked live before
 * building this: the API itself returns explicit null for landlocked
 * coordinates (Denver, central Kansas) rather than a fabricated nearest-water
 * value, so gating on that null is a genuine signal from the data, not a
 * boundary this plugin has to guess at — unlike the satellite feature, where
 * no such signal existed and a coverage boundary had to be computed by hand.
 *
 * Coverage includes the Great Lakes as well as the ocean — Chicago on Lake
 * Michigan returns real wave data, not just coastal cities — so this reads
 * more accurately as "large open water nearby" than "ocean only."
 */
object MarineClient {

    private const val TAG = "MarineClient"
    private const val TIMEOUT_MS = 12_000

    data class Conditions(
        /** Metres, converted to feet by the caller if the user wants imperial. */
        val waveHeightM: Double,
        val wavePeriodS: Double,
        val waveDirectionDeg: Int
    )

    /** Blocking. Null when unavailable — including simply "no water nearby." */
    fun fetch(lat: Double, lon: Double): Conditions? {
        val url = "https://marine-api.open-meteo.com/v1/marine" +
                "?latitude=$lat&longitude=$lon" +
                "&current=wave_height,wave_period,wave_direction&timezone=auto"
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
            val current = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                .optJSONObject("current") ?: return null

            // The landlocked case returns the keys present but explicitly
            // JSON null, not absent — isNull() is the documented, unambiguous
            // way to check that, rather than relying on optDouble's behaviour
            // on a null value, which isn't something worth assuming without
            // being able to verify it directly.
            if (current.isNull("wave_height") || current.isNull("wave_period")) {
                return null
            }
            val height = current.optDouble("wave_height", Double.NaN)
            val period = current.optDouble("wave_period", Double.NaN)
            if (height.isNaN() || period.isNaN()) return null

            Conditions(
                waveHeightM = height,
                wavePeriodS = period,
                waveDirectionDeg = current.optInt("wave_direction", 0)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Marine fetch failed: ${t.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** "Waves 2 ft, 6 s" or "Waves 0.6 m, 6 s", matching the user's units. */
    fun label(c: Conditions, metric: Boolean): String {
        val height = if (metric) {
            "${"%.1f".format(c.waveHeightM)} m"
        } else {
            val feet = (c.waveHeightM * 3.28084).let { Math.round(it * 10) / 10.0 }
            "$feet ft"
        }
        val period = "${Math.round(c.wavePeriodS)} s"
        return "Waves $height, $period"
    }
}
