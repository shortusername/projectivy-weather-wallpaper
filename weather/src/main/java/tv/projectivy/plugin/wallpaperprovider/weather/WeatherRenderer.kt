package tv.projectivy.plugin.wallpaperprovider.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Draws the weather panel into a 1920x1080 bitmap and writes it to cacheDir.
 *
 * Layout is deliberately top-left and generously padded: Projectivy's own
 * clock/status sits top-right, and TV overscan eats the outer ~3% on some sets.
 */
object WeatherRenderer {

    private const val W = 1920
    private const val H = 1080
    private const val MARGIN = 120f
    const val OUTPUT_NAME = "weather_wallpaper.png"

    private fun gradientFor(bucket: String, isDay: Boolean): Pair<Int, Int> = when {
        !isDay -> Color.parseColor("#0B1026") to Color.parseColor("#1C2541")
        bucket == "clear" -> Color.parseColor("#1E6FB8") to Color.parseColor("#7EC8E3")
        bucket == "cloud" -> Color.parseColor("#3E4A5B") to Color.parseColor("#8A9BA8")
        bucket == "rain" -> Color.parseColor("#243B53") to Color.parseColor("#4A6D8C")
        bucket == "snow" -> Color.parseColor("#4A5A6B") to Color.parseColor("#B8C6D1")
        bucket == "storm" -> Color.parseColor("#1A1A2E") to Color.parseColor("#3D3D5C")
        else -> Color.parseColor("#2B2B2B") to Color.parseColor("#5A5A5A")
    }

    fun render(context: Context, c: OpenMeteoClient.Conditions, placeLabel: String): File {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bucket = OpenMeteoClient.bucket(c.weatherCode)
        val (top, bottom) = gradientFor(bucket, c.isDay)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, W * 0.4f, H.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        // Scrim behind the text block so labels stay legible on any gradient.
        // Drawn full-height with the fade completing at 50%: if the rect ended
        // where the gradient ended, the rect edge itself would show as a seam.
        val scrim = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                intArrayOf(
                    Color.argb(140, 0, 0, 0),
                    Color.argb(60, 0, 0, 0),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.30f, 0.50f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), scrim)

        val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        fun paint(size: Float, face: Typeface, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = face
            color = Color.WHITE
            this.alpha = alpha
            setShadowLayer(8f, 0f, 3f, Color.argb(120, 0, 0, 0))
        }

        var y = MARGIN + 60f

        canvas.drawText(placeLabel.uppercase(), MARGIN, y, paint(38f, medium, 200))
        y += 190f

        val temp = "${c.temperature.roundToInt()}${c.unitSuffix}"
        canvas.drawText(temp, MARGIN, y, paint(220f, light))
        y += 90f

        canvas.drawText(OpenMeteoClient.describe(c.weatherCode), MARGIN, y, paint(64f, light, 235))
        y += 80f

        val detail = "Feels like ${c.apparentTemperature.roundToInt()}°   ·   " +
                "H ${c.high.roundToInt()}°  L ${c.low.roundToInt()}°   ·   " +
                "Wind ${c.windSpeed.roundToInt()}"
        canvas.drawText(detail, MARGIN, y, paint(40f, light, 190))

        val out = File(context.cacheDir, OUTPUT_NAME)
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return out
    }
}
