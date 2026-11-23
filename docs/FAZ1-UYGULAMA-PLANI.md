# FAZ 1 Uygulama Planı: Kullanıcı Kararlarının Teknik Karşılığı

**Kapsam:** Yalnızca API kodu ve testleri. Bütün çalışma bu depo ve yerel test ortamında yapılır.
**Kapsam dışı:** Canlı sunucuya yükleme, veritabanı değişikliği, WebView projesi, Android ekranları.

| # | Karar | Uygulama | Doğrulama (test) |
|---|---|---|---|
| 1 | Mevcut fonksiyonlar kullanılır, yeni eşleştirme yazılmaz | Karşılaştırma için yalnızca `cmp_groups()` ve `mk_similar_groups()` çağrılır. API, `img/cmpcache.json` dosyasını sitenin kendisiyle ortak kullanır | Web ↔ API eşdeğerlik testi: `fiyat-karsilastirma.php` HTML'indeki gruplar ve fiyatlar ile API JSON'u karşılaştırılır |
| 2 | API salt okunur; `inc.php` değiştirilmez | Üç katmanlı koruma (aşağıda 2.1) | Sorgu günlüğü analizi, yazma girişimi testi, `schema_v≠5` testi (2.2) |
| 3 | PHP sürümü belirsiz kabul edilir | Kod PHP 7.4 söz dizimiyle sınırlı tutulur (`match`, `?->`, adlandırılmış argüman, `enum`, `readonly`, `str_contains` yok) | PHP 8.3 ile çalıştırma testi; mümkünse 7.4 ve 8.4 ile söz dizimi denetimi. Gerçek hosting sürümü cPanel'den teyit edilene kadar "doğrulanmadı" olarak raporlanır |
| 4 | Favori: `store_id`, `hash`, `product_name`, `item_id` | `GET /products?fav=` yalnızca market + hash ile **birebir** eşleşme arar; tahmin yok. Veritabanı değişmez | Birim testi + `as_hkey` ile `items.hash` karşılaştırma testi (4.1) |
| 5 | `mt_featured()` içeriği değişmeden taşınır | Fonksiyon, açıklama yorumu ve `MT_MIN_GAIN` sabiti karakteri karakterine `ortak-veri.php` dosyasına alınır. `tema-modern-ana.php` dosyası `require_once` ile yükler | Dört senaryoda eski ve yeni sitenin bütün sayfalarının HTML çıktısı **bayt bayt** karşılaştırılır. Tek bir fark bile çıkarsa değişiklik reddedilir |
| 6 | Native proje `MarketlerNative/` klasöründe; WebView projesine dokunulmaz | Sunucu dosyaları `MarketlerNative/server/` altında | `git diff` ile `MarketlerAndroid/` klasöründe değişiklik olmadığı gösterilir |
| 7 | Firebase yok | API'de bildirim uç noktası yok | – |
| 8 | Veritabanı şeması değişmez; minimum veri | Testler sentetik veriyle yapılır; şema olarak kullanıcının yapı dökümü kullanılır | Test ortamı her çalıştırmada sıfırdan kurulur. Canlı veritabanına bağlantı yok |
| 9 | Her dosyanın amacı, testler, rapor | `MarketlerNative/server/README.md` dosya dosya açıklama; `docs/FAZ1-TEST-RAPORU.md` | Test betiği tek komutla çalışır |

## 2.1 `inc.php` dosyasına dokunmadan salt okunurluk

**Sorun:** `inc.php` yüklenirken `schema_v` değeri `'5'` değilse `CREATE TABLE`, `ALTER`, `INSERT`, `UPDATE` ve `DELETE` içeren şema güncellemesini çalıştırıyor.

**Çözüm** (`inc.php` aynen kalır):
1. **Ön kontrol.** API, `inc.php`'yi yüklemeden önce kendi ayrı veritabanı bağlantısını açar ve bu bağlantıyı hemen `SET SESSION TRANSACTION READ ONLY` ile kilitler.
   - Bağlantı bilgilerini almak için `config.php` **çalıştırılmaz**; dosya metin olarak okunur. Çalıştırılsaydı `inc.php` aynı dosyayı tekrar yüklerken "sabit zaten tanımlı" uyarısı oluşurdu.
   - Kilitli bağlantıyla `SELECT v FROM settings WHERE k='schema_v'` okunur.
   - Değer `'5'` değilse API **`inc.php`'yi hiç yüklemez** ve `503` döner. Böylece şema güncelleme bloğu API üzerinden hiçbir zaman çalışamaz.
2. **Oturum kilidi.** `inc.php` yüklendikten hemen sonra, kendi bağlantısı (`db()`) da `SET SESSION TRANSACTION READ ONLY` ile kilitlenir. Bundan sonra API kodunda bir hata olsa bile veritabanı her türlü yazmayı **sunucu tarafında** reddeder (MariaDB hata 1792). Bu davranış bu ortamda denendi.
3. **Kod kuralı.** API dosyalarında `INSERT`, `UPDATE`, `DELETE`, `REPLACE`, `ALTER`, `CREATE`, `DROP` ve `TRUNCATE` kelimeleri geçmez. Bunu otomatik bir test denetler.

**Bilinmesi gereken:** API veritabanına yazmaz. Ancak **sitenin kendi kullandığı önbellek dosyalarını** site ile aynı biçimde okur ve yazar:
- `img/cmpcache.json` (`cmp_groups()`)
- Geçici klasördeki öne çıkanlar ve öneri havuzu dosyaları

Bunlara ek olarak kendi hız sınırı sayaç dosyalarını geçici klasöre yazar. Bunlar veritabanı yazması değildir.

## 2.2 Salt okunurluk testleri
- **T-RO-1 (sorgu günlüğü):** Test ortamında MariaDB `general_log` açılır. Bütün API testleri çalıştırılır. API bağlantılarından gelen her ifade incelenir: yalnızca `SELECT` ve `SET SESSION TRANSACTION READ ONLY` olmalıdır.
- **T-RO-2 (yazma girişimi):** API önyüklemesi komut satırından çalıştırılır ve `q("INSERT …")` denenir. Veritabanının hata 1792 ile reddetmesi beklenir.
- **T-RO-3 (`schema_v≠5`):** Test veritabanında `schema_v='4'` yapılır, bir API isteği gönderilir. Yanıt `503` olmalı ve sorgu günlüğünde hiçbir yazma ifadesi görünmemelidir. Ayrıca `categories` ve `settings` tablolarının satır sayısı değişmemelidir.

## 4.1 `as_hkey` ile `items.hash`
- `items.hash` = `md5(mb_strtolower(ad))`
- `as_hkey` = `market_slug | boşlukları sadeleştirilmiş, Türkçe karakterleri sadeleştirilmiş, ondalık virgülü noktaya çevrilmiş ad`

Bu ikisinin aynı olduğu **varsayılmaz**. Bir birim testi, ad varyantlarından oluşan bir listede iki anahtarın hangi durumlarda ayrıştığını gösterir. Gerçek verideki tekrar oranı yalnızca `D3-sayim-sorgulari.sql` sonuçlarıyla ölçülebilir; bu sonuçlar gelene kadar "doğrulanmadı" olarak raporlanır.

## Uç nokta sayısı
`FAZ1-UC-NOKTALAR.md` belgesinde gerekçesiyle açıklandığı gibi 14 uç nokta uygulanır: `/meta` ve `/compare/{id}` başka uç noktalarda zaten bulunan veriyi tekrar ediyordu. İsterseniz bu ikisi kolayca geri eklenebilir.
