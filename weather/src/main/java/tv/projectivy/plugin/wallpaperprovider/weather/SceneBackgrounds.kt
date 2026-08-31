package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedurally drawn scene backgrounds, one per weather condition.
 *
 * Everything here is Canvas primitives — no bundled image assets. That keeps the
 * APK small, stays sharp at any resolution, and means the background can respond
 * to conditions and time of day rather than being one of a fixed set of photos.
 *
 * Randomness is seeded from the day plus the condition, so a scene is stable
 * across refreshes within a day but differs tomorrow. Star fields and snowflake
 * scatter change; the composition doesn't jump around every 15 minutes.
 */
object SceneBackgrounds {

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        c: OpenMeteoClient.Conditions
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bucket = OpenMeteoClient.bucket(c.weatherCode)
        val day = System.currentTimeMillis() / 86_400_000L
        val rng = Random(day * 31 + c.weatherCode)

        when {
            !c.isDay && bucket == "clear" -> clearNight(canvas, w, h, rng)
            !c.isDay -> overcastNight(canvas, w, h, bucket, rng)
            bucket == "clear" -> clearDay(canvas, w, h, c.weatherCode)
            bucket == "cloud" -> cloudy(canvas, w, h, c.weatherCode, rng)
            bucket == "rain" -> rainy(canvas, w, h, rng)
            bucket == "snow" -> snowy(canvas, w, h, rng)
            bucket == "storm" -> stormy(canvas, w, h, rng)
            else -> cloudy(canvas, w, h, c.weatherCode, rng)
        }

        // Unifying vignette. Pulls the corners down slightly so the panel text
        // always has somewhere dark to sit regardless of what's behind it.
        val vignette = Paint().apply {
            shader = RadialGradient(
                w * 0.62f, h * 0.42f, w * 0.78f,
                intArrayOf(Color.TRANSPARENT, Color.argb(70, 0, 0, 12)),
                floatArrayOf(0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, vignette)
    }

    // ------------------------------------------------------------------ skies

    private fun sky(canvas: Canvas, w: Float, h: Float, vararg colors: Int) {
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h, colors, null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    private fun clearDay(canvas: Canvas, w: Float, h: Float, code: Int) {
        sky(
            canvas, w, h,
            Color.parseColor("#0E4C8A"),
            Color.parseColor("#2A7FBF"),
            Color.parseColor("#7FC4E8"),
            Color.parseColor("#C9E6F2")
        )

        // Sun low on the right, well clear of the text block.
        val sunX = w * 0.78f
        val sunY = h * 0.30f
        glow(canvas, sunX, sunY, w * 0.34f, Color.argb(150, 255, 241, 200))
        glow(canvas, sunX, sunY, w * 0.10f, Color.argb(220, 255, 252, 235))

        // Faint high cirrus for depth. Code 1 ("mainly clear") gets a little more.
        val bands = if (code == 1) 5 else 3
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until bands) {
            val y = h * (0.18f + i * 0.09f)
            paint.color = Color.argb(28 - i * 3, 255, 255, 255)
            val rect = RectF(w * (0.05f + i * 0.06f), y, w * (0.55f + i * 0.08f), y + h * 0.018f)
            canvas.drawRoundRect(rect, h * 0.01f, h * 0.01f, paint)
        }
    }

    private fun clearNight(canvas: Canvas, w: Float, h: Float, rng: Random) {
        sky(
            canvas, w, h,
            Color.parseColor("#050814"),
            Color.parseColor("#0B1230"),
            Color.parseColor("#16224A"),
            Color.parseColor("#243156")
        )

        // Stars: density falls off toward the horizon, brightness varies.
        val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        repeat(220) {
            val x = rng.nextFloat() * w
            val yBias = rng.nextFloat() * rng.nextFloat()   // clusters toward the top
            val y = yBias * h * 0.85f
            val r = 1f + rng.nextFloat() * 2.4f
            // Dimmer near the horizon, so the field has depth.
            star.alpha = (60 + rng.nextInt(170) * (1f - yBias * 0.45f)).toInt().coerceIn(20, 255)
            canvas.drawCircle(x, y, r, star)
        }

        // A few brighter stars with a soft halo.
        repeat(7) {
            val x = rng.nextFloat() * w
            val y = rng.nextFloat() * h * 0.6f
            glow(canvas, x, y, 26f, Color.argb(70, 200, 220, 255))
            star.alpha = 245
            canvas.drawCircle(x, y, 2.6f, star)
        }

        val moonX = w * 0.79f
        val moonY = h * 0.26f
        glow(canvas, moonX, moonY, w * 0.20f, Color.argb(90, 210, 224, 255))
        val moon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(240, 240, 244, 255) }
        val crescent = Path().apply {
            addCircle(moonX, moonY, 62f, Path.Direction.CW)
            addCircle(moonX + 30f, moonY - 22f, 56f, Path.Direction.CCW)
            fillType = Path.FillType.EVEN_ODD
        }
        canvas.drawPath(crescent, moon)
    }

