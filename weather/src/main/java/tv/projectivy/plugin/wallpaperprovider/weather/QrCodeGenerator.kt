package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Generates a QR code as a plain Bitmap, for display in a Leanback guidance
 * icon or anywhere else a Bitmap is accepted.
 *
 * Generated at runtime from the destination URL rather than bundled as a
 * static image — the usual preference in this codebase for anything that can
 * be derived from a short piece of data rather than shipped as an asset, and
 * it means changing the destination later is a one-line string edit, not a
 * new image to produce and bundle.
 *
 * Requires com.google.zxing:core as a dependency — see UPLOAD-NOTES for the
 * one line to add to weather/build.gradle.kts.
 */
object QrCodeGenerator {

    private const val TAG = "QrCodeGenerator"

    /** Null on any failure — including simply not having the dependency. */
    fun generate(content: String, sizePx: Int): Bitmap? = try {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (t: Throwable) {
        // Deliberately quiet in normal use — this only ever fails if the
        // dependency is missing or the content is malformed, both of which
        // are build-time/setup problems rather than something that should
        // repeat on every settings screen open.
        Log.w(TAG, "QR generation failed: ${t.message}")
        null
    }
}
