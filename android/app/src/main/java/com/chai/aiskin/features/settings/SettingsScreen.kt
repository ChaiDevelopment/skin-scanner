package com.chai.aiskin.features.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chai.aiskin.BuildConfig
import com.chai.aiskin.core.designsystem.AppCard
import com.chai.aiskin.core.designsystem.AppTopBar
import com.chai.aiskin.core.designsystem.Eyebrow
import com.chai.aiskin.core.designsystem.HairlineDivider
import com.chai.aiskin.core.designsystem.Spacing
import com.chai.aiskin.core.security.FirebaseStatus
import com.chai.aiskin.domain.model.UserPreferences
import com.chai.aiskin.domain.repository.PreferencesRepository
import com.chai.aiskin.features.profile.MenuRow
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = prefs.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    fun setStorePhotos(enabled: Boolean) = viewModelScope.launch {
        prefs.setStorePhotos(enabled)
        syncPrivacyPreference(enabled)
    }

    /** Mirrors the choice to users/{uid}/preferences/privacy (allowed by firestore.rules). Best effort. */
    private suspend fun syncPrivacyPreference(storePhotos: Boolean) {
        if (!FirebaseStatus.isConfigured(context)) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        runCatching {
            FirebaseFirestore.getInstance().collection("users").document(uid).collection("preferences").document("privacy")
                .set(mapOf("storePhotos" to storePhotos, "updatedAt" to FieldValue.serverTimestamp())).await()
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onPrivacy: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val p by vm.preferences.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("Pengaturan", onBack = onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Eyebrow("Privasi")
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Simpan foto secara bawaan", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(Spacing.xxs))
                        Text(
                            "Jika aktif, foto pindaian disimpan di akun Anda bersama hasilnya. Anda tetap bisa mengubahnya per pindaian. Foto dihapus otomatis saat analisisnya dihapus.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = p.storePhotos, onCheckedChange = vm::setStorePhotos, modifier = Modifier.testTag("settings_store_photos"))
                }
            }
            AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.xs)) {
                MenuRow(Icons.Outlined.PrivacyTip, "Kebijakan privasi & persetujuan", onPrivacy)
            }

            Eyebrow("Tentang")
            AppCard {
                InfoRow("Versi aplikasi", BuildConfig.VERSION_NAME + if (BuildConfig.DEBUG) " (debug)" else "")
                HairlineDivider()
                InfoRow("Server", BuildConfig.API_BASE_URL)
                HairlineDivider()
                InfoRow("Firebase", if (BuildConfig.FIREBASE_CONFIGURED) "Terkonfigurasi" else "Belum dikonfigurasi")
                HairlineDivider()
                InfoRow("Model AI", "Google Gemini (dikonfigurasi di server)")
            }
            Text(
                "Analisis AI dibekali materi referensi dermatologi untuk observasi visual dan saran perawatan kulit yang dijual bebas. " +
                    "Aplikasi ini bukan alat medis dan tidak menggantikan pemeriksaan dokter.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = Spacing.sm)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
