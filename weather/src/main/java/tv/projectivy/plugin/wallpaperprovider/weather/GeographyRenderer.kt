package tv.projectivy.plugin.wallpaperprovider.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/**
 * Draws coastlines, borders and lakes from vector data bundled in the APK.
 *
 * Replaces the tile-server basemap. No network, no API key, no usage policy to
 * fall foul of, and it works offline. Data is Natural Earth 1:50m, which is
 * public domain.
 *
 * Format (assets/geography.bin), little-endian:
 *   magic "WXGE", uint16 version, uint32 runCount
 *   per run: uint8 layer, uint16 pointCount,
 *            int32 lon, int32 lat        (anchor, degrees * 100000)
 *            int16 dLon, int16 dLat      (repeated pointCount-1 times)
 *
 * Delta encoding keeps it to roughly 4 bytes a point at metre precision. The
 * whole file is about 350 KB and parses in one pass.
 */
object GeographyRenderer {

    private const val TAG = "GeographyRenderer"
    private const val ASSET = "geography.bin"
    private const val SCALE = 100_000.0

    const val LAYER_COAST = 0
    const val LAYER_COUNTRY = 1
    const val LAYER_STATE = 2
    const val LAYER_LAKE = 3

    /** One polyline in degrees, flattened as lon,lat pairs. */
    private class Run(val layer: Int, val coords: DoubleArray) {
        val minLon: Double
        val maxLon: Double
        val minLat: Double
        val maxLat: Double

        init {
            var nlo = Double.MAX_VALUE; var xlo = -Double.MAX_VALUE
            var nla = Double.MAX_VALUE; var xla = -Double.MAX_VALUE
            var i = 0
            while (i < coords.size) {
                val lon = coords[i]; val lat = coords[i + 1]
                if (lon < nlo) nlo = lon
                if (lon > xlo) xlo = lon
                if (lat < nla) nla = lat
                if (lat > xla) xla = lat
                i += 2
            }
            minLon = nlo; maxLon = xlo; minLat = nla; maxLat = xla
        }
    }

    @Volatile
    private var runs: List<Run>? = null

    /** Parsed once and held: about 1.5 MB of doubles, reused every refresh. */
    private fun load(context: Context): List<Run> {
        runs?.let { return it }
        synchronized(this) {
            runs?.let { return it }
            val parsed = try {
                parse(context)
            } catch (e: Exception) {
                Log.w(TAG, "Geography load failed: ${e.message}")
                emptyList()
            }
            runs = parsed
            return parsed
        }
    }

    private fun parse(context: Context): List<Run> {
        val bytes = context.assets.open(ASSET).use { it.readBytes() }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = ByteArray(4).also { buf.get(it) }
        require(String(magic) == "WXGE") { "bad magic" }
        val version = buf.short.toInt()
        require(version == 2) { "unsupported geography version $version" }

        val runCount = buf.int
        val out = ArrayList<Run>(runCount)

        repeat(runCount) {
            val layer = buf.get().toInt()
            val n = buf.short.toInt() and 0xFFFF
            var lon = buf.int
            var lat = buf.int
            val coords = DoubleArray(n * 2)
            coords[0] = lon / SCALE
            coords[1] = lat / SCALE
            for (i in 1 until n) {
                lon += buf.short.toInt()
                lat += buf.short.toInt()
                coords[i * 2] = lon / SCALE
                coords[i * 2 + 1] = lat / SCALE
            }
            out.add(Run(layer, coords))
        }
        Log.i(TAG, "Loaded ${out.size} geography runs")
        return out
    }

    /**
     * Draws the visible geography for a Web Mercator viewport.
     *
     * @param left  world-pixel x of the viewport's left edge at this zoom
     * @param top   world-pixel y of the viewport's top edge
     * @param scale world pixels to canvas pixels
     */
    fun draw(
        context: Context,
        canvas: Canvas,
        zoom: Int,
        left: Double,
        top: Double,
        scale: Double,
        width: Int,
        height: Int
    ): Boolean {
        val all = load(context)
        if (all.isEmpty()) return false

        val n = 1 shl zoom
        val worldPx = n * 256.0

        // Viewport bounds in degrees, padded so lines entering from off-screen
        // still get drawn.
        val viewW = width / scale
        val viewH = height / scale
        val lonMin = left / worldPx * 360.0 - 180.0 - 1.0
        val lonMax = (left + viewW) / worldPx * 360.0 - 180.0 + 1.0
        val latMax = mercatorToLat(top / worldPx) + 1.0
        val latMin = mercatorToLat((top + viewH) / worldPx) - 1.0

        // Thinner strokes at low zoom, where far more coastline is on screen.
        val w = when {
            zoom <= 5 -> 1.4f
            zoom <= 7 -> 1.9f
            else -> 2.4f
        }

        val coast = stroke(Color.argb(190, 150, 190, 220), w * 1.15f)
        val country = stroke(Color.argb(150, 190, 175, 150), w)
        val state = stroke(Color.argb(95, 170, 170, 180), w * 0.8f)
        val lake = stroke(Color.argb(130, 120, 165, 205), w * 0.85f)

        var drew = 0
        val path = Path()

        for (run in all) {
            if (run.maxLon < lonMin || run.minLon > lonMax) continue
            if (run.maxLat < latMin || run.minLat > latMax) continue

            path.rewind()
            var started = false
            var i = 0
            while (i < run.coords.size) {
                val px = ((run.coords[i] + 180.0) / 360.0 * worldPx - left) * scale
                val py = (latToMercator(run.coords[i + 1]) * worldPx - top) * scale
                if (started) path.lineTo(px.toFloat(), py.toFloat())
                else { path.moveTo(px.toFloat(), py.toFloat()); started = true }
                i += 2
            }

            canvas.drawPath(
                path,
                when (run.layer) {
                    LAYER_COUNTRY -> country
                    LAYER_STATE -> state
                    LAYER_LAKE -> lake
                    else -> coast
                }
            )
            drew++
        }

        Log.i(TAG, "Drew $drew geography runs at z$zoom")
        return drew > 0
    }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    private fun latToMercator(lat: Double): Double {
        val clamped = lat.coerceIn(-85.05, 85.05)
        val rad = Math.toRadians(clamped)
        return (1.0 - ln(tan(rad) + 1.0 / Math.cos(rad)) / PI) / 2.0
    }

    private fun mercatorToLat(y: Double): Double {
        val n = PI * (1 - 2 * y)
        return Math.toDegrees(Math.atan(Math.sinh(n)))
    }
}
