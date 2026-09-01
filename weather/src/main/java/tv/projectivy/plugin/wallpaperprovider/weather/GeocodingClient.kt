package tv.projectivy.plugin.wallpaperprovider.weather

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Place search via Open-Meteo's geocoding API. Free, keyless, same provider as
 * the forecast.
 *
 * This exists so locations can be added by name. Typing "Ossining" on a TV
 * remote is tolerable; typing 41.157, -73.862 is not, and a mistyped digit puts
 * the forecast in the wrong county with no obvious sign.
 */
object GeocodingClient {

    private const val TAG = "GeocodingClient"
    private const val TIMEOUT_MS = 12_000
    private const val UA = "ProjectivyWeatherWallpaper"

    data class Place(
        val name: String,
        val admin: String,
        val country: String,
        val latitude: Double,
        val longitude: Double,
        val population: Int
    ) {
        /**
         * "Springfield, Illinois, US" — the region and country matter, because
         * a bare name is frequently ambiguous.
         */
        val label: String
            get() = listOf(name, admin, country)
                .filter { it.isNotBlank() }
                .joinToString(", ")

        /** Short form for the wallpaper itself, where space is tight. */
        val shortLabel: String get() = name
    }

    /** Blocking. Results are ordered by relevance as the API returns them. */
    fun search(query: String, limit: Int = 6): List<Place> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
                "?name=$encoded&count=$limit&language=en&format=json"

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${conn.responseCode}")
                return emptyList()
            }
            val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            // A query with no matches omits "results" entirely rather than
            // returning an empty array.
            val results = root.optJSONArray("results") ?: return emptyList()

            val out = mutableListOf<Place>()
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                val lat = r.optDouble("latitude", Double.NaN)
                val lon = r.optDouble("longitude", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue
                out.add(
                    Place(
                        name = r.optString("name"),
                        admin = r.optString("admin1"),
                        country = r.optString("country_code"),
                        latitude = lat,
                        longitude = lon,
                        population = r.optInt("population", 0)
                    )
                )
            }
            out
        } catch (t: Throwable) {
            Log.w(TAG, "Search failed: ${t.message}")
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }
}
