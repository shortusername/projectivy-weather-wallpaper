package tv.projectivy.plugin.wallpaperprovider.weather

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONObject

/**
 * Uses the default SharedPreferences file, same as the upstream template, so
 * Projectivy's settings backup/restore round-trips through export()/import().
 *
 * The AIDL contract exchanges preferences as a plain String; JSON keeps it
 * readable and lets you seed values over adb (see SettingsActivity).
 */
object PreferencesManager {

    private const val TAG = "WeatherPrefs"

    const val KEY_LAT = "latitude"
    const val KEY_LON = "longitude"
    const val KEY_PLACE = "placeLabel"
    const val KEY_METRIC = "useMetric"
    const val KEY_CONFIGURED = "locationConfigured"
    const val KEY_BACKGROUND = "backgroundSource"
    const val KEY_UNSPLASH = "unsplashKey"
    const val KEY_RADAR_ZOOM = "radarZoom"
    const val KEY_SHOW_HOURLY = "showHourly"
    const val KEY_SHOW_DAILY = "showDaily"
    const val KEY_SHOW_STATS = "showStats"
    const val KEY_SHOW_SUN = "showSun"
    const val KEY_SELECTED_PACK = "selectedPack"
    const val KEY_DEMO_MODE = "demoMode"
    const val KEY_DEMO_LABEL = "demoLabel"
    const val KEY_LABEL_DENSITY = "labelDensity"
    const val KEY_SHOW_ALERTS = "showAlerts"
    const val KEY_ANIMATE_RADAR = "animateRadar"
    const val KEY_THEME_MODE = "themeMode"
    const val KEY_WORLD_WATCH = "worldWatch"
    const val KEY_WORLD_CURSOR = "worldCursor"
    const val KEY_REFRESH_COUNT = "refreshCount"
    const val KEY_LOCATIONS = "savedLocations"
    const val KEY_LOCATION_CURSOR = "locationCursor"
    const val KEY_CYCLE_MODE = "cycleMode"
    const val KEY_HOLIDAY = "holidayThemes"
    const val KEY_FORCE_DATE = "forceDate"
    const val KEY_PANEL_SCALE = "panelScale"
    const val KEY_ANIMATE_PRECIP = "animatePrecipitation"
    const val KEY_EXPERIMENTAL = "experimentalFeatures"
    const val KEY_NOWCAST = "showNowcast"
    const val KEY_AURORA = "showAurora"
    const val KEY_MARINE = "showMarine"
    const val KEY_YESTERDAY = "showYesterday"
    const val KEY_SAFE_RADAR = "safeRadarPalette"
    const val KEY_AIR_QUALITY = "showAirQuality"
    const val KEY_ADVISORIES = "showAdvisories"
    const val KEY_CLOCK = "showClock"
    const val KEY_CLOCK_DATE = "showClockDate"
    const val KEY_CLOCK_POSITION = "clockPosition"
    const val KEY_CLOCK_SIZE = "clockSize"
    const val KEY_CLOCK_STYLE = "clockStyle"
    const val KEY_CLOCK_HOURS = "clockHours"
    const val KEY_SAFE_BOTTOM = "safeBottomPercent"
    const val KEY_IDLE_FULL = "idleFullFrame"
    const val KEY_UPDATE_INTERVAL = "updateCheckInterval"
    const val KEY_UPDATE_LAST_AT = "updateLastCheckedAt"
    const val KEY_UPDATE_FOUND = "updateVersionFound"
    const val KEY_REDUCE_BURN_IN = "reduceBurnIn"
    const val KEY_BASEMAP_URL = "basemapUrl"
    const val KEY_BASEMAP_ATTRIBUTION = "basemapAttribution"

