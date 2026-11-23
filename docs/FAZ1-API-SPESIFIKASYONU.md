# Marketler.org API v1: Uç Nokta ve Veri Şeması Spesifikasyonu (FAZ 1 öncesi, onay taslağı)

**Durum:** Yalnızca tasarım. **Kod yazılmadı, canlı sistemde değişiklik yapılmadı.**
**Dayanak:** FAZ 0 incelemesi (gönderilen 40 dosya + şema dökümü) ve kullanıcının FAZ 0 kararları.
**Temel ilke:** API **yalnızca okur**. Her alan mevcut bir PHP fonksiyonundan ya da sitenin kullandığı sorgudan gelir. Uygulama hiçbir değeri kendisi hesaplamaz: fiyat farkı, indirim ve birim fiyat sunucudan gelir.

---

## A. Genel kurallar

### A.1 Adres, yöntem, biçim
- Temel adres: `https://marketler.org/api/v1/`. Yalnızca HTTPS; HTTP isteği 301 ile HTTPS'e yönlendirilir (mevcut `.htaccess` kuralı).
- Yöntemler:
  - `GET`: 15 uç nokta
  - `POST`: yalnızca `/assistant`
  - Diğer yöntemler `405` döner.
- Yanıt: `Content-Type: application/json; charset=utf-8`, UTF-8, `JSON_UNESCAPED_UNICODE`.
- İstek başlığı (isteğe bağlı): `X-App-Version: 1.0.0`. Yalnızca günlükte kullanılır; yetki için kullanılmaz.

### A.2 Ortak yanıt zarfı

```json
{ "success": true, "data": { }, "meta": { "generated_at": "2026-11-23T22:10:05+03:00", "page": 1, "limit": 24, "total": 135 } }
```
- `page`, `limit` ve `total` yalnızca sayfalı uç noktalarda bulunur.
- `generated_at`: Sunucunun yanıtı ürettiği an (Europe/Istanbul). Uygulama "son güncelleme" bilgisini buradan gösterir.

```json
{ "success": false, "error": { "code": "NOT_FOUND", "message": "Ürün bulunamadı." }, "meta": { "generated_at": "..." } }
```

### A.3 Hata kodları

| HTTP | `code` | Ne zaman | Mesaj (sabit, Türkçe) |
|---|---|---|---|
| 400 | `INVALID_PARAMETER` | Parametre biçimi veya sınırı hatalı | "Geçersiz parametre: {alan}." |
| 404 | `NOT_FOUND` | Kayıt yok veya gizli (`hidden≠0`) | "{Kaynak} bulunamadı." |
| 404 | `ENDPOINT_NOT_FOUND` | Tanımsız yol | "İstenen adres bulunamadı." |
| 405 | `METHOD_NOT_ALLOWED` | Yanlış yöntem | "Bu yöntem desteklenmiyor." |
| 413 | `PAYLOAD_TOO_LARGE` | `/assistant` gövdesi 2 KB'den büyük | "İstek çok büyük." |
| 429 | `RATE_LIMITED` | Hız sınırı aşıldı (`Retry-After` başlığıyla) | "Çok fazla istek. Lütfen biraz bekleyin." |
| 503 | `SERVICE_UNAVAILABLE` | Veritabanına ulaşılamıyor | "Hizmet geçici olarak kullanılamıyor." |
| 500 | `INTERNAL_ERROR` | Beklenmeyen hata (ayrıntı yalnızca sunucu günlüğünde) | "Beklenmeyen bir hata oluştu." |

Gizli bir katalog veya ürün istendiğinde, varlığını sızdırmamak için `404` döner (`403` değil).

### A.4 Veri biçimleri
- **Para:** Veritabanındaki `DECIMAL(10,2)` değeri, kayan nokta hatası olmasın diye **metin** olarak döner: `"price": "4599.00"`. Yanına sitedeki biçimin aynısı (`tl()`) eklenir: `"price_text": "4.599,00 TL"`. Android `BigDecimal` kullanır.
- **Tarih:** `YYYY-MM-DD` (`cdate`, hesaplanan `cend`). Zaman damgası ISO 8601 ve `+03:00`.
- **Görsel URL'leri:** Mutlak HTTPS adresi. Yalnızca sunucudaki yerel `img/` dosyaları döner (G4 kararı, bkz. E.1). Dosya yoksa alan `null` olur.
- **Boş değer:** Olmayan bilgi `null` olur. Uydurma veya varsayılan değer verilmez (ör. `old_price: null`).

### A.5 Sayfalama
- `page` (≥1, varsayılan 1) ve `limit` (her uç noktanın üst sınırı tabloda). Geçersizse `400`.
- `meta.total` toplam kayıt sayısıdır. Uygulama "daha fazla" durumunu `page*limit < total` ile anlar.

