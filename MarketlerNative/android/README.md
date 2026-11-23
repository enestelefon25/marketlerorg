# Marketler.org: Native Android Uygulaması (FAZ 2.1, mock)

**Bu sürüm:** `0.2.1-mock`. Ana sayfa, arama ve ürün detay ekranları, **yalnızca uygulamaya gömülü örnek (mock) veriyle**.
**Ağ bağlantısı yok:** Manifestte `INTERNET` izni bilerek yok; uygulama API'ye veya herhangi bir sunucuya bağlanamaz.

## Android Studio ile açma ve APK üretme
1. Android Studio → **Open** → bu `android` klasörünü seçin (içinde `settings.gradle.kts` olan klasör). "Trust Project" deyin.
2. Gradle senkronizasyonunu bekleyin. Araç sürümleri, bilgisayarınızda WebView projesiyle derlenen sürümlerle aynıdır: AGP 8.12.0, Kotlin 2.2.0, Gradle 8.14.3, compileSdk 36. "AGP Upgrade" önerisini şimdilik kapatın.
3. **Kurulabilir APK:** Menü **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. Çıktı: `app/build/outputs/apk/debug/app-debug.apk`.
   - Hata ayıklama sürümünün uygulama kimliği `org.marketler.app.dev`. Telefondaki WebView uygulamasının (`org.marketler.app`) **yerine geçmez, yanına kurulur.**
4. Telefonda doğrudan çalıştırmak için üstteki **▶ Run** düğmesi.
5. Birim testleri: `core/data` modülüne sağ tıklayıp **Run 'Tests in data'**, ya da komut satırında `./gradlew :core:data:test`.

**İmzalı release (isteğe bağlı):** Proje kökünde `keystore.properties` (git dışı; `storeFile`, `storePassword`, `keyAlias`, `keyPassword`) varsa release sürümü imzalanır. Prototip aşamasında debug APK yeterlidir.

## Modüller (karar: 4 modül)
| Modül | Tür | İçerik |
|---|---|---|
| `app` | Android uygulaması | Gezinme (5 sekme), ekranlar ve ViewModel'ler: `ui/home`, `ui/search`, `ui/product` |
| `core/model` | Saf Kotlin | API Rev3 veri modelleri (`docs/FAZ1-UC-NOKTALAR.md` ile birebir), favori anahtarı `itemId:storeId:hash` |
| `core/data` | Saf Kotlin | `MarketlerRepository` arayüzü, `MockRepository` (gömülü JSON), birim testleri |
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
