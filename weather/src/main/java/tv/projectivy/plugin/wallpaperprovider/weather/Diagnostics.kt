package tv.projectivy.plugin.wallpaperprovider.weather

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a diagnostic report a user can attach to a bug report.
 *
 * Redaction is the point of this class, not an afterthought. A raw dump would
 * contain the user's home coordinates, their town name, and their Unsplash API
 * key — all things people paste into public issue trackers without noticing.
 * Everything here is either non-identifying or deliberately coarsened.
 *
 * Note that since Android 4.1 an app can only read its own logcat entries, so
 * the log section covers this plugin and nothing else on the device. That's the
 * relevant part anyway; Projectivy's own logs aren't ours to read.
 */
object Diagnostics {

    private const val TAG = "Diagnostics"
    private const val REPORT_NAME = "weather-plugin-report.txt"
    private const val LOG_LINES = 400

    /** Our own tags, so the log section stays relevant. */
    private val OUR_TAGS = listOf(
        "WeatherWallpaper", "OpenMeteoClient", "IpLocationClient", "NwsAlertsClient",
        "Backgrounds", "GeographyRenderer", "PackManager", "RadarAnimator",
        "LottieComposer", "UpdateChecker", "WeatherPrefs", "Diagnostics"
    )

    fun build(context: Context): String {
        val sb = StringBuilder()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())

        sb.appendLine("Projectivy Weather Wallpaper — diagnostic report")
        sb.appendLine("Generated: $stamp")
        sb.appendLine()

        sb.appendLine("== Build ==")
        sb.appendLine("Plugin version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("Application id: ${context.packageName}")
        sb.appendLine("Projectivy installed: ${projectivyVersion(context)}")
        sb.appendLine()

        sb.appendLine("== Device ==")
        sb.appendLine("Model: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Device: ${Build.DEVICE} / ${Build.PRODUCT}")
        sb.appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine("Locale: ${Locale.getDefault()}")
        sb.appendLine()

        sb.appendLine("== Memory ==")
        val rt = Runtime.getRuntime()
        sb.appendLine("Heap max: ${rt.maxMemory() / 1048576} MB")
        sb.appendLine("Heap used: ${(rt.totalMemory() - rt.freeMemory()) / 1048576} MB")
        sb.appendLine()

        sb.appendLine("== Settings ==")
        sb.appendLine(settingsSnapshot(context))
        sb.appendLine()

        sb.appendLine("== Cache ==")
        sb.appendLine("Pack cache: ${PackManager.cacheSize(context) / 1024} KB")
        sb.appendLine("Local photos folder: ${localPhotoCount(context)} image(s)")
        sb.appendLine("Geography asset: ${assetSize(context, "geography.bin")}")
        sb.appendLine()

        sb.appendLine("== Recent log (this plugin only, last $LOG_LINES lines) ==")
        sb.appendLine(logcat())
        sb.appendLine()

        sb.appendLine("== Redaction note ==")
        sb.appendLine("Coordinates are rounded to whole degrees (~70 miles).")
        sb.appendLine("Location name and API keys are omitted entirely.")

        return sb.toString()
    }