### A.6 Önbellek başlıkları ve hız sınırı

| Sınıf | Uç noktalar | `Cache-Control` | Hız sınırı (IP başına) |
|---|---|---|---|
| Statik | `/categories`, `/pages/*`, `/markets` | `public, max-age=3600` | 60/dk |
| Katalog | `/catalogs*`, `/markets/{slug}` | `public, max-age=900` | 60/dk |
| Ürün / ana sayfa | `/home`, `/products*`, `/compare*`, `/meta` | `public, max-age=300` | 60/dk |
| Ağır | `/search`, `/assistant` | `no-store` | 20/dk |
| Anlık öneri | `/suggest` | `private, max-age=60` (sitedeki gibi) | 120/dk |

- Hız sınırı dosya tabanlıdır (yönetici girişindeki yöntemle aynı): `sys_get_temp_dir()` altında IP özetinden oluşan bir sayaç dosyası.
- Bütün GET yanıtlarında `ETag` başlığı bulunur. `If-None-Match` ile gelen istekte veri değişmemişse `304` döner (mobil veriden tasarruf).

---

## B. Ortak veri nesneleri

### B.1 `Market`
| Alan | Tür | Kaynak |
|---|---|---|
| `id` | int | `stores.id` |
| `slug` | string | `stores.slug` |
| `name` | string | `stores.name` |
| `color` | string (`#RRGGBB`) | `stores.color` |
| `logo_url` | string\|null | `setting('logo_<slug>')`, mutlak URL |
| `live_count` | int | `by_status()` → aktif katalog sayısı (ana sayfadaki ile aynı) |
| `soon_count` | int | `by_status()` → yaklaşan katalog sayısı |

### B.2 `CatalogSummary`
| Alan | Tür | Kaynak |
|---|---|---|
| `id`, `slug`, `title`, `label` | | `catalogs.*` |
| `market` | `{id, slug, name, color}` | `CSEL` → `s.*` |
| `start_date` | date | `catalogs.cdate` |
| `end_date` | date | `CEND` (hesaplanan; tabloda yok) |
| `status` | `live`\|`soon`\|`past` | `cstatus()` |
| `status_text` | string | `STL` (`Aktif`, `Yaklaşan`, `Süresi dolmuş`) |
| `date_text` | string | `ccard()` kuralı: yaklaşan ise "Başlangıç: 24 Kas 2026", değilse `range_label()` |
| `item_count` | int | `CSEL` → `n` |
| `cover` | `{url, full_url}`\|null | `images[0]` → `url` = `img_small(...,'c')` (480 px), `full_url` = asıl dosya |
| `web_url` | string | `u_kat()` mantığıyla mutlak adres |

### B.3 `Item` (ürün = katalog kalemi)
| Alan | Tür | Kaynak / kural |
|---|---|---|
| `id` | int | `items.id` |
| `fav_key` | string | `"{store_id}:{hash}"` (bkz. bölüm D) |
| `hash` | string(32) | `items.hash` |
| `name` | string | `items.name` |
| `category` | `{slug, name}` | `categories` (`PSEL` → `cn`) |
| `market` | `{id, slug, name, color}` | `PSEL` → `sn`, `ss`, `color` |
| `price`, `price_text` | string | `items.price`, `tl()` |
| `old_price`, `old_price_text` | string\|null | Yalnızca `old_price > price` ise; değilse `null` |
| `discount_pct` | int\|null | `pcard()` formülü: `round(100 - price/old_price*100)`; yalnızca `old_price > price` ise |
| `unit_price` | `{value, unit, text}`\|null | `as_uprice()`; `unit` = `kg`\|`L`; `text` = "≈ 112,45 TL/kg" (`as_uline` biçimi) |
| `image` | `{url}`\|null | `item_media` (`status=1`, `img/u/...`, dosya mevcut) = `item_img()` kuralı; mutlak URL ve `?v=` sürümü |
| `catalog` | `{id, slug, title, start_date, end_date, status, status_text}` | `PSEL` |
| `campaign_text` | string | `range_label(cdate, cend)` |
| `web_url` | string | `u_kat(slug).'#viewer'` (mutlak) |

> `id`'nin sorguda geldiği satırlarda `PSEL` satırı + `item_img(id)` + `as_uprice()` sitedeki `pcard()` / `mt_card()` verisinin aynısını verir.

