# FAZ 1: Son Teslim, Test Raporu (revizyon 2)

**Ortam:** Bu bulut ortamı, PHP 8.3.6, MariaDB 10.11.14 (yetki denetimi açık). Veri **sentetik**. Şema, kullanıcının gönderdiği yalnızca yapı dökümü.
**Canlı sistem:** Hiçbir dosya yüklenmedi, canlı veritabanına bağlanılmadı. Veritabanı şeması değişmedi. WebView projesine dokunulmadı. Android ekranlarına başlanmadı.
**Sonuç:** Test takımı sıfırdan **iki kez** çalıştırıldı. Her çalıştırmada veritabanı, test sunucuları ve önbellekler yeniden kuruldu.
- Çalıştırma 1: **319/319**
- Çalıştırma 2: **320/320**
- Fark, sayısı veriye göre değişen bir bilgi satırından ibaret (bölüm 4). Başarı ölçütleri iki çalıştırmada aynı.

## 1. Revizyon 2'de yapılan değişiklikler

| # | Karar | Yapılan | Dosya |
|---|---|---|---|
| 1 | Favori: hash yalnızca ipucu | Favori artık `itemId:storeId:hash` ile sorgulanıyor. Favorinin **kendisi** `item_id` ile bulunuyor. Aynı market ve hash'e sahip başka kalemler yalnızca `candidates` (`confirmed:false`) olarak dönüyor; **otomatik eşleştirme yok**. Durumlar: `aktif`, `yaklasan`, `suresi_doldu`, `bulunamadi`; her biri açık bir mesajla. Yönetici adı değiştirdiyse `name_changed:true`. | `api/v1/endpoints/products.php` |
| 2 | `ara.php` eşitlik düzeltmesi | Mevcut sıralama ölçütleri aynen korundu; yalnızca sonlarına `,i.id ASC` eklendi. API kopyası da aynı. | `site/ara.php` (yalnızca 15. satır), `products.php` |
| 3 | Cloudflare | `CF-Connecting-IP` başlığı **yalnızca** isteği doğrudan gönderen adres (`REMOTE_ADDR`) Cloudflare IP aralıklarındaysa **ve** başlık geçerli bir IP ise kullanılıyor; aksi halde yok sayılıyor. IPv4 ve IPv6 CIDR denetimi var. | `api/v1/config.php`, `lib/ratelimit.php` |
| 4 | Kaldırılan uç noktalar | `meta.api_version` her yanıta eklendi. Kaldırılan `/meta` ve `/compare/{id}` alanlarının alan alan karşılığı ve gerekçesi belgelendi; karşılıkların eksiksizliği testle doğrulandı. | `lib/response.php`, `docs/FAZ1-UC-NOKTALAR.md` |
| 5 | Hata durumlarında JSON | Ölümcül PHP hatası ve `inc.php`'nin düz metin basıp çıkması durumunda da yanıt JSON hata zarfında dönüyor (500 / 503). | `lib/response.php`, `index.php` |

## 2. Yeniden yapılan testler (istenen 7 başlık)