    /**
     * Settings, with anything identifying removed.
     *
     * Coordinates are rounded to whole degrees: enough to tell whether the
     * location is plausible or obviously wrong, nowhere near enough to identify
     * a household.
     */
    private fun settingsSnapshot(context: Context): String {
        PreferencesManager.init(context)
        val sb = StringBuilder()
        sb.appendLine("Background source: ${PreferencesManager.backgroundSource}")
        sb.appendLine("Theme mode: ${PreferencesManager.themeMode}")
        sb.appendLine("Radar area (zoom): ${PreferencesManager.radarZoom}")
        sb.appendLine("Animate radar: ${PreferencesManager.animateRadar}")
        sb.appendLine("Label density: ${PreferencesManager.labelDensity}")
        sb.appendLine("Alerts enabled: ${PreferencesManager.showAlerts}")
        sb.appendLine("Panels: hourly=${PreferencesManager.showHourly} " +
                "daily=${PreferencesManager.showDaily} " +
                "stats=${PreferencesManager.showStats} " +
                "sun=${PreferencesManager.showSun}")
        sb.appendLine("Units: ${if (PreferencesManager.useMetric) "metric" else "imperial"}")
        sb.appendLine("Demo mode: ${PreferencesManager.demoMode}")
        sb.appendLine("Selected pack: ${PreferencesManager.selectedPack.ifBlank { "(none)" }}")
        sb.appendLine("Custom basemap set: ${PreferencesManager.basemapUrl.isNotBlank()}")
        sb.appendLine("Unsplash key set: ${PreferencesManager.unsplashKey.isNotBlank()}")
        sb.appendLine(
            "Approx location: ${Math.round(PreferencesManager.latitude)}, " +
                    "${Math.round(PreferencesManager.longitude)} (rounded)"
        )
        sb.appendLine("Location configured: ${PreferencesManager.locationConfigured}")
        return sb.toString().trimEnd()
    }

    /**
     * Own-process logcat, filtered to our tags and scrubbed.
     *
     * Coordinates appear in logged request URLs, so those are stripped before
     * the text goes anywhere near a public issue.
     */
    private fun logcat(): String = try {
        val process = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-v", "time", "-t", LOG_LINES.toString())
        )
        val raw = process.inputStream.bufferedReader().use { it.readText() }
        val kept = raw.lineSequence()
            .filter { line -> OUR_TAGS.any { line.contains(it) } }
            .map { redact(it) }
            .toList()
        if (kept.isEmpty()) "(no plugin log entries found)" else kept.joinToString("\n")
    } catch (t: Throwable) {
        "(couldn't read logcat: ${t.message})"
    }

    private fun redact(line: String): String = line
        .replace(Regex("latitude=-?\\d+\\.?\\d*"), "latitude=REDACTED")
        .replace(Regex("longitude=-?\\d+\\.?\\d*"), "longitude=REDACTED")
        .replace(Regex("point=-?\\d+\\.?\\d*,-?\\d+\\.?\\d*"), "point=REDACTED")
        .replace(Regex("client_id=[A-Za-z0-9_-]+"), "client_id=REDACTED")
        .replace(Regex("(?i)Client-ID [A-Za-z0-9_-]+"), "Client-ID REDACTED")
        .replace(Regex("[?&]key=[A-Za-z0-9_-]+"), "?key=REDACTED")
        .replace(Regex("[?&]api_key=[A-Za-z0-9_-]+"), "?api_key=REDACTED")
        .replace(Regex("[?&]apikey=[A-Za-z0-9_-]+"), "?apikey=REDACTED")

    private fun projectivyVersion(context: Context): String = try {
        val info = context.packageManager.getPackageInfo("com.spocky.projengmenu", 0)
        "${info.versionName}"
    } catch (_: PackageManager.NameNotFoundException) {
        "not installed"
    } catch (t: Throwable) {
        "unknown"
    }

    private fun localPhotoCount(context: Context): Int = try {
        Backgrounds.localFolder(context).listFiles()?.count { it.isFile } ?: 0
    } catch (_: Throwable) {
        -1
    }

    private fun assetSize(context: Context, name: String): String = try {
        context.assets.open(name).use { "${it.available() / 1024} KB" }
    } catch (_: Throwable) {
        "missing"
    }

    /**
     * Writes the report where the user can actually retrieve it: the plugin's
     * external files directory, reachable over adb, from a file manager, or by
     * USB, with no runtime permission needed.
     */
    fun write(context: Context): File? = try {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, REPORT_NAME)
        file.writeText(build(context))
        Log.i(TAG, "Report written to ${file.absolutePath} (${file.length()} bytes)")
        file
    } catch (t: Throwable) {
        Log.w(TAG, "Report write failed: ${t.message}")
        null
    }
}
