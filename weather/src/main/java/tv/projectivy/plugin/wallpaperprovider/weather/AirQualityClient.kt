package tv.projectivy.plugin.wallpaperprovider.weather

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Air quality and pollen, from Open-Meteo's air-quality endpoint. Free, keyless,
 * same provider as the forecast.
 *
 * Coverage differs by region and the code has to respect that: AQI is global,
 * but pollen comes from the CAMS Europe model and is only returned for Europe.
 * Checked against five continents — London and Berlin return six species, New
 * York, Delhi and Sydney return none. Outside Europe the pollen line simply
 * doesn't appear rather than showing zeroes.
 */
object AirQualityClient {

    private const val TAG = "AirQualityClient"
    private const val TIMEOUT_MS = 12_000

    private const val FIELDS =
        "us_aqi,pm2_5,alder_pollen,birch_pollen,grass_pollen," +
                "mugwort_pollen,olive_pollen,ragweed_pollen"

    data class Reading(
        /** US AQI, 0-500. Chosen because it's returned worldwide. */
        val aqi: Int,
        val pm25: Double,
        /** Highest pollen species and its concentration, when available. */
        val topPollen: Pair<String, Double>?
    ) {
        /** True once the AQI reaches the "unhealthy for sensitive groups" band. */
        val notable: Boolean get() = aqi >= 101
    }

    fun fetch(lat: Double, lon: Double): Reading? {
        val url = "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=$lat&longitude=$lon&current=$FIELDS&timezone=auto"
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

            val aqi = current.optDouble("us_aqi", Double.NaN)
            if (aqi.isNaN()) return null

            // Nulls outside Europe, so the maximum is taken over what exists.
            val species = mapOf(
                "Alder" to "alder_pollen",
                "Birch" to "birch_pollen",
                "Grass" to "grass_pollen",
                "Mugwort" to "mugwort_pollen",
                "Olive" to "olive_pollen",
                "Ragweed" to "ragweed_pollen"
            )
            val top = species.mapNotNull { (label, key) ->
                val v = current.optDouble(key, Double.NaN)
                if (v.isNaN() || v <= 0.0) null else label to v
            }.maxByOrNull { it.second }

            Reading(
                aqi = aqi.toInt(),
                pm25 = current.optDouble("pm2_5", Double.NaN),
                topPollen = top
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Air quality fetch failed: ${t.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Standard US AQI band names. */
    fun bandName(aqi: Int): String = when {
        aqi <= 50 -> "Good"
        aqi <= 100 -> "Moderate"
        aqi <= 150 -> "Unhealthy for sensitive groups"
        aqi <= 200 -> "Unhealthy"
        aqi <= 300 -> "Very unhealthy"
        else -> "Hazardous"
    }

    fun bandColour(aqi: Int): Int = when {
        aqi <= 50 -> 0xFF9BD98A.toInt()
        aqi <= 100 -> 0xFFE8D07A.toInt()
        aqi <= 150 -> 0xFFF0A868.toInt()
        aqi <= 200 -> 0xFFE87878.toInt()
        aqi <= 300 -> 0xFFC98AC8.toInt()
        else -> 0xFFC06868.toInt()
    }

    /** Compact form for the stats line: "AQI 53". */
    fun shortLabel(r: Reading): String = "AQI ${r.aqi}"

    /**
     * Prominent line, only once the AQI is worth acting on.
     *
     * A daily "Good 53" is noise; "Unhealthy 162" is worth interrupting for.
     */
    fun alertLabel(r: Reading): String? =
        if (r.notable) "Air quality ${bandName(r.aqi).lowercase()} \u00B7 AQI ${r.aqi}"
        else null

    /**
     * Pollen band from grains per cubic metre.
     *
     * Thresholds vary by species and by national convention; these are broad
     * bands rather than anything authoritative, which is why the label says
     * "high" instead of quoting a number.
     */
    fun pollenLabel(r: Reading): String? {
        val (name, value) = r.topPollen ?: return null
        val band = when {
            value >= 200 -> "very high"
            value >= 50 -> "high"
            value >= 10 -> "moderate"
            else -> return null   // low pollen isn't worth a line
        }
        return "$name pollen $band"
    }
}
