package tv.projectivy.plugin.wallpaperprovider.weather

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Community wallpaper packs.
 *
 * A pack maps weather conditions to assets. The index lives in the repo so new
 * packs land via pull request without an APK release; assets download on demand
 * and are cached under filesDir/packs/<packId>/.
 *
 * Contributors never ship code — only images, Lottie JSON, and an index entry.
 * See CONTRIBUTING.md for the format.
 */
object PackManager {

    private const val TAG = "PackManager"
    private const val TIMEOUT_MS = 12_000
    private const val UA = "ProjectivyWeatherWallpaper/1.5"

    /**
     * jsDelivr rather than raw.githubusercontent: it's CDN-backed, sends proper
     * caching headers, and won't rate-limit a few thousand TVs checking in.
     */
    private const val INDEX_URL =
            "https://cdn.jsdelivr.net/gh/shortusername/projectivy-weather-wallpaper@main/packs/index.json"

    private const val INDEX_CACHE = "packs_index.json"
    private const val INDEX_TTL_MS = 24 * 60 * 60 * 1000L

    const val KIND_STATIC = "static"
    const val KIND_LOTTIE = "lottie"
    const val KIND_VIDEO = "video"

    data class Pack(
        val id: String,
        val name: String,
        val author: String,
        val license: String,
        val kind: String,
        /** Condition key -> asset URL. Keys as documented in CONTRIBUTING.md. */
        val assets: Map<String, String>
    )

    // ------------------------------------------------------------------ index

    /**
     * Cached index only — never touches the network.
     *
     * Safe to call from the settings UI thread. Returns empty until refresh()
     * has succeeded at least once.
     */
    fun cachedPacks(context: Context): List<Pack> {
        val cache = File(context.filesDir, INDEX_CACHE)
        if (!cache.exists()) return emptyList()
        return runCatching { parseIndex(cache.readText()) }.getOrElse {
            Log.w(TAG, "Index parse failed: ${it.message}")
            emptyList()
        }
    }

    fun indexIsStale(context: Context): Boolean {
        val cache = File(context.filesDir, INDEX_CACHE)
        return !cache.exists() ||
                System.currentTimeMillis() - cache.lastModified() >= INDEX_TTL_MS
    }

    /**
     * Downloads the index. Blocking — call from a background thread or the
     * service's binder thread, never from the settings UI.
     * Returns true when the cache was updated.
     */
    fun refresh(context: Context): Boolean {
        val body = fetchText(INDEX_URL) ?: return false
        return runCatching {
            // Parse before writing, so a malformed index can't poison the cache.
            parseIndex(body)
            File(context.filesDir, INDEX_CACHE).writeText(body)
            true
        }.getOrDefault(false)
    }

    /** Cache-first with a refresh when stale. For use off the UI thread. */
    fun packs(context: Context): List<Pack> {
        if (indexIsStale(context)) refresh(context)
        return cachedPacks(context)
    }

    private fun parseIndex(body: String): List<Pack> {
        val root = JSONObject(body)
        val arr: JSONArray = root.getJSONArray("packs")
        val out = mutableListOf<Pack>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val assetsObj = o.getJSONObject("assets")
            val assets = mutableMapOf<String, String>()
            assetsObj.keys().forEach { k -> assets[k] = assetsObj.getString(k) }

            out.add(
                Pack(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    author = o.optString("author", "Unknown"),
                    license = o.optString("license", "unspecified"),
                    kind = o.optString("kind", KIND_STATIC),
                    assets = assets
                )
            )
        }
        return out
    }

    fun findPack(context: Context, id: String): Pack? = packs(context).firstOrNull { it.id == id }

    // ------------------------------------------------------------------ assets

    /**
     * Condition keys, most specific first. A pack only needs the ones it wants
     * to cover; resolution falls back through this list to "default".
     */
    fun candidateKeys(c: OpenMeteoClient.Conditions): List<String> {
        val bucket = OpenMeteoClient.bucket(c.weatherCode)
        val time = if (c.isDay) "day" else "night"
        return listOf(
            "${bucket}-$time",   // clear-night
            bucket,              // clear
            time,                // night
            "default"
        )
    }

    /**
     * Local file for the current conditions, downloading if needed.
     * Null when the pack covers nothing applicable or the download fails.
     */
    fun resolveAsset(context: Context, pack: Pack, c: OpenMeteoClient.Conditions): File? {
        val url = candidateKeys(c).firstNotNullOfOrNull { pack.assets[it] } ?: return null

        val dir = File(context.filesDir, "packs/${pack.id}").apply { mkdirs() }
        // Hash the URL for the filename: keeps it filesystem-safe and means a
        // changed URL naturally invalidates the old cached copy.
        val ext = url.substringAfterLast('.', "bin").takeIf { it.length <= 5 } ?: "bin"
        val cached = File(dir, "${sha1(url)}.$ext")

        if (cached.exists() && cached.length() > 0) return cached

        val bytes = fetchBytes(url) ?: return null
        return runCatching {
            cached.writeBytes(bytes)
            cached
        }.getOrNull()
    }

    /** Total bytes cached for all packs, for the settings screen. */
    fun cacheSize(context: Context): Long =
        File(context.filesDir, "packs").walkBottomUp()
            .filter { it.isFile }
            .sumOf { it.length() }

    fun clearCache(context: Context) {
        File(context.filesDir, "packs").deleteRecursively()
        File(context.filesDir, INDEX_CACHE).delete()
    }

    // ------------------------------------------------------------------ shared

    private fun sha1(input: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

    private fun fetchText(url: String): String? = fetchBytes(url)?.toString(Charsets.UTF_8)

    private fun fetchBytes(url: String): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${conn.responseCode} for $url")
                null
            } else {
                conn.inputStream.use { it.readBytes() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch failed ($url): ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
