# Marketler.org API v1: Uç Noktaların Ayrıntılı Listesi (onay taslağı, sürüm 2)

**Durum:** Yalnızca tasarım. Kod yazılmadı, canlı sistemde ve veritabanında değişiklik yapılmadı.
**Önceki belge:** `FAZ1-API-SPESIFIKASYONU.md`. Genel kurallar (A) ve veri nesneleri (B) geçerliliğini koruyor. Bu belge **C bölümünün yerine geçer.**
**Örnek JSON'lar:** Değerler **örnek amaçlı uydurulmuştur**; gerçek ürün veya fiyat değildir. Alan adları ve türleri bağlayıcıdır.

---

## 0. Liste: 16 → 14 uç nokta

İnceleme sonucunda iki uç nokta çıkarıldı: döndürecekleri verinin **tamamı** başka uç noktalarda zaten bulunuyor. Aşağıdaki tablo, kaldırılan her alanın nereden alınacağını gösteriyor. Bu karşılıkların eksiksiz olduğunu otomatik testler doğruluyor (`tests/run.php`, C3 grubu).

**`GET /meta` (kaldırıldı)**

| Alan | Karşılığı | Doğrulayan test |
|---|---|---|
| `api_version` | **Her** yanıtta `meta.api_version` (`"1.0"`) | C3: o ana kadar alınan bütün yanıtlar (başarılı ve hata) tek tek denetlenir |
| `server_time` | **Her** yanıtta `meta.generated_at` (ISO 8601, +03:00) | C3 |
| `data_updated_at` (son veri toplama) | `GET /home` → `data.data_updated_at` (`settings.src_run`) | C3 |
| `ttl` (önbellek süreleri) | Her yanıttaki `Cache-Control: max-age=…` başlığı. Uygulama süreyi buradan okur | C3 |
| `min_app_version` | İlk sürümde **gerek yok** (zorunlu güncelleme yok). Gerekirse sonraki bir sürümde `meta` alanına eklenir | – |

**`GET /compare/{item_id}` (kaldırıldı)**

| Alan | Karşılığı | Doğrulayan test |
|---|---|---|
| Ürünün kesin eşleşme grubu | `GET /products/{id}` → `data.compare` (aynı `CompareGroup` nesnesi) | C3: `/compare` listesindeki **bütün** grupların **bütün** kalemleri için `/products/{id}.compare` alanının grupla birebir aynı olduğu doğrulanır |
| "Eşleşme yok" bilgisi | `data.compare = null` ve `data.compare_reason` metni | C3: grupta olmayan bütün aktif kalemler |

Kalan 14 uç noktanın her biri ayrı bir ekranı veya işlevi karşılıyor ve birbirinin yerine geçmiyor:

| # | Yöntem | URL | Uygulamadaki karşılığı | Web eşdeğeri |
|---|---|---|---|---|
| 1 | GET | `/api/v1/home` | Ana sayfa | `index.php` (modern tema) → `tema-modern-ana.php` |
| 2 | GET | `/api/v1/markets` | Marketler listesi | Ana sayfa "Marketler" bölümü |
| 3 | GET | `/api/v1/markets/{slug}` | Market detay | `index.php?m={slug}` |
| 4 | GET | `/api/v1/catalogs` | Kataloglar (aktif / yaklaşan / arşiv) | `kataloglar.php` |
| 5 | GET | `/api/v1/catalogs/{slug}` | Katalog detay + broşür sayfaları | `katalog.php?s={slug}` |
| 6 | GET | `/api/v1/categories` | Arama filtresindeki kategori listesi | `ara.php` kategori seçimi |
| 7 | GET | `/api/v1/products` | Ara ekranı (filtreli liste) + favorilerin güncel durumu | `ara.php` |
| 8 | GET | `/api/v1/products/{id}` | Ürün detay | `pcard()` + `asistan.php` fiyat geçmişi satırı + karşılaştırma |
| 9 | GET | `/api/v1/search` | Asistan "tüm sonuçları gör" sayfası | `asistan.php?q=…` |
| 10 | GET | `/api/v1/suggest` | Arama kutusu anlık önerileri | `asistan.php?oneri=1` |
| 11 | GET | `/api/v1/compare` | Karşılaştır ekranı | `fiyat-karsilastirma.php` (üst bölüm) |
| 12 | GET | `/api/v1/compare/similar` | Karşılaştır → "Benzer ürünlerde birim fiyat" sekmesi | `fiyat-karsilastirma.php#benzer` |
| 13 | POST | `/api/v1/assistant` | Asistan sohbet ekranı | `asistan.php?ajax=1` |
| 14 | GET | `/api/v1/pages/{slug}` | Hakkında / Gizlilik / Koşullar / İletişim | `sayfa.php?s={slug}` |

