package org.marketler.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.marketler.app.ui.UiState
import org.marketler.core.data.MarketlerRepository
import org.marketler.core.model.Home
import org.marketler.core.model.Market
import org.marketler.core.model.MarketRef
import org.marketler.core.ui.CatalogCard
import org.marketler.core.ui.CompareCard
import org.marketler.core.ui.DataSourceBanner
import org.marketler.core.ui.EmptyState
import org.marketler.core.ui.ErrorState
import org.marketler.core.ui.FullScreenSkeleton
import org.marketler.core.ui.MarketBadge
import org.marketler.core.ui.MarketlerLogo
import org.marketler.core.ui.MkColors
import org.marketler.core.ui.ProductCard
import org.marketler.core.ui.SectionHeader
import org.marketler.core.ui.StatusPill

@Composable
fun HomeScreen(
    repo: MarketlerRepository,
    onOpenSearch: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenMarket: (String) -> Unit,
    onOpenTab: (String) -> Unit,
) {
    val vm: HomeViewModel = viewModel { HomeViewModel(repo) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { MarketlerLogo() }
        when (val s = state) {
            is UiState.Loading -> FullScreenSkeleton()
            is UiState.Error -> ErrorState(s.message, onRetry = vm::load)
            is UiState.Content -> HomeContent(s.data, vm.isMock, vm.generatedAt, onOpenSearch, onOpenProduct, onOpenCategory, onOpenMarket, onOpenTab)
        }
    }
}

@Composable
private fun HomeContent(
    home: Home, isMock: Boolean, generatedAt: String?,
    onOpenSearch: () -> Unit, onOpenProduct: (Long) -> Unit, onOpenCategory: (String) -> Unit, onOpenMarket: (String) -> Unit, onOpenTab: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(key = "hero") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Aradığın ürünün en uygun fiyatını bul.", style = MaterialTheme.typography.headlineSmall)
                Text("Marketlerdeki güncel ürünleri ve fiyatları tek yerde karşılaştır.", color = MkColors.Muted)
                Surface(onClick = onOpenSearch, shape = RoundedCornerShape(14.dp), color = MkColors.Surface, border = BorderStroke(1.dp, MkColors.Line), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = MkColors.Muted)
                        Spacer(Modifier.width(10.dp))
                        Text("Ürün adı yazın… Örn: ayçiçek yağı, süt", color = MkColors.Subtle)
                    }
                }
                DataSourceBanner(isMock, generatedAt)
            }
        }
        item(key = "markets") {
            SectionHeader("Marketler")
            Spacer(Modifier.height(10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(home.markets, key = { it.id }) { m -> MarketTile(m, onClick = { onOpenMarket(m.slug) }) }
            }
        }
        item(key = "featured") {
            SectionHeader(home.featured.title, subtitle = home.featured.subtitle, action = "Tüm ürünler", onAction = { onOpenCategory("") })
            Spacer(Modifier.height(10.dp))
            if (home.featured.items.isEmpty()) EmptyState("Şu an listelenecek ürün yok.", "Güncel kataloglarda ürün olduğunda burada gerçek fiyatlarıyla görünür.")
            else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(home.featured.items, key = { it.id }) { ProductCard(it, onClick = { onOpenProduct(it.id) }, modifier = Modifier.width(250.dp)) }
            }
        }
        item(key = "live") {
            SectionHeader("Güncel kataloglar", action = "Tüm kataloglar", onAction = { onOpenTab("catalogs") })
            Spacer(Modifier.height(10.dp))
            if (home.liveCatalogs.isEmpty()) EmptyState("Şu an aktif katalog yok.", "Yeni kataloglar yayınlandığında burada görünür.")
            else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(home.liveCatalogs, key = { it.id }) { CatalogCard(it, onClick = { onOpenTab("catalogs") }, modifier = Modifier.width(220.dp)) }
            }
        }
        item(key = "compare") {
            SectionHeader("Aynı ürün, farklı marketler", subtitle = "Aynı anda geçerli kataloglarda bulunan aynı ürünler")
            Spacer(Modifier.height(10.dp))
            val g = home.topCompare
            if (g == null) Text("Şu an eşleşen ürün yok. Aynı anda geçerli kataloglarda aynı ürün bulunduğunda burada görünür.", color = MkColors.Muted, modifier = Modifier.padding(horizontal = 16.dp))
            else CompareCard(g, onItemClick = onOpenProduct, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth())
        }
        item(key = "categories") {
            SectionHeader("Kategoriler")
            Spacer(Modifier.height(10.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                home.categoryShortcuts.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { c ->
                            Surface(onClick = { onOpenCategory(c.slug) }, shape = RoundedCornerShape(14.dp), color = MkColors.Surface, border = BorderStroke(1.dp, MkColors.Line), modifier = Modifier.weight(1f).height(52.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text(c.name, fontWeight = FontWeight.SemiBold, color = MkColors.Navy) }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (home.soonCatalogs.isNotEmpty()) item(key = "soon") {
            SectionHeader("Yaklaşan kataloglar", subtitle = "Henüz başlamadı")
            Spacer(Modifier.height(10.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                home.soonCatalogs.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenTab("catalogs") }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MarketBadge(c.market, height = 30.dp)
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(c.dateText, color = MkColors.Muted, fontSize = 13.sp)
                        }
                        StatusPill(c.status, c.statusText)
                    }
                }
            }
        }
        item(key = "footer") {
            Text("BİM, A101 ve ŞOK ilgili şirketlerin markalarıdır. Fiyatlar yayınlanan kataloglardan derlenmiştir, mağazalarda değişiklik gösterebilir.",
                color = MkColors.Subtle, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        }
    }
}

@Composable
private fun MarketTile(m: Market, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MkColors.Surface, border = BorderStroke(1.dp, MkColors.Line), modifier = Modifier.width(150.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MarketBadge(MarketRef(m.id, m.slug, m.name, m.color), height = 36.dp)
            Text(m.name, fontWeight = FontWeight.ExtraBold, color = MkColors.Ink)
            Text(if (m.liveCount > 0) "${m.liveCount} güncel katalog" else "Şu an aktif katalog yok", color = MkColors.Muted, fontSize = 12.5.sp)
        }
    }
}
