package com.chai.aiskin.features.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.common.formatDateTime
import com.chai.aiskin.core.designsystem.AiSkinTheme
import com.chai.aiskin.core.designsystem.AppTopBar
import com.chai.aiskin.core.designsystem.EmptyView
import com.chai.aiskin.core.designsystem.ErrorView
import com.chai.aiskin.core.designsystem.LoadingView
import com.chai.aiskin.core.designsystem.PrimaryButton
import com.chai.aiskin.core.designsystem.QuietButton
import com.chai.aiskin.core.designsystem.Spacing
import com.chai.aiskin.core.designsystem.StatusPill
import com.chai.aiskin.core.designsystem.Tone
import com.chai.aiskin.domain.model.HistoryItem
import com.chai.aiskin.domain.model.ImageQualityStatus
import com.chai.aiskin.features.result.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpen: (String) -> Unit,
    onCompare: (String, String) -> Unit,
    onNewScan: () -> Unit,
    onSignInAgain: () -> Unit,
    vm: HistoryViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<HistoryItem?>(null) }
    var resumedOnce by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumedOnce) vm.refresh(silent = true) else resumedOnce = true
    }
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    Scaffold(
        topBar = {
            if (s.selected.isEmpty()) AppTopBar("Riwayat analisis")
            else AppTopBar("${s.selected.size} dipilih", actions = {
                IconButton(onClick = vm::clearSelection) { Icon(Icons.Outlined.Close, "Batalkan pilihan") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (s.selected.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(Spacing.screenHorizontal, Spacing.sm)) {
                        PrimaryButton(
                            if (s.canCompare) "Bandingkan 2 analisis" else "Pilih 1 analisis lagi",
                            onClick = { s.selected.toList().let { onCompare(it[0], it[1]) } },
                            enabled = s.canCompare, icon = Icons.Outlined.CompareArrows,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                s.initialLoading && s.items.isEmpty() -> LoadingView("Memuat riwayat...")
                s.error != null && s.items.isEmpty() -> {
                    val e = s.error!!
                    if (e is AppError.Unauthenticated) ErrorView("Sesi berakhir", e.message, retryLabel = "Masuk kembali", onRetry = onSignInAgain)
                    else ErrorView("Gagal memuat riwayat", e.message, onRetry = { vm.refresh() })
                }
                s.isEmpty -> EmptyView(
                    "Belum ada riwayat",
                    "Hasil analisis kulit Anda akan muncul di sini. Mulai pemindaian pertama Anda.",
                    icon = Icons.Outlined.History,
                    actionLabel = "Mulai pindai", onAction = onNewScan,
                )
                else -> PullToRefreshBox(isRefreshing = s.initialLoading, onRefresh = { vm.refresh() }) {
                    HistoryList(s, vm, onOpen, onDeleteRequest = { pendingDelete = it })
                }
            }
        }
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            "Hapus analisis?",
            "Analisis tanggal ${item.createdAt.formatDateTime()} beserta foto tersimpannya (jika ada) akan dihapus permanen.",
            "Hapus",
            onConfirm = { vm.delete(item.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun HistoryList(s: HistoryUiState, vm: HistoryViewModel, onOpen: (String) -> Unit, onDeleteRequest: (HistoryItem) -> Unit) {
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 3 } }
    LaunchedEffect(nearEnd, s.canLoadMore) { if (nearEnd && s.canLoadMore) vm.loadMore() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxSize().testTag("history_list"),
    ) {
        item {
            Text(
                "Tekan lama untuk memilih dua analisis dan membandingkannya.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(s.items, key = { it.id }) { item ->
            HistoryRow(
                item = item,
                selectionMode = s.selected.isNotEmpty(),
                selected = item.id in s.selected,
                onClick = { if (s.selected.isNotEmpty()) vm.toggleSelect(item.id) else onOpen(item.id) },
                onLongClick = { vm.toggleSelect(item.id) },
                onDelete = { onDeleteRequest(item) },
            )
        }
        item {
            Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                when {
                    s.loadingMore -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = AiSkinTheme.extended.accent)
                    s.loadMoreError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.loadMoreError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        QuietButton("Coba lagi", vm::retryLoadMore)
                    }
                    s.nextCursor == null && s.items.size > 3 -> Text("Semua riwayat telah dimuat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    item: HistoryItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val ext = AiSkinTheme.extended
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) ext.accentSoft else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) ext.accent else ext.hairline),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.Top) {
            if (selectionMode) {
                Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = ext.accent, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(Spacing.sm))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.createdAt.formatDateTime(), style = MaterialTheme.typography.labelMedium, color = ext.accent, modifier = Modifier.weight(1f))
                    StatusPill("Foto: ${item.imageQuality.label}", when (item.imageQuality) {
                        ImageQualityStatus.GOOD -> Tone.Positive
                        ImageQualityStatus.ACCEPTABLE -> Tone.Caution
                        else -> Tone.Alert
                    })
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(item.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (item.categories.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        item.categories.take(4).joinToString(" · ") { it.label },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.consultationRecommended) {
                    Spacer(Modifier.height(Spacing.xs))
                    StatusPill("Konsultasi disarankan", Tone.Alert)
                }
            }
            if (!selectionMode) {
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Hapus", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
