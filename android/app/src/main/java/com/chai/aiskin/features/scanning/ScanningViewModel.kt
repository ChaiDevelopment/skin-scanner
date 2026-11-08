package com.chai.aiskin.features.scanning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.domain.usecase.AnalyzeSkinUseCase
import com.chai.aiskin.scanner.ScanSession
import com.chai.aiskin.scanner.ScanStage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScanningUiState {
    data class InProgress(val stage: ScanStage, val stageIndex: Int, val totalStages: Int) : ScanningUiState
    data class Done(val analysisId: String) : ScanningUiState
    data class Failed(val error: AppError) : ScanningUiState
    data object NoPhoto : ScanningUiState
}

@HiltViewModel
class ScanningViewModel @Inject constructor(
    private val analyze: AnalyzeSkinUseCase,
    private val session: ScanSession,
) : ViewModel() {

    private val stages = ScanStage.entries
    private val _state = MutableStateFlow<ScanningUiState>(ScanningUiState.InProgress(stages[0], 0, stages.size))
    val state: StateFlow<ScanningUiState> = _state.asStateFlow()

    private var job: Job? = null

    init { start() }

    /** Starts (or retries) the upload. Ignored while a request is already running → no duplicate submissions. */
    fun start() {
        if (job?.isActive == true) return
        val scan = session.capture.value
        if (scan == null) {
            _state.value = ScanningUiState.NoPhoto
            return
        }
        job = viewModelScope.launch {
            _state.value = ScanningUiState.InProgress(stages[0], 0, stages.size)
            val ticker = launch {
                // Informative progress only — the backend does the actual work in one request.
                var i = 0
                while (isActive && i < stages.lastIndex) {
                    delay(if (i == 0) 900 else 2600)
                    i++
                    _state.update { s -> if (s is ScanningUiState.InProgress) s.copy(stage = stages[i], stageIndex = i) else s }
                }
            }
            val result = analyze(scan.file, session.storePhoto)
            ticker.cancel()
            _state.value = when (result) {
                is AppResult.Success -> {
                    session.completeWith(result.data)
                    ScanningUiState.Done(result.data.id)
                }
                is AppResult.Failure -> ScanningUiState.Failed(result.error)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Leaving after a quality failure: drop the photo so the next attempt starts fresh. */
    fun discardPhoto() = session.clearCapture()
}
