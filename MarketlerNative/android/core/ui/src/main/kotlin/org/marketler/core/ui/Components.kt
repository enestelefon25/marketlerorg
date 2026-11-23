package org.marketler.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.marketler.core.model.CatalogSummary
import org.marketler.core.model.CompareGroup
import org.marketler.core.model.Item
import org.marketler.core.model.MarketRef

/* ---------- yardımcılar ---------- */

/** Sunucudan gelen "#RRGGBB" market rengi; geçersizse lacivert. */
fun marketColor(hex: String): Color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { MkColors.Navy }

/** Sitenin img/ klasöründeki market logoları (uygulamaya gömülü). Bilinmeyen market için null → renkli ad rozeti. */
private fun marketLogoRes(slug: String): Int? = when (slug) {
    "bim" -> R.drawable.market_bim
    "a101" -> R.drawable.market_a101
    "sok" -> R.drawable.market_sok
    else -> null
}

/* ---------- logo ---------- */

/** Marketler.org'un mevcut logosu (sitenin modern teması): sepet simgesi + "MARKETLER" + kırmızı ".ORG". */
@Composable
fun MarketlerLogo(modifier: Modifier = Modifier, iconSize: Dp = 28.dp, textSize: Int = 19) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_marketler_cart), contentDescription = null, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = MkColors.Navy)) { append("MARKETLER") }
                withStyle(SpanStyle(color = MkColors.LogoRed)) { append(".ORG") }
            },
            fontSize = textSize.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp,
        )
    }
}

/* ---------- rozetler ---------- */

@Composable
fun MarketBadge(market: MarketRef, modifier: Modifier = Modifier, height: Dp = 34.dp) {
    val res = marketLogoRes(market.slug)
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White)
            .border(1.dp, MkColors.Line, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (res != null) Image(painterResource(res), contentDescription = market.name, contentScale = ContentScale.Fit, modifier = Modifier.height(height - 10.dp))
        else Text(market.name, color = marketColor(market.color), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    }
}

