# FAZ 1: API Geliştirme Raporu (değişiklik listesi, test sonuçları, doğrulanamayanlar)

**Ortam:** Bu bulut ortamı, PHP 8.3.6, MariaDB 10.11.14. Veri **sentetiktir**. Şema, kullanıcının gönderdiği yalnızca yapı dökümüdür.
**Canlı sistem:** Hiçbir dosya yüklenmedi, canlı veritabanına bağlanılmadı.
**Sonuç:** 255 kontrolün 255'i geçti (tam tablo en altta).

## 1. Değişiklik listesi

| Dosya | Durum | Not |
|---|---|---|
| `MarketlerNative/server/site/ortak-veri.php` | Yeni | `mt_featured()` + `MT_MIN_GAIN`, içerik değişmeden |
| `MarketlerNative/server/site/tema-modern-ana.php` | Değişen kopya | Yalnızca blok yerine 1 `require_once` satırı |
| `MarketlerNative/server/site/api/**` | Yeni (23 dosya) | 14 uç nokta, 6 kütüphane, `index.php`, `config.php`, `.htaccess` |
| `MarketlerNative/server/tests/**` | Yeni | Test betiği, sentetik veri, şema dökümü |
| `MarketlerNative/server/README.md` | Yeni | Dosya dosya açıklama, test ve geri alma |
| `docs/FAZ1-UYGULAMA-PLANI.md`, `docs/FAZ1-TEST-RAPORU.md` | Yeni | |
| `MarketlerAndroid/` (WebView) | **Dokunulmadı** | Testle doğrulandı (G grubu) |
| `inc.php`, `eslestirme.php`, `asistan*.php`, `sayfa.php`, `ara.php`, `cron.php`, `admin*` | **Dokunulmadı** | |
| Veritabanı şeması | **Değişmedi** | |

## 2. Koşullarınıza göre sonuçlar

| Koşul | Sonuç | Kanıt (test grubu) |
|---|---|---|
| API salt okunur; `inc.php` değiştirilmeden | ✅ | **F:** Sorgu günlüğünde 38 API bağlantısı ve 119 sorgu vardı: hepsi `SELECT`; her bağlantı `READ ONLY` ile kilitlendi; 0 yazma. Önyükleme sonrası `INSERT` denemesini veritabanı reddetti (1792). `schema_v='4'` iken API 503 döndü ve **hiçbir yazma yapılmadı**. Aynı durumda sitenin kendisi şemayı güncelledi; bu, korumanın neden gerekli olduğunu gösteriyor. |
| `mt_featured()` içerik değişmeden taşındı; 4 senaryoda bayt bayt aynı | ✅ | **B:** Senaryolar: 0 indirimli, 2 indirimli, 5 indirimli, boş site. Ana sayfa (önbelleksiz ve önbellekli) ve diğer 14–15 sayfa **bayt bayt aynı**. **G:** taşınan blok orijinal dosyada karakteri karakterine mevcut; yeni dosya = orijinal − blok + 1 satır. |
| Mevcut eşleştirme korunur, yeni algoritma yok | ✅ | **D:** `/compare` ile `fiyat-karsilastirma.php` aynı gruplar, sıra, fiyatlar, "en düşük" işareti ve tasarruf cümlesi. **C:** engellenen eşleşme görünmüyor, elle kurulan grup görünüyor. |
| Web ile API aynı veri ve kurallar | ✅ | **D:** 2 senaryoda 14 uç noktanın hepsi sitedeki karşılığıyla karşılaştırıldı: 13 farklı ürün arama filtresi, 8 akıllı arama, 5 öneri, 10 sohbet örneği, kataloglar, katalog detayı, market sayfası, sayfa metinleri. |
| Kopyalanan mantık (karar A) bozulmadı | ✅ | **G:** `ara.php`, `asistan.php`, `sayfa.php` ve `tema-modern-ana.php` dosyalarından kopyalanan 55 satır hedef dosyalarda birebir mevcut. |
| Olmayan görsel ve eski fiyat üretilmez | ✅ | **C:** onaylı ama dosyası olmayan görsel → `null`; kaldırılmış görsel → `null`; `old_price < price` → indirim yok. Uzak (başka siteden) broşür adresi API yanıtında hiç yok. |
| Favori: market + hash, tahmin yok | ✅ | **C:** aktif katalogdaki kalem seçiliyor; bulunamayan anahtar `missing` listesine düşüyor; gizli katalogdaki ürün bulunmuyor. |
| Güvenlik | ✅ | **E:** 16 geçersiz parametre → 400; 12 geçersiz yol → 404 (lib/ ve endpoints/ dosyaları dahil); 5 yanlış yöntem → 405; SQL enjeksiyonu metinleri düz metin olarak işlendi; gövde > 2 KB → 413; HTTPS yoksa 400; hız sınırı 429. Bütün API yanıtlarında gizli ayar, yönetici bilgisi, gizli katalog, hata ayrıntısı veya sunucu yolu **yok**. |

