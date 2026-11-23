# FAZ 1: Test Raporu (revizyon 3)

**Ortam:** Bu bulut ortamı (izole kapsayıcı), PHP 8.3.6, MariaDB 10.11.14 (yetki denetimi açık). Veri **sentetik**; şema kullanıcının gönderdiği yalnızca yapı dökümü.
**Canlı sistem:** Hiçbir dosya yüklenmedi; canlı veritabanına bağlanılmadı. Veritabanı şeması, `inc.php`, `eslestirme.php`, `cron.php`, admin dosyaları ve WebView projesi değişmedi. Android ekranlarına başlanmadı. **Canlıya yükleme onayı yok.**

## Sonuç

| Çalıştırma (her biri sıfırdan, benzersiz veritabanı/kullanıcı/klasör ile) | Önceki testler | Yeni (R3) testler | Toplam | Çalıştırma sonrası artık kaynak |
|---|---|---|---|---|
| 1 | 322/322 | 59/59 | **381/381** | Yok (veritabanı, kullanıcı, klasör silindi; sorgu günlüğü ayarları geri yüklendi) |
| 2 | 322/322 | 59/59 | **381/381** | Yok |

**Önceki 320 testin korunması:** Revizyon 2'deki bütün kontroller ad ad eşleştirildi.
- **Değişmeden korunan:** 317 kontrol.
- **Değiştirilip güçlendirilen:** Kurulum grubundaki 3 kontrol, yeni güvenlik kuralları nedeniyle.
  - "test veritabanı oluşturuldu" → "test veritabanı oluşturuldu + işaret tablosu".
  - "`mk_test_ro` kullanıcısı hazır" → "bu çalıştırmaya özel `mk_ro_<belirteç>` kullanıcısı".
  - "test sunucuları başladı" → 11 örnekle.
- **Önceki bölüme eklenen:** Kurulum grubuna 2 kontrol (yerel sunucu denetimi, benzersiz adlar).
- Bu yüzden önceki bölüm 320 yerine 322 kontrol içeriyor.

## 1. Değişiklik listesi (revizyon 2 → 3)

| Dosya | Satırlar (yeni dosyada) | Değişiklik |
|---|---|---|
| `site/api/v1/lib/map.php` | 68 | `fav_key` = `itemId:storeId:hash` (tek standart; `/products?fav=` ile aynı) |
| `site/api/v1/lib/proxy.php` | 1–42 (yeni) | Güvenilir vekil doğrulaması: `mk_ip_in_cidr`, `mk_from_trusted_proxy`, `mk_client_ip`, `mk_is_https` |
| `site/api/v1/index.php` | 9, 13–14 | `proxy.php` yükleniyor. HTTPS yalnızca doğrudan bağlantıdan ya da **güvenilir vekil** başlığından kabul ediliyor; eski koşulsuz `X-Forwarded-Proto` / `CF-Visitor` kabulü kaldırıldı |
| `site/api/v1/lib/ratelimit.php` | 2–4, 7–9, 14–15, 18–23 | IP fonksiyonları `proxy.php` dosyasına taşındı. Sayaç klasörü/dosyası hatasında **güvenli hata** (`mk_rate_fail`: 503 + `Retry-After` + sunucu günlüğü) |
| `tests/guard.php` | 1–40 (yeni) | Test betiği güvenlik korumaları (yerel sunucu, ad kalıpları, varlık denetimi, silme güvencesi, klasör silme güvencesi) |
| `tests/run.php` | 32 bölüm (3–120 kurulum, R3-S/F/H/R/Z grupları, F grubu sorgu günlüğü, rapor) | Güvenli kurulum/temizlik, benzersiz kaynaklar, `TRUNCATE` kaldırıldı, örnek başına `TMPDIR`, yeni test grupları, ayrı rapor tabloları |
| `docs/FAZ1-API-SPESIFIKASYONU.md` | 14, 105, 212, 216 | HTTPS kuralı, `fav_key` biçimi, `fav` parametresi, eski favori maddesinin tarihçe notu |
| `docs/FAZ1-UC-NOKTALAR.md` | 59–60, 87 | Favori anahtarı standardı notu, `meta.api_version`, örnek JSON'daki `fav_key` |
| `MarketlerNative/server/README.md` | 16, 21–22, 26–27, 36–38, 41, 45–53 | `proxy.php`, `guard.php` satırları; "yalnızca izole yerel test ortamı" uyarısı; test betiği güvenliği bölümü |

**Değişmeyen site davranışı:** Bu revizyonda site dosyalarına (`ara.php`, `tema-modern-ana.php`, `ortak-veri.php`) dokunulmadı. B grubu (eski ve yeni site HTML karşılaştırması) ve D grubu (web ile API eşdeğerliği) yeniden geçti.

