# FAZ 1: Son Doğrulama ve Değerlendirme Raporu

**Tarih:** 23–24 Kasım 2026
**Kod durumu:** Dondurulmuş. Revizyon 3 (`e95404e`, rapor `5f73ebb`). Bu çalışmada **hiçbir kod, API dosyası, veritabanı veya üretim dosyası değiştirilmedi.**
**Sonuç (özet):** İstenen kontrollerin büyük bölümü **bu ortamdan yapılamadı.** Nedeni erişim yokluğu; ayrıntılar bölüm 0'da. Tahmin yürütülmedi. **FAZ 1 henüz kapatılamaz** (bölüm 4).

---

## 0. Erişim durumu (neden yapılamadı)

| Kaynak | Bu ortamdan erişim | Kanıt |
|---|---|---|
| Gerçek veritabanı (`bayirtkf_aktuel`) | **Yok.** Bağlantı bilgisi yok (gönderilen `config.php` kopyasında parola `GIZLI` olarak maskeli); cPanel/phpMyAdmin erişimi yok | – |
| Sunucu (cPanel, dosyalar, PHP) | **Yok.** SSH, FTP ya da cPanel erişimi yok | – |
| `https://marketler.org` (herkese açık site) | **Yok.** Ortamın ağ politikası engelliyor | `CONNECT tunnel failed, response 403 (agent proxy: no rule or network setting allows this host)` |
| Cloudflare paneli | **Yok** | – |

Erişim için ortam ayarı değişikliği daha önce reddedildiği için yeniden istenmedi. Bu nedenle aşağıdaki kontrollerin çoğu **sizin uygulayacağınız, yalnızca okuma yapan adımlar** olarak hazırlandı (bölüm 5). Sonuçları ekran görüntüsü olarak gönderdiğinizde bu rapor doldurulacak.

---

## 1. Tamamlanan kontroller

| # | Kontrol | Sonuç | Sınır |
|---|---|---|---|
| 1 | **D.3 sorgularının belirlenmesi** | Dört sorgu (T1–T4) `docs/sql/D3-sayim-sorgulari.sql` dosyasında. Yalnızca `SELECT` içerdikleri otomatik olarak tarandı (yorumlar hariç 0 yazma anahtar kelimesi). Yerel MariaDB 10.11'de boş şema ve sentetik veriyle hatasız çalıştılar | Gerçek veritabanında **çalıştırılmadı** |
| 2 | **`config.php` biçiminin API ile uyumu** | Gönderilen kopya 5 satır: `define('DB_HOST','…'); define('DB_NAME','…'); define('DB_USER','…'); define('DB_PASS','…');`. API'nin `mk_read_db_config()` fonksiyonu (dosyayı çalıştırmadan okur) dört anahtarın dördünü de okudu | Yalnızca 22–23 Kasım'da gönderilen, **parolası maskeli kopya** denendi. Sunucudaki güncel dosya görülmedi. Değerlerde tek tırnak veya ters bölü varsa da desteklenir (kurulum betiğinin `var_export` biçimi) |
| 3 | **API'nin ihtiyaç duyduğu PHP eklentilerinin belirlenmesi** (koddan) | `pdo_mysql`, `mbstring`, `json`, `ctype`, `filter`. Ayrıca standart `inet_pton` (IPv6 desteği) ve `PHP ≥ 7.4` sözdizimi. Sitenin kendisi ek olarak `gd` ve `curl` kullanıyor (cron/admin) | Sunucuda **yüklü olup olmadıkları doğrulanmadı** |
| 4 | **API'nin veritabanı yetki ihtiyacı** (yerel test) | API'nin yalnızca `SELECT` yetkisiyle eksiksiz çalıştığı yerelde kanıtlandı (rev3, T-RO-4) | Gerçek kullanıcının yetkileri **görülmedi** |

## 2. Başarısız veya bekleyen kontroller

