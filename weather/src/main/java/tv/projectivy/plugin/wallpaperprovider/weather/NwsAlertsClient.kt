package tv.projectivy.plugin.wallpaperprovider.weather

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Active severe weather alerts from the US National Weather Service.
 *
 * Free, no API key, no rate limit worth worrying about. US and its territories
 * only — outside that coverage the endpoint returns an empty list, which we
 * treat the same as "no alerts" rather than as an error.
 *
 * NWS asks that clients send a User-Agent identifying the application with a
 * contact, so they can reach you if your client misbehaves.
 */
object NwsAlertsClient {

    private const val TAG = "NwsAlertsClient"
    private const val TIMEOUT_MS = 10_000
    private const val UA =
        "ProjectivyWeatherWallpaper/2.0 (+https://github.com/shortusername/projectivy-weather-wallpaper)"

    /** Ordered most severe first; used to pick which alert to show. */
    private val SEVERITY_ORDER = listOf("Extreme", "Severe", "Moderate", "Minor", "Unknown")

    data class Alert(
        val event: String,
        val severity: String,
        val urgency: String,
        val headline: String,
        val ends: String
    ) {
        /** Rank for sorting: lower is more serious. */
        val rank: Int get() = SEVERITY_ORDER.indexOf(severity).let { if (it < 0) 99 else it }
    }

    fun fetch(lat: Double, lon: Double): List<Alert> {
        val url = "https://api.weather.gov/alerts/active?point=$lat,$lon"
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/geo+json")
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${conn.responseCode}")
                return emptyList()
            }
            val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val features = root.optJSONArray("features") ?: return emptyList()

            val out = mutableListOf<Alert>()
            for (i in 0 until features.length()) {
                val p = features.getJSONObject(i).optJSONObject("properties") ?: continue
                // Cancellations and expiries arrive as features too; only show live ones.
                if (p.optString("messageType") == "Cancel") continue
                val event = p.optString("event").ifBlank { continue }
                out.add(
                    Alert(
                        event = event,
                        severity = p.optString("severity", "Unknown"),
                        urgency = p.optString("urgency", "Unknown"),
                        headline = p.optString("headline", ""),
                        ends = p.optString("ends").ifBlank { p.optString("expires") }
                    )
                )
            }
            out.sortedBy { it.rank }
        } catch (e: Exception) {
            Log.w(TAG, "Alerts fetch failed: ${e.message}")
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    /** Banner colour by severity. Extreme and Severe get warm, urgent tones. */
    fun colorFor(severity: String): Int = when (severity) {
        "Extreme" -> 0xFF8E1B2E.toInt()
        "Severe" -> 0xFFA8461A.toInt()
        "Moderate" -> 0xFF8A6410.toInt()
        else -> 0xFF2E4A66.toInt()
    }

    /** "…until 2:00 AM", or empty when the end time is missing or unparseable. */
    fun untilLabel(ends: String): String {
        if (ends.length < 16) return ""
        return try {
            val hour = ends.substring(11, 13).toInt()
            val minute = ends.substring(14, 16)
            val suffix = if (hour < 12) "AM" else "PM"
            val h12 = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            "until $h12:$minute $suffix"
        } catch (_: Exception) {
            ""
        }
    }
}
