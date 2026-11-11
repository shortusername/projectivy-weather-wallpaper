package tv.projectivy.plugin.wallpaperprovider.weather

import android.util.Log
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wind speed and direction on a coarse grid, for the wind-flow background.
 *
 * One Open-Meteo request carries every grid point (checked: a 25-point request
 * returned 25 results in a single response), so this is one HTTP call per
 * refresh rather than dozens. Wind changes slowly compared with rain, so the
 * result is kept for an hour — which also keeps this well inside the free
 * tier's fair-use limit even if each grid point is counted as its own call.
 *
 * The grid is deliberately coarse. The flow between points is interpolated,
 * which is plenty for the broad patterns a wind map shows at regional zoom, and
 * it keeps the request small.
 */
object WindClient {

    private const val TAG = "WindClient"
    private const val TIMEOUT_MS = 12_000
    private const val TTL_MS = 60 * 60 * 1000L

    const val COLS = 10
    const val ROWS = 6

    /** East/north wind components in m/s on a regular latitude/longitude grid. */
    class Field(
        private val lats: DoubleArray,
        private val lons: DoubleArray,
        private val u: Array<FloatArray>,
        private val v: Array<FloatArray>
    ) {
        /** Writes the interpolated (east, north) wind in m/s into out[0], out[1]. */
        fun sample(lat: Double, lon: Double, out: FloatArray) {
            val cols = lons.size
            val rows = lats.size
            val fx = ((lon - lons[0]) / (lons[cols - 1] - lons[0]) * (cols - 1))
                .coerceIn(0.0, cols - 1.0001)
            val fy = ((lat - lats[0]) / (lats[rows - 1] - lats[0]) * (rows - 1))
                .coerceIn(0.0, rows - 1.0001)
            val i = fx.toInt()
            val j = fy.toInt()
            val tx = (fx - i).toFloat()
            val ty = (fy - j).toFloat()
            out[0] = bilinear(u, i, j, tx, ty)
            out[1] = bilinear(v, i, j, tx, ty)
        }

        private fun bilinear(a: Array<FloatArray>, i: Int, j: Int, tx: Float, ty: Float): Float =
            a[j][i] * (1 - tx) * (1 - ty) + a[j][i + 1] * tx * (1 - ty) +
                a[j + 1][i] * (1 - tx) * ty + a[j + 1][i + 1] * tx * ty
    }

    private var cachedKey: String? = null
    private var cachedField: Field? = null
    private var cachedAt = 0L

    /**
     * Blocking. Null when the data can't be had; if an earlier fetch for the
     * same area exists, that stale copy is returned instead, since a slightly
     * old wind map is better than no background at all.
     */
    @Synchronized
    fun fetch(latMin: Double, latMax: Double, lonMin: Double, lonMax: Double): Field? {
        val la0 = latMin.coerceIn(-89.0, 89.0)
        val la1 = latMax.coerceIn(-89.0, 89.0)
        val lo0 = lonMin.coerceIn(-180.0, 180.0)
        val lo1 = lonMax.coerceIn(-180.0, 180.0)
        if (la1 - la0 < 0.1 || lo1 - lo0 < 0.1) return null

        // Locale.US throughout: a device set to a comma-decimal language would
        // otherwise format 41.162 as "41,162" and break the request.
        val key = String.format(Locale.US, "%.2f|%.2f|%.2f|%.2f", la0, la1, lo0, lo1)
        val now = System.currentTimeMillis()
        val hit = cachedField
        if (hit != null && key == cachedKey && now - cachedAt < TTL_MS) return hit

        val fresh = download(la0, la1, lo0, lo1)
        if (fresh == null) return if (key == cachedKey) hit else null

        cachedField = fresh
        cachedKey = key
        cachedAt = now
        return fresh
    }

    private fun download(latMin: Double, latMax: Double, lonMin: Double, lonMax: Double): Field? {
        val lats = DoubleArray(ROWS) { latMin + (latMax - latMin) * it / (ROWS - 1) }
        val lons = DoubleArray(COLS) { lonMin + (lonMax - lonMin) * it / (COLS - 1) }

        val latParam = StringBuilder()
        val lonParam = StringBuilder()
        for (j in 0 until ROWS) {
            for (i in 0 until COLS) {
                if (latParam.isNotEmpty()) {
                    latParam.append(',')
                    lonParam.append(',')
                }
                latParam.append(String.format(Locale.US, "%.3f", lats[j]))
                lonParam.append(String.format(Locale.US, "%.3f", lons[i]))
            }
        }
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latParam&longitude=$lonParam" +
            "&current=wind_speed_10m,wind_direction_10m&wind_speed_unit=ms"

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
            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            if (arr.length() != ROWS * COLS) {
                Log.w(TAG, "Expected ${ROWS * COLS} points, got ${arr.length()}")
                return null
            }

            val u = Array(ROWS) { FloatArray(COLS) }
            val v = Array(ROWS) { FloatArray(COLS) }
            for (k in 0 until arr.length()) {
                val cur = arr.optJSONObject(k)?.optJSONObject("current") ?: return null
                // isNull rather than trusting optDouble's behaviour on a JSON
                // null — the same check MarineClient settled on.
                if (cur.isNull("wind_speed_10m") || cur.isNull("wind_direction_10m")) return null
                val speed = cur.getDouble("wind_speed_10m")
                // Meteorological convention: the direction the wind blows FROM,
                // so the vector points the opposite way.
                val dir = Math.toRadians(cur.getDouble("wind_direction_10m"))
                val j = k / COLS
                val i = k % COLS
                u[j][i] = (-speed * sin(dir)).toFloat()
                v[j][i] = (-speed * cos(dir)).toFloat()
            }
            Field(lats, lons, u, v)
        } catch (t: Throwable) {
            Log.w(TAG, "Wind fetch failed: ${t.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
