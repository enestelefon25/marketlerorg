# FAZ 2.2.1 Raporu: Ağ Katmanı (Retrofit + Kotlin Serialization) ve Hata Yönetimi

**Sürüm:** `0.2.2.1-mock` (versionCode 2) · **Kaynak:** `756f6e7` işlemesi.
**Kapsam:** Kararınız gereği yalnızca ağ katmanı, veri modelleri, NULL veri kuralları ve hata yönetimi.
- **Ekranlar API'ye bağlanmadı:** APK hâlâ uygulamaya gömülü örnek (mock) veriyle çalışır. Bağlantı FAZ 2.2.2'de.
- **INTERNET izni bu aşamada eklenmedi:** Ağ kodu henüz hiçbir ekrandan çağrılmıyor; izin, build variant'lar ve ağ güvenlik ayarı ekranlarla birlikte 2.2.2'de gelecek (bölüm 7).
- **Değişmedi:** API kodu, PHP eşleştirme algoritması, veritabanı şeması, yönetim paneli, WebView uygulaması, canlı site. Canlıya hiçbir şey yüklenmedi, canlı sisteme bağlanılmadı.

## 1. Teslim edilenler
| Dosya | SHA-256 | Boyut |
|---|---|---|
| `Marketler-FAZ2.2.1-debug.apk` | `1d7b0e44d9f0c41aeb9dd2c2427a9589b6c0d2abbfad8097ec01d9c65a781197` | 17.746.230 bayt |
| `MarketlerNative-FAZ2.2.1.zip` (kaynak) | Teslim mesajında verilir (zip bu raporu içerdiği için) | |

**APK bilgileri** (`aapt2` ve `apksigner` ile okundu):
- applicationId `org.marketler.app.dev`, versionCode `2`, versionName `0.2.2.1-mock-debug`, targetSdk 36.
- **INTERNET izni yok.**
- İmza sertifikası düzeltme 3 APK'sı ile aynı (SHA-256 `b42fcad3…4979`). Bu yüzden telefondaki düzeltme 3 sürümünün **üzerine kurulur;** silmeniz gerekmez.

**Kaynak paket:** `MarketlerNative-FAZ2.2.1.zip`.
- Kontrol listesi: `docs/FAZ2.2.1-DOSYA-OZETLERI.txt`.
- Android Studio'da açılacak klasör: `MarketlerNative-FAZ2.2.1/MarketlerNative/android`.

## 2. Yapılanlar

### 2.1 Ağ katmanı (`core/data/.../remote/`)
| Dosya | İçerik |
|---|---|
| `MarketlerApi.kt` | Retrofit arayüzü. Kapsamdaki 6 salt okunur uç nokta: `home`, `markets`, `categories`, `products` (liste modu), `products/{id}`, `suggest`. Parametre adları ve değerleri sözleşmeyle birebir. Boş `q` gönderilmez; `limit` 1–50 aralığına sıkıştırılır |
| `ApiClient.kt` | Retrofit, kotlinx-serialization dönüştürücü ve OkHttp (ayrıntılar aşağıda). Tek çağrı noktası `call()`: HTTP durumunu, ağ hatasını ve ayrıştırma hatasını `DataError` türlerine çevirir |
| `RemoteRepository.kt` | `MarketlerRepository` arayüzünün API uygulaması. Mock ile aynı arayüz ve aynı veri kuralları |

**`ApiClient` ayrıntıları:**
- `ignoreUnknownKeys`: sözleşmeye sonradan eklenen alan uygulamayı bozmaz.
- Zaman aşımları.
- İsteğe bağlı HTTP önbelleği: sunucunun `ETag` ve `Cache-Control` yanıtlarıyla çalışır.
- **Yönlendirme izlenmez.**
- `requireHttps=true` iken `http://` adresiyle istemci **oluşturulamaz.**
- Adres, anahtar veya gizli bilgi içermez. Adres 2.2.2'de derleme türünden verilecek.

**Hata eşlemesi (A.3 sözleşmesi):**
| Durum | Uygulamadaki karşılığı |
|---|---|
| 404 `NOT_FOUND` | `NotFound` |
| 429 | `RateLimited` (`Retry-After` saniyesiyle) |
| 503 | `Unavailable` (`Retry-After` ile) |
| 400 / 405 / 413 / 404 `ENDPOINT_NOT_FOUND` | `BadRequest` (uygulama hatası; kod ve mesaj saklanır) |
| 500 ve diğer durumlar | `Server` |
| Bağlantı yok / zaman aşımı | `Network` |
| HTML, bozuk JSON, `success:false` | `Malformed` |

