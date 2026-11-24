# FAZ 2.2.2 Raporu: Ekranların Veri Kaynağına Bağlanması, Sürümler ve Ağ Güvenliği

**Sürüm:** `0.2.2.2` (versionCode 3) · **Kod:** `450e9c5` işlemesi.

**Kapsam:**
- Ana sayfa, arama ve ürün detayı artık seçilen veri kaynağından çalışıyor: örnek veri, yerel API kopyası veya canlı API.
- Üç derleme sürümü (`mock`, `yerel`, `canli`) ve ağ güvenlik ayarları eklendi.
- B1 kararı uygulandı: önerilerde fiyat gösterilmiyor.

**Değişmedi:** PHP API, eşleştirme algoritması, veritabanı, yönetim paneli, WebView uygulaması, canlı site. Canlıya hiçbir şey yüklenmedi; canlı API'ye hiç istek gönderilmedi.

## 0. API sözleşmesinin yeniden kontrolü
- API kodu (`MarketlerNative/server/`) Rev3 işlemesinden (`e95404e`) bu yana **hiç değişmedi.** `git diff` boş.
- Ekranların kullandığı 6 uç nokta, parametreleri ve alanları 2.2.1 karşılaştırmasıyla aynı.
- Yeni bir uyumsuzluk bulunmadı. 2.2.1'deki B1–B6 bulguları aynen geçerli; uygulamadaki karşılıkları korunuyor.

## 1. Teslim edilen APK'lar
| Dosya | SHA-256 | Boyut | Kimlik / sürüm | Telefondaki ad |
|---|---|---|---|---|
| `Marketler-FAZ2.2.2-mock-debug.apk` | `02946ce8359bd7584c6b50dccb6ffcba124d3574b8856fc722c394e6c2c7e65b` | 17.762.857 | `org.marketler.app.mock.dev` · 3 · `0.2.2.2-mock-debug` | Marketler (örnek) |
| `Marketler-FAZ2.2.2-yerel-debug.apk` | `1d3a4b6d168e515abe2d36a3ac52f7af239f7ed42d89376d512c0b6ca126fb08` | 17.763.406 | `org.marketler.app.yerel.dev` · 3 · `0.2.2.2-yerel-debug` | Marketler (yerel) |
| `Marketler-FAZ2.2.2-canli-debug.apk` | `fe0394496aa4d610241043f4f152943c297c2e4835d4c8a55f1ef5f4ab2b271c` | 17.763.466 | `org.marketler.app.dev` · 3 · `0.2.2.2-canli-debug` | Marketler.org |
| `MarketlerNative-FAZ2.2.2.zip` (kaynak) | Teslim mesajında (zip bu raporu içerdiği için) | | | |

**Kurulum notları:**
- Üç APK farklı kimlikte; telefona **yan yana** kurulur. Hiçbiri WebView uygulamasının (`org.marketler.app`) yerine geçmez.
- İmzaları önceki APK'larla aynı (`b42fcad3…4979`).
- `canli` APK'sının kimliği `org.marketler.app.dev`; telefondaki 2.2.1 / düzeltme 3 test uygulamasının **üzerine kurulur.**
  - Bu uygulama artık örnek veri göstermez; "Canlı veri henüz yayında değil" mesajını gösterir (bölüm 2.2).
  - Örnek veriyle denemek için `mock` APK'sını ayrıca kurun.
- **`yerel` APK'sı telefonda çalışmaz.** Yalnızca Android emülatöründe, bilgisayarda yerel API açıkken çalışır (10.0.2.2 = emülatörün bilgisayara ulaştığı adres). Telefonda bağlantı hatası ekranını gösterir.

## 2. Sürümler (build variant) ve güvenlik

