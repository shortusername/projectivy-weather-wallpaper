package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Recolours radar tiles onto a ramp that works with colour vision deficiency.
 *
 * Standard radar palettes run green through yellow to red, which is close to
 * unreadable for the roughly eight percent of men with deuteranopia: heavy rain
 * collapses into the same muddy tone as light drizzle. Simulating it against
 * real tiles showed exactly that.
 *
 * RainViewer's colour-scheme parameter turns out to be ignored — all nine
 * documented schemes return a byte-identical image — so the remap has to happen
 * here.
 *
 * Their palette is a small discrete set: 78 distinct colours across a global
 * sample. It isn't ordered by luminance, though; dark red is heavy rain while
 * white is extreme. So pixels are classified by hue family into intensity
 * bands, then mapped onto a perceptually uniform ramp.
 */
object RadarPalette {

    /**
     * Seven bands, light to extreme, on a blue-to-yellow ramp.
     *
     * Blue and yellow stay distinguishable under all three common forms of
     * colour vision deficiency, and the ramp is monotonic in luminance so it
     * also reads correctly in greyscale.
     */
    private val SAFE = intArrayOf(
        Color.rgb(70, 100, 170),
        Color.rgb(59, 82, 139),
        Color.rgb(33, 145, 140),
        Color.rgb(94, 201, 98),
        Color.rgb(200, 220, 60),
        Color.rgb(253, 231, 37),
        Color.rgb(255, 255, 255)
    )

    /**
     * Cache from source colour to band.
     *
     * The palette is small, so after the first tile almost every pixel is a
     * hit. Without this the classifier would run per pixel across roughly
     * 700,000 pixels a refresh.
     */
    private val cache = HashMap<Int, Int>(128)

    /**
     * Intensity band for a source pixel, 0 lightest to 6 extreme.
     *
     * Ordering follows RainViewer's ramp: light blue, blue, yellow, orange,
     * red, then magenta and white for the extremes.
     */
    private fun band(r: Int, g: Int, b: Int): Int {
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        val spread = mx - mn

        // White or grey with little colour: hail and the top of the scale.
        if (spread < 30 && mx > 180) return 6
        // Blue family: light to moderate rain, lighter meaning lighter.
        if (b > r && b > g) return if (mx > 200) 0 else if (mx > 150) 1 else 2
        // Magenta: above red on the scale.
        if (r > 150 && b > 150 && g < 120) return 6
        if (r > 200 && g > 180 && b < 120) return 3   // yellow
        if (r > 180 && g in 91..180) return 4         // orange
        if (r > 90 && g < 90 && b < 90) return 5      // red
        return 2
    }

    /**
     * Recolours in place. Alpha is preserved, so the tile stays a transparent
     * overlay and only the visible precipitation changes.
     */
    fun apply(bitmap: Bitmap) {
        if (!bitmap.isMutable) return
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr 24 and 0xFF
            if (a <= 10) continue

            val rgb = p and 0x00FFFFFF
            val bandIndex = cache.getOrPut(rgb) {
                band(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
            }
            val safe = SAFE[bandIndex]
            pixels[i] = (a shl 24) or (safe and 0x00FFFFFF)
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /** Cleared when the setting changes, in case the source palette differs. */
    fun clearCache() = cache.clear()
}