| Başlık | Nasıl doğrulandı | Sonuç |
|---|---|---|
| **API hiç yazma yapmıyor** | (a) MariaDB sorgu günlüğü: 104 API bağlantısı, 356 sorgu → hepsi `SELECT`, her bağlantıda bir `READ ONLY` kilidi, 0 başka ifade. (b) Önyükleme sonrası `INSERT` denemesini veritabanı reddetti (1792). (c) **Bağımsız:** API, **yalnızca SELECT yetkisi olan** bir veritabanı kullanıcısıyla çalıştırıldı; 18 çağrının hepsi başarılı ve tam yetkili kullanıcıyla aynı yanıtı verdi. (d) **Bağımsız:** Bütün API çağrılarından önce ve sonra 13 tablonun `CHECKSUM TABLE` değeri aynı. | ✅ |
| **`schema_v` farklıyken güvenli durma** | `schema_v='4'` iken API `GET` ve `POST` isteklerinde 503 döndü. `inc.php` yüklenmedi; sorgu günlüğünde 0 yazma; tablo satır sayıları değişmedi. Karşılaştırma: aynı durumda site sayfası şemayı güncelliyor (`'4'`→`'5'`). | ✅ |
| **Web ile API eşitliği** | İki senaryoda 14 uç nokta sitedeki karşılığıyla karşılaştırıldı: 15 ürün arama filtresi (**artık birebir sıra**), 8 akıllı arama, 5 öneri, 10 sohbet, karşılaştırma, benzer ürünler, kataloglar, katalog detayı, market sayfası, sayfa metinleri. | ✅ |
| **Türkçe karakter ve ad farkları** | 7 Türkçe ad API'de baytı baytına korunuyor ve JSON'da `\u` kaçışı yok. 18 Türkçe sorguda (`İnci/inci/INCI`, `ISITEK/ısıtek/Isıtek`, `şeker/ŞEKER`, `çay/ÇAY`, `dağköy/DAĞKÖY`, `kırmızı/KIRMIZI`…) `ara.php`, `asistan.php` ve öneri sonuçları API ile birebir aynı. "Dağköy Peynir" ile "DAĞKÖY PEYNİR" favoride eşleştirilmiyor; aynı yazımlı yeni kalem yalnızca aday. | ✅ |
| **Sayfalama tutarlılığı** | 18 `ara.php` sorgusunun her biri 6 tekrarda bayt bayt aynı. Web'de 24'lük sayfalar boyunca tekrar veya kayıp yok. API'de 8 filtre/sıralama için limit 3, 5 ve 7 ile gezilen sayfaların birleşimi tek listeyle birebir aynı; tekrar yok, kayıp yok. Sıralama kuralları (tarih↓ad↑ / fiyat↑ / fiyat↓) korunuyor, eşitlikte kalem no artan. `ara.php` farkının yalnızca ORDER satırı olduğu otomatik denetlendi. | ✅ |
| **Cloudflare IP ve hız sınırı** | Güvenilmeyen kaynaktan, her istekte farklı sahte `CF-Connecting-IP` gönderilse de 6. istek 429 aldı. Güvenilir kaynakta sınır ziyaretçi IP'sine göre işliyor (bir ziyaretçinin sınırı diğerini etkilemiyor, IPv6 dahil). Güvenilir kaynak + geçersiz başlık değeri → başlık yok sayılıyor. CIDR birim testi 12/12 (IPv4/IPv6 sınır değerleri). | ✅ |
| **Hata durumlarında JSON** | Hata enjeksiyonu (yalnızca geçici test kopyalarında): ölümcül PHP hatası → JSON 500; site kodunun düz metin basıp çıkması → JSON 503; veritabanına ulaşılamıyor → JSON 503 + `Retry-After`. Testler boyunca alınan **63 hata yanıtının hepsi** (400/404/405/413/429/500/503) geçerli JSON hata zarfı. Ayrıntı veya sunucu yolu sızmıyor. | ✅ |

## 3. Kaldırılan uç noktaların karşılığı (C3)
- `/meta` ve `/compare/{id}` → 404.
- O ana kadar alınan **her** yanıtta `meta.api_version` ve `meta.generated_at` var.
- `/home` yanıtında `data_updated_at` var; önbellek süresi `Cache-Control` başlığında.
- `/compare` listesindeki **bütün** grupların **bütün** kalemleri için `/products/{id}.compare` alanı grupla birebir aynı. Grupta olmayan aktif kalemlerde `compare=null` ve açıklama dönüyor.

## 4. Bulgular

1. **`cmp_groups()` eşitlik sırası (sitede zaten var, `inc.php` değiştirilmedi).**
   - Karşılaştırma önbelleği her seferinde silindiğinde değiştirilmemiş site `fiyat-karsilastirma.php` için 8 denemede 2–3 farklı çıktı üretti. Önbellek dururken çıktı sabit.
   - Neden: fonksiyon ürünleri yalnızca `c.cdate DESC` ile sıralıyor. Eşit fiyatlı kayıtların sırası ve fiyat farkı eşit grupların sırası belirsiz.
   - Canlıda site ve API aynı `cmpcache.json` dosyasını kullandığı için **ikisi birbiriyle tutarlı**; yalnızca önbellek yenilendiğinde sıra değişebiliyor.
   - Testte bunu yalıtmak için yeni site, eski sitenin karşılaştırma önbelleğini kullandı.
   - Öneri: `ara.php` düzeltmesindeki gibi sıralamaya `i.id` eklemek. Bu `inc.php` değişikliği gerektirdiği için **onayınıza bırakıldı**.