Her hatanın kullanıcıya gösterilebilir Türkçe mesajı var.

### 2.2 Veri modelleri (`core/model/Models.kt`)
- U3 kararına göre veritabanında `DEFAULT NULL` olan kolonların karşılığı olan alanlar **nullable** yapıldı:
  - ürün adı, fiyat, fiyat metni;
  - market adı, kısa adı ve rengi;
  - katalog kısa adı, başlığı ve tarihleri;
  - kategori;
  - karşılaştırma grubu özet alanları;
  - öneri alanları.
- **Hoşgörülü liste okuma (`LenientListSerializer`):** Listelerin her öğesi ayrı çözülür. Yapısı bozuk öğe (ör. `id` metin) atlanır ve sayılır; diğer öğeler gösterilir.
- **Görüntüleme yardımcıları:**
  - `priceLabel`: fiyat yoksa "Fiyat belirtilmemiş".
  - `shownOldPriceText`, `shownDiscountPct`, `shownUnitPrice`: fiyat yoksa gösterilmez.
  - `MarketRef.label`: ad yoksa kısa ad, o da yoksa "Market".
  - `CatalogSummary.titleLabel`: başlık yoksa market adı.
- Uygulama hiçbir fiyat, indirim veya fark **hesaplamaz.**
- **Görsel alanları korundu:** `image.url`, `cover.url/full_url`, `logo_url` (U4).
- **Hata zarfı modeli eklendi:** `ApiEnvelope`, `ApiErrorBody`.

### 2.3 NULL veri kuralları (`core/data/DataRules.kt`)
Hem API'ye hem mock'a aynı şekilde uygulanır.

| Kararınız | Uygulama |
|---|---|
| Ürün adı boşsa listeleme | Listelerden çıkarılır, sayısı günlüğe yazılır. Adsız ürünün detayı "Ürün bulunamadı" verir |
| Fiyat NULL ise silme, "Fiyat belirtilmemiş" göster | Ürün listede kalır. Sunucunun gönderdiği `"0,00 TL"` **gösterilmez**; eski fiyat, indirim ve birim fiyat da gösterilmez |
| Fiyatsız ürün karşılaştırmaya girmesin | Satır gruptan çıkarılır. Sunucunun bu grup için NULL fiyatla hesapladığı özet (en ucuz işareti, fark, tasarruf cümlesi) **gösterilmez;** uygulama yerine değer hesaplamaz. 2'den az fiyatlı satır kalırsa grup gösterilmez ve "Karşılaştırma için yeterli fiyat bilgisi yok." yazar |
| NULL fiyat asla 0 TL değil | `priceValue` NULL'da `null` döner; sıralama ve gösterimde 0 kullanılmaz |
| NULL market/katalog bilgisi çökertmesin | Alanlar nullable; görünen metinlerde nötr karşılık (gri renk, kısa ad, market adı) |
| Hatalı kayıt diğerlerini engellemesin | Öğe öğe okuma ve öğe başına kural |

Fiyatı olmayan ürünün fiyat geçmişi de gösterilmez, çünkü sunucu bu geçmişi NULL fiyatla hesaplıyor.

### 2.4 Ekranlar
- Ekranlarda yeni özellik veya düzen değişikliği **yok.**
- Yalnızca nullable modellerin derlenmesi ve NULL güvenliği için zorunlu küçük değişiklikler yapıldı (`HomeScreen`, `SearchScreen`, `ProductScreen`, `Components`). Ör. fiyat yoksa "Fiyat belirtilmemiş" yazısı.
- Mock veride NULL alan olmadığı için görünüş değişmez.

### 2.5 Yerel API test verisi (`MarketlerNative/tools/api_test_verisi_uret.php`)
Rev3 API'nin **yerel kopyasını** (`php -S` ve yerel MariaDB) FAZ 1 güvenlik kurallarıyla kurar:
- yalnızca yerel veritabanı sunucusu;
- benzersiz test veritabanı;
- işaret tablosuyla silme güvencesi;
- sonunda temizlik.

Ardından:
- Sentetik veriye bilerek **kirli kayıtlar** ekler: NULL veya boş ad, NULL fiyat, NULL fiyat + eski fiyat, NULL hash, NULL kategori, NULL ad/renkli market, NULL başlıklı katalog.
- 28 gerçek HTTP yanıtını kaydeder: gövde, durum kodu, `Retry-After` dahil, 429 ve 503 de var. Kayıtlar `core/data/src/test/resources/api-yerel/` klasörüne yazılır.
- `--acik-kal` ile yerel API'yi uçtan uca test için açık tutar.

