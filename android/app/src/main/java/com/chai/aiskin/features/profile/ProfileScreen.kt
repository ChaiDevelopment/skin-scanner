package com.chai.aiskin.features.profile

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.core.designsystem.AiSkinTheme
import com.chai.aiskin.core.designsystem.AppCard
import com.chai.aiskin.core.designsystem.AppTopBar
import com.chai.aiskin.core.designsystem.HairlineDivider
import com.chai.aiskin.core.designsystem.NoticeBanner
import com.chai.aiskin.core.designsystem.Spacing
import com.chai.aiskin.core.designsystem.StatusPill
import com.chai.aiskin.core.designsystem.Tone
import com.chai.aiskin.domain.model.AuthState
import com.chai.aiskin.domain.repository.AnalysisRepository
import com.chai.aiskin.domain.repository.AuthRepository
import com.chai.aiskin.domain.repository.PreferencesRepository
import com.chai.aiskin.scanner.ScanSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(val deleting: Boolean = false, val error: String? = null)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val analyses: AnalysisRepository,
    private val prefs: PreferencesRepository,
    private val session: ScanSession,
) : ViewModel() {
    val authState: StateFlow<AuthState> = auth.authState
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun signOut() = viewModelScope.launch {
        session.clearCapture()
        auth.signOut()
    }

    /** Backend deletes photos, Firestore data and the Firebase Auth user; then local data is wiped. */
    fun deleteAccount() {
        if (_state.value.deleting) return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true, error = null) }
            when (val r = analyses.deleteAccount()) {
                is AppResult.Success -> {
                    session.clearCapture()
                    prefs.clearAccountScoped()
                    auth.signOut()
                    _state.update { it.copy(deleting = false) }
                }
                is AppResult.Failure -> _state.update { it.copy(deleting = false, error = r.error.message) }
            }
        }
    }
}

@Composable
fun ProfileScreen(onSettings: () -> Unit, onPrivacy: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val auth by vm.authState.collectAsStateWithLifecycle()
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val user = (auth as? AuthState.SignedIn)?.user

    Scaffold(topBar = { AppTopBar("Profil") }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(AiSkinTheme.extended.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                        Text((user?.displayName ?: user?.email ?: "?").take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(user?.displayName ?: "Pengguna", style = MaterialTheme.typography.titleMedium)
                        Text(user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (user?.isDevMode == true) {
                            Spacer(Modifier.height(Spacing.xxs))
                            StatusPill("Mode pengembang", Tone.Caution)
                        }
                    }
                }
            }
            AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.xs)) {
                MenuRow(Icons.Outlined.Settings, "Pengaturan", onSettings)
                HairlineDivider(Modifier.padding(horizontal = Spacing.md))
                MenuRow(Icons.Outlined.PrivacyTip, "Privasi & persetujuan", onPrivacy)
                HairlineDivider(Modifier.padding(horizontal = Spacing.md))
                MenuRow(Icons.AutoMirrored.Outlined.Logout, "Keluar", { confirmLogout = true })
            }
            AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.xs)) {
                MenuRow(Icons.Outlined.DeleteForever, "Hapus akun & semua data", { confirmDelete = true }, danger = true)
            }
            if (s.deleting) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Spacing.sm))
                Text("Menghapus akun...", style = MaterialTheme.typography.bodySmall)
            }
            s.error?.let { NoticeBanner(it, tone = Tone.Alert) }
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Keluar dari akun?") },
        text = { Text("Riwayat Anda tetap tersimpan dan dapat diakses kembali setelah masuk.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; vm.signOut() }) { Text("Keluar") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Batal") } },
    )
    if (confirmDelete) DeleteAccountDialog(onConfirm = { confirmDelete = false; vm.deleteAccount() }, onDismiss = { confirmDelete = false })
}

@Composable
private fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus akun permanen?") },
        text = {
            Column {
                Text(
                    "Semua analisis, foto tersimpan, preferensi, dan akun Anda akan dihapus permanen dari layanan kami. " +
                        "Tindakan ini tidak dapat dibatalkan. Data yang mungkin disimpan sementara oleh penyedia AI pihak ketiga (Google) diatur oleh kebijakan mereka.",
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(typed, { typed = it }, label = { Text("Ketik HAPUS untuk konfirmasi") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == "HAPUS") { Text("Hapus akun", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (danger) color else AiSkinTheme.extended.accent)
            Spacer(Modifier.width(Spacing.md))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