**Neden hem `/products` hem `/search` var?** Sitede de iki ayrı arama bulunuyor:
- `ara.php`: kelime + filtre ile `LIKE` araması. Sıralama ve sayfalama var.
- `asistan.php`: yazım hatasını anlayan, "500 tl altı" gibi ifadeleri çözen motor.

"Mevcut arama davranışı korunsun" kararı gereği ikisi de ayrı ayrı korunur. `/assistant` en fazla 5 ürün döndürür; daha fazlasını görmek için `/search` kullanılır. Sitedeki "Daha fazla" bağlantısının karşılığı budur.

---

## Ortak notlar

- Bütün yanıtlar `{ "success": true, "data": …, "meta": { "generated_at": …, "api_version": "1.0" } }` zarfıyla döner (A.2).
- **Favori anahtarı tek standarttır:** her ürün nesnesindeki `fav_key` = `"{item_id}:{store_id}:{hash}"`. Uygulama bu değeri olduğu gibi saklar ve `GET /products?fav=` ile aynı biçimde geri gönderir. Hash kalıcı kimlik değildir (bölüm 7).
- Hata biçimi ve kodları A.3'teki gibidir.
- Kısaltmalar: `Item` = B.3 ürün nesnesi, `CatalogSummary` = B.2, `Market` = B.1, `CompareGroup` = B.4. Bir nesnenin tam hali ilk geçtiği yerde gösterilir, sonraki örneklerde kısaltılır.
- "PHP fonksiyonları" satırında **değiştirilmeden çağrılan** mevcut fonksiyonlar listelenir. **(kopya)** etiketi, kullanıcı kararı A gereği API'de birebir yeniden yazılan sayfa içi mantığı gösterir. Bu kısımlar web ile çıktı karşılaştırma testine girer.

---

## 1. `GET /api/v1/home`

**Parametre:** Yok.
**Önbellek:** `public, max-age=300` · **Hız sınırı:** 60/dk

**Örnek yanıt:**
```json
{
  "success": true,
  "data": {
    "data_updated_at": "2026-11-23 06:00:12",
    "markets": [
      { "id": 1, "slug": "bim", "name": "BİM", "color": "#e30613",
        "logo_url": "https://marketler.org/img/logo-bim.png", "live_count": 2, "soon_count": 1 }
    ],
    "featured": {
      "title": "Uygun Fiyatlı Ürünler",
      "subtitle": "Aynı türdeki ürünlerin birim fiyat medyanına göre",
      "items": [
        {
          "id": 48213, "fav_key": "48213:1:3f2a9c0e5b7d41a8c6e2f0b9d1a4c7e3", "hash": "3f2a9c0e5b7d41a8c6e2f0b9d1a4c7e3",
          "name": "Örnek Marka Ayçiçek Yağı 5 L",
          "category": { "slug": "gida", "name": "Gıda" },
          "market": { "id": 1, "slug": "bim", "name": "BİM", "color": "#e30613" },
          "price": "289.00", "price_text": "289,00 TL",
          "old_price": null, "old_price_text": null, "discount_pct": null,
          "unit_price": { "value": "57.80", "unit": "L", "text": "≈ 57,80 TL / L" },
          "image": null,
          "catalog": { "id": 311, "slug": "bim-20-kasim-2026-aktuel-urunler-katalogu", "title": "BİM 20 Kasım 2026 Aktüel Ürünler Kataloğu",
                       "start_date": "2026-11-20", "end_date": "2026-11-23", "status": "live", "status_text": "Aktif" },
          "campaign_text": "20 Kas - 23 Kas 2026",
          "web_url": "https://marketler.org/katalog/bim-20-kasim-2026-aktuel-urunler-katalogu#viewer",
          "note": ["%12 daha uygun", "7 emsal ürünün medyan birim fiyatına göre"]
        }
      ]
    },
    "live_catalogs": [
      { "id": 311, "slug": "bim-20-kasim-2026-aktuel-urunler-katalogu", "title": "BİM 20 Kasım 2026 Aktüel Ürünler Kataloğu", "label": "20 Kasım 2026",
        "market": { "id": 1, "slug": "bim", "name": "BİM", "color": "#e30613" },
        "start_date": "2026-11-20", "end_date": "2026-11-23", "status": "live", "status_text": "Aktif",
        "date_text": "20 Kas - 23 Kas 2026", "item_count": 64,
        "cover": { "url": "https://marketler.org/img/k/bim-20-kasim-2026-aktuel-urunler-katalogu/c/m17951188010.webp",
                   "full_url": "https://marketler.org/img/k/bim-20-kasim-2026-aktuel-urunler-katalogu/m17951188010.webp" },
        "web_url": "https://marketler.org/katalog/bim-20-kasim-2026-aktuel-urunler-katalogu" }
    ],
    "soon_catalogs": [ "…CatalogSummary (en çok 4)…" ],
    "top_compare": { "…CompareGroup (tam hali 11. uç noktada) veya null…": "" },
    "category_shortcuts": [ { "slug": "gida", "name": "Gıda" }, { "slug": "temizlik", "name": "Temizlik" } ]
  },
  "meta": { "generated_at": "2026-11-23T22:10:05+03:00" }
}
```

