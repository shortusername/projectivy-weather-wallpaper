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

    /** Animate the radar background as a loop of recent observations. */
    var animateRadar: Boolean
        get() = prefs.getBoolean(KEY_ANIMATE_RADAR, false)
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
