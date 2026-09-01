package tv.projectivy.plugin.wallpaperprovider.weather

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/**
 * Draws a filled basemap from vector data bundled in the APK: land and lakes as
 * tonal areas, borders as lines, and city labels for context.
 *
 * Replaces the old tile-server basemap. No network, no API key, no usage policy
 * to fall foul of, and it works offline. Data is Natural Earth, public domain.
 *
 * The look is deliberately close to broadcast weather graphics: dark filled
 * land, darker water, faint borders, sparse labels. Anything busier competes
 * with the precipitation, which is the actual subject.
 *
 * Format (assets/geography.bin), little-endian:
 *   "WXGE", uint16 version=3, uint32 polyCount, lineCount, placeCount
 *   polygon: uint8 layer, uint16 ringCount, then rings
 *   line:    uint8 layer, then one ring
 *   ring:    uint16 pointCount, int32 lon, int32 lat, (int16 dLon, dLat)*
 *   place:   int32 lon, int32 lat, uint8 minZoom*10, uint8 nameLen, utf8 name
 * Coordinates are degrees * 100000.
 */
object GeographyRenderer {

    private const val TAG = "GeographyRenderer"
    private const val ASSET = "geography.bin"
    private const val SCALE = 100_000.0

    private const val POLY_LAND = 0
    private const val POLY_LAKE = 1
    private const val LINE_COUNTRY = 0

    // Broadcast-style palette: land reads lighter than water, both desaturated
    // so the radar colours stay dominant.
    private const val C_WATER_TOP = 0xFF16283C.toInt()
    private const val C_WATER = 0xFF081220.toInt()
    private const val C_LAND_TOP = 0xFF4A5868.toInt()
    private const val C_LAND = 0xFF34424F.toInt()
    private const val C_LAKE = 0xFF1A2838.toInt()
    private const val C_COAST = 0xFF96BEDF.toInt()
    private const val C_COAST_GLOW = 0xFF5F96C8.toInt()

    private class Poly(val layer: Int, val rings: List<DoubleArray>) {
        var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        init {
            for (r in rings) {
                var i = 0
                while (i < r.size) {
                    if (r[i] < minLon) minLon = r[i]
                    if (r[i] > maxLon) maxLon = r[i]
                    if (r[i + 1] < minLat) minLat = r[i + 1]
                    if (r[i + 1] > maxLat) maxLat = r[i + 1]
                    i += 2
                }
            }
        }
    }

    private class Line(val layer: Int, val coords: DoubleArray) {
        val minLon: Double; val maxLon: Double
        val minLat: Double; val maxLat: Double
        init {
            var a = Double.MAX_VALUE; var b = -Double.MAX_VALUE
            var c = Double.MAX_VALUE; var d = -Double.MAX_VALUE
            var i = 0
            while (i < coords.size) {
                if (coords[i] < a) a = coords[i]
                if (coords[i] > b) b = coords[i]
                if (coords[i + 1] < c) c = coords[i + 1]
                if (coords[i + 1] > d) d = coords[i + 1]
                i += 2
            }
            minLon = a; maxLon = b; minLat = c; maxLat = d
        }
    }

    /**
     * @param rank the zoom at which this place earns a label, derived from
     *   population when the asset is built. Lower means more significant.
     */
    private class Place(val lon: Double, val lat: Double, val rank: Double, val name: String)

    private class Data(val polys: List<Poly>, val lines: List<Line>, val places: List<Place>)

    @Volatile private var data: Data? = null

    private fun load(context: Context): Data? {
        data?.let { return it }
        synchronized(this) {
            data?.let { return it }
            val parsed = try {
                parse(context)
            } catch (e: Exception) {
                Log.w(TAG, "Geography load failed: ${e.message}")
                null
            }
            data = parsed
            return parsed
        }
    }

