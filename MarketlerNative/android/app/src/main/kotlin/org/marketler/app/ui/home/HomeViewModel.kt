package org.marketler.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.marketler.app.ui.UiState
import org.marketler.app.ui.userMessage
import org.marketler.core.data.MarketlerRepository
import org.marketler.core.model.Home

class HomeViewModel(private val repo: MarketlerRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<Home>>(UiState.Loading)
    val state: StateFlow<UiState<Home>> = _state.asStateFlow()
    val isMock: Boolean get() = repo.isMock
    val generatedAt: String? get() = repo.dataGeneratedAt

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try { UiState.Content(repo.home()) } catch (e: Exception) { UiState.Error(e.userMessage()) }
        }
    }
}