    private fun overcastNight(canvas: Canvas, w: Float, h: Float, bucket: String, rng: Random) {
        sky(
            canvas, w, h,
            Color.parseColor("#080B18"),
            Color.parseColor("#101830"),
            Color.parseColor("#1B2542")
        )
        cloudBank(canvas, w, h, Color.argb(70, 150, 165, 195), rng, layers = 3)
        when (bucket) {
            "rain" -> rainStreaks(canvas, w, h, rng, alpha = 90)
            "snow" -> snowflakes(canvas, w, h, rng, alpha = 170)
            "storm" -> lightning(canvas, w, h, rng)
        }
    }

    private fun cloudy(canvas: Canvas, w: Float, h: Float, code: Int, rng: Random) {
        val overcast = code == 3 || code == 45 || code == 48
        if (overcast) {
            sky(
                canvas, w, h,
                Color.parseColor("#39424F"),
                Color.parseColor("#5C6875"),
                Color.parseColor("#8C99A5")
            )
        } else {
            sky(
                canvas, w, h,
                Color.parseColor("#2E6E9E"),
                Color.parseColor("#6FA6C9"),
                Color.parseColor("#A8C4D4")
            )
            glow(canvas, w * 0.80f, h * 0.24f, w * 0.24f, Color.argb(110, 255, 246, 215))
        }

        cloudBank(canvas, w, h, Color.argb(if (overcast) 105 else 130, 255, 255, 255), rng, layers = 4)

        if (code == 45 || code == 48) {
            // Fog: soft horizontal bands rather than discrete clouds.
            val band = Paint(Paint.ANTI_ALIAS_FLAG)
            for (i in 0 until 6) {
                val y = h * (0.42f + i * 0.10f)
                band.shader = LinearGradient(
                    0f, y, w, y,
                    Color.argb(0, 220, 226, 232),
                    Color.argb(62, 220, 226, 232),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, y, w, y + h * 0.07f, band)
            }
        }
    }

    private fun rainy(canvas: Canvas, w: Float, h: Float, rng: Random) {
        sky(
            canvas, w, h,
            Color.parseColor("#1B2C3E"),
            Color.parseColor("#2F4A66"),
            Color.parseColor("#4C6E8C")
        )
        cloudBank(canvas, w, h, Color.argb(95, 190, 205, 220), rng, layers = 4)
        rainStreaks(canvas, w, h, rng, alpha = 105)
    }

    private fun snowy(canvas: Canvas, w: Float, h: Float, rng: Random) {
        sky(
            canvas, w, h,
            Color.parseColor("#3F4E5C"),
            Color.parseColor("#6D7F8E"),
            Color.parseColor("#A9BAC6")
        )
        cloudBank(canvas, w, h, Color.argb(85, 225, 233, 240), rng, layers = 3)
        snowflakes(canvas, w, h, rng, alpha = 205)
    }

    private fun stormy(canvas: Canvas, w: Float, h: Float, rng: Random) {
        sky(
            canvas, w, h,
            Color.parseColor("#10131F"),
            Color.parseColor("#1E2438"),
            Color.parseColor("#333A54")
        )
        cloudBank(canvas, w, h, Color.argb(105, 120, 130, 155), rng, layers = 5)
        lightning(canvas, w, h, rng)
        rainStreaks(canvas, w, h, rng, alpha = 85)
    }

