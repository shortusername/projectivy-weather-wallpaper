package tv.projectivy.plugin.wallpaperprovider.weather

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.FileProvider
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperProviderContract
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType

class WallpaperProviderService : Service() {

    companion object {
        private const val PROJECTIVY_PACKAGE = "com.spocky.projengmenu"
        /** Don't hit the API more than once per 10 min even if we're called more often. */
        private const val MIN_FETCH_INTERVAL_MS = 10 * 60 * 1000L
        /** Alerts change faster than conditions, but not that fast. */
        private const val ALERT_INTERVAL_MS = 5 * 60 * 1000L
        /** Kp updates every few hours; half an hour is ample. */
        private const val AURORA_INTERVAL_MS = 30 * 60 * 1000L
        /** Air quality moves slowly; hourly is plenty. */
        private const val AIR_INTERVAL_MS = 60 * 60 * 1000L
        /** Frames in an animated radar loop. Seven spans ~2h of observations. */
        private const val RADAR_FRAME_COUNT = 7
        /** World event list is re-fetched no more often than this. */
        private const val WORLD_INTERVAL_MS = 30 * 60 * 1000L
    }

    private var lastFetchAt = 0L
    private var cached: OpenMeteoClient.Conditions? = null
    private var lastAlertAt = 0L
    private var cachedAlert: NwsAlertsClient.Alert? = null
    /**
     * The last still render, reused while nothing that affects it has changed.
     *
     * Output filenames carry a timestamp so the launcher's image cache can't
     * serve a stale bitmap. But Projectivy re-requests on its own rotation
     * interval, so a fresh filename every request meant a visible reload every
     * few seconds. Returning the same URI when the content is identical keeps
     * both properties: no stale images, no needless reloads.
     */
    private var lastRenderKey: String? = null
    private var lastRenderFile: java.io.File? = null

    private var lastAirAt = 0L
    private var cachedAir: AirQualityClient.Reading? = null
    private var lastAuroraAt = 0L
    private var cachedAurora: AuroraClient.Conditions? = null
    private var yesterdayHigh: Double? = null
    private var yesterdayFetchedForDay = -1
    private var lastWorldAt = 0L
    private var cachedEvents: List<WorldEventsClient.Event> = emptyList()

    /**
     * Drives the per-minute refresh needed by the clock.
     *
     * itemsCacheDurationMillis is manifest metadata, fixed at build time, so
     * the plugin can't ask the launcher to poll faster. Instead it broadcasts
     * the same update the settings screen sends.
     *
     * Only while the service is bound, and only when the clock is on. The
     * background is cached for nine minutes, so a minute-by-minute redraw
     * re-encodes a bitmap rather than refetching radar tiles.
     */
    private val clockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            if (!PreferencesManager.showClock) return

            // Don't poke the launcher while it's idle. Each self-update makes
            // Projectivy re-request and redraw, which its own idle detection
            // may read as activity — that would stop the screensaver from ever
            // starting. Nobody is reading the clock at that point anyway.
            if (PreferencesManager.launcherIdle) {
                // Keep checking, cheaply, so ticking resumes on wake.
                scheduleNextTick()
                return
            }