API kodu değiştirilmedi. Yalnızca geçici kopyanın ayar dosyasında HTTP izni ve hız sınırı değiştirildi; bu FAZ 1 testleriyle aynı yöntem.

## 3. Test sonuçları
| Test | Ortam | Sonuç |
|---|---|---|
| **Gerçek Android derlemesi:** `clean assembleDebug`, kaydedilmiş koddan temiz kopya | AGP 8.12.0, Gradle 8.14.3, SDK 36, JDK 21 | **Başarılı** |
| APK incelemesi (`aapt2`, `apksigner`) | Bu ortam | Paket, sürüm ve izinler bölüm 1'deki gibi; imza geçerli |
| `DataRulesTest`: karşılaştırma ayıklama, detay kuralları, NULL fiyat ≠ 0 | JVM | **5/5** |
| `MockRepositoryTest` (FAZ 2.1 testleri, nullable modele uyarlandı) | JVM | **14/14** |
| `RemoteRepositoryTest`: sahte sunucu (MockWebServer) **gerçek yerel API yanıtlarını** oynatır (ayrıntı aşağıda) | JVM | **14/14** |
| `YerelApiUctanUcaTest`: **çalışan yerel Rev3 API kopyasına** gerçek HTTP istekleri (ana sayfa, arama, süzgeçler, 3 sıralamada sayfalama tekrarsızlığı, detay, öneri, 404) | JVM ve yerel `php -S` + MariaDB | **4/4** (`MK_YEREL_API` ile). Ortam değişkeni yoksa testler atlanır; temiz kopya derlemesinde bu yüzden atlandı |
| Ekran gerilemesi: FAZ 2.1 gezinme testi, 5 senaryo, gerçek Navigation ve Compose | Robolectric 4.15.1 | **5/5** |
| Telefonda deneme | — | **Sizde.** Ekranlar değişmediği için beklenen: FAZ 2.1 düzeltme 3 ile aynı davranış |

**`RemoteRepositoryTest` kapsamı:**
- İstek parametreleri ve başlıkları;
- NULL kuralları;
- karşılaştırma ayıklama;
- 400/404/405/429/500/503 eşlemesi ve `Retry-After`;
- yönlendirmenin izlenmemesi;
- HTML veya bozuk gövde;
- bozuk öğenin atlanması;
- zaman aşımı ve bağlantı yok;
- yalnızca HTTPS kuralı.

## 4. Değişen / eklenen dosyalar
| Dosya | Durum |
|---|---|
| `android/core/model/.../Models.kt` | Değişti: nullable alanlar, hoşgörülü listeler, hata zarfı, görüntüleme yardımcıları |
| `android/core/data/.../Repository.kt` | Değişti: `DataError` türleri genişletildi |
| `android/core/data/.../MockRepository.kt` | Değişti: aynı veri kuralları ve nullable uyumu (davranış aynı) |
| `android/core/data/.../DataRules.kt` | Yeni |
| `android/core/data/.../remote/MarketlerApi.kt`, `ApiClient.kt`, `RemoteRepository.kt` | Yeni |
| `android/core/data/build.gradle.kts`, `gradle/libs.versions.toml` | Değişti: Retrofit 2.11.0, converter-kotlinx-serialization 2.11.0, OkHttp 4.12.0, MockWebServer 4.12.0 (yalnızca test) |
| `android/core/ui/.../Components.kt`, `app/.../HomeScreen.kt`, `SearchScreen.kt`, `ProductScreen.kt` | Değişti: yalnızca NULL güvenliği |
| `android/app/build.gradle.kts` | Değişti: versionCode 2, versionName `0.2.2.1-mock` |
| `android/core/data/src/test/...`: `DataRulesTest.kt`, `remote/RemoteRepositoryTest.kt`, `remote/YerelApiUctanUcaTest.kt`, `resources/api-yerel/*` (29 dosya) | Yeni / değişti (`MockRepositoryTest.kt`) |
| `MarketlerNative/tools/api_test_verisi_uret.php` | Yeni |
| `docs/FAZ2.2-UYUMLULUK-RAPORU.md`, `docs/FAZ2.2.1-RAPORU.md`, `docs/FAZ2.2.1-DOSYA-OZETLERI.txt` | Yeni |
| `MarketlerNative/server/**` (API), `MarketlerAndroid/**` (WebView) | **Değişmedi** |

