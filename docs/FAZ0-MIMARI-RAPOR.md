# Marketler.org Native Android – FAZ 0: Mevcut Sistem İncelemesi ve Mimari Rapor

**Durum:** Yalnızca inceleme yapıldı. Canlı sitede, dosyalarda ve veritabanında **hiçbir değişiklik yapılmadı**.
**Kaynak:** Kullanıcının gönderdiği `marketler.org.zip` (40 dosya, parolalar temizlenmiş) ve `bayirtkf_aktuel` veritabanının yalnızca yapı (şema) dökümü.
**Erişilemeyenler:** Canlı site ve gerçek veriler. Bu nedenle veri hacmi, `products`/`prices` tablolarının içeriği ve PHP sürümü **doğrulanmadı**; ilgili yerlerde "teyit gerekli" diye belirtildi.

---

## 1. Mevcut PHP sisteminin mimarisi

### 1.1 Genel yapı

- Çatısız (framework'süz), düz PHP. Her sayfa `require 'inc.php'` ile başlar ve HTML'i doğrudan `echo` ile üretir.
- Barındırma: cPanel. Site klasörü `~/public_html/marketler.org/` (addon domain).
- PHP sürümü: `error_log` içindeki hata mesajının biçimi ("Cannot redeclare **function** as_fold()") **PHP 8.4 veya üstünü** gösteriyor. *Teyit gerekli.*
- Veritabanı: MySQL uyumlu, InnoDB, `utf8mb4`. Şema dökümünün biçimi (`current_timestamp()`) **MariaDB**'ye işaret ediyor. *Teyit gerekli.*
- Ek servis yok: Redis/Memcached kullanılmıyor. Önbellekler dosya tabanlı: `img/cmpcache.json` ve `sys_get_temp_dir()` altındaki JSON dosyaları.

### 1.2 Dosyalar ve görevleri

| Dosya | Görev | Veritabanına yazar mı? |
|---|---|---|
| `config.php` | `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASS` sabitleri | – |
| `inc.php` | Çekirdek: PDO bağlantısı `db()`, parametreli sorgu `q()`, `setting()`, yardımcılar, kategori sınıflandırıcı `cat_of()`, görsel küçültme, katalog sorgu sabitleri (`CEND`, `CSEL`, `PSEL`), `cstatus()`, HTML bileşenleri, **fiyat karşılaştırma `cmp_groups()`**, `head()`/`foot()`, site haritası | **Evet, koşullu:** `schema_v ≠ 5` ise şema güncellemesi çalışır (şu an 5, yani çalışmaz) |
| `eslestirme.php` | Ürün eşleştirme motoru: `mk_parse()`, `mk_same()` (kesin/olası), emsal grupları, birim fiyat karşılaştırması, olası eşleşme adayları | Hayır |
| `asistan-motor.php` | Kural tabanlı arama motoru: `as_parse()`, `as_search()`, `as_chat()`, `as_uprice()`, fiyat geçmişi `as_histrow()` | Hayır |
| `asistan.php` | Asistan sayfası ve iki JSON uç noktası: `?oneri=1` (anlık öneri) ve `?ajax=1` (sohbet) | Hayır |
| `index.php` | Klasik ana sayfa ve market sayfası (`?m=slug`) | Hayır |
| `tema-modern-ana.php` | Modern ana sayfa ve **öne çıkan ürünler** `mt_featured()` (10 dakika dosya önbelleği) | Yalnız geçici dosya |
| `katalog.php` | Katalog detayı: broşür sayfaları ve kategoriye göre ürünler | Hayır |
| `kataloglar.php` | Katalog listesi: aktif, yaklaşan, süresi dolmuş | Hayır |
| `ara.php` | Filtreli ürün arama: `LIKE`, market, kategori, fiyat, durum, tarih, sıralama, sayfalama. `?ajax=1` HTML parçası döner | Hayır |
| `fiyat-karsilastirma.php` | `cmp_groups()` (aynı ürün) ve `mk_similar_groups()` (benzer ürünlerde birim fiyat) | Hayır |
| `sayfa.php` | Hakkımızda, iletişim, gizlilik, koşullar (`settings.page_*`) | Hayır |
| `cron.php` | Veri toplama (aşağıda 1.3) | **Evet** |
| `admin.php` + `admin/*.php` | Yönetim paneli: kataloglar, ürünler, afiş görselleri, eşleştirme, kategori denetimi, işlem günlüğü | **Evet** |
| `instal2l.php` | Kurulum betiği. `.htaccess` ile engellenmiş | Evet (kurulumda) |

### 1.3 Veri akışı

```
cron.php (anahtarlı URL ya da CLI)
  ├─ 1. kaynak: aktuel-urunler.com → katalog başlığı, tarih, ürün adı + fiyat tablosu, broşür görselleri
  ├─ yedek: BİM / A101 / ŞOK'un kendi siteleri (görselsiz, haftalık sarmalayıcı katalog)
  └─ görseli eksik kataloglar için yalnız görsel: aktuel.blog
        ↓
  catalogs (hidden=2: ONAY BEKLİYOR)  +  items (REPLACE, hash = md5(küçük harf ad))
  görseller → img/k/<katalog-slug>/N.webp  +  küçük kopyalar c/ (480 px), t/ (180 px)
        ↓
admin.php: yönetici kataloğu onaylar (hidden=0) → sitede görünür
admin/afis.php: broşürden ürün kutusu kırpılır → img/u/... → item_media(status=1)
admin.php "Eşleştirme": elle grup (product_links) / eşleşmeyi reddet (link_blocks)
```

### 1.4 Önemli iş kuralları (API aynen korumalı)

- **Katalog bitiş tarihi saklanmaz, hesaplanır** (`CEND`): Aynı marketin bir sonraki yayındaki kataloğunun başlangıcından bir gün öncesi; sonraki katalog yoksa başlangıç + 6 gün.
- **Katalog durumu** (`cstatus`): `soon` (başlamadı), `live` (aktif), `past` (süresi dolmuş).
- **Görünürlük:** Sitede yalnızca `catalogs.hidden = 0` olanlar görünür.
  - `1`: yönetici gizlemiş
  - `2`: onay bekliyor
- **Fiyat karşılaştırma** (`cmp_groups`):
  - Yalnızca bitmemiş kataloglardaki ürünler alınır.
  - Önce **elle kurulan gruplar** (`product_links`) eklenir; en az iki farklı markette olmalıdır.
  - Elle gruplanmamış ürünler markaya göre ayrılır; `mk_same() === 'kesin'` olan, **kampanya tarihleri örtüşen** ve **engellenmemiş** (`link_blocks`) çiftler gruplanır.
  - **"Olası" eşleşmeler sitede gösterilmez**; yalnızca yönetici onay kuyruğunda yer alır.
  - Sonuç `img/cmpcache.json` dosyasında önbelleğe alınır. Önbellek anahtarı ürün sayısı, eşleşme sayısı, engel sayısı, tarih ve `MK_MATCH_VER` bilgisinden oluşur.
- **Sahte indirim yok:** İndirim yüzdesi yalnızca `old_price > price` ise gösterilir. Kaynaklar `old_price` vermediği için bu alan **çoğunlukla NULL** (cron koduna göre çıkarım; *veriyle teyit gerekli*).
- **Birim fiyat** (`as_uprice`): Yalnızca gıda, temizlik, kişisel bakım ve anne-bebek kategorilerinde, adda tek ve net bir miktar varsa hesaplanır. Tahmin yürütülmez.
- **Fiyat geçmişi** (`as_histrow`): Yalnızca **aynı market + birebir aynı ürün adı** taşıyan başka kataloglardaki kayıtlardan gelir.

---

## 2. Android için gerekli API uç noktaları

Temel: `https://marketler.org/api/v1/`

| Uç nokta | Ne döner | Yeniden kullanılacak mevcut mantık |
|---|---|---|
| `GET /home` | Marketler ve aktif katalog sayıları, öne çıkan ürünler (başlık + gerekçe notu), güncel kataloglar (ilk 6), yaklaşan kataloglar (ilk 4), öne çıkan karşılaştırma (1 grup), kategori kısayolları | `by_status`, `CSEL`, `mt_featured()`, `cmp_groups(1)` |
| `GET /markets` | Market listesi: id, slug, ad, renk, logo URL'si, aktif katalog sayısı | `stores`, `setting('logo_<slug>')`, `by_status` |
| `GET /markets/{slug}` | Market ve katalogları, duruma göre gruplu | `index.php?m=` mantığı |
| `GET /catalogs?market=&status=&page=` | Katalog listesi: kapak (c/), başlangıç ve bitiş, durum, ürün sayısı | `CSEL`, `cstatus`, `img_small(...,'c')` |
| `GET /catalogs/{slug}` | Katalog detayı, broşür sayfaları (asıl + t/ küçük), kategoriye göre ürünler | `katalog.php` sorguları |
| `GET /categories` | 14 sabit kategori (sıralı) | `KATS`, `KORDER` |
| `GET /products?q=&market=&category=&min=&max=&status=&date=&sort=&page=` | Filtreli ürün listesi ve toplam sayı | `ara.php` WHERE kuralları + `PSEL` |
| `GET /products/{id}` | Ürün (katalog kalemi): fiyat, eski fiyat, kategori, market, katalog, kampanya tarihi, görsel (varsa), birim fiyat, fiyat geçmişi, karşılaştırma grubu | `PSEL`, `item_img`, `as_uprice`, `as_histrow`, `cmp_groups` |
| `GET /search?q=` | Akıllı arama: anlaşılan filtreler, tam ve yakın sonuçlar, "bunu mu demek istedin" önerisi | `as_index` + `as_search` |
| `GET /suggest?q=` | Yazarken öneri (en çok 5) | `asistan.php?oneri=1` mantığı |
| `GET /compare` | Tüm karşılaştırma grupları (sayfalı) | `cmp_groups()` |
| `GET /compare/{item_id}` | Ürünün bulunduğu kesin eşleşme grubu, en ucuz, fark ve tasarruf | `cmp_groups()` (**aynı önbellek, aynı sonuç**) |
| `GET /compare/similar` | Benzer ürünlerde birim fiyat (farklı ürünler etiketiyle) | `mk_similar_groups()` |
| `POST /assistant` | Sohbet yanıtı: metin, anlaşılan filtreler, ürün kartları, hızlı yanıtlar, "bunu mu demek istedin" | `as_chat` + `as_parse` + `as_search` + `as_histrow` |
| `GET /pages/{hakkimizda\|iletisim\|gizlilik\|kosullar}` | Statik sayfa metni (düz metin) | `sayfa.php` |
| `GET /meta` | API sürümü, sunucu saati, verinin son güncellenme zamanı | `settings.src_run` |

**Yanıt biçimi** talimattaki gibi olacak: `{success, data, meta}` / `{success:false, error:{code,message}}`. Ek olarak her yanıtta `meta.generated_at` bulunur. Böylece uygulama "veri ne zaman alındı" bilgisini gösterebilir.

**Bilerek olmayanlar:** yazma işlemi, yönetici işlemi, `settings` tablosunun dökümü, `users`, `admin_log`, `logs`, gizli (`hidden ≠ 0`) kataloglar. **"Olası" eşleşmeler de dönmez**, çünkü sitede de gösterilmiyorlar.

---

## 3. Veritabanı tablolarının kullanım alanları

| Tablo | Kullanım | API'de |
|---|---|---|
| `stores` | 3 market (BİM, A101, ŞOK): slug, ad, renk. Logo `settings.logo_<slug>` içinde | ✅ Okunur |
| `catalogs` | Haftalık aktüel katalog: `cdate` başlangıç; `hidden` 0/1/2; `images` = JSON dizi (`img/k/...` göreli yol **ya da** indirilemediyse uzak URL) | ✅ Yalnızca `hidden=0` |
| `items` | Katalogdaki ürün satırı. **Sitedeki "ürün" budur**: id, ad, fiyat, eski fiyat, kategori | ✅ Okunur |
| `categories` | 14 sabit kategori | ✅ Okunur |
| `item_media` | Onaylı ürün görseli (`status=1`, dosya `img/u/...`); `status=2` kaldırılmış | ✅ Yalnızca `status=1` |
| `product_links` | Elle kurulan eşleşme grupları (`grp`, `item_id`) | ✅ Karşılaştırmada (mevcut fonksiyon üzerinden) |
| `link_blocks` | Reddedilmiş eşleşme çiftleri (`a<b`) | ✅ Karşılaştırmada (mevcut fonksiyon üzerinden) |
| `settings` | Anahtar-değer ayarlar: site metinleri, logolar, tema, **`cron_key` (gizli)**, iletişim e-postası, kaynak durumu | ⚠️ Yalnızca izin verilen anahtarlar tek tek okunur |
| `users` | Yönetici hesapları | ❌ Asla |
| `admin_log`, `logs` | İşlem ve cron günlükleri | ❌ Asla |
| `products`, `prices` | **Hiçbir PHP dosyası kullanmıyor** (eski sürümden kalma olabilir) | ❌ Kullanılmaz. *İçerikleri ve silinip silinmeyeceği teyit edilmeli; şimdilik dokunulmayacak.* |

**Önemli sonuç:** Sistemde haftalar boyunca kalıcı bir "ürün kimliği" yok. Her katalog, ürünleri yeni `items` satırları (yeni ID'ler) olarak ekliyor. Bunun Android tarafındaki etkileri:
- **Ürün detay ekranı** bir katalog kalemini gösterir. Katalog süresi dolunca kalem arşivde kalır; silinirse bulunamaz.
- **Favoriler** yalnızca ID ile tutulursa haftaya geçersiz olur. Öneri: favori kaydı ID ile birlikte "market + ürün adı" anahtarını (`as_hkey`) da saklasın. Böylece aynı ürün yeni katalogda çıkınca uygulama onu bulabilir. Bu, sitenin fiyat geçmişinde kullandığı mantığın aynısı.

---

## 4. API'de kullanılabilecek mevcut fonksiyonlar

| Doğrudan kullanılabilir (çıktı üretmez, DB'ye yazmaz) | Not |
|---|---|
| `db()`, `q()`, `setting()` | PDO, prepared statement. Bağlantı hatasında düz metin basıp `exit` ediyor; API'de JSON hata dönülecek (bkz. 5.2) |
| `CEND`, `CSEL`, `CSELB`, `PSEL`, `cstatus()`, `by_status()`, `sort_current()`, `range_label()`, `dshort()` | Katalog ve tarih kuralları |
| `KATS`, `KORDER`, `cat_of()` | Kategori listesi ve sırası |
| `img_small()` | Küçük görsel yolu (dosya yoksa asıl görsel) |
| `cmp_groups()` | **Karşılaştırmanın tek kaynağı.** Aynı önbellek dosyası kullanılır; web ile fark oluşmaz |
| `mk_parse`, `mk_same`, `mk_similar_groups`, `mk_type_label`, `mk_peer_groups`, `mk_peer_scores` | Eşleştirme ve emsal (değiştirilmeyecek) |
| `as_fold`, `as_index`, `as_parse`, `as_search`, `as_chat`, `as_say`, `as_uprice`, `as_hist`, `as_histrow` | Asistan ve arama motoru |

**Doğrudan kullanılamayanlar ve nedenleri:**

1. **URL fonksiyonları** (`bpath()`, `imgurl()`, `u_kat()`, `u_mkt()`, `item_img()`): Adresleri çalışan betiğin klasöründen (`SCRIPT_NAME`) türetiyorlar. `/api/v1/` altında çağrılınca **yanlış adres** üretirler (`/api/v1/img/...`). API, site kökünü `site_url()` ile alıp aynı kuralla mutlak URL üretecek. Bunun için `api/` içinde küçük bir yardımcı yazılacak; mevcut fonksiyonlar değişmeyecek.
2. **`mt_featured()`:** `tema-modern-ana.php` içinde tanımlı. Bu dosya `require` edilince HTML basıyor, bu yüzden API onu doğrudan yükleyemez. Seçenekler:
   - **(Önerilen) Küçük taşıma:** Fonksiyon ve yardımcı sabiti, içeriği değiştirilmeden yeni bir `ortak-veri.php` dosyasına taşınır. `tema-modern-ana.php` dosyası bunu `require_once` ile yükler. Davranış birebir aynı kalır. **Mevcut bir dosyada değişiklik olduğu için onayınız gerekir.**
   - (Alternatif) API'de kopya: Talimattaki "aynı işi yapan ikinci kod yazma" kuralına aykırı.
3. **`asistan.php` sohbet akışı ve `ara.php` filtre kurgusu:** Fonksiyon değil, sayfa içinde satır satır yazılı. Motor fonksiyonları ortak kullanılacak; API yalnızca bu birkaç satırlık "yönetim" kısmını kendi uç noktasında yapacak. İsterseniz sonraki bir aşamada bu kısımlar da ortak fonksiyonlara taşınabilir (ayrıca onay gerekir).
4. **`inc.php` yan etkisi:** `schema_v ≠ 5` olursa sayfa açılışında şema güncellemesi çalışıyor. Şu an 5 olduğu için sorun yok, ama API de aynı dosyayı yükleyeceğinden bunu bilmek gerekiyor.

---

## 5. Güvenlik açısından dikkat edilecek noktalar

### 5.1 Mevcut sistemde iyi olanlar
- Tüm sorgular PDO prepared statement. `ara.php` içindeki `LIMIT/OFFSET` tam sayıya çevriliyor, sıralama beyaz listede.
- Hatalar ekrana basılmıyor (`display_errors=0`), sunucu günlüğüne yazılıyor.
- Yönetici girişi: `password_hash`, IP başına 15 dakikada 5 deneme sınırı, oturum kimliği yenileme, `HttpOnly` + `SameSite=Lax` çerez, tüm POST işlemlerinde CSRF belirteci.
- Yüklenen görsellerin türü içerikten doğrulanıyor, SVG içinde betik reddediliyor. `img/` klasöründe PHP çalıştırma kapalı.
- `.htaccess`: HTTPS ve www yönlendirmesi, `.sql/.zip/.ini/.log`, `cmpcache.json`, `debug_*.html` ve kurulum dosyaları dışarıya kapalı, `nosniff` ve `X-Frame-Options` başlıkları var.

### 5.2 Bulgular (hiçbirine dokunulmadı; bilginize ve onayınıza sunuluyor)

| # | Bulgu | Risk | Öneri |
|---|---|---|---|
| G1 | Site kökünde `error_log` dosyası var. `.htaccess` kuralı yalnızca `.log` **uzantılı** dosyaları kapatıyor; `error_log` adı bu kurala uymuyor. Dosya `https://marketler.org/error_log` adresinden **açık olabilir** ve sunucu klasör yollarını gösteriyor | Düşük–orta | Tarayıcıdan bu adresi deneyin. Açılıyorsa `.htaccess` kuralına `error_log` adını eklemek tek satırlık bir değişiklik |
| G2 | `instal2l.php` sunucuda duruyor (`.htaccess` ile kapalı) | Düşük | Kurulum bittiyse silinmesi daha güvenli |
| G3 | `OKU.txt` herkese açık (değişiklik notları) | Çok düşük | İsteğe bağlı olarak silinebilir veya kapatılabilir |
| G4 | `catalogs.images` içinde görseli indirilemeyen katalogların **uzak URL'leri** kalabiliyor (aktuel-urunler.com) | Telif / bağımlılık | Uygulama yalnızca `img/` altındaki yerel görselleri gösterecek, uzak URL'leri göstermeyecek (öneri) |
| G5 | `ara.php` aramasında `%` ve `_` karakterleri kaçışlanmıyor | Çok düşük (performans) | API'de kaçışlanacak; site aynı kalır |
| G6 | `HSTS` başlığı yorum satırında | Düşük | Site uzun süredir HTTPS'teyse açılabilir (sizin kararınız) |
| G7 | `error_log` içinde 22 Kasım tarihli `as_fold()` yeniden tanımlama hataları var | Bilgi | `eslestirme.php` içindeki not bu hatanın düzeltildiğini gösteriyor. Güncel günlükte tekrar ediyorsa bakılmalı |

### 5.3 API için güvenlik tasarımı
- Yalnızca `GET` (asistan için `POST`). Kimlik doğrulama yok, çünkü veriler zaten herkese açık. **Yazma işlemi yok.**
- `settings` tablosu yalnızca beyaz listedeki anahtarlarla okunur: `logo_*`, `page_*`, `contact_email`, `src_run`, `site_url`. `cron_key` asla dönmez.
- Dosya tabanlı hız sınırı (yönetici girişindeki yöntemle aynı): IP başına dakikada ~60 genel istek, asistan ve arama için ~20. Aşılırsa `429` ve `Retry-After` döner.
- Girdi doğrulama: `q` en çok 120 karakter (sitede de 120), sayfa ve limit sınırları, slug yalnızca `[a-z0-9-]`, tarih yalnızca `YYYY-MM-DD`.
- Tüm hatalar sabit Türkçe mesajla döner; ayrıntı yalnızca sunucu günlüğüne yazılır.
- Veritabanı bağlantı hatasında `inc.php` düz metin basıp çıkıyor. API kendi hata yakalayıcısıyla `503` ve JSON dönecek; mevcut `db()` değişmeyecek.
- CORS kapalı: Android uygulaması tarayıcı olmadığı için CORS'a gerek yok.
- `api/` altına site kökündekine benzer bir `.htaccess` gelir: dizin listeleme kapalı, yalnızca `index.php` çalışır.

---

## 6. Android uygulaması: ekran ve özellik listesi (gerçek veriye göre)

| Ekran | İçerik | Veri notu |
|---|---|---|
| Ana Sayfa | Logo, arama kutusu, marketler, öne çıkan ürünler (sitedeki başlık ve gerekçesiyle), güncel kataloglar, öne çıkan karşılaştırma, kategoriler, yaklaşan kataloglar | Sitedeki modern ana sayfanın birebir verisi |
| Ara | Yazarken öneri (300 ms bekleme), sonuç listesi, filtreler (market, kategori, fiyat aralığı, katalog durumu), sıralama, "bunu mu demek istedin" | `/suggest`, `/products`, `/search` |
| Ürün Detay | Ürün görseli (**varsa**; yoksa market renginde yer tutucu), ad, kategori, fiyat, eski fiyat (**varsa**), birim fiyat, fiyat geçmişi satırı, kampanya tarihi, durum, aynı ürünün diğer marketlerdeki fiyatları, broşürde gör, favori | Çoğu üründe görsel ve eski fiyat **yok**; uydurulmayacak |
| Karşılaştır | Grup listesi; en düşük fiyat yeşil, fark ve % tasarruf; ayrı sekmede "Benzer ürünlerde birim fiyat" (**"farklı ürünler"** etiketiyle) | Sitedeki açıklama metinleri korunacak |
| Kataloglar | Aktif, yaklaşan ve arşiv sekmeleri, market filtresi | |
| Katalog Detay | Broşür sayfaları: kaydırma ve yakınlaştırma; küçük resim şeridi (t/), tam boyut asıl görsel; kategoriye göre ürünler | Yüksek kalite görsel için asıl dosya |
| Marketler / Market Detay | Logo, renk, katalogları | |
| Asistan | Sohbet balonları, "yazıyor…" göstergesi, ürün kartları, hızlı yanıt düğmeleri, oturum içi sohbet geçmişi, önceki soruyu hatırlama ("peki A101'de?") | Sitedeki adla: **"Akıllı Ürün Asistanı"**. Kural tabanlı olduğu, sitedeki gibi dürüstçe belirtilecek |
| Favoriler | Yerel (Room). Ürün süresi dolunca "kampanya sona erdi" etiketi; aynı ürün yeni katalogda varsa gösterilir | Bkz. bölüm 3 |
| Profil / Ayarlar | Hakkında, gizlilik, koşullar (sunucudan), önbelleği temizle, bildirim ayarı, sürüm | |

**Alt menü:** Ana Sayfa · Ara · Kataloglar · Karşılaştır · Asistan. Favoriler ve Ayarlar üst çubuktan açılır.

**Bildirimler hakkında:** Talimatta yalnızca "bildirim ayarları" geçiyor. Gerçek push bildirimi için Firebase gerekir; bu ayrı bir karar. İlk sürüm için öneri: WorkManager ile "yeni katalog yayımlandı" **yerel** bildirimi. Firebase gerekmez, sunucuya ek yük getirmez.

---

## 7. Değiştirilecek ve oluşturulacak dosyalar

### 7.1 Sunucu: yeni dosyalar (canlıya yüklemeden önce ayrıca onayınız alınacak)

```
public_html/marketler.org/api/
  .htaccess                 # yalnızca v1/index.php çalışır, dizin listeleme kapalı
  v1/
    index.php               # yönlendirici: /api/v1/<kaynak>/<id>
    bootstrap.php           # inc.php + eslestirme.php + asistan-motor.php yükler, JSON hata yakalayıcı
    response.php            # ok()/fail() standart yanıt, sayfalama meta
    ratelimit.php           # dosya tabanlı hız sınırı
    urls.php                # mutlak site/görsel URL üretimi (bpath sorununa karşı)
    home.php  markets.php  catalogs.php  categories.php  products.php
    search.php  suggest.php  compare.php  assistant.php  pages.php  meta.php
```

### 7.2 Sunucu: mevcut dosyalarda önerilen değişiklikler (her biri ayrı onay)

| Dosya | Değişiklik | Neden | Zorunlu mu? |
|---|---|---|---|
| `tema-modern-ana.php` → yeni `ortak-veri.php` | `mt_featured()` ve `MT_MIN_GAIN` **içeriği değişmeden** taşınır; eski dosya `require_once` ile yükler | Öne çıkan ürünlerin web ile birebir aynı olması, kod tekrarının önlenmesi | Ana sayfadaki "öne çıkanlar" için evet |
| `.htaccess` | `error_log` dosyasını kapatma | Güvenlik (G1) | Hayır, önerilir |

**Veritabanı değişikliği gerekmiyor.** `ALTER`, `DROP`, `DELETE` ya da `UPDATE` yok. API yalnızca okuyacak.

### 7.3 Depo ve teslim yapısı

```
MarketlerAndroid/
  android/   app/ core/(network, database, designsystem, common) domain/ data/ feature/(home, search, product, compare, catalogs, markets, assistant, favorites, settings)
  server/    api/v1/... (yukarıdaki dosyaların kaynak kopyası)  tests/ (PHP birim + uç nokta testleri)  README.md
  docs/      API-DOCUMENTATION.md  DATABASE-MAPPING.md  ANDROID-ARCHITECTURE.md  TEST-REPORT.md  INSTALLATION.md
```

Depodaki mevcut WebView projesi de `MarketlerAndroid/` klasöründe. Öneri: onu `MarketlerWebView/` olarak yeniden adlandırıp saklamak. *Onayınız gerekir.*

### 7.4 Geri dönüş planı
- **API:** Tamamen ayrı bir klasörde (`api/`). Sorun çıkarsa klasörü silmek veya yeniden adlandırmak yeterli; site etkilenmez.
- **`ortak-veri.php` taşıması:** Yüklemeden önce `tema-modern-ana.php` dosyasının yedeği alınır. Geri almak için eski dosyayı geri yüklemek yeterli (ayrıca tema ayarından "Klasik" seçmek de modern sayfayı anında devre dışı bırakır).
- **Veritabanı:** Değişiklik olmadığı için geri alınacak bir şey yok.

---

## 8. Sonraki fazlar için ihtiyaçlar ve açık sorular

1. **Test verisi (FAZ 2 için kritik):** Web ile API sonuçlarının aynı olduğunu kanıtlamam için gerçek veriye ihtiyacım var. phpMyAdmin'den **yalnızca şu tabloların "yapı + veri" dökümü** yeterli: `stores`, `catalogs`, `items`, `categories`, `item_media`, `product_links`, `link_blocks`. **`users`, `settings`, `admin_log`, `logs` dahil edilmemeli.** Bu tablolarda kişisel veri bulunmuyor. Bu verilerle API'yi kendi ortamımda MariaDB üzerinde çalıştırıp siteyle karşılaştırabilirim.
2. **PHP ve veritabanı sürümü:** cPanel ana sayfasında "PHP sürümü" ve "MySQL/MariaDB sürümü" yazar. Teyit lütfen.
3. **`products` ve `prices` tabloları:** Hiçbir kod kullanmıyor. İçlerinde veri var mı? API bunlara dokunmayacak.
4. **Cron sıklığı:** `cron.php` cPanel'de hangi aralıkla çalışıyor? Uygulamanın arka plan yenileme aralığı buna göre ayarlanacak.
5. **APK derlemesi (FAZ 8):** Bu bulut ortamı Google'ın Android sunucularına erişemiyor. Derlemeyi sizin bilgisayarınızda (Android Studio çalıştığını gördük) ya da GitHub Actions üzerinden yaparız.
6. **Marka renkleri:** Talimatta "mavi" deniyor; sitenin modern teması `#172554` (koyu lacivert) ve `#2da8ff` (mavi) kullanıyor. Bu tonlarla ilerlemeyi öneriyorum.

---

## 9. FAZ 1'e geçmek için onay istenen kararlar

- [ ] Bölüm 2'deki uç nokta listesi uygun mu?
- [ ] `mt_featured()` fonksiyonunun `ortak-veri.php` dosyasına taşınması (7.2) onaylanıyor mu?
- [ ] `.htaccess` dosyasına `error_log` kuralı eklenmesi (G1) isteniyor mu? (Canlıya siz yüklersiniz.)
- [ ] Favorilerin "market + ürün adı" anahtarıyla da saklanması (bölüm 3) uygun mu?
- [ ] İlk sürümde Firebase yerine yerel bildirim yeterli mi?
- [ ] Mevcut WebView projesinin `MarketlerWebView/` olarak yeniden adlandırılması uygun mu?
- [ ] Test verisi dökümü (8.1) gönderilebilir mi?

Onayınızdan sonra FAZ 1'de API kodu **yalnızca depoda** yazılacak. Canlıya yükleme, FAZ 2 testleri geçtikten ve değişiklik listesini onayladıktan sonra yapılacak.
