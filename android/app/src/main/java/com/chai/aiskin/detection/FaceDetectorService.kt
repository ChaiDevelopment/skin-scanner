package com.chai.aiskin.detection

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * ML Kit face *detection* (where is a face?) used only to validate photo framing.
 * No landmarks, no classification, no face recognition / identity features.
 */
@Singleton
class FaceDetectorService @Inject constructor() {

    private val accurate: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setMinFaceSize(0.1f)
                .build(),
        )
    }

    val fastDetector: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.15f)
                .build(),
        )
    }

    /** Validates a captured, already upright bitmap: detection + light + sharpness on the face region. */
    suspend fun validate(bitmap: Bitmap): FaceValidation {
        val faces = accurate.process(InputImage.fromBitmap(bitmap, 0)).await().map { it.toDetected() }
        val main = faces.maxByOrNull { it.box.width * it.box.height }
        val light = main?.let { faceRegionMetrics(bitmap, it.box) } ?: wholeFrameMetrics(bitmap)
        return FaceQualityRules.evaluate(faces, bitmap.width, bitmap.height, light)
    }

    private fun faceRegionMetrics(bitmap: Bitmap, box: FaceBox): LightMetrics {
        val rect = Rect(
            max(0, box.left.toInt()),
            max(0, box.top.toInt()),
            min(bitmap.width, box.right.toInt()),
            min(bitmap.height, box.bottom.toInt()),
        )
        if (rect.width() < 8 || rect.height() < 8) return wholeFrameMetrics(bitmap)
        val crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        // Normalize scale so blur thresholds are resolution independent.
        val targetW = 256
        val targetH = max(8, (crop.height * targetW.toFloat() / crop.width).toInt())
        val scaled = Bitmap.createScaledBitmap(crop, targetW, targetH, true)
        val px = IntArray(targetW * targetH).also { scaled.getPixels(it, 0, targetW, 0, 0, targetW, targetH) }
        if (crop !== bitmap) crop.recycle()
        scaled.recycle()
        val gray = ImageQualityMetrics.toGray(px)
        return ImageQualityMetrics.lightMetrics(gray, targetW, targetH, includeSharpness = true)
    }

    private fun wholeFrameMetrics(bitmap: Bitmap): LightMetrics {
        val w = 128
        val h = max(8, (bitmap.height * w.toFloat() / bitmap.width).toInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val px = IntArray(w * h).also { scaled.getPixels(it, 0, w, 0, 0, w, h) }
        scaled.recycle()
        return ImageQualityMetrics.lightMetrics(ImageQualityMetrics.toGray(px), w, h, includeSharpness = false)
    }

    fun close() {
        accurate.close()
        fastDetector.close()
    }
}

fun Face.toDetected() = DetectedFace(
    box = FaceBox(boundingBox.left.toFloat(), boundingBox.top.toFloat(), boundingBox.right.toFloat(), boundingBox.bottom.toFloat()),
    yaw = headEulerAngleY,
    pitch = headEulerAngleX,
    roll = headEulerAngleZ,
)