            requestSelfUpdate()
            scheduleNextTick()
        }
    }

    override fun onCreate() {
        super.onCreate()
        PreferencesManager.init(this)
        if (PreferencesManager.showClock) scheduleNextTick()
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockTick)
        Backgrounds.clearBackgroundCache()
        super.onDestroy()
    }

    /** Aligns to the next whole minute, so the clock changes when it should. */
    private fun scheduleNextTick() {
        val now = System.currentTimeMillis()
        val delay = 60_000L - (now % 60_000L) + 250L
        clockHandler.removeCallbacks(clockTick)
        clockHandler.postDelayed(clockTick, delay)
    }

    private fun requestSelfUpdate() {
        try {
            sendBroadcast(
                Intent(WallpaperProviderContract.ACTION_WALLPAPER_PROVIDER_UPDATED).apply {
                    `package` = PROJECTIVY_PACKAGE
                    putExtra(
                        WallpaperProviderContract.EXTRA_PROVIDER_ID,
                        getString(R.string.plugin_uuid)
                    )
                    putExtra(
                        WallpaperProviderContract.EXTRA_UPDATE_REASON,
                        WallpaperProviderContract.UpdateReason.DATA_CHANGED
                    )
                }
            )
        } catch (t: Throwable) {
            Log.w("WeatherWallpaper", "Self update failed: ${t.message}")
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    private val binder = object : IWallpaperProviderService.Stub() {

        override fun getWallpapers(event: Event?): List<Wallpaper> {
            // Only TIME_ELAPSED is declared in the manifest (updateMode=1), so this is
            // the only branch that should fire. Anything else: leave the wallpaper alone.
            // Idle changes tell us whether anything is covering the wallpaper.
            // Recorded before the early return, since an idle event should
            // update the layout even though it isn't a time tick.
            if (event is Event.LauncherIdleModeChanged) {
                PreferencesManager.launcherIdle = event.isIdle

                // Return immediately when going idle, with no render and no
                // network. The launcher is about to hand over to a screensaver
                // and blocking this binder call could delay or prevent that.
                // The layout change takes effect on the next ordinary refresh.
                if (event.isIdle) return emptyList()
            }

            if (event !is Event.TimeElapsed && event !is Event.LauncherIdleModeChanged) {
                return emptyList()
            }

            val conditions = currentConditions() ?: return emptyList()

            // One counter for the whole refresh, incremented here rather than
            // inside either feature: previously the world-watch path owned it
            // and returned early when disabled, so it never advanced and
            // location cycling lost its sense of time.
            val refreshIndex = PreferencesManager.refreshCount + 1
            PreferencesManager.refreshCount = refreshIndex

            // Pick which saved location this refresh renders, before anything
            // fetches or draws.
            selectActiveLocation(refreshIndex)

            // Scheduled update check. Fires on a worker thread and only records
            // what it found, so it never delays this call.
            if (UpdateChecker.isCheckDue()) {
                UpdateChecker.checkInBackground(BuildConfig.VERSION_NAME)
            }

            // Alerts and aurora are drawn by both render paths, so resolve
            // before either.
            WeatherRenderer.currentAlert = currentAlert()
            WeatherRenderer.currentAurora = currentAurora()
            WeatherRenderer.currentAir = currentAir()

            return try {
                // Diagnostic mode overrides everything, so the result is
                // unambiguous.
                selfTestWallpaper()?.let { return listOf(it) }

                // An animated pack is rendered by the launcher, not by us, so the
                // panel gets embedded into the animation instead of composited.
                animatedWallpaper(conditions)?.let { return listOf(it) }

                // Animated radar: same constraint, solved by embedding the frames.
                animatedRadar(conditions)?.let { return listOf(it) }

                // World weather watch takes over the whole frame when it's this
                // refresh's turn, so it's resolved before the local render.
                worldEventWallpaper(conditions, refreshIndex)?.let { return listOf(it) }

                // A video from the user's own folder, if one matches.
                localVideoWallpaper(conditions)?.let { return listOf(it) }

                // Animated precipitation: vector particles over a still scene.
                animatedPrecipitation(conditions)?.let { return listOf(it) }

                val file = renderCached(conditions)

                val uri = FileProvider.getUriForFile(
                    this@WallpaperProviderService,
                    "$packageName.fileprovider",
                    file
                )

                // The launcher is a separate process reading our content:// URI, so it
                // needs an explicit grant. This is why <queries> lists the package.
                grantUriPermission(PROJECTIVY_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

                listOf(
                    Wallpaper(
                        uri = uri.toString(),
                        type = WallpaperType.IMAGE,
                        displayMode = WallpaperDisplayMode.CROP,
                        title = OpenMeteoClient.describe(conditions.weatherCode),
                        source = "https://open-meteo.com/",
                        author = "Open-Meteo"
                    )
                )
            } catch (t: Throwable) {
                Log.w("WeatherWallpaper", "getWallpapers failed: ${t.message}")
                emptyList()
            }
        }

        override fun getPreferences(): String = PreferencesManager.export()

        override fun setPreferences(params: String) {
            PreferencesManager.import(params)
            // The clock may have just been switched on or off.
            if (PreferencesManager.showClock) scheduleNextTick()
            else clockHandler.removeCallbacks(clockTick)
        }
    }

    /**
     * First run only: derive an approximate location from the public IP so the
     * plugin works the moment it's selected, with no trip into settings.
     * Runs on the binder thread, which is off the UI thread by contract.
     */
    private fun bootstrapLocationIfNeeded() {
        if (PreferencesManager.locationConfigured) return

        IpLocationClient.lookup()?.let { loc ->
            PreferencesManager.latitude = loc.latitude
            PreferencesManager.longitude = loc.longitude
            PreferencesManager.placeLabel = loc.label
            PreferencesManager.locationConfigured = true
        }
        // On failure we leave the flag unset and retry next time, falling back
        // to the built-in default coordinates in the meantime.
    }

    /**
     * Builds a LOTTIE or VIDEO wallpaper when the selected pack is animated.
     * Returns null for static packs and on any failure, so the caller falls
     * through to the normal composited PNG.
     */
    private fun animatedWallpaper(c: OpenMeteoClient.Conditions): Wallpaper? {
        if (PreferencesManager.backgroundSource != Backgrounds.SOURCE_PACK) return null
        val id = PreferencesManager.selectedPack
        if (id.isBlank()) return null

        val pack = PackManager.findPack(this, id) ?: return null
        if (pack.kind == PackManager.KIND_STATIC) return null

        val phase = ThemeResolver.resolve(c)
        val asset = PackManager.resolveAsset(this, pack, c, phase) ?: return null
        val credit = "${pack.name} by ${pack.author} · ${pack.license}"

        return when (pack.kind) {
            PackManager.KIND_LOTTIE -> {
                // Served exactly as the contributor made it. The panel is no
                // longer injected: testing showed shape layers animate
                // correctly but embedded images never display, and the panel
                // was an embedded image. So an animated pack behaves like a
                // video pack — motion, no weather readout.
                wallpaperFor(asset, WallpaperType.LOTTIE, pack, credit)
            }
            PackManager.KIND_VIDEO -> {
                // Video can't carry the panel — the launcher decodes it directly.
                // Users who pick a video pack are trading the readout for motion.
                wallpaperFor(asset, WallpaperType.VIDEO, pack, credit)
            }
            else -> null
        }
    }

    private fun wallpaperFor(
        file: java.io.File,
        type: Int,
        pack: PackManager.Pack,
        credit: String
    ): Wallpaper? = try {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        grantUriPermission(PROJECTIVY_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Wallpaper(
            uri = uri.toString(),
            type = type,
            displayMode = WallpaperDisplayMode.CROP,
            title = pack.name,
            source = credit,
            author = pack.author
        )
    } catch (e: Exception) {
        null
    }

    /**
     * Renders the still wallpaper, or reuses the previous file unchanged.
     *
     * The key covers everything that alters the output. The clock contributes
     * the current minute, which is what makes it tick; without the clock the
     * key only moves when the weather or a setting does.
     */
    private fun renderCached(c: OpenMeteoClient.Conditions): java.io.File {
        val minute = if (PreferencesManager.showClock)
            System.currentTimeMillis() / 60_000L else 0L

        val key = listOf(
            c.temperature, c.weatherCode, c.apparentTemperature, c.high, c.low,
            c.windSpeed, c.windDirection, c.humidity,
            c.nowcast?.startsInMinutes, c.nowcast?.stopsInMinutes, c.yesterdayHigh,
            PreferencesManager.displayLabel,
            PreferencesManager.backgroundSource,
            ThemeResolver.resolve(c).name,
            HolidayThemes.current()?.id,
            PreferencesManager.panelScale,
            PreferencesManager.currentLatitude, PreferencesManager.currentLongitude,
            PreferencesManager.showHourly, PreferencesManager.showDaily,
            PreferencesManager.showStats, PreferencesManager.showSun,
            PreferencesManager.showNowcast, PreferencesManager.showAdvisories,
            PreferencesManager.showAirQuality, PreferencesManager.showAurora,
            PreferencesManager.showYesterday, PreferencesManager.showClock,
            PreferencesManager.clockPosition, PreferencesManager.clockSize,
            PreferencesManager.clockStyle, PreferencesManager.clockHours,
            PreferencesManager.showClockDate,
            PreferencesManager.safeBottomPercent, PreferencesManager.launcherIdle,
            PreferencesManager.idleFullFrame, PreferencesManager.labelDensity,
            PreferencesManager.safeRadarPalette, PreferencesManager.radarZoom,
            WeatherRenderer.currentAlert?.event,
            WeatherRenderer.currentAurora?.kp,
            WeatherRenderer.currentAir?.aqi,
            Advisories.top(c)?.text,
            UpdateChecker.pendingVersion(BuildConfig.VERSION_NAME),
            minute
        ).joinToString("|")

        val cached = lastRenderFile
        if (key == lastRenderKey && cached != null && cached.exists() && cached.length() > 0) {
            return cached
        }

        val file = WeatherRenderer.render(
            this, c, PreferencesManager.displayLabel
        )
        lastRenderKey = key
        lastRenderFile = file
        return file
    }

    /**
     * The Lottie diagnostic, when switched on.
     *
     * Served exactly like any other animation — same content:// URI, same
     * permission grant — so the only variable is the file's contents.
     */
    private fun selfTestWallpaper(): Wallpaper? {
        val mode = PreferencesManager.lottieSelfTest
        if (mode == LottieSelfTest.OFF) return null
        return try {
            val file = LottieSelfTest.build(cacheDir, mode) ?: return null
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            grantUriPermission(PROJECTIVY_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Wallpaper(
                uri = uri.toString(),
                type = WallpaperType.LOTTIE,
                displayMode = WallpaperDisplayMode.CROP,
                title = "Lottie self-test $mode",
                source = "diagnostic",
                author = ""
            )
        } catch (t: Throwable) {
            Log.w("WeatherWallpaper", "Self-test failed: ${t.message}")
            null
        }
    }

    /**
     * Animated radar, encoded as an MP4 on the device.
     *
     * Lottie was the original approach and cannot work: testing showed the
     * launcher renders Lottie shape layers correctly but doesn't display
     * embedded image assets at all, and radar is raster by nature. Video takes
     * a different path entirely — the launcher hands the URI to a media player,
     * which reads content:// natively, as the existing video packs demonstrate.
     *
     * Everything survives this way: radar, the vector map, the weather panel,
     * the alert banner, all burned into the frames.
     *
     * Frames are composed and encoded one at a time, so memory stays flat
     * regardless of how many there are.
     */
    private fun animatedRadar(c: OpenMeteoClient.Conditions): Wallpaper? {
        if (!PreferencesManager.animateRadar) return null
        if (PreferencesManager.backgroundSource != Backgrounds.SOURCE_RADAR) return null

        var baseMap: android.graphics.Bitmap? = null
        var scene: android.graphics.Bitmap? = null
        return try {
            val phase = ThemeResolver.resolve(c)
            val (host, paths) = Backgrounds.radarFramePaths(RADAR_FRAME_COUNT) ?: return null

            // The still scene, drawn once: map, panel, banner, everything but
            // the precipitation.
            baseMap = Backgrounds.radarBaseMap(this, 1920, 1080, phase) ?: return null
            scene = WeatherRenderer.composeScene(
                this, c, PreferencesManager.displayLabel, baseMap, WeatherRenderer.currentAlert
            )
            baseMap.recycle(); baseMap = null

            val sceneScaled = android.graphics.Bitmap.createScaledBitmap(
                scene, VideoEncoder.WIDTH, VideoEncoder.HEIGHT, true
            )
            scene.recycle(); scene = null

            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            val video = VideoEncoder.encode(cacheDir, paths.size, holdFrames = 5) { index, target ->
                val canvas = android.graphics.Canvas(target)
                canvas.drawBitmap(sceneScaled, 0f, 0f, null)
                val layer = Backgrounds.radarFrameAt(
                    this, VideoEncoder.WIDTH, VideoEncoder.HEIGHT, host, paths[index]
                )
                if (layer != null) {
                    canvas.drawBitmap(layer, 0f, 0f, paint)
                    layer.recycle()
                }
                // A frame with no precipitation is still a valid frame.
                true
            }
            sceneScaled.recycle()
            if (video == null) return null

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", video)
            grantUriPermission(PROJECTIVY_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            Wallpaper(
                uri = uri.toString(),
                type = WallpaperType.VIDEO,
                displayMode = WallpaperDisplayMode.CROP,
                title = OpenMeteoClient.describe(c.weatherCode),
                source = "RainViewer",
                author = "RainViewer"
            )
        } catch (t: Throwable) {
            Log.w("WeatherWallpaper", "Animated radar failed: ${t.message}")
            null
        } finally {
            baseMap?.recycle()
            scene?.recycle()
        }
    }

    /**
     * Sets the location for this refresh.
     *
     * With cycling off, or only one location saved, this is always the primary
     * one. Otherwise it advances through the rotation. The chosen location is
     * published on PreferencesManager so the renderers pick it up without every
     * drawing call taking a location argument.
     */
    private fun selectActiveLocation(refreshIndex: Int) {
        val rotation = PreferencesManager.locationRotation
        val mode = PreferencesManager.cycleMode

        if (mode == PreferencesManager.CYCLE_OFF || rotation.size < 2) {
            PreferencesManager.activeLatitude = null
            PreferencesManager.activeLongitude = null
            PreferencesManager.activeLabel = null
            return
        }

        // Alternate mode holds each location for two refreshes, so a 15-minute
        // cycle doesn't move on before anyone has looked at it.
        val step = if (mode == PreferencesManager.CYCLE_ALTERNATE) 2 else 1
        if (refreshIndex % step != 0) return

        val cursor = PreferencesManager.locationCursor % rotation.size
        PreferencesManager.locationCursor = (cursor + 1) % rotation.size

        val chosen = rotation[cursor]
        PreferencesManager.activeLatitude = chosen.latitude
        PreferencesManager.activeLongitude = chosen.longitude
        PreferencesManager.activeLabel = chosen.label

        // All three are location-specific, so a change must invalidate them.
        lastFetchAt = 0L
        yesterdayFetchedForDay = -1
        lastAirAt = 0L
    }

    /**
     * A notable weather event elsewhere, on its turn in the rotation.
     *
     * Returns null whenever it isn't this refresh's turn, the feed is empty, or
     * anything fails — the caller then renders local weather as normal. GDACS
     * often lists only a handful of current weather events, so an empty feed is
     * the expected case rather than an error.
     */
    private fun worldEventWallpaper(
        c: OpenMeteoClient.Conditions,
        refreshIndex: Int
    ): Wallpaper? {
        val mode = PreferencesManager.worldWatch
        if (mode == PreferencesManager.WORLD_OFF) return null

        val everyN = if (mode == PreferencesManager.WORLD_FREQUENT) 2 else 4
        if (refreshIndex % everyN != 0) return null

        return try {
            val now = System.currentTimeMillis()
            if (cachedEvents.isEmpty() || now - lastWorldAt > WORLD_INTERVAL_MS) {
                cachedEvents = WorldEventsClient.fetch()
                lastWorldAt = now
            }
            if (cachedEvents.isEmpty()) return null

            // Advance the cursor so successive turns show different events.
            val cursor = PreferencesManager.worldCursor % cachedEvents.size
            PreferencesManager.worldCursor = (cursor + 1) % cachedEvents.size
            val event = cachedEvents[cursor]

            // Conditions at the event, not at home.
            val eventConditions = OpenMeteoClient.fetch(
                event.latitude, event.longitude, PreferencesManager.useMetric
            )

            val phase = ThemeResolver.resolve(eventConditions ?: c)
            val file = WeatherRenderer.renderWorldEvent(
                this, event, eventConditions, phase
            )

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            grantUriPermission(PROJECTIVY_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            Wallpaper(
                uri = uri.toString(),
                type = WallpaperType.IMAGE,
                displayMode = WallpaperDisplayMode.CROP,
                title = "${event.name} \u00B7 ${event.countries}",
                source = "GDACS",
                author = "GDACS (UN/EC)"
            )
        } catch (t: Throwable) {
            Log.w("WeatherWallpaper", "World event render failed: ${t.message}")
            null
        }
    }

    /**
     * A video from the local folder, when that's the chosen background.
     *
     * The launcher plays the file directly, so nothing can be drawn over it —
     * no weather panel, no alert banner. That's inherent to handing over a
     * single URI, and the settings screen says so when the folder is selected.
     */
    private fun localVideoWallpaper(c: OpenMeteoClient.Conditions): Wallpaper? {
        if (PreferencesManager.backgroundSource != Backgrounds.SOURCE_LOCAL) return null
        return try {
            val phase = ThemeResolver.resolve(c)
            val video = Backgrounds.localVideo(this, c, phase) ?: return null
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", video)
            grantUriPermission(PROJECTIVY_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Wallpaper(
                uri = uri.toString(),
                type = WallpaperType.VIDEO,
                displayMode = WallpaperDisplayMode.CROP,
                title = video.nameWithoutExtension,
                source = "Local folder",
                author = ""
            )
        } catch (t: Throwable) {
            Log.w("WeatherWallpaper", "Local video failed: ${t.message}")
            null
        }
    }

    /**
     * Animated precipitation, encoded as video.
     *
     * Was Lottie: vector particles over the scene. The particles themselves
     * would have worked — shape layers render fine — but the scene behind them
     * was an embedded image, and those don't display. Drawing the particles
     * onto real frames and encoding avoids the problem entirely, and keeps the
     * panel and the background.
     *
     * Returns null for clear and cloudy conditions, so the wallpaper falls
     * through to the ordinary still render rather than animating nothing.
     */
    private fun animatedPrecipitation(c: OpenMeteoClient.Conditions): Wallpaper? {
        if (!PreferencesManager.animatePrecipitation) return null
        if (!PrecipitationFrames.isAnimatable(c.weatherCode)) return null
        // The radar path already owns the frame when it's active.
        if (PreferencesManager.animateRadar &&
            PreferencesManager.backgroundSource == Backgrounds.SOURCE_RADAR
        ) return null

        var scene: android.graphics.Bitmap? = null
        var sceneScaled: android.graphics.Bitmap? = null
        return try {
            val phase = ThemeResolver.resolve(c)
            scene = WeatherRenderer.composeScene(
                this, c, PreferencesManager.displayLabel,
                backgroundFor(c, phase), WeatherRenderer.currentAlert
            )
            sceneScaled = android.graphics.Bitmap.createScaledBitmap(
                scene, VideoEncoder.WIDTH, VideoEncoder.HEIGHT, true
            )
            scene.recycle(); scene = null

            val frames = PrecipitationFrames.frameCount(c.weatherCode)
            val backdrop = sceneScaled
            val video = VideoEncoder.encode(cacheDir, frames, holdFrames = 1) { index, target ->
                val canvas = android.graphics.Canvas(target)
                canvas.drawBitmap(backdrop, 0f, 0f, null)
                PrecipitationFrames.draw(
                    canvas, VideoEncoder.WIDTH, VideoEncoder.HEIGHT,
                    c.weatherCode, phase.isDay,
                    progress = index.toFloat() / frames
                )
                true
            }
            sceneScaled.recycle(); sceneScaled = null
            if (video == null) return null

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", video)
            grantUriPermission(PROJECTIVY_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            Wallpaper(
                uri = uri.toString(),
                type = WallpaperType.VIDEO,
                displayMode = WallpaperDisplayMode.CROP,
                title = OpenMeteoClient.describe(c.weatherCode),
                source = "Open-Meteo",
                author = "Open-Meteo"
            )
        } catch (t: Throwable) {
            Log.w("WeatherWallpaper", "Animated precipitation failed: ${t.message}")
            null
        } finally {
            scene?.recycle()
            sceneScaled?.recycle()
        }
    }

    /**
     * The background bitmap for the current source, or null for the drawn
     * scenes which composeScene renders itself.
     */
    private fun backgroundFor(
        c: OpenMeteoClient.Conditions,
        phase: ThemeResolver.Phase
    ): android.graphics.Bitmap? {
        val source = PreferencesManager.backgroundSource
        if (source == Backgrounds.SOURCE_SCENE || source == Backgrounds.SOURCE_GRADIENT) {
            return null
        }
        return Backgrounds.resolve(this, source, c, 1920, 1080, phase)?.first
    }

    /** Air quality, on an hourly cadence and keyed to the active location. */
    private fun currentAir(): AirQualityClient.Reading? {
        if (!PreferencesManager.showAirQuality) return null
        val now = System.currentTimeMillis()
        if (now - lastAirAt > AIR_INTERVAL_MS) {
            cachedAir = AirQualityClient.fetch(
                PreferencesManager.currentLatitude, PreferencesManager.currentLongitude
            )
            lastAirAt = now
        }
        return cachedAir
    }

    /** K-index conditions, refreshed on its own slower cadence. */
    private fun currentAurora(): AuroraClient.Conditions? {
        if (!PreferencesManager.showAurora) return null
        val now = System.currentTimeMillis()
        if (now - lastAuroraAt > AURORA_INTERVAL_MS) {
            cachedAurora = AuroraClient.fetch(PreferencesManager.currentLatitude)
            lastAuroraAt = now
        }
        return cachedAurora
    }

    /**
     * Yesterday's high, fetched once a day.
     *
     * It cannot change, so it's keyed on the day of the year rather than a
     * duration — that also makes it refetch correctly across midnight and when
     * the active location changes.
     */
    private fun yesterdayHighFor(lat: Double, lon: Double): Double? {
        if (!PreferencesManager.showYesterday) return null
        val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        if (day != yesterdayFetchedForDay) {
            yesterdayHigh = OpenMeteoClient.fetchYesterdayHigh(
                lat, lon, PreferencesManager.useMetric
            )
            yesterdayFetchedForDay = day
        }
        return yesterdayHigh
    }

    /** Most severe active alert, refreshed independently of conditions. */
    private fun currentAlert(): NwsAlertsClient.Alert? {
        if (!PreferencesManager.showAlerts) return null
        val now = System.currentTimeMillis()
        if (now - lastAlertAt > ALERT_INTERVAL_MS) {
            cachedAlert = NwsAlertsClient.fetch(
                PreferencesManager.currentLatitude, PreferencesManager.currentLongitude
            ).firstOrNull()
            lastAlertAt = now
        }
        return cachedAlert
    }

    private fun currentConditions(): OpenMeteoClient.Conditions? {
        bootstrapLocationIfNeeded()
        val now = System.currentTimeMillis()
        val stale = now - lastFetchAt > MIN_FETCH_INTERVAL_MS
        if (stale || cached == null) {
            OpenMeteoClient.fetch(
                PreferencesManager.currentLatitude,
                PreferencesManager.currentLongitude,
                PreferencesManager.useMetric
            )?.let {
                cached = it.copy(
                    yesterdayHigh = yesterdayHighFor(
                        PreferencesManager.currentLatitude,
                        PreferencesManager.currentLongitude
                    )
                )
                lastFetchAt = now
            }
        }
        // On a failed refresh we fall back to the last good reading rather than
        // blanking the wallpaper.
        return cached
    }
}
