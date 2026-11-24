# FAZ 2.2 Öncesi: API Sözleşmesi ile Android Modellerinin Karşılaştırması

**Durum:** Kodlamaya başlanmadı. Bu rapor, istenen "önce karşılaştır, uyumsuzluk varsa raporla" adımıdır.

**Karşılaştırılan kaynaklar:**
- Sözleşme: `docs/FAZ1-API-SPESIFIKASYONU.md` (A.2 zarf, A.3 hatalar, B.1–B.4 nesneler) ve `docs/FAZ1-UC-NOKTALAR.md`.
- API kodu (Rev3, donmuş): `MarketlerNative/server/site/api/v1/` (`index.php`, `lib/response.php`, `lib/input.php`, `lib/map.php`, `endpoints/home|markets|categories|products|product|suggest.php`).
- Veritabanı yapısı: `MarketlerNative/server/tests/fixtures/schema.sql` (FAZ 0'da gerçek tablo yapısından çıkarıldı).
- Android: `core/model/.../Models.kt`, `core/data/.../Repository.kt`, `MockRepository.kt`.

**Kullanılacak uç noktalar (FAZ 2.2 kapsamı):**

| Ekran | Uç nokta |
|---|---|
| Ana sayfa | `GET /home` |
| Arama listesi | `GET /products?q&market&category&status&sort&page&limit` |
| Arama önerileri | `GET /suggest?q` |
| Ürün detay | `GET /products/{id}` |
| Arama süzgeçleri | `GET /markets`, `GET /categories` |

## Özet

| # | Konu | Tür | Önem |
|---|---|---|---|
| U1 | API canlıda yok; bu ortamdan marketler.org'a erişim yok | Ortam / yayın | **Engelleyici** (gerçek veri için) |
| U2 | Hata zarfı (`success:false`) Android'de modellenmemiş | Model eksiği | Yüksek; kodla çözülür |
| U3 | Sözleşmede zorunlu görünen alanlar veritabanında `NULL` olabilir | Sözleşme ile veri çelişkisi | **Yüksek; karar gerekli** |
| U4 | Görsel adresleri (ürün, kapak, logo) geliyor ama uygulamada görsel yükleyici yok | Kapsam | Karar gerekli |
| U5 | `INTERNET` izni yok (FAZ 2.1 kararı) | Yapılandırma | Kodla çözülür, onayınızla |
| U6 | Arama: mock Türkçe sadeleştirme yapıyor, gerçek API MySQL `LIKE` kullanıyor | Davranış farkı | Düşük; sözleşmeye uygun olan API |

Bunların dışında alan adları, türler, parametre adları ve değerleri **birebir uyumlu**:
- `status=aktif|yaklasan|dolmus|hepsi`, `sort=new|asc|desc`, `limit` 1–50.
- Sayfalama `meta.page/limit/total`.
- Para değerleri metin (`"4599.00"`).
- `fav_key` biçimi, `compare`/`compare_reason`, `history`, `note` (metin, dizi ya da null).

Mock veri zaten Rev3 API'nin kendi çıktısından üretildiği için 14 birim testi bu yapıyla geçiyor.

## U1. API canlıda yok (engelleyici)
- Rev3 API **canlıya yüklenmedi.** Kararınız: "canlıya yükleme onayı verilmedi".
- FAZ 1 kapanışı için D.3 sonuçları ve sunucu bilgileri hâlâ bekleniyor (`FAZ1-SON-DEGERLENDIRME.md`).
- Bu ortamdan `https://marketler.org` adresine bağlantı ağ kuralı tarafından engelleniyor. Ağ vekili HTTP 403 ile reddediyor.
- Rev3'ü canlıya almak yalnızca `api/` klasörünü eklemek değildir. Daha önce onayladığınız **site dosyası değişiklikleri** de gerekir:
  - `ortak-veri.php` (yeni);
  - `tema-modern-ana.php` (öne çıkanlar bölümü bu dosyaya taşındı, çıktı birebir aynı);
  - `ara.php` (eşitlikte `i.id` sıralama düzeltmesi).
- Eşleştirme algoritmasına (`eslestirme.php`, `cmp_groups`) ve yönetim paneline dokunulmaz.
- **Sonuç:** Uygulama gerçek API'ye bağlanacak şekilde yazılabilir, test edilebilir ve APK'sı üretilebilir. Ancak gerçek veriyle çalıştığı, API canlıya alınmadan **doğrulanamaz.** API'ye bağlanan APK bugün telefonda yalnızca "bağlantı hatası" ekranını gösterir.

## U2. Hata zarfı modellenmemiş
- API hata durumunda `{"success":false,"error":{"code","message"},"meta":{…}}` döndürüyor; `data` alanı yok.
- Android'deki `Envelope<T>` modeli `data` alanını zorunlu tutuyor, bu yüzden hata yanıtı ayrıştırma hatasına düşer.
- **Çözüm (kodla, karar gerekmez):**
  - `ApiError` modeli eklenir.
  - HTTP koduna ve `error.code` değerine göre `DataError` üretilir:
    - `NotFound` (404);
    - `RateLimited` (429, `Retry-After` başlığıyla);
    - `Unavailable` (503 veya ağ yok);
    - `Server` (500);
    - `BadRequest` (400; uygulama hatasıdır, günlüğe yazılır).
- **Ek:** API `ETag` gönderiyor ve `304` dönebiliyor. OkHttp önbelleği bunu kendisi yönetir.

## U3. Sözleşmede zorunlu olan alanlar veritabanında NULL olabilir (karar gerekli)
Sözleşme (B.1–B.3) aşağıdaki alanları `string` / `int` olarak tanımlıyor, yani boş gelmeyecekleri varsayılıyor. Ancak:
- Veritabanında bu kolonların hepsi `DEFAULT NULL`.
- API eşleme kodu (`lib/map.php`) NULL değeri olduğu gibi aktarıyor.

Android modeli bu alanları zorunlu tuttuğu için tek bir hatalı kayıt **bütün ekranın** "veri okunamadı" hatasına düşmesine yol açar.

| JSON alanı | Veritabanı kolonu | API NULL'da ne döndürür (koddan) | Android modeli |
|---|---|---|---|
| `Item.name` | `items.name` | `null` | zorunlu |
| `Item.price` | `items.price` | `null` (`mk_money`) | zorunlu, `BigDecimal(price)` |
| `Item.hash` → `fav_key` | `items.hash` | `"id:sid:"` (geçersiz anahtar) | metin olarak okunur; `FavKey.parse` boş döner. Sorun yok |
| `market.color` / `slug` / `name` | `stores.*` | `null` | zorunlu |
| `catalog.slug`, `title` | `catalogs.slug`, `title` | `null` | `slug` zorunlu; `CatalogSummary.title` zorunlu |
| `catalog.start_date` | `catalogs.cdate` | `null` | zorunlu |
| `category.name` | `categories.name` | `null` | zorunlu |

- Bu alanlarda gerçekten NULL kayıt olup olmadığı **bilinmiyor.** D.3 sorguları bunu ölçmüyor. Ölçmek için aşağıdaki **salt okunur** T5 sorgusu önerilir; yalnızca sayı döndürür.
- T5 sorgusu yerel MariaDB 10.11 üzerinde, gerçek tablo yapısıyla hatasız çalıştırıldı (boş veritabanı, tüm sayılar 0).
- **Öneri (A):** Uygulama hatalı kayda karşı dayanıklı olur.
  - Bir öğede ad veya fiyat yoksa o öğe listeye **alınmaz** (fiyat uydurulmaz) ve sayısı günlüğe yazılır. Ekranın geri kalanı çalışır.
  - Yalnızca görünüş alanı eksikse (`color`, `title`) nötr gösterim kullanılır: renk yerine gri, başlık yerine katalog tarihleri.
  - API'ye dokunulmaz.
- **Alternatif (B):** Katı davranış. Sözleşmeye aykırı yanıt gelirse ekran hata gösterir. Basittir ama tek bir hatalı kayıt ana sayfayı kapatabilir.

```sql
-- T5 (öneri): Android'in zorunlu saydığı alanlarda boş kayıt sayısı. YALNIZCA SELECT; ad veya fiyat döndürmez, yalnızca sayı.
SELECT
  (SELECT COUNT(*) FROM items i JOIN catalogs c ON c.id=i.catalog_id WHERE c.hidden=0 AND (i.name IS NULL OR i.name='')) AS urun_adi_bos,
  (SELECT COUNT(*) FROM items i JOIN catalogs c ON c.id=i.catalog_id WHERE c.hidden=0 AND i.price IS NULL) AS fiyat_bos,
  (SELECT COUNT(*) FROM items i JOIN catalogs c ON c.id=i.catalog_id WHERE c.hidden=0 AND (i.hash IS NULL OR i.hash='')) AS hash_bos,
  (SELECT COUNT(*) FROM catalogs WHERE hidden=0 AND (slug IS NULL OR slug='' OR cdate IS NULL)) AS katalog_slug_tarih_bos,
  (SELECT COUNT(*) FROM catalogs WHERE hidden=0 AND (title IS NULL OR title='')) AS katalog_baslik_bos,
  (SELECT COUNT(*) FROM stores WHERE slug IS NULL OR name IS NULL OR color IS NULL OR color='') AS market_eksik,
  (SELECT COUNT(*) FROM categories WHERE name IS NULL OR name='' OR slug IS NULL) AS kategori_eksik;
```

## U4. Görseller (karar gerekli)
- Gerçek API ürün görseli (`image.url`), katalog kapağı (`cover.url`) ve market logosu (`logo_url`) adreslerini veriyor. Mock'ta bunlar hep boştu.
- Bunları göstermek için bir görsel yükleyici (Coil) gerekir. İstediğiniz kütüphaneler Retrofit ve Kotlin Serialization; Coil listede yok.
- **Öneri:** FAZ 2.2'de görseller gösterilmez; bugünkü market renkli yer tutucu kalır. Görseller sonraki ayrı bir aşamada (Coil ve önbellek ile) eklenir.

## U5. İnternet izni
- FAZ 2.1'de `INTERNET` izni bilerek yoktu. FAZ 2.2'de eklenmesi gerekiyor.
- Önerilen güvenlik ayarları:
  - Yalnızca HTTPS (`usesCleartextTraffic=false`).
  - Sabit adres `https://marketler.org/api/v1/`.
  - Firebase veya izleme yok.

## U6. Arama davranışı farkı (bilgi)
- Mock arama Türkçe harf sadeleştirmesi yapıyordu (ş/s, İ/i).
- Gerçek `/products` araması sitedeki `ara.php` ile aynı: MySQL `LIKE`, sonuç tablonun karşılaştırma kuralına (collation) bağlı.
- Sözleşmeye uygun olan gerçek API'dir. Uygulama sonucu düzeltmeye çalışmaz.

## Önerilen aşamalar (her aşamada gerçek APK ve SHA-256)

| Aşama | İçerik | Test |
|---|---|---|
| **2.2.1** | Ağ katmanı: Retrofit, kotlinx-serialization dönüştürücü, OkHttp (zaman aşımı, önbellek). `MarketlerApi` (6 uç nokta), `RemoteRepository`, `ApiError` ve hata eşlemesi, U3 kararına göre ayrıştırma. **Ekranlar değişmez;** APK mock ile çalışmaya devam eder | Birim testleri: MockWebServer ile mock JSON'un HTTP üzerinden aynen okunması; 400/404/429/500/503, bozuk JSON, NULL alanlı kayıtlar. **Yerel Rev3 kopyasına (php ve MariaDB, sentetik veri) karşı uçtan uca test** |
| **2.2.2** | Ekranların `RemoteRepository`'ye bağlanması. Yükleme, hata (ağ yok, 429, 503, 404) ve boş veri durumları; yeniden dene. `INTERNET` izni. Mock veri ayrı derleme türünde (`mock`) korunur | Robolectric ekran testleri (sahte sunucuyla), gerçek APK |
| **2.2.3** | Canlı doğrulama: API canlıya alındıktan sonra telefonda gerçek veriyle kontrol listesi | Sizin telefonunuz. İzin verirseniz bu ortamdan canlı API'ye salt okunur istekler |

## Sizden gereken kararlar
1. **U1:** Rev3 API'yi (ve gereken üç site dosyası değişikliğini) canlıya alma planı. Kim yükleyecek, ne zaman? O zamana kadar 2.2.1 ve 2.2.2 yerel kopyaya karşı test edilerek ilerleyebilir.
2. **U3:** Dayanıklı ayrıştırma (A, önerilen) mı, katı (B) mı? İsterseniz T5 sorgusunu phpMyAdmin'de çalıştırıp sayıları gönderin.
3. **U4:** Görseller FAZ 2.2'de gösterilmesin (önerilen) mi, yoksa Coil eklensin mi?
4. **U5:** `INTERNET` izni ve yalnızca HTTPS `marketler.org` ayarı onayı.