    // New York City, so a fresh install shows something rather than nothing.
    private const val DEFAULT_LAT = 40.7128
    private const val DEFAULT_LON = -74.0060

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        }
    }

    var latitude: Double
        get() = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: DEFAULT_LAT
        set(v) = prefs.edit().putString(KEY_LAT, v.toString()).putBoolean(KEY_CONFIGURED, true).apply()

    var longitude: Double
        get() = prefs.getString(KEY_LON, null)?.toDoubleOrNull() ?: DEFAULT_LON
        set(v) = prefs.edit().putString(KEY_LON, v.toString()).apply()

    var placeLabel: String
        get() = prefs.getString(KEY_PLACE, null) ?: "Local weather"
        set(v) = prefs.edit().putString(KEY_PLACE, v).apply()

    var useMetric: Boolean
        get() = prefs.getBoolean(KEY_METRIC, false)
        set(v) = prefs.edit().putBoolean(KEY_METRIC, v).apply()

    /**
     * True once a location has been established, either by the user in settings
     * or by the one-time IP lookup. Gates the auto-locate attempt so we don't
     * re-query on every service start.
     */
    /** One of Backgrounds.SOURCE_* — gradient, local, stock or radar. */
    var backgroundSource: String
        get() = prefs.getString(KEY_BACKGROUND, null) ?: Backgrounds.SOURCE_SCENE
        set(v) = prefs.edit().putString(KEY_BACKGROUND, v).apply()

    /** Unsplash Access Key. Empty means the stock source is unavailable. */
    var unsplashKey: String
        get() = prefs.getString(KEY_UNSPLASH, null) ?: ""
        set(v) = prefs.edit().putString(KEY_UNSPLASH, v).apply()

    /**
     * Radar zoom, 4 (regional) to 7 (metro area). RainViewer's public tiles
     * don't go beyond 7; higher values return a placeholder image.
     */
    var radarZoom: Int
        get() = (prefs.getString(KEY_RADAR_ZOOM, null)?.toIntOrNull() ?: 6).coerceIn(4, 7)
        set(v) = prefs.edit().putString(KEY_RADAR_ZOOM, v.toString()).apply()

    // Optional panels. Hourly defaults on; the rest stay off so the panel starts
    // sparse and the user opts into density.
    var showHourly: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HOURLY, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_HOURLY, v).apply()

    var showDaily: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DAILY, false)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_DAILY, v).apply()

    var showStats: Boolean
        get() = prefs.getBoolean(KEY_SHOW_STATS, false)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_STATS, v).apply()

    var showSun: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SUN, false)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_SUN, v).apply()

    /** Pack id from packs/index.json. Empty means none selected. */
    var selectedPack: String
        get() = prefs.getString(KEY_SELECTED_PACK, null) ?: ""
        set(v) = prefs.edit().putString(KEY_SELECTED_PACK, v).apply()

    /**
     * Readout size as a percentage, 80-120.
     *
     * Clamped at both ends deliberately. Below 80 the temperature stops being
     * readable across a room, which is the point of the panel. Above 100, with
     * both extra stat lines enabled, the hourly and daily strips no longer fit
     * above the launcher's app shelf and are dropped rather than overlapped —
     * so 120 is the ceiling, and the setting says as much.
     */
    var panelScale: Int
        get() = prefs.getInt(KEY_PANEL_SCALE, 100).coerceIn(80, 120)
        set(v) = prefs.edit().putInt(KEY_PANEL_SCALE, v.coerceIn(80, 120)).apply()

    /** Scale as a multiplier, for the renderer. */
    val panelScaleFactor: Float get() = panelScale / 100f

    /** Holiday colour grading. Off by default: it runs on someone's television. */
    var holidayThemes: Boolean
        get() = prefs.getBoolean(KEY_HOLIDAY, false)
        set(v) = prefs.edit().putBoolean(KEY_HOLIDAY, v).apply()

    /** "MM-DD" test override for date-gated themes. Empty means use today. */
    var forceDate: String
        get() = prefs.getString(KEY_FORCE_DATE, null) ?: ""
        set(v) = prefs.edit().putString(KEY_FORCE_DATE, v).apply()

    const val CYCLE_OFF = "off"
    const val CYCLE_EVERY = "every"
    const val CYCLE_ALTERNATE = "alternate"

    /** One saved place. Label is what the wallpaper prints. */
    data class SavedLocation(val label: String, val latitude: Double, val longitude: Double)

    /**
     * Extra locations to cycle through, beyond the primary one.
     *
     * Stored as JSON rather than as individual keys so the list can grow
     * without a migration, and so it round-trips through the settings export
     * the AIDL contract expects.
     */
    var savedLocations: List<SavedLocation>
        get() = try {
            val raw = prefs.getString(KEY_LOCATIONS, null) ?: return emptyList()
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val lat = o.optDouble("lat", Double.NaN)
                val lon = o.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) null
                else SavedLocation(o.optString("label"), lat, lon)
            }
        } catch (_: Exception) {
            emptyList()
        }
        set(v) {
            val arr = org.json.JSONArray()
            v.forEach {
                arr.put(org.json.JSONObject().apply {
                    put("label", it.label); put("lat", it.latitude); put("lon", it.longitude)
                })
            }
            prefs.edit().putString(KEY_LOCATIONS, arr.toString()).apply()
        }

    /** How often to move to the next location in the rotation. */
    var cycleMode: String
        get() = prefs.getString(KEY_CYCLE_MODE, null) ?: CYCLE_OFF
        set(v) = prefs.edit().putString(KEY_CYCLE_MODE, v).apply()

    var locationCursor: Int
        get() = prefs.getInt(KEY_LOCATION_CURSOR, 0)
        set(v) = prefs.edit().putInt(KEY_LOCATION_CURSOR, v).apply()

    /**
     * The full rotation: the primary location first, then the saved extras.
     * Always at least one entry, so callers never have to special-case empty.
     */
    val locationRotation: List<SavedLocation>
        get() = listOf(SavedLocation(placeLabel, latitude, longitude)) + savedLocations

    /**
     * The location currently being rendered.
     *
     * Set by the service at the start of each refresh and read by the renderers,
     * rather than threading a location through every drawing signature. Safe
     * because a refresh renders one frame on one thread; the defaults are the
     * primary location, so nothing breaks if it is never set.
     */
    @Volatile var activeLatitude: Double? = null
    @Volatile var activeLongitude: Double? = null
    @Volatile var activeLabel: String? = null

    /** Coordinates the renderers should use right now. */
    val currentLatitude: Double get() = activeLatitude ?: latitude
    val currentLongitude: Double get() = activeLongitude ?: longitude

    const val WORLD_OFF = "off"
    const val WORLD_OCCASIONAL = "occasional"
    const val WORLD_FREQUENT = "frequent"

    /** How often the wallpaper shows a notable weather event from elsewhere. */
    var worldWatch: String
        get() = prefs.getString(KEY_WORLD_WATCH, null) ?: WORLD_OFF
        set(v) = prefs.edit().putString(KEY_WORLD_WATCH, v).apply()

    /** Which event in the list to show next, so it cycles rather than repeats. */
    var worldCursor: Int
        get() = prefs.getInt(KEY_WORLD_CURSOR, 0)
        set(v) = prefs.edit().putInt(KEY_WORLD_CURSOR, v).apply()

    /** Counts refreshes, so world events can appear every Nth one. */
    var refreshCount: Int
        get() = prefs.getInt(KEY_REFRESH_COUNT, 0)
        set(v) = prefs.edit().putInt(KEY_REFRESH_COUNT, v).apply()

    const val THEME_AUTO = "auto"
    const val THEME_DAY = "day"
    const val THEME_NIGHT = "night"

    /**
     * Day/night theming. Auto follows sunrise and sunset, including a twilight
     * window either side of each.
     */
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, null) ?: THEME_AUTO
        set(v) = prefs.edit().putString(KEY_THEME_MODE, v).apply()

    /** Severe weather banner from the US National Weather Service. */
    var showAlerts: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ALERTS, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_ALERTS, v).apply()

    /**
     * "Rain starting in 25 minutes" from the 15-minute series.
     *
     * On by default: it costs no permanent space, appearing only when there is
     * something imminent to report, and it's the most useful single line the
     * panel can show.
     */
    var showNowcast: Boolean
        get() = prefs.getBoolean(KEY_NOWCAST, true)
        set(v) = prefs.edit().putBoolean(KEY_NOWCAST, v).apply()

    /**
     * Draw a clock on the wallpaper.
     *
     * Off by default, and it has a real cost: keeping it accurate means the
     * plugin asks the launcher to re-render every minute rather than every
     * fifteen. The background is cached so that redraw doesn't refetch radar
     * tiles, but it is still fifteen times as much rendering.
     *
     * Worth turning off Projectivy's own clock first — two clocks look worse
     * than one.
     */
    var showClock: Boolean
        get() = prefs.getBoolean(KEY_CLOCK, false)
        set(v) = prefs.edit().putBoolean(KEY_CLOCK, v).apply()

    /**
     * Where the launcher's app row begins, as a percentage of screen height.
     *
     * The plugin cannot detect this. Projectivy's API exposes the focused
     * card's colours, title and package name, but no geometry whatsoever — so
     * there is nothing to read and this has to be a setting.
     *
     * The default matches Projectivy's stock layout. Anyone who has moved or
     * resized their rows can tune it, and the value is used to decide where the
     * forecast strips may sit.
     */
    var safeBottomPercent: Int
        get() = prefs.getInt(KEY_SAFE_BOTTOM, 78).coerceIn(55, 95)
        set(v) = prefs.edit().putInt(KEY_SAFE_BOTTOM, v.coerceIn(55, 95)).apply()

    /**
     * Use the whole frame once the launcher reports itself idle.
     *
     * When idle nothing is covering the wallpaper, so the strips no longer need
     * to dodge an app row that isn't being drawn.
     */
    var idleFullFrame: Boolean
        get() = prefs.getBoolean(KEY_IDLE_FULL, true)
        set(v) = prefs.edit().putBoolean(KEY_IDLE_FULL, v).apply()

    const val UPDATE_NEVER = "never"
    const val UPDATE_WEEKLY = "weekly"
    const val UPDATE_FORTNIGHTLY = "fortnightly"
    const val UPDATE_MONTHLY = "monthly"

    /**
     * How often to look for a new release.
     *
     * Weekly by default. Deliberately no daily option: releases don't arrive
     * that often, and a wallpaper that phones home every day to a service with
     * a 60-request hourly limit would be poor manners for no benefit.
     *
     * Checking only ever shows a notice. Nothing downloads or installs on its
     * own — an app that silently replaced itself would be alarming.
     */
    var updateCheckInterval: String
        get() = prefs.getString(KEY_UPDATE_INTERVAL, null) ?: UPDATE_WEEKLY
        set(v) = prefs.edit().putString(KEY_UPDATE_INTERVAL, v).apply()

    /** Milliseconds between checks, or null when disabled. */
    val updateCheckIntervalMs: Long?
        get() = when (updateCheckInterval) {
            UPDATE_WEEKLY -> 7L * 24 * 60 * 60 * 1000
            UPDATE_FORTNIGHTLY -> 14L * 24 * 60 * 60 * 1000
            UPDATE_MONTHLY -> 30L * 24 * 60 * 60 * 1000
            else -> null
        }

    var updateLastCheckedAt: Long
        get() = prefs.getLong(KEY_UPDATE_LAST_AT, 0L)
        set(v) = prefs.edit().putLong(KEY_UPDATE_LAST_AT, v).apply()

    /**
     * The newest version seen, or empty.
     *
     * Persisted so the notice survives a restart, and self-clearing: it's only
     * displayed while it's actually newer than what's installed, so updating
     * makes it disappear without needing a dismiss action.
     */
    var updateVersionFound: String
        get() = prefs.getString(KEY_UPDATE_FOUND, null) ?: ""
        set(v) = prefs.edit().putString(KEY_UPDATE_FOUND, v).apply()

    /**
     * Nudges the panel, clock and banner by a few pixels on a slow rotating
     * cycle, to spread wear across more pixels on OLED and plasma displays.
     *
     * This is a wallpaper: unlike most on-screen content it can sit unchanged
     * on a home screen for hours at a stretch, and the panel is exactly the
     * high-contrast, fixed-position content that causes burn-in fastest. The
     * shift is small enough (a few pixels at 1920x1080) to be imperceptible,
     * and only ever applies to the drawn UI — never to layout decisions like
     * whether the forecast strips fit, which are computed before this and
     * have generous margin either side of the shift's range.
     *
     * On by default: the cost is a couple of extra lines in a canvas
     * transform, so there's no real reason to leave it off even on a panel
     * that isn't OLED.
     */
    var reduceBurnIn: Boolean
        get() = prefs.getBoolean(KEY_REDUCE_BURN_IN, true)
        set(v) = prefs.edit().putBoolean(KEY_REDUCE_BURN_IN, v).apply()

    /** Set by the service from LAUNCHER_IDLE_MODE_CHANGED. */
    @Volatile var launcherIdle: Boolean = false

    const val CLOCK_TOP_RIGHT = "topRight"
    const val CLOCK_TOP_CENTRE = "topCentre"
    const val CLOCK_WITH_PANEL = "withPanel"

    const val CLOCK_SMALL = "small"
    const val CLOCK_MEDIUM = "medium"
    const val CLOCK_LARGE = "large"

    const val CLOCK_DIGITAL_LIGHT = "digitalLight"
    const val CLOCK_DIGITAL_BOLD = "digitalBold"
    const val CLOCK_ANALOGUE = "analogue"

    const val CLOCK_HOURS_SYSTEM = "system"
    const val CLOCK_HOURS_12 = "h12"
    const val CLOCK_HOURS_24 = "h24"

    /** Where the clock sits. Top right is free once Projectivy's is hidden. */
    var clockPosition: String
        get() = prefs.getString(KEY_CLOCK_POSITION, null) ?: CLOCK_TOP_RIGHT
        set(v) = prefs.edit().putString(KEY_CLOCK_POSITION, v).apply()

    var clockSize: String
        get() = prefs.getString(KEY_CLOCK_SIZE, null) ?: CLOCK_MEDIUM
        set(v) = prefs.edit().putString(KEY_CLOCK_SIZE, v).apply()

    var clockStyle: String
        get() = prefs.getString(KEY_CLOCK_STYLE, null) ?: CLOCK_DIGITAL_LIGHT
        set(v) = prefs.edit().putString(KEY_CLOCK_STYLE, v).apply()

    /**
     * 12 or 24 hour, or follow the system.
     *
     * An override exists because a TV's locale is often wrong for the household
     * using it, and it isn't always easy to change.
     */
    var clockHours: String
        get() = prefs.getString(KEY_CLOCK_HOURS, null) ?: CLOCK_HOURS_SYSTEM
        set(v) = prefs.edit().putString(KEY_CLOCK_HOURS, v).apply()

    /** Base type size in pixels for the chosen clock size. */
    val clockBaseSize: Float
        get() = when (clockSize) {
            CLOCK_SMALL -> 68f
            CLOCK_LARGE -> 140f
            else -> 96f
        }

    var showClockDate: Boolean
        get() = prefs.getBoolean(KEY_CLOCK_DATE, true)
        set(v) = prefs.edit().putBoolean(KEY_CLOCK_DATE, v).apply()

    /**
     * Plain-language advice inferred from the forecast.
     *
     * On by default. It needs no extra network calls and appears only when
     * something applies, so it costs nothing on an ordinary day.
     */
    var showAdvisories: Boolean
        get() = prefs.getBoolean(KEY_ADVISORIES, true)
        set(v) = prefs.edit().putBoolean(KEY_ADVISORIES, v).apply()

    /**
     * Air quality and pollen.
     *
     * The AQI figure joins the stats line; a poor reading gets its own
     * prominent line. Pollen only appears where the data exists, which for now
     * means Europe.
     */
    var showAirQuality: Boolean
        get() = prefs.getBoolean(KEY_AIR_QUALITY, false)
        set(v) = prefs.edit().putBoolean(KEY_AIR_QUALITY, v).apply()

    /** Aurora line when the K-index makes it plausible at this latitude. */
    var showAurora: Boolean
        get() = prefs.getBoolean(KEY_AURORA, false)
        set(v) = prefs.edit().putBoolean(KEY_AURORA, v).apply()

    /**
     * Wave height and period, only shown where the data actually exists — the
     * API returns explicit null rather than a fabricated value for locations
     * with no large body of water nearby, so this is off by default and only
     * ever appears somewhere it's genuinely meaningful.
     */
    var showMarine: Boolean
        get() = prefs.getBoolean(KEY_MARINE, false)
        set(v) = prefs.edit().putBoolean(KEY_MARINE, v).apply()

    /** "4° cooler than yesterday". */
    var showYesterday: Boolean
        get() = prefs.getBoolean(KEY_YESTERDAY, false)
        set(v) = prefs.edit().putBoolean(KEY_YESTERDAY, v).apply()

    /**
     * Recolour radar onto a ramp readable with colour vision deficiency.
     *
     * Standard radar palettes run green to red, which collapses for
     * deuteranopia — heavy rain becomes indistinguishable from drizzle.
     */
    var safeRadarPalette: Boolean
        get() = prefs.getBoolean(KEY_SAFE_RADAR, false)
        set(v) = prefs.edit().putBoolean(KEY_SAFE_RADAR, v).apply()

    /**
     * Reveals unproven features.
     *
     * Off by default, and the gated features stay off even when it's on — this
     * only makes them visible. Anything behind this gate has a known risk: it
     * may not render on some devices, or hasn't been verified on hardware at
     * all. Everything falls back to the still wallpaper on failure, which is
     * why a single toggle is enough rather than a separate beta build.
     */
    var experimentalFeatures: Boolean
        get() = prefs.getBoolean(KEY_EXPERIMENTAL, false)
        set(v) = prefs.edit().putBoolean(KEY_EXPERIMENTAL, v).apply()

    /**
     * Animate falling rain, snow and drifting fog over the background.
     *
     * Vector particles rather than bitmap frames, so the file stays a few
     * hundred kilobytes. Off by default: it's the only feature that asks the
     * launcher to animate continuously.
     */
    var animatePrecipitation: Boolean
        get() = experimentalFeatures && prefs.getBoolean(KEY_ANIMATE_PRECIP, false)
        set(v) = prefs.edit().putBoolean(KEY_ANIMATE_PRECIP, v).apply()

    /**
     * The stored choices, ignoring the gate.
     *
     * The settings screen shows these so a checkbox doesn't appear to reset
     * itself when the gate is closed and reopened.
     */
    val animateRadarStored: Boolean get() = prefs.getBoolean(KEY_ANIMATE_RADAR, false)
    val animatePrecipStored: Boolean get() = prefs.getBoolean(KEY_ANIMATE_PRECIP, false)

    /** Animate the radar background as a loop of recent observations. */
    var animateRadar: Boolean
        get() = experimentalFeatures && prefs.getBoolean(KEY_ANIMATE_RADAR, false)
        set(v) = prefs.edit().putBoolean(KEY_ANIMATE_RADAR, v).apply()

    const val LABELS_OFF = "off"
    const val LABELS_FEW = "few"
    const val LABELS_BALANCED = "balanced"
    const val LABELS_MANY = "many"

    /** How many place names the radar map shows. */
    var labelDensity: String
        get() = prefs.getString(KEY_LABEL_DENSITY, null) ?: LABELS_BALANCED
        set(v) = prefs.edit().putString(KEY_LABEL_DENSITY, v).apply()

    /**
     * Tile URL template for the radar basemap, with {z}/{x}/{y} placeholders.
     *
     * Empty by default and intentionally so: no basemap ships with the app,
     * because OpenStreetMap's tile policy forbids distributing an app that
     * uses their servers. Supply a provider whose terms permit app use.
     */
    var basemapUrl: String
        get() = prefs.getString(KEY_BASEMAP_URL, null) ?: ""
        set(v) = prefs.edit().putString(KEY_BASEMAP_URL, v).apply()

    /** Credit line your tile provider requires, drawn under the panel. */
    var basemapAttribution: String
        get() = prefs.getString(KEY_BASEMAP_ATTRIBUTION, null) ?: ""
        set(v) = prefs.edit().putString(KEY_BASEMAP_ATTRIBUTION, v).apply()

    /**
     * Hides identifying details for screenshots and demos. Does not change which
     * forecast is fetched — only what is drawn.
     */
    var demoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO_MODE, false)
        set(v) = prefs.edit().putBoolean(KEY_DEMO_MODE, v).apply()

    var demoLabel: String
        get() = prefs.getString(KEY_DEMO_LABEL, null) ?: "Weather"
        set(v) = prefs.edit().putString(KEY_DEMO_LABEL, v).apply()

    /**
     * The label the wallpaper should actually draw. Use this rather than
     * placeLabel anywhere user-visible, so demo mode can't be bypassed by a
     * caller that forgot to check it.
     */
    val displayLabel: String
        get() = if (demoMode) demoLabel else (activeLabel ?: placeLabel)

    var locationConfigured: Boolean
        get() = prefs.getBoolean(KEY_CONFIGURED, false)
        set(v) = prefs.edit().putBoolean(KEY_CONFIGURED, v).apply()

    fun export(): String = JSONObject().apply {
        put(KEY_LAT, latitude.toString())
        put(KEY_LON, longitude.toString())
        put(KEY_PLACE, placeLabel)
        put(KEY_METRIC, useMetric)
        put(KEY_CONFIGURED, locationConfigured)
        put(KEY_BACKGROUND, backgroundSource)
        put(KEY_RADAR_ZOOM, radarZoom.toString())
        put(KEY_SHOW_HOURLY, showHourly)
        put(KEY_SHOW_DAILY, showDaily)
        put(KEY_SHOW_STATS, showStats)
        put(KEY_SHOW_SUN, showSun)
        put(KEY_SELECTED_PACK, selectedPack)
        put(KEY_DEMO_MODE, demoMode)
        put(KEY_DEMO_LABEL, demoLabel)
        put(KEY_LABEL_DENSITY, labelDensity)
        put(KEY_SHOW_ALERTS, showAlerts)
        put(KEY_ANIMATE_RADAR, animateRadar)
        put(KEY_THEME_MODE, themeMode)
        put(KEY_WORLD_WATCH, worldWatch)
        put(KEY_CYCLE_MODE, cycleMode)
        put(KEY_HOLIDAY, holidayThemes)
        put(KEY_PANEL_SCALE, panelScale)
        put(KEY_ANIMATE_PRECIP, animatePrecipitation)
        put(KEY_EXPERIMENTAL, experimentalFeatures)
        put(KEY_NOWCAST, showNowcast)
        put(KEY_AURORA, showAurora)
        put(KEY_MARINE, showMarine)
        put(KEY_YESTERDAY, showYesterday)
        put(KEY_SAFE_RADAR, safeRadarPalette)
        put(KEY_AIR_QUALITY, showAirQuality)
        put(KEY_ADVISORIES, showAdvisories)
        put(KEY_CLOCK, showClock)
        put(KEY_CLOCK_DATE, showClockDate)
        put(KEY_CLOCK_POSITION, clockPosition)
        put(KEY_CLOCK_SIZE, clockSize)
        put(KEY_CLOCK_STYLE, clockStyle)
        put(KEY_CLOCK_HOURS, clockHours)
        put(KEY_SAFE_BOTTOM, safeBottomPercent)
        put(KEY_IDLE_FULL, idleFullFrame)
        put(KEY_UPDATE_INTERVAL, updateCheckInterval)
        put(KEY_REDUCE_BURN_IN, reduceBurnIn)
        put(KEY_LOCATIONS, prefs.getString(KEY_LOCATIONS, "[]"))
        put(KEY_BASEMAP_URL, basemapUrl)
        put(KEY_BASEMAP_ATTRIBUTION, basemapAttribution)
        // Deliberately not exported: an API key shouldn't travel in a settings
        // blob that Projectivy may back up or log.
    }.toString()

    fun import(params: String) {
        try {
            val json = JSONObject(params)
            if (json.has(KEY_LAT)) json.optString(KEY_LAT).toDoubleOrNull()?.let { latitude = it }
            if (json.has(KEY_LON)) json.optString(KEY_LON).toDoubleOrNull()?.let { longitude = it }
            if (json.has(KEY_PLACE)) placeLabel = json.getString(KEY_PLACE)
            if (json.has(KEY_METRIC)) useMetric = json.getBoolean(KEY_METRIC)
            if (json.has(KEY_CONFIGURED)) locationConfigured = json.getBoolean(KEY_CONFIGURED)
            if (json.has(KEY_BACKGROUND)) backgroundSource = json.getString(KEY_BACKGROUND)
            if (json.has(KEY_RADAR_ZOOM)) json.optString(KEY_RADAR_ZOOM).toIntOrNull()?.let { radarZoom = it }
            if (json.has(KEY_SHOW_HOURLY)) showHourly = json.getBoolean(KEY_SHOW_HOURLY)
            if (json.has(KEY_SHOW_DAILY)) showDaily = json.getBoolean(KEY_SHOW_DAILY)
            if (json.has(KEY_SHOW_STATS)) showStats = json.getBoolean(KEY_SHOW_STATS)
            if (json.has(KEY_SHOW_SUN)) showSun = json.getBoolean(KEY_SHOW_SUN)
            if (json.has(KEY_SELECTED_PACK)) selectedPack = json.getString(KEY_SELECTED_PACK)
            if (json.has(KEY_DEMO_MODE)) demoMode = json.getBoolean(KEY_DEMO_MODE)
            if (json.has(KEY_DEMO_LABEL)) demoLabel = json.getString(KEY_DEMO_LABEL)
            if (json.has(KEY_LABEL_DENSITY)) labelDensity = json.getString(KEY_LABEL_DENSITY)
            if (json.has(KEY_SHOW_ALERTS)) showAlerts = json.getBoolean(KEY_SHOW_ALERTS)
            if (json.has(KEY_ANIMATE_RADAR)) animateRadar = json.getBoolean(KEY_ANIMATE_RADAR)
            if (json.has(KEY_THEME_MODE)) themeMode = json.getString(KEY_THEME_MODE)
            if (json.has(KEY_WORLD_WATCH)) worldWatch = json.getString(KEY_WORLD_WATCH)
            if (json.has(KEY_CYCLE_MODE)) cycleMode = json.getString(KEY_CYCLE_MODE)
            if (json.has(KEY_HOLIDAY)) holidayThemes = json.getBoolean(KEY_HOLIDAY)
            if (json.has(KEY_PANEL_SCALE)) panelScale = json.getInt(KEY_PANEL_SCALE)
            if (json.has(KEY_ANIMATE_PRECIP)) {
                animatePrecipitation = json.getBoolean(KEY_ANIMATE_PRECIP)
            }
            if (json.has(KEY_EXPERIMENTAL)) {
                experimentalFeatures = json.getBoolean(KEY_EXPERIMENTAL)
            }
            if (json.has(KEY_NOWCAST)) showNowcast = json.getBoolean(KEY_NOWCAST)
            if (json.has(KEY_AURORA)) showAurora = json.getBoolean(KEY_AURORA)
            if (json.has(KEY_MARINE)) showMarine = json.getBoolean(KEY_MARINE)
            if (json.has(KEY_YESTERDAY)) showYesterday = json.getBoolean(KEY_YESTERDAY)
            if (json.has(KEY_SAFE_RADAR)) safeRadarPalette = json.getBoolean(KEY_SAFE_RADAR)
            if (json.has(KEY_AIR_QUALITY)) showAirQuality = json.getBoolean(KEY_AIR_QUALITY)
            if (json.has(KEY_ADVISORIES)) showAdvisories = json.getBoolean(KEY_ADVISORIES)
            if (json.has(KEY_CLOCK)) showClock = json.getBoolean(KEY_CLOCK)
            if (json.has(KEY_CLOCK_DATE)) showClockDate = json.getBoolean(KEY_CLOCK_DATE)
            if (json.has(KEY_CLOCK_POSITION)) clockPosition = json.getString(KEY_CLOCK_POSITION)
            if (json.has(KEY_CLOCK_SIZE)) clockSize = json.getString(KEY_CLOCK_SIZE)
            if (json.has(KEY_CLOCK_STYLE)) clockStyle = json.getString(KEY_CLOCK_STYLE)
            if (json.has(KEY_CLOCK_HOURS)) clockHours = json.getString(KEY_CLOCK_HOURS)
            if (json.has(KEY_SAFE_BOTTOM)) safeBottomPercent = json.getInt(KEY_SAFE_BOTTOM)
            if (json.has(KEY_IDLE_FULL)) idleFullFrame = json.getBoolean(KEY_IDLE_FULL)
            if (json.has(KEY_UPDATE_INTERVAL)) {
                updateCheckInterval = json.getString(KEY_UPDATE_INTERVAL)
            }
            if (json.has(KEY_REDUCE_BURN_IN)) {
                reduceBurnIn = json.getBoolean(KEY_REDUCE_BURN_IN)
            }
            if (json.has(KEY_LOCATIONS)) {
                prefs.edit().putString(KEY_LOCATIONS, json.getString(KEY_LOCATIONS)).apply()
            }
            if (json.has(KEY_BASEMAP_URL)) basemapUrl = json.getString(KEY_BASEMAP_URL)
            if (json.has(KEY_BASEMAP_ATTRIBUTION)) basemapAttribution = json.getString(KEY_BASEMAP_ATTRIBUTION)
        } catch (e: Exception) {
            // Malformed input from the launcher shouldn't wipe working settings.
            Log.e(TAG, "Error importing preferences", e)
        }
    }
}
