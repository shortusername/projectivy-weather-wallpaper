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
 * Layout sits top-left: Projectivy's clock/status is top-right, the app row
 * takes the bottom fifth, and TV overscan eats the outer few percent on some
 * sets. Optional panels stack downward from the detail line, so enabling one
 * never shifts anything above it.
 */
object WeatherRenderer {

    private const val W = 1920
    private const val H = 1080
    private const val MARGIN = 120f

    /** Below this the app row starts; nothing should be drawn past it. */
    private const val SAFE_BOTTOM = 840f

    const val OUTPUT_NAME = "weather_wallpaper.png"

    private lateinit var light: Typeface
    private lateinit var medium: Typeface

    private fun gradientFor(bucket: String, isDay: Boolean): Pair<Int, Int> = when {
        !isDay -> Color.parseColor("#0B1026") to Color.parseColor("#1C2541")
        bucket == "clear" -> Color.parseColor("#1E6FB8") to Color.parseColor("#7EC8E3")
        bucket == "cloud" -> Color.parseColor("#3E4A5B") to Color.parseColor("#8A9BA8")
        bucket == "rain" -> Color.parseColor("#243B53") to Color.parseColor("#4A6D8C")
        bucket == "snow" -> Color.parseColor("#4A5A6B") to Color.parseColor("#B8C6D1")
        bucket == "storm" -> Color.parseColor("#1A1A2E") to Color.parseColor("#3D3D5C")
        else -> Color.parseColor("#2B2B2B") to Color.parseColor("#5A5A5A")
    }

