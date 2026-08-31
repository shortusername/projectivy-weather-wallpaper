package tv.projectivy.plugin.wallpaperprovider.weather

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Composites the weather panel into a contributed Lottie animation.
 *
 * Why not Lottie text layers: font resolution happens inside Projectivy's
 * process, via lottie-android's FontAssetManager. We can't install a
 * FontAssetDelegate there, so a text layer referencing a font we can't supply
 * may silently fail to render.
 *
 * Embedded image assets have no such problem. Lottie's spec allows an asset
 * with e=1 and p set to a base64 data URI, so we render the panel to a
 * transparent PNG, embed it, and add one image layer pinned above everything
 * else. No fonts, no external files, no launcher cooperation needed.
 */
object LottieComposer {

    private const val TAG = "LottieComposer"
    private const val OUTPUT_NAME = "weather_composed.json"
    private const val OVERLAY_ASSET_ID = "wx_overlay"

    /** Panel PNG dimensions, matching WeatherRenderer's canvas. */
    private const val OVERLAY_W = 1920
    private const val OVERLAY_H = 1080

    /**
     * Returns a merged Lottie file, or null to fall back to a static render.
     *
     * The contributed animation is left untouched; we only append an asset and
     * a layer, so a pack author's timing and easing are preserved exactly.
     */
    fun compose(cacheDir: File, lottieFile: File, overlayPng: File): File? = try {
        val root = JSONObject(lottieFile.readText())

        val width = root.optInt("w", OVERLAY_W)
        val height = root.optInt("h", OVERLAY_H)
        val outPoint = root.optDouble("op", 60.0)
        val inPoint = root.optDouble("ip", 0.0)

        val base64 = Base64.encodeToString(overlayPng.readBytes(), Base64.NO_WRAP)

        // 1. Register the panel as an embedded image asset.
        val assets = root.optJSONArray("assets") ?: JSONArray().also { root.put("assets", it) }
        assets.put(
            JSONObject().apply {
                put("id", OVERLAY_ASSET_ID)
                put("w", OVERLAY_W)
                put("h", OVERLAY_H)
                put("u", "")
                put("p", "data:image/png;base64,$base64")
                put("e", 1)          // embedded
            }
        )

        // 2. Add an image layer referencing it.
        val layers = root.optJSONArray("layers") ?: JSONArray().also { root.put("layers", it) }

        // Scale the panel to the animation's canvas if the pack isn't 1080p.
        val scaleX = width.toDouble() / OVERLAY_W * 100.0
        val scaleY = height.toDouble() / OVERLAY_H * 100.0

        val overlayLayer = JSONObject().apply {
            put("ddd", 0)
            put("ind", nextIndex(layers))
            put("ty", 2)             // image layer
            put("nm", "Weather overlay")
            put("refId", OVERLAY_ASSET_ID)
            put("sr", 1)
            put("ip", inPoint)
            put("op", outPoint)
            put("st", 0)
            put("bm", 0)
            put("ks", JSONObject().apply {
                put("o", staticValue(100))                      // opacity
                put("r", staticValue(0))                        // rotation
                put("p", staticArray(listOf(width / 2.0, height / 2.0, 0.0)))
                put("a", staticArray(listOf(OVERLAY_W / 2.0, OVERLAY_H / 2.0, 0.0)))
                put("s", staticArray(listOf(scaleX, scaleY, 100.0)))
            })
        }

        // Lottie paints index 0 first, so inserting at position 0 puts the panel
        // on top of the contributed art rather than behind it.
        insertFirst(layers, overlayLayer)

        val out = File(cacheDir, OUTPUT_NAME)
        out.writeText(root.toString())
        out
    } catch (e: Exception) {
        Log.w(TAG, "Compose failed, falling back to static: ${e.message}")
        null
    }

    /** Lottie property shorthand: a non-animated scalar. */
    private fun staticValue(v: Number) = JSONObject().apply {
        put("a", 0)
        put("k", v)
    }

    /** Lottie property shorthand: a non-animated vector. */
    private fun staticArray(values: List<Double>) = JSONObject().apply {
        put("a", 0)
        put("k", JSONArray(values))
    }

    private fun nextIndex(layers: JSONArray): Int {
        var max = 0
        for (i in 0 until layers.length()) {
            max = maxOf(max, layers.optJSONObject(i)?.optInt("ind", 0) ?: 0)
        }
        return max + 1
    }

    private fun insertFirst(layers: JSONArray, layer: JSONObject) {
        val existing = mutableListOf<JSONObject>()
        for (i in 0 until layers.length()) {
            layers.optJSONObject(i)?.let { existing.add(it) }
        }
        // org.json has no insert-at-index on older Android, so rebuild in place.
        for (i in layers.length() - 1 downTo 0) layers.remove(i)
        layers.put(layer)
        existing.forEach { layers.put(it) }
    }
}