    private fun parse(context: Context): Data {
        val buf = ByteBuffer.wrap(context.assets.open(ASSET).use { it.readBytes() })
            .order(ByteOrder.LITTLE_ENDIAN)

        val magic = ByteArray(4).also { buf.get(it) }
        require(String(magic) == "WXGE") { "bad magic" }
        val version = buf.short.toInt()
        require(version == 5) { "unsupported geography version $version" }

        val polyCount = buf.int
        val lineCount = buf.int
        val placeCount = buf.int

        // mode 0 = int32 anchor + int16 deltas, mode 1 = raw int32 pairs.
        // Rings whose steps overflow int16 use mode 1 rather than being split:
        // fragmenting a ring breaks its closure and the fill silently fails.
        fun ring(): DoubleArray {
            val mode = buf.get().toInt()
            val n = buf.short.toInt() and 0xFFFF
            val out = DoubleArray(n * 2)
            if (mode == 0) {
                var lon = buf.int
                var lat = buf.int
                out[0] = lon / SCALE; out[1] = lat / SCALE
                for (i in 1 until n) {
                    lon += buf.short.toInt(); lat += buf.short.toInt()
                    out[i * 2] = lon / SCALE; out[i * 2 + 1] = lat / SCALE
                }
            } else {
                for (i in 0 until n) {
                    out[i * 2] = buf.int / SCALE
                    out[i * 2 + 1] = buf.int / SCALE
                }
            }
            return out
        }

        val polys = ArrayList<Poly>(polyCount)
        repeat(polyCount) {
            val layer = buf.get().toInt()
            val rc = buf.short.toInt() and 0xFFFF
            polys.add(Poly(layer, List(rc) { ring() }))
        }

        val lines = ArrayList<Line>(lineCount)
        repeat(lineCount) {
            val layer = buf.get().toInt()
            lines.add(Line(layer, ring()))
        }

        val places = ArrayList<Place>(placeCount)
        val nameBuf = ByteArray(64)
        repeat(placeCount) {
            val lon = buf.int / SCALE
            val lat = buf.int / SCALE
            val rank = (buf.get().toInt() and 0xFF) / 10.0
            val len = buf.get().toInt() and 0xFF
            buf.get(nameBuf, 0, len)
            places.add(Place(lon, lat, rank, String(nameBuf, 0, len, Charsets.UTF_8)))
        }

        Log.i(TAG, "Geography: ${polys.size} polys, ${lines.size} lines, ${places.size} places")
        return Data(polys, lines, places)
    }

