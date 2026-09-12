package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Encodes an H.264 MP4 on the device, frame by frame.
 *
 * Why this exists: Lottie can't be used for raster animation here. Testing on
 * an Nvidia Shield showed that shape layers render and animate correctly, but
 * embedded image assets don't display at all — not a format problem, since the
 * probe image was PNG. Radar is inherently raster, so no amount of fixing the
 * JSON helps.
 *
 * Video takes a completely different path: the launcher hands the URI to a media
 * player, which reads content:// natively. Video wallpaper packs already work,
 * so the transport is proven. Everything can be burned into the frames — radar,
 * the vector map, the panel, the alert banner — with nothing lost.
 *
 * MediaCodec and MediaMuxer are both API 18, comfortably below this app's
 * minimum of 23.
 *
 * Note ByteBuffer input rather than an input Surface: you can't lockCanvas on a
 * codec's input surface, and a GL path would be far more machinery than a
 * manual colour conversion.
 */
object VideoEncoder {

    private const val TAG = "VideoEncoder"
    private const val MIME = "video/avc"
    private const val PREFIX = "wallpaper_video_"

    /** 720p upscales acceptably for radar and encodes several times faster. */
    const val WIDTH = 1280
    const val HEIGHT = 720

    private const val FPS = 8
    private const val BITRATE = 3_000_000
    // Deliberately longer than any loop we ever produce (radar's worst case
    // is ~4.9s), so the whole clip is a single GOP with one keyframe at the
    // very start — one SPS emission, not one per second.
    //
    // A real device log caught the actual failure this was chosen to prevent:
    // a specific vendor decoder (OMX.MS.AVC.Decoder, an embedded/budget SoC)
    // hit OMX_EventPortSettingsChanged partway through playback, failed to
    // renegotiate its output buffer count for either of the two counts
    // offered, spent several seconds trying to recover, and then explicitly
    // set its own internal "blackScreenInfo.bEnable" flag and gave up —
    // deliberate, not a crash. A repeated SPS at every keyframe, once a
    // second, is the most plausible trigger for that renegotiation event on a
    // stream that never changes resolution or any other decode-relevant
    // property. Removing the repetition removes the trigger.
    private const val KEYFRAME_INTERVAL_S = 10
    private const val TIMEOUT_US = 10_000L