    private fun paint(size: Float, face: Typeface, alpha: Int = 255) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = face
            color = Color.WHITE
            this.alpha = alpha
            setShadowLayer(10f, 0f, 3f, Color.argb(140, 0, 0, 0))
        }

    fun render(context: Context, c: OpenMeteoClient.Conditions, placeLabel: String): File {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        light = Typeface.create("sans-serif-light", Typeface.NORMAL)
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        val bucket = OpenMeteoClient.bucket(c.weatherCode)
        val source = PreferencesManager.backgroundSource

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
            SceneBackgrounds.draw(canvas, W, H, c)
            drawScrim(canvas, strong = false)
        }

        var y = MARGIN + 60f

        canvas.drawText(placeLabel.uppercase(), MARGIN, y, paint(38f, medium, 205))
        y += 190f

        val temp = "${c.temperature.roundToInt()}${c.unitSuffix}"
        val tempPaint = paint(220f, light)
        canvas.drawText(temp, MARGIN, y, tempPaint)

        WeatherIcons.draw(
            canvas, c.weatherCode, c.isDay,
            cx = MARGIN + tempPaint.measureText(temp) + 130f,
            cy = y - 70f,
            size = 175f
        )

        y += 90f
        canvas.drawText(OpenMeteoClient.describe(c.weatherCode), MARGIN, y, paint(64f, light, 238))
        y += 78f

        // Primary detail line, always shown.
        val wind = buildString {
            append("Wind ${c.windSpeed.roundToInt()}")
            val dir = OpenMeteoClient.compass(c.windDirection)
            if (dir.isNotEmpty()) append(" $dir")
        }
        val detail = "Feels like ${c.apparentTemperature.roundToInt()}\u00B0   \u00B7   " +
                "H ${c.high.roundToInt()}\u00B0  L ${c.low.roundToInt()}\u00B0   \u00B7   $wind"
        canvas.drawText(detail, MARGIN, y, paint(40f, light, 195))
        y += 58f

        if (PreferencesManager.showStats) {
            statsLine(c)?.let {
                canvas.drawText(it, MARGIN, y, paint(34f, light, 165))
                y += 50f
            }
        }

        if (PreferencesManager.showSun) {
            sunLine(c)?.let {
                canvas.drawText(it, MARGIN, y, paint(34f, light, 165))
                y += 50f
            }
        }

        // Hourly and daily sit side by side on one row so both can be enabled
        // without running into the app row.
        val showHourly = PreferencesManager.showHourly && c.hourly.isNotEmpty()
        val showDaily = PreferencesManager.showDaily && c.daily.isNotEmpty()

        if (showHourly || showDaily) {
            val stripTop = y + 22f
            if (stripTop + 130f <= SAFE_BOTTOM) {
                var x = MARGIN
                if (showHourly) {
                    x = hourlyStrip(canvas, c, x, stripTop)
                    x += 70f
                }
                if (showDaily) {
                    dailyStrip(canvas, c, x, stripTop)
                }
            }
        }

        attribution?.let {
            canvas.drawText(it, MARGIN, H - 46f, paint(26f, light, 130))
        }

        val out = File(context.cacheDir, OUTPUT_NAME)
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return out
    }

    // ------------------------------------------------------------------ lines

    private fun statsLine(c: OpenMeteoClient.Conditions): String? {
        val parts = mutableListOf<String>()
        if (c.humidity >= 0) parts.add("Humidity ${c.humidity}%")
        if (!c.uvIndexMax.isNaN()) parts.add("UV ${c.uvIndexMax.roundToInt()}")
        if (!c.dewPoint.isNaN()) parts.add("Dew ${c.dewPoint.roundToInt()}\u00B0")
        if (!c.visibility.isNaN()) {
            // Open-Meteo returns visibility in metres regardless of unit choice.
            val v = if (c.metric) "${(c.visibility / 1000).roundToInt()} km"
            else "${(c.visibility / 1609.34).roundToInt()} mi"
            parts.add("Visibility $v")
        }
        if (!c.pressure.isNaN()) {
            val p = if (c.metric) "${c.pressure.roundToInt()} hPa"
            else String.format("%.2f inHg", c.pressure * 0.02953)
            parts.add(p)
        }
        return if (parts.isEmpty()) null else parts.joinToString("   \u00B7   ")
    }

    private fun sunLine(c: OpenMeteoClient.Conditions): String? {
        val rise = OpenMeteoClient.formatTime(c.sunrise)
        val set = OpenMeteoClient.formatTime(c.sunset)
        if (rise.isEmpty() && set.isEmpty()) return null

        val parts = mutableListOf<String>()
        if (rise.isNotEmpty()) parts.add("Sunrise $rise")
        if (set.isNotEmpty()) parts.add("Sunset $set")

        // Only meaningful while the sun is still up.
        if (c.isDay && c.sunset.isNotEmpty()) {
            val nowIso = c.sunset.substring(0, 11) + nowClock()
            OpenMeteoClient.minutesUntil(c.sunset, nowIso)?.let { mins ->
                val h = mins / 60
                val m = mins % 60
                parts.add(if (h > 0) "${h}h ${m}m of daylight left" else "${m}m of daylight left")
            }
        }
        return parts.joinToString("   \u00B7   ")
    }

    /** Local wall clock as "HH:mm", to compare against Open-Meteo's local times. */
    private fun nowClock(): String {
        val cal = java.util.Calendar.getInstance()
        return String.format(
            "%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    }

    // ----------------------------------------------------------------- strips

    /** Returns the x coordinate just past the strip it drew. */
    private fun hourlyStrip(
        canvas: Canvas,
        c: OpenMeteoClient.Conditions,
        startX: Float,
        top: Float
    ): Float {
        val colWidth = 118f
        val entries = c.hourly.take(6)

        val panel = RectF(
            startX - 26f, top - 14f,
            startX + colWidth * entries.size + 10f, top + 128f
        )
        canvas.drawRoundRect(panel, 18f, 18f, panelPaint())

        entries.forEachIndexed { i, hour ->
            val cx = startX + colWidth * i + colWidth / 2f - 12f
            canvas.drawText(hour.label, cx - labelHalf(hour.label, 28f), top + 22f, paint(28f, light, 175))
            WeatherIcons.draw(canvas, hour.weatherCode, hour.isDay, cx, top + 60f, 46f)
            val t = "${hour.temperature.roundToInt()}\u00B0"
            canvas.drawText(t, cx - labelHalf(t, 36f), top + 108f, paint(36f, light, 235))

            // Only show precipitation chance when it's worth knowing.
            if (hour.precipChance >= 20) {
                val p = "${hour.precipChance}%"
                canvas.drawText(
                    p, cx - labelHalf(p, 24f), top + 128f,
                    paint(24f, light, 190).apply { color = Color.parseColor("#8FD3F4") }
                )
            }
        }
        return panel.right
    }

    private fun dailyStrip(
        canvas: Canvas,
        c: OpenMeteoClient.Conditions,
        startX: Float,
        top: Float
    ) {
        val colWidth = 124f
        val entries = c.daily.take(5)

        val panel = RectF(
            startX - 26f, top - 14f,
            startX + colWidth * entries.size + 10f, top + 128f
        )
        if (panel.right > W - 60f) return   // no room; skip rather than overflow
        canvas.drawRoundRect(panel, 18f, 18f, panelPaint())

        entries.forEachIndexed { i, day ->
            val cx = startX + colWidth * i + colWidth / 2f - 12f
            canvas.drawText(day.label, cx - labelHalf(day.label, 28f), top + 22f, paint(28f, light, 175))
            WeatherIcons.draw(canvas, day.weatherCode, true, cx, top + 60f, 46f)
            val hi = "${day.high.roundToInt()}\u00B0"
            val lo = "${day.low.roundToInt()}\u00B0"
            canvas.drawText(hi, cx - labelHalf(hi, 34f) - 22f, top + 108f, paint(34f, light, 235))
            canvas.drawText(lo, cx - labelHalf(lo, 30f) + 24f, top + 108f, paint(30f, light, 150))
        }
    }

    private fun panelPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(52, 0, 0, 0)
    }

    /** Half the rendered width of a label, for centring without a layout pass. */
    private fun labelHalf(text: String, size: Float): Float =
        paint(size, light).measureText(text) / 2f

    // ------------------------------------------------------------------ scrim

    /**
     * Full-height scrim. Drawn across the whole canvas on purpose: an earlier
     * version stopped the rect at 65% height while its gradient was still
     * faintly opaque there, and the rect's own edge showed as a hard seam.
     */
    private fun drawScrim(canvas: Canvas, strong: Boolean) {
        val topAlpha = if (strong) 190 else 140
        val midAlpha = if (strong) 95 else 60
        val midStop = if (strong) 0.38f else 0.30f
        val endStop = if (strong) 0.78f else 0.68f

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
