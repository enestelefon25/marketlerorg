# Marketler.org Mobil API v1: Sunucu Dosyaları (FAZ 1)

> **Durum:** Yalnızca depoda. **Canlı sunucuya yüklenmedi.** Yükleme ancak ayrı bir değişiklik listesi onayından sonra yapılır.
> API **salt okunurdur**: veritabanına yazmaz, şema değiştirmez, yönetici işlemi sunmaz.

## Klasör yapısı

`site/` klasörünün içeriği, sitenin kök klasörüne (`public_html/marketler.org/`) **aynı yollarla** kopyalanmak üzere hazırlanmıştır.

| Dosya | Amaç |
|---|---|
| `site/ortak-veri.php` | **Yeni.** `mt_featured()` fonksiyonu ve `MT_MIN_GAIN` sabiti, `tema-modern-ana.php` içinden **karakteri karakterine aynı** taşındı. Hem site hem API kullanır. Çıktı üretmez, veritabanına yazmaz. |
| `site/tema-modern-ana.php` | **Değişen** site dosyası. Tek fark: taşınan blok yerine tek satır `require_once __DIR__.'/ortak-veri.php';`. Testle kanıtlandı: orijinal = yeni − bu satır + blok. |
| `site/api/.htaccess` | `/api/v1/...` isteklerini `v1/index.php` dosyasına yönlendirir. HTTPS zorunluluğu, dizin listeleme kapalı, `index.php` dışındaki PHP dosyalarına doğrudan erişim kapalı, güvenlik başlıkları. |
| `site/api/v1/index.php` | Tek giriş noktası: yol çözümleme, HTTP yöntemi denetimi (405), bilinmeyen yol (404), hız sınırı, uç nokta dosyasını yükleme. |
| `site/api/v1/config.php` | API ayarları: sürüm, hız sınırları (istek/dk/IP), yerel geliştirme için HTTP izni (**canlıda `false`**). Gizli bilgi içermez. |
| `site/api/v1/lib/bootstrap.php` | **Salt okunurluk güvencesi.** `config.php` dosyasını çalıştırmadan okur. Ayrı, kilitli (`READ ONLY`) bir bağlantıyla `schema_v` değerini denetler: `'5'` değilse `inc.php`'yi **hiç yüklemez** ve 503 döner (şema güncellemesi API üzerinden asla tetiklenmez). Sonra `inc.php`'yi değiştirmeden yükler ve `db()` bağlantısını da `READ ONLY` ile kilitler. Site URL fonksiyonlarının doğru adres üretmesi için `SCRIPT_NAME` değerini ayarlar. |
| `site/api/v1/lib/response.php` | `{success, data, meta}` zarfı, standart hata kodları, `ETag` / `304`, `Cache-Control`. Beklenmeyen PHP çıktısını yanıta karıştırmaz. Hata ayrıntısını kullanıcıya göstermez. |
| `site/api/v1/lib/input.php` | Parametre doğrulama (uzunluk, kalıp, sayfa/limit, UTF-8). Hatalı girdi `400 INVALID_PARAMETER`. |
| `site/api/v1/lib/ratelimit.php` | Dosya tabanlı hız sınırı (yönetici girişindeki yöntemin aynısı), `429` + `Retry-After`. |
| `site/api/v1/lib/map.php` | Veritabanı satırlarını belgelenen JSON nesnelerine (Market, CatalogSummary, Item, CompareGroup) çevirir. Hesaplamaların tümü sitedeki fonksiyonlarla aynıdır. Yalnızca sunucuda gerçekten bulunan görseller döner. |
| `site/api/v1/lib/weblogic.php` | Kullanıcı kararı "A": `asistan.php` ve `sayfa.php` içinde fonksiyon olarak değil, sayfa içinde yazılı olan mantığın **birebir kopyaları**. Her kopyanın kaynak satırı belirtilmiştir; testler kopyaların bozulmadığını denetler. |
| `site/api/v1/endpoints/*.php` | 14 uç nokta (ayrıntı: `docs/FAZ1-UC-NOKTALAR.md`): `home`, `markets`, `market`, `catalogs`, `catalog`, `categories`, `products`, `product`, `search`, `suggest`, `compare`, `similar`, `assistant`, `pages`. |
| `tests/run.php` | Tek komutla bütün testler (B–H aşamaları). Orijinal site kopyasını geçici klasöre kopyalar; kaynağa **yazmaz**. |
| `tests/fixtures/schema.sql` | Kullanıcının gönderdiği **yalnızca yapı** dökümü (veri yok). |
| `tests/fixtures/seed.php` | **Sentetik** test verisi: uydurma markalar, gerçek fiyat veya kişisel veri yok. |
| `tests/router.php` | Yalnızca yerel `php -S` sunucusu için `.htaccess` karşılığı. Canlıya yüklenmez. |
| `tests/readonly_probe.php` | API önyüklemesinden sonra bir yazma dener; veritabanının reddettiğini doğrular. |

## Dokunulmayan dosyalar
`inc.php`, `eslestirme.php`, `asistan-motor.php`, `asistan.php`, `sayfa.php`, `ara.php`, `cron.php`, `admin.php`, `admin/*`, `.htaccess` ve veritabanı şeması **değiştirilmedi**.

## Testleri çalıştırma (yerel)

```bash
# Gerekenler: PHP CLI (curl, gd, pdo_mysql, mbstring), yerel MariaDB/MySQL
php MarketlerNative/server/tests/run.php --site=/yol/marketler.org --rapor=rapor.md
# Veritabanı bağlantısı: MK_TEST_DB_HOST, MK_TEST_DB_USER, MK_TEST_DB_PASS (varsayılan localhost/root/parolasız)
```

`--site` olarak **canlı sunucu değil**, sitenin yerel bir kopyası verilir. Betik `mk_api_test` adlı test veritabanını her çalıştırmada siler ve yeniden kurar.

## Canlıya alma (henüz YAPILMADI; ayrıca onay gerekir)
1. Yedek: `tema-modern-ana.php` dosyasının mevcut hali.
2. Yüklenecekler: `ortak-veri.php` (yeni), `tema-modern-ana.php` (değişen), `api/` klasörü (yeni).
3. `api/v1/config.php` içinde `MK_API_ALLOW_HTTP = false` olduğundan emin olun.
4. **Geri alma:**
   - API için `api/` klasörünü silmek veya yeniden adlandırmak yeterli.
   - Ana sayfa için yedeklenen `tema-modern-ana.php` geri yüklenir (`ortak-veri.php` kalsa da zararsızdır).
   - Veritabanı değişmediği için geri alınacak bir şey yoktur.
