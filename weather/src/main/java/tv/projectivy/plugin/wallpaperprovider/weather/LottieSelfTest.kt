package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A deliberately minimal Lottie, to find out why animation renders blank.
 *
 * Animated radar shows nothing on an Nvidia Shield, and three features depend
 * on the same mechanism. There are two candidate explanations and they need
 * very different responses:
 *
 *  A. The launcher can't load a Lottie from the content:// URI a plugin can
 *     offer. Still images work that way, but Lottie is usually loaded by a
 *     different code path. If so, no amount of fixing the JSON helps and all
 *     three features have to go.
 *
 *  B. It loads fine, but embedded base64 image assets don't render. Our
 *     animations all depend on those. That would be worth working around.
 *
 * Two test files separate them. SHAPES_ONLY contains no assets array at all —
 * the same shape-only structure as the reference plugin's own sample, which is
 * known to work from an android.resource:// URI. SHAPES_AND_IMAGE adds a single
 * embedded image beside the shapes.
 *
 *  - Shapes-only animates       -> content:// is fine, explanation B
 *  - Shapes-only blank          -> explanation A, and the answer is video
 *  - Shapes animate, image absent -> confirms B directly
 */
object LottieSelfTest {

    private const val TAG = "LottieSelfTest"
    private const val PREFIX = "lottie_selftest_"

    private const val W = 1920
    private const val H = 1080
    private const val FPS = 24
    private const val DURATION = 48   // two seconds

    const val OFF = 0
    const val SHAPES_ONLY = 1
    const val SHAPES_AND_IMAGE = 2

    fun build(cacheDir: File, mode: Int): File? {
        if (mode == OFF) return null
        return try {
            val assets = JSONArray()
            val layers = JSONArray()

            // A bar sweeping left to right. Unmistakable when it works, and
            // unmistakable when it doesn't.
            layers.put(sweepingBar(1))
            // A static caption band, so a still-but-loaded file is
            // distinguishable from nothing loading at all.
            layers.put(staticBand(2))

            if (mode == SHAPES_AND_IMAGE) {
                assets.put(solidImageAsset())
                layers.put(imageLayer(3))
            }

            // Solid backdrop last, so it paints behind everything.
            layers.put(backdrop(if (mode == SHAPES_AND_IMAGE) 4 else 3))

            val root = JSONObject().apply {
                put("v", "5.7.0")
                put("fr", FPS)
                put("ip", 0)
                put("op", DURATION)
                put("w", W)
                put("h", H)
                put("nm", "Lottie self test")
                put("ddd", 0)
                // Only include an assets array when there is something in it:
                // an empty one is another difference from the reference sample.
                if (assets.length() > 0) put("assets", assets)
                put("layers", layers)
            }

            cacheDir.listFiles { f -> f.name.startsWith(PREFIX) }
                ?.forEach { runCatching { it.delete() } }

            val out = File(cacheDir, "$PREFIX${System.currentTimeMillis()}.json")
            out.writeText(root.toString())
            Log.i(TAG, "Self-test mode $mode written, ${out.length()} bytes")
            out
        } catch (t: Throwable) {
            Log.w(TAG, "Self-test build failed: ${t.message}")
            null
        }
    }

    // ------------------------------------------------------------------ parts

    private fun sweepingBar(ind: Int) = JSONObject().apply {
        put("ddd", 0); put("ind", ind); put("ty", 4); put("nm", "sweep")
        put("sr", 1); put("ip", 0); put("op", DURATION); put("st", 0); put("bm", 0)
        put("ao", 0)
        put("ks", JSONObject().apply {
            put("o", scalar(100)); put("r", scalar(0))
            put("p", JSONObject().apply {
                put("a", 1)
                put("k", JSONArray().apply {
                    put(JSONObject().apply {
                        put("t", 0)
                        put("s", JSONArray(listOf(-200.0, H / 2.0, 0.0)))
                        put("e", JSONArray(listOf(W + 200.0, H / 2.0, 0.0)))
                        put("i", ease()); put("o", ease())
                    })
                    put(JSONObject().apply { put("t", DURATION) })
                })
            })
            put("a", vector(0.0, 0.0)); put("s", vector(100.0, 100.0))
        })
        put("shapes", JSONArray().apply {
            put(JSONObject().apply {
                put("ty", "gr")
                put("it", JSONArray().apply {
                    put(rect(220.0, 220.0, 24.0))
                    put(fill(1.0, 0.85, 0.2))
                    put(transform())
                })
            })
        })
    }

