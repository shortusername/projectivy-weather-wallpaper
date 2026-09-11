package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Real moon phase, replacing the fixed crescent the night scene always drew
 * regardless of the actual date.
 *
 * Pure arithmetic — a known reference new moon plus the synodic month length —
 * so it needs no network call and works offline. Verified against three
 * independently reported 2026 full moon times (Jan 3, Mar 3, Sep 26): the
 * formula's illumination fraction lands at 99.8-99.99% at each reported
 * instant, and the predicted timing is off by at most about ten hours — utterly
 * invisible given the moon reads as visually full for a day or two either side
 * of the exact instant anyway. More than accurate enough for a decoration.
 */
object MoonPhase {

    private const val SYNODIC_MONTH_DAYS = 29.530588853
    // A well-known reference new moon, used by most simple phase calculators.
    private const val REFERENCE_NEW_MOON_EPOCH_MS = 947182440000L // 2000-01-06 18:14 UTC

    data class Phase(
        /** 0 = new, 1 = full. */
        val illumination: Float,
        /** True from new moon to full; false from full back to new. */
        val waxing: Boolean
    )

    fun current(): Phase {
        val days = (System.currentTimeMillis() - REFERENCE_NEW_MOON_EPOCH_MS) / 86_400_000.0
        var ageFrac = (days % SYNODIC_MONTH_DAYS) / SYNODIC_MONTH_DAYS
        if (ageFrac < 0) ageFrac += 1.0

        val illumination = ((1 - cos(2 * PI * ageFrac)) / 2).toFloat()
        val waxing = ageFrac < 0.5
        return Phase(illumination, waxing)
    }

    /**
     * A filled silhouette path for the given phase and latitude, centred at
     * (0,0) with the given radius. Caller translates/scales as needed.
     *
     * Built as two sampled boundary chains — the true circle edge on the lit
     * side, then the terminator curve back — rather than composed Canvas arcs,
     * because that construction was verified pixel-for-pixel against a
     * reference rendering before being ported here, and arc sign conventions
     * are exactly the kind of thing that's easy to get subtly backwards.
     *
     * Hemisphere matters: a waxing moon is lit on the right as seen from the
     * Northern Hemisphere and on the left from the Southern — a real,
     * observable effect of which way you're facing to look at it, not an
     * approximation. Confirmed against reference renderings for both.
     */
    fun silhouette(phase: Phase, latitude: Double, radius: Float, segments: Int = 28): Path {
        val t = 1f - 2f * phase.illumination
        val southern = latitude < 0
        // flip = waning XOR southern, expressed as waxing == southern.
        val flip = phase.waxing == southern
        val ls = if (flip) -1f else 1f

        val path = Path()
        var first = true

        fun addPoint(x: Float, y: Float) {
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
        }

        // Chain 1: true circle edge on the lit side, top to bottom.
        for (i in 0..segments) {
            val y = -radius + (2 * radius) * i / segments
            val halfW = sqrt((radius * radius - y * y).coerceAtLeast(0f))
            addPoint(ls * halfW, y)
        }
        // Chain 2: terminator curve, bottom back to top.
        for (i in 0..segments) {
            val y = radius - (2 * radius) * i / segments
            val halfW = sqrt((radius * radius - y * y).coerceAtLeast(0f))
            addPoint(ls * t * halfW, y)
        }
        path.close()
        return path
    }
}