    /**
     * Encodes frames supplied on demand.
     *
     * [frameCount] frames are requested one at a time through [produce], which
     * should draw into the supplied bitmap. Nothing is retained between frames,
     * so peak memory is a single frame regardless of length — which matters,
     * given the memory trouble the Lottie approach ran into.
     *
     * Each source frame is held on screen for [holdFrames] encoded frames.
     */
    fun encode(
        cacheDir: File,
        frameCount: Int,
        holdFrames: Int = 4,
        produce: (index: Int, target: Bitmap) -> Boolean
    ): File? {
        if (frameCount <= 0) return null

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var frame: Bitmap? = null
        val out = File(cacheDir, "$PREFIX${System.currentTimeMillis()}.mp4")

        try {
            cacheDir.listFiles { f -> f.name.startsWith(PREFIX) }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(1)
                ?.forEach { runCatching { it.delete() } }

            codec = MediaCodec.createEncoderByType(MIME)

            // The colour format has to be chosen from what this device's encoder
            // actually supports, and it must be known before configure().
            val supported = codec.codecInfo.getCapabilitiesForType(MIME).colorFormats
            val colorFormat = when {
                supported.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                supported.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                supported.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                else -> {
                    Log.w(TAG, "No usable YUV420 colour format; encoder offers ${supported.toList()}")
                    return null
                }
            }
            val semiPlanar =
                colorFormat != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar

            // Request Baseline profile when the device's own encoder actually
            // offers it. We never set a profile at all before this, which
            // left every device encoding at whatever profile its encoder
            // defaults to — often Main or High, chosen for better
            // compression. Some weaker or older hardware decoders only
            // support Baseline, which creates a real, silent failure mode:
            // the SAME device's encoder can produce a file its OWN decoder
            // then can't play back, since encode and decode capability aren't
            // guaranteed to match on cheaper hardware. Only requesting it when
            // it's confirmed present avoids trading that failure for a
            // different one on hardware that can't encode Baseline at all.
            val profiles = codec.codecInfo.getCapabilitiesForType(MIME).profileLevels
            val baselineSupported = profiles.any {
                it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
            }

            // Level is deliberately NOT requested, reversing the previous
            // attempt. A real device log showed the actual failure: a decoder
            // rejecting an output-buffer-count renegotiation outright — the
            // same BadParameter error for two different counts offered, which
            // reads as a flat refusal rather than "this specific count is too
            // high." Level is exactly what governs how much buffer capacity a
            // decoder must support (via the H.264 MaxDpbMbs table), so explicitly
            // requesting Level 3.1 is a real candidate for having caused or
            // worsened this, not fixed it — the failure shifted by one frame
            // after that request was added, not away. Left unset, as it was
            // before that attempt, so the encoder picks whatever its own
            // hardware defaults to.
            val format = MediaFormat.createVideoFormat(MIME, WIDTH, HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEYFRAME_INTERVAL_S)

                // No B-frames, unconditionally. B-frames reference both past
                // and future frames, which costs more decoder-side reference
                // buffer capacity than a simple I/P sequence — a plausible
                // reason playback would degrade partway through a loop
                // specifically on weaker hardware rather than failing
                // immediately. This key is honoured broadly enough to set
                // regardless of which profile ends up being used, unlike
                // profile or level, which do need the defensive check above.
                setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)

                if (baselineSupported) {
                    setInteger(
                        MediaFormat.KEY_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
                    )
                }
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false

            frame = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val argb = IntArray(WIDTH * HEIGHT)
            val yuv = ByteArray(WIDTH * HEIGHT * 3 / 2)
            val info = MediaCodec.BufferInfo()

            var encodedFrames = 0
            var sourceIndex = 0
            var holdRemaining = 0
            var endOfInput = false

            while (true) {
                if (!endOfInput) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        if (holdRemaining == 0) {
                            if (sourceIndex >= frameCount) {
                                codec.queueInputBuffer(
                                    inputIndex, 0, 0,
                                    ptsFor(encodedFrames),
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                endOfInput = true
                            } else {
                                if (!produce(sourceIndex, frame)) {
                                    // Producer gave up; end cleanly with what we have.
                                    codec.queueInputBuffer(
                                        inputIndex, 0, 0,
                                        ptsFor(encodedFrames),
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    endOfInput = true
                                    continue
                                }
                                frame.getPixels(argb, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
                                argbToYuv420(argb, yuv, WIDTH, HEIGHT, semiPlanar)
                                sourceIndex++
                                holdRemaining = holdFrames
                            }
                        }
                        if (!endOfInput) {
                            val buffer: ByteBuffer = codec.getInputBuffer(inputIndex)
                                ?: return null
                            buffer.clear()
                            buffer.put(yuv)
                            codec.queueInputBuffer(
                                inputIndex, 0, yuv.size, ptsFor(encodedFrames), 0
                            )
                            encodedFrames++
                            holdRemaining--
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> if (endOfInput) continue
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    else -> {
                        if (outputIndex < 0) continue
                        val encoded = codec.getOutputBuffer(outputIndex)
                        if (encoded != null && info.size > 0 && muxerStarted &&
                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, encoded, info)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            }

            Log.i(TAG, "Encoded $encodedFrames frames -> ${out.length() / 1024} KB")
            return if (out.length() > 0) out else null
        } catch (t: Throwable) {
            Log.w(TAG, "Encode failed: ${t.message}")
            runCatching { out.delete() }
            return null
        } finally {
            frame?.recycle()
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    private fun ptsFor(frameIndex: Int): Long =
        frameIndex.toLong() * 1_000_000L / FPS

    /**
     * ARGB to YUV420, either semi-planar (NV12) or fully planar (I420).
     *
     * BT.601 coefficients, which is what H.264 at this resolution expects.
     * Chroma is subsampled by taking every second pixel of every second row
     * rather than averaging — cheaper, and imperceptible on radar imagery.
     */
    private fun argbToYuv420(
        argb: IntArray,
        yuv: ByteArray,
        width: Int,
        height: Int,
        semiPlanar: Boolean
    ) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize

        for (row in 0 until height) {
            for (col in 0 until width) {
                val p = argb[row * width + col]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (row % 2 == 0 && col % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    if (semiPlanar) {
                        yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                        yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                    } else {
                        // Planar keeps the two chroma planes separate.
                        val uPlane = frameSize
                        val vPlane = frameSize + frameSize / 4
                        val offset = (row / 2) * (width / 2) + (col / 2)
                        yuv[uPlane + offset] = u.coerceIn(0, 255).toByte()
                        yuv[vPlane + offset] = v.coerceIn(0, 255).toByte()
                    }
                }
            }
        }
    }
}
