package tv.projectivy.plugin.wallpaperprovider.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Draws the weather panel into a 1920x1080 bitmap and writes it to cacheDir.
 *
 * Layout sits top-left and is generously padded: Projectivy's clock/status sits
 * top-right, the app row occupies the bottom third, and TV overscan eats the
 * outer few percent on some sets.
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
        val source = PreferencesManager.backgroundSource

        // Try the configured image source; fall back to the gradient on any
        // failure so a network blip never blanks the screen.
        val resolved = if (source == Backgrounds.SOURCE_SCENE || source == Backgrounds.SOURCE_GRADIENT) {
            null
        } else {
            Backgrounds.resolve(context, source, c, W, H)
        }
        var attribution: String? = null

        if (resolved != null) {
            canvas.drawBitmap(resolved.first, 0f, 0f, null)
            resolved.first.recycle()
            attribution = resolved.second
            // Photos and maps need a stronger, wider scrim than a flat gradient.
            drawScrim(canvas, strong = true)
        } else if (source == Backgrounds.SOURCE_GRADIENT) {
            val (top, bottom) = gradientFor(bucket, c.isDay)
            val bg = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, W * 0.4f, H.toFloat(), top, bottom, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
            drawScrim(canvas, strong = false)
        } else {
            // Default: procedural scene for the current condition.
            SceneBackgrounds.draw(canvas, W, H, c)
            drawScrim(canvas, strong = false)
        }

        val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        fun paint(size: Float, face: Typeface, alpha: Int = 255) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                typeface = face
                color = Color.WHITE
                this.alpha = alpha
                setShadowLayer(10f, 0f, 3f, Color.argb(140, 0, 0, 0))
            }

        var y = MARGIN + 60f

        canvas.drawText(placeLabel.uppercase(), MARGIN, y, paint(38f, medium, 205))
        y += 190f

        val temp = "${c.temperature.roundToInt()}${c.unitSuffix}"
        val tempPaint = paint(220f, light)
        canvas.drawText(temp, MARGIN, y, tempPaint)

        // Icon sits to the right of the temperature, vertically centred on it.
        val tempWidth = tempPaint.measureText(temp)
        WeatherIcons.draw(
            canvas, c.weatherCode, c.isDay,
            cx = MARGIN + tempWidth + 130f,
            cy = y - 70f,
            size = 175f
        )

        y += 90f
        canvas.drawText(OpenMeteoClient.describe(c.weatherCode), MARGIN, y, paint(64f, light, 238))
        y += 80f

        val detail = "Feels like ${c.apparentTemperature.roundToInt()}°   ·   " +
                "H ${c.high.roundToInt()}°  L ${c.low.roundToInt()}°   ·   " +
                "Wind ${c.windSpeed.roundToInt()}"
        canvas.drawText(detail, MARGIN, y, paint(40f, light, 195))

        // Attribution, bottom-left above the app row, small and unobtrusive.
        attribution?.let {
            canvas.drawText(it, MARGIN, H - 60f, paint(26f, light, 130))
        }

        val out = File(context.cacheDir, OUTPUT_NAME)
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return out
    }

    /**
     * Full-height scrim. Drawn across the whole canvas on purpose: an earlier
     * version stopped the rect at 65% height while its gradient was still
     * faintly opaque there, and the rect's own edge showed as a hard seam.
     */
    private fun drawScrim(canvas: Canvas, strong: Boolean) {
        val topAlpha = if (strong) 190 else 140
        val midAlpha = if (strong) 95 else 60
        val midStop = if (strong) 0.38f else 0.30f
        val endStop = if (strong) 0.68f else 0.50f

        val scrim = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                intArrayOf(
                    Color.argb(topAlpha, 0, 0, 0),
                    Color.argb(midAlpha, 0, 0, 0),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, midStop, endStop),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), scrim)

        if (strong) {
            // Horizontal falloff as well, so the right side of a photo stays
            // visible while the text side keeps its contrast.
            val side = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, W * 0.62f, 0f,
                    Color.argb(120, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), side)
        }
    }
}
