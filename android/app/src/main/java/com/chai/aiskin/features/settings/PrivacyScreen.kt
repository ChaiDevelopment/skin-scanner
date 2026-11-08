package com.chai.aiskin.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chai.aiskin.core.designsystem.AppCard
import com.chai.aiskin.core.designsystem.AppTopBar
import com.chai.aiskin.core.designsystem.Eyebrow
import com.chai.aiskin.core.designsystem.Spacing
import com.chai.aiskin.features.onboarding.OnboardingViewModel

private val sections = listOf(
    "Apa yang dianalisis" to "Hanya foto wajah yang Anda ambil dan konfirmasi sendiri di layar pratinjau. Kamera tidak pernah aktif tanpa izin Anda, dan aplikasi tidak melakukan pengenalan identitas wajah.",
    "Pemrosesan oleh pihak ketiga" to "Untuk dianalisis, foto dikirim melalui server kami ke Google Gemini (Google LLC). Server kami menghapus metadata foto (seperti lokasi) sebelum dikirim. Google memproses data sesuai ketentuan layanan Gemini API.",
    "Penyimpanan foto" to "Secara bawaan foto TIDAK disimpan permanen: setelah analisis selesai, foto dihapus dari perangkat dan server kami. Hanya jika Anda mengaktifkan \"Simpan foto\", foto disimpan di Firebase Storage pada folder akun Anda sendiri.",
    "Data yang disimpan" to "Hasil analisis teks (ringkasan, observasi, rekomendasi, kualitas foto) disimpan di Cloud Firestore di bawah akun Anda dan hanya dapat diakses oleh Anda.",
    "Kendali Anda" to "Anda dapat menghapus foto saja, menghapus satu analisis (beserta fotonya), atau menghapus akun dan semua data kapan saja dari menu Profil. Penghapusan di aplikasi ini tidak otomatis menghapus data yang mungkin disimpan sementara oleh penyedia pihak ketiga sesuai kebijakan mereka.",
    "Bukan diagnosis medis" to "Hasil adalah observasi visual berbasis AI dan saran perawatan umum. Aplikasi tidak mendiagnosis penyakit kulit. Konsultasikan dengan dokter spesialis kulit untuk masalah yang menetap, memburuk, nyeri, atau mengkhawatirkan.",
)

@Composable
fun PrivacyScreen(onBack: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val p by vm.preferences.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("Privasi & persetujuan", onBack = onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            sections.forEach { (title, body) ->
                AppCard {
                    Eyebrow(title)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            }
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = p.privacyConsentAccepted, onCheckedChange = { vm.setConsent(it) })
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Saya menyetujui pemrosesan foto sesuai penjelasan di atas.", style = MaterialTheme.typography.bodyMedium)
                }
                if (!p.privacyConsentAccepted && p.onboardingCompleted) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Tanpa persetujuan, pemindaian tidak dapat dilakukan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
