package tv.projectivy.plugin.wallpaperprovider.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
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

    /**
     * Anti burn-in pixel shift.
     *
     * A slow walk around an 8-point circle, one step every SHIFT_INTERVAL_MS
     * of real elapsed time — not render count, since burn-in risk tracks how
     * long the screen has actually shown this content, not how often the
     * plugin happened to redraw it. Whichever step is "current" gets used
     * whenever a render does happen; there's no need to track continuity
     * across periods where the wallpaper wasn't even on screen; e.g. while
     * something else was playing, since there's no burn-in risk to offset
     * during that time regardless.
     *
     * Kept to whole pixels via a plain canvas translate wrapped around the
     * panel/clock/banner draw calls only — never the background, which
     * already varies enough with conditions and time of day, and never fed
     * into layout decisions like whether the forecast strips fit, which are
     * computed independently and have far more clearance than this shift's
     * range needs.
     */
    private const val SHIFT_RADIUS = 4f
    private const val SHIFT_INTERVAL_MS = 12 * 60 * 1000L  // 12 minutes/step
    private val SHIFT_PATTERN: List<Pair<Float, Float>> = (0 until 8).map { i ->
        val angle = Math.toRadians(i * 45.0)
        (Math.cos(angle).toFloat() * SHIFT_RADIUS) to (Math.sin(angle).toFloat() * SHIFT_RADIUS)
    }

    /**
     * Which step of the shift pattern is current, as a plain counter.
     *
     * Public so the render-cache key in WallpaperProviderService can include
     * it directly rather than duplicating SHIFT_INTERVAL_MS as a second
     * hardcoded constant that would have to be kept in sync by hand — exactly
     * the kind of silent drift this codebase has been bitten by before.
     */
    fun burnInBucket(): Long = System.currentTimeMillis() / SHIFT_INTERVAL_MS

    private fun burnInOffset(): Pair<Float, Float> {
        if (!PreferencesManager.reduceBurnIn) return 0f to 0f
        val step = burnInBucket() % SHIFT_PATTERN.size
        return SHIFT_PATTERN[step.toInt()]
    }

    /**
     * Where the launcher's app row starts. Nothing is drawn past it.
     *
     * A setting rather than a measurement: the plugin API exposes no layout
     * information, so there is nothing to detect. When the launcher reports
     * itself idle, nothing is covering the wallpaper and the full frame is
     * available instead.
     */
    private fun safeBottom(): Float =
        if (PreferencesManager.idleFullFrame && PreferencesManager.launcherIdle) {
            H - 60f
        } else {
            H * (PreferencesManager.safeBottomPercent / 100f)
        }

    /** Height of the hourly/daily strip panels, including the precip row. */
    private const val STRIP_HEIGHT = 142f

    /**
     * Output files carry a timestamp rather than a fixed name.
     *
     * The launcher loads the wallpaper through an image loader that caches by
     * URI. With a constant filename the content:// URI was identical on every
     * render, so a freshly drawn bitmap was discarded in favour of the cached
     * one and the wallpaper appeared frozen — including after settings changes.
     */
    private const val OUTPUT_PREFIX = "weather_wallpaper_"
    private const val OVERLAY_PREFIX = "weather_overlay_"

    private lateinit var light: Typeface
    private lateinit var medium: Typeface

    /**
     * Set by the service before rendering. Kept here rather than threaded
     * through every signature because both render paths need it and neither
     * owns the fetch.
     */
    @Volatile
    var currentAlert: NwsAlertsClient.Alert? = null

    /** Set by the service alongside the alert. Null when nothing to report. */
    @Volatile
    var currentAurora: AuroraClient.Conditions? = null

    @Volatile
    var currentAir: AirQualityClient.Reading? = null

    private fun gradientFor(bucket: String, isDay: Boolean): Pair<Int, Int> = when {
        !isDay -> Color.parseColor("#0B1026") to Color.parseColor("#1C2541")
        bucket == "clear" -> Color.parseColor("#1E6FB8") to Color.parseColor("#7EC8E3")
        bucket == "cloud" -> Color.parseColor("#3E4A5B") to Color.parseColor("#8A9BA8")
        bucket == "rain" -> Color.parseColor("#243B53") to Color.parseColor("#4A6D8C")
        bucket == "snow" -> Color.parseColor("#4A5A6B") to Color.parseColor("#B8C6D1")
        bucket == "storm" -> Color.parseColor("#1A1A2E") to Color.parseColor("#3D3D5C")
        else -> Color.parseColor("#2B2B2B") to Color.parseColor("#5A5A5A")
    }

    /** Set once per render from the user's size preference. */
    private var scale = 1f

    /** Scaled type size. */
    private fun sz(v: Float) = v * scale

    /** Scaled vertical step. */
    private fun dy(v: Float) = v * scale

    /**
     * A uniquely named output file, with older generations removed.
     *
     * Keeps the two most recent: the launcher may still be reading the previous
     * one when the next render lands.
     */
    private fun freshOutput(context: Context, prefix: String, ext: String): File {
        val dir = context.cacheDir
        dir.listFiles { f -> f.name.startsWith(prefix) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(1)
            ?.forEach { runCatching { it.delete() } }
        return File(dir, "$prefix${System.currentTimeMillis()}.$ext")
    }

    private fun stripHeight() = STRIP_HEIGHT * scale

    private fun paint(size: Float, face: Typeface, alpha: Int = 255) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = face
            color = Color.WHITE
            this.alpha = alpha
            setShadowLayer(10f, 0f, 3f, Color.argb(140, 0, 0, 0))
        }

    /**
     * World weather watch: a notable weather event somewhere else in the world.
     *
     * Laid out deliberately unlike the local view. A header names the feature,
     * the affected country is prominent, and the caption strip runs along the
     * bottom rather than the panel sitting top-left. Someone glancing at the
     * screen has to be able to tell at once that this is not their own weather
     * — a Red flood alert mistaken for local would be actively misleading.
     */
    fun renderWorldEvent(
        context: Context,
        event: WorldEventsClient.Event,
        conditions: OpenMeteoClient.Conditions?,
        phase: ThemeResolver.Phase
    ): File {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        light = Typeface.create("sans-serif-light", Typeface.NORMAL)
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        scale = PreferencesManager.panelScaleFactor

        // Cyclones and droughts span huge areas; floods and fires are local.
        val zoom = when (event.typeCode) {
            "TC", "DR" -> 4
            else -> 5
        }
        val map = Backgrounds.eventMap(
            context, W, H, phase, event.latitude, event.longitude, zoom
        )
        if (map != null) {
            canvas.drawBitmap(map, 0f, 0f, null)
            map.recycle()
        } else {
            canvas.drawColor(Color.parseColor("#0A1420"))
        }

        // Top and bottom scrims: the caption sits low, the header high.
        val topScrim = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, H * 0.22f,
                Color.argb(200, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H * 0.22f, topScrim)

        val bottomScrim = Paint().apply {
            shader = LinearGradient(
                0f, H.toFloat(), 0f, H * 0.52f,
                Color.argb(215, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, H * 0.52f, W.toFloat(), H.toFloat(), bottomScrim)

        // Everything from here down is fixed-position, high-contrast text —
        // the same burn-in exposure as the local panel — so it gets the same
        // shift, wrapped around the whole block up to the credit line.
        val (shiftX, shiftY) = burnInOffset()
        canvas.save()
        canvas.translate(shiftX, shiftY)

        // Header: says plainly what this is.
        canvas.drawText("WORLD WEATHER WATCH", MARGIN, MARGIN + 24f, paint(34f, medium, 215))

        // Alert-level chip beside the header.
        val chipLabel = "${event.alertLevel.uppercase()} ALERT"
        val chipPaint = paint(26f, medium, 240)
        val chipW = chipPaint.measureText(chipLabel) + 44f
        val chipLeft = MARGIN + paint(34f, medium).measureText("WORLD WEATHER WATCH") + 40f
        val chipRect = RectF(chipLeft, MARGIN - 4f, chipLeft + chipW, MARGIN + 38f)
        canvas.drawRoundRect(chipRect, 19f, 19f, Paint().apply {
            color = WorldEventsClient.colorFor(event.alertLevel)
        })
        canvas.drawText(chipLabel, chipLeft + 22f, MARGIN + 26f, chipPaint)

        // Caption block, bottom left, above the app shelf.
        var y = H - 250f
        canvas.drawText(event.countries.uppercase(), MARGIN, y, paint(32f, medium, 200))
        y += 84f
        canvas.drawText(event.name, MARGIN, y, paint(76f, light))

        // Named cyclones get a type line; a generated name like "Flood in
        // Nepal" already contains it, so printing "Flood" again reads oddly.
        if (!event.nameIsGenerated) {
            y += 52f
            canvas.drawText(event.type, MARGIN, y, paint(38f, light, 210))
        }

        if (event.severityText.isNotEmpty()) {
            y += 46f
            canvas.drawText(event.severityText, MARGIN, y, paint(30f, light, 175))
        }

        // Conditions at the event location, right-aligned so it can't be
        // confused with the local panel's left-aligned block.
        conditions?.let { c ->
            val temp = "${c.temperature.roundToInt()}${c.unitSuffix}"
            val tp = paint(96f, light, 235)
            canvas.drawText(temp, W - MARGIN - tp.measureText(temp), H - 268f, tp)

            val desc = OpenMeteoClient.describe(c.weatherCode)
            val dp = paint(36f, light, 200)
            canvas.drawText(desc, W - MARGIN - dp.measureText(desc), H - 214f, dp)

            WeatherIcons.draw(
                canvas, c.weatherCode, phase.isDay,
                cx = W - MARGIN - 62f, cy = H - 392f, size = 104f
            )
        }

        val credit = "Events: GDACS (UN/EC) \u00B7 Radar: RainViewer \u00B7 Map: Natural Earth"
        canvas.drawText(credit, MARGIN, H - 46f, paint(24f, light, 125))
        canvas.restore()

        val out = freshOutput(context, OUTPUT_PREFIX, "png")
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return out
    }

    /**
     * Panel only, on a transparent canvas.
     *
     * Used when the background is an animation the launcher renders itself: we
     * can't composite onto that, so the panel is embedded into the Lottie as an
     * video frames instead. See VideoEncoder.
     */
    fun renderOverlay(context: Context, c: OpenMeteoClient.Conditions, placeLabel: String): File {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        light = Typeface.create("sans-serif-light", Typeface.NORMAL)
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        scale = PreferencesManager.panelScaleFactor

        // Scrim still needed: contributed art can be any brightness.
        val overlayPhase = ThemeResolver.resolve(c)
        drawScrim(canvas, strong = true)

        val (shiftX1, shiftY1) = burnInOffset()
        canvas.save()
        canvas.translate(shiftX1, shiftY1)
        drawPanel(
            canvas, c, placeLabel, attribution = null,
            alert = currentAlert, phaseIsDay = overlayPhase.isDay, context = context
        )
        canvas.restore()

        val out = freshOutput(context, OVERLAY_PREFIX, "png")
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return out
    }

    /**
     * The full still scene as a Bitmap, over a supplied background.
     *
     * Used by the animated radar path: the map and panel are composited once
     * here, then each radar observation is drawn over a copy of it.
     */
    fun composeScene(
        context: Context,
        c: OpenMeteoClient.Conditions,
        placeLabel: String,
        background: Bitmap?,
        alert: NwsAlertsClient.Alert?
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        light = Typeface.create("sans-serif-light", Typeface.NORMAL)
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        scale = PreferencesManager.panelScaleFactor

        val phase = ThemeResolver.resolve(c)
        if (background != null) {
            canvas.drawBitmap(background, 0f, 0f, null)
            drawScrim(canvas, strong = true, extraDark = phase == ThemeResolver.Phase.DAY)
        } else {
            SceneBackgrounds.draw(canvas, W, H, c, phase)
            drawScrim(canvas, strong = false)
        }
        val (shiftX2, shiftY2) = burnInOffset()
        canvas.save()
        canvas.translate(shiftX2, shiftY2)
        drawPanel(
            canvas, c, placeLabel,
            "Radar: RainViewer \u00B7 Map: Natural Earth \u00B7 Places: GeoNames (CC BY)",
            alert, phase.isDay, context
        )
        canvas.restore()
        return bitmap
    }

    fun render(context: Context, c: OpenMeteoClient.Conditions, placeLabel: String): File {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        light = Typeface.create("sans-serif-light", Typeface.NORMAL)
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        scale = PreferencesManager.panelScaleFactor

        val bucket = OpenMeteoClient.bucket(c.weatherCode)
        val source = PreferencesManager.backgroundSource
        val phase = ThemeResolver.resolve(c)

        val resolved = if (source == Backgrounds.SOURCE_SCENE || source == Backgrounds.SOURCE_GRADIENT) {
            null
        } else {
            Backgrounds.resolveCached(context, source, c, W, H, phase)
        }
        var attribution: String? = null

        val theme = HolidayThemes.current()

        // With a theme active the background is drawn to its own bitmap and
        // composited through a ColorMatrix. Without one it draws straight to
        // the canvas, so the extra 8 MB is only allocated when it's needed.
        val target: Canvas
        val layer: Bitmap?
        if (theme != null) {
            layer = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            target = Canvas(layer)
        } else {
            layer = null
            target = canvas
        }

        var strongScrim = false
        var extraDark = false

        if (resolved != null) {
            target.drawBitmap(resolved.first, 0f, 0f, null)
            resolved.first.recycle()
            attribution = resolved.second
            strongScrim = true
            extraDark = phase == ThemeResolver.Phase.DAY
        } else if (source == Backgrounds.SOURCE_GRADIENT) {
            val (top, bottom) = gradientFor(bucket, c.isDay)
            target.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
                shader = LinearGradient(
                    0f, 0f, W * 0.4f, H.toFloat(), top, bottom, Shader.TileMode.CLAMP
                )
            })
        } else {
            SceneBackgrounds.draw(target, W, H, c, phase)
        }

        if (layer != null && theme != null) {
            canvas.drawBitmap(layer, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(HolidayThemes.matrixFor(theme))
            })
            layer.recycle()
            if (theme.flecks) drawFlecks(canvas, theme)
        }

        drawScrim(canvas, strong = strongScrim, extraDark = extraDark)

        val (shiftX3, shiftY3) = burnInOffset()
        canvas.save()
        canvas.translate(shiftX3, shiftY3)
        drawPanel(canvas, c, placeLabel, attribution, currentAlert, phase.isDay, context)
        canvas.restore()

        val out = freshOutput(context, OUTPUT_PREFIX, "png")
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return out
    }

    /** Everything above the background: readings, icon, optional panels. */
    private fun drawPanel(
        canvas: Canvas,
        c: OpenMeteoClient.Conditions,
        placeLabel: String,
        attribution: String?,
        alert: NwsAlertsClient.Alert? = null,
        phaseIsDay: Boolean = false,
        context: Context? = null
    ) {
        // Subtle, slow drift to spread wear on burn-in-prone displays. Every
        // fixed-position element the panel draws — temperature, clock,
        // location label, the strip backgrounds — otherwise sits on the same
        // pixels refresh after refresh, which is exactly the pattern OLED
        // burn-in punishes. Real TVs solve this the same way: a small,
        // continuous, content-independent shift.
        //
        // Two different periods (not a simple ratio) so the path sweeps a
        // spread of positions rather than retracing a short loop. Amplitude is
        // tiny relative to MARGIN, so nothing gets pushed toward the overscan
        // edge, and the change between two consecutive refreshes is at most a
        // few pixels — imperceptible on a still image, and sub-pixel between
        // one-minute clock ticks so the clock itself never visibly jitters.
        // Verified numerically before shipping this.
        canvas.save()
        if (PreferencesManager.reduceBurnIn) {
            val t = System.currentTimeMillis() / 1000.0
            val dx = (6.0 * kotlin.math.cos(2 * Math.PI * t / (3 * 3600))).toFloat()
            val dy = (4.0 * kotlin.math.sin(2 * Math.PI * t / (4.5 * 3600))).toFloat()
            canvas.translate(dx, dy)
        }

        alert?.let { drawAlertBanner(canvas, it) }

        if (PreferencesManager.showClock && context != null) {
            drawClock(canvas, context, alert != null)
        }

        var y = MARGIN + dy(60f) + (if (alert != null) 74f else 0f)

        // Seasonal accent applies to the location label only. Everything below
        // it stays white: tinting the temperature would hurt legibility for
        // decoration, which is the wrong trade on a screen people glance at.
        val seasonal = HolidayThemes.current()
        val labelPaint = paint(sz(38f), medium, 205)
        seasonal?.let { labelPaint.color = it.accent }
        canvas.drawText(placeLabel.uppercase(), MARGIN, y, labelPaint)
        y += dy(190f)

        val temp = "${c.temperature.roundToInt()}${c.unitSuffix}"
        val tempPaint = paint(sz(220f), light)
        canvas.drawText(temp, MARGIN, y, tempPaint)

        WeatherIcons.draw(
            canvas, c.weatherCode, phaseIsDay,
            cx = MARGIN + tempPaint.measureText(temp) + dy(130f),
            cy = y - dy(70f),
            size = sz(175f)
        )

        y += dy(90f)
        canvas.drawText(
            OpenMeteoClient.describe(c.weatherCode), MARGIN, y, paint(sz(64f), light, 238)
        )

        // Whether it is about to rain matters more than anything else here, so
        // it goes immediately under the conditions and takes an accent colour.
        // Costs no space when there's nothing imminent.
        if (PreferencesManager.showNowcast) {
            c.nowcast?.let { n ->
                OpenMeteoClient.nowcastLabel(n, c.weatherCode)?.let { text ->
                    y += dy(52f)
                    canvas.drawText(text, MARGIN, y, paint(sz(40f), medium).apply {
                        color = Color.parseColor("#8FD3F4")
                    })
                }
            }
        }

        // One advisory, the most important. Grouped with the nowcast because
        // both answer "is there anything I should do about today".
        if (PreferencesManager.showAdvisories) {
            Advisories.top(c)?.let { advisory ->
                y += dy(50f)
                canvas.drawText(advisory.text, MARGIN, y, paint(sz(36f), medium).apply {
                    color = advisory.colour
                })
            }
        }

        // Poor air quality earns the same prominence as imminent rain. A
        // routine "Good 53" does not, and goes in the stats line instead.
        if (PreferencesManager.showAirQuality) {
            currentAir?.let { air ->
                AirQualityClient.alertLabel(air)?.let { text ->
                    y += dy(52f)
                    canvas.drawText(text, MARGIN, y, paint(sz(38f), medium).apply {
                        color = AirQualityClient.bandColour(air.aqi)
                    })
                }
            }
        }

        y += dy(78f)

        // Primary detail line, always shown.
        val wind = buildString {
            append("Wind ${c.windSpeed.roundToInt()}")
            val dir = OpenMeteoClient.compass(c.windDirection)
            if (dir.isNotEmpty()) append(" $dir")
        }
        val yesterday = if (PreferencesManager.showYesterday) yesterdayPhrase(c) else null
        val detail = buildString {
            append("Feels like ${c.apparentTemperature.roundToInt()}\u00B0   \u00B7   ")
            append("H ${c.high.roundToInt()}\u00B0  L ${c.low.roundToInt()}\u00B0   \u00B7   ")
            append(wind)
            // Appended rather than given its own line: it's context, and the
            // panel has limited vertical room.
            yesterday?.let { append("   \u00B7   $it") }
        }
        canvas.drawText(detail, MARGIN, y, paint(sz(40f), light, 195))
        y += dy(52f)

        // Aurora, when the K-index makes it plausible here. Conditional, so it
        // costs nothing on the overwhelming majority of nights.
        if (PreferencesManager.showAurora) {
            currentAurora?.let { a ->
                AuroraClient.label(a)?.let { text ->
                    y += dy(46f)
                    canvas.drawText(text, MARGIN, y, paint(sz(34f), medium, 220).apply {
                        color = Color.parseColor("#A8E6A0")
                    })
                }
            }
        }

        if (PreferencesManager.showStats) {
            statsLine(c)?.let {
                canvas.drawText(it, MARGIN, y, paint(sz(34f), light, 165))
                y += dy(46f)
            }
        }

        if (PreferencesManager.showSun) {
            sunLine(c)?.let {
                canvas.drawText(it, MARGIN, y, paint(sz(34f), light, 165))
                y += dy(46f)
            }
        }

        // Hourly and daily sit side by side on one row so both can be enabled
        // without running into the app row.
        val showHourly = PreferencesManager.showHourly && c.hourly.isNotEmpty()
        val showDaily = PreferencesManager.showDaily && c.daily.isNotEmpty()

        if (showHourly || showDaily) {
            // Clamp upward to stay clear of the app row rather than dropping the
            // strips entirely. The earlier version discarded them silently once
            // the stats and sun lines had pushed y far enough down, which looked
            // exactly like the toggles not working.
            val desired = y + dy(22f)
            val highest = safeBottom() - stripHeight()
            val stripTop = minOf(desired, highest)

            // Only bail if there is genuinely no room left below the text.
            if (stripTop >= y - 6f) {
                var x = MARGIN
                if (showHourly) {
                    x = hourlyStrip(canvas, c, x, stripTop)
                    x += dy(70f)
                }
                if (showDaily) {
                    dailyStrip(canvas, c, x, stripTop)
                }
            }
        }

        // Update notice, kept to the same small print as the credits. A
        // wallpaper is a poor place to shout, and it self-clears once updated.
        UpdateChecker.pendingVersion(BuildConfig.VERSION_NAME)?.let { version ->
            val notice = "Update available \u00B7 v$version"
            val p = paint(sz(26f), medium, 150)
            canvas.drawText(notice, MARGIN, H - MARGIN * 0.45f - dy(34f), p)
        }

        attribution?.let {
            canvas.drawText(it, MARGIN, H - 46f, paint(26f, light, 130))
        }

        canvas.restore()
    }

    /**
     * Clock, in the position, size and style the user picked.
     *
     * Only accurate because the service asks for a refresh every minute while
     * this is enabled; a wallpaper redrawn every fifteen minutes would show a
     * time wrong by up to a quarter of an hour, which is worse than no clock.
     *
     * There is deliberately no seconds option: that would mean re-rendering a
     * 1080p bitmap sixty times a minute.
     */
    private fun drawClock(canvas: Canvas, context: Context, alert: Boolean) {
        val cal = java.util.Calendar.getInstance()
        val base = sz(PreferencesManager.clockBaseSize)
        // Pushed down when an alert banner occupies the top of the frame.
        val offsetY = if (alert) 92f else 0f

        if (PreferencesManager.clockStyle == PreferencesManager.CLOCK_ANALOGUE) {
            drawAnalogueClock(canvas, cal, base, offsetY)
        } else {
            drawDigitalClock(canvas, context, cal, base, offsetY)
        }
    }

    private fun use24Hour(context: Context): Boolean =
        when (PreferencesManager.clockHours) {
            PreferencesManager.CLOCK_HOURS_12 -> false
            PreferencesManager.CLOCK_HOURS_24 -> true
            // The system setting is right for most people, but a TV's locale is
            // often wrong for the household using it, hence the override above.
            else -> android.text.format.DateFormat.is24HourFormat(context)
        }

    private fun dateText(cal: java.util.Calendar): String =
        java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault())
            .format(cal.time)

    private fun drawDigitalClock(
        canvas: Canvas,
        context: Context,
        cal: java.util.Calendar,
        base: Float,
        offsetY: Float
    ) {
        val h24 = use24Hour(context)
        val hour = if (h24) cal.get(java.util.Calendar.HOUR_OF_DAY)
        else cal.get(java.util.Calendar.HOUR).let { if (it == 0) 12 else it }
        val time = String.format("%d:%02d", hour, cal.get(java.util.Calendar.MINUTE))
        val suffix = if (h24) null else
            if (cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "AM" else "PM"

        val face = if (PreferencesManager.clockStyle == PreferencesManager.CLOCK_DIGITAL_BOLD)
            medium else light
        val timePaint = paint(base, face)
        val suffixPaint = paint(base * 0.35f, medium, 225)
        val datePaint = paint(base * 0.31f, light, 195)

        val timeWidth = timePaint.measureText(time)
        val suffixWidth = suffix?.let { suffixPaint.measureText(it) + base * 0.12f } ?: 0f
        val showDate = PreferencesManager.showClockDate
        val date = if (showDate) dateText(cal) else null

        when (PreferencesManager.clockPosition) {
            PreferencesManager.CLOCK_TOP_CENTRE -> {
                val centre = W / 2f
                val y = MARGIN * 0.5f + base * 0.75f + offsetY
                canvas.drawText(time, centre - (timeWidth + suffixWidth) / 2f, y, timePaint)
                suffix?.let {
                    canvas.drawText(
                        it,
                        centre + (timeWidth + suffixWidth) / 2f - suffixPaint.measureText(it),
                        y, suffixPaint
                    )
                }
                date?.let {
                    canvas.drawText(
                        it, centre - datePaint.measureText(it) / 2f, y + base * 0.42f, datePaint
                    )
                }
            }
            PreferencesManager.CLOCK_WITH_PANEL -> {
                // Above the location label, reading as one group with it.
                val y = MARGIN - base * 0.10f + offsetY
                canvas.drawText(time, MARGIN, y, timePaint)
                suffix?.let {
                    canvas.drawText(it, MARGIN + timeWidth + base * 0.10f, y, suffixPaint)
                }
            }
            else -> {
                val right = W - MARGIN
                val y = MARGIN + base * 0.62f + offsetY
                canvas.drawText(time, right - timeWidth - suffixWidth, y, timePaint)
                suffix?.let {
                    canvas.drawText(it, right - suffixPaint.measureText(it), y, suffixPaint)
                }
                date?.let {
                    canvas.drawText(
                        it, right - datePaint.measureText(it), y + base * 0.40f, datePaint
                    )
                }
            }
        }
    }

    /**
     * Analogue face.
     *
     * Charming but less legible across a room than digits, which is why the
     * setting suggests pairing it with the large size.
     */
    private fun drawAnalogueClock(
        canvas: Canvas,
        cal: java.util.Calendar,
        base: Float,
        offsetY: Float
    ) {
        val r = base * 0.62f
        val (cx, cy) = when (PreferencesManager.clockPosition) {
            PreferencesManager.CLOCK_TOP_CENTRE -> (W / 2f) to (MARGIN * 0.4f + r + offsetY)
            PreferencesManager.CLOCK_WITH_PANEL -> (MARGIN + r) to (MARGIN - r * 0.1f + offsetY)
            else -> (W - MARGIN - r) to (MARGIN + r * 0.5f + offsetY)
        }

        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(2f, r * 0.045f)
            color = Color.WHITE
            setShadowLayer(8f, 0f, 2f, Color.argb(140, 0, 0, 0))
        }
        canvas.drawCircle(cx, cy, r, rim)

        // Hour marks, longer at the quarters.
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 240, 240, 240)
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90.0)
            val inner = if (i % 3 == 0) r * 0.84f else r * 0.91f
            tick.strokeWidth = if (i % 3 == 0) maxOf(2f, r * 0.06f) else maxOf(1f, r * 0.03f)
            canvas.drawLine(
                cx + (Math.cos(a) * inner).toFloat(), cy + (Math.sin(a) * inner).toFloat(),
                cx + (Math.cos(a) * r * 0.97).toFloat(), cy + (Math.sin(a) * r * 0.97).toFloat(),
                tick
            )
        }

        val minute = cal.get(java.util.Calendar.MINUTE)
        val hourAngle = Math.toRadians(
            ((cal.get(java.util.Calendar.HOUR) % 12) + minute / 60.0) * 30.0 - 90.0
        )
        val minuteAngle = Math.toRadians(minute * 6.0 - 90.0)

        val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeCap = Paint.Cap.ROUND
            setShadowLayer(8f, 0f, 2f, Color.argb(150, 0, 0, 0))
        }
        hand.strokeWidth = maxOf(3f, r * 0.075f)
        canvas.drawLine(
            cx, cy,
            cx + (Math.cos(hourAngle) * r * 0.52).toFloat(),
            cy + (Math.sin(hourAngle) * r * 0.52).toFloat(), hand
        )
        hand.strokeWidth = maxOf(2f, r * 0.048f)
        canvas.drawLine(
            cx, cy,
            cx + (Math.cos(minuteAngle) * r * 0.80).toFloat(),
            cy + (Math.sin(minuteAngle) * r * 0.80).toFloat(), hand
        )
        canvas.drawCircle(cx, cy, r * 0.055f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        })

        if (PreferencesManager.showClockDate) {
            val datePaint = paint(base * 0.30f, light, 195)
            val text = dateText(cal)
            canvas.drawText(
                text, cx - datePaint.measureText(text) / 2f, cy + r + base * 0.28f, datePaint
            )
        }
    }

    /**
     * Severe weather banner, top of screen and full width.
     *
     * Deliberately the loudest element on the wallpaper: if there's a tornado
     * warning out, that matters more than the temperature does.
     */
    private fun drawAlertBanner(canvas: Canvas, alert: NwsAlertsClient.Alert) {
        val h = 92f
        val bg = Paint().apply { color = NwsAlertsClient.colorFor(alert.severity) }
        canvas.drawRect(0f, 0f, W.toFloat(), h, bg)

        // Bright rule along the bottom edge lifts it off the wallpaper.
        canvas.drawRect(0f, h - 4f, W.toFloat(), h, Paint().apply {
            color = Color.argb(190, 255, 255, 255)
        })

        val title = paint(42f, medium)
        canvas.drawText(alert.event.uppercase(), MARGIN, 60f, title)

        val until = NwsAlertsClient.untilLabel(alert.ends)
        if (until.isNotEmpty()) {
            val p = paint(32f, light, 210)
            canvas.drawText(until, W - MARGIN - p.measureText(until), 58f, p)
        }
    }

    // ------------------------------------------------------------------ lines

    /**
     * "4\u00B0 warmer than yesterday", or null when it's within a degree.
     *
     * A one-degree difference isn't worth a line, and reporting it would make
     * the comparison feel like noise rather than information.
     */
    private fun yesterdayPhrase(c: OpenMeteoClient.Conditions): String? {
        val y = c.yesterdayHigh ?: return null
        val delta = (c.high - y).roundToInt()
        return when {
            delta >= 2 -> "$delta\u00B0 warmer than yesterday"
            delta <= -2 -> "${-delta}\u00B0 cooler than yesterday"
            else -> null
        }
    }

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
        if (PreferencesManager.showAirQuality) {
            currentAir?.let { air ->
                parts.add(AirQualityClient.shortLabel(air))
                // Only present where the pollen model has coverage.
                AirQualityClient.pollenLabel(air)?.let { parts.add(it) }
            }
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
        val colWidth = 118f * scale
        val entries = c.hourly.take(6)

        val panel = RectF(
            startX - dy(26f), top - dy(14f),
            startX + colWidth * entries.size + dy(10f), top + dy(128f)
        )
        canvas.drawRoundRect(panel, dy(18f), dy(18f), panelPaint())

        entries.forEachIndexed { i, hour ->
            val cx = startX + colWidth * i + colWidth / 2f - dy(12f)
            canvas.drawText(hour.label, cx - labelHalf(hour.label, sz(28f)), top + dy(22f), paint(sz(28f), light, 175))
            WeatherIcons.draw(canvas, hour.weatherCode, hour.isDay, cx, top + dy(60f), sz(46f))
            val t = "${hour.temperature.roundToInt()}\u00B0"
            canvas.drawText(t, cx - labelHalf(t, sz(36f)), top + dy(108f), paint(sz(36f), light, 235))

            // Only show precipitation chance when it's worth knowing.
            if (hour.precipChance >= 20) {
                val p = "${hour.precipChance}%"
                canvas.drawText(
                    p, cx - labelHalf(p, sz(24f)), top + dy(128f),
                    paint(sz(24f), light, 190).apply { color = Color.parseColor("#8FD3F4") }
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
        val colWidth = 124f * scale
        val entries = c.daily.take(5)

        val panel = RectF(
            startX - dy(26f), top - dy(14f),
            startX + colWidth * entries.size + dy(10f), top + dy(128f)
        )
        if (panel.right > W - 60f) return   // no room; skip rather than overflow
        canvas.drawRoundRect(panel, dy(18f), dy(18f), panelPaint())

        entries.forEachIndexed { i, day ->
            val cx = startX + colWidth * i + colWidth / 2f - dy(12f)
            canvas.drawText(day.label, cx - labelHalf(day.label, sz(28f)), top + dy(22f), paint(sz(28f), light, 175))
            WeatherIcons.draw(canvas, day.weatherCode, true, cx, top + dy(60f), sz(46f))
            val hi = "${day.high.roundToInt()}\u00B0"
            val lo = "${day.low.roundToInt()}\u00B0"
            canvas.drawText(hi, cx - labelHalf(hi, sz(34f)) - dy(22f), top + dy(108f), paint(sz(34f), light, 235))
            canvas.drawText(lo, cx - labelHalf(lo, sz(30f)) + dy(24f), top + dy(108f), paint(sz(30f), light, 150))
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
     * Sparse drifting flecks for the wintery themes. Seeded by the day so they
     * hold still between refreshes rather than jumping every 15 minutes.
     */
    private fun drawFlecks(canvas: Canvas, theme: HolidayThemes.Theme) {
        val rng = kotlin.random.Random(System.currentTimeMillis() / 86_400_000L)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(90) {
            val x = rng.nextFloat() * W
            val y = rng.nextFloat() * H
            val depth = rng.nextFloat()
            paint.color = Color.argb(
                (70 + depth * 90).toInt(),
                Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent)
            )
            canvas.drawCircle(x, y, 1.5f + depth * 4f, paint)
        }
    }

    /**
     * Full-height scrim. Drawn across the whole canvas on purpose: an earlier
     * version stopped the rect at 65% height while its gradient was still
     * faintly opaque there, and the rect's own edge showed as a hard seam.
     */
    private fun drawScrim(canvas: Canvas, strong: Boolean, extraDark: Boolean = false) {
        val boost = if (extraDark) 45 else 0
        val topAlpha = (if (strong) 190 else 140) + boost
        val midAlpha = (if (strong) 95 else 60) + boost
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
                    Color.argb(120 + boost, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), side)
        }
    }
}
