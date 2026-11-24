package org.marketler.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.marketler.app.ui.userMessage
import org.marketler.core.data.MarketlerRepository
import org.marketler.core.data.ProductQuery
import org.marketler.core.model.Category
import org.marketler.core.model.Item
import org.marketler.core.model.Market
import org.marketler.core.model.Suggestion

data class SearchState(
    val input: String = "",
    val query: ProductQuery = ProductQuery(),
    val items: List<Item> = emptyList(),
    val total: Int = 0,
    /** Sunucu sayfalarına göre devam var mı (page*limit < total). Adsız ürünler süzüldüğü için öğe sayısıyla hesaplanmaz. */
    val serverHasMore: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val suggestions: List<Suggestion> = emptyList(),
    val showSuggestions: Boolean = false,
    val markets: List<Market> = emptyList(),
    val categories: List<Category> = emptyList(),
) {
    val hasMore: Boolean get() = serverHasMore
}

/** Arama: ara.php ile aynı süzgeçler (API /products) + yazarken öneri (API /suggest, 300 ms bekleme). */
class SearchViewModel(
    private val repo: MarketlerRepository,
    initialQuery: String, initialCategory: String?, initialMarket: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState(input = initialQuery, query = ProductQuery(q = initialQuery, category = initialCategory, market = initialMarket)))
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var suggestJob: Job? = null
    private var loadJob: Job? = null

    init {
        loadFilters()
        reload()
    }

    /** Süzgeç listeleri (market, kategori). Hata olursa sessizce boş kalır; sonraki yeniden yüklemede tekrar denenir. */
    private fun loadFilters() {
        viewModelScope.launch {
            runCatching { repo.markets() to repo.categories() }.onSuccess { (m, c) -> _state.update { it.copy(markets = m, categories = c) } }
        }
    }

    fun onInput(text: String) {
        _state.update { it.copy(input = text, showSuggestions = text.isNotBlank()) }
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(300)   // gereksiz istek göndermemek için bekleme (debounce)
            val s = runCatching { repo.suggest(text) }.getOrDefault(emptyList())
            _state.update { if (it.input == text) it.copy(suggestions = s) else it }
        }
    }

    fun submit(text: String = _state.value.input) {
        suggestJob?.cancel()
        _state.update { it.copy(input = text, showSuggestions = false, suggestions = emptyList(), query = it.query.copy(q = text.trim(), page = 1)) }
        reload()
    }

    fun hideSuggestions() = _state.update { it.copy(showSuggestions = false) }
    fun setMarket(slug: String?) { _state.update { it.copy(query = it.query.copy(market = slug, page = 1)) }; reload() }
    fun setCategory(slug: String?) { _state.update { it.copy(query = it.query.copy(category = slug, page = 1)) }; reload() }
    fun setStatus(s: ProductQuery.Status) { _state.update { it.copy(query = it.query.copy(status = s, page = 1)) }; reload() }
    fun setSort(s: ProductQuery.Sort) { _state.update { it.copy(query = it.query.copy(sort = s, page = 1)) }; reload() }

    fun reload() {
        loadJob?.cancel()
        if (_state.value.markets.isEmpty() || _state.value.categories.isEmpty()) loadFilters()
        _state.update { it.copy(loading = true, error = null, items = emptyList(), total = 0, serverHasMore = false) }
        val q = _state.value.query.copy(page = 1)
        loadJob = viewModelScope.launch {
            try { val p = repo.products(q); _state.update { it.copy(items = p.items.distinctBy { i -> i.id }, total = p.total, serverHasMore = p.hasMore, loading = false, query = q) } }
            catch (e: Exception) { _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    /** Sonraki sayfa hatasından sonra yeniden dene (liste korunur). */
    fun retryMore() { _state.update { it.copy(error = null) }; loadMore() }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore || s.error != null) return
        _state.update { it.copy(loadingMore = true) }
        val next = s.query.copy(page = s.query.page + 1)
        loadJob = viewModelScope.launch {
            /* Canlı veride sayfalar arasında kayıt değişebilir: aynı ürün iki kez eklenmez (liste anahtarı benzersiz kalır). */
            try {
                val p = repo.products(next)
                _state.update { st -> val seen = st.items.mapTo(HashSet()) { i -> i.id }; st.copy(items = st.items + p.items.filter { i -> seen.add(i.id) }, total = p.total, serverHasMore = p.hasMore, loadingMore = false, query = next) }
            }
            catch (e: Exception) { _state.update { it.copy(loadingMore = false, error = e.userMessage()) } }
        }
    }
}
