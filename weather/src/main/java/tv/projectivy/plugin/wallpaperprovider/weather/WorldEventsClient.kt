package tv.projectivy.plugin.wallpaperprovider.weather

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Notable weather events worldwide, from GDACS — the Global Disaster Alert and
 * Coordination System, run jointly by the UN and the European Commission.
 * Public, keyless, GeoJSON.
 *
 * Two things shape how this is used elsewhere in the plugin:
 *
 * GDACS ranks by expected humanitarian impact, not by how dramatic the weather
 * looks. A severe drought appears; a spectacular blizzard may not. So the list
 * is often short — four current weather events was typical when this was
 * written — and callers must cope with an empty result rather than assuming
 * there is always something to show.
 *
 * These are real disasters affecting real people. The renderer presents them
 * factually and never uses casualty or impact figures as decoration.
 */
object WorldEventsClient {

    private const val TAG = "WorldEventsClient"
    private const val TIMEOUT_MS = 20_000
    private const val UA =
        "ProjectivyWeatherWallpaper (+https://github.com/shortusername/projectivy-weather-wallpaper)"

    private const val LIST_URL =
        "https://www.gdacs.org/gdacsapi/api/events/geteventlist/SEARCH?"

    /**
     * Weather-driven event types only. Earthquakes (EQ) and volcanoes (VO) are
     * in the same feed but aren't weather, so they're excluded.
     */
    private val TYPES = mapOf(
        "TC" to "Tropical cyclone",
        "FL" to "Flood",
        "WF" to "Wildfire",
        "DR" to "Drought"
    )

    data class Event(
        val id: String,
        val type: String,          // human label
        val typeCode: String,      // TC / FL / WF / DR
        val name: String,
        val countries: String,
        val alertLevel: String,    // Red / Orange / Green
        val severityText: String,
        val latitude: Double,
        val longitude: Double
    ) {
        /**
         * True when [name] was generated from the type, e.g. "Flood in Nepal".
         * The renderer skips the separate type line in that case rather than
         * printing "Flood" underneath "Flood in Nepal".
         */
        val nameIsGenerated: Boolean get() = name.startsWith(type, ignoreCase = true)

        /** Red first, then Orange. */
        val rank: Int
            get() = when (alertLevel) {
                "Red" -> 0
                "Orange" -> 1
                else -> 2
            }
    }

    /** Blocking. Returns an empty list on any failure — never throws. */
    fun fetch(): List<Event> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LIST_URL).openConnection() as HttpURLConnection).apply {
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
            val features = root.optJSONArray("features") ?: return emptyList()

            val out = mutableListOf<Event>()
            for (i in 0 until features.length()) {
                val f = features.getJSONObject(i)
                val p = f.optJSONObject("properties") ?: continue

                val code = p.optString("eventtype")
                val label = TYPES[code] ?: continue
                if (!isCurrent(p)) continue

                val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates")
                    ?: continue
                if (coords.length() < 2) continue
                // GeoJSON is [longitude, latitude] — the reverse of how we
                // store user coordinates everywhere else.
                val lon = coords.optDouble(0, Double.NaN)
                val lat = coords.optDouble(1, Double.NaN)
                if (lon.isNaN() || lat.isNaN()) continue

                val severity = p.optJSONObject("severitydata")
                    ?.optString("severitytext").orEmpty()

                out.add(
                    Event(
                        id = p.optString("eventid"),
                        type = label,
                        typeCode = code,
                        name = cleanName(p.optString("eventname"), label,
                            p.optString("country")),
                        countries = countryList(p),
                        alertLevel = p.optString("alertlevel", "Orange"),
                        severityText = cleanSeverity(severity),
                        latitude = lat,
                        longitude = lon
                    )
                )
            }
            out.sortedBy { it.rank }
        } catch (t: Throwable) {
            Log.w(TAG, "World events fetch failed: ${t.message}")
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * affectedcountries is an array of objects, not a string — reading it with
     * optString yields raw JSON, which would end up rendered on screen.
     * Falls back to the plain "country" field, and caps the list so a
     * basin-wide cyclone doesn't produce a caption twelve countries long.
     */
    private fun countryList(p: JSONObject): String {
        val arr = p.optJSONArray("affectedcountries")
        if (arr != null && arr.length() > 0) {
            val names = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val name = arr.optJSONObject(i)?.optString("countryname")?.trim()
                if (!name.isNullOrEmpty() && name != "null") names.add(name)
            }
            if (names.isNotEmpty()) {
                return if (names.size <= 3) names.joinToString(", ")
                else names.take(3).joinToString(", ") + " +${names.size - 3} more"
            }
        }
        return p.optString("country").takeIf {
            it.isNotBlank() && it != "null"
        } ?: ""
    }

    /** The feed carries expired entries alongside live ones. */
    private fun isCurrent(p: JSONObject): Boolean {
        val v = p.opt("iscurrent") ?: return false
        return when (v) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true)
            else -> false
        }
    }

    /**
     * Cyclones get names like "SAUDEL-26"; floods often have no name at all.
     * Falls back to "Flood in Nepal" rather than showing an empty string.
     */
    private fun cleanName(raw: String, label: String, country: String): String {
        // country here may itself be blank; caller passes the plain field.
        val name = raw.trim()
        if (name.isNotEmpty() && !name.equals("null", ignoreCase = true)) {
            // Strip the trailing year suffix on cyclone names.
            return name.substringBeforeLast('-').ifBlank { name }
        }
        return if (country.isNotBlank()) "$label in $country" else label
    }

    /** GDACS emits "Magnitude 0" placeholders for events without a figure. */
    private fun cleanSeverity(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty() || s.startsWith("Magnitude 0")) return ""
        return s
    }

    /** Alert-level colour for the caption strip. */
    fun colorFor(level: String): Int = when (level) {
        "Red" -> 0xFF7E2231.toInt()
        "Orange" -> 0xFF8A5418.toInt()
        else -> 0xFF2A4358.toInt()
    }
}