    // ----------------------------------------------------------------- pieces

    private fun glow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        val paint = Paint().apply {
            shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(color, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, radius, paint)
    }

    /**
     * Soft cloud masses built from overlapping circles. Sine-based placement
     * keeps them irregular without looking scattered, and each layer sits lower
     * and more opaque than the one behind it.
     */
    private fun cloudBank(
        canvas: Canvas,
        w: Float,
        h: Float,
        color: Int,
        rng: Random,
        layers: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (layer in 0 until layers) {
            val depth = layer / (layers - 1f).coerceAtLeast(1f)
            val baseY = h * (0.10f + depth * 0.30f)
            val scale = 0.55f + depth * 0.75f
            paint.color = Color.argb(
                (Color.alpha(color) * (0.45f + depth * 0.55f)).toInt().coerceIn(0, 255),
                Color.red(color), Color.green(color), Color.blue(color)
            )

            var x = -w * 0.1f + rng.nextFloat() * w * 0.15f
            while (x < w * 1.1f) {
                val puffY = baseY + sin(x / w * 6.2f + layer) * h * 0.035f
                val r = h * (0.055f + rng.nextFloat() * 0.05f) * scale
                canvas.drawCircle(x, puffY, r, paint)
                canvas.drawCircle(x + r * 0.75f, puffY + r * 0.25f, r * 0.78f, paint)
                canvas.drawCircle(x - r * 0.7f, puffY + r * 0.3f, r * 0.66f, paint)
                x += r * 1.55f
            }
        }
    }

    private fun rainStreaks(canvas: Canvas, w: Float, h: Float, rng: Random, alpha: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
        }
        repeat(260) {
            val x = rng.nextFloat() * w * 1.1f - w * 0.05f
            val y = rng.nextFloat() * h
            val len = h * (0.025f + rng.nextFloat() * 0.045f)
            val lean = len * 0.22f
            paint.strokeWidth = 1.6f + rng.nextFloat() * 2.2f
            paint.color = Color.argb(
                (alpha * (0.4f + rng.nextFloat() * 0.6f)).toInt(), 220, 234, 245
            )
            canvas.drawLine(x, y, x - lean, y + len, paint)
        }
    }

    private fun snowflakes(canvas: Canvas, w: Float, h: Float, rng: Random, alpha: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(190) {
            val x = rng.nextFloat() * w
            val y = rng.nextFloat() * h
            // Depth cue: smaller flakes are dimmer and read as further away.
            val depth = rng.nextFloat()
            val r = 2f + depth * 7f
            paint.color = Color.argb(
                (alpha * (0.35f + depth * 0.65f)).toInt(), 255, 255, 255
            )
            canvas.drawCircle(x, y, r, paint)
        }
    }

    private fun lightning(canvas: Canvas, w: Float, h: Float, rng: Random) {
        // One bolt on the right side, away from the text, with a soft flash
        // behind it so it reads as illumination rather than a drawn line.
        val startX = w * (0.62f + rng.nextFloat() * 0.28f)
        glow(canvas, startX, h * 0.30f, w * 0.26f, Color.argb(60, 190, 205, 255))

        val path = Path().apply { moveTo(startX, h * 0.10f) }
        var x = startX
        var y = h * 0.10f
        val segments = 5 + rng.nextInt(3)
        repeat(segments) {
            x += (rng.nextFloat() - 0.45f) * w * 0.07f
            y += h * (0.055f + rng.nextFloat() * 0.045f)
            path.lineTo(x, y)
        }

        val bolt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        bolt.strokeWidth = 14f
        bolt.color = Color.argb(70, 200, 215, 255)
        canvas.drawPath(path, bolt)
        bolt.strokeWidth = 4.5f
        bolt.color = Color.argb(235, 245, 248, 255)
        canvas.drawPath(path, bolt)
    }
}
