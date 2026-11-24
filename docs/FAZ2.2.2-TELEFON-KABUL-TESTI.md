# FAZ 2.2.2 Telefon Kabul Testi: Hazırlık

**Bu belgenin kapsamı:** Yalnızca teslimat ve telefon testine hazırlık.
- Uygulama kodu **değişmedi.** APK'lar FAZ 2.2.2 raporundaki dosyaların aynısı; SHA-256 değerleri aynı.
- Yalnızca iki doğrulama testi eklendi (bölüm 5 ve 6).
- PHP dosyalarına, canlı siteye, veritabanına ve API dağıtımına dokunulmadı.
- FAZ 2.2.3'e geçilmedi.

## 1. İndirme bağlantıları
GitHub Releases, `marketlerorg` deposu, `faz2.2.2` sürümü (ön sürüm). Önceki sürümler (`faz2.1-duzeltme3`) değiştirilmedi.

| APK | Doğrudan indirme |
|---|---|
| mock | https://github.com/enestelefon25/marketlerorg/releases/download/faz2.2.2/Marketler-FAZ2.2.2-mock-debug.apk |
| yerel | https://github.com/enestelefon25/marketlerorg/releases/download/faz2.2.2/Marketler-FAZ2.2.2-yerel-debug.apk |
| canli | https://github.com/enestelefon25/marketlerorg/releases/download/faz2.2.2/Marketler-FAZ2.2.2-canli-debug.apk |
| Kaynak zip | https://github.com/enestelefon25/marketlerorg/releases/download/faz2.2.2/MarketlerNative-FAZ2.2.2.zip |

Sürüm sayfası: https://github.com/enestelefon25/marketlerorg/releases/tag/faz2.2.2

## 2. APK bilgileri
Değerler `aapt2 dump badging` ve `apksigner verify --print-certs` ile APK dosyalarından okundu.

| | mock | yerel | canli |
|---|---|---|---|
| Dosya | `Marketler-FAZ2.2.2-mock-debug.apk` | `Marketler-FAZ2.2.2-yerel-debug.apk` | `Marketler-FAZ2.2.2-canli-debug.apk` |
| Dosya SHA-256 | `02946ce8359bd7584c6b50dccb6ffcba124d3574b8856fc722c394e6c2c7e65b` | `1d3a4b6d168e515abe2d36a3ac52f7af239f7ed42d89376d512c0b6ca126fb08` | `fe0394496aa4d610241043f4f152943c297c2e4835d4c8a55f1ef5f4ab2b271c` |
| Boyut (bayt) | 17.762.857 | 17.763.406 | 17.763.466 |
| applicationId | `org.marketler.app.mock.dev` | `org.marketler.app.yerel.dev` | `org.marketler.app.dev` |
| versionCode | 3 | 3 | 3 |
| versionName | `0.2.2.2-mock-debug` | `0.2.2.2-yerel-debug` | `0.2.2.2-canli-debug` |
| Telefondaki ad | Marketler (örnek) | Marketler (yerel) | Marketler.org |
| İmza sertifikası SHA-256 | `b42fcad39d46e2f6b9204216b032818590f68b322e2321a1324bb32a30cd4979` | aynı | aynı |
| İmza | Android Debug (v2 şeması doğrulandı) | aynı | aynı |
| INTERNET izni | yok | var | var |
| minSdk / targetSdk | 24 / 36 | 24 / 36 | 24 / 36 |

İmza bir **debug** anahtarıdır, yalnızca test içindir. Play Store'a yüklenecek sürüm ayrı bir yayın anahtarıyla imzalanacak.

## 3. Mock ve canlı APK aynı telefonda yan yana kurulabilir mi? **Evet.**
Android'in iki uygulamayı birlikte kurmayı reddettiği üç durum APK'lardan tek tek kontrol edildi:

| Kontrol | mock | canli | Sonuç |
|---|---|---|---|
| Uygulama kimliği | `org.marketler.app.mock.dev` | `org.marketler.app.dev` | Farklı ✔ |
| İçerik sağlayıcı (provider) yetkisi | `org.marketler.app.mock.dev.androidx-startup` | `org.marketler.app.dev.androidx-startup` | Farklı ✔ (aynı olsaydı `INSTALL_FAILED_CONFLICTING_PROVIDER`) |
| Uygulamanın tanımladığı izin | `org.marketler.app.mock.dev.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `org.marketler.app.dev.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Farklı ✔ |
| `sharedUserId` | yok | yok | ✔ |

