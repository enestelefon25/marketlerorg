package org.marketler.app

import android.app.Application
import org.marketler.core.data.MarketlerRepository
import org.marketler.core.data.MockRepository

/**
 * Uygulama kapsayıcısı (basit bağımlılık yönetimi).
 * FAZ 2.1: yalnızca MockRepository: gömülü JSON, ağ bağlantısı yok (manifestte INTERNET izni de yok).
 * Not: Plan Hilt öngörüyor; derlemesi bu ortamda doğrulanamayan ek eklenti riskini azaltmak için FAZ 2.1'de elle
 * bağlama kullanıldı. Gerçek API aşamasında Hilt'e geçiş ekranları değiştirmez.
 */
class MarketlerApplication : Application() {
    val repository: MarketlerRepository by lazy { MockRepository() }
}