| # | Kontrol | Durum | Neden | Nasıl tamamlanır (bölüm 5) |
|---|---|---|---|---|
| B1 | **T1:** `products`/`prices` kayıt sayısı + veritabanı sürümü | ⏳ Bekliyor | Veritabanı erişimi yok | Adım 1 |
| B2 | **T2:** yayındaki kalem / anahtar / tekrar sayıları | ⏳ Bekliyor | Aynı | Adım 1 |
| B3 | **T3:** aynı market+hash altında farklı ad yazımı | ⏳ Bekliyor | Aynı | Adım 1 |
| B4 | **T4:** market bazında tekrar oranı | ⏳ Bekliyor | Aynı | Adım 1 |
| B5 | **PHP sürümü** | ⏳ Bekliyor | Sunucu erişimi yok | Adım 2 |
| B6 | **PHP eklentileri** (`pdo_mysql`, `mbstring`, `json`, `ctype`, `filter`) | ⏳ Bekliyor | Aynı | Adım 2 |
| B7 | **MySQL/MariaDB sürümü** | ⏳ Bekliyor | Aynı | Adım 1 (T1) veya cPanel ana sayfası |
| B8 | **Sunucudaki `config.php`** biçimi | ⏳ Kısmen | Yalnızca gönderilen kopya denetlendi | Adım 4 |
| B9 | **Cloudflare proxy ve SSL modu** | ⏳ Bekliyor | Cloudflare ve site erişimi yok | Adım 3 |
| B10 | **Geçici dizinin yazma izni** | ⛔ **Bu aşamada yapılamaz** | Yazılabilirlik ancak sunucuda PHP kodu çalıştırılarak denenebilir; bu da üretime dosya yüklemek demek ve şu an yasak. cPanel ekranları yalnızca ayar değerini gösterir, yazılabilirliği göstermez | Canlıya alma aşamasında, ayrı onayla, geçici bir alt klasörde (bkz. 3.4) |
| B11 | **Veritabanı kullanıcısının yetkileri** | ⏳ Bekliyor | Erişim yok | Adım 1 (cPanel "MySQL Veritabanları" ekranı) |

## 3. Gerçek sunucuya ilişkin riskler

Bunlar **gerçek sunucuda tespit edilmiş** değil; kod ve tasarımdan çıkan, gerçek sunucuda **doğrulanması gereken** risklerdir. Gerçek sunucuda tespit yapılamadı (bölüm 0).

| # | Risk | Etki | Doğrulama |
|---|---|---|---|
| 3.1 | **API, sitenin kendi veritabanı kullanıcısını kullanıyor.** Bu kullanıcının yazma yetkisi var, çünkü yönetici paneli ve `cron.php` aynı kullanıcıyla yazıyor. API'nin salt okunurluğu **kullanıcı yetkisiyle değil**; `schema_v` ön denetimi, oturum düzeyinde `READ ONLY` kilidi ve kod kuralıyla sağlanıyor (yerelde kanıtlandı). | Yetki düzeyinde ikinci bir güvence yok | Daha güçlü seçenek: API'ye ayrı, yalnızca `SELECT` yetkili bir veritabanı kullanıcısı. Bu **kod değişikliği** gerektirir (API şu an yalnızca sitenin `config.php` dosyasını okuyor); kod donmuş olduğu için **karar size bırakıldı** |
| 3.2 | **Cloudflare SSL modu "Flexible" ve sunucuda `mod_remoteip` etkinse** API bütün istekleri `400 HTTPS_REQUIRED` ile reddeder | API hiç çalışmaz | Adım 3. "Full" veya "Full (strict)" modunda sorun yok |
| 3.3 | **Cloudflare IP listesi** geliştiricinin bilgisinden yazıldı; doğrulanmadı | Liste eskiyse hız sınırı bazı ziyaretçileri tek IP gibi sayabilir; HTTPS algılaması bazı Cloudflare sunucularında başarısız olabilir | Canlı öncesi https://www.cloudflare.com/ips/ ile karşılaştırma |
| 3.4 | **Geçici dizin yazılamazsa** API, bilinçli tasarım gereği `503` döner (hız sınırı sessizce kapanmaz). Sitenin öneri ve öne çıkanlar önbelleği de aynı dizini kullanır | API kullanılamaz | Canlıya alma sırasında, ayrı onayla, alt klasörde deneme |
| 3.5 | **PHP sürümü 7.4'ten eskiyse** API çalışmaz. **8.4 ve sonrası** yalnızca söz dizimi taramasıyla değerlendirildi | API yüklenmez veya beklenmeyen uyarı | Adım 2 |
| 3.6 | **`cmp_groups()` eşitlik sırası** (`inc.php`, mevcut) | Karşılaştırma önbelleği yenilenince eşit kayıtların sırası değişebilir; site ve API tutarlı kalır | Rev3 raporu, risk 3 (karar sizde) |
| 3.7 | **`items.hash` kararlılığı** | Favori adaylarının ne sıklıkla bulunacağı bilinmiyor | T2–T4 |

## 4. Son değerlendirme

### FAZ 1 kapatılabilir mi?
**Hayır, henüz değil.** Sizin koyduğunuz kapanış koşulları:
- D.3 gerçek veri sonuçları: **bekleniyor** (B1–B4).
- Gerçek sunucu uyumluluğu: **bekleniyor** (B5–B9, B11).
- Bağımsız kaynak kod incelemesi: **sizde.**

