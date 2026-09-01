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
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType

class WallpaperProviderService : Service() {

    companion object {
        private const val PROJECTIVY_PACKAGE = "com.spocky.projengmenu"
        /** Don't hit the API more than once per 10 min even if we're called more often. */
        private const val MIN_FETCH_INTERVAL_MS = 10 * 60 * 1000L
        /** Alerts change faster than conditions, but not that fast. */
        private const val ALERT_INTERVAL_MS = 5 * 60 * 1000L
        /** Frames in an animated radar loop. Seven spans ~2h of observations. */
        private const val RADAR_FRAME_COUNT = 7
        /** World event list is re-fetched no more often than this. */
        private const val WORLD_INTERVAL_MS = 30 * 60 * 1000L
    }

    private var lastFetchAt = 0L
    private var cached: OpenMeteoClient.Conditions? = null
    private var lastAlertAt = 0L
    private var cachedAlert: NwsAlertsClient.Alert? = null
    private var lastWorldAt = 0L
    private var cachedEvents: List<WorldEventsClient.Event> = emptyList()

    override fun onCreate() {
        super.onCreate()
        PreferencesManager.init(this)
    }

    override fun onBind(intent: Intent): IBinder = binder

    private val binder = object : IWallpaperProviderService.Stub() {

        override fun getWallpapers(event: Event?): List<Wallpaper> {
            // Only TIME_ELAPSED is declared in the manifest (updateMode=1), so this is
            // the only branch that should fire. Anything else: leave the wallpaper alone.
            if (event !is Event.TimeElapsed) return emptyList()

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

            // Alerts are drawn by both render paths, so resolve before either.
            WeatherRenderer.currentAlert = currentAlert()

            return try {
                // An animated pack is rendered by the launcher, not by us, so the
                // panel gets embedded into the animation instead of composited.
                animatedWallpaper(conditions)?.let { return listOf(it) }

                // Animated radar: same constraint, solved by embedding the frames.
                animatedRadar(conditions)?.let { return listOf(it) }

                // World weather watch takes over the whole frame when it's this
                // refresh's turn, so it's resolved before the local render.
                worldEventWallpaper(conditions, refreshIndex)?.let { return listOf(it) }

                val file = WeatherRenderer.render(
                    this@WallpaperProviderService,
                    conditions,
                    PreferencesManager.displayLabel
                )

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

        override fun setPreferences(params: String) = PreferencesManager.import(params)
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
                val overlay = WeatherRenderer.renderOverlay(
                    this, c, PreferencesManager.displayLabel
                )
                val composed = LottieComposer.compose(cacheDir, asset, overlay) ?: return null
                wallpaperFor(composed, WallpaperType.LOTTIE, pack, credit)
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
     * Builds the animated radar loop when enabled. Returns null on any failure
     * so the caller falls through to the normal still wallpaper.
     */
    private fun animatedRadar(c: OpenMeteoClient.Conditions): Wallpaper? {
        if (!PreferencesManager.animateRadar) return null
        if (PreferencesManager.backgroundSource != Backgrounds.SOURCE_RADAR) return null

        return try {
            val phase = ThemeResolver.resolve(c)
            val baseMap = Backgrounds.radarBaseMap(this, 1920, 1080, phase) ?: return null
            val scene = WeatherRenderer.composeScene(
                this, c, PreferencesManager.displayLabel, baseMap, WeatherRenderer.currentAlert
            )
            baseMap.recycle()

            // Already-encoded WebP bytes, one frame decoded at a time upstream.
            val frames = Backgrounds.radarFrames(this, 1920, 1080, RADAR_FRAME_COUNT)
            if (frames.isEmpty()) { scene.recycle(); return null }

            val loop = RadarAnimator.build(cacheDir, scene, frames)
            scene.recycle()
            if (loop == null) return null

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", loop)
            grantUriPermission(PROJECTIVY_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            Wallpaper(
                uri = uri.toString(),
                type = WallpaperType.LOTTIE,
                displayMode = WallpaperDisplayMode.CROP,
                title = OpenMeteoClient.describe(c.weatherCode),
                source = "RainViewer",
                author = "RainViewer"
            )
        } catch (t: Throwable) {
            // Throwable deliberately: an OutOfMemoryError here would otherwise
            // escape the binder call and leave the wallpaper blank instead of
            // falling back to the still render.
            Log.w("WeatherWallpaper", "Animated radar failed: ${t.message}")
            null
        }
    }

    /**
     * Sets the location for this refresh.
     *
     * With cycling off, or only one location saved, this is always the primary
     * one. Otherwise it advances through the rotation. The chosen location is
     * published on PreferencesManager so the renderers pick it up without
     * every drawing call taking a location argument.
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
        // The forecast is location-specific, so force a refetch on a change.
        lastFetchAt = 0L
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
                cached = it
                lastFetchAt = now
            }
        }
        // On a failed refresh we fall back to the last good reading rather than
        // blanking the wallpaper.
        return cached
    }
}
