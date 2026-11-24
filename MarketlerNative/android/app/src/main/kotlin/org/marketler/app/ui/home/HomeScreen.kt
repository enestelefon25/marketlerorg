package org.marketler.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.marketler.app.ui.UiState
import org.marketler.core.data.MarketlerRepository
import org.marketler.core.model.Home
import org.marketler.core.model.Market
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
            Grid(home.markets, columns = 3, spacing = 10.dp) { m, mod -> MarketTile(m, onClick = { m.slug?.let(onOpenMarket) }, modifier = mod) }
        }
        item(key = "featured") {
            SectionHeader(home.featured.title.orEmpty(), subtitle = home.featured.subtitle, action = "Tüm ürünler", onAction = { onOpenCategory("") })
            Spacer(Modifier.height(10.dp))
            if (home.featured.items.isEmpty()) EmptyState("Şu an listelenecek ürün yok.", "Güncel kataloglarda ürün olduğunda burada gerçek fiyatlarıyla görünür.")
            else Grid(home.featured.items, columns = 2, spacing = 12.dp) { it, mod -> ProductCard(it, onClick = { onOpenProduct(it.id) }, modifier = mod) }
        }
        item(key = "live") {
            SectionHeader("Güncel kataloglar", action = "Tüm kataloglar", onAction = { onOpenTab("catalogs") })
            Spacer(Modifier.height(10.dp))
            if (home.liveCatalogs.isEmpty()) EmptyState("Şu an aktif katalog yok.", "Yeni kataloglar yayınlandığında burada görünür.")
            else Grid(home.liveCatalogs, columns = 2, spacing = 12.dp) { it, mod -> CatalogCard(it, onClick = { onOpenTab("catalogs") }, modifier = mod) }
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
                            Text(c.titleLabel, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            if (c.dateLabel.isNotBlank()) Text(c.dateLabel, color = MkColors.Muted, fontSize = 13.sp)
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

/**
 * Ana sayfa ızgarası: öğeleri ekran genişliğine sığan sütunlara böler (yana kaydırma YOK).
 * Düzeltme (telefon denemesi 1): önceki yatay şeritler sayfanın sağa sola kaymasına yol açıyordu.
 * Aynı satırdaki kartlar eşit yükseklikte; eksik kalan hücre boş bırakılır.
 */
@Composable
private fun <T> Grid(items: List<T>, columns: Int, spacing: Dp, cell: @Composable (T, Modifier) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { cell(it, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MarketTile(m: Market, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MkColors.Surface, border = BorderStroke(1.dp, MkColors.Line), modifier = modifier) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MarketBadge(m.ref, height = 32.dp)
            Text(m.label, fontWeight = FontWeight.ExtraBold, color = MkColors.Ink)
            Text(if (m.liveCount > 0) "${m.liveCount} güncel katalog" else "Şu an aktif katalog yok", color = MkColors.Muted, fontSize = 12.5.sp)
        }
    }
}