**Alan kuralları:**
- `featured.items[].note`: string, string dizisi ya da `null` olabilir. `mt_featured()` sonucundaki `_note` alanının aynısıdır.
- `live_catalogs`: en çok 6. `soon_catalogs`: en çok 4.
- `category_shortcuts`: modern sayfadaki 8 kısayol, aynı sırayla.
- `image`: Yalnızca onaylı (`item_media.status=1`) ve **dosyası sunucuda gerçekten olan** görsellerde dolu; aksi halde `null`.
- `old_price` ve `discount_pct`: Yalnızca `old_price > price` ise dolu.

**PHP fonksiyonları:**
- `q(CSEL ...)`, `by_status()`
- `mt_featured()` (yeni `ortak-veri.php` içinden; içeriği değişmez)
- `cmp_groups(1)`, `setting('src_run')`, `setting('logo_'.slug)`
- `item_img()` kuralı, `as_uprice()`, `img_small(...,'c')`, `range_label()`, `cstatus()`, `STL`, `tl()`

---

## 2. `GET /api/v1/markets`

**Parametre:** Yok.
**Önbellek:** `public, max-age=3600` · **Hız sınırı:** 60/dk

```json
{ "success": true,
  "data": [
    { "id": 1, "slug": "bim",  "name": "BİM",  "color": "#e30613", "logo_url": "https://marketler.org/img/logo-bim.png",  "live_count": 2, "soon_count": 1 },
    { "id": 2, "slug": "a101", "name": "A101", "color": "#00a0e3", "logo_url": "https://marketler.org/img/logo-a101.svg", "live_count": 1, "soon_count": 0 },
    { "id": 3, "slug": "sok",  "name": "ŞOK",  "color": "#ffd100", "logo_url": null, "live_count": 0, "soon_count": 1 }
  ],
  "meta": { "generated_at": "2026-11-23T22:10:05+03:00" } }
```

**PHP fonksiyonları:** `q('SELECT * FROM stores')`, `setting('logo_'.slug)`, `q(CSEL ...)` + `by_status()` (sayılar için; ana sayfadaki `$cnt` hesabının aynısı).

---

## 3. `GET /api/v1/markets/{slug}`

| Parametre | Yer | Kural |
|---|---|---|
| `slug` | yol | `[a-z0-9]{1,30}`; site `preg_replace('/[^a-z0-9]/','')` uyguluyor |

**Önbellek:** `public, max-age=900` · **Hata:** `404 NOT_FOUND` "Market bulunamadı."

```json
{ "success": true,
  "data": {
    "market": { "id": 1, "slug": "bim", "name": "BİM", "color": "#e30613", "logo_url": "https://marketler.org/img/logo-bim.png", "live_count": 2, "soon_count": 1 },
    "catalogs": {
      "live": [ "…CatalogSummary…" ],
      "soon": [ "…CatalogSummary…" ],
      "past": [ "…CatalogSummary…" ]
    }
  },
  "meta": { "generated_at": "2026-11-23T22:10:05+03:00" } }
```

