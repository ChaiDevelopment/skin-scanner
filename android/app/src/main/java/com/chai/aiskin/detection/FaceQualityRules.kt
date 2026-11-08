package com.chai.aiskin.detection

import kotlin.math.abs

/** Axis-aligned face bounds in the upright image coordinate space. */
data class FaceBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
}

data class DetectedFace(
    val box: FaceBox,
    /** Head rotation in degrees from ML Kit: Y = left/right turn, X = up/down tilt, Z = in-plane roll. */
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
)

data class LightMetrics(
    /** Mean luma 0..255 over the face region (or whole frame). */
    val meanLuma: Double,
    /** Fraction of pixels ≥ 250 (blown highlights). */
    val clippedFraction: Double,
    /** Variance of Laplacian over the face region; null when not computed (live preview). */
    val sharpness: Double?,
)

enum class FaceIssue(val message: String, val blocking: Boolean) {
    NO_FACE("Wajah tidak terdeteksi. Posisikan wajah di tengah kamera.", true),
    MULTIPLE_FACES("Terdeteksi lebih dari satu wajah. Pastikan hanya ada satu wajah di kamera.", true),
    FACE_TOO_FAR("Wajah terlalu jauh. Dekatkan kamera ke wajah Anda.", true),
    FACE_PARTIALLY_OUTSIDE("Sebagian wajah berada di luar bingkai. Posisikan wajah di tengah kamera.", true),
    EXCESSIVE_ANGLE("Wajah terlalu miring. Hadapkan wajah lurus ke kamera.", true),
    TOO_DARK("Foto terlalu gelap. Gunakan pencahayaan yang cukup.", true),
    OVEREXPOSED("Foto terlalu terang atau silau. Hindari cahaya langsung yang terlalu kuat.", true),
    BLURRY("Foto buram. Tahan ponsel dengan stabil dan pastikan wajah terlihat jelas.", true),
    FACE_TOO_SMALL("Wajah tampak kecil. Dekatkan sedikit agar detail kulit lebih terlihat.", false),
    NOT_CENTERED("Geser sedikit agar wajah berada di tengah.", false),
    SLIGHTLY_BLURRY("Foto sedikit kurang tajam. Hasil analisis mungkin kurang detail.", false),
    UNEVEN_LIGHT("Pencahayaan agak redup. Cahaya yang lebih terang akan membantu.", false),
}

data class FaceValidation(val issues: List<FaceIssue>, val face: DetectedFace?) {
    val blockingIssues get() = issues.filter { it.blocking }
    val warnings get() = issues.filterNot { it.blocking }
    val isAcceptable get() = blockingIssues.isEmpty()
    val primaryIssue: FaceIssue? get() = blockingIssues.firstOrNull() ?: warnings.firstOrNull()
}

/**
 * Pure decision rules — no Android dependencies, fully unit-tested.
 * This only checks that a usable face photo was taken. It is NOT skin analysis
 * and NOT identity recognition.
 */
object FaceQualityRules {
    const val MIN_FACE_AREA_RATIO = 0.05f      // below → too far
    const val GOOD_FACE_WIDTH_RATIO = 0.38f    // below → "a bit small" warning
    const val EDGE_TOLERANCE = 0.02f
    const val MAX_YAW = 18f
    const val MAX_PITCH = 20f
    const val MAX_ROLL = 15f
    const val CENTER_TOLERANCE = 0.18f
    const val MIN_LUMA = 55.0
    const val DIM_LUMA = 85.0
    const val MAX_LUMA = 215.0
    const val MAX_CLIPPED = 0.20
    const val BLUR_BLOCKING = 18.0
    const val BLUR_WARNING = 45.0

    fun evaluate(faces: List<DetectedFace>, imageWidth: Int, imageHeight: Int, light: LightMetrics?): FaceValidation {
        if (faces.isEmpty()) return FaceValidation(listOf(FaceIssue.NO_FACE), null)
        if (faces.size > 1) {
            // Ignore tiny background faces (e.g. a poster) if one face clearly dominates.
            val sorted = faces.sortedByDescending { it.box.width * it.box.height }
            val main = sorted[0].box.width * sorted[0].box.height
            val second = sorted[1].box.width * sorted[1].box.height
            if (second > main * 0.25f) return FaceValidation(listOf(FaceIssue.MULTIPLE_FACES), null)
        }
        val face = faces.maxBy { it.box.width * it.box.height }
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        val issues = mutableListOf<FaceIssue>()

        val areaRatio = (face.box.width * face.box.height) / (w * h)
        if (areaRatio < MIN_FACE_AREA_RATIO) issues += FaceIssue.FACE_TOO_FAR

        val outside = face.box.left < -EDGE_TOLERANCE * w || face.box.top < -EDGE_TOLERANCE * h ||
            face.box.right > (1 + EDGE_TOLERANCE) * w || face.box.bottom > (1 + EDGE_TOLERANCE) * h
        if (outside) issues += FaceIssue.FACE_PARTIALLY_OUTSIDE

        if (abs(face.yaw) > MAX_YAW || abs(face.pitch) > MAX_PITCH || abs(face.roll) > MAX_ROLL) {
            issues += FaceIssue.EXCESSIVE_ANGLE
        }

        if (light != null) {
            when {
                light.meanLuma < MIN_LUMA -> issues += FaceIssue.TOO_DARK
                light.meanLuma > MAX_LUMA || light.clippedFraction > MAX_CLIPPED -> issues += FaceIssue.OVEREXPOSED
                light.meanLuma < DIM_LUMA -> issues += FaceIssue.UNEVEN_LIGHT
            }
            light.sharpness?.let { s ->
                if (s < BLUR_BLOCKING) issues += FaceIssue.BLURRY
                else if (s < BLUR_WARNING) issues += FaceIssue.SLIGHTLY_BLURRY
            }
        }

        if (FaceIssue.FACE_TOO_FAR !in issues && face.box.width / w < GOOD_FACE_WIDTH_RATIO) issues += FaceIssue.FACE_TOO_SMALL
        if (FaceIssue.FACE_PARTIALLY_OUTSIDE !in issues &&
            (abs(face.box.centerX / w - 0.5f) > CENTER_TOLERANCE || abs(face.box.centerY / h - 0.5f) > CENTER_TOLERANCE)
        ) issues += FaceIssue.NOT_CENTERED

        return FaceValidation(issues.sortedBy { it.ordinal }, face)
    }
}