## 3. Bulgular (bilginize; hiçbirine dokunulmadı)

1. **`items.hash` Türkçe büyük "I" harfinde kararsız.** `mb_strtolower("ISITEK")` sonucu "isitek" oluyor, "ısıtek" değil. Kaynak bir hafta adı büyük harfle yazarsa hash değişir ve favori o haftanın ürününü bulamaz. Ayrıca boşluk, "1,5"/"1.5" ve Türkçe karakter farkları da hash'i değiştiriyor.
   - `as_hkey` daha toleranslı ama **aynı şey değil**: H testindeki 10 örneğin 6'sında sonuçları farklı.
   - Kararınız gereği favori yalnızca market + hash ile eşleşiyor. Gerçek verideki tekrar oranı **ölçülemedi** (bkz. 4).
2. **`ara.php` sıralamasında eşitlik belirsizliği (sitede zaten var).** `ORDER BY c.cdate DESC, i.name ASC` ölçütünde aynı tarih ve aynı ada sahip ürünlerin (ör. üç markette aynı ürün) sırası istekten isteğe değişebiliyor. Değişiklik yapılmamış sitede de gözlendi.
   - Sayfalar arası geçişte bu ürünlerden biri iki sayfada görünebilir ya da hiç görünmeyebilir.
   - API, web ile aynı davranmak için aynı sorguyu kullanıyor.
   - Öneri: her iki tarafa `, i.id` eklemek. Bu `ara.php` değişikliği gerektirdiği için **onayınıza bırakıldı**.
3. **Hız sınırı ve Cloudflare.** `.htaccess` kuralları sitenin Cloudflare arkasında olabileceğini gösteriyor. Öyleyse PHP, ziyaretçinin değil Cloudflare'in IP adresini görür ve hız sınırı bütün kullanıcılara ortak uygulanır.
   - Yönetici girişindeki sınır için de aynı durum geçerli.
   - Canlıya almadan önce sunucuda `mod_remoteip` (gerçek IP) ayarı kontrol edilmeli.
4. **API, sitenin önbellek dosyalarını paylaşır.** `img/cmpcache.json`, öne çıkanlar ve öneri havuzu dosyaları site ile aynı biçimde okunur ve yazılır; ayrıca hız sınırı sayaçları geçici klasöre yazılır. **Veritabanına yazma yoktur.**

## 4. Doğrulanamayanlar

