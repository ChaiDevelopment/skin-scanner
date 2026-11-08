package com.chai.aiskin.scanner

import android.graphics.Bitmap
import com.chai.aiskin.detection.FaceValidation
import com.chai.aiskin.domain.model.SkinAnalysis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class CapturedScan(val file: java.io.File, val preview: Bitmap, val validation: FaceValidation, val fromFrontCamera: Boolean = true)

/**
 * Holds the in-progress scan between Camera → Preview → Scanning → Result.
 * The photo stays in memory/private cache only and is cleared after use.
 */
@Singleton
class ScanSession @Inject constructor(private val processor: CapturedImageProcessor) {
    private val _capture = MutableStateFlow<CapturedScan?>(null)
    val capture: StateFlow<CapturedScan?> = _capture.asStateFlow()

    var storePhoto: Boolean = false
    var lastResult: SkinAnalysis? = null
        private set

    fun setCapture(scan: CapturedScan) {
        val previous = _capture.value
        _capture.value = scan
        if (previous != null && previous.file != scan.file) previous.file.delete()
    }

    fun completeWith(result: SkinAnalysis) {
        lastResult = result
        clearCapture()
    }

    /** Bitmaps are not recycled manually: Compose may still be drawing them; GC reclaims them. */
    fun clearCapture() {
        _capture.value?.file?.delete()
        _capture.value = null
        processor.clearAll()
    }
}

/** Informative stages shown while waiting for the backend. They describe the process, not measurements. */
enum class ScanStage(val message: String) {
    CHECKING_QUALITY("Memeriksa kualitas gambar..."),
    UPLOADING("Mengirim foto secara aman..."),
    OBSERVING_TEXTURE("Mengamati tekstur kulit..."),
    IDENTIFYING("Mengidentifikasi karakteristik visual..."),
    COMPOSING("Menyusun laporan analisis..."),
}
