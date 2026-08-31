package tv.projectivy.plugin.wallpaperprovider.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.tan

/**
 * Background sources for the wallpaper. Each returns a bitmap sized to the
 * canvas, or null to fall back to the gradient.
 *
 * All network work happens on the binder thread, which is off the UI thread by
 * contract, and every path is wrapped so a failure degrades to the gradient
 * rather than killing the wallpaper.
 */
object Backgrounds {

    private const val TAG = "Backgrounds"
    private const val TIMEOUT_MS = 10_000
    private const val UA = "ProjectivyWeatherWallpaper/1.1 (+https://github.com/)"

    const val SOURCE_GRADIENT = "gradient"
    const val SOURCE_LOCAL = "local"
    const val SOURCE_STOCK = "stock"
    const val SOURCE_RADAR = "radar"

    /** Folder the user drops images into. No runtime permission needed. */
    fun localFolder(context: Context): File =
        File(context.getExternalFilesDir(null), "wallpapers").apply { mkdirs() }

    fun resolve(
        context: Context,
        source: String,
        c: OpenMeteoClient.Conditions,
        width: Int,
        height: Int
    ): Pair<Bitmap, String?>? = try {
        when (source) {
            SOURCE_LOCAL -> localPhoto(context, c, width, height)?.let { it to null }
            SOURCE_STOCK -> stockPhoto(c, width, height)
            SOURCE_RADAR -> radarMap(width, height)?.let {
                it to "Radar: RainViewer · Map: © OpenStreetMap contributors"
            }
            else -> null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Background source '$source' failed: ${e.message}")
        null
    }

    // ---------------------------------------------------------------- local

    /**
     * Picks from <externalFilesDir>/wallpapers. A file whose name contains the
     * condition bucket ("rain-1.jpg") is preferred, so you can tailor images to
     * conditions; otherwise any image in the folder is used.
     */
    private fun localPhoto(
        context: Context,
        c: OpenMeteoClient.Conditions,
        width: Int,
        height: Int
    ): Bitmap? {
        val folder = localFolder(context)
        val images = folder.listFiles { f ->
            f.isFile && f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
        }?.toList().orEmpty()
        if (images.isEmpty()) return null

        val bucket = OpenMeteoClient.bucket(c.weatherCode)
        val timeTag = if (c.isDay) "day" else "night"
        val matches = images.filter {
            val n = it.name.lowercase()
            n.contains(bucket) || n.contains(timeTag)
        }
        val pool = matches.ifEmpty { images }

        // Rotate through the pool over time rather than picking at random, so
        // successive refreshes don't repeat the same image.
        val index = ((System.currentTimeMillis() / 900_000L) % pool.size).toInt()
        val chosen = pool[index]

        return decodeScaled(chosen.readBytes(), width, height)
    }

    // ---------------------------------------------------------------- stock

    /**
     * Unsplash random photo matched to conditions.
     *
     * Needs a free Access Key from unsplash.com/developers. Their API terms
     * require attribution to the photographer and a call to the download
     * endpoint when a photo is used, both of which this does.
     */
    private fun stockPhoto(
        c: OpenMeteoClient.Conditions,
        width: Int,
        height: Int
    ): Pair<Bitmap, String?>? {
        val key = PreferencesManager.unsplashKey.trim()
        if (key.isEmpty()) return null

        val query = when (OpenMeteoClient.bucket(c.weatherCode)) {
            "clear" -> if (c.isDay) "blue sky landscape" else "starry night sky"
            "cloud" -> "overcast clouds landscape"
            "rain" -> "rainy landscape"
            "snow" -> "snowy landscape"
            "storm" -> "storm clouds landscape"
            else -> "landscape"
        }

        val meta = getJson(
            "https://api.unsplash.com/photos/random" +
                    "?query=${query.replace(" ", "%20")}&orientation=landscape&content_filter=high",
            mapOf("Authorization" to "Client-ID $key")
        ) ?: return null

        val urls = meta.optJSONObject("urls") ?: return null
        val imageUrl = urls.optString("regular").ifBlank { urls.optString("full") }
        if (imageUrl.isBlank()) return null

        val bytes = getBytes(imageUrl) ?: return null
        val bitmap = decodeScaled(bytes, width, height) ?: return null

        val photographer = meta.optJSONObject("user")?.optString("name").orEmpty()

        // Required by Unsplash's API guidelines when a photo is actually used.
        meta.optJSONObject("links")?.optString("download_location")?.let { loc ->
            if (loc.isNotBlank()) {
                runCatching { getJson(loc, mapOf("Authorization" to "Client-ID $key")) }
            }
        }

        val credit = if (photographer.isNotBlank()) "Photo: $photographer / Unsplash" else "Unsplash"
        return bitmap to credit
    }

    // ---------------------------------------------------------------- radar

    /**
     * RainViewer precipitation tiles over a darkened OpenStreetMap basemap.
     *
     * RainViewer's public tiles need no key and are free for personal use.
     * Radar tiles are transparent, so without a basemap you'd see rain blobs
     * floating with no coastlines for reference.
     */
    private fun radarMap(width: Int, height: Int): Bitmap? {
        val zoom = PreferencesManager.radarZoom.coerceIn(4, 9)
        val lat = PreferencesManager.latitude
        val lon = PreferencesManager.longitude

        val maps = getJson("https://api.rainviewer.com/public/weather-maps.json") ?: return null
        val host = maps.optString("host", "https://tilecache.rainviewer.com")
        val past = maps.optJSONObject("radar")?.optJSONArray("past")
        if (past == null || past.length() == 0) return null
        val latestPath = past.getJSONObject(past.length() - 1).optString("path")
        if (latestPath.isBlank()) return null

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.parseColor("#0F1620"))

        val n = 1 shl zoom
        val tileSize = 256
        // World pixel coords of the centre point at this zoom.
        val cxWorld = (lon + 180.0) / 360.0 * n * tileSize
        val latRad = Math.toRadians(lat)
        val cyWorld = (1.0 - ln(tan(latRad) + 1.0 / Math.cos(latRad)) / PI) / 2.0 * n * tileSize

        // Scale so 3 tiles across fills the canvas width.
        val scale = width / (3.0 * tileSize)
        val viewW = width / scale
        val viewH = height / scale
        val left = cxWorld - viewW / 2.0
        val top = cyWorld - viewH / 2.0

        val tileXStart = floor(left / tileSize).toInt()
        val tileXEnd = floor((left + viewW) / tileSize).toInt()
        val tileYStart = floor(top / tileSize).toInt()
        val tileYEnd = floor((top + viewH) / tileSize).toInt()

        // Desaturate and darken the basemap so white text and the radar colours
        // stay readable on top of it.
        val darken = ColorMatrix().apply {
            setSaturation(0.25f)
            postConcat(ColorMatrix(floatArrayOf(
                0.55f, 0f, 0f, 0f, -18f,
                0f, 0.55f, 0f, 0f, -18f,
                0f, 0f, 0.62f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        val basePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(darken)
        }
        val radarPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 235 }

        var drewAny = false
        for (tx in tileXStart..tileXEnd) {
            for (ty in tileYStart..tileYEnd) {
                val wrappedX = ((tx % n) + n) % n
                if (ty < 0 || ty >= n) continue

                val dstLeft = ((tx * tileSize - left) * scale).toFloat()
                val dstTop = ((ty * tileSize - top) * scale).toFloat()
                val dst = RectF(
                    dstLeft, dstTop,
                    dstLeft + (tileSize * scale).toFloat(),
                    dstTop + (tileSize * scale).toFloat()
                )

                getBytes("https://tile.openstreetmap.org/$zoom/$wrappedX/$ty.png")
                    ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    ?.let { base ->
                        canvas.drawBitmap(base, null, dst, basePaint)
                        base.recycle()
                        drewAny = true
                    }

                getBytes("$host$latestPath/$tileSize/$zoom/$wrappedX/$ty/2/1_1.png")
                    ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    ?.let { radar ->
                        canvas.drawBitmap(radar, null, dst, radarPaint)
                        radar.recycle()
                    }
            }
        }

        if (!drewAny) {
            out.recycle()
            return null
        }

        // Marker for the configured location.
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.argb(200, 255, 255, 255)
        }
        canvas.drawCircle(width / 2f, height / 2f, 7f, dot)
        canvas.drawCircle(width / 2f, height / 2f, 18f, ring)

        return out
    }

    // ---------------------------------------------------------------- shared

    private fun decodeScaled(bytes: ByteArray, width: Int, height: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null

        // Downsample while decoding so a 6000px stock photo doesn't allocate
        // 100+ MB on a TV box with a modest heap.
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2

        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        // Centre-crop to the target aspect.
        val scale = max(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
        val scaledW = (decoded.width * scale).toInt()
        val scaledH = (decoded.height * scale).toInt()
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            decoded,
            Rect(0, 0, decoded.width, decoded.height),
            Rect((width - scaledW) / 2, (height - scaledH) / 2,
                (width - scaledW) / 2 + scaledW, (height - scaledH) / 2 + scaledH),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        decoded.recycle()
        return out
    }

    private fun getBytes(url: String): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", UA)
            }
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            Log.w(TAG, "GET failed ($url): ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            if (conn.responseCode !in 200..299) return null
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            Log.w(TAG, "JSON failed ($url): ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