## 2. Yeni testler (R3), özet

| Grup | Kapsam | Sonuç |
|---|---|---|
| **R3-S Test betiği güvenliği** | 11 adreste yerel sunucu denetimi; ad kalıpları; silme koruması (yanlış belirteç, `mysql` / `information_schema`, işaret tablosu olmayan tuzak veritabanı, başka çalıştırmanın belirteci); kullanıcı çakışması; klasör silme sınırı; `MK_TEST_DB_HOST=10.0.0.5 / db.example.com / localhost:3307` ile **gerçek alt süreç** çalıştırma → çıkış kodu 3, hiçbir kaynak oluşmadı | 13/13 |
| **R3-F Favori anahtarı** | **Olumlu:** 8 uç noktadan toplanan bütün ürün nesnelerinde `fav_key` = `itemId:storeId:hash`; 50 anahtarın değiştirilmeden geri sorgulanması; aktif kalem. **Olumsuz:** eski `storeId:hash` biçimi dahil 10 hatalı biçim → 400; yanlış market → `bulunamadi`; farklı hash → kalem `item_id` ile bulunur, `name_changed=true`, aday üretilmez. **Davranış korundu:** otomatik eşleştirme yok | 17/17 |
| **R3-H HTTPS sahte başlık** | Canlı ayarda (127.0.0.1 güvenilir değil) 7 sahte başlık birleşimi → 400 `HTTPS_REQUIRED`. Güvenilir vekil benzetiminde 8 senaryo (geçerli `X-Forwarded-Proto` / `CF-Visitor` → 200; eksik, `http` veya bozuk değer → 400). Depodaki `MK_API_ALLOW_HTTP=false`. `.htaccess` ile çelişki yok | 17/17 |
| **R3-R Hız sınırı dosya sistemi hatası** | Sayaç klasörü oluşturulamıyor / sayaç dosyası açılamıyor → GET ve POST 503 + `Retry-After`, sunucu günlüğünde kayıt; normal ortamda 200 | 7/7 |
| **R3-Z Temizlik** | Test veritabanı, kullanıcı ve klasör silindi; sorgu günlüğü ayarları geri yüklendi; başka veritabanına/kullanıcıya dokunulmadı | 5/5 |

## 3. Açık kalan riskler

| # | Risk | Durum / gereken |
|---|---|---|
| 1 | **Cloudflare IP listesi** geliştiricinin bilgisinden yazıldı; bu ortamdan doğrulanamadı | Canlı öncesi https://www.cloudflare.com/ips/ ile karşılaştırılmalı; liste değiştikçe güncellenmeli |
| 2 | **`mod_remoteip` + Cloudflare "Flexible SSL"** birleşimi: `mod_remoteip` `REMOTE_ADDR` değerini ziyaretçi adresine çevirir. Sunucuya bağlantı HTTP ise API vekil başlığına güvenmez ve **bütün istekleri 400 ile reddeder** | Hosting ayarı öğrenilmeli. Cloudflare "Full / Full (strict)" ile sorun yok (doğrudan HTTPS) |
| 3 | **`cmp_groups()` eşitlik sırası** (`inc.php`, mevcut): karşılaştırma önbelleği yenilenince eşit fiyatlı satırların ve fiyat farkı eşit grupların sırası değişebiliyor. Site ve API aynı önbelleği kullandığından ikisi tutarlı | Düzeltme `inc.php` değişikliği gerektirir; onayınıza bırakıldı. Testler önbelleği eşitleyerek bu etkiyi yalıtıyor |
| 4 | **`items.hash` Türkçe "I" / yazım farkları**: favori hash ile eşleşmiyor (tasarım gereği; yalnızca aday) | Gerçek tekrar oranı D.3 sorgularıyla ölçülecek |
| 5 | **PHP sürümü**: yalnızca 8.3 ile test edildi | cPanel → MultiPHP Manager'dan sürüm öğrenilip o sürümle test edilmeli |
| 6 | **Gerçek veri hacmi ve Apache**: testler sentetik veriyle ve PHP'nin yerleşik sunucusunda yapıldı | Canlı öncesi bir alt klasörde deneme önerilir |
| 7 | **Hız sınırı güvenli hata davranışı**: geçici klasör dolarsa veya yazılamazsa API 503 döner (bilinçli tercih: sınırsız çalışmaktansa durmak) | İzleme: sunucu günlüğünde `MARKETLER API hız sınırı hatası` |
| 8 | **Test betiği yönetici yetkisi ister** (veritabanı/kullanıcı oluşturma, `SET GLOBAL general_log`) | README'de "yalnızca izole yerel test ortamı" olarak belirtildi; betik uzak sunucuyu koşulsuz reddeder |

## 4. Test sonuçları (Çalıştırma 2, tam liste)