@Composable
fun DiscountBadge(pct: Int, modifier: Modifier = Modifier) {
    Text("-%$pct", modifier.clip(CircleShape).background(MkColors.DiscountBg).padding(horizontal = 9.dp, vertical = 2.dp),
        color = MkColors.DiscountFg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
}

/** Katalog durumu: live (aktif) yeşil · soon (yaklaşan) mavi · past (süresi dolmuş) gri. Metin sunucudan gelir. */
@Composable
fun StatusPill(status: String, text: String, modifier: Modifier = Modifier) {
    val (fg, bg) = when (status) { "live" -> MkColors.Best to MkColors.BestBg; "soon" -> MkColors.SoonFg to MkColors.SoonBg; else -> MkColors.PastFg to MkColors.PastBg }
    Text(text, modifier.clip(CircleShape).background(bg).padding(horizontal = 9.dp, vertical = 2.dp), color = fg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
}

/* ---------- ürün ---------- */

/** Görsel yoksa (çoğu üründe yok) sitedeki gibi market renginde yer tutucu. Sahte görsel kullanılmaz. */
@Composable
fun ProductImagePlaceholder(market: MarketRef, modifier: Modifier = Modifier, iconSize: Dp = 34.dp) {
    val c = marketColor(market.color)
    Box(modifier.background(Brush.verticalGradient(listOf(c.copy(alpha = 0.10f), c.copy(alpha = 0.20f)))), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.LocalOffer, contentDescription = null, tint = c.copy(alpha = 0.75f), modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PriceBlock(item: Item, modifier: Modifier = Modifier, big: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(item.priceText, color = MkColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = if (big) 30.sp else 22.sp, letterSpacing = (-0.3).sp)
        if (item.oldPriceText != null) Text(item.oldPriceText!!, color = MkColors.Subtle, textDecoration = TextDecoration.LineThrough, fontSize = if (big) 16.sp else 14.sp, modifier = Modifier.padding(bottom = 3.dp))
    }
}

/** Sitedeki modern ürün kartının (mc2) karşılığı. */
@Composable
fun ProductCard(item: Item, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, color = MkColors.Surface, border = androidx.compose.foundation.BorderStroke(1.dp, MkColors.Line)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarketBadge(item.market, height = 30.dp)
                Text(item.category.name, Modifier.weight(1f), color = MkColors.Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.discountPct?.let { DiscountBadge(it) }
            }
            ProductImagePlaceholder(item.market, Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)))
            Text(item.name, style = MaterialTheme.typography.titleSmall, color = MkColors.Ink, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            PriceBlock(item)
            item.unitPrice?.let { Text(it.text, color = MkColors.Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            item.noteLines.forEachIndexed { i, n -> Text(n, color = if (i == 0) MkColors.Best else MkColors.Subtle, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Text("Kampanya: ${item.campaignText}", color = MkColors.Subtle, fontSize = 12.5.sp)
        }
    }
}

/** Arama sonuçları için yatay satır. */
@Composable
fun ProductRow(item: Item, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        ProductImagePlaceholder(item.market, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), iconSize = 24.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, color = MkColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.market.name, color = marketColor(item.market.color), fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                Text("· ${item.category.name}", color = MkColors.Subtle, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.catalog.status != "live") StatusPill(item.catalog.status, item.catalog.statusText)
            }
            item.unitPrice?.let { Text(it.text, color = MkColors.Muted, fontSize = 12.5.sp) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(item.priceText, color = MkColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            item.oldPriceText?.let { Text(it, color = MkColors.Subtle, textDecoration = TextDecoration.LineThrough, fontSize = 12.5.sp) }
            item.discountPct?.let { Spacer(Modifier.height(2.dp)); DiscountBadge(it) }
        }
    }
}

/* ---------- katalog ---------- */

/** Katalog kartı. FAZ 2.1 (mock): ağ bağlantısı olmadığı için kapak görseli yüklenmez; market renginde kapak alanı gösterilir. */
@Composable
fun CatalogCard(catalog: CatalogSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = marketColor(catalog.market.color)
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, color = MkColors.Surface, border = androidx.compose.foundation.BorderStroke(1.dp, MkColors.Line)) {
        Column {
            Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.linearGradient(listOf(c.copy(alpha = 0.85f), c))), contentAlignment = Alignment.Center) {
                MarketBadge(catalog.market, height = 38.dp)
                StatusPill(catalog.status, catalog.statusText, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(catalog.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                Text(catalog.dateText, color = MkColors.Muted, fontSize = 13.sp)
                Text("${catalog.itemCount} ürün", color = MkColors.Subtle, fontSize = 12.5.sp)
            }
        }
    }
}

/* ---------- karşılaştırma ---------- */

/** Aynı ürünün marketlerdeki fiyatları. En düşük fiyat (sunucunun is_best işareti) yeşil vurgulanır; tasarruf cümlesi sunucudan gelir. */
@Composable
fun CompareCard(group: CompareGroup, onItemClick: (Long) -> Unit, modifier: Modifier = Modifier, currentItemId: Long? = null) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MkColors.Surface, border = androidx.compose.foundation.BorderStroke(1.dp, MkColors.Line)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(group.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            group.rows.forEach { r ->
                val best = r.isBest == true
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (best) MkColors.BestBg else MkColors.Background)
                        .border(if (r.id == currentItemId) 1.5.dp else 0.dp, if (r.id == currentItemId) MkColors.Navy else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { onItemClick(r.id) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MarketBadge(r.market, height = 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(r.campaignText, color = MkColors.Subtle, fontSize = 12.sp)
                        if (best) Text("En düşük", color = MkColors.Best, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                    }
                    Text(r.priceText, color = if (best) MkColors.Best else MkColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
            }
            group.savingText?.let { Text(it, color = MkColors.Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp) }
        }
    }
}

/* ---------- bölüm başlığı ve durumlar ---------- */

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MkColors.Ink)
            subtitle?.let { Text(it, color = MkColors.Muted, fontSize = 13.sp) }
        }
        if (action != null && onAction != null) Text(action, color = MkColors.Navy, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onAction).padding(6.dp))
    }
}

/** Mock modda veri kaynağının ve tarihinin açıkça gösterilmesi. */
@Composable
fun DataSourceBanner(isMock: Boolean, generatedAt: String?, modifier: Modifier = Modifier) {
    val text = (if (isMock) "Örnek veri (mock): gerçek fiyat değildir" else "Canlı veri") + (generatedAt?.let { " · güncelleme: ${it.take(16).replace('T', ' ')}" } ?: "")
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (isMock) Color(0xFFFFF7ED) else MkColors.BestBg).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = if (isMock) Color(0xFF9A3412) else MkColors.Best, modifier = Modifier.size(18.dp))
        Text(text, color = if (isMock) Color(0xFF9A3412) else MkColors.Best, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MessageState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = MkColors.Subtle, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, color = MkColors.Muted, textAlign = TextAlign.Center)
        if (action != null && onAction != null) Button(onClick = onAction) { Text(action) }
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) = MessageState(Icons.Outlined.SearchOff, title, message, modifier)

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) =
    MessageState(Icons.Outlined.CloudOff, "Bir sorun oluştu", message, modifier, "Tekrar dene", onRetry)

/** Yüklenirken içerik iskeleti (titreşen gri kutu). */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "iskelet")
    val a by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alfa")
    Box(modifier.clip(RoundedCornerShape(14.dp)).alpha(a).background(MkColors.Line))
}

@Composable
fun FullScreenSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SkeletonBox(Modifier.fillMaxWidth().height(52.dp))
        SkeletonBox(Modifier.fillMaxWidth().height(80.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(2) { SkeletonBox(Modifier.weight(1f).height(240.dp)) } }
        SkeletonBox(Modifier.fillMaxWidth().height(160.dp))
    }
}
