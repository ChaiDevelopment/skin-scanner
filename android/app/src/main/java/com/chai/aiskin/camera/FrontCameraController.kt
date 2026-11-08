package com.chai.aiskin.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class Lens { FRONT, BACK }

/**
 * Owns the CameraX use cases (preview + capture + live analysis). Bound to a
 * LifecycleOwner, so CameraX stops the camera automatically when the screen
 * stops; [release] frees the analysis executor when the screen leaves composition.
 */
class FrontCameraController(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    var lens: Lens = Lens.FRONT
        private set

    val hasFlashUnit: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer?,
        lens: Lens = this.lens,
    ): Result<Unit> = runCatching {
        val p = provider ?: awaitProvider().also { provider = it }
        val selector = selectorFor(lens, p)
        val ratio = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()

        val preview = Preview.Builder().setResolutionSelector(ratio).build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val capture = ImageCapture.Builder()
            .setResolutionSelector(ratio)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()
        val analysis = analyzer?.let {
            ImageAnalysis.Builder()
                .setResolutionSelector(ratio)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { a -> a.setAnalyzer(analysisExecutor, it) }
        }

        p.unbindAll()
        camera = if (analysis != null) {
            p.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
        } else {
            p.bindToLifecycle(lifecycleOwner, selector, preview, capture)
        }
        imageCapture = capture
        this.lens = if (selector == CameraSelector.DEFAULT_FRONT_CAMERA) Lens.FRONT else Lens.BACK
    }

    private fun selectorFor(lens: Lens, p: ProcessCameraProvider): CameraSelector {
        val front = CameraSelector.DEFAULT_FRONT_CAMERA
        val back = CameraSelector.DEFAULT_BACK_CAMERA
        return when {
            lens == Lens.FRONT && p.hasCamera(front) -> front
            lens == Lens.BACK && p.hasCamera(back) -> back
            p.hasCamera(front) -> front
            p.hasCamera(back) -> back
            else -> throw IllegalStateException("Tidak ada kamera yang tersedia di perangkat ini.")
        }
    }

    fun hasBothLenses(): Boolean {
        val p = provider ?: return false
        return runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) && p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
    }

    /** Flash is only offered when the active lens actually has a flash unit (most front cameras do not). */
    fun setFlash(enabled: Boolean) {
        imageCapture?.flashMode = if (enabled && hasFlashUnit) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    /** Captures a JPEG to [output]. CameraX writes EXIF orientation; rotation is applied later by the processor. */
    suspend fun capture(output: File): File = suspendCancellableCoroutine { cont ->
        val capture = imageCapture ?: run {
            cont.resumeWithException(IllegalStateException("Kamera belum siap."))
            return@suspendCancellableCoroutine
        }
        val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = false }
        val options = ImageCapture.OutputFileOptions.Builder(output).setMetadata(metadata).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                if (cont.isActive) cont.resume(output)
            }
            override fun onError(exception: ImageCaptureException) {
                output.delete()
                if (cont.isActive) cont.resumeWithException(exception)
            }
        })
    }

    fun release() {
        runCatching { provider?.unbindAll() }
        analysisExecutor.shutdown()
        camera = null
        imageCapture = null
    }

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching { future.get() }
                .onSuccess { cont.resume(it) }
                .onFailure { cont.resumeWithException(it) }
        }, ContextCompat.getMainExecutor(context))
    }
}