Toplam 381 kontrol · 381 geçti · 0 başarısız (önceki testler 322/322 · yeni R3 testleri 59/59)

### Yeni testler (revizyon 3)

| Grup | Kontrol | Sonuç |
|---|---|---|
| R3-S. Test betiği güvenliği | yerel sunucu denetimi: 11 adres (yalnızca localhost/127.0.0.1/::1 kabul) | ✓ |
| R3-S. Test betiği güvenliği | veritabanı adı denetimi: 8 ad (yalnızca mk_api_test_<12 hex>) | ✓ |
| R3-S. Test betiği güvenliği | kullanıcı adı denetimi: 5 ad (var olan/genel kullanıcılar reddedilir) | ✓ |
| R3-S. Test betiği güvenliği | silme koruması: yanlış belirteç → silinmez | ✓ |
| R3-S. Test betiği güvenliği | silme koruması: kalıba uymayan ad (mysql, information_schema) → silinmez | ✓ |
| R3-S. Test betiği güvenliği | silme koruması: kalıba uyan ama işaret tablosu OLMAYAN veritabanı → silinmez | ✓ |
| R3-S. Test betiği güvenliği | silme koruması: başka çalıştırmanın belirteci → silinmez | ✓ |
| R3-S. Test betiği güvenliği | kullanıcı çakışması algılanır (var olan kullanıcı → betik durur, yetkisine dokunulmaz) | ✓ |
| R3-S. Test betiği güvenliği | silinecek klasör denetimi: yalnızca bu çalıştırmanın geçici klasörü | ✓ |
| R3-S. Test betiği güvenliği | uzak sunucu reddi: MK_TEST_DB_HOST=10.0.0.5 → çıkış kodu 3, bağlantı yok | ✓ |
| R3-S. Test betiği güvenliği | uzak sunucu reddi: MK_TEST_DB_HOST=db.example.com → çıkış kodu 3, bağlantı yok | ✓ |
| R3-S. Test betiği güvenliği | uzak sunucu reddi: MK_TEST_DB_HOST=localhost:3307 → çıkış kodu 3, bağlantı yok | ✓ |
| R3-S. Test betiği güvenliği |   reddedilen çalıştırmalar veritabanı/kullanıcı/klasör oluşturmadı | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMLU: 163 ürün nesnesinin hepsinde fav_key = "itemId:storeId:hash" (8 farklı uç nokta) | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMLU: ürün nesnesinden alınan fav_key DEĞİŞTİRİLMEDEN /products?fav= ile sorgulanabiliyor (50 anahtar, gidiş-dönüş aynı) | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMLU: aktif kalem → state=aktif, item.id aynı, adaylar onaysız | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMSUZ: eski biçim storeId:hash → 400 INVALID_PARAMETER | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMSUZ: itemId:hash (market yok) → 400 INVALID_PARAMETER | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMSUZ: fazla parça → 400 INVALID_PARAMETER | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMSUZ: büyük harf hash → 400 INVALID_PARAMETER | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMSUZ: kısa hash → 400 INVALID_PARAMETER | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMSUZ: itemId 0 → 400 INVALID_PARAMETER | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMSUZ: storeId 0 → 400 INVALID_PARAMETER | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMSUZ: negatif → 400 INVALID_PARAMETER | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMSUZ: boş öğe yalnızca → boş öğeler yok sayılır | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMSUZ: sayı olmayan itemId → 400 INVALID_PARAMETER | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMSUZ: boşluk → 400 INVALID_PARAMETER | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMSUZ: doğru biçim ama yanlış market → bulunamadi (başka marketin kalemi döndürülmez) | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | OLUMSUZ: doğru kalem ama farklı hash → kalem item_id ile bulunur, name_changed=true, hash ile ADAY ÜRETİLMEZ | ✓ |
| R3-F. Favori anahtarı (itemId:storeId:hash) | DAVRANIŞ KORUNDU: geçen haftanın favorisi otomatik yeni kaleme çevrilmez (item.id = kayıtlı id, adaylar confirmed=false) | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilmeyen kaynak + sahte X-Forwarded-Proto: https → 400 HTTPS_REQUIRED | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilmeyen kaynak + sahte CF-Visitor: {"scheme":"https"} → 400 HTTPS_REQUIRED | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilmeyen kaynak + sahte X-Forwarded-Proto: https + CF-Visitor: {"scheme":"https"} → 400 HTTPS_REQUIRED | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilmeyen kaynak + sahte X-Forwarded-Proto: HTTPS, http → 400 HTTPS_REQUIRED | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilmeyen kaynak + sahte X-Forwarded-Ssl: on → 400 HTTPS_REQUIRED | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilmeyen kaynak + sahte Front-End-Https: on → 400 HTTPS_REQUIRED | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilmeyen kaynak + sahte X-Forwarded-Proto: https + CF-Connecting-IP: 1.2.3.4 → 400 HTTPS_REQUIRED | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilir vekil + X-Forwarded-Proto: https → 200 | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilir vekil + CF-Visitor: {"scheme":"https"} → 200 | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilir vekil + X-Forwarded-Proto: https, http → 200 | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilir vekil + başlık yok → 400 | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilir vekil + X-Forwarded-Proto: http → 400 | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilir vekil + CF-Visitor: {"scheme":"http"} → 400 | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilir vekil + CF-Visitor: https → 400 | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | güvenilir vekil + CF-Visitor: {"scheme":"httpsx"} → 400 | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | geliştirme ayarı (MK_API_ALLOW_HTTP=true) yalnızca test kopyasında; depodaki config.php değeri false | ✓ |
| R3-H. HTTPS sahte başlık senaryoları | .htaccess ile çelişki yok: Apache yalnızca HTTP→HTTPS yönlendirir; PHP aynı başlıkları yalnızca güvenilir vekilden kabul eder (daha katı) | ✓ |
| R3-R. Hız sınırı dosya sistemi hatası (güvenli hata) | sayaç klasörü oluşturulamadı → istek REDDEDİLİR: JSON 503 SERVICE_UNAVAILABLE + Retry-After (sınırlama sessizce kapanmaz) | ✓ |
| R3-R. Hız sınırı dosya sistemi hatası (güvenli hata) |   aynı durumda POST /assistant da 503 | ✓ |
| R3-R. Hız sınırı dosya sistemi hatası (güvenli hata) |   sunucu günlüğüne kayıt: "hız sınırı hatası: sayaç klasörü oluşturulamadı" | ✓ |
| R3-R. Hız sınırı dosya sistemi hatası (güvenli hata) | sayaç dosyası açılamadı → istek REDDEDİLİR: JSON 503 SERVICE_UNAVAILABLE + Retry-After (sınırlama sessizce kapanmaz) | ✓ |
| R3-R. Hız sınırı dosya sistemi hatası (güvenli hata) |   aynı durumda POST /assistant da 503 | ✓ |
| R3-R. Hız sınırı dosya sistemi hatası (güvenli hata) |   sunucu günlüğüne kayıt: "hız sınırı hatası: sayaç dosyası açılamadı" | ✓ |
| R3-R. Hız sınırı dosya sistemi hatası (güvenli hata) | normal ortamda (yazılabilir geçici klasör) aynı istek 200 | ✓ |
| R3-Z. Temizlik (yalnızca bu çalıştırmanın oluşturdukları) | test veritabanı silindi (mk_api_test_44fdb70eb0d3) | ✓ |
| R3-Z. Temizlik (yalnızca bu çalıştırmanın oluşturdukları) | test kullanıcısı silindi (mk_ro_44fdb70e) | ✓ |
| R3-Z. Temizlik (yalnızca bu çalıştırmanın oluşturdukları) | geçici klasör silindi | ✓ |
| R3-Z. Temizlik (yalnızca bu çalıştırmanın oluşturdukları) | sorgu günlüğü ayarları eski haline döndü (general_log=0, log_output=TABLE) | ✓ |
| R3-Z. Temizlik (yalnızca bu çalıştırmanın oluşturdukları) | başka veritabanına/kullanıcıya dokunulmadı (betik başındaki liste ile aynı) | ✓ |

