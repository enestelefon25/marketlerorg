# FAZ 2.2.2 Düzeltme 1 Raporu: Arama Öneri Modu (B1)

**Telefon testinde bildirilen hata:** mock APK'da arama önerilerinde fiyat görünüyordu. B1 kararına göre önerilerde yalnızca ürün adı ve market adı görünmeliydi.

**Sürüm:** `0.2.2.2-d1` (versionCode **4**) · **Kod:** `ac50096` işlemesi.

**Kapsam:** Yalnızca arama ekranındaki öneri davranışı.
- Başka ekranlarda tasarım veya davranış değişikliği yok.
- PHP dosyaları, canlı site, veritabanı ve API dağıtımı değişmedi. Canlı API'ye istek gönderilmedi.
- FAZ 2.2.3'e geçilmedi.

## 1. Hatanın kaynağı (inceleme özeti)
- Öneri satırı bileşeni fiyat göstermiyordu. Mock APK'nın derlenmiş kodunda öneri fiyatını (`Suggestion.priceText`) okuyan kod yoktu.
- Ancak arama ekranında öneri bloğunun **hemen altında** süzgeçler, "N ürün bulundu" ve sonuç listesi açık kalıyordu. Sonuç satırları (`ProductRow`) fiyat gösteriyor.
- Yazarken sonuç listesi güncellenmediği için, yazılan metinle ilgisiz fiyatlı satırlar önerilerin devamı gibi görünüyordu.

## 2. Uygulanan davranış (onayladığınız 7 madde)
| # | Davranış | Uygulama |
|---|---|---|
| 1 | Yazmaya başlayınca öneriler açılır | Kutuya yazı girilince **öneri modu** açılır; öneriler 300 ms beklemeyle istenir |
| 2 | Öneriler açıkken süzgeç, ürün sayısı ve fiyatlı sonuçlar tamamen gizli | Öneri modunda ekranda yalnızca arama kutusu ve öneri paneli var; süzgeçler, sayı ve sonuç listesi **hiç çizilmez** |
| 3 | Önerilerde yalnızca ad ve market | Satırda ürün adı ve market adı. Mock veri kaynağı önerilere artık fiyat **koymuyor** (ek güvence; model alanı API sözleşmesi için korunuyor) |
| 4 | Öneriye dokununca arama, fiyatlı sonuçlar | Önerinin adıyla arama yapılır, normal sonuç ekranı açılır |
| 5 | Ara düğmesi normal sonuçları açar | Klavyedeki "Ara" → normal sonuç ekranı |
| 6 | Öneri bulunamazsa bilgilendirme | "Bu yazıyla eşleşen öneri bulunamadı. Aramak için klavyedeki Ara düğmesine basın." Tek harfte: "Öneri için en az 2 harf yazın…" Yüklenirken: "Öneriler aranıyor…" Hiçbirinde fiyat yok |
| 7 | Kutu temizlenince normal ekran | "Temizle" (✕) düğmesi veya yazıyı silmek: öneri modu kapanır, normal arama ekranı (boş sorgu, bütün aktif ürünler) açılır |

**Ek (aynı ekran, öneri modunun parçası):** Öneri modu açıkken telefonun **geri** tuşu yalnızca öneri modunu kapatır. Önceden arama ekranından çıkılıyordu; bu sayede klavye kapatılınca kullanıcı ekranda takılı kalmaz.

**Değişen dosyalar:**
- `app/.../ui/search/SearchScreen.kt`: öneri paneli ve modu; test işaretleri (`testTag`, görünüşü değiştirmez).
- `app/.../ui/search/SearchViewModel.kt`: öneri modu durumu, temizleme, öneri yükleniyor bilgisi.
- `core/data/.../MockRepository.kt`: önerilerde fiyat alanı boş.
- `app/build.gradle.kts`: versionCode 4, versionName `0.2.2.2-d1`.

## 3. Testler

### 3.1 Önceki test neden yetersizdi?
`EkranVeriTest.onerideFiyatGosterilmez`:
1. **Önerilerin çizildiğini kanıtlamıyordu.** Beklediği "Kirlitest Deterjan 3 kg" ve "A101" metinleri sonuç listesinde de vardı; öneri hiç çıkmasa da test geçebilirdi.
2. **Yalnızca iki sabit metne bakıyordu** ("89,90 TL" ve "0,00 TL"). Ekrandaki başka fiyatları ("275,00 TL" gibi) görmüyordu.
3. **Bileşene bakıyordu, ekrana değil.** "Öneri satırı fiyat göstermiyor" doğruydu. Ama "öneriler açıkken kullanıcı ekranda fiyat görmemeli" şartını hiç sınamadı; önerilerin altında açık kalan sonuç listesi düşünülmemişti.
4. **Mock verisiyle hiç çalışmadı.** Mock sürümünde de çalışıyordu ama hep kayıtlı API yanıtlarıyla; telefondaki mock APK'nın gerçek verisi denenmedi.

