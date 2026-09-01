package tv.projectivy.plugin.wallpaperprovider.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
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
    /**
     * Must identify this specific application. Tile providers block generic or
     * placeholder agents, and several require a contactable URL.
     */
    private const val UA =
        "ProjectivyWeatherWallpaper/1.7 (+https://github.com/shortusername/projectivy-weather-wallpaper)"

    const val SOURCE_SCENE = "scene"
    const val SOURCE_PACK = "pack"
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
        height: Int,
        phase: ThemeResolver.Phase
    ): Pair<Bitmap, String?>? = try {
        when (source) {
            SOURCE_PACK -> packImage(context, c, width, height, phase)
            SOURCE_LOCAL -> localPhoto(context, c, width, height, phase)?.let { it to null }
            SOURCE_STOCK -> stockPhoto(c, width, height, phase)
            SOURCE_RADAR -> radarMap(context, width, height, phase, includeRadar = true)?.let {
                it to "Radar: RainViewer · Map: © OpenStreetMap contributors"
            }
            else -> null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Background source '$source' failed: ${e.message}")
        null
    }

    // ----------------------------------------------------------------- pack

    /**
     * Static image from the selected community pack. Animated packs never reach
     * here — the service handles those via LottieComposer.
     */
    private fun packImage(
        context: Context,
        c: OpenMeteoClient.Conditions,
        width: Int,
        height: Int,
        phase: ThemeResolver.Phase
    ): Pair<Bitmap, String?>? {
        val id = PreferencesManager.selectedPack
        if (id.isBlank()) return null
        val pack = PackManager.findPack(context, id) ?: return null
        val file = PackManager.resolveAsset(context, pack, c, phase) ?: return null
        val bitmap = decodeScaled(file.readBytes(), width, height) ?: return null
        return bitmap to "${pack.name} by ${pack.author} · ${pack.license}"
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
        height: Int,
        phase: ThemeResolver.Phase
    ): Bitmap? {
        val folder = localFolder(context)
        val images = folder.listFiles { f ->
            f.isFile && f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
        }?.toList().orEmpty()
        if (images.isEmpty()) return null

        val bucket = OpenMeteoClient.bucket(c.weatherCode)
        // Exact phase first, so a file named "dusk-*" wins over a "night-*" one.
        val matches = images.filter { it.name.lowercase().contains(phase.key) }
            .ifEmpty {
                val coarse = if (phase.isDay) "day" else "night"
                images.filter {
                    val n = it.name.lowercase()
                    n.contains(bucket) || n.contains(coarse)
                }
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
        height: Int,
        phase: ThemeResolver.Phase
    ): Pair<Bitmap, String?>? {
        val key = PreferencesManager.unsplashKey.trim()
        if (key.isEmpty()) return null

        val query = when (OpenMeteoClient.bucket(c.weatherCode)) {
            "clear" -> when (phase) {
                ThemeResolver.Phase.DAY -> "blue sky landscape"
                ThemeResolver.Phase.DAWN -> "sunrise landscape"
                ThemeResolver.Phase.DUSK -> "sunset landscape"
                ThemeResolver.Phase.NIGHT -> "starry night sky"
            }
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
     * RainViewer precipitation tiles, optionally over a user-supplied basemap.
     *
     * No basemap ships by default, deliberately. OpenStreetMap's tile usage
     * policy forbids distributing an app that pulls from tile.openstreetmap.org
     * without prior permission, and blocked requests come back as an error tile
     * reading "not supported" rather than failing cleanly.
     *
     * Users who want geography behind the radar supply their own tile URL
     * template from a provider whose terms allow app use. Without one, radar
     * draws over a dark backdrop: less legible, but nobody's servers get abused.
     */
    private fun radarMap(
        context: Context,
        width: Int,
        height: Int,
        phase: ThemeResolver.Phase,
        includeRadar: Boolean = true,
        centre: Pair<Double, Double>? = null,
        zoomOverride: Int? = null
    ): Bitmap? {
        // RainViewer's public tiles stop at zoom 7 — above that every request
        // returns an identical "Zoom Level Not Supported" placeholder image.
        // World events pass their own centre and a wider zoom; otherwise the
        // active location and the user's area setting apply.
        val zoom = (zoomOverride ?: PreferencesManager.radarZoom).coerceIn(3, 7)
        val lat = centre?.first ?: PreferencesManager.currentLatitude
        val lon = centre?.second ?: PreferencesManager.currentLongitude

        val maps = getJson("https://api.rainviewer.com/public/weather-maps.json") ?: return null
        val host = maps.optString("host", "https://tilecache.rainviewer.com")
        val past = maps.optJSONObject("radar")?.optJSONArray("past")
        if (past == null || past.length() == 0) return null
        val latestPath = past.getJSONObject(past.length() - 1).optString("path")
        if (latestPath.isBlank()) return null

        val basemapTemplate = PreferencesManager.basemapUrl.trim()
        // A custom tile URL overrides the bundled vectors; otherwise we draw
        // coastlines and borders ourselves from data shipped in the APK.
        val useTiles = basemapTemplate.isNotBlank()

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        // GeographyRenderer paints its own water base; this only shows if the
        // bundled vectors fail to load or a custom tile source is in use.
        canvas.drawColor(Color.parseColor("#0A1420"))

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
        // Local view: the panel occupies the upper left, so push the marker
        // right and down into clear space. World events sit nearer the centre,
        // since their caption strip runs along the bottom instead.
        val fracX = if (centre != null) 0.55 else 0.62
        val fracY = if (centre != null) 0.46 else 0.54
        val left = cxWorld - viewW * fracX
        val top = cyWorld - viewH * fracY

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
        // Two passes: a blurred, low-alpha copy underneath gives precipitation a
        // soft bloom, then the sharp copy on top keeps the detail. Reads much
        // closer to broadcast weather graphics than a single flat overlay.
        val radarGlow = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = 90
            maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
        }
        val radarPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 225 }

        var drewAny = false

        if (!useTiles) {
            // Bundled vectors: no network, no key, no tile usage policy.
            if (GeographyRenderer.draw(
                    context, canvas, zoom, left, top, scale, width, height, phase
            )) {
                drewAny = true
            }
        }

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

                if (useTiles) {
                    val tileUrl = basemapTemplate
                        .replace("{z}", zoom.toString())
                        .replace("{x}", wrappedX.toString())
                        .replace("{y}", ty.toString())
                    getBytes(tileUrl)
                        ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                        ?.let { base ->
                            canvas.drawBitmap(base, null, dst, basePaint)
                            base.recycle()
                            drewAny = true
                        }
                }

                if (!includeRadar) continue
                getBytes("$host$latestPath/$tileSize/$zoom/$wrappedX/$ty/2/1_1.png")
                    ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    ?.let { radar ->
                        val glowDst = RectF(
                            dst.left - 3f, dst.top - 3f, dst.right + 3f, dst.bottom + 3f
                        )
                        canvas.drawBitmap(radar, null, glowDst, radarGlow)
                        canvas.drawBitmap(radar, null, dst, radarPaint)
                        radar.recycle()
                        drewAny = true
                    }
            }
        }

        if (!drewAny) {
            out.recycle()
            return null
        }

        // Depth: darken the outer edges so the centre reads as the subject.
        val vignette = Paint().apply {
            shader = android.graphics.RadialGradient(
                width / 2f, height / 2f, width * 0.72f,
                intArrayOf(Color.TRANSPARENT, Color.argb(60, 0, 4, 10)),
                floatArrayOf(0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignette)

        // Marker for the configured location. Suppressed in demo mode, though
        // note the basemap itself still identifies the area.
        if (!PreferencesManager.demoMode && centre == null) {
            val mx = (width * fracX).toFloat()
            val my = (height * fracY).toFloat()
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(120, 0, 0, 0)
            }
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = Color.argb(110, 255, 255, 255)
            }
            canvas.drawCircle(mx, my, 11f, halo)
            canvas.drawCircle(mx, my, 7f, dot)
            canvas.drawCircle(mx, my, 19f, ring)
            canvas.drawCircle(mx, my, 29f, Paint(ring).apply { alpha = 55 })
        }

        return out
    }

    /** Credit line: RainViewer always, plus whatever the basemap needs. */
    private fun radarAttribution(): String {
        val extra = PreferencesManager.basemapAttribution.trim()
        return when {
            extra.isNotEmpty() -> "Radar: RainViewer \u00B7 $extra"
            PreferencesManager.basemapUrl.isBlank() ->
                "Radar: RainViewer \u00B7 Map: Natural Earth \u00B7 Places: GeoNames (CC BY)"
            else -> "Radar: RainViewer"
        }
    }

    // ------------------------------------------------------------ animation

    /**
     * Map centred on an arbitrary point, for World weather watch.
     *
     * Radar coverage outside the US, Europe and Japan is patchy, so this may
     * well return a map with no precipitation on it at all. That's expected —
     * the geography and the caption carry the view.
     */
    fun eventMap(
        context: Context,
        width: Int,
        height: Int,
        phase: ThemeResolver.Phase,
        lat: Double,
        lon: Double,
        zoom: Int
    ): Bitmap? = try {
        radarMap(
            context, width, height, phase,
            includeRadar = true, centre = lat to lon, zoomOverride = zoom
        )
    } catch (t: Throwable) {
        Log.w(TAG, "Event map failed: ${t.message}")
        null
    }

    /** The radar map with no precipitation drawn: basemap, borders, labels. */
    fun radarBaseMap(
        context: Context,
        width: Int,
        height: Int,
        phase: ThemeResolver.Phase
    ): Bitmap? =
        try { radarMap(context, width, height, phase, includeRadar = false) }
        catch (e: Exception) { Log.w(TAG, "Base map failed: ${e.message}"); null }

    /**
     * The most recent radar observations as transparent overlays, oldest first.
     *
     * RainViewer publishes roughly 13 past frames covering two hours. Taking
     * every other one gives a smoother-feeling loop over the same span for half
     * the payload.
     */
    fun radarFrames(context: Context, width: Int, height: Int, count: Int): List<ByteArray> {
        val maps = getJson("https://api.rainviewer.com/public/weather-maps.json")
            ?: return emptyList()
        val host = maps.optString("host", "https://tilecache.rainviewer.com")
        val past = maps.optJSONObject("radar")?.optJSONArray("past") ?: return emptyList()

        val paths = mutableListOf<String>()
        var i = past.length() - 1
        while (i >= 0 && paths.size < count) {
            past.optJSONObject(i)?.optString("path")?.takeIf { it.isNotBlank() }
                ?.let { paths.add(it) }
            i -= 2
        }
        paths.reverse()

        // Encode and release each frame before fetching the next. Holding seven
        // full-size ARGB bitmaps at once is ~56 MB and will OOM on a TV box —
        // that was the cause of the blank wallpaper in 2.0.
        return paths.mapNotNull { path ->
            var frame: Bitmap? = null
            try {
                frame = radarLayerOnly(context, width, height, host, path)
                frame?.let { RadarAnimator.encodeFrame(it) }
            } catch (t: Throwable) {
                Log.w(TAG, "Frame failed: ${t.message}")
                null
            } finally {
                frame?.recycle()
            }
        }
    }

    /** Precipitation only, on a transparent canvas, matching radarMap's grid. */
    private fun radarLayerOnly(
        context: Context,
        width: Int,
        height: Int,
        host: String,
        path: String
    ): Bitmap? {
        val zoom = PreferencesManager.radarZoom.coerceIn(4, 7)
        val lat = PreferencesManager.currentLatitude
        val lon = PreferencesManager.currentLongitude

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val n = 1 shl zoom
        val tileSize = 256
        val cxWorld = (lon + 180.0) / 360.0 * n * tileSize
        val latRad = Math.toRadians(lat)
        val cyWorld = (1.0 - ln(tan(latRad) + 1.0 / Math.cos(latRad)) / PI) / 2.0 * n * tileSize

        val scale = width / (3.0 * tileSize)
        val viewW = width / scale
        val viewH = height / scale
        val left = cxWorld - viewW * 0.62
        val top = cyWorld - viewH * 0.54

        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 225 }
        var drew = 0

        for (tx in floor(left / tileSize).toInt()..floor((left + viewW) / tileSize).toInt()) {
            for (ty in floor(top / tileSize).toInt()..floor((top + viewH) / tileSize).toInt()) {
                if (ty < 0 || ty >= n) continue
                val wrappedX = ((tx % n) + n) % n
                val dstLeft = ((tx * tileSize - left) * scale).toFloat()
                val dstTop = ((ty * tileSize - top) * scale).toFloat()
                val dst = RectF(
                    dstLeft, dstTop,
                    dstLeft + (tileSize * scale).toFloat(),
                    dstTop + (tileSize * scale).toFloat()
                )
                getBytes("$host$path/$tileSize/$zoom/$wrappedX/$ty/2/1_1.png")
                    ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    ?.let { tile ->
                        canvas.drawBitmap(tile, null, dst, paint)
                        tile.recycle()
                        drew++
                    }
            }
        }

        if (drew == 0) { out.recycle(); return null }
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
