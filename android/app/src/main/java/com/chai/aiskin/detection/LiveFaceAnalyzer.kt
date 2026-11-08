package com.chai.aiskin.detection

import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector

/**
 * Real-time framing guidance for the camera preview (throttled).
 * Light level is estimated from the Y (luma) plane. Sharpness is not computed live.
 */
class LiveFaceAnalyzer(
    private val detector: FaceDetector,
    private val intervalMs: Long = 350,
    private val onResult: (FaceValidation) -> Unit,
) : ImageAnalysis.Analyzer {

    private var lastRun = 0L
    @Volatile private var busy = false

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val media = image.image
        if (busy || media == null || now - lastRun < intervalMs) {
            image.close()
            return
        }
        busy = true
        lastRun = now
        val rotation = image.imageInfo.rotationDegrees
        val upright = rotation == 90 || rotation == 270
        val w = if (upright) image.height else image.width
        val h = if (upright) image.width else image.height
        val light = lumaFromYPlane(image)

        detector.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { faces ->
                onResult(FaceQualityRules.evaluate(faces.map { it.toDetected() }, w, h, light))
            }
            .addOnCompleteListener {
                busy = false
                image.close()
            }
    }

    private fun lumaFromYPlane(image: ImageProxy): LightMetrics? = runCatching {
        val plane = image.planes[0]
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        var sum = 0L
        var clipped = 0
        var n = 0
        val step = 8
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val idx = y * rowStride + x * pixelStride
                if (idx < buf.limit()) {
                    val v = buf.get(idx).toInt() and 0xFF
                    sum += v
                    if (v >= 250) clipped++
                    n++
                }
                x += step
            }
            y += step
        }
        if (n == 0) null else LightMetrics(sum.toDouble() / n, clipped.toDouble() / n, null)
    }.getOrNull()
}
