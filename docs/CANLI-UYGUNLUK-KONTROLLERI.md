# FAZ 1 Canlı Ortam Uygunluk Kontrolleri: Ayrıştırma ve Güvenli Doğrulama Planı

**Tarih:** 25 Kasım 2026.

**Durum:** Yalnızca inceleme ve plan.
- Canlı siteye dosya yüklenmedi, PHP dosyası değiştirilmedi, veritabanına dokunulmadı.
- Canlı ortamda SQL çalıştırılmadı, API dağıtılmadı.
- Canlı API **kapalı kalıyor** (karar 14).

**Kabul edilen temel sürüm:** FAZ 2.2.2 düzeltme 1. Kod `ac50096`, git etiketi `faz2.2.2-d1-kabul` (karar 13).

## 0. "K1–K7" adlandırması hakkında
Proje belgelerinde **K1–K7 diye numaralanmış bir liste yok.** FAZ 1'de açık kalan canlı ortam kontrolleri `FAZ1-SON-DEGERLENDIRME.md` dosyasında şöyle kayıtlı:
- **B1–B11:** bekleyen kontroller;
- **3.1–3.7:** gerçek sunucuda doğrulanması gereken riskler.

Aşağıdaki **K1–K7**, bu maddelerin canlı ortam konularına göre **benim önerdiğim gruplamasıdır.** Sizin elinizde farklı bir K1–K7 listesi varsa gönderin, birebir eşleştireyim.

| K | Konu | Kapsadığı FAZ 1 maddeleri |
|---|---|---|
| K1 | PHP sürümü ve API'nin bu sürümle uyumu | B5, risk 3.5 |
| K2 | PHP eklentileri ve PHP ayarları | B6 |
| K3 | Veritabanı sunucusu, şema durumu, kullanıcı yetkileri | B7, B11, risk 3.1 |
| K4 | Sunucudaki `config.php` biçimi | B8 |
| K5 | Cloudflare: vekil, SSL modu, gerçek IP, IP listesi | B9, risk 3.2, risk 3.3 |
| K6 | Geçici dizinin yazılabilirliği | B10, risk 3.4 |
| K7 | Sunucudaki site dosyalarının incelenen kopyayla aynı olması; `.htaccess` ve yönlendirme | Yeni: Rev3'ün dayandığı dosyalar |
| (D.3) | Gerçek veri sayımları (T1–T4, T5) | B1–B4 |

## 1. Özet: hangisi nereden doğrulanabilir?
"Elimizdeki kanıt", kaynak kod ve **daha önce gönderdiğiniz site paketindeki** dosyalardır: `php.ini`, `error_log`, phpMyAdmin dökümünün **yalnızca başlık satırları.** Dökümün veri içeriği okunmadı.

