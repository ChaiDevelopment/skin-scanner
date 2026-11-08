package com.chai.aiskin.features.result

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.designsystem.AppCard
import com.chai.aiskin.core.designsystem.AppTopBar
import com.chai.aiskin.core.designsystem.ErrorView
import com.chai.aiskin.core.designsystem.LoadingView
import com.chai.aiskin.core.designsystem.PrimaryButton
import com.chai.aiskin.core.designsystem.QuietButton
import com.chai.aiskin.core.designsystem.SecondaryButton
import com.chai.aiskin.core.designsystem.Spacing

@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onScanAgain: () -> Unit,
    onDeleted: () -> Unit,
    vm: ResultViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDeletePhoto by remember { mutableStateOf(false) }

    LaunchedEffect(s.deleted) { if (s.deleted) onDeleted() }
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    BackHandler(enabled = s.fresh) { onDone() }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (s.fresh) "Hasil analisis" else "Detail analisis",
                onBack = if (s.fresh) onDone else onBack,
                actions = {
                    if (s.content is ResultContent.Loaded) {
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete_analysis")) {
                            Icon(Icons.Outlined.DeleteOutline, "Hapus analisis")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val c = s.content) {
            ResultContent.Loading -> LoadingView("Memuat analisis...", Modifier.padding(padding))
            is ResultContent.Error -> ErrorView(
                title = if (c.error is AppError.NotFound) "Analisis tidak ditemukan" else "Gagal memuat analisis",
                message = c.error.message,
                icon = Icons.Outlined.ErrorOutline,
                onRetry = if (c.error is AppError.NotFound) null else { { vm.load() } },
                secondaryLabel = "Kembali", onSecondary = onBack,
                modifier = Modifier.padding(padding),
            )
            is ResultContent.Loaded -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenHorizontal).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                AnalysisBody(c.analysis)
                if (c.analysis.photoStored) {
                    AppCard {
                        Text("Foto tersimpan", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(Spacing.xxs))
                        Text("Foto analisis ini disimpan di akun Anda.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        QuietButton("Hapus foto saja", { confirmDeletePhoto = true })
                    }
                }
                FeedbackCard(sent = s.feedbackSent, sending = s.feedbackSending, onSend = vm::sendFeedback)
                if (s.fresh) {
                    PrimaryButton("Selesai", onDone)
                    SecondaryButton("Pindai lagi", onScanAgain, icon = Icons.Outlined.CameraAlt)
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus analisis?",
            text = "Analisis ini beserta foto yang tersimpan (jika ada) akan dihapus permanen dari akun Anda. Data yang mungkin disimpan sementara oleh penyedia AI pihak ketiga tidak ikut terhapus oleh tindakan ini.",
            confirm = "Hapus",
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmDeletePhoto) {
        ConfirmDialog(
            title = "Hapus foto?",
            text = "Foto akan dihapus permanen. Hasil analisis teks tetap tersimpan.",
            confirm = "Hapus foto",
            onConfirm = { confirmDeletePhoto = false; vm.deletePhoto() },
            onDismiss = { confirmDeletePhoto = false },
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_dialog_ok")) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun FeedbackCard(sent: Boolean, sending: Boolean, onSend: (Boolean, Int?, String?) -> Unit) {
    AppCard {
        Text("Apakah analisis ini membantu?", style = MaterialTheme.typography.titleSmall)
        if (sent) {
            Spacer(Modifier.height(Spacing.xs))
            Text("Masukan Anda sudah kami terima. Terima kasih.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AppCard
        }
        var helpful by rememberSaveable { mutableStateOf<Boolean?>(null) }
        var comment by rememberSaveable { mutableStateOf("") }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FilterChip(selected = helpful == true, onClick = { helpful = true }, label = { Text("Membantu") }, leadingIcon = { Icon(Icons.Outlined.ThumbUp, null) })
            FilterChip(selected = helpful == false, onClick = { helpful = false }, label = { Text("Kurang") }, leadingIcon = { Icon(Icons.Outlined.ThumbDown, null) })
        }
        if (helpful != null) {
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = comment, onValueChange = { if (it.length <= 1000) comment = it },
                label = { Text("Komentar (opsional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(Spacing.sm))
            PrimaryButton("Kirim masukan", { onSend(helpful!!, null, comment) }, loading = sending)
        }
    }
}
