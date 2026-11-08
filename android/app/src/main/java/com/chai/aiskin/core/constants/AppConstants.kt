package com.chai.aiskin.core.constants

object AppConstants {
    /** Longest edge of the photo sent to the backend. */
    const val UPLOAD_MAX_DIMENSION_PX = 1600
    const val UPLOAD_JPEG_QUALITY = 90
    const val UPLOAD_MIN_JPEG_QUALITY = 70

    /** Keep well below the backend's MAX_IMAGE_SIZE_MB (10 MB). */
    const val UPLOAD_MAX_BYTES = 4L * 1024 * 1024
    const val MIN_IMAGE_DIMENSION_PX = 480

    const val HISTORY_PAGE_SIZE = 10

    /** Gemini can take a while; backend timeout is 90 s, so the client waits a bit longer. */
    const val ANALYSIS_READ_TIMEOUT_SECONDS = 150L

    const val DEV_TOKEN = "dev-local-token"
    const val SCAN_DIR = "scans"
}

object CameraGuidance {
    const val CENTER_FACE = "Posisikan wajah di tengah kamera."
    const val CLEAR_FACE = "Pastikan wajah terlihat jelas."
    const val LIGHTING = "Gunakan pencahayaan yang cukup."
    const val NO_FILTER = "Jangan menggunakan filter kamera."
    val ALL = listOf(CENTER_FACE, CLEAR_FACE, LIGHTING, NO_FILTER)
}