| K | Kaynak koddan / elimizdeki kanıttan | Sizden gereken bilgi | Durum |
|---|---|---|---|
| K1 | **Büyük ölçüde doğrulandı:** site PHP **8.4** üzerinde çalışıyor (kanıt 2.1). API'de 8.4'te kaldırılan veya uyarı veren yapı bulunamadı | MultiPHP Manager'daki sürümün ekran görüntüsü (teyit) | 🟡 Teyit bekliyor |
| K2 | **Kısmen doğrulandı:** `pdo_mysql`, `mbstring`, `json`, `ctype` sitenin çalışan kodunda zaten kullanılıyor, yani sunucuda var. `filter`, `inet_pton` ve `flock` yalnızca API kullanıyor; PHP'nin varsayılan parçaları ama doğrulanmadı | PHP eklenti ekranı + `open_basedir`, `sys_temp_dir` değerleri | 🟡 Bilgi gerekli |
| K3 | **Kısmen:** MariaDB **10.11.19** (döküm başlığı); yerel testlerimiz de 10.11 ile yapıldı. `schema_v` koddan: site her sayfa açılışında şemayı 5'e getiriyor; API de 5 bekliyor | Veritabanı kullanıcısının ayrıcalık listesi (cPanel ekranı). Ayrı salt okunur kullanıcı kararı | 🟡 Bilgi + karar gerekli |
| K4 | **Kısmen:** Gönderilen kopyanın biçimi API ile uyumlu (FAZ 1'de denendi) | Sunucudaki dosyanın **biçiminin** görüntülenmesi (değerler karalanarak) | 🟡 Bilgi gerekli |
| K5 | **Dolaylı ipucu:** sitenin kök `.htaccess` dosyası `X-Forwarded-Proto` ve `CF-Visitor` başlıklarına göre HTTPS yönlendirmesi yapıyor; site büyük olasılıkla Cloudflare arkasında. **Doğrulanamadı.** Cloudflare'in resmî IP listesine bu ortamdan erişim ağ kuralıyla engelli | Cloudflare DNS (turuncu/gri bulut), SSL modu, `mod_remoteip` / LiteSpeed gerçek IP ayarı | 🔴 Bilgi gerekli |
| K6 | **Dolaylı:** Site önbellekleri zaten aynı geçici dizini kullanıyor; sunucu CloudLinux (`cll-lve`), kullanıcıya özel `/tmp` normalde yazılabilir. **Gerçek yazma denemesi yapılamaz** (dosya yüklemek gerekir) | `open_basedir`, `sys_temp_dir`, `upload_tmp_dir` değerleri (ekran) | ⛔ Kesin doğrulama ancak dağıtım onayıyla |
| K7 | **Kısmen:** Kök `.htaccess` dosyasında `/api/` yolunu yakalayan genel kural yok. Sitenin kendi yönlendirme kuralları çalıştığına göre `mod_rewrite` ve `.htaccess` desteği etkin | Sunucudaki dosya **boyutlarının** listeyle karşılaştırılması (bölüm 3, Adım 5) | 🟡 Bilgi gerekli |
| D.3 | Sorgular hazır ve yerelde denendi | Bu aşamada **çalıştırılmayacak** (canlı ortamda SQL yasak) | ⏸ Ertelendi |

## 2. Elimizdeki kanıtlar (ayrıntı)

### 2.1 K1: PHP sürümü
- **Sitenin hata günlüğü** (`error_log`, 22 Kasım) şu biçimde 4 ölümcül hata içeriyor: `Cannot redeclare function as_fold()`.
  - Bu ileti biçimi ("**function** as_fold()") **PHP 8.4** ile geldi.
  - Buradaki yerel PHP 8.3.6 aynı hatayı `Cannot redeclare a()` diye yazıyor (denendi).
  - Sonuç: site 22 Kasım'da PHP ≥ 8.4 ile çalışıyordu.
- **phpMyAdmin dökümü başlığı:** "PHP Sürümü: 8.4.28".
  - Bu phpMyAdmin'in PHP'sidir; sitenin sürümü MultiPHP Manager'da ayrıca seçilebilir.
  - Yukarıdaki hata iletisiyle birlikte güçlü bir ipucu.
- **API kodu (Rev3), belirteç tabanlı tarama:**
  - 7.4/8.x'e özgü yapı yok.
  - PHP 8.4'te uyarı veren "örtük nullable parametre" yok.
  - 8.2'de uyarı veren `"${}"` yok.
  - Yerel PHP 8.3.6 ile bütün dosyalar sözdizimi denetiminden geçti.
- **Doğrulanamayan:** API test paketi PHP **8.4** ile çalıştırılmadı. Bu ortamda yalnızca 8.3.6 var; 8.4 kurmak için ek paket kaynağına ağ izni gerekir.

### 2.2 K2: eklentiler
| Eklenti / fonksiyon | API kullanıyor | Site zaten kullanıyor (sunucuda var) |
|---|---|---|
| PDO + `pdo_mysql` | ✔ | ✔ `inc.php` |
| `mbstring` | ✔ | ✔ birçok dosya |
| `json` | ✔ | ✔ |
| `ctype` | ✔ | ✔ `inc.php`, `eslestirme.php` |
| `filter` (`filter_var`) | ✔ | ✘ kullanılmıyor → doğrulanmadı |
| `inet_pton` (IPv6) | ✔ | ✘ → doğrulanmadı |
| `flock` | ✔ (hız sınırı) | ✘ → doğrulanmadı |
| `sys_get_temp_dir` | ✔ | ✔ `asistan.php`, `tema-modern-ana.php` |

Sitedeki `php.ini` yalnızca günlük ayarlarını içeriyor; `open_basedir` veya geçici dizin ayarı yok.

### 2.3 K3: veritabanı
- **Sunucu:** `10.11.19-MariaDB-cll-lve` (döküm başlığı). FAZ 1 testleri MariaDB 10.11 ile yapıldı; aynı ana sürüm.
- **`schema_v`:** API `5` bekliyor. Sitenin `inc.php` dosyası değer 5 değilse her sayfa açılışında şemayı günceller (satır 111–121). Sunucudaki `inc.php` incelenen kopyayla aynıysa değer 5'tir; bu, K7'ye bağlı.
- **Risk 3.1 (yetki):** API, sitenin yazma yetkili kullanıcısını kullanıyor. Salt okunurluk kod ve oturum kilidiyle sağlanıyor. Ayrı `SELECT` kullanıcısı kod değişikliği gerektirir; **karar sizde** (açık konu).

### 2.4 K5: Cloudflare
- Kök `.htaccess` dosyası `X-Forwarded-Proto` ve `CF-Visitor` başlıklarını tanıyor.
- Sunucu CloudLinux; bu tür barındırmada genellikle **LiteSpeed** web sunucusu bulunur. LiteSpeed gerçek ziyaretçi IP'sini kendi ayarıyla çözer. API bu durumu da destekliyor (`REMOTE_ADDR` ziyaretçi adresi olur), ama hangi yolun etkin olduğu bilinmiyor.
- Cloudflare IP listesi (risk 3.3): `cloudflare.com` bu ortamın ağ kuralıyla engelli; karşılaştırma yapılamadı.

### 2.5 K7: dosyalar ve `.htaccess`
- API'nin sunucuda dayandığı mevcut site dosyaları: **`inc.php`, `asistan-motor.php`, `config.php`.**
- Rev3 ile gelen ve değişen dosyalar: `ortak-veri.php` (yeni), `tema-modern-ana.php`, `ara.php`.
- Kök `.htaccess`:
  - HTTPS ve `www` yönlendirmesi var.
  - Yalnızca `katalog/`, `market/` ve `sitemap.xml` için kural var; `/api/` için bir engel yok.
  - API'nin kendi `api/.htaccess` dosyası `mod_rewrite` ister. Site zaten yeniden yazma kuralı kullandığına göre bu modül etkin.

### 2.6 Ek bulgular (canlı ortam güvenliği; işlem yapılmadı, karar sizde)
1. **`error_log` dosyası web kökünde ve herkese açık olabilir.** Kök `.htaccess` dosyasındaki engelleme kalıbı `.log` uzantısını kapsıyor. Ancak dosyanın adı `error_log`, uzantısı yok; bu yüzden kalıba uymuyor. Dosya sunucu klasör yollarını içeriyor. Öneri: bu dosyanın `https://marketler.org/error_log` adresinden açılıp açılmadığına tarayıcıdan bakın.
2. **Web kökünde veritabanı dökümü:** Gönderilen site paketinde `bayirtkf_aktuel (4).sql` vardı; dosya kullanıcı verisi içeriyor. Sunucuda da duruyorsa yalnızca `.htaccess` dosyasındaki `.sql` engeli onu koruyor. Öneri: sunucuda varsa `public_html` dışına taşınması (sizin işleminiz).
3. **22 Kasım'daki `as_fold()` ölümcül hataları:** `asistan.php`, `asistan-motor.php` dosyasını düz `require` ile yüklüyor. Aynı istekte dosya daha önce yüklenmişse site sayfası çöküyor.
   - API bu dosyayı `require_once` ile yükler; **API etkilenmez.**
   - Hatanın bugün tekrarlanıp tekrarlanmadığı sunucudaki güncel günlükten görülmeli.
   - Site koduna dokunulmadı.

## 3. Güvenli doğrulama adımları (sizin için; hepsi yalnızca görüntüleme)
> Hiçbir adımda dosya yüklenmez, dosya düzenlenmez, SQL çalıştırılmaz, ayar değiştirilmez. Ekran görüntülerinde **parola, kullanıcı adı, IP ve e-posta** bilgilerini karalayın.

**Adım 1 (K1):** cPanel → **MultiPHP Manager** → `marketler.org` satırındaki PHP sürümü.

**Adım 2 (K2, K6):** cPanel → **Select PHP Version** → **Extensions** sekmesi. Yalnızca bakın: `pdo_mysql` / `nd_pdo_mysql`, `mbstring`, `json`, `ctype`, `filter` işaretli mi? Sonra **Options** sekmesinde şu değerlere bakın: `open_basedir`, `sys_temp_dir`, `upload_tmp_dir`. Boş olmaları da bir bilgidir.

**Adım 3 (K3):** cPanel → **MySQL Veritabanları** → `bayirtkf_aktuel` satırındaki kullanıcının ayrıcalık listesi. Düğmelere basmayın. Kullanıcı adını karalayın.

**Adım 4 (K4):** cPanel → **Dosya Yöneticisi** → `public_html/marketler.org/config.php` → **Görüntüle** (düzenle değil). Yalnızca biçim: dört satır `define('DB_…','…');` mı, değerler tek tırnak içinde mi? **Değerleri karalayın.**

**Adım 5 (K7):** Aynı Dosya Yöneticisinde, **dosyaları açmadan**, liste görünümündeki **boyutları** gönderin. Beklenen boyutlar, gönderdiğiniz kopyanınkiler:

| Dosya | Gönderilen kopyadaki boyut |
|---|---|
| `inc.php` | 43.764 bayt |
| `asistan-motor.php` | 21.208 bayt |
| `eslestirme.php` | 12.934 bayt |
| `tema-modern-ana.php` | 11.440 bayt |
| `ara.php` | 5.620 bayt |
| `asistan.php` | 11.109 bayt |
| `.htaccess` | 2.296 bayt |

Boyut farkı, dosyanın kopya alındıktan sonra değiştiğini gösterir. O durumda Rev3 yeniden kontrol edilmelidir. Boyut aynıysa dosya büyük olasılıkla aynıdır (kesin değil). Kesinlik isterseniz cPanel'den yalnızca o dosyaları indirip gönderebilirsiniz; SHA-256 ile karşılaştırırım.

**Adım 6 (K5):** Cloudflare paneli:
- **DNS** → `marketler.org` kaydının bulutu turuncu mu, gri mi?
- **SSL/TLS → Overview** → mod: Off / Flexible / Full / Full (strict)?
- Barındırma firmasına sorulacak soru: "Sunucu Apache mi, LiteSpeed mi? Cloudflare gerçek IP (`mod_remoteip` veya LiteSpeed 'Use Client IP in Header') etkin mi?"

**Adım 7 (ek bulgular):**
- Tarayıcıda `https://marketler.org/error_log` adresini açmayı deneyin: 403/404 mü, yoksa dosya içeriği mi görünüyor?
- Dosya Yöneticisinde `public_html/marketler.org` içinde `.sql` uzantılı dosya var mı? Yalnızca bakın.
- cPanel → **Errors** (veya `logs/php.error.log`): son günlerde `as_fold` hatası tekrarlanıyor mu?

**Adım 8 (risk 3.3, isteğe bağlı):** Cloudflare'in resmî IP listesini karşılaştırabilmem için iki seçenek var:
- (a) Bu ortamın ağ iznine `www.cloudflare.com` eklenir; sizin onayınızla, yalnızca okuma.
- (b) https://www.cloudflare.com/ips-v4 ve https://www.cloudflare.com/ips-v6 sayfalarının içeriğini kopyalayıp gönderirsiniz.

## 4. Bu aşamada yapılmayacaklar ve neden
| Kontrol | Neden | Ne zaman |
|---|---|---|
| D.3 sayımları (T1–T4) ve boş veri sayımı (T5) | Canlı ortamda SQL çalıştırma yasak (bu aşamanın kuralı) | Ayrı onayla; sorgular `docs/sql/` ve `FAZ2.2-UYUMLULUK-RAPORU.md` içinde hazır, yalnızca `SELECT` |
| K6 gerçek yazma denemesi | Sunucuya dosya yüklemek gerekir | Dağıtım onayıyla, geçici alt klasörde, sonra silinerek |
| API'nin PHP 8.4 ile test edilmesi | Bu ortamda PHP 8.4 yok; kurmak ek ağ izni ister | Onaylarsanız bu ortamda yapılabilir (canlıya dokunmadan) |
| Canlı API'nin açılması | Karar 14: bu kontroller tamamlanmadan açılmaz | K1–K7 sonuçlandıktan ve ayrı onaydan sonra |

## 5. Canlıya alma için ön koşul özeti (bilgi amaçlı, karar değil)
API'nin açılabilmesi için en az şunlar gerekir:
- K1: PHP 8.4 teyidi; tercihen yerelde PHP 8.4 ile API testlerinin geçmesi.
- K2: eklentiler ve geçici dizin ayarları.
- K4: `config.php` biçimi.
- K5: SSL modu "Full" veya "Full (strict)"; gerçek IP çözümü.
- K7: dosyaların aynı olduğu.

K3'teki ayrı veritabanı kullanıcısı ve D.3 sayımları sizin kararınıza bağlıdır.
