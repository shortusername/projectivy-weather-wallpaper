package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

/**
 * Animated precipitation over a still background, as a Lottie file.
 *
 * The launcher renders one file, so animation has to ship inside it. The radar
 * loop solves that with pre-rendered bitmap frames, which is why it costs about
 * a megabyte. Precipitation doesn't need bitmaps: rain and snow are simple
 * shapes on linear paths, and Lottie animates vector layers natively. The
 * result is a few hundred kilobytes of JSON, smooth rather than a short cycle,
 * and resolution-independent.
 *
 * The background scene is embedded once as an image; everything moving is
 * vector.
 *
 * Seamless looping works by giving every particle the same travel distance —
 * exactly the loop distance — so when the composition restarts, each particle
 * lands where its neighbour was. Uniform distribution does the rest.
 */
object PrecipitationAnimator {

    private const val TAG = "PrecipAnimator"
    private const val OUTPUT_PREFIX = "precip_loop_"

    private const val W = 1920
    private const val H = 1080
    private const val FPS = 24

    /** Particles travel this far, which is screen height plus a margin. */
    private const val LOOP_DISTANCE = H + 240

    /** Quality for the embedded still. Same reasoning as the radar loop. */
    private const val BASE_QUALITY = 82

    /**
     * Particle counts are capped around 150.
     *
     * The generated file is small either way — the cost is lottie-android
     * animating that many transforms per frame on a TV box, not the bytes. 150
     * simple layers is a conservative ceiling given how little headroom these
     * devices turned out to have with the radar loop.
     */
    private class Spec(
        val count: Int,
        /** Loop length in frames. Longer means slower fall. */
        val durationFrames: Int,
        val kind: Kind,
        val lean: Float = 0f,
        val flash: Boolean = false
    )

    private enum class Kind { STREAK, FLAKE, BAND }

    /**
     * Null for conditions with nothing falling — the caller then renders the
     * ordinary still wallpaper rather than an animation of nothing.
     */
    private fun specFor(code: Int): Spec? = when (code) {
        // Drizzle: sparse, short, slow.
        51, 53, 55, 56, 57 -> Spec(70, 64, Kind.STREAK, lean = 0.10f)
        // Rain, with the heavier codes denser and faster.
        61, 66, 80 -> Spec(105, 52, Kind.STREAK, lean = 0.16f)
        63, 81 -> Spec(130, 44, Kind.STREAK, lean = 0.20f)
        65, 67, 82 -> Spec(150, 36, Kind.STREAK, lean = 0.26f)
        // Snow: slower, larger, drifting.
        71, 77, 85 -> Spec(80, 150, Kind.FLAKE, lean = 0.12f)
        73 -> Spec(110, 130, Kind.FLAKE, lean = 0.14f)
        75, 86 -> Spec(140, 110, Kind.FLAKE, lean = 0.18f)
        // Thunderstorms: heavy rain plus lightning flashes.
        95 -> Spec(130, 38, Kind.STREAK, lean = 0.22f, flash = true)
        96, 99 -> Spec(150, 32, Kind.STREAK, lean = 0.26f, flash = true)
        // Fog: drifting horizontal bands rather than particles.
        45, 48 -> Spec(7, 300, Kind.BAND)
        else -> null
    }

    fun isAnimatable(code: Int): Boolean = specFor(code) != null

    fun build(cacheDir: File, base: Bitmap, weatherCode: Int, isDay: Boolean): File? {
        val spec = specFor(weatherCode) ?: return null
        return try {
            // Seeded per condition so the layout is stable between refreshes
            // rather than reshuffling every fifteen minutes.
            val rng = Random(weatherCode * 7919L)

            val assets = JSONArray().apply { put(baseAsset(base)) }
            val layers = JSONArray()

            // Particles first in the array, so they paint above the background:
            // Lottie draws index 0 last... in practice it draws in array order
            // with earlier entries on top, matching the radar loop's layering.
            var ind = 1
            repeat(spec.count) {
                layers.put(particleLayer(ind++, spec, rng, isDay))
            }
            if (spec.flash) {
                layers.put(flashLayer(ind++, spec.durationFrames, rng))
            }
            layers.put(baseLayer(ind, spec.durationFrames))

            val root = JSONObject().apply {
                put("v", "5.7.0")
                put("fr", FPS)
                put("ip", 0)
                put("op", spec.durationFrames)
                put("w", W)
                put("h", H)
                put("nm", "Precipitation")
                put("ddd", 0)
                put("assets", assets)
                put("layers", layers)
            }

            cacheDir.listFiles { f -> f.name.startsWith(OUTPUT_PREFIX) }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(1)
                ?.forEach { runCatching { it.delete() } }

            val out = File(cacheDir, "$OUTPUT_PREFIX${System.currentTimeMillis()}.json")
            out.writeText(root.toString())
            Log.i(
                TAG,
                "Built ${spec.kind} x${spec.count}, ${spec.durationFrames}f, " +
                        "${out.length() / 1024} KB"
            )
            out
        } catch (t: Throwable) {
            Log.w(TAG, "Precipitation build failed: ${t.message}")
            null
        }
    }

