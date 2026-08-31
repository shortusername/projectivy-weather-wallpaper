package tv.projectivy.plugin.wallpaperprovider.weather

import android.app.Service
import android.content.Intent
import android.os.IBinder
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
    }

    private var lastFetchAt = 0L
    private var cached: OpenMeteoClient.Conditions? = null

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

            return try {
                // An animated pack is rendered by the launcher, not by us, so the
                // panel gets embedded into the animation instead of composited.
                animatedWallpaper(conditions)?.let { return listOf(it) }

                val file = WeatherRenderer.render(
                    this@WallpaperProviderService,
                    conditions,
                    PreferencesManager.placeLabel
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
            } catch (e: Exception) {
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

        val asset = PackManager.resolveAsset(this, pack, c) ?: return null
        val credit = "${pack.name} by ${pack.author} · ${pack.license}"

        return when (pack.kind) {
            PackManager.KIND_LOTTIE -> {
                val overlay = WeatherRenderer.renderOverlay(
                    this, c, PreferencesManager.placeLabel
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

    private fun currentConditions(): OpenMeteoClient.Conditions? {
        bootstrapLocationIfNeeded()
        val now = System.currentTimeMillis()
        val stale = now - lastFetchAt > MIN_FETCH_INTERVAL_MS
        if (stale || cached == null) {
            OpenMeteoClient.fetch(
                PreferencesManager.latitude,
                PreferencesManager.longitude,
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
