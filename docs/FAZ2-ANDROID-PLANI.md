# FAZ 2: Marketler.org Native Android Uygulaması Geliştirme Planı

**Durum:** TASLAK. **FAZ 1 kapanınca başlar** (kapanış koşulları: `FAZ1-SON-DEGERLENDIRME.md` ve bu belgenin 0. bölümü).
**Esas:** `KARARLAR.md`, API sözleşmesi `FAZ1-UC-NOKTALAR.md` (14 uç nokta, Rev3). API kodu bu fazda **değiştirilmez**.
**Hedef:** Çalışan, hızlı, güvenilir ve Google Play'de yayınlanabilir bir uygulama. API geliştirmesini uzatmak değil.

---

## 0. Başlamadan önce gerekenler

| # | Gereken | Neden | Kim |
|---|---|---|---|
| Ö1 | **FAZ 1 kapanışı** (D.3 sonuçları, sunucu bilgileri, bağımsız inceleme) | Süreç kararı | Siz |
| Ö2 | **Derleme ortamı kararı** (aşağıda) | Kodun gerçekten derlenip test edilebilmesi | Siz |
| Ö3 | Marka varlıkları: logo (SVG veya en az 1024 px PNG), renk onayı | Uygulama ikonu ve açılış ekranı. Şimdilik sitedeki `#172554` / `#2da8ff` tonları kullanılır | Siz |
| Ö4 | Google Play geliştirici hesabı (yayın aşamasında) | Yayın | Siz |

**Derleme ortamı (kritik):** Bu bulut ortamı Google'ın Android deposuna (`dl.google.com`) erişemiyor; burada Android kodu **derlenemez**. Üç seçenek var:
- **A. Bilgisayarınızdaki Android Studio.** Daha önce çalıştığını gördük. Her adımda ben kodu yazarım, siz derleyip telefonda denersiniz. En yavaş geri bildirim döngüsü budur.
- **B. Bu ortama yalnızca `dl.google.com` ve `maven.google.com` erişimi.** Ben derleyip birim testlerini çalıştırabilirim; siz yalnızca telefonda denersiniz. **Önerim budur.** Ortam ayarı daha önce reddedildi; karar sizin.
- **C. GitHub Actions.** Depoya gönderim şu an 403 hatası veriyor; GitHub erişimi düzelirse mümkün.

## 1. Mimari (hız ve bakım dengesi)

- **Teknoloji:** Kotlin · Jetpack Compose · Material 3 · MVVM + tek yönlü veri akışı (StateFlow) · Hilt · Retrofit + OkHttp · kotlinx.serialization · Room · Paging 3 · Coil · Navigation Compose · WorkManager · DataStore.
- **minSdk 24.** targetSdk, geliştirme tarihinde Google Play'in istediği güncel sürüm olacak; başlarken doğrulanacak.
- **Modüller (sınırlı sayıda, derleme süresi kısa):**

```
MarketlerNative/android/
  app/                 navigasyon, ekranlar (feature paketleri: home, search, product, compare, catalogs, markets, assistant, favorites, settings), Hilt kurulumu
  core/model/          API ve uygulama modelleri (saf Kotlin)
  core/data/           ağ (Retrofit), Room, depolar (repository), önbellek kuralları, favori mantığı
  core/ui/             tasarım sistemi: tema, renkler, ProductCard, CatalogCard, PriceTag, durum bileşenleri
```

- **Ürün çeşitleri (product flavor):**

| Çeşit | Veri kaynağı | Ne zaman |
|---|---|---|
| `mock` | Uygulamaya gömülü JSON örnekleri | API canlıya alınmadan geliştirme ve gösterim |
| `staging` | Ayarlanabilir test adresi | Hazırlık sunucusunda deneme |
| `prod` | `https://marketler.org/api/v1/` | Yayın |

- **JSON örnekleri** (`mock` çeşidi ve sözleşme testleri için) Rev3 API'nin **yerel test kopyasından, sentetik veriyle** üretilir. API koduna dokunulmaz.

## 2. Temel teknik kurallar (API sözleşmesiyle uyum)