    // ------------------------------------------------------------------ parts

    @Suppress("DEPRECATION")
    private fun baseAsset(bitmap: Bitmap): JSONObject {
        val bytes = ByteArrayOutputStream().use { stream ->
            val format =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                    Bitmap.CompressFormat.WEBP_LOSSY
                else
                    Bitmap.CompressFormat.WEBP
            bitmap.compress(format, BASE_QUALITY, stream)
            stream.toByteArray()
        }
        return JSONObject().apply {
            put("id", "scene")
            put("w", bitmap.width)
            put("h", bitmap.height)
            put("u", "")
            put("p", "data:image/webp;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
            put("e", 1)
        }
    }

    private fun baseLayer(ind: Int, duration: Int) = JSONObject().apply {
        put("ddd", 0); put("ind", ind); put("ty", 2); put("nm", "scene")
        put("refId", "scene"); put("sr", 1)
        put("ip", 0); put("op", duration); put("st", 0); put("bm", 0)
        put("ks", JSONObject().apply {
            put("o", scalar(100)); put("r", scalar(0))
            put("p", vector(W / 2.0, H / 2.0))
            put("a", vector(W / 2.0, H / 2.0))
            put("s", vector(100.0, 100.0))
        })
    }

    /**
     * One falling particle.
     *
     * Position is a single linear keyframe pair spanning the whole composition,
     * travelling exactly LOOP_DISTANCE. Every particle covering the same
     * distance in the same time is what makes the loop seamless.
     */
    private fun particleLayer(ind: Int, spec: Spec, rng: Random, isDay: Boolean): JSONObject {
        val depth = rng.nextFloat()

        return when (spec.kind) {
            Kind.BAND -> {
                // Fog: a wide soft bar drifting sideways.
                val y = H * (0.34f + ind * 0.085f)
                val height = 26.0 + rng.nextDouble() * 44.0
                val startX = -W * 0.3 + rng.nextDouble() * W * 0.4
                shapeLayer(
                    ind = ind,
                    shape = roundedRect(W * 1.6, height, height / 2),
                    colour = Triple(0.86, 0.89, 0.93),
                    opacity = 16 + (depth * 16).toInt(),
                    from = doubleArrayOf(startX, y.toDouble()),
                    to = doubleArrayOf(startX + W * 0.6, y.toDouble() + 18),
                    duration = spec.durationFrames,
                    rotation = 0.0
                )
            }
            Kind.FLAKE -> {
                val r = 2.0 + depth * 7.0
                val x = rng.nextDouble() * W
                val y0 = rng.nextDouble() * LOOP_DISTANCE - 120
                val drift = (rng.nextDouble() - 0.5) * W * 0.10 + spec.lean * W * 0.10
                shapeLayer(
                    ind = ind,
                    shape = ellipse(r * 2, r * 2),
                    colour = Triple(1.0, 1.0, 1.0),
                    opacity = 34 + (depth * 52).toInt(),
                    from = doubleArrayOf(x, y0),
                    to = doubleArrayOf(x + drift, y0 + LOOP_DISTANCE),
                    duration = spec.durationFrames,
                    rotation = 0.0
                )
            }
            Kind.STREAK -> {
                val len = 16.0 + depth * 30.0
                val thickness = 1.4 + depth * 2.0
                val x = rng.nextDouble() * (W * 1.2) - W * 0.1
                val y0 = rng.nextDouble() * LOOP_DISTANCE - 160
                val dx = spec.lean * LOOP_DISTANCE
                // Tilt the streak to match its travel direction.
                val angle = Math.toDegrees(Math.atan2(dx, LOOP_DISTANCE.toDouble()))
                val tone = if (isDay) Triple(0.86, 0.92, 0.98) else Triple(0.72, 0.82, 0.94)
                shapeLayer(
                    ind = ind,
                    shape = roundedRect(thickness, len, thickness / 2),
                    colour = tone,
                    opacity = 26 + (depth * 44).toInt(),
                    from = doubleArrayOf(x, y0),
                    to = doubleArrayOf(x + dx, y0 + LOOP_DISTANCE),
                    duration = spec.durationFrames,
                    rotation = -angle
                )
            }
        }
    }

    /**
     * Lightning: a pale full-frame layer whose opacity spikes briefly.
     *
     * Two flashes per loop at irregular points, so it doesn't read as a metronome.
     */
    private fun flashLayer(ind: Int, duration: Int, rng: Random): JSONObject {
        val first = (duration * (0.18 + rng.nextDouble() * 0.12)).toInt()
        val second = (duration * (0.62 + rng.nextDouble() * 0.14)).toInt()

        val keys = JSONArray()
        fun hold(t: Int, v: Int) {
            keys.put(JSONObject().apply {
                put("t", t); put("s", JSONArray(listOf(v))); put("e", JSONArray(listOf(v)))
                put("i", ease()); put("o", ease())
            })
        }
        fun ramp(t: Int, from: Int, to: Int) {
            keys.put(JSONObject().apply {
                put("t", t); put("s", JSONArray(listOf(from))); put("e", JSONArray(listOf(to)))
                put("i", ease()); put("o", ease())
            })
        }
        ramp(0, 0, 0)
        ramp(first, 0, 30)
        ramp(first + 2, 30, 0)
        ramp(second, 0, 22)
        ramp(second + 3, 22, 0)
        hold(duration, 0)

        return JSONObject().apply {
            put("ddd", 0); put("ind", ind); put("ty", 4); put("nm", "flash")
            put("sr", 1); put("ip", 0); put("op", duration); put("st", 0); put("bm", 0)
            put("ao", 0)
            put("ks", JSONObject().apply {
                put("o", JSONObject().apply { put("a", 1); put("k", keys) })
                put("r", scalar(0))
                put("p", vector(W / 2.0, H / 2.0))
                put("a", vector(0.0, 0.0))
                put("s", vector(100.0, 100.0))
            })
            put("shapes", JSONArray().apply {
                put(JSONObject().apply {
                    put("ty", "gr")
                    put("it", JSONArray().apply {
                        put(roundedRect(W * 1.2, H * 1.2, 0.0))
                        put(fill(Triple(0.87, 0.90, 1.0)))
                        put(transform())
                    })
                })
            })
        }
    }

    // ------------------------------------------------------------- primitives

    private fun shapeLayer(
        ind: Int,
        shape: JSONObject,
        colour: Triple<Double, Double, Double>,
        opacity: Int,
        from: DoubleArray,
        to: DoubleArray,
        duration: Int,
        rotation: Double
    ) = JSONObject().apply {
        put("ddd", 0); put("ind", ind); put("ty", 4); put("nm", "p$ind")
        put("sr", 1); put("ip", 0); put("op", duration); put("st", 0); put("bm", 0)
        put("ao", 0)
        put("ks", JSONObject().apply {
            put("o", scalar(opacity))
            put("r", scalar(rotation))
            put("p", JSONObject().apply {
                put("a", 1)
                put("k", JSONArray().apply {
                    put(JSONObject().apply {
                        put("t", 0)
                        put("s", JSONArray(listOf(from[0], from[1], 0.0)))
                        put("e", JSONArray(listOf(to[0], to[1], 0.0)))
                        put("i", ease()); put("o", ease())
                    })
                    put(JSONObject().apply { put("t", duration) })
                })
            })
            put("a", vector(0.0, 0.0))
            put("s", vector(100.0, 100.0))
        })
        put("shapes", JSONArray().apply {
            put(JSONObject().apply {
                put("ty", "gr")
                put("it", JSONArray().apply {
                    put(shape)
                    put(fill(colour))
                    put(transform())
                })
            })
        })
    }

    private fun ellipse(w: Double, h: Double) = JSONObject().apply {
        put("ty", "el"); put("d", 1)
        put("s", vector(w, h))
        put("p", vector(0.0, 0.0))
    }

    private fun roundedRect(w: Double, h: Double, radius: Double) = JSONObject().apply {
        put("ty", "rc"); put("d", 1)
        put("s", vector(w, h))
        put("p", vector(0.0, 0.0))
        put("r", scalar(radius))
    }

    private fun fill(c: Triple<Double, Double, Double>) = JSONObject().apply {
        put("ty", "fl")
        put("c", JSONObject().apply {
            put("a", 0); put("k", JSONArray(listOf(c.first, c.second, c.third, 1.0)))
        })
        put("o", scalar(100))
        put("r", 1)
    }

    private fun transform() = JSONObject().apply {
        put("ty", "tr")
        put("p", vector(0.0, 0.0))
        put("a", vector(0.0, 0.0))
        put("s", vector(100.0, 100.0))
        put("r", scalar(0))
        put("o", scalar(100))
    }

    /** Linear easing, expressed the way the Lottie schema expects. */
    private fun ease() = JSONObject().apply {
        put("x", JSONArray(listOf(0.5)))
        put("y", JSONArray(listOf(0.5)))
    }

    private fun scalar(v: Number) = JSONObject().apply { put("a", 0); put("k", v) }

    private fun vector(x: Double, y: Double) = JSONObject().apply {
        put("a", 0); put("k", JSONArray(listOf(x, y, 0.0)))
    }
}
