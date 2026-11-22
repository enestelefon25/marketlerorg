# Marketler.org – Android Uygulaması

https://marketler.org sitesini güvenli bir WebView içinde açan Android uygulaması.
PHP/MySQL altyapısı sunucuda olduğu gibi kalır; uygulama yalnızca siteyi gösterir.

| Bilgi | Değer |
|---|---|
| Uygulama adı | Marketler.org |
| Paket adı | `org.marketler.app` |
| Sürüm | 1.0.0 (versionCode 1) |
| Minimum Android | 7.0 (API 24) |
| Hedef / derleme SDK | 36 (Android 16) |
| Dil / araçlar | Kotlin 2.2.0, AGP 8.12.0, Gradle 8.14.3, Java 17 |

## Proje yapısı

```
MarketlerAndroid/
├── app/src/main/java/org/marketler/app/
│   ├── MainActivity.kt          Ekran durumları (yükleniyor / site / hata), geri tuşu, yaşam döngüsü
│   ├── WebViewManager.kt        Güvenli WebView ayarları, gezinme kuralları, SSL, ilerleme
│   ├── UrlPolicy.kt             Hangi adres nerede açılır (birim testli)
│   ├── ExternalLinkOpener.kt    Harici siteler → tarayıcı, tel:/mailto:/intent: → ilgili uygulama
│   ├── NetworkManager.kt        Bağlantı durumu, bağlantı gelince otomatik yeniden deneme
│   ├── FileDownloadManager.kt   İndirmeler → Downloads klasörü, bildirim + "Aç"
│   ├── FileChooserManager.kt    <input type="file">, galeri/dosya, kamera (izinsiz)
│   ├── UiMessenger.kt           Snackbar bildirimleri
│   └── AppConfig.kt             Ana adres ve izinli alan adı
├── app/src/main/res/            Arayüz, ikonlar, renkler, metinler, güvenlik ayarları
├── app/src/test/                UrlPolicy birim testleri
└── .github/workflows/android.yml (depo kökünde) GitHub'da otomatik derleme
```

## Özellikler

- Açılış ekranı (Android 12+ yerel splash, 7–11 için uyumluluk), ardından logolu yüklenme ekranı
- Üstte ilerleme çubuğu, aşağı çekerek yenileme
- Çevrimdışı ekranı ve **Tekrar dene**; bağlantı geri gelince otomatik yeniden yükleme
- Geri tuşu önceki sayfaya döner; ilk sayfada çıkış onayı sorar
- `marketler.org` ve alt alan adları uygulama içinde; diğer siteler tarayıcıda
- PDF/görsel indirme → Downloads klasörü, sistem bildiriminde ilerleme, bitince "Aç"
- Dosya yükleme: galeri/dosyalar, site görsel kabul ediyorsa kamera seçeneği
- Kenardan kenara çizim: durum çubuğu marka renginde, klavye açılınca içerik yukarı kayar

## Güvenlik

- Yalnızca HTTPS: `network_security_config` ile düz HTTP kapalı, sadece sistem sertifikalarına güvenilir.
- Karışık içerik (HTTPS sayfada HTTP) asla yüklenmez.
- marketler.org'a giden HTTP bağlantısı HTTPS'e yükseltilir; diğer HTTP adresleri engellenir.
- `javascript:`, `file:`, `content:`, `data:` gezinmeleri engellenir.
- SSL hatalarında `handler.cancel()` çağrılır, sertifika hatası hiçbir koşulda yok sayılmaz.
- Safe Browsing açık (manifest ve `WebSettingsCompat`).
- Dosya erişimi, içerik erişimi, file URL'den evrensel erişim kapalı.
- JavaScript köprüsü (`addJavascriptInterface`) **yok**.
- WebView hata ayıklama yalnızca debug sürümünde açık.
- Uygulama parola veya kullanıcı verisi saklamaz; oturum yalnızca WebView çerezlerindedir.
  Yedekleme kapalı (`allowBackup=false`), çerezler buluta veya başka cihaza taşınmaz.
- `intent:` bağlantılarında hedef bileşen ve seçici temizlenir.
- Üçüncü taraf çerezleri kapalı.

**İzinler:** `INTERNET`, `ACCESS_NETWORK_STATE`; yalnızca Android 7–9'da indirme sırasında istenen
`WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`). Kamera izni istenmez; fotoğrafı sistem kamera uygulaması çeker.

## Android Studio ile derleme