### B.4 `CompareGroup`
| Alan | Tür | Kaynak |
|---|---|---|
| `group_id` | string | Gruptaki en küçük `items.id` (aynı veride kararlı; elle grupta `grp` yerine yine en küçük id) |
| `title` | string | `$g[0]['name']`: grup fiyata göre sıralı olduğundan en ucuz ürünün adı (sitedeki gibi) |
| `is_manual` | bool | Gruptaki satırlardan birinde `manual=1` (`product_links`) |
| `rows` | `CompareRow[]` | Fiyata göre artan (`cmp_groups` sırası korunur) |
| `min_price`, `max_price` | string | `min`/`max(array_column($g,'price'))` |
| `diff`, `diff_text` | string | `max - min`, `tl()` |
| `diff_pct` | int | `round((max-min)/max*100)` (`fiyat-karsilastirma.php` formülü) |
| `saving_text` | string\|null | "En ucuz markette 400,00 TL (%8) daha az." Yalnızca `max > min` ise |

`CompareRow` = `Item` + `is_best: bool` (`price == min`, sitedeki `==` karşılaştırmasıyla aynı).

### B.5 `SimilarGroup` (benzer ürünlerde birim fiyat; **farklı ürünler**)
| Alan | Tür | Kaynak |
|---|---|---|
| `key` | string | `mk_similar_groups()` → `key` |
| `label` | string | `mk_type_label(key)` (ör. "Toz deterjan") |
| `unit` | `kg`\|`L` | `k` |
| `rows` | `(Item + is_best)[]` | Birim fiyata göre artan; ilk satır `is_best=true` |
| `notice` | string | Sitedeki uyarı metni: "Bu bölümdeki ürünler aynı ürün değildir…" |

---

## C. Uç noktalar (16)

Her uç noktada, sitede karşılığı olan sayfa ile aynı veriyi verdiği FAZ 2'de test edilecek ("Web eşdeğeri" sütunu).

### 1. `GET /meta`
- **Amaç:** Uygulama açılışında sürüm ve veri tazeliği bilgisi.
- **data:** `api_version` ("1.0"), `server_time`, `data_updated_at` (`setting('src_run')`: son cron çalışması, yoksa `null`), `min_app_version` ("1.0.0"; zorunlu güncelleme için ileride kullanılır), `ttl` (önerilen önbellek süreleri, saniye: `home` 900, `product` 1800, `markets` 21600, `catalogs` 3600).
- **Mevcut fonksiyon:** `setting()`; **yalnızca `src_run` anahtarı okunur.**

### 2. `GET /home`
- **Web eşdeğeri:** `tema-modern-ana.php` (modern ana sayfa).
- **data:**
  - `markets: Market[]`
  - `featured: { title, subtitle, items: (Item + note)[] }`. `title` ve `subtitle` sitedeki gibi ("Öne çıkan fırsatlar" / "Uygun Fiyatlı Ürünler"). `note` string, string dizisi ya da `null` (ör. ["%12 daha uygun", "7 emsal ürünün medyan birim fiyatına göre"]).
  - `live_catalogs: CatalogSummary[]` (ilk 6, başlangıç tarihine göre yeniden eskiye)
  - `soon_catalogs: CatalogSummary[]` (ilk 4, yakından uzağa)
  - `top_compare: CompareGroup|null`
  - `category_shortcuts: [{slug, name}]` (modern sayfadaki 8 kısayol, aynı sırayla)
- **Mevcut fonksiyonlar:** `CSEL` + `by_status()`, **`mt_featured()`** (taşınacak, bkz. bölüm E.2), `cmp_groups(1)`.
- **Not:** `mt_featured()` sitedeki 10 dakikalık önbellek dosyasını paylaşır. Aynı anda açılan site ve uygulama aynı öne çıkanları görür.

### 3. `GET /markets`
- **Web eşdeğeri:** Ana sayfadaki "Marketler" bölümü.
- **data:** `Market[]` (`stores` tablosundaki sırayla).

### 4. `GET /markets/{slug}`
- **Web eşdeğeri:** `index.php?m={slug}`.
- **Parametre:** `slug` = `[a-z0-9]{1,30}` (site `preg_replace('/[^a-z0-9]/','')` uyguluyor).
- **data:** `market: Market`, `catalogs: { live: CatalogSummary[], soon: [...], past: [...] }`. Sorgu `CSEL AND s.id=? ORDER BY c.cdate DESC, c.id DESC LIMIT 120`, gruplama ve sıralama `by_status()` ile (sitedekiyle aynı).
- **Hata:** Market yoksa `404 NOT_FOUND` ("Market bulunamadı.").

