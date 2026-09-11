package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real cloud imagery from NOAA's GOES satellites. Public domain, keyless — a
 * US government work, same legal footing as the NWS alerts and NOAA K-index
 * already used elsewhere in the plugin.
 *
 * Verified live before building this: both endpoints return real, decodable
 * JPEGs, not placeholder tiles — checked pixel content directly rather than
 * trusting the HTTP status, after an EUMETSAT endpoint that returned 200 with
 * a solid-white blank image turned out to need registration this plugin
 * doesn't have.
 *
 * This is deliberately scoped to what two keyless, confirmed-working images
 * can honestly cover, using the same pattern as other regional-limited
 * features (NWS alerts US-only, pollen Europe-only): a close-up crop for the
 * continental US, a whole-hemisphere "Earth from space" view for anywhere
 * else within either satellite's usable disk, and a plain "not available
 * here" for the gap between them — currently the Middle East through China
 * and Southeast Asia, where neither GOES satellite has a usable view.
 *
 * No attempt is made to crop the full-disk image to the user's specific
 * location. Precisely reprojecting a point onto a geostationary full-disk
 * image needs the actual GOES fixed-grid projection formula, is easy to get
 * subtly wrong, and the failure mode — quietly showing the wrong part of the
 * planet — is much worse than the honest "here's Earth from the nearest
 * weather satellite" framing used instead.
 */
object SatelliteClient {

    private const val TAG = "SatelliteClient"
    private const val TIMEOUT_MS = 15_000

    // Real subpoints (degrees longitude) for the two operational GOES
    // satellites, and the +/-81 degree half-angle NOAA documents as the
    // usable full-disk extent before limb distortion becomes severe.
    private const val GOES_EAST_SUBPOINT = -75.2
    private const val GOES_WEST_SUBPOINT = -137.2
    private const val USABLE_HALF_ANGLE = 81.0

    private const val CONUS_URL =
        "https://cdn.star.nesdis.noaa.gov/GOES19/ABI/CONUS/GEOCOLOR/1250x750.jpg"
    private const val GOES_EAST_FULL_DISK_URL =
        "https://cdn.star.nesdis.noaa.gov/GOES19/ABI/FD/GEOCOLOR/678x678.jpg"
    private const val GOES_WEST_FULL_DISK_URL =
        "https://cdn.star.nesdis.noaa.gov/GOES18/ABI/FD/GEOCOLOR/678x678.jpg"

    private fun normalize(lon: Double): Double {
        var l = lon
        while (l > 180) l -= 360
        while (l < -180) l += 360
        return l
    }

    /** Angular distance between two longitudes, shortest way round, 0-180. */
    private fun angularDistance(a: Double, b: Double): Double {
        val d = kotlin.math.abs(normalize(a) - normalize(b))
        return if (d > 180) 360 - d else d
    }

    private fun isConus(lat: Double, lon: Double) =
        lat in 24.0..50.0 && lon in -125.0..-66.0

    /**
     * Which image to use for this location, or null if it falls in the gap
     * between the two satellites' usable disks.
     */
    private fun pickSource(lat: Double, lon: Double): Pair<String, String>? {
        if (isConus(lat, lon)) {
            return CONUS_URL to "Satellite: NOAA GOES-East (CONUS)"
        }
        val eastDist = angularDistance(lon, GOES_EAST_SUBPOINT)
        val westDist = angularDistance(lon, GOES_WEST_SUBPOINT)
        return when {
            eastDist <= USABLE_HALF_ANGLE && eastDist <= westDist ->
                GOES_EAST_FULL_DISK_URL to "Satellite: NOAA GOES-East (full disk)"
            westDist <= USABLE_HALF_ANGLE ->
                GOES_WEST_FULL_DISK_URL to "Satellite: NOAA GOES-West (full disk)"
            else -> null
        }
    }

    /** True when a satellite view is available for this location at all. */
    fun isAvailable(lat: Double, lon: Double): Boolean = pickSource(lat, lon) != null

    /**
     * Fetches the appropriate image, scaled to fit width x height. Null on any
     * failure or when the location falls in the uncovered gap — callers should
     * fall back to the default background in either case.
     */
    fun fetch(lat: Double, lon: Double, width: Int, height: Int): Pair<Bitmap, String>? {
        val (url, credit) = pickSource(lat, lon) ?: return null
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "ProjectivyWeatherWallpaper")
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${conn.responseCode} for $url")
                return null
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.w(TAG, "Decode failed for $url — not a valid image")
                return null
            }
            val sample = maxOf(
                1, minOf(bounds.outWidth / width, bounds.outHeight / height)
            )
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: return null

            // NOAA bakes its own credit and timestamp into the image itself —
            // a solid caption band across the bottom, measured at roughly
            // 20-30px tall at native resolution on both endpoints used here.
            // Cropped before scaling (so the crop stays proportionally correct
            // regardless of output size) since our own on-screen attribution
            // already credits NOAA, and their caption sitting right where our
            // own attribution line and the launcher's app row also live would
            // otherwise double up.
            val captionPx = (decoded.height * 32 / 678).coerceAtLeast(20)
            val trimmedHeight = (decoded.height - captionPx).coerceAtLeast(1)
            val trimmed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, trimmedHeight)
            if (trimmed !== decoded) decoded.recycle()

            val scaled = Bitmap.createScaledBitmap(trimmed, width, height, true)
            if (scaled !== trimmed) trimmed.recycle()
            scaled to credit
        } catch (t: Throwable) {
            Log.w(TAG, "Satellite fetch failed: ${t.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