- **yerel** APK'sı da aynı şekilde ayrı (`org.marketler.app.yerel.dev`). Üçü birlikte kurulabilir.
- **WebView uygulaması** (`org.marketler.app`) dördüncü, ayrı bir kimliktir. Hiçbiri onun yerine geçmez.

## 4. Telefondaki FAZ 2.1 düzeltme 3 uygulamasının üzerine hangisi kurulur? **Yalnızca canli.**
Telefondaki düzeltme 3 uygulaması:
- kimlik `org.marketler.app.dev`;
- versionCode 1, versionName `0.2.1-mock-debug`;
- imza sertifikası `b42fcad3…4979`.

Telefonda 2.2.1 kuruluysa: aynı kimlik, versionCode 2, aynı imza.

| APK | Düzeltme 3 / 2.2.1'in üzerine kurulur mu? | Neden |
|---|---|---|
| **canli** | **Evet, günceller** | Aynı kimlik, aynı imza, daha yüksek sürüm (3 > 1 ve 3 > 2). Eski uygulamanın yerine geçer |
| mock | Hayır; **ayrı uygulama** olarak yanına kurulur | Farklı kimlik |
| yerel | Hayır; ayrı uygulama olarak yanına kurulur | Farklı kimlik |

- **Dikkat:** canli APK'sı kurulunca o simge artık örnek veri değil, "Canlı veri henüz yayında değil" mesajı gösterir. Örnek veriyle denemek için **mock** APK'sını kurun; simgesi "Marketler (örnek)".
- **İstisna:** Telefondaki `org.marketler.app.dev` uygulamasını bir gün **kendi bilgisayarınızda** Android Studio ile derleyip kurduysanız, onun imzası farklıdır. Bu durumda "Uygulama yüklenmedi" hatası alırsınız. Önce o uygulamayı kaldırın.
- Bu turdaki bağlantılardan kurduğunuz APK'ların hepsi aynı imzalıdır.

## 5. OkHttp yönlendirme güvenliği
**Yapılandırma** (`core/data/.../remote/ApiClient.kt`):
- `followRedirects(false)`: OkHttp hiçbir 3xx yanıtını izlemez.
- `followSslRedirects` yalnızca yönlendirme izlenirken anlam taşıdığı için o da etkisizdir.
- 3xx yanıtı uygulamaya **hata** olarak gelir (`DataError.Server(3xx)`, "Sunucuda beklenmeyen bir hata oluştu."). Hiçbir veri gösterilmez.

**Ek kilitler:**
- Taban adres dışındaki bir alan adına veya üretimde HTTPS dışına giden her istek, ağa çıkmadan reddedilir (`lockHost`, `requireHttps`).
- Üretimde `http://` taban adresle istemci hiç kurulamaz.
- `canli` APK'sının ağ güvenlik ayarı HTTP'yi her adrese kapatır.

**Test** (`YonlendirmeGuvenligiTest`, 5/5 geçti). Uygulamanın kullandığı istemci yapılandırmasıyla, iki ayrı sahte sunucu: "API" ve yönlendirmenin gösterdiği "hedef".

