package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Random
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Draws the wind-flow picture: short streaks that follow the wind, tapering
 * from a faint tail to a bright head so the direction reads without any
 * animation, coloured by speed.
 *
 * [drawStreaks] is one frozen frame. It's an ordinary image, so it works on
 * every device. [FlowAnimator] is the same flow set in motion, for the video
 * path: it only ever produces frames for the encoder, never anything the
 * launcher animates itself.
 *
 * Streaks are traced through the wind field in screen space, so the picture
 * has to know how canvas pixels map to latitude and longitude; that's what
 * [Viewport] carries.
 */
object WindFlowRenderer {

    /** Maps canvas pixels to geographic coordinates, matching the basemap. */
    class Viewport(
        private val left: Double,
        private val top: Double,
        private val scale: Double,
        private val worldPx: Double
    ) {
        fun lon(x: Float): Double = (left + x / scale) / worldPx * 360.0 - 180.0

        fun lat(y: Float): Double {
            val yn = (top + y / scale) / worldPx
            return Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * yn))))
        }
    }

    private const val MAX_SPEED = 25f      // m/s at the top of the colour ramp
    private const val MIN_SPEED = 0.05f    // below this a direction isn't meaningful
    private const val STEPS = 14           // segments per still streak

    private val RAMP_SPEEDS = floatArrayOf(0f, 3f, 6f, 10f, 14f, 18f, 25f)
    private val RAMP_COLORS = intArrayOf(
        Color.rgb(111, 168, 220),
        Color.rgb(91, 192, 190),
        Color.rgb(123, 228, 149),
        Color.rgb(242, 233, 107),
        Color.rgb(245, 160, 74),
        Color.rgb(239, 91, 91),
        Color.rgb(210, 91, 224)
    )

    fun colorFor(speed: Float): Int {
        if (speed <= RAMP_SPEEDS[0]) return RAMP_COLORS[0]
        for (k in 1 until RAMP_SPEEDS.size) {
            if (speed <= RAMP_SPEEDS[k]) {
                val t = (speed - RAMP_SPEEDS[k - 1]) / (RAMP_SPEEDS[k] - RAMP_SPEEDS[k - 1])
                return blend(RAMP_COLORS[k - 1], RAMP_COLORS[k], t)
            }
        }
        return RAMP_COLORS[RAMP_COLORS.size - 1]
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    private fun withAlpha(color: Int, alpha: Int): Int =
        (alpha shl 24) or (color and 0x00FFFFFF)

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Quieter streaks behind the weather readout, which sits in the upper
     * left. Fading rather than cutting off keeps the flow visible there
     * without competing with the text.
     */
    private fun panelFactor(x: Float, y: Float, width: Int, height: Int, px: Float): Float {
        val dx = max(x - 0.58f * width, 0f)
        val dy = max(y - 0.84f * height, 0f)
        val d = sqrt(dx * dx + dy * dy)
        return 0.30f + 0.70f * smoothstep(0f, 110f * px, d)
    }

    /** Wind in screen space: east is +x, and north is -y because y grows down. */
    private fun velocity(field: WindClient.Field, vp: Viewport, x: Float, y: Float, out: FloatArray) {
        field.sample(vp.lat(y), vp.lon(x), out)
        out[1] = -out[1]
    }

    /**
     * Follows the wind from a start point for up to [count] steps of length
     * [h], forwards ([sign] = 1) or backwards (-1), using a midpoint step so
     * curved flow doesn't drift off. Returns the index of the last point.
     */
    private fun trace(
        field: WindClient.Field, vp: Viewport,
        x0: Float, y0: Float, sign: Float, count: Int, h: Float,
        xs: FloatArray, ys: FloatArray, v: FloatArray
    ): Int {
        var x = x0
        var y = y0
        xs[0] = x
        ys[0] = y
        var n = 0
        while (n < count) {
            velocity(field, vp, x, y, v)
            val s1 = sqrt(v[0] * v[0] + v[1] * v[1])
            if (s1 < MIN_SPEED) break
            val mx = x + sign * v[0] / s1 * h * 0.5f
            val my = y + sign * v[1] / s1 * h * 0.5f
            velocity(field, vp, mx, my, v)
            val s2 = sqrt(v[0] * v[0] + v[1] * v[1])
            if (s2 < MIN_SPEED) break
            x += sign * v[0] / s2 * h
            y += sign * v[1] / s2 * h
            n++
            xs[n] = x
            ys[n] = y
        }
        return n
    }

    /** The still picture: every streak at once, tail to head. */
    fun drawStreaks(
        canvas: Canvas,
        field: WindClient.Field,
        vp: Viewport,
        width: Int,
        height: Int,
        seed: Long
    ) {
        val px = width / 1920f
        val cell = 34f * px
        // Seeded, so the same area draws the same picture each time rather
        // than shimmering between refreshes.
        val rnd = Random(seed)
        // Butt caps: semi-transparent round caps overlap at each joint and
        // show as a row of brighter beads along the streak.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
        }

        val vel = FloatArray(2)
        val tailX = FloatArray(STEPS / 2 + 1)
        val tailY = FloatArray(STEPS / 2 + 1)
        val xs = FloatArray(STEPS + 1)
        val ys = FloatArray(STEPS + 1)

        val gridCols = (width / cell).toInt() + 1
        val gridRows = (height / cell).toInt() + 1
        for (gy in 0 until gridRows) {
            for (gx in 0 until gridCols) {
                // One jittered seed per cell: even coverage without a visible grid.
                val x0 = (gx + rnd.nextFloat()) * cell
                val y0 = (gy + rnd.nextFloat()) * cell
                velocity(field, vp, x0, y0, vel)
                val speed = sqrt(vel[0] * vel[0] + vel[1] * vel[1])
                if (speed < 0.3f) continue

                val length = (26f + speed * 8f).coerceIn(26f, 170f) * px
                val h = length / STEPS
                // Back up half a streak so the seed sits in the middle of it.
                val tailN = trace(field, vp, x0, y0, -1f, STEPS / 2, h, tailX, tailY, vel)
                val n = trace(field, vp, tailX[tailN], tailY[tailN], 1f, STEPS, h, xs, ys, vel)
                if (n < 2) continue

                val base = 0.5f + 0.5f * min(speed / 12f, 1f)
                for (k in 0 until n) {
                    val t = (k + 1f) / n
                    val mx = (xs[k] + xs[k + 1]) / 2f
                    val my = (ys[k] + ys[k + 1]) / 2f
                    velocity(field, vp, mx, my, vel)
                    val segSpeed = sqrt(vel[0] * vel[0] + vel[1] * vel[1])
                    val a = (0.08f + 0.92f * t.pow(1.5f)) * base *
                        panelFactor(mx, my, width, height, px)
                    paint.color = withAlpha(colorFor(segSpeed), (255f * a).toInt().coerceIn(0, 255))
                    paint.strokeWidth = (1.6f + min(segSpeed, 20f) / 20f * 2.0f) * px
                    canvas.drawLine(xs[k], ys[k], xs[k + 1], ys[k + 1], paint)
                }
            }
        }
    }

    /**
     * The moving version, for encoding into a loop.
     *
     * The wind field is a single snapshot, so the streamlines themselves never
     * change. What moves is a short bright "comet" sliding along each one. The
     * streamlines are traced once up front; each frame just redraws every
     * comet a little further along, which is far cheaper than retracing.
     *
     * The loop is seamless by construction. A comet's position is a repeating
     * function of time, so the last frame hands straight back to the first, and
     * each comet fades in and out at the ends of its path so the point where it
     * wraps round is never visible. Random per-comet phases keep them from
     * moving in lockstep. Faster wind has a longer path covered in the same
     * time, so it genuinely moves faster.
     */
    class FlowAnimator(
        field: WindClient.Field,
        vp: Viewport,
        private val width: Int,
        private val height: Int,
        seed: Long
    ) {
        private class Comet(
            val xs: FloatArray,
            val ys: FloatArray,
            val colors: IntArray,
            val speed: Float,
            val phase: Float,
            val strokeWidth: Float
        )

        private val comets = ArrayList<Comet>()
        private val px = width / 1920f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
        }

        init {
            // A different random stream from the still picture, so the two
            // don't share their jitter pattern.
            val rnd = Random(seed xor 0x5DEECE66DL)
            val cell = 46f * px
            val half = PATH_POINTS / 2
            val vel = FloatArray(2)
            val backX = FloatArray(half + 1)
            val backY = FloatArray(half + 1)
            val fwdX = FloatArray(half + 1)
            val fwdY = FloatArray(half + 1)

            val gridCols = (width / cell).toInt() + 1
            val gridRows = (height / cell).toInt() + 1
            for (gy in 0 until gridRows) {
                for (gx in 0 until gridCols) {
                    val x0 = (gx + rnd.nextFloat()) * cell
                    val y0 = (gy + rnd.nextFloat()) * cell
                    WindFlowRenderer.velocity(field, vp, x0, y0, vel)
                    val speed = sqrt(vel[0] * vel[0] + vel[1] * vel[1])
                    // The phase is drawn for every cell, used or not, so one
                    // calm cell can't shift the random sequence of the rest.
                    val phase = rnd.nextFloat()
                    if (speed < 0.3f) continue

                    val pathLength = (60f + speed * 16f).coerceIn(60f, 320f) * px
                    val h = pathLength / PATH_POINTS
                    val nb = WindFlowRenderer.trace(field, vp, x0, y0, -1f, half, h, backX, backY, vel)
                    val nf = WindFlowRenderer.trace(field, vp, x0, y0, 1f, half, h, fwdX, fwdY, vel)
                    val count = nb + nf + 1
                    if (count < 6) continue

                    // Tail first: the backward points reversed (ending on the
                    // seed), then the forward points.
                    val xs = FloatArray(count)
                    val ys = FloatArray(count)
                    var k = 0
                    for (i in nb downTo 0) {
                        xs[k] = backX[i]; ys[k] = backY[i]; k++
                    }
                    for (i in 1..nf) {
                        xs[k] = fwdX[i]; ys[k] = fwdY[i]; k++
                    }
                    val colors = IntArray(count)
                    for (i in 0 until count) {
                        WindFlowRenderer.velocity(field, vp, xs[i], ys[i], vel)
                        colors[i] = WindFlowRenderer.colorFor(sqrt(vel[0] * vel[0] + vel[1] * vel[1]))
                    }
                    val stroke = (1.6f + min(speed, 20f) / 20f * 2.0f) * px
                    comets.add(Comet(xs, ys, colors, speed, phase, stroke))
                }
            }
        }

        /** Draws frame [index] of a loop of [total] frames onto [canvas]. */
        fun drawFrame(canvas: Canvas, index: Int, total: Int) {
            val t = index.toFloat() / total
            for (comet in comets) {
                val n = comet.xs.size - 1
                val u = ((t + comet.phase) % 1f) * n
                val envelope = sin(PI.toFloat() * u / n)
                if (envelope <= 0.02f) continue
                val base = (0.55f + 0.45f * min(comet.speed / 12f, 1f)) * envelope
                val lo = max(0f, u - TRAIL_POINTS)
                paint.strokeWidth = comet.strokeWidth
                val last = min(n - 1, u.toInt())
                for (k in lo.toInt()..last) {
                    val s0 = max(k.toFloat(), lo)
                    val s1 = min(k + 1f, u)
                    if (s1 <= s0) continue
                    val ax = at(comet.xs, s0)
                    val ay = at(comet.ys, s0)
                    val bx = at(comet.xs, s1)
                    val by = at(comet.ys, s1)
                    val age = u - (s0 + s1) / 2f
                    val a = (1f - age / TRAIL_POINTS).coerceIn(0f, 1f).pow(1.5f) * base *
                        WindFlowRenderer.panelFactor((ax + bx) / 2f, (ay + by) / 2f, width, height, px) * 1.25f
                    paint.color = WindFlowRenderer.withAlpha(comet.colors[k], (255f * a).toInt().coerceIn(0, 255))
                    canvas.drawLine(ax, ay, bx, by, paint)
                }
            }
        }

        private fun at(values: FloatArray, s: Float): Float {
            val i = min(s.toInt(), values.size - 2)
            return values[i] + (values[i + 1] - values[i]) * (s - i)
        }

        private companion object {
            const val PATH_POINTS = 24
            const val TRAIL_POINTS = 9f
        }
    }

    /**
     * Speed legend, bottom right. Tick values are round numbers in the user's
     * own units, positioned by their equivalent in m/s along the colour ramp.
     * The unit letters stay as written, the same call made for km/h elsewhere.
     */
    fun drawLegend(canvas: Canvas, width: Int, height: Int, metric: Boolean, title: String) {
        val px = width / 1920f
        val barW = 520f * px
        val barH = 12f * px
        val right = width - 120f * px
        val left = right - barW
        val top = height - 132f * px

        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                left, 0f, right, 0f, RAMP_COLORS,
                FloatArray(RAMP_SPEEDS.size) { RAMP_SPEEDS[it] / MAX_SPEED },
                Shader.TileMode.CLAMP
            )
            alpha = 235
        }
        canvas.drawRoundRect(RectF(left, top, right, top + barH), barH / 2f, barH / 2f, bar)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 235, 235, 240)
            textSize = 22f * px
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val ticks = if (metric) intArrayOf(0, 20, 40, 60, 80) else intArrayOf(0, 10, 20, 30, 40, 50)
        val toMetresPerSecond = if (metric) 1f / 3.6f else 0.44704f
        val unit = if (metric) "km/h" else "mph"

        canvas.drawText("$title ($unit)", left, top - 14f * px, text)
        for (tick in ticks) {
            val label = tick.toString()
            val x = left + barW * (tick * toMetresPerSecond / MAX_SPEED)
            canvas.drawText(label, x - text.measureText(label) / 2f, top + barH + 28f * px, text)
        }
    }
}
