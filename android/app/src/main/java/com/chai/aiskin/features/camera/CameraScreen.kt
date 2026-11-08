package com.chai.aiskin.features.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlipCameraAndroid
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chai.aiskin.camera.FrontCameraController
import com.chai.aiskin.camera.Lens
import com.chai.aiskin.core.constants.CameraGuidance
import com.chai.aiskin.core.designsystem.AiSkinColors
import com.chai.aiskin.core.designsystem.EmptyView
import com.chai.aiskin.core.designsystem.ErrorView
import com.chai.aiskin.core.designsystem.Spacing
import com.chai.aiskin.detection.LiveFaceAnalyzer
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(onClose: () -> Unit, onCaptured: () -> Unit, vm: CameraViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val rationale = context.findActivity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } ?: false
        vm.onPermission(CameraPermissionReducer.onResult(granted, rationale))
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.onPermission(CameraPermissionStatus.GRANTED) else launcher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) { vm.events.collect { if (it is CameraEvent.OpenPreview) onCaptured() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (state.permission) {
            CameraPermissionStatus.GRANTED -> CameraContent(state, vm, onClose)
            CameraPermissionStatus.UNKNOWN -> Unit
            CameraPermissionStatus.DENIED -> PermissionMessage(
                title = "Izin kamera diperlukan",
                message = "Kamera depan digunakan untuk mengambil foto wajah Anda. Kamera tidak pernah aktif tanpa izin Anda.",
                action = "Izinkan kamera", onAction = { launcher.launch(Manifest.permission.CAMERA) }, onClose = onClose,
            )
            CameraPermissionStatus.PERMANENTLY_DENIED -> PermissionMessage(
                title = "Izin kamera ditolak",
                message = "Aktifkan izin kamera melalui Pengaturan aplikasi untuk mulai memindai.",
                action = "Buka pengaturan aplikasi",
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun PermissionMessage(title: String, message: String, action: String, onAction: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        EmptyView(title = title, message = message, icon = Icons.Outlined.NoPhotography, actionLabel = action, onAction = onAction)
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xs)) {
            Icon(Icons.Outlined.Close, "Tutup")
        }
    }
}

@Composable
private fun CameraContent(state: CameraUiState, vm: CameraViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val controller = remember { FrontCameraController(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var lens by remember { mutableStateOf(Lens.FRONT) }
    var bindAttempt by remember { mutableStateOf(0) }

    LaunchedEffect(lens, bindAttempt) {
        val analyzer = LiveFaceAnalyzer(vm.liveDetector) { vm.onLiveValidation(it) }
        controller.bind(lifecycleOwner, previewView, analyzer, lens)
            .onSuccess { vm.onCameraBound(controller.hasFlashUnit, controller.hasBothLenses()) }
            .onFailure { vm.onCameraError(it.message ?: "Kamera tidak dapat dibuka.") }
    }
    DisposableEffect(Unit) { onDispose { controller.release() } }
    LaunchedEffect(state.flashOn) { controller.setFlash(state.flashOn) }

    if (state.cameraError != null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ErrorView("Kamera tidak tersedia", state.cameraError, onRetry = { vm.clearCameraError(); bindAttempt++ }, secondaryLabel = "Tutup", onSecondary = onClose)
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().testTag("camera_preview"))
        val issue = state.liveValidation?.primaryIssue
        val ok = state.liveValidation?.isAcceptable == true
        FaceGuideOverlay(highlight = ok)

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Outlined.Close, "Tutup kamera", onClose)
                Spacer(Modifier.weight(1f))
                if (state.flashAvailable) {
                    RoundIcon(if (state.flashOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff, if (state.flashOn) "Matikan flash" else "Nyalakan flash", vm::toggleFlash)
                }
                if (state.canSwitchLens) {
                    RoundIcon(Icons.Outlined.FlipCameraAndroid, "Ganti kamera") { lens = if (lens == Lens.FRONT) Lens.BACK else Lens.FRONT }
                }
            }
            Spacer(Modifier.weight(1f))

            AnimatedContent(targetState = state.captureError ?: issue?.message ?: if (ok) "Posisi baik. Tahan sebentar, lalu ambil foto." else CameraGuidance.CENTER_FACE, label = "hint") { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl)
                        .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.extraLarge)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "${CameraGuidance.LIGHTING}  ·  ${CameraGuidance.NO_FILTER}",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            )
            Spacer(Modifier.height(Spacing.lg))
            Box(Modifier.fillMaxWidth().padding(bottom = Spacing.xl), contentAlignment = Alignment.Center) {
                CaptureButton(
                    enabled = state.cameraReady && !state.busy,
                    busy = state.busy,
                    onClick = {
                        val file = vm.beginCapture() ?: return@CaptureButton
                        scope.launch {
                            runCatching { controller.capture(file) }
                                .onSuccess { vm.onCaptured(it, controller.lens == Lens.FRONT) }
                                .onFailure { vm.onCaptureFailed() }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FaceGuideOverlay(highlight: Boolean) {
    val ring = if (highlight) AiSkinColors.ChampagneLight else Color.White.copy(alpha = 0.85f)
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        drawRect(Color.Black.copy(alpha = 0.42f))
        val ovalW = size.width * 0.70f
        val ovalH = ovalW * 1.32f
        val topLeft = Offset((size.width - ovalW) / 2f, size.height * 0.44f - ovalH / 2f)
        drawOval(Color.Transparent, topLeft, Size(ovalW, ovalH), blendMode = BlendMode.Clear)
        drawOval(ring, topLeft, Size(ovalW, ovalH), style = Stroke(width = if (highlight) 4.dp.toPx() else 2.dp.toPx()))
    }
}

@Composable
private fun CaptureButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(84.dp).border(3.dp, Color.White, CircleShape).padding(7.dp)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Ambil foto" }
            .testTag("capture_button"),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = AiSkinColors.Champagne, strokeWidth = 2.dp)
        else Icon(Icons.Outlined.CameraAlt, null, tint = AiSkinColors.Charcoal)
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.padding(4.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape)) {
        Icon(icon, description, tint = Color.White)
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