    /**
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
        val d = load(context) ?: return false

        val n = 1 shl zoom
        val worldPx = n * 256.0
        val viewW = width / scale
        val viewH = height / scale

        val lonMin = left / worldPx * 360.0 - 180.0 - 1.5
        val lonMax = (left + viewW) / worldPx * 360.0 - 180.0 + 1.5
        val latMax = mercatorToLat(top / worldPx) + 1.5
        val latMin = mercatorToLat((top + viewH) / worldPx) - 1.5

        fun px(lon: Double) = (((lon + 180.0) / 360.0 * worldPx - left) * scale).toFloat()
        fun py(lat: Double) = ((latToMercator(lat) * worldPx - top) * scale).toFloat()

        // Water base, with a vertical tone shift so it isn't a flat slab.
        val waterPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                C_WATER_TOP, C_WATER, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), waterPaint)

        // Land lighter at the top, so the scene has an implied light direction
        // rather than reading as a flat cutout.
        val landPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                C_LAND_TOP, C_LAND, Shader.TileMode.CLAMP
            )
        }
        val lakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = C_LAKE
        }

        val coastPaint = strokePaint(C_COAST, if (zoom <= 5) 1.8f else 2.4f)
        // Wide blurred pass under the crisp line: gives the shoreline a soft
        // halo, which is most of what makes broadcast maps look considered.
        val coastGlow = strokePaint(C_COAST_GLOW, if (zoom <= 5) 10f else 16f).apply {
            alpha = 105
            maskFilter = BlurMaskFilter(
                if (zoom <= 5) 8f else 12f, BlurMaskFilter.Blur.NORMAL
            )
        }
        val lakeEdge = strokePaint(Color.argb(120, 110, 150, 190), 1.8f)
        val pad = 300f
        val path = Path()
        var drew = 0

        for (poly in d.polys) {
            if (poly.maxLon < lonMin || poly.minLon > lonMax) continue
            if (poly.maxLat < latMin || poly.minLat > latMax) continue

            path.rewind()
            // EVEN_ODD makes holes work without depending on ring winding order,
            // which Natural Earth doesn't guarantee.
            path.fillType = Path.FillType.EVEN_ODD
            var any = false
            for (r in poly.rings) {
                val projected = FloatArray(r.size)
                var i = 0
                while (i < r.size) {
                    projected[i] = px(r[i]); projected[i + 1] = py(r[i + 1])
                    i += 2
                }
                val clipped = clipPolygon(projected, -pad, -pad, width + pad, height + pad)
                if (clipped.size < 6) continue
                path.moveTo(clipped[0], clipped[1])
                var j = 2
                while (j < clipped.size) { path.lineTo(clipped[j], clipped[j + 1]); j += 2 }
                path.close()
                any = true
            }
            if (!any) continue
            if (poly.layer == POLY_LAKE) {
                canvas.drawPath(path, lakePaint)
                canvas.drawPath(path, lakeEdge)
            } else {
                canvas.drawPath(path, coastGlow)
                canvas.drawPath(path, landPaint)
                // Crisp shoreline on top: the strongest orientation cue there is.
                canvas.drawPath(path, coastPaint)
            }
            drew++
        }

        // Borders on top of the fills. State lines are dashed, which is the
        // broadcast convention and stops them reading as coastline.
        val strokeW = if (zoom <= 5) 1.4f else if (zoom <= 7) 1.9f else 2.3f
        val dashes = DashPathEffect(floatArrayOf(14f, 9f), 0f)

        val countryHalo = strokePaint(Color.argb(190, 8, 14, 24), strokeW * 3.0f)
        val stateHalo = strokePaint(Color.argb(170, 8, 14, 24), strokeW * 2.6f).apply {
            pathEffect = dashes
        }
        val country = strokePaint(Color.argb(240, 225, 236, 250), strokeW * 1.5f)
        val state = strokePaint(Color.argb(215, 196, 214, 236), strokeW * 1.05f).apply {
            pathEffect = dashes
        }

        for (line in d.lines) {
            if (line.maxLon < lonMin || line.minLon > lonMax) continue
            if (line.maxLat < latMin || line.minLat > latMax) continue
            path.rewind()
            path.fillType = Path.FillType.WINDING
            var i = 0
            while (i < line.coords.size) {
                val x = px(line.coords[i]); val y = py(line.coords[i + 1])
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                i += 2
            }
            // Dark halo first so borders stay legible over land and radar alike.
            canvas.drawPath(path, if (line.layer == LINE_COUNTRY) countryHalo else stateHalo)
            canvas.drawPath(path, if (line.layer == LINE_COUNTRY) country else state)
            drew++
        }

        drawPlaces(canvas, d, zoom, lonMin, lonMax, latMin, latMax, ::px, ::py, width, height)
        return drew > 0
    }

    /**
     * City labels, thinned by Natural Earth's own min_zoom ranking so only the
     * significant places for this zoom appear, then capped so a dense metro
     * doesn't turn into a wall of text.
     */
    private fun drawPlaces(
        canvas: Canvas,
        d: Data,
        zoom: Int,
        lonMin: Double, lonMax: Double, latMin: Double, latMax: Double,
        px: (Double) -> Float, py: (Double) -> Float,
        width: Int, height: Int
    ) {
        val density = PreferencesManager.labelDensity
        if (density == PreferencesManager.LABELS_OFF) return

        val (offset, cap) = when (density) {
            PreferencesManager.LABELS_FEW -> 0.5 to 10
            PreferencesManager.LABELS_MANY -> 2.5 to 30
            else -> 1.5 to 18
        }
        val threshold = zoom + offset

        val visible = d.places.asSequence()
            .filter { it.rank <= threshold }
            .filter { it.lon in lonMin..lonMax && it.lat in latMin..latMax }
            .sortedBy { it.rank }
            .take(cap * 6)          // candidates; collision culling trims to cap
            .toList()
        if (visible.isEmpty()) return

        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(150, 210, 220, 235)
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = 26f
            color = Color.argb(185, 225, 232, 242)
            setShadowLayer(6f, 0f, 2f, Color.argb(190, 0, 0, 0))
        }