### 5. `GET /catalogs`
- **Web eşdeğeri:** `kataloglar.php`.
- **Parametreler:** `market` (slug, isteğe bağlı), `status` = `live|soon|past|all` (varsayılan `all`), `page`, `limit` (≤50, varsayılan 20).
- **data:** `status=all` ise `{ live: [...], soon: [...], past: [...] }` ve sayfalama yalnızca `past` üzerinde (arşiv uzun). Diğer durumlarda `CatalogSummary[]`.
- **Mevcut:** `CSEL (+ AND s.slug=?) ORDER BY c.cdate DESC, c.id DESC LIMIT 300` + `by_status()` (site sınırı 300; aynı kalır).

### 6. `GET /catalogs/{slug}`
- **Web eşdeğeri:** `katalog.php?s={slug}`.
- **Parametre:** `slug` = `[a-z0-9-]{1,190}`.
- **data:**
  - `catalog: CatalogSummary`
  - `pages: [{index, url, thumb_url}]`. `url` asıl görsel (yüksek kalite), `thumb_url` = `img_small(...,'t')` (180 px). **Yalnızca yerel `img/k/` dosyaları.**
  - `price_min`, `price_max`
  - `groups: [{category: {slug, name}, items: Item[]}]`. Sıra `ORDER BY FIELD(k.slug, KORDER), i.name` (sitedekiyle aynı).
  - `other_catalogs: CatalogSummary[]` (aynı marketin 4 kataloğu)
- **Kural:** Yalnızca `hidden=0`. Yönetici önizlemesi API'de **yok**.
- **Hata:** `404` ("Katalog bulunamadı.").

### 7. `GET /categories`
- **data:** `[{slug, name, order, live_item_count}]`. Sıra `KORDER`. `live_item_count` = aktif kataloglardaki ürün sayısı (tek `COUNT … GROUP BY` sorgusu).

### 8. `GET /products`
- **Web eşdeğeri:** `ara.php` (filtreli arama; **akıllı asistan değil**).
- **Parametreler** (sitedeki adlar korunarak, okunaklı karşılıklarıyla):

| Parametre | Site | Doğrulama | Varsayılan |
|---|---|---|---|
| `q` | `q` | ≤120 karakter; kelimelere bölünür, her kelime `i.name LIKE %kelime%` | – |
| `market` | `m` | `[a-z0-9]` | – |
| `category` | `k` | `[a-z0-9-]` | – |
| `min`, `max` | `min`, `max` | `pnum()` ile ayrıştırılır, ≥0 | – |
| `status` | `st` | `aktif\|yaklasan\|dolmus\|hepsi` | `aktif` |
| `date` | `d` | `YYYY-MM-DD` (verilirse `status` yerine geçer) | – |
| `sort` | `o` | `new\|asc\|desc` (`new` = sitedeki boş değer) | `new` |
| `page`, `limit` | `off` | limit ≤50 | 1, 24 |
| `fav` | – | En çok 50 adet `storeId:hash`, virgülle ayrılmış | – |

- **data:** `Item[]`, `meta.total`.
- **Mevcut:** `ara.php` WHERE kurgusu ve `PSEL`, sıralama beyaz listesi (`i.price ASC/DESC`, varsayılan `c.cdate DESC, i.name ASC`).
- **`fav` modu** (favoriler ekranı için): Her anahtar için o market + hash ile eşleşen, **görünür kataloglardaki** kalemler bulunur. Her anahtar için tek kalem döner. Öncelik sırası: önce `live`, sonra `soon`, sonra `past`; aynı durumda en yeni `cdate`. Yanıtta `fav_key` alanıyla eşleştirilir. Bulunamayan anahtar `data.missing: string[]` içinde döner.
- **Not (G5):** Site `%` ve `_` karakterlerini kaçışlamıyor. **Web ile aynı sonucu vermek için API de kaçışlamayacak.** Bu karakterler SQL enjeksiyonu riski taşımaz; yalnızca joker karakter olarak çalışır. Kaçışlama istenirse site ile birlikte değiştirilmelidir.

### 9. `GET /products/{id}`
- **Parametre:** `id` = pozitif tam sayı.
- **data:**
  - `item: Item`
  - `history: {prev_price, prev_price_text, prev_date, change_pct, is_lowest, is_highest, other_count, text}|null`. Kaynak `as_histrow()`; `text` = sitedeki `as_hline()` cümlesi (ör. "Önceki katalogdan (17 Kas, 54,90 TL) %9 daha ucuz").
  - `compare: CompareGroup|null` (ürün bir kesin eşleşme grubundaysa)
  - `catalog: CatalogSummary`
- **Mevcut:** `PSEL AND i.id=?`, `item_img`, `as_uprice`, `as_hist` + `as_histrow`, `cmp_groups()`.
- **Performans:** Sitede `as_hist()` tüm ürünleri okuyor. API bu haritayı ürün sayısına bağlı bir anahtarla 10 dakika dosyada önbelleğe alır (yalnız okuma; sonuç aynı).
- **Kural:** Kalemin kataloğu `hidden=0` değilse `404` ("Ürün bulunamadı.").

