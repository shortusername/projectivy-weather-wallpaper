package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.random.Random

/**
 * Draws one frame of falling precipitation onto a canvas.
 *
 * Replaces the Lottie approach. That built rain and snow as vector shape layers
 * — which testing showed the launcher renders correctly — but the scene behind
 * them was an embedded image, and embedded images don't display at all. So the
 * particles would have fallen over nothing.
 *
 * Drawing here and encoding to video keeps the whole scene, and is simpler:
 * ordinary Canvas calls instead of hand-assembled Lottie JSON.
 *
 * Motion is a pure function of the frame index, so the loop closes seamlessly:
 * at the last frame every particle sits exactly where its neighbour began.
 */
object PrecipitationFrames {

    private enum class Kind { STREAK, FLAKE, BAND }

    private class Spec(
        val count: Int,
        val kind: Kind,
        val lean: Float = 0f,
        val flash: Boolean = false
    )

    private fun specFor(code: Int): Spec? = when (code) {
        51, 53, 55, 56, 57 -> Spec(70, Kind.STREAK, lean = 0.10f)
        61, 66, 80 -> Spec(105, Kind.STREAK, lean = 0.16f)
        63, 81 -> Spec(130, Kind.STREAK, lean = 0.20f)
        65, 67, 82 -> Spec(150, Kind.STREAK, lean = 0.26f)
        71, 77, 85 -> Spec(80, Kind.FLAKE, lean = 0.12f)
        73 -> Spec(110, Kind.FLAKE, lean = 0.14f)
        75, 86 -> Spec(140, Kind.FLAKE, lean = 0.18f)
        95 -> Spec(130, Kind.STREAK, lean = 0.22f, flash = true)
        96, 99 -> Spec(150, Kind.STREAK, lean = 0.26f, flash = true)
        45, 48 -> Spec(7, Kind.BAND)
        else -> null
    }

    fun isAnimatable(code: Int): Boolean = specFor(code) != null

    /**
     * How many source frames a loop of this condition should have.
     *
     * Snow drifts slowly and needs more frames to avoid looking stepped; rain
     * falls fast enough that fewer will do.
     */
    fun frameCount(code: Int): Int = when (specFor(code)?.kind) {
        Kind.FLAKE -> 24
        Kind.BAND -> 16
        else -> 16
    }

    /**
     * @param progress 0f at the start of the loop, approaching 1f at the end
     */
    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        weatherCode: Int,
        isDay: Boolean,
        progress: Float
    ) {
        val spec = specFor(weatherCode) ?: return
        // Seeded by condition so the layout is stable between refreshes rather
        // than reshuffling every time the wallpaper is rebuilt.
        val rng = Random(weatherCode * 7919L)
        val loop = height + 240f

        when (spec.kind) {
            Kind.BAND -> drawBands(canvas, width, height, spec, rng, progress)
            Kind.FLAKE -> drawFlakes(canvas, width, height, spec, rng, progress, loop)
            Kind.STREAK ->
                drawStreaks(canvas, width, height, spec, rng, progress, loop, isDay)
        }

        if (spec.flash) drawFlash(canvas, width, height, progress)
    }

    private fun drawFlakes(
        canvas: Canvas, width: Int, height: Int,
        spec: Spec, rng: Random, progress: Float, loop: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(spec.count) {
            val depth = rng.nextFloat()
            val x0 = rng.nextFloat() * width
            val y0 = rng.nextFloat() * loop
            val drift = (rng.nextFloat() - 0.5f) * width * 0.10f + spec.lean * width * 0.10f

            // Modulo keeps every particle on screen for the whole loop.
            val y = (y0 + progress * loop) % loop - 120f
            val x = x0 + drift * progress

            paint.color = Color.argb(
                (34 + depth * 52).toInt().coerceIn(0, 255), 255, 255, 255
            )
            canvas.drawCircle(x, y, 2f + depth * 7f, paint)
        }
    }

    private fun drawStreaks(
        canvas: Canvas, width: Int, height: Int,
        spec: Spec, rng: Random, progress: Float, loop: Float, isDay: Boolean
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
        }
        val dx = spec.lean * loop
        repeat(spec.count) {
            val depth = rng.nextFloat()
            val x0 = rng.nextFloat() * (width * 1.2f) - width * 0.1f
            val y0 = rng.nextFloat() * loop
            val len = 16f + depth * 30f

            val y = (y0 + progress * loop) % loop - 160f
            val x = x0 + dx * progress

            paint.strokeWidth = 1.4f + depth * 2f
            paint.color = if (isDay) {
                Color.argb((26 + depth * 44).toInt().coerceIn(0, 255), 219, 235, 250)
            } else {
                Color.argb((26 + depth * 44).toInt().coerceIn(0, 255), 184, 209, 240)
            }
            // Tilted to match travel direction, so it reads as falling rather
            // than as a vertical tick sliding sideways.
            canvas.drawLine(x, y, x + spec.lean * len * 2f, y + len, paint)
        }
    }

    private fun drawBands(
        canvas: Canvas, width: Int, height: Int,
        spec: Spec, rng: Random, progress: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(spec.count) { i ->
            val depth = rng.nextFloat()
            val y = height * (0.34f + i * 0.085f)
            val h = 26f + rng.nextFloat() * 44f
            val startX = -width * 0.3f + rng.nextFloat() * width * 0.4f
            val x = startX + width * 0.6f * progress

            paint.color = Color.argb(
                (16 + depth * 16).toInt().coerceIn(0, 255), 219, 227, 237
            )
            canvas.drawRoundRect(
                RectF(x, y, x + width * 1.6f, y + h), h / 2f, h / 2f, paint
            )
        }
    }

    /**
     * Two lightning flashes per loop at irregular points, so it doesn't read as
     * a metronome.
     */
    private fun drawFlash(canvas: Canvas, width: Int, height: Int, progress: Float) {
        val intensity = when {
            progress in 0.22f..0.26f -> 1f - (progress - 0.22f) / 0.04f
            progress in 0.68f..0.71f -> (1f - (progress - 0.68f) / 0.03f) * 0.7f
            else -> 0f
        }
        if (intensity <= 0f) return
        canvas.drawRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            Paint().apply {
                color = Color.argb((30 * intensity).toInt().coerceIn(0, 255), 222, 230, 255)
            }
        )
    }
}
