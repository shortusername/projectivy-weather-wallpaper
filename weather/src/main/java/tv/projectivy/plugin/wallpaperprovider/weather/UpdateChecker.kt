package tv.projectivy.plugin.wallpaperprovider.weather

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer build and installs it.
 *
 * The releases API is public and needs no key. Unauthenticated requests are
 * limited to 60 an hour per IP, which a once-a-day check never approaches.
 *
 * Installing requires REQUEST_INSTALL_PACKAGES and the user granting "install
 * unknown apps" for this plugin. Android also refuses any update whose signing
 * signature differs from the installed copy, so this is only useful when
 * releases are signed with a stable key.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val TIMEOUT_MS = 15_000
    private const val UA =
        "ProjectivyWeatherWallpaper (+https://github.com/shortusername/projectivy-weather-wallpaper)"

    private const val LATEST_URL =
        "https://api.github.com/repos/shortusername/projectivy-weather-wallpaper/releases/latest"

    private const val APK_NAME = "update.apk"

    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long
    )

    /** Blocking. Call from a worker thread, never the settings UI thread. */
    fun fetchLatest(): Release? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${conn.responseCode}")
                return null
            }
            val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null

            val tag = root.optString("tag_name").ifBlank { return null }
            val assets = root.optJSONArray("assets") ?: return null

            var url = ""
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size", 0L)
                    break
                }
            }
            if (url.isBlank()) return null

            Release(
                version = tag.removePrefix("v"),
                notes = root.optString("body", ""),
                apkUrl = url,
                sizeBytes = size
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Update check failed: ${t.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * True when [candidate] is a higher version than what's installed.
     *
     * Compares dot-separated numbers, so 2.10 beats 2.9 — a plain string
     * comparison would get that backwards.
     */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v")
            .split('.', '-', '+')
            .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }

    /** Downloads the APK to cacheDir. Blocking. Returns null on failure. */
    fun download(context: Context, release: Release): File? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
            }
            if (conn.responseCode !in 200..299) return null

            val out = File(context.cacheDir, APK_NAME)
            conn.inputStream.use { input ->
                out.outputStream().use { input.copyTo(it, 64 * 1024) }
            }
            // A truncated download would fail to install with a confusing error,
            // so check the size the API reported before handing it over.
            if (release.sizeBytes > 0 && out.length() != release.sizeBytes) {
                Log.w(TAG, "Size mismatch: ${out.length()} vs ${release.sizeBytes}")
                out.delete()
                return null
            }
            out
        } catch (t: Throwable) {
            Log.w(TAG, "Download failed: ${t.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Hands the APK to the system installer. The user still confirms, and must
     * have allowed this app to install unknown apps.
     */
    fun install(context: Context, apk: File): Boolean = try {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (t: Throwable) {
        Log.w(TAG, "Install intent failed: ${t.message}")
        false
    }
}