### 10. `GET /search`
- **Web eşdeğeri:** `asistan.php?q=…` (akıllı arama sayfası).
- **Parametreler:** `q` (zorunlu, 1–120), `all` (`0|1`, süresi dolmuşları da ara), `page`, `limit` (≤50, varsayılan 24).
- **data:**
  - `understood: {market, category, terms[], qty[], min, max, sort, discounted}` (`as_parse()`)
  - `chips: string[]`: sitedeki "Anladığım: …" parçaları, aynı metinlerle
  - `match`: `full`\|`partial`\|`none`
  - `items: (Item + history_text)[]`
  - `note`: string\|null. Aktif kataloglarda sonuç yoksa arşivde aranır ve sitedeki not metni döner.
  - `did_you_mean`: string\|null (`fixq`)
  - `similar_words`: string[] (`fix`)
  - `by_unit`: `kg`\|`L`\|null
- **Mevcut:** `as_index`, `as_search`, `as_hist`, `as_histrow`. Arşive düşme mantığı `asistan.php` ile birebir aynıdır.

### 11. `GET /suggest`
- **Web eşdeğeri:** `asistan.php?oneri=1&q=…` (arama kutusu önerisi).
- **Parametre:** `q` (2–120 karakter; daha kısaysa boş liste döner, hata vermez).
- **data:** `[{name, price_text, market_name, catalog_slug, web_url}]` (en çok 5).
- **Mevcut:** Sitedeki havuz önbelleği dosyası (`mk_oneri_*.json`, 120 sn) **ortak kullanılır**. Puanlama sitedekiyle aynıdır.
- **Uyarı:** Puanlama kodu bir fonksiyon değil, `asistan.php` içinde satır satır yazılı (~15 satır). İki seçenek var (bkz. E.3).

### 12. `GET /compare`
- **Web eşdeğeri:** `fiyat-karsilastirma.php` (üst bölüm).
- **Parametreler:** `q` (isteğe bağlı; grup içindeki ürün adlarında `as_fold` ile içerir araması; sitedeki istemci tarafı süzgecin karşılığı), `page`, `limit` (≤50, varsayılan 20).
- **data:** `CompareGroup[]`. Sıra `cmp_groups()` sırasıdır (fiyat farkı büyükten küçüğe).
- **Mevcut:** `cmp_groups()`. **Aynı `img/cmpcache.json` dosyası**, aynı anahtar. Web ile fark imkânsız.
- **Not:** "Olası" eşleşmeler dönmez (sitede de gösterilmiyor).

### 13. `GET /compare/{item_id}`
- **data:** `group: CompareGroup|null`, `reason: string|null`. Grup yoksa `200` döner, `group=null`, `reason`="Bu ürün için şu an geçerli kataloglarda kesin eşleşen ürün yok." Bu bir hata değil, bilgi.
- **Hata:** Ürün yoksa veya gizliyse `404`.
- **Mevcut:** `cmp_groups()` içinde `id` araması.

### 14. `GET /compare/similar`
- **Web eşdeğeri:** `fiyat-karsilastirma.php#benzer`.
- **data:** `SimilarGroup[]` (en çok 12).
- **Mevcut:** `mk_similar_groups(q(PSEL.' AND c.cdate<=? AND '.CEND.'>=? LIMIT 6000', [today, today]), 12)` (sitedeki sorgunun aynısı). Sonuç 10 dakika önbelleklenir.

### 15. `POST /assistant`
- **Web eşdeğeri:** `asistan.php?ajax=1` (sohbet kutusu).
- **Gövde (JSON, ≤2 KB):** `{ "message": "a101 çay", "last_query": "kahve", "include_expired": false }`
  - `message`: 1–120 karakter, zorunlu
  - `last_query`: ≤120, isteğe bağlı. Bir önceki yanıttaki `effective_query`; "peki A101'de?" gibi devam soruları için.
- **data:**
  - `say`: asistanın cümlesi
  - `chat_only`: bool. Yalnızca sohbet ise (selamlaşma vb.) ürün listesi yoktur.
  - `links: [{title, date_text, catalog_slug}]`: katalog sorusunda
  - `items`: en çok 5 adet `Item + {unit_text, history_text, status_note}`
  - `quick_replies: string[]` (ör. "Daha ucuzu", "A101'de")
  - `chips`, `note`, `partial`, `total`
  - `effective_query`: bir sonraki istekte `last_query` olarak gönderilir
  - `did_you_mean`, `similar_words`