1. Android Studio'yu (güncel kararlı sürüm) açın → **Open** → `MarketlerAndroid` klasörünü seçin.
2. Gradle senkronizasyonunu bekleyin. Eksik SDK bileşenlerini Android Studio kendisi indirir.
3. Telefonda deneme: **Run ▶** (debug sürümü `org.marketler.app.debug` adıyla ayrıca kurulur).
4. Komut satırından: `./gradlew testDebugUnitTest assembleDebug`

## İmzalı release APK

İmzalama anahtarı **size aittir** ve kaynak koduna, ZIP'e veya depoya girmez
(`.gitignore` içinde `*.jks`, `*.keystore`, `keystore.properties` engellidir).

> Anahtarı ve parolalarını güvenli bir yerde yedekleyin. Kaybederseniz aynı uygulamaya güncelleme yayımlayamazsınız.

### Yol A – Android Studio (en kolay)

**Build → Generate Signed App Bundle or APK → APK → Create new…** ile anahtar oluşturun,
**release** seçip bitirin. Çıktı: `app/release/app-release.apk`.

### Yol B – Komut satırı

```bash
keytool -genkeypair -v -keystore ~/marketler-release.jks -alias marketler \
  -keyalg RSA -keysize 4096 -validity 10000
```

Proje kökünde `keystore.properties` oluşturun (git'e girmez):

```properties
storeFile=/tam/yol/marketler-release.jks
storePassword=...
keyAlias=marketler
keyPassword=...
```

Ardından `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`

### Yol C – GitHub ile imzalı APK

Depoya her gönderimde `.github/workflows/android.yml` projeyi derler, testleri ve lint'i çalıştırır.
İmzalı APK için **GitHub → Settings → Secrets and variables → Actions** altına ekleyin:

| Secret | İçerik |
|---|---|
| `MARKETLER_KEYSTORE_BASE64` | `base64 -w0 marketler-release.jks` çıktısı |
| `MARKETLER_KEYSTORE_PASSWORD` | Keystore parolası |
| `MARKETLER_KEY_ALIAS` | Ör. `marketler` |
| `MARKETLER_KEY_PASSWORD` | Anahtar parolası |

Çalışma bitince **Actions → ilgili çalışma → Artifacts → MarketlerAndroid** içinde
`MarketlerAndroid.apk` (imzalı release), `MarketlerAndroid-debug.apk` ve
`MarketlerAndroid-Source.zip` bulunur. Secret'lar yoksa yalnızca debug APK ve kaynak ZIP üretilir.

## Özelleştirme

- **Logo:** Şu anki ikon geçici bir alışveriş sepeti simgesidir. Gerçek logo için Android Studio'da
  `res` → **New → Image Asset** ile `ic_launcher` oluşturun; açılış ekranı için `drawable/ic_splash_logo.xml`
  ve yüklenme ekranı için `drawable/ic_logo_mark.xml` dosyalarını değiştirin.
- **Renkler:** `res/values/colors.xml` (`brand_primary`, `brand_primary_dark`, `brand_accent`).
- **Adres / alan adı:** `AppConfig.kt`.

## Bilinen sınırlamalar

- `blob:` ve `data:` adresli indirmeler (JavaScript ile üretilen dosyalar) desteklenmez; kullanıcıya bilgi verilir.
  Bunun için JavaScript köprüsü gerekir, güvenlik gereği eklenmedi.
- Sitedeki bir **form gönderimi (POST)** doğrudan başka bir alan adına gidiyorsa (ör. bazı ödeme sayfaları),
  o sayfa tarayıcıda GET ile açılır ve form verisi taşınamaz. Ödeme akışı varsa gerçek cihazda test edilmelidir.
- Aşağı çekerek yenileme, sayfa kaydırma konumuna (`scrollY`) bakar; kendi içinde kayan alanları olan
  sayfalarda beklenmedik tetiklenebilir.

## Test listesi (gerçek cihazda yapılmalı)

- [ ] Açılış ekranı → yüklenme ekranı → ana sayfa
- [ ] Giriş yap, uygulamayı kapatıp aç: oturum korunuyor mu
- [ ] Geri tuşu: önceki sayfa; ana sayfada çıkış onayı
- [ ] Uçak modu: çevrimdışı ekranı; bağlantı gelince otomatik yükleme; Tekrar dene
- [ ] Harici market bağlantısı tarayıcıda açılıyor mu; tel:/mailto:/WhatsApp bağlantıları
- [ ] PDF ve görsel indirme → bildirim, Downloads klasörü, "Aç"
- [ ] Dosya yükleme: galeri, dosyalar, kamera
- [ ] Android 7 (API 24) ve güncel bir Android sürümünde (14/15/16) deneme
- [ ] Yatay ekran, tablet, karanlık mod