**PHP fonksiyonları:** `q(CSEL.' AND s.id=? ORDER BY c.cdate DESC,c.id DESC LIMIT 120', [id])` (index.php'deki sorgunun aynısı), `by_status()`.

---

## 4. `GET /api/v1/catalogs`

| Parametre | Kural | Varsayılan |
|---|---|---|
| `market` | slug, `[a-z0-9]`; geçersizse `400` | – |
| `status` | `live` \| `soon` \| `past` \| `all` | `all` |
| `page` | ≥1 | 1 |
| `limit` | 1–50 | 20 |

**Önbellek:** `public, max-age=900`

`status=all` yanıtı. Sayfalama yalnızca arşiv (`past`) için uygulanır; aktif ve yaklaşan listeler kısa olduğu için tamamı döner:
```json
{ "success": true,
  "data": { "live": [ "…CatalogSummary…" ], "soon": [ "…" ], "past": [ "…en çok limit kadar…" ] },
  "meta": { "generated_at": "…", "page": 1, "limit": 20, "total": 42, "total_live": 3, "total_soon": 1 } }
```

`status=live|soon|past` yanıtı:
```json
{ "success": true, "data": [ "…CatalogSummary…" ], "meta": { "generated_at": "…", "page": 1, "limit": 20, "total": 3 } }
```

**PHP fonksiyonları:** `q(CSEL.($m ? ' AND s.slug=?' : '').' ORDER BY c.cdate DESC,c.id DESC LIMIT 300')` (kataloglar.php sorgusunun aynısı), `by_status()`, `img_small()`, `range_label()`, `dshort()`.

---

## 5. `GET /api/v1/catalogs/{slug}`

| Parametre | Yer | Kural |
|---|---|---|
| `slug` | yol | `[a-z0-9-]{1,190}` |

**Önbellek:** `public, max-age=900`
**Kural:** Yalnızca `hidden=0`. Gizli veya onay bekleyen katalog için `404` "Katalog bulunamadı." döner.

```json
{ "success": true,
  "data": {
    "catalog": { "…CatalogSummary…": "" },
    "price_min": "9.90", "price_min_text": "9,90 TL",
    "price_max": "8999.00", "price_max_text": "8.999,00 TL",
    "pages": [
      { "index": 1,
        "url": "https://marketler.org/img/k/bim-20-kasim-2026-aktuel-urunler-katalogu/m17951188010.webp",
        "thumb_url": "https://marketler.org/img/k/bim-20-kasim-2026-aktuel-urunler-katalogu/t/m17951188010.webp" }
    ],
    "pages_skipped": 0,
    "groups": [
      { "category": { "slug": "elektronik", "name": "Elektronik" }, "items": [ "…Item…" ] },
      { "category": { "slug": "gida", "name": "Gıda" }, "items": [ "…Item…" ] }
    ],
    "other_catalogs": [ "…CatalogSummary (en çok 4)…" ]
  },
  "meta": { "generated_at": "…" } }
```

**Alan kuralları:**
- `pages`: Yalnızca `img/k/` altındaki ve **sunucuda dosyası bulunan** sayfalar. Uzak adresli veya dosyası olmayan sayfalar atlanır, sayıları `pages_skipped` alanında bildirilir.
- `thumb_url`: `t/` küçük kopyası yoksa asıl görselle aynıdır (`img_small()` kuralı).

**PHP fonksiyonları:**
- `q(CSELB.' WHERE c.slug=? AND c.hidden=0')`
- Ürünler: `q('SELECT i.id,i.name,i.price,i.old_price,k.name cn,k.slug ks FROM items i JOIN categories k … WHERE i.catalog_id=? ORDER BY FIELD(k.slug,'.KORDER.'),i.name')` (katalog.php sorgusunun aynısı)
- `img_small(...,'t')`
- Diğer kataloglar: `q(CSEL.' AND c.store_id=? AND c.id<>? ORDER BY c.cdate DESC LIMIT 4')`

---

## 6. `GET /api/v1/categories`

**Parametre:** Yok. **Önbellek:** `public, max-age=3600`

```json
{ "success": true,
  "data": [
    { "slug": "elektronik", "name": "Elektronik", "order": 1, "live_item_count": 37 },
    { "slug": "beyaz-esya", "name": "Beyaz Eşya", "order": 2, "live_item_count": 4 },
    { "slug": "diger",      "name": "Diğer",      "order": 14, "live_item_count": 12 }
  ],
  "meta": { "generated_at": "…" } }
```

**PHP fonksiyonları:**
- `q('SELECT slug,name FROM categories ORDER BY FIELD(slug,'.KORDER.')')` (ara.php sorgusunun aynısı)
- Sayım: `ara.php` "aktif" koşuluyla tek `COUNT … GROUP BY k.slug` sorgusu

---

## 7. `GET /api/v1/products`

**İki modu var:**
- **Liste modu:** Ara ekranı. `ara.php` ile birebir aynı davranır.
- **Favori modu:** `fav` parametresi verilirse. Kaydedilen favorilerin bugünkü durumunu döndürür.

| Parametre | Site adı | Kural | Varsayılan |
|---|---|---|---|
| `q` | `q` | ≤120 karakter; boşluklara göre kelimeler, her biri `i.name LIKE '%kelime%'` (site gibi, joker karakterler kaçışlanmaz) | – |
| `market` | `m` | `[a-z0-9]` | – |
| `category` | `k` | `[a-z0-9-]` | – |
| `min`, `max` | `min`, `max` | `pnum()` ile ayrıştırılır | – |
| `status` | `st` | `aktif` \| `yaklasan` \| `dolmus` \| `hepsi` | `aktif` |
| `date` | `d` | `YYYY-MM-DD`; verilirse `status` yerine geçer | – |
| `sort` | `o` | `new` \| `asc` \| `desc` | `new` |
| `page`, `limit` | `off` | limit 1–50 | 1, 24 |
| `fav` | – | Favori modu: en çok 50 adet `itemId:storeId:hash` (virgülle). Kimlikler pozitif tam sayı, hash `[0-9a-f]{32}` | – |

**Önbellek:** `public, max-age=300`

Liste modu:
```json
{ "success": true,
  "data": [ "…Item…", "…Item…" ],
  "meta": { "generated_at": "…", "page": 1, "limit": 24, "total": 135 } }
```

Favori modu: `?fav=40112:1:3f2a…c7e3`

```json
{ "success": true,
  "data": [
    { "fav_key": "40112:1:3f2a…c7e3",
      "state": "suresi_doldu",
      "message": "Bu ürünün kampanyası sona erdi. Başka bir katalogda aynı adla kayıtlı ürün var; aynı ürün olduğu doğrulanamadığı için favoriniz değiştirilmedi.",
      "item": { "…Item (favorinin KENDİSİ, item_id ile)…": "" },
      "name_changed": false,
      "candidates": [ { "match": "ayni_market_ayni_ad", "confirmed": false, "item": { "…Item…": "" } } ] }
  ],
  "meta": { "generated_at": "…", "api_version": "1.0" } }
```

**Favori kuralları** (kullanıcı kararları):
- Uygulama her favori için şunları saklar: `store_id`, `item_id`, `hash` ve **favoriye eklendiği andaki ürün adı**. Veritabanında tablo veya kolon değişikliği yoktur; bilgiler telefonda tutulur.
- Favorinin **kendisi** her zaman `item_id` ile bulunur. Kayıtlı market uyuşmazsa aynı kalem sayılmaz.
- `state` alanı: `aktif`, `yaklasan`, `suresi_doldu` ya da `bulunamadi` (silinmiş, gizlenmiş veya onay bekleyen katalogda).
- `name_changed`: Yönetici kalemin adını değiştirdiyse `true` olur; kalem yine aynı `item_id` ile bulunur.
- **`items.hash` ve `as_hkey` kalıcı ürün kimliği DEĞİLDİR.** Hash yalnızca arama ipucudur: aynı market ve aynı hash'e sahip **başka** kalemler `candidates` listesinde döner (en çok 3; önce aktif olanlar). Bunlar `confirmed: false` olarak işaretlidir.
- Aynı ürün olduğu kesin doğrulanamadığı için favori **otomatik değiştirilmez**. Uygulama adayı yalnızca öneri olarak gösterebilir; değiştirme kararı kullanıcınındır.
- Kalem bulunamazsa uygulama **kaydettiği adı** ve `message` metnini gösterir. Eski favori silinmez.
- Benzer adla veya tahminle eşleştirme yapılmaz. Örnek: "Dağköy Peynir 500 g" ile "DAĞKÖY PEYNİR 500 G" farklı hash alır ve aday olarak bile gösterilmez (Türkçe "I" sorunu, bkz. test raporu).

**PHP fonksiyonları:**
- **(kopya)** `ara.php` WHERE / ORDER kurgusu: `PSEL`, `CEND`, `pnum()`, `today()`. Sıralamada onaylanan düzeltme dahil: eşitlikte son ölçüt `i.id ASC`; site ve API aynı.
- Toplam sayım sorgusu ara.php ile aynı
- Favori modu: `PSEL.' AND i.id IN (…)'` (favorinin kendisi), `PSEL.' AND (c.store_id=? AND i.hash=?) …'` (adaylar), `cstatus()`

---

## 8. `GET /api/v1/products/{id}`

| Parametre | Yer | Kural |
|---|---|---|
| `id` | yol | pozitif tam sayı |

**Önbellek:** `public, max-age=300`
**Hata:** Ürün yoksa veya kataloğu yayında değilse `404` "Ürün bulunamadı."

```json
{ "success": true,
  "data": {
    "item": { "…Item…": "" },
    "history": {
      "prev_price": "54.90", "prev_price_text": "54,90 TL", "prev_date": "2026-11-13",
      "change_pct": -9, "is_lowest": true, "is_highest": false, "other_count": 3,
      "text": "Önceki katalogdan (13 Kas, 54,90 TL) %9 daha ucuz · Kayıtlı en düşük fiyat"
    },
    "compare": { "…CompareGroup veya null…": "" },
    "compare_reason": null,
    "catalog": { "…CatalogSummary…": "" }
  },
  "meta": { "generated_at": "…" } }
```

**Alan kuralları:**
- `history`: Aynı market + birebir aynı ad taşıyan başka katalog kaydı yoksa `null`. `text` sitedeki `as_hline()` cümlesidir.
- `compare`: Ürün `cmp_groups()` sonucundaki bir grupta değilse `null`. Bu durumda `compare_reason` = "Bu ürün için şu an geçerli kataloglarda kesin eşleşen ürün yok."

**PHP fonksiyonları:**
- `q(PSEL.' AND i.id=?')`, `item_img()` kuralı, `as_uprice()`
- `as_hist(q(PSEL)->fetchAll())` + `as_histrow()` (harita 10 dakika dosya önbelleğinde)
- **(kopya)** `as_hline()` metin kuralı (asistan.php içinde tanımlı)
- `cmp_groups()` → id araması

---

## 9. `GET /api/v1/search`

| Parametre | Kural | Varsayılan |
|---|---|---|
| `q` | **zorunlu**, 1–120 karakter | – |
| `all` | `0` \| `1` (süresi dolmuşlarda da ara) | `0` |
| `page`, `limit` | limit 1–50 | 1, 24 |

**Önbellek:** `no-store` · **Hız sınırı:** 20/dk

```json
{ "success": true,
  "data": {
    "understood": { "market": "bim", "category": null, "terms": ["sut"], "qty": ["1000ml"],
                    "min": null, "max": null, "sort": "asc", "discounted": false },
    "chips": ["Market: BİM", "Ürün: sut", "Miktar: 1000 ml", "En ucuzdan"],
    "match": "full",
    "items": [ { "…Item…": "", "history_text": "Önceki katalogla aynı fiyat (13 Kas)" } ],
    "by_unit": "L",
    "note": null,
    "did_you_mean": null,
    "similar_words": []
  },
  "meta": { "generated_at": "…", "page": 1, "limit": 24, "total": 6 } }
```

**Alan kuralları:**
- `match`: `full` (tam eşleşme), `partial` (yakın sonuçlar) ya da `none`.
- `note`: Aktif kataloglarda sonuç yoksa arşive bakılır ve sitedeki cümle döner: "Aktif ve yaklaşan kataloglarda sonuç yok; süresi dolmuş kataloglardaki kayıtlar gösteriliyor."
- `did_you_mean`, `similar_words`: Sonuç yoksa, yazım düzeltme önerileri.

**PHP fonksiyonları:**
- `as_index()`, `as_search()`, `as_hist()`, `as_histrow()`
- **(kopya)** `asistan.php` arşive düşme akışı, `$chips` metinleri ve `as_hline()`

---

## 10. `GET /api/v1/suggest`

| Parametre | Kural |
|---|---|
| `q` | 2–120 karakter; daha kısaysa boş liste (hata değil) |

**Önbellek:** `private, max-age=60` (sitedeki gibi) · **Hız sınırı:** 120/dk

```json
{ "success": true,
  "data": [
    { "name": "Örnek Marka Süt 1 L", "price_text": "32,50 TL", "market_name": "A101",
      "catalog_slug": "a101-19-kasim-2026-aktuel-urunler-katalogu",
      "web_url": "https://marketler.org/katalog/a101-19-kasim-2026-aktuel-urunler-katalogu#viewer" }
  ],
  "meta": { "generated_at": "…" } }
```

**PHP fonksiyonları:**
- `as_fold()`, `tl()`, `CEND`
- **(kopya)** `asistan.php` öneri modu: havuz sorgusu, puanlama, en çok 5 sonuç. **Sitenin havuz önbellek dosyası** (`mk_oneri_<md5(site klasörü)>.json`, 120 sn) ortak okunur ve yazılır.

---

## 11. `GET /api/v1/compare`

| Parametre | Kural | Varsayılan |
|---|---|---|
| `q` | ≤120; grup içindeki ürün adlarında `as_fold()` ile içerir araması (sitedeki "Karşılaştırmalarda ara" kutusunun karşılığı) | – |
| `page`, `limit` | limit 1–50 | 1, 20 |

**Önbellek:** `public, max-age=300`

```json
{ "success": true,
  "data": [
    {
      "group_id": "48213",
      "title": "Örnek Marka Airfryer 4.2 L XYZ1234",
      "is_manual": false,
      "rows": [
        { "…Item (A101)…": "", "is_best": true },
        { "…Item (BİM)…": "",  "is_best": false },
        { "…Item (ŞOK)…": "",  "is_best": false }
      ],
      "min_price": "4599.00", "max_price": "5199.00",
      "diff": "600.00", "diff_text": "600,00 TL", "diff_pct": 12,
      "saving_text": "En ucuz markette 600,00 TL (%12) daha az."
    }
  ],
  "meta": { "generated_at": "…", "page": 1, "limit": 20, "total": 18 } }
```

**Alan kuralları:**
- Sıra `cmp_groups()` sırasının aynısıdır (fiyat farkı büyükten küçüğe).
- `rows` fiyata göre artan sıradadır. `is_best` değeri `price == min` karşılaştırmasıyla belirlenir; sitedeki "En düşük" etiketiyle aynı kuraldır.
- `diff_pct` ve `saving_text` formülleri `fiyat-karsilastirma.php` ile aynıdır.
- Yalnızca **kesin** eşleşmeler döner. "Olası" eşleşmeler (sitede de gösterilmiyor) dönmez.

**PHP fonksiyonları:**
- `cmp_groups()`: aynı `img/cmpcache.json` dosyası ve aynı anahtar; yeni eşleştirme yok
- `tl()`, `item_img()` kuralı, `as_uprice()`

---

## 12. `GET /api/v1/compare/similar`

**Parametre:** Yok. **Önbellek:** `public, max-age=300`

```json
{ "success": true,
  "data": {
    "notice": "Bu bölümdeki ürünler aynı ürün değildir. Yalnızca aynı türde (ör. ayçiçek yağı) ve aynı ölçü birimine (kg veya L) çevrilebilen ürünlerin birim fiyatı karşılaştırılır. En az 2 markette ve en az 3 ürün bulunan türler listelenir.",
    "groups": [
      { "key": "aycicek|L", "label": "Ayçiçek yağı", "unit": "L",
        "rows": [
          { "…Item…": "", "unit_price": { "value": "57.80", "unit": "L", "text": "57,80 TL/L" }, "is_best": true },
          { "…Item…": "", "unit_price": { "value": "61.25", "unit": "L", "text": "61,25 TL/L" }, "is_best": false }
        ] }
    ]
  },
  "meta": { "generated_at": "…" } }
```

**Alan kuralları:**
- `notice`: Sitedeki uyarı metni, kelimesi kelimesine aynı.
- `groups`: En çok 12 grup. İlk satır en düşük birim fiyatlıdır (`is_best`).

**PHP fonksiyonları:**
- `mk_similar_groups(q(PSEL.' AND c.cdate<=? AND '.CEND.'>=? LIMIT 6000', [today(), today()])->fetchAll(), 12)` (sitedeki sorgunun aynısı)
- `mk_type_label()`
- Sonuç 10 dakika önbelleklenir.

---

## 13. `POST /api/v1/assistant`

**İstek** (`Content-Type: application/json`, gövde ≤ 2 KB):
```json
{ "message": "peki a101'de?", "last_query": "kahve", "include_expired": false }
```

| Alan | Kural |
|---|---|
| `message` | **zorunlu**, 1–120 karakter (sitedeki `maxlength=120`) |
| `last_query` | isteğe bağlı, ≤120. Önceki yanıttaki `effective_query` değeri (sitedeki `lq`) |
| `include_expired` | isteğe bağlı bool (sitedeki `all`) |

**Önbellek:** `no-store` · **Hız sınırı:** 20/dk · **Hatalar:** `400` (boş veya uzun mesaj), `413` (gövde > 2 KB)

Ürün sonucu yanıtı:
```json
{ "success": true,
  "data": {
    "say": "Tamam, aynı ürün için bakıyorum… 4 ürün buldum A101 kataloglarında. İşte en uygunları:",
    "chat_only": false,
    "effective_query": "kahve a101",
    "chips": ["Market: A101", "Ürün: kahve"],
    "items": [
      { "…Item…": "", "unit_text": "≈ 412,00 TL/kg", "history_text": "", "status_note": "" }
    ],
    "links": [],
    "quick_replies": ["Daha ucuzu", "A101'de", "BİM'de", "ŞOK'ta"],
    "note": "", "partial": false, "total": 4,
    "did_you_mean": "", "similar_words": []
  },
  "meta": { "generated_at": "…" } }
```

Yalnızca sohbet yanıtı (ör. "merhaba" veya "bu hafta hangi kataloglar var?"):
```json
{ "success": true,
  "data": {
    "say": "Şu an geçerli kataloglar:", "chat_only": true, "effective_query": "",
    "items": [],
    "links": [ { "title": "BİM 20 Kasım 2026 Aktüel Ürünler Kataloğu", "date_text": "20 Kas - 23 Kas 2026", "catalog_slug": "bim-20-kasim-2026-aktuel-urunler-katalogu" } ],
    "quick_replies": ["En ucuz kahve", "İndirimli ürünler", "Bu haftanın katalogları", "Neler yapabilirsin?"]
  },
  "meta": { "generated_at": "…" } }
```

**Alan kuralları:**
- `say`: Sitede `as_pick()` rastgele cümle seçtiği için aynı soruya farklı giriş cümlesi gelebilir. Ürün listesi ve sayılar değişmez. Testlerde bu alan cümle havuzuna göre doğrulanır.
- Sunucu sohbeti **saklamaz**. Sohbet geçmişi telefonda tutulur.

**PHP fonksiyonları:**
- `as_chat()`, `as_parse()`, `as_index()`, `as_search()`, `as_say()`, `as_pick()`, `as_hist()`, `as_histrow()`
- `by_status(q(CSEL))`, `range_label()`, `cstatus()`, `STL`
- **(kopya)** `asistan.php` ajax bloğu: sohbet → önceki sorguyla birleştirme → arama → ilk 5 → yanıt metni; `as_uline()` ve `as_hline()`

---

## 14. `GET /api/v1/pages/{slug}`

| Parametre | Yer | Kural |
|---|---|---|
| `slug` | yol | `hakkimizda` \| `iletisim` \| `gizlilik` \| `kosullar`; başka değer `404` |

**Önbellek:** `public, max-age=3600`

```json
{ "success": true,
  "data": {
    "title": "Gizlilik Politikası",
    "paragraphs": [
      "Bu site, ziyaretçilerden ad, adres gibi kişisel bilgi toplamaz. Ürün arama sırasında girilen kelimeler yalnızca sonuçları göstermek için kullanılır.",
      "Site yazı tipi için Google Fonts'u kullanır. …"
    ]
  },
  "meta": { "generated_at": "…" } }
```

**Kurallar:**
- Kaynak: `setting('page_'.slug)` (yalnızca bu dört anahtar). Değer boşsa sitedeki varsayılan metin kullanılır.
- Paragraflar sitedeki gibi boş satırdan bölünür (`preg_split('/\n\s*\n/')`).
- `iletisim` sayfasında e-posta, sitedeki gibi metnin içinde yer alır (`setting('contact_email')`).

**PHP fonksiyonları:** `setting()`, `BRAND`; **(kopya)** `sayfa.php` varsayılan metin dizisi ve bölme kuralı.

> **Gizlilik notu (uygulama için):** Sitedeki gizlilik metni web sitesini anlatıyor. Uygulama telefonda favori ve sohbet geçmişi saklayacak (sunucuya gönderilmeden). Bunun gizlilik metnine eklenmesi gerekebilir. Bu bir **içerik kararıdır**; API metni değiştirmez, kararı size bırakıyorum.

---

## 15. Test edilecek eşdeğerlik (FAZ 2'ye girdi)

| Uç nokta | Karşılaştırma yöntemi |
|---|---|
| `/home` featured, top_compare | `mt_featured()` ve `cmp_groups(1)` çıktısı ile API `data` alanı aynı veri üzerinde alan alan karşılaştırılır |
| `/products` | `ara.php?ajax=1` ile aynı parametrelerde: toplam sayı ve ürün ID sırası aynı olmalı |
| `/search` | `asistan.php?q=` sayfası: sonuç ID sırası, `chips`, `note` |
| `/suggest` | `asistan.php?oneri=1` JSON yanıtı: ad, fiyat, market sırası aynı olmalı |
| `/assistant` | `asistan.php?ajax=1` JSON yanıtı: `items`, `chips`, `qr`, `total`, `part` aynı; `say` cümle havuzuna göre doğrulanır |
| `/compare` | `cmp_groups()` sırası ve fiyatları; `fiyat-karsilastirma.php` HTML'inden ayrıştırılan grup ve fiyat listesi |
| `/compare/similar` | `mk_similar_groups()` |
| `/pages/*` | `sayfa.php` çıktısındaki paragraflar |
| `/catalogs*`, `/markets*` | `kataloglar.php`, `index.php?m=`, `katalog.php` HTML'inden ayrıştırılan katalog ve ürün sırası |

Bu testler yerelde, sentetik veriyle ve gönderilen PHP dosyalarının kendisi çalıştırılarak yapılacak. Siteye istek gönderilmeyecek.

---

## Uygulama notları (FAZ 1 kodlaması sırasında netleşen ayrıntılar)
- **`/products/{id}` fiyat geçmişi:** Site `as_hist()` için tüm ürünleri okur. API yalnızca **aynı marketin** ürünlerini okur. `as_hkey` market kodunu içerdiği için sonuç birebir aynıdır (test C ve D ile doğrulandı). Önbellek kullanılmaz.
- **`/compare/similar`:** Site bu bölümü önbelleğe almıyor. Web ile aynı sonuç için API de **önbelleksiz** hesaplar (önceki taslaktaki "10 dakika önbellek" kaldırıldı).
- **`/assistant` → `links[]`:** `web_url` alanı eklendi (mutlak katalog adresi).
- **`/catalogs/{slug}`:** Dosyası sunucuda bulunmayan yerel sayfalar da `pages_skipped` sayısına eklenir.
- **Hız sınırı ve Cloudflare:** Sınır, gerçek ziyaretçi IP'sine göre uygulanır. `CF-Connecting-IP` başlığı **yalnızca** isteği doğrudan gönderen adres (`REMOTE_ADDR`) `config.php` içindeki Cloudflare IP aralıklarındaysa **ve** başlık geçerli bir IP adresiyse kullanılır. Aksi halde başlık yok sayılır ve bağlantı adresi esas alınır. Sunucuda `mod_remoteip` zaten etkinse `REMOTE_ADDR` ziyaretçi adresi olur, bu kural da doğru çalışır. Cloudflare IP listesi canlıya almadan önce resmi listeyle karşılaştırılmalıdır.
- **Hata durumlarında JSON:** Ölümcül PHP hatası veya site kodunun düz metin basıp çıkması (ör. `inc.php` veritabanı bağlantı hatası) gibi durumlarda da yanıt standart JSON hata zarfında döner (`500 INTERNAL_ERROR` / `503 SERVICE_UNAVAILABLE`).
