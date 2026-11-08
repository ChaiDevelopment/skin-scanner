package com.chai.aiskin.features.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.core.constants.AppConstants
import com.chai.aiskin.domain.model.HistoryItem
import com.chai.aiskin.domain.repository.AnalysisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val nextCursor: String? = null,
    val initialLoading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: AppError? = null,
    val loadMoreError: String? = null,
    val selected: Set<String> = emptySet(),
    val message: String? = null,
) {
    val isEmpty get() = !initialLoading && error == null && items.isEmpty()
    val canLoadMore get() = nextCursor != null && !loadingMore && loadMoreError == null
    val canCompare get() = selected.size == 2
}

/** Cursor-paginated history (page size [AppConstants.HISTORY_PAGE_SIZE]); never downloads everything at once. */
@HiltViewModel
class HistoryViewModel @Inject constructor(private val repository: AnalysisRepository) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()
    private var pageJob: Job? = null

    init { refresh() }

    /** Reloads the first page. [silent] keeps current items visible (used on resume). */
    fun refresh(silent: Boolean = false) {
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            if (!silent) _state.update { it.copy(initialLoading = true, error = null) }
            when (val r = repository.history(null, AppConstants.HISTORY_PAGE_SIZE)) {
                is AppResult.Success -> _state.update { s ->
                    s.copy(
                        items = r.data.items, nextCursor = r.data.nextCursor, initialLoading = false, error = null, loadMoreError = null,
                        selected = s.selected.filter { id -> r.data.items.any { it.id == id } }.toSet(),
                    )
                }
                is AppResult.Failure -> _state.update {
                    if (silent && it.items.isNotEmpty()) it.copy(initialLoading = false) else it.copy(initialLoading = false, error = r.error)
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore || pageJob?.isActive == true) return
        pageJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, loadMoreError = null) }
            when (val r = repository.history(cursor, AppConstants.HISTORY_PAGE_SIZE)) {
                is AppResult.Success -> _state.update { st ->
                    val known = st.items.map { it.id }.toSet()
                    st.copy(items = st.items + r.data.items.filterNot { it.id in known }, nextCursor = r.data.nextCursor, loadingMore = false)
                }
                is AppResult.Failure -> _state.update { it.copy(loadingMore = false, loadMoreError = r.error.message) }
            }
        }
    }

    fun retryLoadMore() {
        _state.update { it.copy(loadMoreError = null) }
        loadMore()
    }

    /** Select up to two scans for comparison. */
    fun toggleSelect(id: String) = _state.update { s ->
        when {
            id in s.selected -> s.copy(selected = s.selected - id)
            s.selected.size >= 2 -> s.copy(message = "Pilih maksimal dua analisis untuk dibandingkan.")
            else -> s.copy(selected = s.selected + id)
        }
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    /** Optimistic delete with rollback on failure. */
    fun delete(id: String) {
        val before = _state.value.items
        _state.update { s -> s.copy(items = s.items.filterNot { it.id == id }, selected = s.selected - id) }
        viewModelScope.launch {
            when (val r = repository.delete(id)) {
                is AppResult.Success -> _state.update { it.copy(message = "Analisis dihapus.") }
                is AppResult.Failure -> _state.update { it.copy(items = before, message = r.error.message) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