## 5. Bulunan sözleşme uyumsuzlukları (API değiştirilmedi; karar sizde)
Yerel API kopyasının **gerçek yanıtlarında** görüldü (`api-yerel/` kayıtları):

| # | Bulgu | Sebebi | Uygulamada |
|---|---|---|---|
| **B1** | `/suggest`: NULL fiyatlı ürün `"price_text": "0,00 TL"` ile geliyor; gerçek 0 TL'den **ayırt edilemiyor** | Öneri havuzu `(float)$price` kullanıyor | Uygulama bunu düzeltemez. **Karar gerekli:** (a) önerilerde fiyat hiç gösterilmesin, (b) "0,00 TL" önerilerde gösterilmesin, (c) API'de düzeltme (canlı onayıyla). Önerim (a) veya (b) |
| B2 | `price:null` iken `price_text:"0,00 TL"`; eski fiyat varsa `discount_pct:100` ve `old_price_text` dolu | `tl(null)` = "0,00 TL"; PHP'de `old_price > null` doğru | Ayrıştırıldı: gösterilmez |
| B3 | Karşılaştırma grubu NULL fiyatlı satırı içeriyor ve onu `is_best:true` işaretliyor; `min_price` NULL | `cmp_groups()` ve `min()` NULL'ı en küçük sayıyor | Ayrıştırıldı (bölüm 2.3) |
| B4 | Adı veya rengi NULL market ve başlığı NULL katalog, ana sayfa listelerinde geliyor | Eşleme NULL'ı aktarıyor | Nötr gösterim |
| B5 | `hash` NULL ise `fav_key` = `"id:sid:"` (geçersiz) | `mk_item` boş hash'i ekliyor | Şimdilik etkisi yok; favoriler (FAZ 2.7) bu ürünü favoriye alamayacak |
| B6 | Liste `meta.total` adsız ürünleri de sayıyor | Sunucu sayımı | "N ürün bulundu" sayısı görünen ürünlerden en çok adsız ürün sayısı kadar fazla olabilir. Sayfalama sunucu sayfalarıyla yürüdüğü için tekrar veya kayıp yok (test edildi) |

Gerçek veritabanında bu durumların görülüp görülmediğini **T5 sorgusu** gösterir (`FAZ2.2-UYUMLULUK-RAPORU.md`).

## 6. Bilinen eksikler
1. **Ekranlar hâlâ mock veride** (kapsam gereği). Yükleniyor, hata, boş durum ekranları ve yeniden deneme API'ye göre 2.2.2'de ayarlanacak.
2. **Gerçek (canlı) API ile test yapılmadı.** API canlıda değil (kararınız). Bütün API testleri yerel kopyaya ve onun kayıtlarına karşı.
3. **Telefonda deneme yapılmadı.** Bu ortamda emülatör çalışmıyor (KVM yok). Gezinme testi Robolectric ile yapıldı.
4. **Release (R8) derlemesi denenmedi.** Retrofit ve kotlinx için R8 kuralları, ağ kodu ekranlara bağlandığında (2.2.2) eklenip release derlemesiyle denenecek.
5. **B1** (öneri fiyatı) kararı bekliyor.

## 7. 2.2.2 için önerilen yapılandırma (onayınızla uygulanır)
| Build variant | Veri | Adres | Ağ |
|---|---|---|---|
| `mock` | Gömülü örnek veri | Yok | INTERNET izni yok |
| `yerel` (**yalnızca debug**) | Yerel Rev3 kopyası (bilgisayarınızda) | `http://10.0.2.2:8099/api/v1/` (emülatör → bilgisayar) | HTTP **yalnızca** 10.0.2.2 ve 127.0.0.1 için, yalnızca bu variant'ın ağ ayarında |
| `canli` | Rev3 API | `https://marketler.org/api/v1/` | Yalnızca HTTPS, cleartext kapalı, başka adres yok |

- `yerel` adresi ve HTTP izni **yalnızca** `yerel` variant'ının kaynak klasöründe durur. `canli` ve release APK'larına girmez; bu, APK'dan okunarak doğrulanacak.
- Kararınızda "HTTP bağlantısı kapalı" maddesi var. Yerel kopya TLS'siz çalıştığı için `yerel` variant'ında bu tek istisnayı öneriyorum.
- **İstemezseniz** `yerel` variant'ı hiç olmaz. Yerel API testleri yalnızca JVM testleriyle (bu aşamadaki gibi) yapılır.