### 2.1 Yapılandırma
| | `mock` | `yerel` | `canli` |
|---|---|---|---|
| Veri | Gömülü örnek veri (`MockRepository`) | Yerel Rev3 kopyası (`RemoteRepository`) | `RemoteRepository`; şimdilik **kapalı** (`DisabledRepository`) |
| Adres | — | `http://10.0.2.2:8099/api/v1/` | `https://marketler.org/api/v1/` |
| INTERNET izni | **Yok** | Var (`src/yerel/AndroidManifest.xml`) | Var (`src/canli/AndroidManifest.xml`) |
| HTTP (cleartext) | Kapalı (`usesCleartextTraffic=false`) | Yalnızca `10.0.2.2` (`src/yerel/res/xml/network_security_config.xml`); 127.0.0.1 dahil başka her adres kapalı | **Hiçbir adrese açık değil**; yalnızca sistem sertifikaları |
| Alan adı kilidi | — | İstemci yalnızca `10.0.2.2`'ye istek atar | İstemci yalnızca `marketler.org`'a ve yalnızca HTTPS ile istek atar |
| Release derlemesi | Var | **Yok** (Gradle'da kapatıldı) | Var (imzasız; denetim için derlendi) |

**Alan adı kilidi (`ApiConfig.lockHost`):**
- Android ağ ayarı (network security config) diğer alan adlarını **yasaklayamaz**; yalnızca HTTP'yi ve sertifika güvenini düzenler.
- Bu yüzden "yalnızca marketler.org" kuralı uygulamanın ağ istemcisinde zorlanıyor: başka alan adına veya HTTP'ye giden istek ağa çıkmadan reddediliyor.
- Yönlendirmeler izlenmiyor.
- Uygulamada başka bir ağ istemcisi (WebView, görsel yükleyici vb.) yok.

### 2.2 Canlı API erişimi: kapalı
- `canli` sürümünde `CANLI_API_ACIK=false`. Uygulama canlı API'ye **hiç istek göndermez.**
- Ekranlarda şu mesaj görünür: "Canlı veri henüz yayında değil. Bu sürüm, canlıya alma onayından sonra etkinleştirilecek."
- Ekran testinde istek sayısı ölçülerek doğrulandı: 0 istek.
- Canlıya alma onayından sonra `app/build.gradle.kts` içindeki tek satır `true` yapılacak (FAZ 2.2.3).
- R8'li `canli` release derlemesinde, erişim kapalı olduğu için ağ kodu ölü kod olarak tamamen çıkarılıyor; API adresi dizgesi bile APK'da yok.

### 2.3 Manifest ve ağ güvenliği kontrolü
Tam çıktı `docs/FAZ2.2.2-GUVENLIK-KONTROLU.txt` dosyasında. `aapt2` ve `apksigner` ile, temiz kopyada derlenen APK'lardan okundu.

| Kontrol | mock | yerel | canli debug | canli release |
|---|---|---|---|---|
| INTERNET izni | yok ✔ | var | var | var |
| `usesCleartextTraffic=false` | ✔ | ✔ | ✔ | ✔ |
| Ağ güvenlik ayarı | yok (ağ yok) ✔ | base: HTTP kapalı; yalnızca `10.0.2.2` açık ✔ | base: HTTP kapalı, sistem sertifikaları; `marketler.org`: HTTP kapalı ✔ | aynı ✔ |
| `10.0.2.2` geçen dosya | yok ✔ | ayar dosyası + adres (beklenen) | **yok ✔** | **yok ✔** |
| Uygulama adresleri | yok | `http://10.0.2.2:8099/api/v1/` | `https://marketler.org/api/v1/` | yok (kapalı erişim; ağ kodu çıkarıldı) |
| Gizli bilgi taraması (DB_PASS, DB_USER, özel anahtar, api_key, config.php) | temiz ✔ | temiz ✔ | temiz ✔ | temiz ✔ |

- **Sonuç:** Yerel HTTP istisnası canlı APK'lara taşınmamış.
- Debug APK'larda görülen `http://localhost/` dizgesi bir kütüphaneden geliyor: R8'li release'te yok ve bağlantı hedefi değil. Alan adı kilidi zaten başka adrese istek atılmasını engelliyor.
- Diğer `http://` dizgeleri XML şema adları.
- APK'da anahtar, parola veya veritabanı bilgisi **yok.** Uygulamanın böyle bir bilgiye ihtiyacı da yok.

## 3. Ekranlardaki değişiklikler
- **Veri kaynağı seçimi:** `MarketlerApplication`, sürüme göre deposunu seçer. Ekran ve ViewModel'ler değişmedi; aynı arayüzü kullanıyorlar.
- **Ana sayfa:** Veri kaynağı etiketi sürüme göre ("Örnek veri (mock)…", "Yerel test sunucusu", "Canlı veri") ve son güncelleme zamanı. Yükleniyor iskeleti, hata ve "Tekrar dene" önceki gibi.
- **Arama:**
  - **Öneride fiyat yok (B1):** yalnızca ürün adı ve market. Fiyat ürün detayında, varsa gösterilir.
  - **Sayfalama düzeltmesi:** "Daha var mı" kararı artık sunucunun sayfa bilgisinden veriliyor. Önceden öğe sayısıyla hesaplanıyordu; adsız ürünler süzüldüğünde sonsuz "daha fazla yükle" döngüsüne girebilirdi.
  - **Çift kayıt koruması:** Canlı veride sayfalar arasında kayıt değişirse aynı ürün iki kez eklenmez. Aksi halde liste anahtarı çakışır ve uygulama çöker.
  - **Sonraki sayfa hatası:** Liste korunur; hata mesajı ve "Tekrar dene" listenin sonunda görünür. Önceden hiçbir şey gösterilmiyor ve yükleme takılı kalıyordu.
  - Hata durumunda "0 ürün bulundu" yazısı gösterilmez.
  - Süzgeç listeleri (market, kategori) yüklenemezse sonraki aramada yeniden denenir.
- **Ürün detayı:** Değişiklik yok. NULL kuralları 2.2.1'deki gibi: "Fiyat belirtilmemiş" ve fiyatsız karşılaştırma ayıklaması.
- **Hata mesajları** (2.2.1'deki `DataError` türleri ekranda):
  - "İnternet bağlantısı kurulamadı…";
  - "Hizmet geçici olarak kullanılamıyor.";
  - "Çok fazla istek gönderildi…";
  - "Ürün bulunamadı.";
  - "Sunucudan gelen veri okunamadı.";
  - "Canlı veri henüz yayında değil…".

## 4. Test sonuçları
| Test | Ortam | Sonuç |
|---|---|---|
| **Gerçek Android derlemesi:** `clean` + `assembleMockDebug`, `assembleYerelDebug`, `assembleCanliDebug`, `assembleCanliRelease` (R8), kaydedilmiş koddan temiz kopya | AGP 8.12.0, Gradle 8.14.3, SDK 36, JDK 21 | **Başarılı** (4 APK) |
| `yerelRelease` derlemesinin olmaması | Gradle görev listesi | **Yok** ✔ |
| Manifest ve ağ güvenliği kontrolü (bölüm 2.3) | `aapt2`, `apksigner`, içerik taraması | **Geçti** |
| `core/data`: veri kuralları 5, mock 14, ağ katmanı 14 (sahte sunucu, yerel API'nin gerçek yanıtları) | JVM | **33/33** |
| `core/data` uçtan uca: çalışan yerel API kopyasına gerçek istekler | JVM + `php -S` + MariaDB | **4/4** |
| **Ekran testleri `EkranVeriTest`** (ayrıntı aşağıda) | Robolectric 4.15.1; mock, yerel, canli sürümlerinin her birinde | **12/12 × 3** |
| Gezinme testi (FAZ 2.1, artık projede) | Robolectric | **5/5 × 3** |
| **Ekranlar çalışan yerel API'ye** (`YerelApiEkranTest`): ana sayfa, arama ("Fiyat belirtilmemiş" dahil), ürün detayı | Robolectric + `php -S` + MariaDB | **3/3** |

**`EkranVeriTest` senaryoları:** Ekranlar gerçek Retrofit/OkHttp ağ katmanıyla çalışıyor; sahte sunucu yerel API'nin kayıtlı yanıtlarını oynatıyor.
1. Ana sayfa içeriği.
2. 503 hatası → "Tekrar dene" → içerik.
3. Bağlantı yok.
4. Aramada NULL fiyat: "Fiyat belirtilmemiş" görünür; "0,00 TL" ve "%100" görünmez.
5. Boş sonuç.
6. 429 hatası, ardından sonraki sayfa 503: liste korunur, hata listenin sonunda.
7. **Öneride fiyat görünmez.**
8. Fiyatsız ürün karşılaştırmaya girmez.
9. Fiyatsız ürün detayı.
10. NULL market ve katalog.
11. Ürün bulunamadı.
12. Canlı kapalıyken **ağa hiç istek gitmez.**

**Notlar:**
- Uçtan uca testler yalnızca `MK_YEREL_API` ortam değişkeni verilince çalışır. Temiz kopya derlemesinde bu değişken verilmediği için atlandılar (beklenen). Yerel API açıkken ayrı çalıştırıldılar ve geçtiler.
- **Telefonda deneme yapılmadı.** Bu ortamda emülatör çalışmıyor (KVM yok).

## 5. Değişen / eklenen dosyalar (2.2.1 → 2.2.2)
| Dosya | Durum |
|---|---|
| `app/build.gradle.kts` | Değişti: 3 sürüm, BuildConfig alanları, `yerelRelease` kapalı, test bağımlılıkları, sürüm 3 / `0.2.2.2` |
| `app/src/main/AndroidManifest.xml` | Değişti: `usesCleartextTraffic=false`; INTERNET izni ortak manifestte yok |
| `app/src/yerel/AndroidManifest.xml`, `app/src/yerel/res/xml/network_security_config.xml`, `app/src/yerel/res/values/strings.xml` | Yeni |
| `app/src/canli/AndroidManifest.xml`, `app/src/canli/res/xml/network_security_config.xml` | Yeni |
| `app/src/mock/res/values/strings.xml` | Yeni (uygulama adı) |
| `app/.../MarketlerApplication.kt`, `MainActivity.kt`, `ui/AppNavigation.kt`, `ui/home/HomeScreen.kt` | Değişti: veri kaynağı seçimi ve etiketi |
| `app/.../ui/search/SearchScreen.kt`, `SearchViewModel.kt` | Değişti: öneride fiyat yok, sayfalama, çift kayıt, sonraki sayfa hatası |
| `app/src/test/.../EkranVeriTest.kt`, `YerelApiEkranTest.kt`, `NavigationTest.kt` | Yeni |
| `core/data/.../DisabledRepository.kt` | Yeni |
| `core/data/.../Repository.kt` (`DataError.Disabled`), `remote/ApiClient.kt` (alan adı ve HTTPS kilidi) | Değişti |
| `core/ui/.../Components.kt` | Değişti: `DataSourceBanner` etiketi |
| `gradle/libs.versions.toml` | Değişti: Robolectric 4.15.1, compose ui-test (yalnızca test) |
| `android/README.md` | Değişti: sürümler ve derleme adımları |
| `docs/FAZ2.2.2-RAPORU.md`, `docs/FAZ2.2.2-GUVENLIK-KONTROLU.txt`, `docs/FAZ2.2.2-DOSYA-OZETLERI.txt` | Yeni |
| `MarketlerNative/server/**`, `MarketlerAndroid/**` | **Değişmedi** |

## 6. Bilinen eksikler ve riskler
1. **Canlı API ile hiç denenmedi** (kapsam gereği kapalı). FAZ 2.2.3'te canlıya alma onayından sonra yapılacak.
2. **Release (R8) çalışma zamanı doğrulanmadı.** `canli` release derlemesi başarılı. Ancak canlı erişim kapalı olduğu için R8 ağ kodunu çıkarıyor; ağ kodunun küçültülmüş hâli ancak erişim açıldığında (2.2.3) derlenip cihazda denenebilir. Retrofit ve kotlinx kuralları o zaman doğrulanacak.
3. **Telefonda veya emülatörde deneme yapılmadı.** `yerel` APK'sı yalnızca emülatörde anlamlı.
4. "N ürün bulundu" sayısı, adı boş ürün sayısı kadar fazla görünebilir (B6, sunucu sayımı).
5. Görseller (Coil) ertelendi (U4). Görsel alanları modellerde korunuyor.
6. Hata ekranındaki "Tekrar dene" düğmesi, düzelmeyecek durumlarda da görünüyor ("Ürün bulunamadı", "Canlı veri henüz yayında değil"). İşlevsel bir sorun yok.

## 7. Telefonda kontrol listesi (sizin için)
- [ ] **mock** APK ("Marketler (örnek)"): ana sayfa, arama, ürün detayı FAZ 2.1 gibi çalışıyor. Öneri listesinde fiyat yok, yalnızca ad ve market.
- [ ] **canli** APK ("Marketler.org"): açılıyor, çökmüyor. Ana sayfada "Canlı veri henüz yayında değil…" mesajı var.
- [ ] Uçak modunda mock APK aynen çalışıyor.
- [ ] (İsteğe bağlı, bilgisayarda) **yerel** APK'yı Android Studio emülatöründe, yerel API açıkken deneyin (README).