- **Mevcut:** `as_chat`, `as_parse`, `as_search`, `as_say`, `as_pick`, `by_status`, `as_hist`, `as_histrow`. Akış `asistan.php` ajax bloğuyla birebir aynıdır.
- **Durumsuz:** Sunucu sohbet saklamaz. Sohbet geçmişi telefonda tutulur (Room). Kişisel veri sunucuya gitmez.
- **Not:** `as_pick()` rastgele cümle seçtiği için aynı soruya farklı giriş cümlesi gelebilir (sitede de böyle). Ürün listesi değişmez.

### 16. `GET /pages/{slug}`
- **Web eşdeğeri:** `sayfa.php?s=…`.
- **Parametre:** `slug` = `hakkimizda|iletisim|gizlilik|kosullar`.
- **data:** `title`, `paragraphs: string[]` (düz metin; sitedeki boş satıra göre bölme kuralıyla aynı). Kaynak: `setting('page_'.slug)`, boşsa sitedeki varsayılan metin.
- **Uyarı:** Varsayılan metinler `sayfa.php` içinde bir dizi olarak tanımlı ve dosya `require` edilince HTML basıyor. Yalnızca bu metinler için bkz. E.3.

---

## D. Favori anahtarı: market kimliği + `items.hash`

### D.1 Hash nasıl üretiliyor (koddan doğrulandı)
Dört yerin tamamında **aynı formül** kullanılıyor: `md5(mb_strtolower(ad))`.
- `cron.php` 117. satır: otomatik veri toplama
- `admin/yardimci.php` 85. satır: elle ürün ekleme
- `admin/urunler.php` 28. satır: ürün düzenleme
- `admin/afis.php` 144/150. satırlar: kontrol

Veritabanındaki tekillik kuralı `UNIQUE(catalog_id, hash)`, yani aynı katalogda aynı adla ikinci ürün olamaz.

### D.2 Haftalar arası kararlılık: koda göre beklenen davranış

