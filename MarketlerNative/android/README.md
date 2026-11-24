# Marketler.org: Native Android Uygulaması (FAZ 2.2.2)

**Bu sürüm:** `0.2.2.2`. Ana sayfa, arama ve ürün detay ekranları; veri kaynağı derleme sürümüne göre: örnek veri, yerel API kopyası veya canlı API (canlı erişim onaya kadar kapalı).

## Sürümler (build variant) — FAZ 2.2.2
| Sürüm | Veri | Ağ | Uygulama kimliği (debug) | Telefondaki ad |
|---|---|---|---|---|
| `mock` | Gömülü örnek veri | **INTERNET izni yok** | `org.marketler.app.mock.dev` | Marketler (örnek) |
| `yerel` (yalnızca debug) | Bilgisayardaki yerel Rev3 API kopyası | HTTP **yalnızca** `10.0.2.2` (emülatör → bilgisayar) | `org.marketler.app.yerel.dev` | Marketler (yerel) |
| `canli` | `https://marketler.org/api/v1/` | Yalnızca HTTPS, yalnızca marketler.org | `org.marketler.app.dev` | Marketler.org |

- `canli` sürümünde `CANLI_API_ACIK=false`: canlıya alma onayına kadar uygulama canlı API'ye **hiç istek göndermez**, ekranlarda "Canlı veri henüz yayında değil" yazar. Onaydan sonra `app/build.gradle.kts` içinde tek satır `true` yapılır.
- `yerel` için bilgisayarda yerel API açık olmalı: `php MarketlerNative/tools/api_test_verisi_uret.php --site=<site klasörü> --acik-kal=3600` (yerel MariaDB gerekir). Bu sürüm yalnızca **Android emülatöründe** çalışır (10.0.2.2 = emülatörün bilgisayara ulaştığı adres).

## Android Studio ile açma ve APK üretme
1. Android Studio → **Open** → bu `android` klasörünü seçin (içinde `settings.gradle.kts` olan klasör). "Trust Project" deyin.
2. Gradle senkronizasyonunu bekleyin. Araç sürümleri: AGP 8.12.0, Kotlin 2.2.0, Gradle 8.14.3, compileSdk 36. "AGP Upgrade" önerisini şimdilik kapatın.
3. Sol kenardaki **Build Variants** panelinden sürümü seçin: `mockDebug`, `yerelDebug` veya `canliDebug`.
4. **Kurulabilir APK:** **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. Çıktı: `app/build/outputs/apk/<sürüm>/debug/app-<sürüm>-debug.apk`.
   - Üç sürüm farklı kimlikle **yan yana** kurulur; telefondaki WebView uygulamasının (`org.marketler.app`) yerine geçmez.
5. Testler: `./gradlew :core:data:test :app:testMockDebugUnitTest` (ekran testleri Robolectric ile; ilk çalıştırmada Android çalışma kitaplığı indirilir). Yerel API açıksa `MK_YEREL_API=http://127.0.0.1:8099/api/v1/` ile uçtan uca testler de çalışır.

## Modüller (karar: 4 modül)
| Modül | Tür | İçerik |
|---|---|---|
| `app` | Android uygulaması | Gezinme (5 sekme), ekranlar ve ViewModel'ler: `ui/home`, `ui/search`, `ui/product` |
| `core/model` | Saf Kotlin | API Rev3 veri modelleri (`docs/FAZ1-UC-NOKTALAR.md` ile birebir), favori anahtarı `itemId:storeId:hash` |
| `core/data` | Saf Kotlin | `MarketlerRepository` arayüzü, `MockRepository` (gömülü JSON), ağ katmanı (`remote/`: Retrofit + kotlinx.serialization), veri kuralları (`DataRules`), birim testleri |
| `core/ui` | Android kütüphanesi | Tema (lacivert `#172554`, mavi `#2da8ff`), logo, ortak bileşenler, market logoları |

`core/model` ve `core/data` saf Kotlin olarak bırakıldı. Bu sayede Android SDK olmadan da derlenip test edilebiliyorlar. Room gerektiğinde (çevrimdışı önbellek aşaması) `core/data` Android kütüphanesine çevrilecek.

## Mock veri
- `core/data/src/main/resources/mock/*.json`: 70 ürün ve 70 ürün detayı, ana sayfa, marketler, kategoriler.
- **Kaynak:** Rev3 API'nin yerel kopyasından, FAZ 1 testlerindeki **sentetik** veriyle üretildi (`MarketlerNative/tools/mock_veri_uret.php`). Yapı gerçek API ile aynı; **marka adları uydurmadır, fiyatlar gerçek değildir.** Uygulama bunu ana sayfada açıkça belirtir.
- Arama ve öneri, sitedeki `ara.php` ve `asistan.php` kurallarını izler. Gerçek API ile küçük farklar olabilir: büyük/küçük harf karşılaştırması Türkçe kurallarla yapılırken API MySQL karşılaştırmasını kullanır.

## Logo ve görseller
- **Uygulama logosu:** Marketler.org'un **mevcut** logosu kullanıldı: sitenin `inc.php` dosyasındaki sepet simgesi (`ico('cart')`) + "MARKETLER" + kırmızı ".ORG" (sitenin modern teması). Yeni logo çizilmedi.
- **Uygulama ikonu ve açılış ekranı:** Aynı sepet simgesi, lacivert zemin üzerinde beyaz.
- **Market logoları:** Sitenin `img/logo-bim.png`, `img/logo-a101.svg` (PNG'ye çevrildi) ve `img/logo-sok.png` dosyaları.
- Sitede yönetim panelinden yüklenmiş ayrı bir logo dosyası (`logo_url` ayarı) varsa elimizde değil; gönderilirse değiştirilecek.