Bölüm 5'teki adımların sonuçları geldiğinde:
- Engel çıkmazsa rapor güncellenir ve FAZ 1 kapatılabilir.
- B10 (geçici dizin) yalnızca canlıya alma aşamasında doğrulanabilir. Bu yüzden FAZ 1 kapanışı için **bilinen sınırlama** olarak kaydedilmesini öneriyorum; engel olarak değil.

### Android geliştirmeye geçmek için engel var mı?
- **Teknik engel yok** (yerel geliştirme için). Uygulama, belgelenmiş API sözleşmesine (`FAZ1-UC-NOKTALAR.md`) göre ve sentetik/sahte veriyle geliştirilebilir. Sunucudaki belirsizlikler (PHP sürümü, Cloudflare modu, geçici dizin) uygulama kodunu değil, **API'nin canlıya alınmasını** etkiler.
- **Süreç engeli var:** Kararınız "FAZ 1 kapandıktan sonra Android". FAZ 1 henüz kapanmadı.
- **Gerçek veriyle uçtan uca test** için API'nin bir sunucuda çalışması gerekir. Bu da canlıya alma onayı ister; şu an böyle bir onay yok.

---

## 5. Sizin uygulayacağınız kontroller (hepsi YALNIZCA OKUMA)

> Hiçbir adımda dosya, tablo, kayıt veya ayar değiştirilmez. Sonuçların ekran görüntüsünü gönderirken **parola, e-posta, IP adresi gibi bilgileri karalayın.** Sorguların hiçbiri kişisel veri döndürmez.

**Adım 1: Veritabanı (phpMyAdmin ve cPanel)**
1. cPanel → **phpMyAdmin** → soldan `bayirtkf_aktuel` → üstte **SQL** sekmesi.
2. `docs/sql/D3-sayim-sorgulari.sql` dosyasındaki dört sorguyu yapıştırıp **Git**'e basın. Dört sonuç tablosunun ekran görüntüsünü alın.
   - **T1:** `products` ve `prices` kayıt sayıları + veritabanı sürümü (B1, B7).
   - **T2:** kalem / anahtar / tekrar sayıları (B2).
   - **T3:** aynı market+hash altında farklı yazım sayısı; `farkli_yazim` 0 beklenir (B3).
   - **T4:** market bazında tekrar oranı. Yalnızca market adı (BİM/A101/ŞOK) ve sayılar döner (B4).
3. cPanel → **MySQL Veritabanları** sayfası → "Mevcut Veritabanları" tablosunda `bayirtkf_aktuel` satırındaki **kullanıcı adı** ve o kullanıcının **ayrıcalık (privileges)** listesi. Kullanıcı adını karalayabilirsiniz (B11). Bu sayfa yalnızca görüntüleme içindir; hiçbir düğmeye basmayın.

**Adım 2: PHP (cPanel)**
1. cPanel → **MultiPHP Manager** → `marketler.org` satırındaki **PHP sürümü** (B5).
2. cPanel → **Select PHP Version** (ya da **PHP Extensions**). Bu ekranda yalnızca bakın, kutucuklara dokunmayın. Şu eklentilerin işaretli olup olmadığına bakın: `pdo_mysql` (veya `nd_pdo_mysql`), `mbstring`, `json`, `ctype`, `filter` (B6). Bazı sürümlerde `json`, `ctype` ve `filter` çekirdekte yerleşiktir ve listede görünmeyebilir; bunu da not edin.

**Adım 3: Cloudflare**
1. Cloudflare paneli → `marketler.org` → **DNS**: `marketler.org` kaydının bulutu **turuncu** (proxied) mi, gri mi?
2. **SSL/TLS → Overview**: mod **Off / Flexible / Full / Full (strict)** hangisi? (B9, risk 3.2)
3. Biliyorsanız ya da hosting desteğine sorabilirseniz: sunucuda **`mod_remoteip`** veya "Cloudflare / gerçek IP" eklentisi etkin mi?

**Adım 4: Sunucudaki `config.php` biçimi (B8)**
cPanel → **Dosya Yöneticisi** → `public_html/marketler.org/config.php` → **Görüntüle** (düzenleme değil). Yalnızca **biçimine** bakın: dört satır `define('DB_…','…');` biçiminde, değerler **tek tırnak** içinde mi? Ekran görüntüsü gönderecekseniz **dört değeri de karalayın.**

**B10 (geçici dizin):** Bu aşamada yapılmayacak. Canlıya alma onayıyla birlikte, ayrı bir alt klasörde ve sonradan silinecek tek bir okuma dosyasıyla denenebilir. O adım için ayrıca onayınız istenecektir.
