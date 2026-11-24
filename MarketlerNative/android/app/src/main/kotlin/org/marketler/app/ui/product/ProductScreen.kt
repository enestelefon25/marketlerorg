package org.marketler.app.ui.product

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.marketler.app.ui.UiState
import org.marketler.core.data.MarketlerRepository
import org.marketler.core.model.ProductDetail
import org.marketler.core.ui.CompareCard
import org.marketler.core.ui.DiscountBadge
import org.marketler.core.ui.ErrorState
import org.marketler.core.ui.FullScreenSkeleton
import org.marketler.core.ui.MarketBadge
import org.marketler.core.ui.MkColors
import org.marketler.core.ui.PriceBlock
import org.marketler.core.ui.ProductImagePlaceholder
import org.marketler.core.ui.SectionHeader
import org.marketler.core.ui.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(repo: MarketlerRepository, id: Long, onBack: () -> Unit, onOpenProduct: (Long) -> Unit) {
    val vm: ProductViewModel = viewModel(key = "product-$id") { ProductViewModel(repo, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MkColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Ürün detayı", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MkColors.Surface, titleContentColor = MkColors.Navy, navigationIconContentColor = MkColors.Navy),
            )
        },
    ) { pad ->
        when (val s = state) {
            is UiState.Loading -> FullScreenSkeleton(Modifier.padding(pad))
            is UiState.Error -> ErrorState(s.message, onRetry = vm::load, modifier = Modifier.padding(pad))
            is UiState.Content -> ProductContent(s.data, Modifier.padding(pad), onOpenProduct)
        }
    }
}

@Composable
private fun ProductContent(d: ProductDetail, modifier: Modifier, onOpenProduct: (Long) -> Unit) {
    val it = d.item
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "ust") {
            ProductImagePlaceholder(it.market, Modifier.fillMaxWidth().height(200.dp), iconSize = 56.dp)
        }
        item(key = "bilgi") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarketBadge(it.market, height = 32.dp)
                    Text(it.category.name.orEmpty(), color = MkColors.Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StatusPill(it.catalog.status, it.catalog.statusText)
                }
                Text(it.nameText, style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PriceBlock(it, big = true)
                    it.shownDiscountPct?.let { p -> DiscountBadge(p) }
                }
                it.shownUnitPrice?.let { u -> Text(u.text.orEmpty(), color = MkColors.Muted, fontWeight = FontWeight.SemiBold) }
                if (it.campaignLabel.isNotBlank()) Text("Kampanya: ${it.campaignLabel}", color = MkColors.Subtle, fontSize = 13.5.sp)
            }
        }
        d.history?.let { h ->
            item(key = "gecmis") {
                Surface(shape = RoundedCornerShape(14.dp), color = MkColors.Surface, border = BorderStroke(1.dp, MkColors.Line), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, contentDescription = null, tint = MkColors.Navy)
                        Text(h.text.ifBlank { "Aynı markette aynı adla başka katalog kaydı var." }, color = MkColors.Ink, fontSize = 14.sp)
                    }
                }
            }
        }
        item(key = "karsilastir") {
            SectionHeader("Diğer marketlerde", subtitle = "Aynı anda geçerli kataloglarda aynı ürün")
            Column(Modifier.padding(top = 10.dp)) {
                val g = d.compare
                if (g != null) CompareCard(g, onItemClick = { id -> if (id != it.id) onOpenProduct(id) }, currentItemId = it.id, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth())
                else Text(d.compareReason ?: "Bu ürün için kesin eşleşen ürün yok.", color = MkColors.Muted, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        d.catalog?.let { c ->
            item(key = "katalog") {
                SectionHeader("Katalog")
                Surface(shape = RoundedCornerShape(14.dp), color = MkColors.Surface, border = BorderStroke(1.dp, MkColors.Line), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(c.titleLabel, style = MaterialTheme.typography.titleSmall)
                        Text(listOf(c.dateLabel, "${c.itemCount} ürün").filter { s -> s.isNotBlank() }.joinToString(" · "), color = MkColors.Muted, fontSize = 13.sp)
                        Text("Broşür sayfaları ve katalog ekranı sonraki aşamada (FAZ 2.5) eklenecek.", color = MkColors.Subtle, fontSize = 12.5.sp)
                    }
                }
            }
        }
        item(key = "not") {
            Text("Fiyatlar yayınlanan kataloglardan derlenmiştir, mağazalarda değişiklik gösterebilir.", color = MkColors.Subtle, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)))
        }
    }
}