    private fun staticBand(ind: Int) = JSONObject().apply {
        put("ddd", 0); put("ind", ind); put("ty", 4); put("nm", "band")
        put("sr", 1); put("ip", 0); put("op", DURATION); put("st", 0); put("bm", 0)
        put("ao", 0)
        put("ks", JSONObject().apply {
            put("o", scalar(100)); put("r", scalar(0))
            put("p", vector(W / 2.0, H * 0.18))
            put("a", vector(0.0, 0.0)); put("s", vector(100.0, 100.0))
        })
        put("shapes", JSONArray().apply {
            put(JSONObject().apply {
                put("ty", "gr")
                put("it", JSONArray().apply {
                    put(rect(W * 0.7, 90.0, 12.0))
                    put(fill(0.25, 0.75, 0.45))
                    put(transform())
                })
            })
        })
    }

    private fun backdrop(ind: Int) = JSONObject().apply {
        put("ddd", 0); put("ind", ind); put("ty", 4); put("nm", "backdrop")
        put("sr", 1); put("ip", 0); put("op", DURATION); put("st", 0); put("bm", 0)
        put("ao", 0)
        put("ks", JSONObject().apply {
            put("o", scalar(100)); put("r", scalar(0))
            put("p", vector(W / 2.0, H / 2.0))
            put("a", vector(0.0, 0.0)); put("s", vector(100.0, 100.0))
        })
        put("shapes", JSONArray().apply {
            put(JSONObject().apply {
                put("ty", "gr")
                put("it", JSONArray().apply {
                    put(rect(W * 1.1, H * 1.1, 0.0))
                    put(fill(0.09, 0.13, 0.20))
                    put(transform())
                })
            })
        })
    }

    /** A flat magenta block, so its absence is obvious. */
    private fun solidImageAsset(): JSONObject {
        val bmp = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.rgb(220, 40, 160))
        val bytes = ByteArrayOutputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray()
        }
        bmp.recycle()
        return JSONObject().apply {
            put("id", "probe")
            put("w", 480); put("h", 270); put("u", "")
            put("p", "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
            put("e", 1)
        }
    }

    private fun imageLayer(ind: Int) = JSONObject().apply {
        put("ddd", 0); put("ind", ind); put("ty", 2); put("nm", "probe")
        put("refId", "probe"); put("sr", 1)
        put("ip", 0); put("op", DURATION); put("st", 0); put("bm", 0)
        put("ks", JSONObject().apply {
            put("o", scalar(100)); put("r", scalar(0))
            put("p", vector(W / 2.0, H * 0.78))
            put("a", vector(240.0, 135.0)); put("s", vector(100.0, 100.0))
        })
    }

    private fun rect(w: Double, h: Double, r: Double) = JSONObject().apply {
        put("ty", "rc"); put("d", 1)
        put("s", vector(w, h)); put("p", vector(0.0, 0.0)); put("r", scalar(r))
    }

    private fun fill(r: Double, g: Double, b: Double) = JSONObject().apply {
        put("ty", "fl")
        put("c", JSONObject().apply { put("a", 0); put("k", JSONArray(listOf(r, g, b, 1.0))) })
        put("o", scalar(100)); put("r", 1)
    }

    private fun transform() = JSONObject().apply {
        put("ty", "tr")
        put("p", vector(0.0, 0.0)); put("a", vector(0.0, 0.0))
        put("s", vector(100.0, 100.0)); put("r", scalar(0)); put("o", scalar(100))
    }

    private fun ease() = JSONObject().apply {
        put("x", JSONArray(listOf(0.5))); put("y", JSONArray(listOf(0.5)))
    }

    private fun scalar(v: Number) = JSONObject().apply { put("a", 0); put("k", v) }

    private fun vector(x: Double, y: Double) = JSONObject().apply {
        put("a", 0); put("k", JSONArray(listOf(x, y, 0.0)))
    }
}
