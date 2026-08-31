package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Weather glyphs drawn directly with Path, not bundled as drawables.
 *
 * Vector means sharp at any size and no asset licensing to worry about, and
 * drawing straight onto the same Canvas avoids a second bitmap allocation.
 * Everything is defined in a 100x100 box and scaled by the caller.
 */
object WeatherIcons {

    private const val BOX = 100f

    fun draw(canvas: Canvas, code: Int, isDay: Boolean, cx: Float, cy: Float, size: Float) {
        val scale = size / BOX
        canvas.save()
        canvas.translate(cx - size / 2f, cy - size / 2f)
        canvas.scale(scale, scale)

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
            setShadowLayer(6f, 0f, 2f, Color.argb(110, 0, 0, 0))
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            setShadowLayer(6f, 0f, 2f, Color.argb(110, 0, 0, 0))
        }

        when (OpenMeteoClient.bucket(code)) {
            "clear" -> if (isDay) sun(canvas, stroke, fill) else moon(canvas, fill)
            "cloud" -> when (code) {
                45, 48 -> fog(canvas, stroke, fill)
                2 -> if (isDay) sunBehindCloud(canvas, stroke, fill) else cloud(canvas, fill, 0f)
                else -> cloud(canvas, fill, 0f)
            }
            "rain" -> precip(canvas, stroke, fill, drops = true)
            "snow" -> precip(canvas, stroke, fill, drops = false)
            "storm" -> storm(canvas, stroke, fill)
            else -> cloud(canvas, fill, 0f)
        }

        canvas.restore()
    }

    private fun sun(canvas: Canvas, stroke: Paint, fill: Paint) {
        canvas.drawCircle(50f, 50f, 20f, fill)
        // Eight rays, drawn as short radial strokes.
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val sx = 50f + (Math.cos(angle) * 30).toFloat()
            val sy = 50f + (Math.sin(angle) * 30).toFloat()
            val ex = 50f + (Math.cos(angle) * 41).toFloat()
            val ey = 50f + (Math.sin(angle) * 41).toFloat()
            canvas.drawLine(sx, sy, ex, ey, stroke)
        }
    }

    private fun moon(canvas: Canvas, fill: Paint) {
        // Crescent via difference of two circles, using a saved layer so the
        // subtraction doesn't punch through the background behind it.
        val path = Path().apply {
            addCircle(52f, 48f, 24f, Path.Direction.CW)
            addCircle(66f, 38f, 22f, Path.Direction.CCW)
            fillType = Path.FillType.EVEN_ODD
        }
        canvas.drawPath(path, fill)
    }

    private fun cloud(canvas: Canvas, fill: Paint, dy: Float) {
        val path = Path().apply {
            addCircle(38f, 55f + dy, 16f, Path.Direction.CW)
            addCircle(56f, 48f + dy, 21f, Path.Direction.CW)
            addCircle(70f, 58f + dy, 14f, Path.Direction.CW)
            addRoundRect(RectF(28f, 58f + dy, 78f, 74f + dy), 8f, 8f, Path.Direction.CW)
        }
        canvas.drawPath(path, fill)
    }

    private fun sunBehindCloud(canvas: Canvas, stroke: Paint, fill: Paint) {
        canvas.drawCircle(66f, 32f, 14f, fill)
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val sx = 66f + (Math.cos(angle) * 20).toFloat()
            val sy = 32f + (Math.sin(angle) * 20).toFloat()
            val ex = 66f + (Math.cos(angle) * 27).toFloat()
            val ey = 32f + (Math.sin(angle) * 27).toFloat()
            canvas.drawLine(sx, sy, ex, ey, stroke)
        }
        cloud(canvas, fill, 8f)
    }

    private fun fog(canvas: Canvas, stroke: Paint, fill: Paint) {
        cloud(canvas, fill, -8f)
        val bars = Paint(stroke).apply { strokeWidth = 6f; alpha = 200 }
        canvas.drawLine(24f, 76f, 72f, 76f, bars)
        canvas.drawLine(34f, 88f, 82f, 88f, bars)
    }

    private fun precip(canvas: Canvas, stroke: Paint, fill: Paint, drops: Boolean) {
        cloud(canvas, fill, -10f)
        if (drops) {
            val drop = Paint(stroke).apply { strokeWidth = 6f }
            canvas.drawLine(40f, 72f, 34f, 88f, drop)
            canvas.drawLine(54f, 72f, 48f, 88f, drop)
            canvas.drawLine(68f, 72f, 62f, 88f, drop)
        } else {
            // Snowflakes as small six-point asterisks.
            for (x in listOf(38f, 54f, 70f)) {
                for (i in 0 until 3) {
                    val angle = Math.toRadians(i * 60.0)
                    val dx = (Math.cos(angle) * 7).toFloat()
                    val dy = (Math.sin(angle) * 7).toFloat()
                    canvas.drawLine(x - dx, 80f - dy, x + dx, 80f + dy, stroke)
                }
            }
        }
    }

    private fun storm(canvas: Canvas, stroke: Paint, fill: Paint) {
        cloud(canvas, fill, -12f)
        val bolt = Path().apply {
            moveTo(56f, 66f)
            lineTo(44f, 86f)
            lineTo(53f, 86f)
            lineTo(48f, 99f)
            lineTo(64f, 79f)
            lineTo(54f, 79f)
            close()
        }
        canvas.drawPath(bolt, fill)
    }
}