| Durum | Hash aynı kalır mı? |
|---|---|
| Kaynak, ürün adını her hafta harfi harfine aynı yazıyor | ✅ Aynı |
| Yalnızca büyük/küçük harf farkı ("ÇAY" / "Çay") | ✅ Aynı (`mb_strtolower`) |
| Boşluk, noktalama veya birim yazımı farkı ("1 Lt" / "1 L", "Coca-Cola" / "Coca Cola") | ❌ Değişir |
| Aynı hafta için veri yedek kaynaktan (marketin kendi sitesi) geldi | ❌ Büyük olasılıkla değişir (ad farklı yazılır) |
| Yönetici paneldeki "ad düzenle" ile adı değiştirdi | ❌ Değişir (panel hash'i yeniden hesaplıyor; ID aynı kalıyor) |
| Aynı ad farklı markette | Hash aynı, **bu yüzden anahtara `store_id` eklendi** ✅ |
| Aynı market, aynı hafta, iki ayrı katalogda aynı ürün (ör. BİM 20 ve 24 Kasım) | Hash aynı. `fav` modu öncelik kuralıyla tek kalem seçer (C.8) |
| Türkçe "İ" | Tutarlı. `mb_strtolower('İ')` her seferinde aynı sonucu (i + birleşik nokta) verir |

**Sonuç:** Hash, "aynı market + birebir aynı ad" için kararlıdır. Sitedeki fiyat geçmişi (`as_hkey`) de aynı kabulle çalışıyor. Ad yazımı haftadan haftaya değiştiğinde favori yeni kalemi bulamaz. Bu durumda uygulama, saklanan adı ve "Bu ürün şu an güncel kataloglarda bulunamadı" notunu gösterir. Tahmini eşleştirme **yapılmaz**.

### D.3 Gerçek veriyle kararlılık testi (veri dışa aktarmadan)
Aşağıdaki sorgular **yalnızca okur** ve **yalnızca sayı** döndürür; hiçbir ürün adı veya kişisel veri dışarı çıkmaz. phpMyAdmin → `bayirtkf_aktuel` → **SQL** sekmesinde çalıştırıp sonuç tablosunun fotoğrafını göndermeniz yeterli.

```sql
-- T1: products / prices kayıt sayıları ve veritabanı sürümü
SELECT 'products' tablo, COUNT(*) adet FROM products
UNION ALL SELECT 'prices', COUNT(*) FROM prices
UNION ALL SELECT CONCAT('surum: ', VERSION()), 0;

-- T2: genel tablo — yayındaki kalem sayısı, farklı (market+hash) anahtar sayısı,
--     2+ katalogda tekrar eden anahtar sayısı, 3+ katalogda tekrar eden anahtar sayısı
SELECT
  (SELECT COUNT(*) FROM items i JOIN catalogs c ON c.id=i.catalog_id WHERE c.hidden=0) AS kalem,
  (SELECT COUNT(DISTINCT c.store_id, i.hash) FROM items i JOIN catalogs c ON c.id=i.catalog_id WHERE c.hidden=0) AS anahtar,
  (SELECT COUNT(*) FROM (SELECT 1 FROM items i JOIN catalogs c ON c.id=i.catalog_id WHERE c.hidden=0
     GROUP BY c.store_id, i.hash HAVING COUNT(DISTINCT i.catalog_id) >= 2) x) AS tekrar_2plus,
  (SELECT COUNT(*) FROM (SELECT 1 FROM items i JOIN catalogs c ON c.id=i.catalog_id WHERE c.hidden=0
     GROUP BY c.store_id, i.hash HAVING COUNT(DISTINCT i.catalog_id) >= 3) x) AS tekrar_3plus;

-- T3: tutarlılık — aynı market+hash için birden fazla farklı ad var mı? (0 beklenir)
SELECT COUNT(*) AS cakisan_anahtar FROM (
  SELECT 1 FROM items i JOIN catalogs c ON c.id=i.catalog_id
  GROUP BY c.store_id, i.hash HAVING COUNT(DISTINCT BINARY i.name) > 1) x;

-- T4: market bazında tekrar oranı
SELECT s.name market, COUNT(*) anahtar,
       SUM(n >= 2) tekrar_eden, ROUND(100*SUM(n >= 2)/COUNT(*),1) yuzde
FROM (SELECT c.store_id, i.hash, COUNT(DISTINCT i.catalog_id) n
      FROM items i JOIN catalogs c ON c.id=i.catalog_id WHERE c.hidden=0
      GROUP BY c.store_id, i.hash) k
JOIN stores s ON s.id=k.store_id GROUP BY s.name;
```

**Yorumlama:**
- **T3 sonucu 0** olmalı. Değilse aynı anahtar farklı adlara karşılık geliyor demektir (yalnızca büyük/küçük harf farkı kaynaklı olabilir).
- **T2 ve T4** hash'in pratikte ne ölçüde tekrar ettiğini gösterir. Aktüel ürünlerin çoğu her hafta değiştiği için düşük oran normaldir.
- **Bu sorgular ad yazımı değişen aynı ürünü ölçemez.** Bunu ölçmek için ad metinleri gerekir. Gerekirse yalnızca `items.name` + `stores.slug` + `catalogs.cdate` (son 8 hafta) içeren, kişisel veri taşımayan **küçük bir örnek** isterim (ayrı onayla).

---

## E. Mevcut dosyalarla ilişki ve onay gereken noktalar

### E.1 G4: Uzak görsel adresleri
`catalogs.images` içinde indirilemeyen sayfalar uzak URL olarak kalabiliyor. **Öneri:** API yalnızca yerel `img/` dosyalarını döner; uzak adresli sayfalar listede atlanır ve `pages_skipped` sayısı bildirilir. Sitede ise bu sayfalar görünmeye devam eder. *Onayınız gerekir; alternatif olarak uzak adresler de döndürülebilir.*

### E.2 `ortak-veri.php`: onaylandı, uygulama ve karşılaştırma planı
- **Taşınacaklar:** `MT_MIN_GAIN` sabiti ve `mt_featured()` fonksiyonu. **Karakteri karakterine aynı**; yalnızca dosya yeri değişir.
- **`mt_card()` taşınmaz.** HTML ürettiği için sitede kalır.
- **`tema-modern-ana.php` değişikliği:** Yalnızca ilgili blok silinir; yerine tek satır eklenir: `require_once __DIR__.'/ortak-veri.php';`. Başka bir satıra dokunulmaz.
- **`ortak-veri.php` içeriği:** Başta `require_once` ile `asistan-motor.php` ve `eslestirme.php` yüklenir (fonksiyonun ihtiyacı). Bu iki dosya zaten `tema-modern-ana.php` tarafından `require_once` ile yükleniyor, yani çift tanımlama riski yok.
  - *Not:* `asistan.php` `asistan-motor.php` dosyasını düz `require` ile yüklüyor, ama `ortak-veri.php`'yi hiç yüklemiyor. Bu yüzden `error_log`'daki "Cannot redeclare" hatası tetiklenmez. Bu ayrıca test edilecek.
- **Karşılaştırma testi (canlıya dokunmadan):**
  1. Bu ortamda PHP + MariaDB kurulur. Şema sizin döküm dosyanızdan yüklenir.
  2. Tablolar **benim üreteceğim sahte (sentetik) katalog ve ürünlerle** doldurulur. Gerçek veri gerekmez. Senaryolar: 0 indirimli, 1–3 indirimli, 4+ indirimli, ürünsüz katalog.
  3. Eski `tema-modern-ana.php` ve yeni (`ortak-veri.php` ile) sürümün **tam HTML çıktısı** her senaryoda üretilir ve bayt bayt karşılaştırılır (`diff`). Önbellek dosyaları her çalıştırmadan önce silinir.
  4. Ayrıca bütün sayfalar (`index.php`, `asistan.php`, `fiyat-karsilastirma.php` vb.) açılıp PHP hatası ve uyarısı olmadığı doğrulanır.
- **PHP sürümü:** Burada PHP 8.3.6 var; canlıdaki büyük olasılıkla 8.4. Kod 8.3 ve 8.4'ün ikisinde de çalışacak şekilde yazılır, ikisinde de sözdizimi denetimi yapılır. *Canlı sürümü cPanel'den teyit etmeniz gerekiyor.* Gönderdiğiniz 40 dosyanın tamamı PHP 8.3'te sözdizimi hatası vermedi.

### E.3 Fonksiyon olmayan, sayfa içine yazılmış mantık: karar gerekiyor
Üç yerde gereken mantık bir fonksiyon değil, sayfanın içinde satır satır yazılı. Bu sayfalar `require` edilince HTML basıyor:

| Yer | Satır | API'de nerede |
|---|---|---|
| `asistan.php` öneri puanlaması | ~15 | `/suggest` |
| `asistan.php` sohbet akışı (ajax) | ~25 | `/assistant` |
| `sayfa.php` varsayılan metinler | ~5 | `/pages` |

**Seçenek A (önerilen, daha güvenli):** API'de bu akışın **birebir kopyası** yazılır ve motor fonksiyonları ortak kullanılır. Mevcut dosyalara **dokunulmaz**. FAZ 2'de API ile web çıktısı aynı girdilerle karşılaştırılır. Eksi: ~45 satırlık tekrar, ileride site değişirse API'nin de güncellenmesi gerekir. `docs/` içinde hangi satırın kopya olduğu açıkça işaretlenir.

**Seçenek B:** Bu akışlar da `ortak-veri.php` içine fonksiyon olarak taşınır ve `asistan.php` ile `sayfa.php` bu fonksiyonları çağırır. Tekrar olmaz, ama **iki mevcut dosyada daha değişiklik** gerekir. Değişiklik "en az müdahale" kuralınızla çelişiyor.

### E.4 Değişiklik listesi (FAZ 1 onaylanırsa)

| Dosya | İşlem | Canlıya |
|---|---|---|
| `server/api/.htaccess`, `server/api/v1/*.php` (12–14 dosya) | Yeni | Hayır (yalnızca depo) |
| `server/ortak-veri.php` | Yeni (taşınan fonksiyon) | Hayır |
| `server/tema-modern-ana.php` | Değişmiş kopya (blok kaldırma + 1 satır) | Hayır |
| `server/tests/*` | Yeni: PHP uç nokta testleri ve karşılaştırma betikleri | Hayır |
| `docs/API-DOCUMENTATION.md`, `docs/DATABASE-MAPPING.md` | Yeni | – |
| `inc.php`, `eslestirme.php`, `asistan-motor.php`, `cron.php`, `admin*` | **Değişmez** | – |
| Veritabanı | **Değişmez** | – |
| `MarketlerAndroid/` (WebView projesi) | **Dokunulmaz** | – |
| Native proje klasörü | `MarketlerNative/` (FAZ 3'te) | – |

---

## F. Onay istenen maddeler
1. [ ] 16 uç nokta, alanları ve fonksiyon ilişkileri (bölüm C) uygun mu?
2. [ ] Para değerlerinin metin olarak (`"4599.00"`) ve sitedeki biçimiyle (`price_text`) dönmesi uygun mu?
3. [ ] G4: API yalnızca yerel görselleri döndürsün mü?
4. [ ] G5: `/products` web ile birebir aynı davransın mı (joker karakterler kaçışlanmadan)?
5. [ ] E.3: Seçenek A mı (önerilen; mevcut dosyalara dokunulmaz) yoksa Seçenek B mi?
6. [ ] D.3'teki dört sayım sorgusunu çalıştırıp sonucunu gönderebilir misiniz? T1, `products`/`prices` kayıt sayılarını da içeriyor.
7. [ ] Bu ortamda yerel test için PHP + MariaDB kurulumu ve **sentetik veri** kullanılması uygun mu? Gerçek veri kullanılmaz; yalnızca şema dosyanız kullanılır.
8. [ ] Native proje klasör adı `MarketlerNative/` olsun mu?
