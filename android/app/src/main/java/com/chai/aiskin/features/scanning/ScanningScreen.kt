package com.chai.aiskin.features.scanning

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.designsystem.AiSkinTheme
import com.chai.aiskin.core.designsystem.ErrorView
import com.chai.aiskin.core.designsystem.Eyebrow
import com.chai.aiskin.core.designsystem.QuietButton
import com.chai.aiskin.core.designsystem.Spacing

@Composable
fun ScanningScreen(
    onDone: (analysisId: String) -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
    onSignInAgain: () -> Unit,
    vm: ScanningViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { (state as? ScanningUiState.Done)?.let { onDone(it.analysisId) } }
    BackHandler(enabled = state is ScanningUiState.InProgress) { vm.cancel(); onCancel() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        when (val s = state) {
            is ScanningUiState.InProgress -> Progress(s, onCancel = { vm.cancel(); onCancel() })
            is ScanningUiState.Done -> Progress(ScanningUiState.InProgress(com.chai.aiskin.scanner.ScanStage.COMPOSING, 4, 5), onCancel = {})
            ScanningUiState.NoPhoto -> ErrorView("Foto tidak tersedia", "Silakan ambil foto terlebih dahulu.", retryLabel = "Buka kamera", onRetry = onRetake)
            is ScanningUiState.Failed -> FailureView(s.error, onRetry = vm::start, onRetake = { vm.discardPhoto(); onRetake() }, onSignInAgain = onSignInAgain)
        }
    }
}

@Composable
private fun Progress(s: ScanningUiState.InProgress, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ScanRings(Modifier.size(180.dp))
        Spacer(Modifier.height(Spacing.xl))
        Eyebrow("Langkah ${s.stageIndex + 1} dari ${s.totalStages}")
        Spacer(Modifier.height(Spacing.xs))
        AnimatedContent(s.stage, label = "stage") { stage ->
            Text(
                stage.message, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.testTag("scan_stage"),
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        LinearProgressIndicator(
            progress = { (s.stageIndex + 1f) / (s.totalStages + 1f) },
            modifier = Modifier.fillMaxWidth(0.6f).height(3.dp),
            color = AiSkinTheme.extended.accent,
            trackColor = AiSkinTheme.extended.hairline,
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(Spacing.lg))
        Text(
            "Biasanya memerlukan 10–40 detik. Tahapan di atas menggambarkan proses, bukan pengukuran medis.",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xl))
        QuietButton("Batalkan", onCancel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScanRings(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "rings")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "sweep")
    val pulse by t.animateFloat(0.92f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
    val accent = AiSkinTheme.extended.accent
    val hair = AiSkinTheme.extended.hairline
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(hair, r * 0.98f, style = Stroke(1.dp.toPx()))
        drawCircle(hair, r * 0.72f * pulse, style = Stroke(1.dp.toPx()))
        drawArc(accent, sweep, 70f, false, topLeft = Offset(size.width / 2 - r * 0.98f, size.height / 2 - r * 0.98f),
            size = androidx.compose.ui.geometry.Size(r * 1.96f, r * 1.96f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(accent.copy(alpha = 0.18f), r * 0.42f * pulse)
    }
}

@Composable
private fun FailureView(error: AppError, onRetry: () -> Unit, onRetake: () -> Unit, onSignInAgain: () -> Unit) {
    when (error) {
        is AppError.InsufficientQuality -> {
            val hints = error.hints.take(3).joinToString("\n") { "•  $it" }
            ErrorView(
                title = "Kualitas foto belum cukup",
                message = listOf(error.message, hints).filter { it.isNotBlank() }.joinToString("\n\n"),
                icon = Icons.Outlined.WbSunny,
                retryLabel = "Ambil ulang foto",
                onRetry = onRetake,
            )
        }
        is AppError.Unauthenticated -> ErrorView("Sesi berakhir", error.message, icon = Icons.Outlined.Lock, retryLabel = "Masuk kembali", onRetry = onSignInAgain)
        is AppError.ImageTooLarge, is AppError.UnsupportedImage, is AppError.Validation ->
            ErrorView("Foto tidak dapat dikirim", error.message, icon = Icons.Outlined.CameraAlt, retryLabel = "Ambil ulang foto", onRetry = onRetake)
        else -> ErrorView(
            title = if (error is AppError.Network) "Koneksi terputus" else "Analisis belum berhasil",
            message = error.message,
            icon = Icons.Outlined.CloudOff,
            onRetry = if (error.isRetryable) onRetry else null,
            secondaryLabel = "Ambil ulang foto",
            onSecondary = onRetake,
        )
    }
}