        // Keep labels out of the weather panel's corner.
        val panelRight = width * 0.46f
        val panelBottom = height * 0.62f

        val dotHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(150, 0, 0, 0)
        }

        // Occupied label boxes. Candidates arrive sorted by rank, so when two
        // labels compete for the same space the more significant one wins.
        val placed = ArrayList<RectF>(cap)
        var drawn = 0

        for (p in visible) {
            if (drawn >= cap) break
            val x = px(p.lon); val y = py(p.lat)
            if (x < 40f || x > width - 40f || y < 40f || y > height - 90f) continue
            if (x < panelRight && y < panelBottom) continue

            // Spaced capitals for the majors; measure first, because the extra
            // tracking can push a label off the right edge.
            var text = if (p.rank <= 3.0) p.name.uppercase().toCharArray().joinToString(" ")
                       else p.name
            var tw = label.measureText(text)
            if (x + 12f + tw > width - 30f) {
                text = p.name
                tw = label.measureText(text)
                if (x + 12f + tw > width - 30f) continue
            }

            val box = RectF(x - 8f, y - 16f, x + 14f + tw, y + 16f)
            if (placed.any { RectF.intersects(it, box) }) continue
            placed.add(box)
            drawn++

            canvas.drawCircle(x, y, 5f, dotHalo)
            canvas.drawCircle(x, y, 3f, dot)
            canvas.drawText(text, x + 12f, y + 9f, label)
        }
    }

    /**
     * Sutherland-Hodgman polygon clip. Landmasses dwarf the viewport at these
     * zooms, and filling a path whose coordinates run to millions of pixels
     * fails silently on some rasterisers. Clipping first keeps them sane.
     */
    private fun clipPolygon(
        pts: FloatArray, xmin: Float, ymin: Float, xmax: Float, ymax: Float
    ): FloatArray {
        var cur = pts
        for (edge in 0..3) {
            if (cur.size < 6) return FloatArray(0)
            val out = ArrayList<Float>(cur.size + 8)
            var px = cur[cur.size - 2]; var py = cur[cur.size - 1]
            var i = 0
            while (i < cur.size) {
                val cx = cur[i]; val cy = cur[i + 1]
                val ci = inside(cx, cy, edge, xmin, ymin, xmax, ymax)
                val pi = inside(px, py, edge, xmin, ymin, xmax, ymax)
                if (ci) {
                    if (!pi) addIntersect(out, px, py, cx, cy, edge, xmin, ymin, xmax, ymax)
                    out.add(cx); out.add(cy)
                } else if (pi) {
                    addIntersect(out, px, py, cx, cy, edge, xmin, ymin, xmax, ymax)
                }
                px = cx; py = cy
                i += 2
            }
            cur = out.toFloatArray()
        }
        return cur
    }

    private fun inside(
        x: Float, y: Float, edge: Int,
        xmin: Float, ymin: Float, xmax: Float, ymax: Float
    ): Boolean = when (edge) {
        0 -> x >= xmin
        1 -> x <= xmax
        2 -> y >= ymin
        else -> y <= ymax
    }

    private fun addIntersect(
        out: ArrayList<Float>, ax: Float, ay: Float, bx: Float, by: Float, edge: Int,
        xmin: Float, ymin: Float, xmax: Float, ymax: Float
    ) {
        if (edge <= 1) {
            val x = if (edge == 0) xmin else xmax
            val t = if (bx != ax) (x - ax) / (bx - ax) else 0f
            out.add(x); out.add(ay + t * (by - ay))
        } else {
            val y = if (edge == 2) ymin else ymax
            val t = if (by != ay) (y - ay) / (by - ay) else 0f
            out.add(ax + t * (bx - ax)); out.add(y)
        }
    }

    private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    private fun latToMercator(lat: Double): Double {
        val c = lat.coerceIn(-85.05, 85.05)
        val rad = Math.toRadians(c)
        return (1.0 - ln(tan(rad) + 1.0 / Math.cos(rad)) / PI) / 2.0
    }

    private fun mercatorToLat(y: Double): Double =
        Math.toDegrees(Math.atan(Math.sinh(PI * (1 - 2 * y))))
}
