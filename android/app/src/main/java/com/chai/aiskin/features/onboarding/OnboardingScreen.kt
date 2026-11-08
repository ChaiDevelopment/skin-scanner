package com.chai.aiskin.features.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraFront
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chai.aiskin.core.constants.CameraGuidance
import com.chai.aiskin.core.designsystem.AiSkinTheme
import com.chai.aiskin.core.designsystem.AppCard
import com.chai.aiskin.core.designsystem.Eyebrow
import com.chai.aiskin.core.designsystem.PrimaryButton
import com.chai.aiskin.core.designsystem.QuietButton
import com.chai.aiskin.core.designsystem.Spacing
import com.chai.aiskin.domain.model.UserPreferences
import com.chai.aiskin.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val prefs: PreferencesRepository) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = prefs.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    fun setConsent(accepted: Boolean) = viewModelScope.launch { prefs.setPrivacyConsent(accepted) }
    fun setStorePhotos(enabled: Boolean) = viewModelScope.launch { prefs.setStorePhotos(enabled) }
    fun complete(onDone: () -> Unit) = viewModelScope.launch {
        prefs.setOnboardingCompleted(true)
        onDone()
    }
}

private data class OnboardingPage(val icon: ImageVector, val eyebrow: String, val title: String, val body: String)

private val pages = listOf(
    OnboardingPage(
        Icons.Outlined.Face, "Selamat datang", "Kenali tampilan kulit wajah Anda",
        "AI Skin Scanner mengamati karakteristik kulit yang terlihat dari foto — seperti pori, tekstur, kemerahan, dan pigmentasi — lalu memberi saran perawatan kulit yang lembut dan umum.\n\nIni adalah observasi visual berbasis AI, bukan diagnosis medis.",
    ),
    OnboardingPage(
        Icons.Outlined.Lock, "Privasi Anda", "Anda yang mengendalikan foto Anda",
        "Untuk dianalisis, foto dikirim melalui server kami ke Google Gemini. Secara bawaan foto TIDAK disimpan permanen — hanya hasil analisis teks yang disimpan di riwayat akun Anda.\n\nAnda dapat mengaktifkan penyimpanan foto kapan saja, dan menghapus foto, analisis, atau akun Anda sepenuhnya.",
    ),
    OnboardingPage(
        Icons.Outlined.CameraFront, "Tips foto", "Foto yang baik, observasi yang lebih baik",
        CameraGuidance.ALL.joinToString("\n") { "•  $it" } + "\n\nAplikasi memerlukan izin kamera untuk mengambil foto wajah dengan kamera depan.",
    ),
)

@Composable
fun OnboardingScreen(onFinished: (cameraGranted: Boolean) -> Unit, onOpenPrivacy: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val pager = rememberPagerState { pages.size + 1 }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.complete { onFinished(granted) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).testTag("onboarding_pager")) { index ->
            if (index < pages.size) PageContent(pages[index])
            else ConsentPage(prefs, vm::setConsent, vm::setStorePhotos, onOpenPrivacy)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.md), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size + 1) { i ->
                val active = i == pager.currentPage
                Box(
                    Modifier.padding(horizontal = 4.dp).size(width = if (active) 22.dp else 8.dp, height = 8.dp)
                        .background(if (active) AiSkinTheme.extended.accent else MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
        }
        Column(Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md)) {
            val last = pager.currentPage == pages.size
            if (!last) {
                PrimaryButton("Lanjut", onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
            } else {
                PrimaryButton(
                    "Izinkan kamera & mulai",
                    enabled = prefs.privacyConsentAccepted,
                    onClick = { permission.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.testTag("onboarding_start"),
                )
            }
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).background(AiSkinTheme.extended.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(page.icon, null, tint = AiSkinTheme.extended.accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(Spacing.xl))
        Eyebrow(page.eyebrow)
        Spacer(Modifier.height(Spacing.xs))
        Text(page.title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(Spacing.lg))
        Text(page.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConsentPage(
    prefs: UserPreferences,
    onConsent: (Boolean) -> Unit,
    onStorePhotos: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Eyebrow("Persetujuan")
        Spacer(Modifier.height(Spacing.xs))
        Text("Sebelum memulai", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(Spacing.lg))
        AppCard {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = prefs.privacyConsentAccepted, onCheckedChange = onConsent, modifier = Modifier.testTag("consent_checkbox"))
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    "Saya memahami bahwa foto wajah saya akan dikirim ke Google Gemini untuk observasi visual, dan hasilnya bukan diagnosis medis.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Simpan foto di akun saya", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Spacing.xxs))
                    Text("Opsional. Nonaktif secara bawaan. Dapat diubah di Pengaturan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = prefs.storePhotos, onCheckedChange = onStorePhotos)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        QuietButton("Baca kebijakan privasi lengkap", onOpenPrivacy)
    }
}
