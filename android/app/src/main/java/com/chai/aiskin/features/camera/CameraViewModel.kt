package com.chai.aiskin.features.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chai.aiskin.core.common.DispatcherProvider
import com.chai.aiskin.detection.FaceDetectorService
import com.chai.aiskin.detection.FaceValidation
import com.chai.aiskin.scanner.CapturedImageProcessor
import com.chai.aiskin.scanner.CapturedScan
import com.chai.aiskin.scanner.ImageProcessingException
import com.chai.aiskin.scanner.ScanSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class CameraPermissionStatus { UNKNOWN, GRANTED, DENIED, PERMANENTLY_DENIED }

/** Pure mapping of the permission callback (unit-tested). */
object CameraPermissionReducer {
    fun onResult(granted: Boolean, shouldShowRationale: Boolean): CameraPermissionStatus = when {
        granted -> CameraPermissionStatus.GRANTED
        shouldShowRationale -> CameraPermissionStatus.DENIED
        else -> CameraPermissionStatus.PERMANENTLY_DENIED
    }
}

data class CameraUiState(
    val permission: CameraPermissionStatus = CameraPermissionStatus.UNKNOWN,
    val cameraReady: Boolean = false,
    val cameraError: String? = null,
    val liveValidation: FaceValidation? = null,
    val busy: Boolean = false,
    val flashAvailable: Boolean = false,
    val flashOn: Boolean = false,
    val canSwitchLens: Boolean = false,
    val captureError: String? = null,
)

sealed interface CameraEvent {
    data object OpenPreview : CameraEvent
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val processor: CapturedImageProcessor,
    private val detector: FaceDetectorService,
    private val session: ScanSession,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()
    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val liveDetector get() = detector.fastDetector

    fun onPermission(status: CameraPermissionStatus) = _state.update { it.copy(permission = status) }
    fun onCameraBound(flashAvailable: Boolean, canSwitch: Boolean) =
        _state.update { it.copy(cameraReady = true, cameraError = null, flashAvailable = flashAvailable, flashOn = it.flashOn && flashAvailable, canSwitchLens = canSwitch) }
    fun onCameraError(message: String) = _state.update { it.copy(cameraReady = false, cameraError = message) }
    fun clearCameraError() = _state.update { it.copy(cameraError = null) }
    fun onLiveValidation(v: FaceValidation) = _state.update { it.copy(liveValidation = v) }
    fun toggleFlash() = _state.update { it.copy(flashOn = !it.flashOn && it.flashAvailable) }
    fun dismissError() = _state.update { it.copy(captureError = null) }

    /** Returns null if a capture is already in progress (prevents double taps). */
    fun beginCapture(): File? {
        if (_state.value.busy) return null
        _state.update { it.copy(busy = true, captureError = null) }
        return processor.newCaptureFile()
    }

    fun onCaptureFailed(message: String = "Gagal mengambil foto. Silakan coba lagi.") =
        _state.update { it.copy(busy = false, captureError = message) }

    fun onCaptured(raw: File, frontCamera: Boolean) {
        viewModelScope.launch {
            try {
                val processed = withContext(dispatchers.default) { processor.process(raw) }
                val validation = detector.validate(processed.bitmap)
                session.setCapture(CapturedScan(processed.file, processed.bitmap, validation, frontCamera))
                _state.update { it.copy(busy = false) }
                _events.send(CameraEvent.OpenPreview)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImageProcessingException) {
                onCaptureFailed(e.message ?: "Foto tidak dapat diproses.")
            } catch (e: Exception) {
                onCaptureFailed("Foto tidak dapat diproses. Silakan coba lagi.")
            }
        }
    }
}