2. **`ara.php` belirsizliği aralıklı.** Bu iki çalıştırmada eski `ara.php` 6'şar tekrarda farklı çıktı vermedi. Belirsizlik eski ve yeni site arasındaki eşitlik sırası farkında görüldü; daha önceki elle yapılan denemede de 6 tekrarda 2 farklı çıktı görülmüştü. Düzeltme, benzersiz `i.id` ölçütü sayesinde yapısı gereği kararlıdır; testler bunu doğruladı.
3. **`items.hash` Türkçe "I" sorunu** (önceki rapordan, değişmedi). Favori tasarımı bu yüzden hash'e güvenmiyor.
4. **Cloudflare IP listesi** geliştiricinin bilgisinden yazıldı; bu ortamdan `cloudflare.com` adresine erişilemedi. Canlıya almadan önce https://www.cloudflare.com/ips/ ile karşılaştırılmalı.

## 5. Doğrulanamayanlar
| Konu | Durum |
|---|---|
| Hosting PHP sürümü; PHP 7.4 / 8.4 ile çalışma | Yalnızca 8.3 ile test edildi; 8.0+ sözdizimi kullanılmadığı otomatik denetlendi. Gerçek sürüm cPanel → MultiPHP Manager'dan öğrenilmeli |
| Gerçek veritabanı sürümü, `products`/`prices` sayıları, hash'in gerçek verideki tekrar oranı | D.3 sorgularının sonucu bekleniyor |
| Gerçek veri hacminde performans | Sentetik veri küçük |
| Apache `.htaccess` yönlendirmesi ve gerçek Cloudflare arkasında IP davranışı | Testler PHP'nin yerleşik sunucusunda yapıldı; canlı öncesi bir alt klasörde deneme önerilir |

## 6. Değişiklik listesi (FAZ 1 son)
| Dosya | Durum |
|---|---|
| `MarketlerNative/server/site/ortak-veri.php` | Yeni (taşınan `mt_featured()`, içerik aynı) |
| `MarketlerNative/server/site/tema-modern-ana.php` | Değişen kopya (blok → 1 `require_once`) |
| `MarketlerNative/server/site/ara.php` | Değişen kopya (15. satır: `,i.id ASC`) |
| `MarketlerNative/server/site/api/**` | Yeni: 14 uç nokta, 6 kütüphane, `index.php`, `config.php`, `.htaccess` |
| `MarketlerNative/server/tests/**` | Test betiği, sentetik veri, şema dökümü |
| `docs/FAZ1-UC-NOKTALAR.md`, `docs/FAZ1-TEST-RAPORU.md`, `MarketlerNative/server/README.md` | Güncellendi |
| `inc.php`, `eslestirme.php`, `asistan*.php`, `sayfa.php`, `cron.php`, `admin*`, veritabanı şeması, `MarketlerAndroid/` | **Değişmedi** |

## 7. Test sonuçları (Çalıştırma 2, tam liste)

Toplam 320 kontrol · 320 geçti · 0 başarısız