Bu test kaldırıldı; yerine aşağıdaki testler geldi.

### 3.2 Yeni testler: `OneriModuTest` (ekranın tamamı)
Testler yalnızca öneri bileşenine değil, **ekranda görünen bütün içeriğe** bakar: her metin, arama kutusundaki yazı ve erişilebilirlik açıklamaları toplanır.
- Fiyat **kalıbına** uyan hiçbir şey olmamalıdır: "275,00 TL", "≈ 55,00 TL/L", "-%20", "Fiyat belirtilmemiş" gibi. Belirli bir fiyat değerine bakılmaz.
- Süzgeçler ve ürün sayısı da ayrıca aranır.
- Her öneri satırında yalnızca 1–2 metin (ad, market) olduğu ve satırların gerçekten çizildiği satır sayısıyla doğrulanır.

Aynı 8 senaryo **iki ayrı veri kaynağıyla** çalışır:
- `OneriModuMockTest`: mock sürümünün veri kaynağı.
- `OneriModuKayitliApiTest`: yerel Rev3 kopyasının kayıtlı gerçek yanıtları; gerçek Retrofit, OkHttp ve `RemoteRepository` ile.

| Senaryo | Düzeltme öncesi kod (`450e9c5`) | Düzeltme sonrası |
|---|---|---|
| Yazınca yalnızca öneriler; ekranda hiç fiyat yok; süzgeç ve sayı gizli | **BAŞARISIZ.** Ekran dökümü: öneriler, ardından "Market, Kategori, Durum: Aktif… 275,00 TL…" | Geçti |
| Öneriye dokununca fiyatlı sonuçlar | Geçti | Geçti |
| "Ara" düğmesi normal sonuçlar (öncesinde ekranda fiyat yok) | **BAŞARISIZ** (Ara'dan önce fiyat görünüyor) | Geçti |
| Öneri yoksa bilgilendirme, fiyat yok | **BAŞARISIZ** (bilgilendirme yok) | Geçti |
| Tek harfte bilgilendirme, fiyat yok | **BAŞARISIZ** | Geçti |
| Temizle düğmesi → normal ekran | Geçti | Geçti |
| Yazı silinince → normal ekran | Geçti | Geçti |
| Aramadan sonra yeniden yazınca yine yalnızca öneriler | **BAŞARISIZ** | Geçti |
| **Toplam (her kaynak ayrı)** | **mock 3/8, kayıtlı API 3/8** | **mock 8/8, kayıtlı API 8/8** |

**Not:** Düzeltme öncesi koda test için **yalnızca test işaretleri** (`testTag`) eklendi; davranışına dokunulmadı. Başarısız olan senaryolar, telefonda bildirdiğiniz hatayı birebir yakalıyor.

### 3.3 Tüm test sonuçları (`ac50096`)
| Test | Sonuç |
|---|---|
| Gerçek Android derlemesi: `clean` + mock / yerel / canli debug + canli release (R8), kaydedilmiş koddan temiz kopya | **Başarılı** |
| `OneriModuMockTest` + `OneriModuKayitliApiTest` | **16/16**, her sürümde (mock, yerel, canli) |
| `EkranVeriTest` (11; eski öneri testi çıkarıldı) | **11/11 × 3** |
| `NavigationTest` | **5/5 × 3** |
| `CanliErisimKapaliTest`: canlı kapalıyken 0 ağ denemesi + izleme kontrolü | **2/2** |
| `core/data`: veri kuralları 5, mock 14, ağ katmanı 14, yönlendirme güvenliği 5 | **38/38** |
| Yerel API'ye karşı uçtan uca testler (`MK_YEREL_API`) | Bu turda çalıştırılmadı (veri katmanı değişmedi; yalnızca mock öneri fiyatı boşaltıldı). Atlandı olarak raporlandı |
| Telefonda deneme | **Sizde** |

## 4. APK'lar
| | mock | yerel | canli |
|---|---|---|---|
| Dosya | `Marketler-FAZ2.2.2-d1-mock-debug.apk` | `Marketler-FAZ2.2.2-d1-yerel-debug.apk` | `Marketler-FAZ2.2.2-d1-canli-debug.apk` |
| SHA-256 | `cbe8e84b6d52f8d3f15662810982592d359ad1855b31f95c3c86bfa466493d3c` | `e9e185fa252ec180ec4117397bc537be4828c35a18aa4f380385a6f86b89aab4` | `9b8bce9355fd394ef7804baa6160275e851497c30eb8f2c8823ca5d3dd2c30fd` |
| Boyut (bayt) | 17.762.869 | 17.763.414 | 17.763.478 |
| applicationId | `org.marketler.app.mock.dev` | `org.marketler.app.yerel.dev` | `org.marketler.app.dev` |
| versionCode / versionName | 4 / `0.2.2.2-d1-mock-debug` | 4 / `0.2.2.2-d1-yerel-debug` | 4 / `0.2.2.2-d1-canli-debug` |
| İmza SHA-256 | `b42fcad39d46e2f6b9204216b032818590f68b322e2321a1324bb32a30cd4979` (v2 doğrulandı) | aynı | aynı |

## 5. Manifest, ağ güvenliği ve kurulum uyumluluğu (yeniden kontrol)
Tam çıktı `docs/FAZ2.2.2-D1-GUVENLIK-KONTROLU.txt` dosyasında.

**Manifest ve ağ güvenliği:** 2.2.2 ile aynı sonuç.

| Kontrol | mock | yerel | canli debug | canli release |
|---|---|---|---|---|
| INTERNET izni | yok ✔ | var | var | var |
| `usesCleartextTraffic=false` | ✔ | ✔ | ✔ | ✔ |
| Ağ güvenlik ayarı | yok ✔ | HTTP yalnızca `10.0.2.2` ✔ | HTTP kapalı; `marketler.org` ✔ | aynı ✔ |
| `10.0.2.2` içeriyor mu | hayır ✔ | evet (beklenen) | **hayır ✔** | **hayır ✔** |
| Gizli bilgi taraması | temiz ✔ | temiz ✔ | temiz ✔ | temiz ✔ |
| OkHttp / Retrofit kodu | var* | var | var | **yok** (erişim kapalı; R8 çıkardı) ✔ |

\* mock debug APK'da OkHttp ve Retrofit sınıfları bulunuyor, çünkü mock veri kaynağı ile ağ katmanı aynı modülde. Ancak mock'un INTERNET izni yok ve mock veri kaynağı ağ kodunu çağırmıyor. 2.2.2'de de böyleydi.

**Kurulum uyumluluğu:**
| Telefonda kurulu | Yeni APK | Sonuç | Neden |
|---|---|---|---|
| 2.2.2 canli (`org.marketler.app.dev`, versionCode 3) veya FAZ 2.1 düzeltme 3 (versionCode 1) | **d1 canli** | **Üzerine kurulur** | Aynı kimlik, aynı imza, 4 > 3 > 1 |
| 2.2.2 mock (`org.marketler.app.mock.dev`, versionCode 3) | **d1 mock** | **Üzerine kurulur** | Aynı kimlik, aynı imza, 4 > 3 |
| 2.2.2 yerel | d1 yerel | Üzerine kurulur | Aynı kimlik, aynı imza, 4 > 3 |
| Mock ve canlı birlikte | — | **Yan yana kurulur** | Farklı kimlik, farklı provider yetkisi ve izin adları, `sharedUserId` yok |
| WebView uygulaması (`org.marketler.app`) | — | Etkilenmez | Ayrı uygulama |

Hiçbir uygulamayı kaldırmanız gerekmez.

## 6. Telefon testi (yalnızca bu düzeltme için kısa liste)
1. [ ] **d1 mock** APK'sını kurun; mevcut "Marketler (örnek)" uygulamasının üzerine kurulmalı.
2. [ ] Ara sekmesine geçin. Arama kutusuna **"sü"** yazın:
   - yalnızca öneri listesi görünüyor;
   - her satırda ürün adı ve market adı var;
   - ekranın **hiçbir yerinde fiyat yok;**
   - süzgeçler ve "N ürün bulundu" görünmüyor.
3. [ ] Bir öneriye dokunun → o ürünün aramasının sonuçları **fiyatlarıyla** açılıyor.
4. [ ] Kutuya tekrar yazın (ör. "çay") → yine yalnızca öneriler. Klavyedeki **Ara** → sonuçlar fiyatlarıyla.
5. [ ] **"zzqq"** yazın → "eşleşen öneri bulunamadı" bilgisi; fiyat yok. **"s"** yazın → "en az 2 harf" bilgisi.
6. [ ] ✕ (Temizle) düğmesine basın → normal arama ekranı (süzgeçler, ürün sayısı, ürünler). Yazıyı klavyeyle silmek de aynı sonucu vermeli.
7. [ ] Yazarken telefonun **geri** tuşu → öneriler kapanıyor (arama ekranında kalınıyor).
8. [ ] **d1 canli** APK'sını kurun; "Marketler.org" üzerine kurulmalı. Açılıyor, "Canlı veri henüz yayında değil" mesajı görünüyor; Ara sekmesinde yazınca çökme yok.
