# FAZ 2.1 Raporu: Ana Sayfa, Arama, Ürün Detay (mock)

**Sürüm:** `0.2.1-mock` · **Veri:** yalnızca gömülü mock JSON · **Ağ:** yok (`INTERNET` izni yok) · **API ve canlı sistem:** değişmedi, bağlanılmadı.
**FAZ 1 kapanış takibi:** Ayrı; durum `FAZ1-SON-DEGERLENDIRME.md` dosyasında değişmedi (D.3 ve sunucu bilgileri hâlâ bekleniyor; doğrulanmış gibi gösterilmiyor).

## 1. APK durumu (önce bunu okuyun)
- **APK bu ortamda üretilemedi.** Ortam Google'ın Android deposuna erişemiyor ve kararınız gereği ağ izni istenmedi.
- APK'yı **siz** Android Studio'da üretiyorsunuz: **Build → Build APK(s)** → `app-debug.apk` (`android/README.md`).
- Bu yüzden derlemenin bilgisayarınızda hatasız geçtiği **henüz doğrulanmadı.** Riski azaltmak için yapılanlar bölüm 4'te. Bir hata çıkarsa çıktısını gönderin, düzeltip yeni paket vereyim.

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
| Arayüz kodunun **tür denetimi**: tema, bileşenler, 3 ekran, 3 ViewModel, durum modeli | Bu ortam, Compose 1.7 API'si (JetBrains masaüstü derlemesi) + Android'e özgü 6 API için taslak. Kasıtlı hata eklenerek denetimin çalıştığı doğrulandı | **Hatasız derlendi** (144 sınıf) |
| Android derlemesi (AGP, kaynaklar, manifest, R8) | **Yapılamadı** (Google deposuna erişim yok) | Bilgisayarınızda yapılacak |
| Telefonda deneme | **Yapılmadı** | Sizde. Gerçek cihazda denenmeyen hiçbir özellik "test edildi" sayılmadı |

## 4. Derleme riskini azaltmak için yapılanlar ve kalan riskler
- **Yapılanlar:**
  - AGP, Kotlin, Gradle ve compileSdk sürümleri bilgisayarınızda derlenen WebView projesiyle aynı.
  - Gradle sarmalayıcısı oradan kopyalandı.
  - Ek eklenti yok: Hilt/KSP bu aşamada kullanılmadı (bkz. 6).
- **Tür denetimi dışında kalanlar:** `AppNavigation.kt` (gezinme API'si), `MainActivity.kt`, `MarketlerApplication.kt`, manifest ve kaynak dosyaları. Elle gözden geçirildi, derlenmedi.
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
1. **APK teslim edilemedi** (bölüm 1).
2. **Planlanan bileşenlerden ertelenenler:**
   - **Hilt:** Bu aşamada elle bağlama kullanıldı; nedeni, bu ortamda doğrulanamayan KSP sürüm uyumu riski. Gerçek API aşamasında eklenecek; ekranlar değişmeyecek.
   - **Retrofit, Room, Coil, WorkManager:** API bağlantısı, çevrimdışı önbellek ve görsel yükleme aşamalarında eklenecek.
3. **Görseller yok:** Mock modda ağ olmadığı için ürün görselleri ve katalog kapakları yüklenmiyor; market renginde yer tutucu gösteriliyor. Çoğu üründe zaten görsel yok.
4. **Mock arama yaklaşık:** Gerçek API'nin MySQL sıralamasıyla birebir değil; Türkçe harf karşılaştırması ve "en yeni" sıralamasında ad sırası küçük farklar gösterebilir. Katalog durumları, verinin üretildiği güne sabitlenmiş durumda.
5. **Bu aşamada olmayan özellikler:**
   - Favoriler (2.7), "Katalogda gör" ve katalog ekranları (2.5), Karşılaştır sekmesi (2.5), Asistan (2.6), koyu tema (2.8).
   - Bu sekmeler çalışmayan düğme yerine açık bir "sonraki aşamada" ekranı gösteriyor.
   - Ürün detayında favori ve katalog düğmesi bilinçli olarak konmadı.
6. **Uygulama kimliği:** Debug sürümü `org.marketler.app.dev`. Yayın sürümünün kimliği `org.marketler.app`, yani telefondaki WebView uygulamasıyla **aynı.** Play'de WebView uygulamasının yerine mi geçecek, ayrı bir uygulama mı olacak, kararı sizin. Aynı kimlikle güncelleme yayımlamak aynı imzalama anahtarını gerektirir.
7. **Logo:** Sitenin kodundaki yerleşik logo kullanıldı. Panelden yüklenmiş ayrı bir logo dosyası varsa gönderin.

## 7. Telefonda kontrol listesi (sizin için)
- [ ] Açılış ekranı (lacivert, sepet simgesi) → ana sayfa
- [ ] Ana sayfa: "Örnek veri (mock)" uyarısı görünüyor; bölümler kayıyor; market kartına dokununca o markete süzülmüş arama
- [ ] Öne çıkan ürüne dokununca ürün detayı; geri tuşu ana sayfaya döndürüyor
- [ ] Arama: "sü" yazınca öneri listesi; "süt" araması; süzgeçler; listenin sonuna kaydırınca yeni sayfa
- [ ] Ürün detayında "Diğer marketlerde" satırına dokununca o ürün açılıyor
- [ ] Kataloglar / Karşılaştır / Asistan sekmeleri "sonraki aşamada" ekranı gösteriyor
- [ ] Uçak modunda uygulama aynen çalışıyor (ağ kullanmadığının kanıtı)
