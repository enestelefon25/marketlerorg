package org.marketler.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.marketler.app.ui.home.HomeScreen
import org.marketler.app.ui.product.ProductScreen
import org.marketler.app.ui.search.SearchScreen
import org.marketler.core.data.MarketlerRepository
import org.marketler.core.ui.MkColors

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Ana Sayfa", Icons.Outlined.Home),
    Tab("search", "Ara", Icons.Outlined.Search),
    Tab("catalogs", "Kataloglar", Icons.Outlined.GridView),
    Tab("compare", "Karşılaştır", Icons.Outlined.Balance),
    Tab("assistant", "Asistan", Icons.Outlined.SmartToy),
)

/** Arama ekranı rotası: isteğe bağlı başlangıç sorgusu, kategori, market. */
fun searchRoute(q: String? = null, category: String? = null, market: String? = null, focus: Boolean = false): String =
    "search?q=${Uri.encode(q ?: "")}&category=${Uri.encode(category ?: "")}&market=${Uri.encode(market ?: "")}&focus=$focus"

fun NavHostController.openProduct(id: Long) = navigate("product/$id")

@Composable
fun MarketlerAppRoot(repo: MarketlerRepository) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val topLevel = tabs.any { route.startsWith(it.route) }
    Scaffold(
        containerColor = MkColors.Background,
        bottomBar = {
            if (topLevel) NavigationBar(containerColor = MkColors.Surface) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route.startsWith(t.route),
                        onClick = {
                            nav.navigate(if (t.route == "search") searchRoute() else t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        // Düzeltme: 5 sekmede "Kataloglar" yazısı kesiliyordu ("Katalogla"); yazı küçültüldü ve satır kırılmaz.
                        label = { Text(t.label, maxLines = 1, softWrap = false, fontSize = 11.sp, letterSpacing = 0.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MkColors.Navy, selectedTextColor = MkColors.Navy, indicatorColor = MkColors.Line),
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(bottom = pad.calculateBottomPadding())) {
            composable("home") {
                HomeScreen(repo,
                    onOpenSearch = { nav.navigate(searchRoute(focus = true)) },
                    onOpenProduct = { nav.openProduct(it) },
                    onOpenCategory = { nav.navigate(searchRoute(category = it)) },
                    onOpenMarket = { nav.navigate(searchRoute(market = it)) },
                    onOpenTab = { r -> nav.navigate(r) { launchSingleTop = true } })
            }
            composable(
                "search?q={q}&category={category}&market={market}&focus={focus}",
                arguments = listOf(
                    navArgument("q") { type = NavType.StringType; defaultValue = "" },
                    navArgument("category") { type = NavType.StringType; defaultValue = "" },
                    navArgument("market") { type = NavType.StringType; defaultValue = "" },
                    navArgument("focus") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { e ->
                val a = e.arguments
                SearchScreen(repo,
                    initialQuery = a?.getString("q").orEmpty(),
                    initialCategory = a?.getString("category")?.takeIf { it.isNotEmpty() },
                    initialMarket = a?.getString("market")?.takeIf { it.isNotEmpty() },
                    autoFocus = a?.getBoolean("focus") ?: false,
                    onOpenProduct = { nav.openProduct(it) })
            }
            composable("product/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                ProductScreen(repo, id = e.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() }, onOpenProduct = { nav.openProduct(it) })
            }
            composable("catalogs") { ComingSoonScreen("Kataloglar", "Katalog listesi ve broşür sayfaları FAZ 2.5'te eklenecek.") }
            composable("compare") { ComingSoonScreen("Karşılaştır", "Fiyat karşılaştırma ekranı FAZ 2.5'te eklenecek. Ürün detayında aynı ürünün diğer marketlerdeki fiyatları şimdiden görünür.") }
            composable("assistant") { ComingSoonScreen("Akıllı Ürün Asistanı", "Sohbet ekranı FAZ 2.6'da eklenecek.") }
        }
    }
}
