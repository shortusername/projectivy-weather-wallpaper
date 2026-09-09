package tv.projectivy.plugin.wallpaperprovider.weather

import android.util.Log
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/**
 * Aurora visibility from NOAA's planetary K-index.
 *
 * The Space Weather Prediction Center publishes Kp as public-domain JSON with
 * no key. Kp is a 0-9 scale of geomagnetic disturbance; the higher it goes, the
 * further from the poles the aurora becomes visible.
 *
 * Nothing on Android TV surfaces this, and the audience for it is real and
 * attentive — at a Kp of 7 in the northern US it is worth walking outside.
 */
object AuroraClient {

    private const val TAG = "AuroraClient"
    private const val TIMEOUT_MS = 12_000
    private const val URL_KP =
        "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"

    data class Conditions(val kp: Double, val visible: Boolean, val marginal: Boolean)

    /**
     * Approximate equatorward limit of visible aurora, in geomagnetic latitude,
     * for each Kp value 0-9.
     *
     * These are the widely used rule-of-thumb figures. Real visibility depends
     * on local darkness, cloud, moonlight and how far north someone can see, so
     * this is treated as "worth looking" rather than a promise.
     */
    private val LIMIT = doubleArrayOf(66.5, 64.5, 62.4, 60.4, 58.3, 56.3, 54.2, 52.2, 50.1, 48.1)

    /** Blocking. Null when unavailable — the caller then shows nothing. */
    fun fetch(latitude: Double): Conditions? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(URL_KP).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "ProjectivyWeatherWallpaper")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) return null

            // An array of objects, newest last. Verified against the live
            // endpoint: each entry is {time_tag, Kp, a_running, station_count},
            // and there is no header row.
            val rows = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            if (rows.length() == 0) return null
            val latest = rows.optJSONObject(rows.length() - 1) ?: return null
            val kp = latest.optDouble("Kp", Double.NaN)
            if (kp.isNaN()) return null

            // Geomagnetic latitude differs from geographic by roughly 10 degrees
            // over North America and Europe, where most users are. A proper
            // conversion needs the IGRF model; this approximation is honest
            // enough for "go and look".
            val geomagnetic = abs(latitude) + if (latitude > 0) 9.0 else 0.0
            val index = kp.toInt().coerceIn(0, 9)
            val limit = LIMIT[index]

            Conditions(
                kp = kp,
                visible = geomagnetic >= limit,
                marginal = geomagnetic >= limit - 3.0 && geomagnetic < limit
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Kp fetch failed: ${t.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Short line for the panel, or null when there's nothing worth saying. */
    fun label(c: Conditions): String? = when {
        c.visible && c.kp >= 7 -> "Aurora likely tonight \u00B7 Kp ${fmt(c.kp)}"
        c.visible -> "Aurora possible tonight \u00B7 Kp ${fmt(c.kp)}"
        c.marginal && c.kp >= 6 -> "Aurora possible far north \u00B7 Kp ${fmt(c.kp)}"
        else -> null
    }

    private fun fmt(kp: Double): String =
        if (kp == kp.toInt().toDouble()) kp.toInt().toString() else String.format("%.1f", kp)
}
