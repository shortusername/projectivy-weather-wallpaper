package tv.projectivy.plugin.wallpaperprovider.weather

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Approximate location from the public IP, used once on first run so the plugin
 * shows something sensible before anyone opens settings.
 *
 * Android TV boxes have no GPS and the location permission would buy us nothing,
 * so this is the pragmatic option. Accuracy is ISP-level — usually the right
 * metro area, sometimes a town or two off. Settings always wins once set.
 *
 * Privacy: this sends a request to ipapi.co, which sees the device's public IP.
 * Set AUTO_LOCATE_ENABLED to false to compile it out of the flow entirely.
 */
object IpLocationClient {

    private const val TAG = "IpLocationClient"
    private const val ENDPOINT = "https://ipapi.co/json/"
    private const val TIMEOUT_MS = 6_000

    const val AUTO_LOCATE_ENABLED = true

    data class Location(val latitude: Double, val longitude: Double, val label: String)

    fun lookup(): Location? {
        if (!AUTO_LOCATE_ENABLED) return null

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ProjectivyWeatherWallpaper")
            }
            if (conn.responseCode !in 200..299) return null

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (json.optBoolean("error", false)) return null

            val lat = json.optDouble("latitude", Double.NaN)
            val lon = json.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return null

            val city = json.optString("city", "").ifBlank { "Local weather" }
            Location(lat, lon, city)
        } catch (e: Exception) {
            Log.w(TAG, "IP lookup failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
