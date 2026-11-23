package org.marketler.app.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.marketler.app.ui.UiState
import org.marketler.app.ui.userMessage
import org.marketler.core.data.MarketlerRepository
import org.marketler.core.model.ProductDetail

class ProductViewModel(private val repo: MarketlerRepository, private val id: Long) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ProductDetail>>(UiState.Loading)
    val state: StateFlow<UiState<ProductDetail>> = _state.asStateFlow()
    init { load() }
    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch { _state.value = try { UiState.Content(repo.product(id)) } catch (e: Exception) { UiState.Error(e.userMessage()) } }
    }
}