| Senaryo | Sonuç |
|---|---|
| 301, 302, 303, 307, 308 → başka alan adı | İstek **izlenmedi**; hedef sunucuya **0 istek**; hata `Server(3xx)` |
| 302 → `http://marketler.org/...` (HTTP'ye düşürme) | İzlenmedi; hata |
| 301 → aynı sunucuda başka yol | İzlenmedi; hata |
| 307 → göreli adres | İzlenmedi; hata |
| Üretim yapılandırmasında `http://` taban adres | İstemci kurulamadı (`IllegalArgumentException`) |

**Sonuç:** marketler.org dışına veya HTTP adresine yönlendirme yapılırsa istek **gönderilmez.** Uygulama hata gösterir.

## 6. Canlı APK'da erişim kapalıyken ağ isteği yok: yeniden doğrulandı
| Doğrulama | Yöntem | Sonuç |
|---|---|---|
| Uygulama yapılandırması | `canli` debug derlemesinde `VERI_KAYNAGI="canli"`, `CANLI_API_ACIK=false`; uygulamanın deposu `DisabledRepository` | ✔ |
| **Uygulamanın tamamı çalışırken bağlantı denemesi** (`CanliErisimKapaliTest`, yalnızca canli sürümünde) | Gerçek `MainActivity` ve `MarketlerApplication` açıldı. Ana sayfa, Ara sekmesi ve "süt" yazımı (öneri dahil), Kataloglar, Karşılaştır, Asistan ve Ana Sayfa'ya dönüş, "Tekrar dene" denendi. JVM'deki bütün bağlantı denemeleri (OkHttp ve HttpURLConnection) vekil seçici ile izlendi | **0 bağlantı denemesi** ✔ |
| İzleme yönteminin kendisi (kontrol testi) | Aynı izleyiciyle bilerek gerçek bir istek yapıldı | İstek **yakalandı** ✔. Yani "0" sonucu güvenilir |
| Ekranlarda kaynağa istek | `EkranVeriTest.canliKapaliIkenAgaCikilmaz`: sahte sunucunun istek sayısı | 0 ✔ |
| Küçültülmüş (R8) canlı yayın derlemesi | APK içeriği tarandı | OkHttp, Retrofit ve `RemoteRepository` kodu **hiç yok;** API adresi yok. Yani erişim kapalıyken yayın sürümü fiziksel olarak HTTP isteği yapamaz ✔ |

## 7. Telefon kabul testi: kısa kontrol listesi
Sırayla yapın. Her madde için "tamam" veya gördüğünüz sorunu yazın; mümkünse ekran görüntüsü ekleyin.

**Hazırlık**
1. [ ] Telefondaki Android sürümünü not edin (Ayarlar → Telefon hakkında).
2. [ ] Üç APK'yı bölüm 1'deki bağlantılardan indirin.
3. [ ] **canli** APK'sını kurun. Mevcut "Marketler.org" test uygulamasının üzerine kurulmalı; kaldırmanız gerekmemeli.
4. [ ] **mock** APK'sını kurun. Ayrı bir "Marketler (örnek)" simgesi oluşmalı.
5. [ ] WebView uygulamasının hâlâ yerinde ve çalışır olduğunu kontrol edin.

**mock ("Marketler (örnek)")**
6. [ ] Açılış ekranı → ana sayfa. Üstte "Örnek veri (mock)…" uyarısı var. Sayfa yana kaymıyor.
7. [ ] Ana sayfada arama kutusu → arama ekranı → alt menüden **Ana Sayfa** → ana sayfaya dönüyor.
8. [ ] Ara sekmesinde "sü" yazın. Öneri listesinde **yalnızca ürün adı ve market** var, **fiyat yok.**
9. [ ] "süt" araması → sonuçlar. Süzgeçler (market, kategori, durum, sıralama) çalışıyor. Aşağı kaydırınca yeni ürünler yükleniyor.
10. [ ] Bir ürüne dokunun → ürün detayı. Fiyat, kampanya tarihleri, "Diğer marketlerde" görünüyor. Geri tuşu çalışıyor.
11. [ ] Kataloglar, Karşılaştır, Asistan sekmeleri "sonraki aşamada" ekranı gösteriyor.
12. [ ] **Uçak modunu açın.** Uygulamayı kapatıp açın; her şey aynen çalışıyor. Uçak modunu kapatın.

**canli ("Marketler.org")**
13. [ ] Uygulama açılıyor, çökmüyor. Ana sayfada **"Canlı veri henüz yayında değil…"** mesajı var.
14. [ ] Ara sekmesi aynı mesajı gösteriyor. Yazı yazınca öneri listesi çıkmıyor; çökme yok.
15. [ ] "Tekrar dene" düğmesine basınca aynı mesaj geliyor; çökme yok.
16. [ ] Alt menüdeki beş sekmenin hepsi açılıyor.
17. (İsteğe bağlı) Ayarlar → Uygulamalar → Marketler.org → Mobil veri kullanımı: bu uygulama için veri kullanımı 0 B veya çok küçük olmalı.

**Bitiş**
18. [ ] Sonuçları, Android sürümünü ve varsa ekran görüntülerini gönderin.

**yerel APK'sı** telefonda **test edilmez.** Yalnızca emülatörde ve bilgisayarda yerel API açıkken anlamlıdır; telefonda bağlantı hatası gösterir.
