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
        get() = if (demoMode) demoLabel else placeLabel

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
            if (json.has(KEY_BASEMAP_URL)) basemapUrl = json.getString(KEY_BASEMAP_URL)
            if (json.has(KEY_BASEMAP_ATTRIBUTION)) basemapAttribution = json.getString(KEY_BASEMAP_ATTRIBUTION)
        } catch (e: Exception) {
            // Malformed input from the launcher shouldn't wipe working settings.
            Log.e(TAG, "Error importing preferences", e)
        }
    }
}
