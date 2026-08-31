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
        get() = prefs.getString(KEY_BACKGROUND, null) ?: Backgrounds.SOURCE_GRADIENT
        set(v) = prefs.edit().putString(KEY_BACKGROUND, v).apply()

    /** Unsplash Access Key. Empty means the stock source is unavailable. */
    var unsplashKey: String
        get() = prefs.getString(KEY_UNSPLASH, null) ?: ""
        set(v) = prefs.edit().putString(KEY_UNSPLASH, v).apply()

    /** Radar zoom: 4 is regional, 9 is close to town level. */
    var radarZoom: Int
        get() = prefs.getString(KEY_RADAR_ZOOM, null)?.toIntOrNull() ?: 7
        set(v) = prefs.edit().putString(KEY_RADAR_ZOOM, v.toString()).apply()

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
        } catch (e: Exception) {
            // Malformed input from the launcher shouldn't wipe working settings.
            Log.e(TAG, "Error importing preferences", e)
        }
    }
}