- **Para:** API'den metin olarak gelir (`"4599.00"`) ve `BigDecimal` olarak işlenir. Ekranda API'nin `price_text` alanı gösterilir; uygulama fiyat **hesaplamaz**: indirim, fark ve birim fiyat sunucudan gelir.
- **Olmayan veri uydurulmaz:** `image`, `old_price` veya `discount_pct` `null` ise uygulama yer tutucu gösterir. Sahte indirim yoktur.
- **Hatalar:** `success:false` zarfı `error.code` değerine göre eşlenir:
  - `RATE_LIMITED` ve `SERVICE_UNAVAILABLE`: `Retry-After` süresine uyan otomatik yeniden deneme.
  - `NOT_FOUND`: ilgili boş durum ekranı.
  - Diğerleri: genel hata ekranı ve "Tekrar dene".
- **Önbellek:** OkHttp HTTP önbelleği (`ETag` / `304`) + Room. Süreler varsayılan olarak ana sayfa 15 dk, ürün 30 dk, marketler 6 sa, kataloglar 1 sa; DataStore'dan değiştirilebilir.
- **Çevrimdışı:** Room'daki son veri gösterilir ve **"Son güncelleme: …" uyarısı** eklenir. Süresi dolmuş fiyat güncelmiş gibi gösterilmez.
- **Favoriler** (karar 4):
  - Room'da saklananlar: `fav_key` (`itemId:storeId:hash`), ilk kaydedilen ad, ilk fiyat ve eklenme tarihi.
  - Güncellik `GET /products?fav=` ile sorgulanır, 50'lik gruplar halinde.
  - `state` değerine göre etiket gösterilir; `bulunamadi` durumunda **kaydedilen ad** ve API'nin mesajı görünür.
  - `candidates`, "Olası eşleşme (doğrulanmadı)" etiketiyle yalnızca öneri olarak gösterilir. **Favori otomatik değiştirilmez**; kullanıcı isterse yeni ürünü ayrıca favoriye ekler.
