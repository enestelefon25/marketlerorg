# Marketler.org Mobil API v1: Sunucu Dosyaları (FAZ 1)

> **Durum:** Yalnızca depoda. **Canlı sunucuya yüklenmedi.** Yükleme ancak ayrı bir değişiklik listesi onayından sonra yapılır.
> API **salt okunurdur**: veritabanına yazmaz, şema değiştirmez, yönetici işlemi sunmaz.

## Klasör yapısı

`site/` klasörünün içeriği, sitenin kök klasörüne (`public_html/marketler.org/`) **aynı yollarla** kopyalanmak üzere hazırlanmıştır.

| Dosya | Amaç |
|---|---|
| `site/ortak-veri.php` | **Yeni.** `mt_featured()` fonksiyonu ve `MT_MIN_GAIN` sabiti, `tema-modern-ana.php` içinden **karakteri karakterine aynı** taşındı. Hem site hem API kullanır. Çıktı üretmez, veritabanına yazmaz. |
| `site/tema-modern-ana.php` | **Değişen** site dosyası. Tek fark: taşınan blok yerine tek satır `require_once __DIR__.'/ortak-veri.php';`. Testle kanıtlandı: orijinal = yeni − bu satır + blok. |
| `site/ara.php` | **Değişen** site dosyası (onaylı düzeltme). Tek fark 15. satır: mevcut sıralama ölçütlerinin sonuna eşitlik durumunda `,i.id ASC` eklendi. Filtreler, `WHERE` ve sayfalama kodu aynı. Aynı sorgu artık her seferinde aynı sırayı verir; sayfalar arasında tekrar ve kayıp olmaz. |
| `site/api/.htaccess` | `/api/v1/...` isteklerini `v1/index.php` dosyasına yönlendirir. HTTPS zorunluluğu, dizin listeleme kapalı, `index.php` dışındaki PHP dosyalarına doğrudan erişim kapalı, güvenlik başlıkları. |
| `site/api/v1/index.php` | Tek giriş noktası: HTTPS denetimi (`mk_is_https`: doğrudan HTTPS ya da yalnızca güvenilir vekil başlığı), yol çözümleme, HTTP yöntemi denetimi (405), bilinmeyen yol (404), hız sınırı, uç nokta dosyasını yükleme, yanıtsız sonlanmada JSON hata (`mk_shutdown`). |
| `site/api/v1/config.php` | API ayarları: sürüm, hız sınırları (istek/dk/ziyaretçi IP'si), yerel geliştirme için HTTP izni (**canlıda `false`**), güvenilir vekil (Cloudflare) IP aralıkları. Gizli bilgi içermez. **Cloudflare listesi canlıya almadan önce https://www.cloudflare.com/ips/ ile karşılaştırılmalıdır.** |
| `site/api/v1/lib/bootstrap.php` | **Salt okunurluk güvencesi.** `config.php` dosyasını çalıştırmadan okur. Ayrı, kilitli (`READ ONLY`) bir bağlantıyla `schema_v` değerini denetler: `'5'` değilse `inc.php`'yi **hiç yüklemez** ve 503 döner (şema güncellemesi API üzerinden asla tetiklenmez). Sonra `inc.php`'yi değiştirmeden yükler ve `db()` bağlantısını da `READ ONLY` ile kilitler. Site URL fonksiyonlarının doğru adres üretmesi için `SCRIPT_NAME` değerini ayarlar. |
| `site/api/v1/lib/response.php` | `{success, data, meta}` zarfı (`meta.api_version` her yanıtta), standart hata kodları, `ETag` / `304`, `Cache-Control`. Ölümcül hata veya site kodunun düz metin basıp çıkması durumunda bile yanıt JSON'dur. Hata ayrıntısını kullanıcıya göstermez. |
| `site/api/v1/lib/input.php` | Parametre doğrulama (uzunluk, kalıp, sayfa/limit, UTF-8). Hatalı girdi `400 INVALID_PARAMETER`. |
| `site/api/v1/lib/proxy.php` | **Revizyon 3.** Güvenilir vekil (Cloudflare) doğrulaması: `mk_ip_in_cidr` (IPv4/IPv6), `mk_from_trusted_proxy`, `mk_client_ip` (gerçek ziyaretçi IP'si), `mk_is_https`. `CF-Connecting-IP`, `X-Forwarded-Proto` ve `CF-Visitor` başlıkları YALNIZCA `REMOTE_ADDR` güvenilir aralıktaysa dikkate alınır. |
| `site/api/v1/lib/ratelimit.php` | Dosya tabanlı hız sınırı, `429` + `Retry-After`. **Güvenli hata:** sayaç klasörü veya dosyası oluşturulamaz, açılamaz, kilitlenemez ya da yazılamazsa istek `503` ile reddedilir ve sunucu günlüğüne `MARKETLER API hız sınırı hatası: …` yazılır; sınırlama sessizce devre dışı kalmaz. |
| `site/api/v1/lib/map.php` | Veritabanı satırlarını belgelenen JSON nesnelerine (Market, CatalogSummary, Item, CompareGroup) çevirir. Hesaplamaların tümü sitedeki fonksiyonlarla aynıdır. Yalnızca sunucuda gerçekten bulunan görseller döner. |
| `site/api/v1/lib/weblogic.php` | Kullanıcı kararı "A": `asistan.php` ve `sayfa.php` içinde fonksiyon olarak değil, sayfa içinde yazılı olan mantığın **birebir kopyaları**. Her kopyanın kaynak satırı belirtilmiştir; testler kopyaların bozulmadığını denetler. |
| `site/api/v1/endpoints/*.php` | 14 uç nokta (ayrıntı: `docs/FAZ1-UC-NOKTALAR.md`): `home`, `markets`, `market`, `catalogs`, `catalog`, `categories`, `products`, `product`, `search`, `suggest`, `compare`, `similar`, `assistant`, `pages`. |
| `tests/run.php` | Tek komutla bütün testler. Orijinal site kopyasını geçici klasöre kopyalar; kaynağa **yazmaz**. Güvenlik kuralları için aşağıdaki "Test betiği güvenliği" bölümüne bakın. |
| `tests/guard.php` | **Revizyon 3.** Test betiğinin güvenlik korumaları (yerel sunucu, benzersiz adlar, silme güvencesi); R3-S grubunda birim testine tabidir. |
| `tests/fixtures/schema.sql` | Kullanıcının gönderdiği **yalnızca yapı** dökümü (veri yok). |
| `tests/fixtures/seed.php` | **Sentetik** test verisi: uydurma markalar, gerçek fiyat veya kişisel veri yok. |
| `tests/router.php` | Yalnızca yerel `php -S` sunucusu için `.htaccess` karşılığı. Canlıya yüklenmez. |
| `tests/readonly_probe.php` | API önyüklemesinden sonra bir yazma dener; veritabanının reddettiğini doğrular. |

## Dokunulmayan dosyalar
`inc.php`, `eslestirme.php`, `asistan-motor.php`, `asistan.php`, `sayfa.php`, `cron.php`, `admin.php`, `admin/*`, `.htaccess` ve veritabanı şeması **değiştirilmedi**.

## Testleri çalıştırma: YALNIZCA İZOLE YEREL TEST ORTAMI

> ⚠ `tests/run.php` **yalnızca izole bir yerel test ortamında** çalıştırılmalıdır: geliştirici makinesi, kapsayıcı (container) veya bu amaçla kurulmuş bir sanal makine. Canlı sunucuda, hosting hesabında, paylaşılan veya üretim veritabanı sunucusunda **çalıştırmayın**. Betik test sırasında veritabanı ve kullanıcı oluşturur, sorgu günlüğünü geçici olarak açar.

```bash
# Gerekenler: PHP CLI (curl, gd, pdo_mysql, mbstring), YEREL MariaDB/MySQL
php MarketlerNative/server/tests/run.php --site=/yol/marketler.org --rapor=rapor.md
```

### Test betiği güvenliği (`tests/guard.php`, revizyon 3)
- **Yalnızca yerel sunucu:** `MK_TEST_DB_HOST` yalnızca `localhost`, `127.0.0.1` veya `::1` olabilir. Başka her değerde betik **hiçbir bağlantı kurmadan** çıkış kodu 3 ile durur; geçersiz kılma seçeneği yoktur.
- **Benzersiz kaynaklar:** Her çalıştırma `mk_api_test_<12 hex>` veritabanı, `mk_ro_<8 hex>` SELECT-yetkili kullanıcı ve `/tmp/mk_api_test_<belirteç>` geçici klasörü kullanır. Aynı adda veritabanı veya kullanıcı zaten varsa betik **durur**.
- **Var olan hiçbir kullanıcının yetkisine dokunulmaz.** `REVOKE` yoktur; kullanıcı `IF NOT EXISTS` olmadan oluşturulur.
- **Silme güvencesi:** Veritabanı yalnızca adı kalıba uyuyorsa, bu çalıştırma oluşturduysa ve içindeki işaret tablosu (`_mk_test_isareti`) bu çalıştırmanın belirtecini taşıyorsa silinir. Klasör yalnızca sistem geçici klasörü altındaki belirteçli klasörse silinir.
- **Sorgu günlüğü silinmez** (`TRUNCATE` yok). Yalnızca bu çalıştırmanın test veritabanına bağlanan oturumlar süzülür; `general_log` ve `log_output` ayarları sonunda eski haline döner.
- **Önbellek ve sayaçlar** yalnızca bu çalıştırmanın geçici klasöründe tutulur (`TMPDIR`); sistemin geçici klasöründeki başka dosyalara dokunulmaz.
- **Temizlik:** Test sonunda (hata olsa da) oluşturulan veritabanı, kullanıcı ve klasör silinir. R3-Z grubu bunu doğrular.
- `MK_TEST_DB_USER` / `MK_TEST_DB_PASS`: yerel sunucuda veritabanı ve kullanıcı oluşturabilen bir hesap (varsayılan `root`, unix soketi).

## Canlıya alma (henüz YAPILMADI; ayrıca onay gerekir)
1. Yedek: `tema-modern-ana.php` ve `ara.php` dosyalarının mevcut halleri.
2. Yüklenecekler: `ortak-veri.php` (yeni), `tema-modern-ana.php` (değişen), `ara.php` (değişen), `api/` klasörü (yeni).
3. `api/v1/config.php` içinde `MK_API_ALLOW_HTTP = false` olduğundan emin olun.
4. **Geri alma:**
   - API için `api/` klasörünü silmek veya yeniden adlandırmak yeterli.
   - Ana sayfa ve arama için yedeklenen `tema-modern-ana.php` ve `ara.php` geri yüklenir (`ortak-veri.php` kalsa da zararsızdır).
   - Veritabanı değişmediği için geri alınacak bir şey yoktur.