| Grup | Kontrol | Sonuç |
|---|---|---|
| A. Kurulum | test veritabanı oluşturuldu | ✓ |
| A. Kurulum | kullanıcının şema dökümü yüklendi (yalnızca yapı) | ✓ |
| A. Kurulum | yalnızca SELECT yetkili test kullanıcısı (mk_test_ro) hazır | ✓ |
| A. Kurulum | test sunucuları başladı (eski site, yeni site+API, hız sınırı, Cloudflare, SELECT-yetkili, 3 hata enjeksiyonu) | ✓ |
| A. Kurulum | PHP sürümü (yerel test) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) | senaryo '0 indirimli': 17 sayfa eski ve yeni sitede aynı (ana sayfa iki kez: önbelleksiz + önbellekli) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   bilgi: /ara.php içerik aynı; yalnızca aynı adlı kartların sırası farklı (onaylanan ara.php eşitlik düzeltmesi + eski sitenin belirsiz sırası) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   bilgi: /ara.php?q=s%C3%BCt içerik aynı; yalnızca aynı adlı kartların sırası farklı (onaylanan ara.php eşitlik düzeltmesi + eski sitenin belirsiz sırası) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   '0 indirimli': 14 sayfa BAYT BAYT aynı (ana sayfa dahil: evet) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) | senaryo '1-3 indirimli (2)': 17 sayfa eski ve yeni sitede aynı (ana sayfa iki kez: önbelleksiz + önbellekli) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   bilgi: /ara.php içerik aynı; yalnızca aynı adlı kartların sırası farklı (onaylanan ara.php eşitlik düzeltmesi + eski sitenin belirsiz sırası) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   bilgi: /ara.php?q=s%C3%BCt içerik aynı; yalnızca aynı adlı kartların sırası farklı (onaylanan ara.php eşitlik düzeltmesi + eski sitenin belirsiz sırası) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   '1-3 indirimli (2)': 14 sayfa BAYT BAYT aynı (ana sayfa dahil: evet) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) | senaryo '4+ indirimli (5)': 17 sayfa eski ve yeni sitede aynı (ana sayfa iki kez: önbelleksiz + önbellekli) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   bilgi: /ara.php içerik aynı; yalnızca aynı adlı kartların sırası farklı (onaylanan ara.php eşitlik düzeltmesi + eski sitenin belirsiz sırası) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   bilgi: /ara.php?q=s%C3%BCt içerik aynı; yalnızca aynı adlı kartların sırası farklı (onaylanan ara.php eşitlik düzeltmesi + eski sitenin belirsiz sırası) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   '4+ indirimli (5)': 14 sayfa BAYT BAYT aynı (ana sayfa dahil: evet) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) | senaryo 'ürünsüz / katalogsuz': 15 sayfa eski ve yeni sitede aynı (ana sayfa iki kez: önbelleksiz + önbellekli) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   'ürünsüz / katalogsuz': 14 sayfa BAYT BAYT aynı (ana sayfa dahil: evet) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   'ürünsüz / katalogsuz' ana sayfa boş durum kartı | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) | eski ve yeni sitede PHP hata/uyarısı yok (sunucu günlüğü) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) | SONUÇ: ortak-veri.php değişikliği kabul koşulu (tüm senaryolar aynı) | ✓ |
| B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo) |   bilgi (mevcut site): karşılaştırma önbelleği her seferinde silinirse orijinal fiyat-karsilastirma.php 8 denemede 2 farklı çıktı verir (cmp_groups eşitlik sırası; inc.php değiştirilmedi) | ✓ |
| B2. ara.php: eşitlikte i.id (tekrarlanabilirlik, içerik korunumu, sayfalama) | düzeltme sonrası 18 sorgunun her biri 6 tekrarda bayt bayt aynı | ✓ |
| B2. ara.php: eşitlikte i.id (tekrarlanabilirlik, içerik korunumu, sayfalama) |   karşılaştırma: düzeltme öncesi (orijinal ara.php) 0 sorgu tekrarlarda farklı çıktı verdi | ✓ |
| B2. ara.php: eşitlikte i.id (tekrarlanabilirlik, içerik korunumu, sayfalama) | içerik korunumu: tüm sorgularda toplam aynı; tek sayfalık sonuçlarda ürün kümesi aynı | ✓ |
| B2. ara.php: eşitlikte i.id (tekrarlanabilirlik, içerik korunumu, sayfalama) | web sayfalaması (off=0,24,…): düzeltme sonrası 0 sorguda tekrar/kayıp (0 beklenir) | ✓ |
| B2. ara.php: eşitlikte i.id (tekrarlanabilirlik, içerik korunumu, sayfalama) |   karşılaştırma: düzeltme öncesi 0 sorguda sayfalar arası tekrar/kayıp görüldü (giderilen hata) | ✓ |
| B2. ara.php: eşitlikte i.id (tekrarlanabilirlik, içerik korunumu, sayfalama) | ara.php farkı yalnızca ORDER satırı (filtreler, WHERE ve sayfalama kodu aynı) | ✓ |
| B2. ara.php: eşitlikte i.id (tekrarlanabilirlik, içerik korunumu, sayfalama) | sayfalama: limit 3/5/7 ile gezilen sayfalar = tek liste; tekrar yok, kayıp yok (8 filtre/sıralama) | ✓ |
| B2. ara.php: eşitlikte i.id (tekrarlanabilirlik, içerik korunumu, sayfalama) | sıralama kuralları korunuyor (tarih↓ad↑ / fiyat↑ / fiyat↓) ve eşitlikte kalem no artan | ✓ |
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
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | favori (geçen haftaki kalem): kendisi döner, durum "suresi_doldu", favori değiştirilmez | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) |   aynı market+hash taşıyan kalemler yalnızca ADAY (confirmed=false), otomatik eşleşme yok | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) |   kullanıcıya açık mesaj: süre doldu + aday doğrulanmadı | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | favori (aktif kalem): durum "aktif", mesaj yok | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | favori (silinmiş/olmayan kalem): durum "bulunamadi", item=null, açık mesaj | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | favori: gizli/onay bekleyen katalogdaki kalem gösterilmez (bulunamadi) | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | favori: kayıtlı market uyuşmazsa aynı kalem sayılmaz | ✓ |
| C. Uç nokta işlev testleri (senaryo: 2 indirimli) | favori: yönetici adı değiştirdiyse kalem yine bulunur, name_changed=true | ✓ |
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
| C3. Kaldırılan uç noktaların karşılığı | /meta yok (404) | ✓ |
| C3. Kaldırılan uç noktaların karşılığı | /compare/{id} yok (404) | ✓ |
| C3. Kaldırılan uç noktaların karşılığı | /meta.data_updated_at → /home data.data_updated_at | ✓ |
| C3. Kaldırılan uç noktaların karşılığı | /meta.api_version ve server_time → HER yanıtta meta.api_version ve meta.generated_at (şu ana kadarki 327 yanıt) | ✓ |
| C3. Kaldırılan uç noktaların karşılığı | /meta.ttl → her yanıtta Cache-Control max-age | ✓ |
| C3. Kaldırılan uç noktaların karşılığı | /products/{id}.compare, /compare listesindeki grupla birebir aynı (37 kalem, 13 grup) | ✓ |
| C3. Kaldırılan uç noktaların karşılığı |   grupta olmayan 26 aktif kalemde compare=null ve açıklama var | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | home: öne çıkanlar (ad, fiyat, sıra) aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | home: öne çıkanlar başlığı aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | home: güncel kataloglar aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | home: öne çıkan karşılaştırma aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php []: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [q=s%C3%BCt]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [q=s%C3%BCt&o=asc]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [q=s%C3%BCt&o=desc]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [m=bim]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [k=gida&st=hepsi]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [st=yaklasan]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [st=dolmus]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [min=100&max=500]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [q=%25]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [q=_]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [q=Kampanya_Sepeti]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [st=hepsi&off=24]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [q=e%C5%9Fitlik&o=asc]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 2 indirimli) | products ↔ ara.php [k=diger&off=24]: toplam ve kart sırası BİREBİR aynı | ✓ |
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
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php []: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [q=s%C3%BCt]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [q=s%C3%BCt&o=asc]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [q=s%C3%BCt&o=desc]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [m=bim]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [k=gida&st=hepsi]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [st=yaklasan]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [st=dolmus]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [min=100&max=500]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [q=%25]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [q=_]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [q=Kampanya_Sepeti]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [st=hepsi&off=24]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [q=e%C5%9Fitlik&o=asc]: toplam ve kart sırası BİREBİR aynı | ✓ |
| D. Web ↔ API eşdeğerlik (senaryo: 5 indirimli) | products ↔ ara.php [k=diger&off=24]: toplam ve kart sırası BİREBİR aynı | ✓ |
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
| T. Türkçe karakter ve ürün adı farkları | UTF-8: Türkçe adlar API'de baytı baytına korunuyor (7 ad) | ✓ |
| T. Türkçe karakter ve ürün adı farkları |   JSON, Türkçe karakterleri \u kaçışı olmadan UTF-8 taşıyor | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [İnci] ara.php / asistan.php / öneri ile API aynı (1 / 1 / 1 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [inci] ara.php / asistan.php / öneri ile API aynı (1 / 1 / 1 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [INCI] ara.php / asistan.php / öneri ile API aynı (1 / 1 / 1 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [ISITEK] ara.php / asistan.php / öneri ile API aynı (5 / 5 / 4 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [ısıtek] ara.php / asistan.php / öneri ile API aynı (5 / 5 / 4 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [Isıtek] ara.php / asistan.php / öneri ile API aynı (5 / 5 / 4 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [şeker] ara.php / asistan.php / öneri ile API aynı (1 / 1 / 1 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [ŞEKER] ara.php / asistan.php / öneri ile API aynı (1 / 1 / 1 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [Şeker] ara.php / asistan.php / öneri ile API aynı (1 / 1 / 1 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [çay] ara.php / asistan.php / öneri ile API aynı (2 / 2 / 2 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [ÇAY] ara.php / asistan.php / öneri ile API aynı (2 / 2 / 2 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [dağköy] ara.php / asistan.php / öneri ile API aynı (2 / 1 / 1 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [DAĞKÖY] ara.php / asistan.php / öneri ile API aynı (2 / 1 / 1 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [yoğurt] ara.php / asistan.php / öneri ile API aynı (1 / 1 / 1 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [YOĞURT] ara.php / asistan.php / öneri ile API aynı (1 / 1 / 1 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [kırmızı] ara.php / asistan.php / öneri ile API aynı (2 / 1 / 1 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [KIRMIZI] ara.php / asistan.php / öneri ile API aynı (2 / 1 / 1 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | [eşitlik iota] ara.php / asistan.php / öneri ile API aynı (3 / 3 / 3 sonuç) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | favori "Dağköy Peynir 500 g" (geçen hafta) → bu hafta "DAĞKÖY PEYNİR 500 G" ile EŞLEŞTİRİLMEZ (hash farklı, aday yok) | ✓ |
| T. Türkçe karakter ve ürün adı farkları | favori "Kırmızı Mercimek 1 kg" → aynı yazımlı yeni kalem yalnızca ADAY, confirmed=false | ✓ |
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
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?fav=1:1:xyz | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?fav=0:1:0cc175b9c0f1b6a831c399e269772661 | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?fav=1:0cc175b9c0f1b6a831c399e269772661 | ✓ |
| E. Güvenlik testleri | 400 INVALID_PARAMETER: products?fav=1:1:c4ca4238a0b923820dcc509a6f75849b,2:1:c81e728d9d4c2f636f067f89cc14862c,3:1:eccbc87e4b5ce2fe28308fd9f2a7baf3,4:1:a87ff679a2f3e71d9181a67b7542122c,5:1:e4da3b7fbbce2345d7772b0674a318d5,6:1:1679091c5a880faf6fb5e6087eb1b2dc,7:1:8f14e45fceea167a5a36dedd4bea2543,8:1:c9f0f895fb98ab9159f51fd0297e236d,9:1:45c48cce2e2d7fbdea1afc51c7c6ad26,10:1:d3d9446802a44259755d38e6d163e820,11:1:6512bd43d9caa6e02c990b0a82652dca,12:1:c20ad4d76fe97759aa27a0c99bff6710,13:1:c51ce410c124a10e0db5e4b97fc2af39,14:1:aab3238922bcc25a6f606eb525ffdc56,15:1:9bf31c7ff062936a96d3c8bd1f8f2ff3,16:1:c74d97b01eae257e44aa9d5bade97baf,17:1:70efdf2ec9b086079795c442636b55fb,18:1:6f4922f45568161a8cdf4ad2299f6d23,19:1:1f0e3dad99908345f7439f8ffabdffc4,20:1:98f13708210194c475687be6106a3b84,21:1:3c59dc048e8850243be8079a5c74d079,22:1:b6d767d2f8ed5d21a44b0e5886680cb9,23:1:37693cfc748049e45d87b8c7d8b9aacd,24:1:1ff1de774005f8da13f42943881c655f,25:1:8e296a067a37563370ded05f5a3bf3ec,26:1:4e732ced3463d06de0ca9a15b6153677,27:1:02e74f10e0327ad868d138f2b4fdd6f0,28:1:33e75ff09dd601bbe69f351039152189,29:1:6ea9ab1baa0efb9e19094440c317e21b,30:1:34173cb38f07f89ddbebc2ac9128303f,31:1:c16a5320fa475530d9583c34fd356ef5,32:1:6364d3f0f495b6ab9dcf8d3b5c6e0b01,33:1:182be0c5cdcd5072bb1864cdee4d3d6e,34:1:e369853df766fa44e1ed0ff613f563bd,35:1:1c383cd30b7c298ab50293adfecb7b18,36:1:19ca14e7ea6328a42e0eb13d585e4c22,37:1:a5bfc9e07964f8dddeb95fc584cd965d,38:1:a5771bce93e200c36f7cd9dfd0e5deaa,39:1:d67d8ab4f4c10bf22aa353e27879133c,40:1:d645920e395fedad7bbbed0eca3fe2e0,41:1:3416a75f4cea9109507cacd8e2f2aefc,42:1:a1d0c6e83f027327d8461063f4ac58a6,43:1:17e62166fc8586dfa4d1bc0e1742c08b,44:1:f7177163c833dff4b38fc8d2872f1ec6,45:1:6c8349cc7260ae62e3b1396831a8398f,46:1:d9d4f495e875a2e075a1a4a6e1b9770f,47:1:67c6a1e7ce56d3d6fa748ab6d9af3fd7,48:1:642e92efb79421734881b53e1e1b18b6,49:1:f457c545a9ded88f18ecee47145a72c0,50:1:c0c7c76d30bd3dcaefc96f40275bdc0a,51:1:2838023a778dfaecdc212708f721b788 | ✓ |
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
| E. Güvenlik testleri | Cloudflare dışı kaynak: her istekte farklı sahte CF-Connecting-IP gönderilse de sınır aşılamaz (6. istek 429) | ✓ |
| E. Güvenlik testleri | güvenilir Cloudflare kaynağı: sınır ziyaretçi IP'sine göre (A: 6. istek 429; B (IPv6) etkilenmez) | ✓ |
| E. Güvenlik testleri | güvenilir kaynak + geçersiz CF-Connecting-IP değeri → başlık yok sayılır, bağlantı adresi sayılır (6. istek 429) | ✓ |
| E. Güvenlik testleri | CIDR denetimi birim testi (IPv4/IPv6, sınır değerleri, geçersiz adres) — 12 durum | ✓ |
| E. Güvenlik testleri | ölümcül PHP hatası → JSON 500 INTERNAL_ERROR (ayrıntı sızmaz) | ✓ |
| E. Güvenlik testleri | site kodunun düz metin basıp çıkması (inc.php db() davranışı) → JSON 503 SERVICE_UNAVAILABLE | ✓ |
| E. Güvenlik testleri | veritabanına ulaşılamıyor → JSON 503 + Retry-After | ✓ |
| E. Güvenlik testleri |   POST /assistant aynı durumda da JSON 503 | ✓ |
| E. Güvenlik testleri | şu ana kadarki TÜM hata yanıtları (63 adet: 400/404/405/413/429/500/503) geçerli JSON hata zarfı | ✓ |
| E. Güvenlik testleri | lib/ ve endpoints/ dosyaları MK_API olmadan çıktı üretmez/çalışmaz | ✓ |
| E. Güvenlik testleri | sızıntı yok (gizli ayar, yönetici, gizli katalog, hata ayrıntısı, sunucu yolu, uzak görsel) | ✓ |
| F. Salt okunurluk (inc.php değiştirilmeden) | T-RO-1 sorgu günlüğü: API bağlantılarında yalnızca SELECT + oturum kilidi | ✓ |
| F. Salt okunurluk (inc.php değiştirilmeden) |   her API bağlantısı kilitlendi (bağlantı başına 1 kilit; ön kontrol + inc.php bağlantısı) | ✓ |
| F. Salt okunurluk (inc.php değiştirilmeden) | T-RO-2 API önyüklemesinden sonra q("INSERT …") veritabanınca reddedilir (1792) | ✓ |
| F. Salt okunurluk (inc.php değiştirilmeden) | T-RO-4 (bağımsız) YALNIZCA SELECT yetkili kullanıcıyla bütün uç noktalar çalışıyor ve root ile aynı yanıtı veriyor (18 çağrı) | ✓ |
| F. Salt okunurluk (inc.php değiştirilmeden) | T-RO-5 (bağımsız) bütün API çağrılarından önce/sonra 13 tablonun sağlama toplamı (CHECKSUM TABLE) aynı | ✓ |
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
