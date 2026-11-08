package com.chai.aiskin.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraFront
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.core.common.formatDateTime
import com.chai.aiskin.core.constants.CameraGuidance
import com.chai.aiskin.core.designsystem.AiSkinColors
import com.chai.aiskin.core.designsystem.AiSkinTheme
import com.chai.aiskin.core.designsystem.AppCard
import com.chai.aiskin.core.designsystem.Eyebrow
import com.chai.aiskin.core.designsystem.NoticeBanner
import com.chai.aiskin.core.designsystem.SectionHeader
import com.chai.aiskin.core.designsystem.Spacing
import com.chai.aiskin.core.designsystem.StatusPill
import com.chai.aiskin.core.designsystem.Tone
import com.chai.aiskin.domain.model.AuthState
import com.chai.aiskin.domain.model.HistoryItem
import com.chai.aiskin.domain.repository.AnalysisRepository
import com.chai.aiskin.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class HomeUiState(
    val name: String = "",
    val latest: HistoryItem? = null,
    val latestLoading: Boolean = true,
    val latestError: String? = null,
    val devMode: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val analyses: AnalysisRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.authState.collect { a ->
                if (a is AuthState.SignedIn) {
                    _state.update { it.copy(name = a.user.displayName ?: a.user.email?.substringBefore('@').orEmpty(), devMode = a.user.isDevMode) }
                }
            }
        }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(latestLoading = it.latest == null, latestError = null) }
        when (val r = analyses.history(null, 1)) {
            is AppResult.Success -> _state.update { it.copy(latest = r.data.items.firstOrNull(), latestLoading = false) }
            is AppResult.Failure -> _state.update { it.copy(latestLoading = false, latestError = r.error.message) }
        }
    }
}

@Composable
fun HomeScreen(onScan: () -> Unit, onOpenAnalysis: (String) -> Unit, onHistory: () -> Unit, onSettings: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var first by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (first) first = false else vm.refresh() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow(greeting())
                Spacer(Modifier.height(Spacing.xxs))
                Text(s.name.ifBlank { "Selamat datang" }, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Pengaturan") }
        }
        if (s.devMode) NoticeBanner("Mode pengembang lokal aktif — riwayat hanya tersimpan sementara di server lokal.", tone = Tone.Caution)

        ScanHero(onScan)

        Column {
            SectionHeader("Analisis terakhir", eyebrow = "Riwayat") { QuietButtonSmall("Lihat semua", onHistory) }
            Spacer(Modifier.height(Spacing.sm))
            when {
                s.latestLoading -> Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = AiSkinTheme.extended.accent)
                }
                s.latestError != null -> AppCard(onClick = vm::refresh) {
                    Text("Riwayat belum dapat dimuat", style = MaterialTheme.typography.titleSmall)
                    Text("${s.latestError} Ketuk untuk mencoba lagi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                s.latest == null -> AppCard {
                    Text("Belum ada analisis", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Spacing.xxs))
                    Text("Lakukan pemindaian pertama untuk melihat observasi kulit Anda di sini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LatestCard(s.latest!!) { onOpenAnalysis(s.latest!!.id) }
            }
        }

        Column {
            SectionHeader("Tips foto terbaik", eyebrow = "Panduan")
            Spacer(Modifier.height(Spacing.sm))
            AppCard {
                CameraGuidance.ALL.forEachIndexed { i, tip ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp).background(AiSkinTheme.extended.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Text(tip, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        NoticeBanner("AI Skin Scanner memberikan observasi visual dan saran perawatan umum — bukan diagnosis medis.", icon = Icons.Outlined.Info)
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun ScanHero(onScan: () -> Unit) {
    AppCard(onClick = onScan, containerColor = AiSkinColors.Charcoal, borderColor = AiSkinColors.Charcoal, modifier = Modifier.testTag("home_scan")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Pindai baru", color = AiSkinColors.ChampagneLight)
                Spacer(Modifier.height(Spacing.xs))
                Text("Amati kulit wajah Anda", style = MaterialTheme.typography.headlineSmall, color = AiSkinColors.OffWhite)
                Spacer(Modifier.height(Spacing.xs))
                Text("Kamera depan · sekitar 30 detik", style = MaterialTheme.typography.bodySmall, color = AiSkinColors.Sand)
            }
            Box(Modifier.size(56.dp).background(AiSkinColors.Champagne, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CameraFront, null, tint = AiSkinColors.OffWhite)
            }
        }
    }
}

@Composable
private fun LatestCard(item: HistoryItem, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.createdAt.formatDateTime(), style = MaterialTheme.typography.labelMedium, color = AiSkinTheme.extended.accent, modifier = Modifier.weight(1f))
            if (item.consultationRecommended) StatusPill("Konsultasi disarankan", Tone.Alert)
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(item.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuietButtonSmall(text: String, onClick: () -> Unit) =
    com.chai.aiskin.core.designsystem.QuietButton(text, onClick)

private fun greeting(): String = when (LocalTime.now().hour) {
    in 4..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}
