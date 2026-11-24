package org.marketler.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.marketler.core.data.MarketlerRepository
import org.marketler.core.data.ProductQuery
import org.marketler.core.ui.EmptyState
import org.marketler.core.ui.ErrorState
import org.marketler.core.ui.MkColors
import org.marketler.core.ui.ProductRow
import org.marketler.core.ui.SkeletonBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repo: MarketlerRepository,
    initialQuery: String, initialCategory: String?, initialMarket: String?, autoFocus: Boolean,
    onOpenProduct: (Long) -> Unit,
) {
    val vm: SearchViewModel = viewModel { SearchViewModel(repo, initialQuery, initialCategory, initialMarket) }
    val s by vm.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { if (autoFocus) focus.requestFocus() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = s.input, onValueChange = vm::onInput, singleLine = true,
            placeholder = { Text("Ürün ara… Örn. kahve, süt 1 L") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = { if (s.input.isNotEmpty()) IconButton(onClick = { vm.onInput(""); vm.submit("") }) { Icon(Icons.Outlined.Close, contentDescription = "Temizle") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.submit(); focusManager.clearFocus() }),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MkColors.Navy, unfocusedBorderColor = MkColors.Line, focusedContainerColor = MkColors.Surface, unfocusedContainerColor = MkColors.Surface),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).focusRequester(focus),
        )

        if (s.showSuggestions && s.suggestions.isNotEmpty()) {
            Surface(color = MkColors.Surface, modifier = Modifier.fillMaxWidth()) {
                Column {
                    s.suggestions.forEach { sg ->
                        Row(Modifier.fillMaxWidth().clickable { vm.submit(sg.name.orEmpty()); focusManager.clearFocus() }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.Search, contentDescription = null, tint = MkColors.Subtle, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sg.name.orEmpty(), fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(listOfNotNull(sg.marketName, sg.priceText).filter { t -> t.isNotBlank() }.joinToString(" · "), color = MkColors.Muted, fontSize = 12.5.sp)
                            }
                            Icon(Icons.Outlined.NorthWest, contentDescription = null, tint = MkColors.Subtle, modifier = Modifier.size(18.dp))
                        }
                        HorizontalDivider(color = MkColors.Line)
                    }
                }
            }
        }

        Filters(s, vm)

        Text(
            if (s.loading) "Aranıyor…" else "${s.total} ürün bulundu",
            color = MkColors.Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        when {
            s.loading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(6) { SkeletonBox(Modifier.fillMaxWidth().height(64.dp)) } }
            s.error != null && s.items.isEmpty() -> ErrorState(s.error!!, onRetry = vm::reload)
            s.items.isEmpty() -> EmptyState("Sonuç bulunamadı", "Aramana uyan ürün yok. Yazımı kontrol et, daha genel bir kelime dene veya süzgeçleri değiştir.")
            else -> Results(s, vm, onOpenProduct)
        }
    }
}

@Composable
private fun Results(s: SearchState, vm: SearchViewModel, onOpenProduct: (Long) -> Unit) {
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, s.items.size) { if (nearEnd) vm.loadMore() }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        itemsIndexed(s.items, key = { _, it -> it.id }) { i, it ->
            ProductRow(it, onClick = { onOpenProduct(it.id) })
            if (i < s.items.lastIndex) HorizontalDivider(color = MkColors.Line, modifier = Modifier.padding(start = 92.dp))
        }
        if (s.loadingMore) item(key = "more") { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), color = MkColors.Navy) } }
    }
}

/** Süzgeçler: market, kategori, katalog durumu, sıralama (API /products parametreleri). */
@Composable
private fun Filters(s: SearchState, vm: SearchViewModel) {
    val marketName = s.markets.firstOrNull { it.slug == s.query.market }?.label
    val categoryName = s.categories.firstOrNull { it.slug == s.query.category }?.name
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Picker(marketName ?: "Market", s.query.market != null, listOf<Pair<String?, String>>(null to "Tüm marketler") + s.markets.map { it.slug to it.label }, vm::setMarket) }
        item { Picker(categoryName ?: "Kategori", s.query.category != null, listOf<Pair<String?, String>>(null to "Tüm kategoriler") + s.categories.map { it.slug to it.name.orEmpty() }, vm::setCategory) }
        item { Picker("Durum: ${s.query.status.label}", s.query.status != ProductQuery.Status.AKTIF, ProductQuery.Status.values().map { it to it.label }) { vm.setStatus(it ?: ProductQuery.Status.AKTIF) } }
        item { Picker(s.query.sort.label, s.query.sort != ProductQuery.Sort.NEW, ProductQuery.Sort.values().map { it to it.label }) { vm.setSort(it ?: ProductQuery.Sort.NEW) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Picker(label: String, active: Boolean, options: List<Pair<T?, String>>, onPick: (T?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active, onClick = { open = true },
            label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MkColors.Navy, selectedLabelColor = MkColors.Surface, selectedTrailingIconColor = MkColors.Surface),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { open = false; onPick(v) }) }
        }
    }
}
