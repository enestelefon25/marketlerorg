# FAZ 2.1 Raporu: Ana Sayfa, Arama, Ürün Detay (mock)

**Sürüm:** `0.2.1-mock` · **Veri:** yalnızca gömülü mock JSON · **Ağ:** yok (`INTERNET` izni yok) · **API ve canlı sistem:** değişmedi, bağlanılmadı.
**FAZ 1 kapanış takibi:** Ayrı; durum `FAZ1-SON-DEGERLENDIRME.md` dosyasında değişmedi (D.3 ve sunucu bilgileri hâlâ bekleniyor; doğrulanmış gibi gösterilmiyor).

## 1. APK durumu (önce bunu okuyun)
> **Güncelleme (telefon denemesi 1):** Android Studio derlemesi sizin bilgisayarınızda **başarılı oldu.** Gönderdiğiniz `app-debug.apk` incelendi: paket `org.marketler.app.dev`, mock veri dosyaları içinde. Telefonda bildirilen sorun ve düzeltmesi bölüm 11'de. Aşağıdaki maddeler, **bu ortamın** yaptıklarını anlatır.

- **Bu ortamda Android'e özgü hiçbir derleme yapılmadı.** Android Gradle eklentisi (AGP) çalıştırılmadı. Kaynak dosyaları (`res/`) derlenmedi, `R` sınıfı üretilmedi. Manifest birleştirilmedi, R8 çalıştırılmadı, APK üretilmedi.
- **Nedeni:** Ortam Google'ın Android deposuna erişemiyor. Kararınız gereği ağ izni de istenmedi.
- **Bölüm 3'teki "tür denetimi" bir Android derlemesi değildir.** Arayüz kodu, JetBrains'in **masaüstü** Compose kütüphanesine ve bazı Android API'leri için yazılmış taslaklara karşı derlendi. Bu denetim yalnızca Kotlin sözdizimini ve Compose API kullanımını sınar. Android derlemesinin başarılı olacağını **göstermez.**
- APK'yı **siz** Android Studio'da üretiyorsunuz (adımlar bölüm 8'de). Derlemenin bilgisayarınızda hatasız geçtiği **henüz doğrulanmadı.**
- Bir hata çıkarsa çıktısını gönderin. Yalnızca o hatayı düzeltip yeni paket veririm.

## 2. Tamamlanan özellikler
| Ekran | Özellikler |
|---|---|
| **Genel** | Marketler.org mevcut logosu; lacivert/mavi tema (sitenin modern temasıyla aynı renkler); açılış ekranı; uygulama ikonu (Android 7–7.1 için PNG, 8+ için uyarlanabilir); alt menü: Ana Sayfa · Ara · Kataloglar · Karşılaştır · Asistan; kenardan kenara çizim |
| **Ana sayfa** | Logo, başlık, arama kutusu (Ara ekranını açar), "Örnek veri (mock)" uyarısı ve veri tarihi; marketler (logo + güncel katalog sayısı, dokununca o markete süzülmüş arama); öne çıkanlar (başlık, alt başlık, kartlar ve sunucu notu **API'den**); güncel kataloglar; öne çıkan karşılaştırma (en ucuz yeşil, tasarruf cümlesi sunucudan; satıra dokununca ürün detayı); 8 kategori kısayolu (dokununca kategoriye süzülmüş arama); yaklaşan kataloglar; sitedeki marka uyarısı |
| **Arama** | Yazarken öneri (**300 ms bekleme**, en çok 5; öneriye dokununca o adla arama); klavyeden "Ara"; süzgeçler: market, kategori, katalog durumu (aktif / yaklaşan / süresi dolmuş / tümü), sıralama (en yeni / en düşük / en yüksek fiyat); "N ürün bulundu"; aşağı kaydırdıkça 24'lük sayfalar; yükleniyor iskeleti; boş sonuç ve hata ekranları |
| **Ürün detay** | Market rengi yer tutucu; market logosu, kategori, katalog durumu; ad; fiyat, eski fiyat ve indirim (yalnızca sunucu verdiyse); birim fiyat; kampanya tarihleri; fiyat geçmişi cümlesi (sunucudan); "Diğer marketlerde" karşılaştırma grubu (bu ürün çerçeveli, diğer satırlar o ürüne gider) veya sunucunun "eşleşme yok" açıklaması; katalog bilgisi; geri düğmesi |

**Kurallar (KARARLAR.md):**
- Fiyat, indirim, fark ve birim fiyat uygulamada **hesaplanmıyor**; sunucunun metni gösteriliyor.
- Olmayan görsel veya eski fiyat **uydurulmuyor.**
- Favori anahtarı modeli `itemId:storeId:hash`.
- Firebase yok, hesap yok, giriş yok.

## 3. Test sonuçları
| Test | Ortam | Sonuç |
|---|---|---|
| `core/data` birim testleri (14): mock verinin Rev3 sözleşmesine göre okunması, fiyatların metin/`BigDecimal` biçimi, indirimin yalnızca sunucu verdiyse olması, favori anahtarı biçimi (olumlu + 3 olumsuz), varsayılan "aktif" süzgeci, kelime araması, Türkçe büyük/küçük harf (şeker/ŞEKER, İnci), market/kategori süzgeci, sıralama + eşitlikte kalem no, **sayfalamada tekrar/kayıp yok** (3 sıralama × 4 sayfa boyu), öneri kuralları, ürün detayı ve karşılaştırma, olmayan verinin uydurulmaması, Türkçe sadeleştirme | Bu ortam, Kotlin 2.2.0, JDK 21 | **14/14 geçti** |
| Arayüz kodunun **tür denetimi** (Android derlemesi **değildir**): tema, bileşenler, 3 ekran, 3 ViewModel, durum modeli | Bu ortam. JetBrains **masaüstü** Compose 1.7.3 kütüphanesi ve Android'e özgü 6 API (`R`, `painterResource(Int)`, `android.graphics.Color`, `ViewModel`/`viewModelScope`, `viewModel{}`, `collectAsStateWithLifecycle`) için elle yazılmış taslaklar. Kasıtlı bir hata eklenerek denetimin gerçekten çalıştığı doğrulandı | Tür hatası çıkmadı (144 sınıf). **Android derlemesinin sonucunu göstermez** |
| Android derlemesi (AGP, kaynaklar, `R` sınıfı, manifest, R8, APK) | **Yapılmadı** (Google deposuna erişim yok) | Bilgisayarınızda yapılacak |
| Telefonda deneme | **Yapılmadı** | Sizde. Gerçek cihazda denenmeyen hiçbir özellik "test edildi" sayılmadı |

## 4. Derleme riskini azaltmak için yapılanlar ve kalan riskler
- **Yapılanlar:**
  - AGP, Kotlin, Gradle ve compileSdk sürümleri bilgisayarınızda derlenen WebView projesiyle aynı.
  - Gradle sarmalayıcısı oradan kopyalandı.
  - Ek eklenti yok: Hilt/KSP bu aşamada kullanılmadı (bkz. 6).
- **Hiç derlenmeyenler** (masaüstü tür denetimine bile girmedi): `AppNavigation.kt` (gezinme API'si), `MainActivity.kt`, `MarketlerApplication.kt`, manifest, tüm `res/` dosyaları ve Gradle betikleri. Bunlar yalnızca elle gözden geçirildi. Android Studio'daki ilk senkronizasyon ve derleme, bunların ilk gerçek denetimi olacak.
- **Doğrulanamayan sürümler:** Compose BOM `2025.01.01`, navigation-compose `2.8.5`, lifecycle `2.8.7`, activity-compose `1.10.1`, core-splashscreen `1.0.1`. Bunlar Google deposunda; bu ortamdan varlıkları kontrol edilemedi. Senkronizasyonda "bulunamadı" hatası çıkarsa sürüm numarasını düzeltirim.

## 5. Değişen / eklenen dosyalar
| Dosya | Durum |
|---|---|
| `MarketlerNative/android/**` (56 dosya + 6 mock JSON) | Yeni |
| `MarketlerNative/tools/mock_veri_uret.php` | Yeni: mock veriyi Rev3 API'nin yerel kopyasından üretir (yerel test kuralları; API kodu değişmez) |
| `MarketlerNative/android/README.md`, `docs/FAZ2.1-RAPORU.md` | Yeni |
| `MarketlerNative/server/**` (API Rev3) | **Değişmedi** |
| `MarketlerAndroid/` (WebView) | **Değişmedi** (yalnızca Gradle sarmalayıcısı dosyaları buradan kopyalandı) |

## 6. Bilinen eksikler ve sınırlamalar
1. **APK teslim edilemedi ve Android derlemesi yapılmadı** (bölüm 1).
2. **Planlanan bileşenlerden ertelenenler:**
   - **Hilt:** Bu aşamada elle bağlama kullanıldı (etkisi ve ekleme planı bölüm 9'da).
   - **Retrofit, Room, Coil, WorkManager:** API bağlantısı, çevrimdışı önbellek ve görsel yükleme aşamalarında eklenecek.
3. **Görseller yok:** Mock modda ağ olmadığı için ürün görselleri ve katalog kapakları yüklenmiyor; market renginde yer tutucu gösteriliyor. Çoğu üründe zaten görsel yok.
4. **Mock arama yaklaşık:** Gerçek API'nin MySQL sıralamasıyla birebir değil; Türkçe harf karşılaştırması ve "en yeni" sıralamasında ad sırası küçük farklar gösterebilir. Katalog durumları, verinin üretildiği güne sabitlenmiş durumda.
5. **Bu aşamada olmayan özellikler:**
   - Favoriler (2.7), "Katalogda gör" ve katalog ekranları (2.5), Karşılaştır sekmesi (2.5), Asistan (2.6), koyu tema (2.8).
   - Bu sekmeler çalışmayan düğme yerine açık bir "sonraki aşamada" ekranı gösteriyor.
   - Ürün detayında favori ve katalog düğmesi bilinçli olarak konmadı.
6. **Uygulama kimliği (karar bekliyor, değiştirilmedi):** Debug sürümü `org.marketler.app.dev`. Yayın sürümünün kimliği şu an `org.marketler.app`, yani telefondaki WebView uygulamasıyla **aynı.** Play'de WebView uygulamasının yerine mi geçecek, ayrı bir uygulama mı olacak, kararı sizin. Aynı kimlikle güncelleme yayımlamak aynı imzalama anahtarını gerektirir. Siz karar verene kadar bu değer değiştirilmeyecek. Debug APK farklı kimlik taşıdığı için bu karar şimdiki denemeyi etkilemez.
7. **Logo:** Sitenin kodundaki yerleşik logo kullanıldı. Panelden yüklenmiş ayrı bir logo dosyası varsa gönderin.

## 7. Telefonda kontrol listesi (sizin için)
- [ ] Açılış ekranı (lacivert, sepet simgesi) → ana sayfa
- [ ] Ana sayfa: "Örnek veri (mock)" uyarısı görünüyor; bölümler kayıyor; market kartına dokununca o markete süzülmüş arama
- [ ] Öne çıkan ürüne dokununca ürün detayı; geri tuşu ana sayfaya döndürüyor
- [ ] Arama: "sü" yazınca öneri listesi; "süt" araması; süzgeçler; listenin sonuna kaydırınca yeni sayfa
- [ ] Ürün detayında "Diğer marketlerde" satırına dokununca o ürün açılıyor
- [ ] Kataloglar / Karşılaştır / Asistan sekmeleri "sonraki aşamada" ekranı gösteriyor
- [ ] Uçak modunda uygulama aynen çalışıyor (ağ kullanmadığının kanıtı)
- [ ] Hiçbir ekranda çökme yok; yazılar taşmıyor, kesilmiyor

## 8. Android Studio'da açma ve APK üretme
1. `MarketlerNative-FAZ2.1-Android.zip` dosyasını bir klasöre çıkarın. Yol kısa olsun, Türkçe karakter ve boşluk içermesin (örnek: `C:\proje`).
2. **Açılacak klasör:** `MarketlerNative-FAZ2.1/MarketlerNative/android`. İçinde `settings.gradle.kts`, `gradlew` ve `app/` bulunan klasör budur.
   - Bir üst klasörü (`MarketlerNative`) veya `app` klasörünü **açmayın.**
   - WebView projesi (`MarketlerAndroid`) bu pakette yok ve bu projeyle ilgisi yok.
3. Android Studio → **File → Open** → yukarıdaki `android` klasörü → **Trust Project**.
4. Gradle senkronizasyonu bitene kadar bekleyin (ilk seferde kütüphaneler indirilir, birkaç dakika sürebilir). "AGP Upgrade Assistant" önerisini kapatın.
5. **APK:** **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
6. Sağ altta çıkan bildirimde **locate** bağlantısı APK'nın yerini açar: `app/build/outputs/apk/debug/app-debug.apk`.
7. Telefona kurulum, WebView uygulamasında yaptığınızla aynı. Uygulama **Marketler.org** adıyla, kimliği `org.marketler.app.dev` olarak, WebView uygulamasının **yanına** kurulur. WebView uygulaması silinmez ve değişmez.
8. **Hata çıkarsa** şunları gönderin:
   - **Build** penceresindeki ilk kırmızı satırın ve altındaki birkaç satırın ekran görüntüsü veya metni;
   - senkronizasyon hatasıysa **Sync** sekmesindeki mesaj.

## 9. Hilt kullanılmamasının etkisi ve sonraki aşamada eklenmesi
**Şu anki yapı (elle bağlama):**
- Tek bağımlılık depo nesnesi.
- `MarketlerApplication` içinde bir kez oluşturuluyor: `val repository: MarketlerRepository by lazy { MockRepository() }`.
- Her ekranın ViewModel'i bu nesneyi kurucu parametresi olarak alıyor (`HomeViewModel(repo)`, `SearchViewModel(repo)`, `ProductViewModel(repo, id)`).
- Gezinme kodu, ViewModel'leri `viewModel { ... }` fabrikasıyla oluşturuyor.

**Etkisi:**
- **Çalışmaya etkisi yok.** Tek depo nesnesi uygulama boyunca bir kez oluşturuluyor, Hilt'teki `@Singleton` ile aynı davranış.
- **Derleme riski daha düşük.** KSP veya kapt eklentisi ve onların Kotlin/AGP sürüm uyumu yok. Bu ortamda doğrulanamayan bir parça eksiliyor.
- **Ekranlar ve ViewModel'ler Hilt'ten bağımsız.** ViewModel'ler zaten bağımlılığı kurucudan alıyor. Bu yüzden Hilt'e geçişte ekran kodu değişmiyor. Testlerde sahte depo vermek bugün de mümkün.
- **Sınırlama:** Bağımlılık sayısı arttıkça (Retrofit, Room, Coil, WorkManager, ayarlar) Application sınıfındaki elle kurulum büyür. WorkManager işçilerine bağımlılık vermek de elle fabrika gerektirir. Hilt'in asıl kazancı bu aşamada başlar.

**Ekleme planı** (gerçek API bağlantısı aşamasında, ayrı ve küçük bir adım olarak):
1. `libs.versions.toml` dosyasına Hilt ve KSP sürümlerini ekleyin. KSP sürümü, Kotlin sürümüyle (2.2.0) eşleşmelidir.
2. Kök ve `app` derleme dosyalarına `com.google.dagger.hilt.android` ve `com.google.devtools.ksp` eklentilerini ekleyin. Ardından `hilt-android`, `ksp(hilt-compiler)` ve `hilt-navigation-compose` bağımlılıklarını ekleyin.
3. `MarketlerApplication` sınıfına `@HiltAndroidApp`, `MainActivity` sınıfına `@AndroidEntryPoint` ekleyin.
4. Bir `DataModule` (`@Module @InstallIn(SingletonComponent::class)`) yazın. İçinde `@Binds @Singleton MarketlerRepository` olsun. Önce `MockRepository`, gerçek API aşamasında API deposu bağlanır.
5. ViewModel'lere `@HiltViewModel` ve `@Inject constructor` ekleyin. Ürün kimliği `SavedStateHandle` üzerinden okunur.
6. Gezinme kodunda `viewModel { ... }` fabrikalarının yerine `hiltViewModel()` kullanın. Application içindeki `lazy` depo kaldırılır.
7. Doğrulama: aynı birim testleri, telefonda bölüm 7 kontrol listesinin tekrarı.

Bu adım **yeni özellik eklemez**, yalnızca bağlama biçimini değiştirir. Hangi dosyaların değiştiği ayrıca raporlanacak.

## 10. Paket ve dosya özetleri
- **Paket:** `MarketlerNative-FAZ2.1-Android.zip`. İçinde tüm Android kaynak kodu, Gradle dosyaları ve sarmalayıcısı, görseller (ikonlar, market logoları), mock veri, mock üretim aracı ve belgeler var.
- **SHA-256 kontrol listesi:** `docs/FAZ2.1-DOSYA-OZETLERI.txt`. Zip'teki her dosyanın özetini içerir; dosyanın kendisi listede yoktur.
- **Paketi kendiniz doğrulamak için** zip'i çıkardığınız `MarketlerNative-FAZ2.1` klasöründe şu komutları çalıştırın:
  - Linux/macOS: `sha256sum -c docs/FAZ2.1-DOSYA-OZETLERI.txt`
  - Windows PowerShell'de tek bir dosya için: `Get-FileHash <dosya> -Algorithm SHA256`
- Paketteki kod, `a6d3a73` işlemesindeki FAZ 2.1 koduyla **aynıdır.** Bu teslimde yalnızca belgeler güncellendi. Kod, uygulama kimliği, WebView projesi, API Rev3, canlı site ve veritabanı değişmedi.

## 11. Telefon denemesi 1: bildirilen sorunlar ve düzeltmeler
**Bildirilen sorun:** Ana sayfa sağa sola kayıyor. Sizin gönderdiğiniz video kare kare incelendi; videoda karşılaştırma için başka bir katalog uygulaması da var.

**Videoda görülen:**
- Ana sayfanın büyük kısmı üç yatay şeritten oluşuyordu: marketler, öne çıkan fırsatlar, güncel kataloglar.
- Aşağı kaydırırken parmak bu şeritlerden birine denk gelince içerik yana kayıyor; ekranın sağında yarım kartlar görünüyordu.
- Karşılaştırdığınız uygulamada ana sayfa yalnızca dikey kayıyor, kartlar ızgara düzeninde.
- Alt menüde "Kataloglar" yazısı "Katalogla" olarak kesiliyordu.

**Düzeltmeler:**
| Dosya | Değişiklik |
|---|---|
| `app/.../ui/home/HomeScreen.kt` | Üç yatay şerit kaldırıldı, yerine ekran genişliğine sığan ızgaralar konuldu: marketler 3 sütun, öne çıkan fırsatlar 2 sütun, güncel kataloglar 2 sütun. **Ana sayfada artık yana kayan hiçbir alan yok.** İçerik ve veri aynı. |
| `core/ui/.../Components.kt` | Ürün kartı ve katalog kartı dar sütuna uyarlandı: market logosu ve indirim üstte, kategori ayrı satırda, görsel alanları biraz alçaltıldı. `PriceBlock` için `stacked` seçeneği eklendi; dar kartta eski fiyat alt satıra iniyor. Ürün detayındaki fiyat görünümü değişmedi. |
| `app/.../ui/AppNavigation.kt` | Alt menü yazıları 11 sp yapıldı ve satır kırılması kapatıldı ("Kataloglar" kesilmesin diye). |
| `app/src/main/AndroidManifest.xml` | Bir önceki adımda öngörülü geri hareketi kapatılmıştı (`enableOnBackInvokedCallback="false"`). Asıl neden şeritler çıktı; bu ayar zararsız olduğu için yerinde bırakıldı. Kenardan kaydırınca sayfa küçülüp kaymaz. |

**Denetim:**
- Değişen arayüz kodu, bölüm 3'teki **masaüstü** tür denetiminden hatasız geçti. Bu bir Android derlemesi değildir.
- `AppNavigation.kt` bu denetimin dışında; değişiklik yalnızca yazı boyutu.
- Uygulama kimliği, sürüm numarası, veri, API ve WebView projesi değişmedi.
- Yeni özellik eklenmedi.

**Telefonda kontrol:**
1. Yeni APK'yı derleyip kurun. Eskisinin üzerine kurulur, silmeniz gerekmez.
2. Ana sayfada aşağı-yukarı kaydırın. Hiçbir bölüm yana kaymamalı, sağda yarım kart görünmemeli.
3. Marketler 3 kutu yan yana, fırsatlar ve kataloglar 2'şerli görünmeli. Yazı taşması veya üst üste binme olmamalı.
4. Alt menüde "Kataloglar" ve "Karşılaştır" tam okunmalı.
- Sorun sürerse ekran görüntüsü veya kısa video gönderin.
