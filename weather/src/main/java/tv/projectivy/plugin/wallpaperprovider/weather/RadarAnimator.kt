package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Builds an animated radar loop as a Lottie file.
 *
 * The launcher renders one file, so animation means shipping every frame inside
 * it. Embedding full 1080p frames would be enormous, so the scene is split: the
 * map and the weather panel are composited once as a single still, and only the
 * radar overlays animate. Those are mostly transparent and compress hard, which
 * is what makes this viable at all.
 *
 * Frames cycle by layer visibility — each radar layer is given in/out points
 * covering its slice of the timeline. No keyframe interpolation is involved, so
 * playback is just Lottie swapping which image is drawn.
 */
object RadarAnimator {

    private const val TAG = "RadarAnimator"
    private const val OUTPUT_NAME = "radar_loop.json"

    private const val W = 1920
    private const val H = 1080

    /** Frames per second of the finished loop. Slow: this is a wallpaper. */
    private const val FPS = 8

    /**
     * Radar layers are encoded at half resolution and scaled back up.
     *
     * Measured on real tiles: full-resolution lossless frames produce a ~10 MB
     * file, which is untenable. Half-scale lossy WebP with alpha is ~50 KB a
     * frame. Radar is soft, blobby imagery and we blur it for bloom anyway, so
     * the resolution loss is invisible.
     */
    private const val RADAR_DIV = 2
    private const val RADAR_QUALITY = 75
    private const val BASE_QUALITY = 82

    /** Timeline frames each radar image stays on screen. */
    private const val HOLD = 6

    /** Extra hold on the newest frame, so the loop pauses on "now". */
    private const val FINAL_HOLD = 22

    /**
     * @param base the still scene: map, borders, labels, weather panel
     * @param radarFrames oldest first; the last is the current observation
     */
    /**
     * Half-scale lossy WebP bytes for one radar frame.
     *
     * Callers encode each frame as it arrives and recycle the bitmap
     * immediately: seven 1920x1080 ARGB bitmaps held together is roughly 56 MB,
     * which a TV box will not tolerate.
     */
    fun encodeFrame(frame: Bitmap): ByteArray {
        val small = Bitmap.createScaledBitmap(
            frame, frame.width / RADAR_DIV, frame.height / RADAR_DIV, true
        )
        return try {
            ByteArrayOutputStream().use { stream ->
                small.compress(webpFormat(), RADAR_QUALITY, stream)
                stream.toByteArray()
            }
        } finally {
            small.recycle()
        }
    }

    fun build(cacheDir: File, base: Bitmap, radarFrames: List<ByteArray>): File? {
        if (radarFrames.isEmpty()) return null
        return try {
            val totalFrames = (radarFrames.size - 1) * HOLD + FINAL_HOLD

            val assets = JSONArray()
            val layers = JSONArray()

            assets.put(imageAsset("base", base, BASE_QUALITY))
            // The still scene sits at the bottom for the whole loop.
            layers.put(
                imageLayer(
                    id = "base", index = radarFrames.size + 1,
                    inPoint = 0, outPoint = totalFrames
                )
            )

            radarFrames.forEachIndexed { i, encoded ->
                val assetId = "radar_$i"
                assets.put(
                    encodedAsset(assetId, encoded, W / RADAR_DIV, H / RADAR_DIV)
                )

                val start = i * HOLD
                val end = if (i == radarFrames.size - 1) totalFrames else start + HOLD
                layers.put(
                    imageLayer(
                        id = assetId, index = radarFrames.size - i,
                        inPoint = start, outPoint = end,
                        scale = RADAR_DIV * 100.0,
                        anchorX = W / (2.0 * RADAR_DIV), anchorY = H / (2.0 * RADAR_DIV)
                    )
                )
            }

            val root = JSONObject().apply {
                put("v", "5.7.0")
                put("fr", FPS)
                put("ip", 0)
                put("op", totalFrames)
                put("w", W)
                put("h", H)
                put("nm", "Radar loop")
                put("ddd", 0)
                put("assets", assets)
                put("layers", layers)
            }

            val out = File(cacheDir, OUTPUT_NAME)
            out.writeText(root.toString())
            Log.i(
                TAG,
                "Built ${radarFrames.size}-frame loop, ${out.length() / 1024} KB, ${totalFrames}f"
            )
            out
        } catch (t: Throwable) {
            // Throwable, not Exception: OutOfMemoryError is an Error, and letting
            // it escape kills the binder call and blanks the wallpaper entirely.
            Log.w(TAG, "Loop build failed: ${t.message}")
            null
        }
    }

    /**
     * Lossy WebP preserves alpha, which the radar layers need, and Android can
     * encode it without a third-party library. PNG would be several times
     * larger; palette quantisation isn't available in the platform encoder.
     */
    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
            Bitmap.CompressFormat.WEBP_LOSSY
        else
            Bitmap.CompressFormat.WEBP

    private fun imageAsset(id: String, bitmap: Bitmap, quality: Int): JSONObject {
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(webpFormat(), quality, stream)
            stream.toByteArray()
        }
        return encodedAsset(id, bytes, bitmap.width, bitmap.height)
    }

    private fun encodedAsset(id: String, bytes: ByteArray, w: Int, h: Int): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("w", w)
            put("h", h)
            put("u", "")
            put("p", "data:image/webp;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
            put("e", 1)
        }

    private fun imageLayer(
        id: String,
        index: Int,
        inPoint: Int,
        outPoint: Int,
        scale: Double = 100.0,
        anchorX: Double = W / 2.0,
        anchorY: Double = H / 2.0
    ) = JSONObject().apply {
            put("ddd", 0)
            put("ind", index)
            put("ty", 2)
            put("nm", id)
            put("refId", id)
            put("sr", 1)
            put("ip", inPoint)
            put("op", outPoint)
            put("st", 0)
            put("bm", 0)
            put("ks", JSONObject().apply {
                put("o", scalar(100))
                put("r", scalar(0))
                put("p", vector(W / 2.0, H / 2.0))
                put("a", vector(anchorX, anchorY))
                put("s", vector(scale, scale))
            })
        }

    private fun scalar(v: Number) = JSONObject().apply { put("a", 0); put("k", v) }

    private fun vector(x: Double, y: Double) = JSONObject().apply {
        put("a", 0)
        put("k", JSONArray(listOf(x, y, 0.0)))
    }
}
