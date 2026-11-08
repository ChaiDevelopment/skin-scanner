package com.chai.aiskin.features.history

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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.core.common.formatDateTime
import com.chai.aiskin.core.designsystem.AiSkinTheme
import com.chai.aiskin.core.designsystem.AppCard
import com.chai.aiskin.core.designsystem.AppTopBar
import com.chai.aiskin.core.designsystem.ErrorView
import com.chai.aiskin.core.designsystem.Eyebrow
import com.chai.aiskin.core.designsystem.HairlineDivider
import com.chai.aiskin.core.designsystem.LoadingView
import com.chai.aiskin.core.designsystem.NoticeBanner
import com.chai.aiskin.core.designsystem.Spacing
import com.chai.aiskin.core.designsystem.StatusPill
import com.chai.aiskin.core.designsystem.Tone
import com.chai.aiskin.domain.repository.AnalysisRepository
import com.chai.aiskin.domain.usecase.CompareAnalysesUseCase
import com.chai.aiskin.domain.usecase.Comparison
import com.chai.aiskin.domain.usecase.VisualChange
import com.chai.aiskin.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CompareUiState {
    data object Loading : CompareUiState
    data class Ready(val comparison: Comparison) : CompareUiState
    data class Error(val error: AppError) : CompareUiState
}

@HiltViewModel
class CompareViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: AnalysisRepository,
    private val compare: CompareAnalysesUseCase,
) : ViewModel() {
    private val route = savedState.toRoute<Route.Compare>()
    private val _state = MutableStateFlow<CompareUiState>(CompareUiState.Loading)
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = CompareUiState.Loading
        val a = async { repository.get(route.firstId) }
        val b = async { repository.get(route.secondId) }
        val ra = a.await()
        val rb = b.await()
        _state.value = when {
            ra is AppResult.Failure -> CompareUiState.Error(ra.error)
            rb is AppResult.Failure -> CompareUiState.Error(rb.error)
            else -> CompareUiState.Ready(compare((ra as AppResult.Success).data, (rb as AppResult.Success).data))
        }
    }
}

@Composable
fun CompareScreen(onBack: () -> Unit, vm: CompareViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("Perbandingan", onBack = onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        when (val st = s) {
            CompareUiState.Loading -> LoadingView("Memuat dua analisis...", Modifier.padding(padding))
            is CompareUiState.Error -> ErrorView("Gagal memuat perbandingan", st.error.message, Modifier.padding(padding), onRetry = { vm.load() })
            is CompareUiState.Ready -> ComparisonBody(st.comparison, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ComparisonBody(c: Comparison, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        NoticeBanner(c.disclaimer, tone = Tone.Caution, icon = Icons.Outlined.Info)
        c.qualityWarning?.let { NoticeBanner(it, tone = Tone.Alert) }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { Eyebrow("Sebelumnya"); Text(c.older.createdAt.formatDateTime(), style = MaterialTheme.typography.bodySmall) }
            Column(Modifier.weight(1f)) { Eyebrow("Terbaru"); Text(c.newer.createdAt.formatDateTime(), style = MaterialTheme.typography.bodySmall) }
        }
        AppCard {
            Text("Atribut visual", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.sm))
            c.rows.forEachIndexed { i, row ->
                Column(Modifier.padding(vertical = 10.dp)) {
                    Row {
                        Text(row.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        StatusPill(row.change.label, when (row.change) {
                            VisualChange.NOT_COMPARABLE -> Tone.Neutral
                            else -> Tone.Accent
                        })
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(row.olderLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("→", style = MaterialTheme.typography.bodySmall, color = AiSkinTheme.extended.accent)
                        Text(row.newerLabel, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                }
                if (i < c.rows.lastIndex) HairlineDivider()
            }
        }
        AppCard {
            Eyebrow("Ringkasan terbaru")
            Spacer(Modifier.height(Spacing.xs))
            Text(c.newer.overallSummary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.md))
            Eyebrow("Ringkasan sebelumnya")
            Spacer(Modifier.height(Spacing.xs))
            Text(c.older.overallSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}