- **Yerel bildirim** (karar 5):
  - WorkManager ile 12 saatte bir, ağ varken kontrol.
  - Bildirim konuları: "favori ürün yeni katalogda" (yalnızca **aynı `item_id`'nin** durumu değiştiğinde) ve "yeni katalog yayımlandı".
  - Varsayılan **kapalı**; Android 13+ için bildirim izni isteğe bağlı.
  - Firebase yok.
- **Güvenlik:**
  - Yalnızca HTTPS (`network_security_config`, `cleartextTrafficPermitted=false`); sistem sertifikaları.
  - API anahtarı yok; Logcat'e veri yazılmaz (yayın sürümünde günlük kapalı).
  - Yedekleme ayarları favori ve sohbet verisini dışarı taşımaz.
  - İzinler: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (isteğe bağlı).
- **Kimlik:** `X-App-Version` başlığı gönderilir; kullanıcı hesabı yoktur.

## 3. İş adımları ve kabul ölçütleri

Her adımın sonunda **telefona kurulabilir bir derleme** ve kısa bir test raporu teslim edilir.

| Adım | İçerik | Kabul ölçütü |
|---|---|---|
| **2.1 İskelet** | Gradle sürüm kataloğu, 4 modül, Hilt, `mock` / `staging` / `prod` çeşitleri, tema, alt menü (Ana Sayfa · Ara · Kataloglar · Karşılaştır · Asistan), imzalama (`keystore.properties`, depoya girmez) | Boş uygulama açılıyor, 5 sekme arasında geçiş çalışıyor, release derlemesi R8 ile küçültülüyor |
| **2.2 Veri katmanı** | 14 uç noktanın Retrofit tanımı, modeller, zarf ve hata eşlemesi, OkHttp önbelleği, Room şeması, depolar | **Sözleşme testleri:** Rev3 API'den alınan JSON örneklerinin 14 uç noktanın hepsinde hatasız ayrıştırılması; hata kodlarının doğru eşlenmesi |
| **2.3 Ana sayfa + ürün detay** | Marketler, öne çıkanlar (sunucunun başlığı ve notlarıyla), güncel ve yaklaşan kataloglar, öne çıkan karşılaştırma; ürün detayında fiyat geçmişi satırı, karşılaştırma grubu ve "Katalogda gör" | `mock` verisiyle sitedeki modern ana sayfayla aynı bilgi; çevrimdışında son veri + uyarı |
| **2.4 Arama** | Yazarken öneri (`/suggest`, 300 ms bekleme), filtreli liste (`/products` + Paging 3: market, kategori, fiyat, durum, sıralama), akıllı arama sonuçları (`/search`) | Sayfalarda tekrar veya kayıp yok (API'nin `i.id` eşitlik kuralı); ağ isteği yalnızca bekleme süresi sonrası |
| **2.5 Karşılaştır + kataloglar + marketler** | Gruplar (en ucuz vurgusu, fark, tasarruf cümlesi), "Benzer ürünlerde birim fiyat" sekmesi ("farklı ürünler" uyarısıyla), katalog listesi ve detay (broşür sayfaları: kaydırma, yakınlaştırma, küçük resim şeridi), market sayfası | Broşür sayfaları için küçük (`t/`, `c/`) ve büyük görsel doğru kullanılıyor; büyük listelerde akıcı kaydırma |
| **2.6 Asistan** | Sohbet ekranı, "yazıyor…" göstergesi, ürün kartları, hızlı yanıt düğmeleri, `effective_query` ile devam soruları, telefonda saklanan geçmiş (temizlenebilir) | Sitedeki sohbet kutusuyla aynı davranış; ağ hatasında mesaj kaybolmuyor |
| **2.7 Favoriler + bildirim + ayarlar** | Favori kuralları (bölüm 2), WorkManager kontrolü, bildirim ayarı, önbelleği temizleme, Hakkında / Gizlilik / Koşullar (`/pages`), sürüm | Dört favori durumunun hepsi doğru görünüyor; adaylar favoriyi değiştirmiyor; bildirim varsayılan kapalı |
| **2.8 Kalite ve performans** | Birim testleri (ViewModel, depo, favori mantığı), ana akışlar için Compose arayüz testleri, Baseline Profile, erişilebilirlik (TalkBack etiketleri, büyük yazı), açık/koyu tema | Orta seviye cihazda soğuk açılış ≤ 2 sn hedefi, kaydırmada takılma yok, testler yeşil |
| **2.9 Gerçek API ile uçtan uca** | `staging`/`prod` çeşidi gerçek API'ye bağlanır | **Ön koşul:** API'nin hazırlık veya canlı sunucuya yüklenmesi; bu **ayrı onay** ister (karar 7) |
| **2.10 Yayın hazırlığı** | İmzalı AAB (Google Play) ve APK, Play App Signing, Veri Güvenliği formu bilgileri, mağaza metni ve ekran görüntüleri, gizlilik metni güncellemesi | Play iç test kanalına yükleme |

**Öncelik:** 2.1 → 2.2 → 2.3 → 2.4 ilk kullanılabilir sürümü verir (göz atma, arama, ürün detay). 2.5–2.7 tam özellik setini tamamlar. 2.8–2.10 yayına hazırlar.

## 4. Test stratejisi
- **Sözleşme testleri:** Uygulamanın API JSON'unu doğru okuduğu, Rev3'ten üretilen örnek JSON'larla sürekli denetlenir.
- **Birim testleri:** Favori durumları ve adayların otomatik eşleşmemesi, önbellek süreleri, hata eşlemesi, Paging sınırları.
- **Arayüz testleri:** Ana sayfa → ürün detay; arama → filtre → sayfalama; favori ekleme ve durum; çevrimdışı uyarısı.
- **Cihaz testi:** Sizin telefonunuz + Android 7.0 (API 24) ve güncel sürüm emülatörü. Gerçek cihazda denenmeyen bir özellik "test edildi" diye raporlanmaz.

## 5. Riskler ve kararlar

| Risk / karar | Etki | Öneri |
|---|---|---|
| Derleme ortamı (Ö2) | Geri bildirim hızı | B seçeneği |
| API'nin gerçek sunucuya yüklenmesi | 2.9 ve yayın buna bağlı | FAZ 2'nin ortasında, hazırlık için bir alt klasörde (ayrı onay) |
| Cloudflare SSL modu / `mod_remoteip` | API isteklerini reddedebilir (FAZ 1 risk 3.2) | FAZ 1 kapanışında doğrulanacak |
| Gizlilik metni | Play Veri Güvenliği formu ile tutarlılık | Telefonda saklanan favori ve sohbet geçmişinin metne eklenmesi (içerik kararı sizin) |
| Görsel eksikliği (çoğu üründe görsel yok) | Kart tasarımı | Market renginde yer tutucu ikon (sitedeki gibi); sahte görsel yok |
