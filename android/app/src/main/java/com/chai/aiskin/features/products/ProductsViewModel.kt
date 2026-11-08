package com.chai.aiskin.features.products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.domain.model.ProductSuggestions
import com.chai.aiskin.domain.repository.AnalysisRepository
import com.chai.aiskin.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProductsContent {
    data object Loading : ProductsContent
    data class Loaded(val suggestions: ProductSuggestions) : ProductsContent
    data class Error(val error: AppError) : ProductsContent
}

data class ProductsUiState(
    val content: ProductsContent = ProductsContent.Loading,
    /** A "cari ulang" request is running while the previous result stays visible. */
    val refreshing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ProductsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: AnalysisRepository,
) : ViewModel() {

    private val analysisId = savedState.toRoute<Route.Products>().analysisId
    private val _state = MutableStateFlow(ProductsUiState())
    val state: StateFlow<ProductsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(content = ProductsContent.Loading) }
            when (val r = repository.findProducts(analysisId, refresh = false)) {
                is AppResult.Success -> _state.update { it.copy(content = ProductsContent.Loaded(r.data)) }
                is AppResult.Failure -> _state.update { it.copy(content = ProductsContent.Error(r.error)) }
            }
        }
    }

    /** Asks the AI again; on failure the previous suggestions stay on screen. */
    fun refresh() {
        if (_state.value.refreshing || _state.value.content !is ProductsContent.Loaded) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val r = repository.findProducts(analysisId, refresh = true)) {
                is AppResult.Success -> _state.update {
                    it.copy(content = ProductsContent.Loaded(r.data), refreshing = false, message = "Rekomendasi diperbarui.")
                }
                is AppResult.Failure -> _state.update { it.copy(refreshing = false, message = r.error.message) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