| Konu | Neden | Gereken |
|---|---|---|
| Gerçek hosting PHP sürümü | Siteye erişim yok | cPanel → MultiPHP Manager ekranındaki sürüm |
| PHP 7.4 ve 8.4 ile çalışma | Bu ortamda yalnızca 8.3 var. WebAssembly PHP denemesi ortamın izin denetimince engellendi | Kod 8.0+ sözdizimi kullanmıyor (otomatik tarama). Gerçek sürüm öğrenilince o sürümle test |
| Gerçek veritabanı sürümü | – | `D3-sayim-sorgulari.sql` içindeki T1 sorgusu |
| `products` / `prices` kayıt sayısı | – | T1 sorgusu |
| Hash'in gerçek veride haftalar arası kararlılığı | Gerçek veri yok | T2–T4 sorguları (yalnızca sayı döner) |
| Gerçek veriyle performans (ör. `as_hist` tüm ürünleri okuyor) | Gerçek veri hacmi bilinmiyor | Anonim örnek veya canlı öncesi bir hazırlık (staging) ortamı |
| Apache `.htaccess` yönlendirmesi | Testler PHP'nin yerleşik sunucusunda çalıştı (`tests/router.php`) | Canlıya almadan önce bir alt klasörde deneme |

## 5. Test sonuçları (tam liste)

Toplam 255 kontrol · 255 geçti · 0 başarısız

