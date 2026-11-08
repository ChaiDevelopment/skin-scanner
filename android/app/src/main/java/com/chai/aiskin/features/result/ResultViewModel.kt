package com.chai.aiskin.features.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.domain.model.SkinAnalysis
import com.chai.aiskin.domain.repository.AnalysisRepository
import com.chai.aiskin.navigation.Route
import com.chai.aiskin.scanner.ScanSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ResultContent {
    data object Loading : ResultContent
    data class Loaded(val analysis: SkinAnalysis) : ResultContent
    data class Error(val error: AppError) : ResultContent
}

data class ResultUiState(
    val content: ResultContent = ResultContent.Loading,
    val fresh: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val feedbackSent: Boolean = false,
    val feedbackSending: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: AnalysisRepository,
    session: ScanSession,
) : ViewModel() {

    private val route = savedState.toRoute<Route.Result>()
    private val _state = MutableStateFlow(ResultUiState(fresh = route.fresh))
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    init {
        val cached = session.lastResult?.takeIf { it.id == route.analysisId }
        if (cached != null) _state.update { it.copy(content = ResultContent.Loaded(cached), feedbackSent = cached.feedback != null) }
        else load()
    }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(content = ResultContent.Loading) }
        when (val r = repository.get(route.analysisId)) {
            is AppResult.Success -> _state.update { it.copy(content = ResultContent.Loaded(r.data), feedbackSent = r.data.feedback != null) }
            is AppResult.Failure -> _state.update { it.copy(content = ResultContent.Error(r.error)) }
        }
    }

    fun delete() {
        if (_state.value.deleting) return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true) }
            when (val r = repository.delete(route.analysisId)) {
                is AppResult.Success -> _state.update { it.copy(deleting = false, deleted = true) }
                is AppResult.Failure -> _state.update { it.copy(deleting = false, message = r.error.message) }
            }
        }
    }

    fun deletePhoto() = viewModelScope.launch {
        when (val r = repository.deletePhoto(route.analysisId)) {
            is AppResult.Success -> {
                _state.update { s ->
                    val c = s.content
                    if (c is ResultContent.Loaded) s.copy(content = ResultContent.Loaded(c.analysis.copy(photoStored = false, photoUrl = null)), message = "Foto telah dihapus.")
                    else s
                }
            }
            is AppResult.Failure -> _state.update { it.copy(message = r.error.message) }
        }
    }

    fun sendFeedback(helpful: Boolean, rating: Int?, comment: String?) {
        if (_state.value.feedbackSending) return
        viewModelScope.launch {
            _state.update { it.copy(feedbackSending = true) }
            when (val r = repository.sendFeedback(route.analysisId, helpful, rating, comment)) {
                is AppResult.Success -> _state.update { it.copy(feedbackSending = false, feedbackSent = true, message = "Terima kasih atas masukan Anda.") }
                is AppResult.Failure -> _state.update { it.copy(feedbackSending = false, message = r.error.message) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