### Önceki testler (revizyon 2'den korunan)

| Grup | Kontrol | Sonuç |
|---|---|---|
| A. Kurulum | veritabanı sunucusu yerel: localhost | ✓ |
| A. Kurulum | benzersiz adlar: veritabanı mk_api_test_44fdb70eb0d3 · kullanıcı mk_ro_44fdb70e · klasör mk_api_test_44fdb70eb0d3 | ✓ |
| A. Kurulum | test veritabanı oluşturuldu + işaret tablosu (silme güvencesi) | ✓ |
| A. Kurulum | kullanıcının şema dökümü yüklendi (yalnızca yapı) | ✓ |
| A. Kurulum | yalnızca SELECT yetkili, bu çalıştırmaya özel test kullanıcısı (mk_ro_44fdb70e) | ✓ |
| A. Kurulum | test sunucuları başladı (11 örnek: eski site, yeni site+API, hız sınırı, Cloudflare, SELECT-yetkili, HTTPS, 3 hata enjeksiyonu, 2 dosya sistemi hatası) | ✓ |
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
| B2. ara.php: eşitlikte i.id (tekrarlanabilirlik, içerik korunumu, sayfalama) |   karşılaştırma: düzeltme öncesi 3 sorguda sayfalar arası tekrar/kayıp görüldü (giderilen hata) | ✓ |
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