| Grup | Kontrol | Sonuç |
|---|---|---|
| A. Kurulum | test veritabanı oluşturuldu | ✓ |
| A. Kurulum | kullanıcının şema dökümü yüklendi (yalnızca yapı) | ✓ |
| A. Kurulum | test sunucuları başladı (eski site :8081, yeni site + API :8082, hız sınırı/HTTPS testi :8083) | ✓ |
| A. Kurulum | PHP sürümü (yerel test) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) | senaryo '0 indirimli': 17 sayfa eski ve yeni sitede aynı (ana sayfa iki kez: önbelleksiz + önbellekli) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   bilgi: /ara.php yalnızca aynı adlı ürün kartlarının kendi aralarındaki sırası farklı (sitedeki ORDER BY eşitliği; içerik aynı) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   '0 indirimli': 15 sayfa BAYT BAYT aynı (ana sayfa dahil: evet) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) | senaryo '1-3 indirimli (2)': 17 sayfa eski ve yeni sitede aynı (ana sayfa iki kez: önbelleksiz + önbellekli) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   bilgi: /ara.php yalnızca aynı adlı ürün kartlarının kendi aralarındaki sırası farklı (sitedeki ORDER BY eşitliği; içerik aynı) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   '1-3 indirimli (2)': 15 sayfa BAYT BAYT aynı (ana sayfa dahil: evet) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) | senaryo '4+ indirimli (5)': 17 sayfa eski ve yeni sitede aynı (ana sayfa iki kez: önbelleksiz + önbellekli) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   bilgi: /ara.php yalnızca aynı adlı ürün kartlarının kendi aralarındaki sırası farklı (sitedeki ORDER BY eşitliği; içerik aynı) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   '4+ indirimli (5)': 15 sayfa BAYT BAYT aynı (ana sayfa dahil: evet) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) | senaryo 'ürünsüz / katalogsuz': 15 sayfa eski ve yeni sitede aynı (ana sayfa iki kez: önbelleksiz + önbellekli) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   'ürünsüz / katalogsuz': 14 sayfa BAYT BAYT aynı (ana sayfa dahil: evet) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   'ürünsüz / katalogsuz' ana sayfa boş durum kartı | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) | eski ve yeni sitede PHP hata/uyarısı yok (sunucu günlüğü) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) | SONUÇ: ortak-veri.php değişikliği kabul koşulu (tüm senaryolar aynı) | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /home 200 + zarf | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /home markets=3, live=3, soon=3 | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /home featured başlık (2 indirimli → "Öne çıkan fırsatlar") | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /home top_compare var, data_updated_at | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /home BİM logo yerel dosyadan mutlak URL | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /home A101 logosu ayarı yok → null | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | ETag ve Cache-Control başlıkları | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | If-None-Match → 304 | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /markets 3 market | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /markets/bim live/soon/past = 1/1/1 (gizli/onay bekleyen yok) | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /markets/yok → 404 | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /catalogs all: live 3 soon 3 past 3 | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /catalogs sayfalama (past, limit 2, sayfa 2 → 1 kayıt) | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /catalogs/{bim aktif}: 2 yerel sayfa, 1 uzak sayfa atlandı | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) |   sayfa 1 küçük kopya t/, sayfa 2 küçük kopya yok → asıl görsel | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) |   kapak c/ küçük kopyası | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) |   ürün grupları KORDER sırasında (elektronik önce) | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | onay bekleyen katalog → 404 | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | gizli katalog → 404 | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /categories 14, KORDER sırası | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /products varsayılan (aktif), toplam > 0 | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /products gizli/onay bekleyen ürün yok | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /products?q=süt&sort=asc artan fiyat | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | old_price < price → eski fiyat/indirim gösterilmez | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | fiyat metin olarak (ör. "27.75") ve sitedeki biçimle | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | birim fiyat (gıda, 1 L) → TL/L | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | favori: market+hash → aktif katalogdaki kalem (geçmiş/yaklaşan değil) | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | favori: bulunamayan anahtar missing listesinde | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | favori: gizli katalogdaki ürün bulunmaz | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /products/{id} fiyat geçmişi: önceki 59,90 TL, %-8 | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) |   geçmiş cümlesi sitedeki biçimde | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) |   eşleşme yoksa compare=null + açıklama | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | Airfryer: 3 marketlik kesin eşleşme grubu, en ucuz A101 | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) |   fark 600,00 TL, %12, sitedeki tasarruf cümlesi | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) |   onaylı + dosyası olan görsel döner | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | kaldırılmış (status=2) görsel → null | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | onaylı ama dosyası olmayan görsel → null | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /products/{gizli katalogdaki ürün} → 404 | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /products/{olmayan} → 404 | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /search en ucuz süt → full, sort asc, chips | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /search yazım hatası → sonuç veya öneri | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /suggest ≤5 öneri | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /suggest tek harf → boş liste (hata değil) | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /compare: Airfryer ve Yaylaköy grupları var | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /compare: elle kurulan grup (product_links) is_manual | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /compare: engellenen eşleşme (link_blocks) yok | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /compare?q= süzgeç | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /compare/similar gruplar + uyarı metni | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /assistant merhaba → chat_only | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /assistant katalog sorusu → links | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /assistant süt → ≤5 ürün | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /assistant devam sorusu önceki aramayla birleşir | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /pages/gizlilik ayardaki metin, 2 paragraf | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /pages/iletisim varsayılan metin + e-posta | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | /pages/yok → 404 | ✓ |
| C2. Boş site (katalog yok) | /home boş: featured boş, başlık "Uygun Fiyatlı Ürünler", top_compare null | ✓ |
| C2. Boş site (katalog yok) | /products boş: total 0 | ✓ |
| C2. Boş site (katalog yok) | /compare boş | ✓ |
| C2. Boş site (katalog yok) | /search boş: match none | ✓ |
| C2. Boş site (katalog yok) | /assistant boş: ürün yok, hata yok | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | home: öne çıkanlar (ad, fiyat, sıra) aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | home: öne çıkanlar başlığı aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | home: güncel kataloglar aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | home: öne çıkan karşılaştırma aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php []: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [q=s%C3%BCt]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [q=s%C3%BCt&o=asc]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [q=s%C3%BCt&o=desc]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [m=bim]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [k=gida&st=hepsi]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [st=yaklasan]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [st=dolmus]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [min=100&max=500]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [q=%25]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [q=_]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [q=Kampanya_Sepeti]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [st=hepsi&off=24]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | categories ↔ ara.php kategori seçimi (sıra) | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | search ↔ asistan.php [en ucuz süt]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | search ↔ asistan.php [bim süt 1 lt]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | search ↔ asistan.php [kahwe]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | search ↔ asistan.php [airfryer]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | search ↔ asistan.php [500 tl altı kulaklık]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | search ↔ asistan.php [deterjan]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | search ↔ asistan.php [indirimli]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | search ↔ asistan.php [zzzqqq]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | suggest ↔ asistan.php?oneri=1 [sü] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | suggest ↔ asistan.php?oneri=1 [süt 1] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | suggest ↔ asistan.php?oneri=1 [airf] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | suggest ↔ asistan.php?oneri=1 [kav] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | suggest ↔ asistan.php?oneri=1 [xx] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | assistant ↔ asistan.php?ajax=1 [süt] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | assistant ↔ asistan.php?ajax=1 [en ucuz kahve] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | assistant ↔ asistan.php?ajax=1 [peki a101'de? / önceki: süt] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | assistant ↔ asistan.php?ajax=1 [daha ucuzu / önceki: kahve] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | assistant ↔ asistan.php?ajax=1 [merhaba] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | assistant ↔ asistan.php?ajax=1 [bu hafta hangi kataloglar var] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | assistant ↔ asistan.php?ajax=1 [yaklaşan kataloglar] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | assistant ↔ asistan.php?ajax=1 [kahwe] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | assistant ↔ asistan.php?ajax=1 [500 tl altı tv] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | assistant ↔ asistan.php?ajax=1 [zzzqqq] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | compare ↔ fiyat-karsilastirma.php: gruplar, sıra, fiyatlar, en düşük, tasarruf cümlesi | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | compare/similar ↔ #benzer bölümü | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | catalogs ↔ kataloglar.php (üç bölüm, sıra) | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | markets/sok ↔ /market/sok | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | catalogs/{slug} ↔ katalog.php [bim-21-kasim-2026-aktuel-urunler-katalogu]: ürünler, sayfa sayısı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | catalogs/{slug} ↔ katalog.php [a101-14-kasim-2026-aktuel-urunler-katalogu]: ürünler, sayfa sayısı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | catalogs/{slug} ↔ katalog.php [sok-26-kasim-2026-aktuel-urunler-katalogu]: ürünler, sayfa sayısı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | pages/gizlilik ↔ sayfa.php | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | pages/hakkimizda ↔ sayfa.php | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | pages/iletisim ↔ sayfa.php | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | pages/kosullar ↔ sayfa.php | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | home: öne çıkanlar (ad, fiyat, sıra) aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | home: öne çıkanlar başlığı aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | home: güncel kataloglar aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | home: öne çıkan karşılaştırma aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php []: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [q=s%C3%BCt]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [q=s%C3%BCt&o=asc]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [q=s%C3%BCt&o=desc]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [m=bim]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [k=gida&st=hepsi]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [st=yaklasan]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [st=dolmus]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [min=100&max=500]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [q=%25]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [q=_]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [q=Kampanya_Sepeti]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [st=hepsi&off=24]: toplam ve kart sırası aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | categories ↔ ara.php kategori seçimi (sıra) | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | search ↔ asistan.php [en ucuz süt]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | search ↔ asistan.php [bim süt 1 lt]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | search ↔ asistan.php [kahwe]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | search ↔ asistan.php [airfryer]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | search ↔ asistan.php [500 tl altı kulaklık]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | search ↔ asistan.php [deterjan]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | search ↔ asistan.php [indirimli]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | search ↔ asistan.php [zzzqqq]: toplam, kartlar, 'Anladığım' | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | suggest ↔ asistan.php?oneri=1 [sü] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | suggest ↔ asistan.php?oneri=1 [süt 1] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | suggest ↔ asistan.php?oneri=1 [airf] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | suggest ↔ asistan.php?oneri=1 [kav] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | suggest ↔ asistan.php?oneri=1 [xx] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | assistant ↔ asistan.php?ajax=1 [süt] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | assistant ↔ asistan.php?ajax=1 [en ucuz kahve] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | assistant ↔ asistan.php?ajax=1 [peki a101'de? / önceki: süt] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | assistant ↔ asistan.php?ajax=1 [daha ucuzu / önceki: kahve] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | assistant ↔ asistan.php?ajax=1 [merhaba] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | assistant ↔ asistan.php?ajax=1 [bu hafta hangi kataloglar var] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | assistant ↔ asistan.php?ajax=1 [yaklaşan kataloglar] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | assistant ↔ asistan.php?ajax=1 [kahwe] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | assistant ↔ asistan.php?ajax=1 [500 tl altı tv] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | assistant ↔ asistan.php?ajax=1 [zzzqqq] | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | compare ↔ fiyat-karsilastirma.php: gruplar, sıra, fiyatlar, en düşük, tasarruf cümlesi | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | compare/similar ↔ #benzer bölümü | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | catalogs ↔ kataloglar.php (üç bölüm, sıra) | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | markets/sok ↔ /market/sok | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | catalogs/{slug} ↔ katalog.php [bim-21-kasim-2026-aktuel-urunler-katalogu]: ürünler, sayfa sayısı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | catalogs/{slug} ↔ katalog.php [a101-14-kasim-2026-aktuel-urunler-katalogu]: ürünler, sayfa sayısı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | catalogs/{slug} ↔ katalog.php [sok-26-kasim-2026-aktuel-urunler-katalogu]: ürünler, sayfa sayısı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | pages/gizlilik ↔ sayfa.php | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | pages/hakkimizda ↔ sayfa.php | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | pages/iletisim ↔ sayfa.php | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | pages/kosullar ↔ sayfa.php | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?market=bim'%20OR%20'1'='1 | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?category=gida';-- | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?page=0 | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?page=-1 | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?limit=51 | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?limit=abc | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?status=x | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?sort=price;drop | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?q=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?q=%ff%fe | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?fav=1:xyz | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?fav=1:c4ca4238a0b923820dcc509a6f75849b,1:c81e728d9d4c2f636f067f89cc14862c,1:eccbc87e4b5ce2fe28308fd9f2a7baf3,1:a87ff679a2f3e71d9181a67b7542122c,1:e4da3b7fbbce2345d7772b0674a318d5,1:1679091c5a880faf6fb5e6087eb1b2dc,1:8f14e45fceea167a5a36dedd4bea2543,1:c9f0f895fb98ab9159f51fd0297e236d,1:45c48cce2e2d7fbdea1afc51c7c6ad26,1:d3d9446802a44259755d38e6d163e820,1:6512bd43d9caa6e02c990b0a82652dca,1:c20ad4d76fe97759aa27a0c99bff6710,1:c51ce410c124a10e0db5e4b97fc2af39,1:aab3238922bcc25a6f606eb525ffdc56,1:9bf31c7ff062936a96d3c8bd1f8f2ff3,1:c74d97b01eae257e44aa9d5bade97baf,1:70efdf2ec9b086079795c442636b55fb,1:6f4922f45568161a8cdf4ad2299f6d23,1:1f0e3dad99908345f7439f8ffabdffc4,1:98f13708210194c475687be6106a3b84,1:3c59dc048e8850243be8079a5c74d079,1:b6d767d2f8ed5d21a44b0e5886680cb9,1:37693cfc748049e45d87b8c7d8b9aacd,1:1ff1de774005f8da13f42943881c655f,1:8e296a067a37563370ded05f5a3bf3ec,1:4e732ced3463d06de0ca9a15b6153677,1:02e74f10e0327ad868d138f2b4fdd6f0,1:33e75ff09dd601bbe69f351039152189,1:6ea9ab1baa0efb9e19094440c317e21b,1:34173cb38f07f89ddbebc2ac9128303f,1:c16a5320fa475530d9583c34fd356ef5,1:6364d3f0f495b6ab9dcf8d3b5c6e0b01,1:182be0c5cdcd5072bb1864cdee4d3d6e,1:e369853df766fa44e1ed0ff613f563bd,1:1c383cd30b7c298ab50293adfecb7b18,1:19ca14e7ea6328a42e0eb13d585e4c22,1:a5bfc9e07964f8dddeb95fc584cd965d,1:a5771bce93e200c36f7cd9dfd0e5deaa,1:d67d8ab4f4c10bf22aa353e27879133c,1:d645920e395fedad7bbbed0eca3fe2e0,1:3416a75f4cea9109507cacd8e2f2aefc,1:a1d0c6e83f027327d8461063f4ac58a6,1:17e62166fc8586dfa4d1bc0e1742c08b,1:f7177163c833dff4b38fc8d2872f1ec6,1:6c8349cc7260ae62e3b1396831a8398f,1:d9d4f495e875a2e075a1a4a6e1b9770f,1:67c6a1e7ce56d3d6fa748ab6d9af3fd7,1:642e92efb79421734881b53e1e1b18b6,1:f457c545a9ded88f18ecee47145a72c0,1:c0c7c76d30bd3dcaefc96f40275bdc0a,1:2838023a778dfaecdc212708f721b788 | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: catalogs?market=../etc | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: search?q= | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: search?q=%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F%C5%9F | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?date=2026-1-1 | ✓ |
| E. Güvenlik testleri | 404: /api/v1/products/1%20OR%201=1 | ✓ |
| E. Güvenlik testleri | 404: /api/v1/catalogs/x'%3B | ✓ |
| E. Güvenlik testleri | 404: /api/v1/products/0 | ✓ |
| E. Güvenlik testleri | 404: /api/v1/products/abc | ✓ |
| E. Güvenlik testleri | 404: /api/v1/markets/BIM | ✓ |
| E. Güvenlik testleri | 404: /api/v1/bilinmeyen | ✓ |
| E. Güvenlik testleri | 404: /api/v1/compare/x | ✓ |
| E. Güvenlik testleri | 404: /api/v1/home/1 | ✓ |
| E. Güvenlik testleri | 404: /api/v1/ | ✓ |
| E. Güvenlik testleri | 404: /api/v1/lib/bootstrap.php | ✓ |
| E. Güvenlik testleri | 404: /api/v1/endpoints/home.php | ✓ |
| E. Güvenlik testleri | 404: /api/v1/config.php | ✓ |
| E. Güvenlik testleri | SQL enjeksiyon metni q içinde düz metin olarak aranır (200, 0 sonuç) | ✓ |
| E. Güvenlik testleri | search SQL metni: 200, tablo yerinde | ✓ |
| E. Güvenlik testleri | 405: POST home | ✓ |
| E. Güvenlik testleri | 405: PUT products | ✓ |
| E. Güvenlik testleri | 405: DELETE compare | ✓ |
| E. Güvenlik testleri | 405: GET assistant | ✓ |
| E. Güvenlik testleri | 405: PATCH pages/gizlilik | ✓ |
| E. Güvenlik testleri | assistant gövde > 2 KB → 413 | ✓ |
| E. Güvenlik testleri | assistant geçersiz gövde → 400: {bozuk | ✓ |
| E. Güvenlik testleri | assistant geçersiz gövde → 400: [] | ✓ |
| E. Güvenlik testleri | assistant geçersiz gövde → 400: {"message":""} | ✓ |
| E. Güvenlik testleri | assistant geçersiz gövde → 400: {"message":"aaaaaaaaaaaaaaaaaaaaaaaaaaaa | ✓ |
| E. Güvenlik testleri | assistant geçersiz gövde → 400: {"message":"s\u00fct","include_expired": | ✓ |
| E. Güvenlik testleri | assistant geçersiz gövde → 400: {"message":["dizi"]} | ✓ |
| E. Güvenlik testleri | assistant geçersiz gövde → 400: {"message":"a","last_query":"bbbbbbbbbbb | ✓ |
| E. Güvenlik testleri | başlıklar: application/json, nosniff, no-referrer | ✓ |
| E. Güvenlik testleri | HTML/JS girdisi JSON içinde çalıştırılabilir çıktı üretmez | ✓ |
| E. Güvenlik testleri | HTTPS yoksa 400 HTTPS_REQUIRED | ✓ |
| E. Güvenlik testleri | hız sınırı: 5 istek/dk sonrası 429 + Retry-After | ✓ |
| E. Güvenlik testleri | lib/ ve endpoints/ dosyaları MK_API olmadan çıktı üretmez/çalışmaz | ✓ |
| E. Güvenlik testleri | sızıntı yok (gizli ayar, yönetici, gizli katalog, hata ayrıntısı, sunucu yolu, uzak görsel) | ✓ |
| F. Salt okunurluk (inc.php değiştirilmeden) | T-RO-1 sorgu günlüğü: API bağlantılarında yalnızca SELECT + oturum kilidi | ✓ |
| F. Salt okunurluk (inc.php değiştirilmeden) |   her API bağlantısı kilitlendi (bağlantı başına 1 kilit; ön kontrol + inc.php bağlantısı) | ✓ |
| F. Salt okunurluk (inc.php değiştirilmeden) | T-RO-2 API önyüklemesinden sonra q("INSERT …") veritabanınca reddedilir (1792) | ✓ |
| F. Salt okunurluk (inc.php değiştirilmeden) | T-RO-3 schema_v='4' iken API 503 döner, inc.php yüklenmez, hiçbir yazma olmaz | ✓ |
| F. Salt okunurluk (inc.php değiştirilmeden) |   karşılaştırma: aynı durumda SİTE (inc.php) şemayı günceller (schema_v '4'→'5') — korumanın neden gerekli olduğunu gösterir | ✓ |
| G. Statik denetimler | API ve ortak-veri.php içinde yazma SQL anahtar kelimesi yok | ✓ |
| G. Statik denetimler | php -l (PHP 8.3.6) tüm dosyalar | ✓ |
| G. Statik denetimler | PHP 8.0+ özel söz dizimi kullanılmıyor (7.4 uyumluluğu hedefi) | ✓ |
| G. Statik denetimler | kopya bütünlüğü: ara.php satır 7-16 → api/v1/endpoints/products.php | ✓ |
| G. Statik denetimler | kopya bütünlüğü: asistan.php satır 11-25 → api/v1/endpoints/suggest.php | ✓ |
| G. Statik denetimler | kopya bütünlüğü: asistan.php satır 33-41 → api/v1/endpoints/assistant.php | ✓ |
| G. Statik denetimler | kopya bütünlüğü: asistan.php satır 63-63 → api/v1/endpoints/assistant.php | ✓ |
| G. Statik denetimler | kopya bütünlüğü: asistan.php satır 45-59 → api/v1/lib/weblogic.php | ✓ |
| G. Statik denetimler | kopya bütünlüğü: asistan.php satır 55-55 → api/v1/lib/weblogic.php | ✓ |
| G. Statik denetimler | kopya bütünlüğü: sayfa.php satır 3-7 → api/v1/lib/weblogic.php | ✓ |
| G. Statik denetimler | kopya bütünlüğü: sayfa.php satır 4-4 → api/v1/lib/weblogic.php | ✓ |
| G. Statik denetimler | kopya bütünlüğü: sayfa.php satır 9-9 → api/v1/endpoints/pages.php | ✓ |
| G. Statik denetimler | kopya bütünlüğü: tema-modern-ana.php satır 66-70 → api/v1/endpoints/home.php | ✓ |
| G. Statik denetimler | ortak-veri.php bloğu orijinal tema-modern-ana.php içinde karakteri karakterine mevcut | ✓ |
| G. Statik denetimler | yeni tema-modern-ana.php = orijinal − blok + tek require_once satırı | ✓ |
| G. Statik denetimler | WebView projesine (MarketlerAndroid/) dokunulmadı | ✓ |
| H. Favori anahtarı: items.hash ile as_hkey karşılaştırması (birim testi) | hash ile as_hkey AYNI DEĞİL: as_hkey daha toleranslı (boşluk, Türkçe karakter, ondalık ayırıcı) | ✓ |
| H. Favori anahtarı: items.hash ile as_hkey karşılaştırması (birim testi) | bu örneklerde: hash aynıysa hkey de aynı (hash daha katı) | ✓ |
| H. Favori anahtarı: items.hash ile as_hkey karşılaştırması (birim testi) | ikisi de birim farkını (1000 g / 1 kg, L / Lt) ve marka yazımını (Coca-Cola / Coca Cola) ayırır | ✓ |
