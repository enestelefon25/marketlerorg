package org.marketler.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.marketler.app.ui.MarketlerAppRoot
import org.marketler.core.data.MockRepository
import org.marketler.core.ui.MarketlerTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationTest {
    @get:Rule val rule = createComposeRule()

    private val HOME = "Aradığın ürünün en uygun fiyatını bul."
    private val SEARCH = "Ürün ara… Örn. kahve, süt 1 L"
    private val CATALOGS = "Kataloglar: sonraki aşamada"
    private val COMPARE = "Karşılaştır: sonraki aşamada"
    private val ASSISTANT = "Akıllı Ürün Asistanı: sonraki aşamada"

    private fun start() {
        rule.setContent { MarketlerTheme { MarketlerAppRoot(MockRepository(latencyMs = 0)) } }
        waitFor(HOME)
    }
    private fun waitFor(text: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(text, substring = true).assertIsDisplayed()
    }
    private fun tab(label: String) { rule.onNode(hasText(label) and hasClickAction()).performClick(); rule.waitForIdle() }
    // Ana sayfa işareti: yalnızca ana sayfada bulunan logo (liste kaydırılmış olsa da görünür) + "Ana Sayfa" sekmesi seçili
    private fun onHome() { waitFor("MARKETLER.ORG"); rule.onNode(hasText("Ana Sayfa") and hasClickAction()).assertIsSelected() }

    @Test fun anaSayfa_aramaKutusundanSonra() {
        start()
        rule.onNodeWithText("Ürün adı yazın", substring = true).performClick(); waitFor(SEARCH)
        tab("Ana Sayfa"); onHome()
        tab("Ana Sayfa"); onHome()
    }

    @Test fun anaSayfa_tumKataloglarBaglantisindanSonra() {
        start()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Tüm kataloglar"))
        rule.onNodeWithText("Tüm kataloglar").performClick(); waitFor(CATALOGS)
        tab("Ana Sayfa"); onHome()
    }

    @Test fun anaSayfa_kategoriKisayolundanSonra() {
        start()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Kategoriler"))
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Elektronik"))
        rule.onNodeWithText("Elektronik").performClick(); waitFor(SEARCH)
        tab("Ana Sayfa"); onHome()
    }

    @Test fun tumSekmeler_sirayla_veHerBirindenAnaSayfa() {
        start()
        for ((label, marker) in listOf("Ara" to SEARCH, "Kataloglar" to CATALOGS, "Karşılaştır" to COMPARE, "Asistan" to ASSISTANT)) {
            tab(label); waitFor(marker)
            tab("Ana Sayfa"); onHome()
        }
        // sekmeden sekmeye doğrudan geçiş ve geri dönüş
        tab("Ara"); waitFor(SEARCH); tab("Kataloglar"); waitFor(CATALOGS); tab("Asistan"); waitFor(ASSISTANT)
        tab("Karşılaştır"); waitFor(COMPARE); tab("Ara"); waitFor(SEARCH); tab("Ana Sayfa"); onHome()
    }

    @Test fun aramaKutusu_sonra_AraSekmesi_sonra_AnaSayfa() {
        start()
        rule.onNodeWithText("Ürün adı yazın", substring = true).performClick(); waitFor(SEARCH)
        tab("Ara"); waitFor(SEARCH)
        tab("Ana Sayfa"); onHome()
        tab("Ara"); waitFor(SEARCH)
        tab("Ana Sayfa"); onHome()
    }
}
