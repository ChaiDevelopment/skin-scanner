package com.chai.aiskin.features.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chai.aiskin.core.designsystem.AiSkinTheme
import com.chai.aiskin.core.designsystem.AppCard
import com.chai.aiskin.core.designsystem.AppTopBar
import com.chai.aiskin.core.designsystem.NoticeBanner
import com.chai.aiskin.core.designsystem.PrimaryButton
import com.chai.aiskin.core.designsystem.SecondaryButton
import com.chai.aiskin.core.designsystem.Spacing
import com.chai.aiskin.core.designsystem.Tone
import com.chai.aiskin.domain.repository.PreferencesRepository
import com.chai.aiskin.scanner.CapturedScan
import com.chai.aiskin.scanner.ScanSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreviewUiState(val scan: CapturedScan?, val storePhoto: Boolean)

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val session: ScanSession,
    private val prefs: PreferencesRepository,
) : ViewModel() {
    private val storeOverride = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)

    val state: StateFlow<PreviewUiState> = combine(session.capture, prefs.preferences, storeOverride) { scan, p, override ->
        PreviewUiState(scan, override ?: p.storePhotos)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PreviewUiState(session.capture.value, false))

    /** Per-scan choice; does not change the saved default. */
    fun setStorePhoto(enabled: Boolean) { storeOverride.value = enabled }

    fun confirm(onConfirmed: () -> Unit) = viewModelScope.launch {
        session.storePhoto = storeOverride.value ?: prefs.preferences.first().storePhotos
        onConfirmed()
    }

    fun retake(onRetake: () -> Unit) {
        session.clearCapture()
        onRetake()
    }
}

@Composable
fun PreviewScreen(onConfirm: () -> Unit, onRetake: () -> Unit, vm: PreviewViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val scan = s.scan
    LaunchedEffect(scan) { if (scan == null) onRetake() }
    if (scan == null) return
    val validation = scan.validation

    Scaffold(
        topBar = { AppTopBar("Pratinjau foto", onBack = { vm.retake(onRetake) }) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(Spacing.screenHorizontal, Spacing.md)) {
                if (validation.isAcceptable) {
                    PrimaryButton("Analisis foto ini", onClick = { vm.confirm(onConfirm) }, modifier = Modifier.testTag("confirm_analysis"))
                    Spacer(Modifier.height(Spacing.sm))
                }
                SecondaryButton("Ambil ulang", onClick = { vm.retake(onRetake) }, icon = Icons.Outlined.Refresh)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal)) {
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Image(
                    bitmap = scan.preview.asImageBitmap(),
                    contentDescription = "Foto wajah yang diambil",
                    contentScale = ContentScale.Crop,
                    // Mirror only for display so it matches what the user saw in the front camera.
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = if (scan.fromFrontCamera) -1f else 1f },
                )
            }
            Spacer(Modifier.height(Spacing.lg))

            if (validation.isAcceptable) {
                ValidationRow(Icons.Outlined.CheckCircle, "Wajah terdeteksi dengan baik", AiSkinTheme.extended.positive)
                validation.warnings.forEach { ValidationRow(Icons.Outlined.WarningAmber, it.message, AiSkinTheme.extended.caution) }
            } else {
                validation.blockingIssues.forEach { ValidationRow(Icons.Outlined.ErrorOutline, it.message, AiSkinTheme.extended.alert) }
                Spacer(Modifier.height(Spacing.xs))
                NoticeBanner("Foto perlu diambil ulang agar analisis dapat dilakukan dengan baik.", tone = Tone.Alert)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Pemeriksaan ini hanya memastikan posisi dan kualitas foto — bukan analisis kulit.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (validation.isAcceptable) {
                Spacer(Modifier.height(Spacing.lg))
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Simpan foto ini di akun saya", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(Spacing.xxs))
                            Text(
                                if (s.storePhoto) "Foto disimpan bersama hasil analisis. Dapat dihapus kapan saja."
                                else "Foto hanya dipakai untuk analisis ini dan tidak disimpan.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = s.storePhoto, onCheckedChange = vm::setStorePhoto, modifier = Modifier.testTag("store_photo_switch"))
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                NoticeBanner(
                    "Dengan melanjutkan, foto dikirim melalui server kami ke Google Gemini untuk observasi visual. Ini bukan diagnosis medis.",
                    icon = Icons.Outlined.Info,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun ValidationRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}
