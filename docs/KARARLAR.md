# Marketler.org Native: Geçerli Kararlar Kaydı

**Esas belge sırası:** Bu kayıt > FAZ 1 Rev3 kodu ve belgeleri (`e95404e`, `FAZ1-UC-NOKTALAR.md`, `FAZ1-TEST-RAPORU.md`) > FAZ 0 raporu.
FAZ 0 raporu, projenin **başlangıç mimari belgesi** olarak geçerlidir. FAZ 0 önerileri ile sonradan verilen kararlar çeliştiğinde **sonraki kararlar** geçerlidir.

| # | Karar | Kaynak |
|---|---|---|
| 1 | API **Rev3** temel alınır. Yeni özellik veya gereksiz yeniden yapılandırma (refactoring) yapılmaz; kod donmuştur | Kullanıcı, Rev3 sonrası |
| 2 | **14 uç nokta** korunur (`/meta` ve `/compare/{id}` kaldırıldı; karşılıkları `FAZ1-UC-NOKTALAR.md` bölüm 0'da) | Kullanıcı |
| 3 | `mt_featured()` taşıması (`ortak-veri.php`) **tamamlanmış** kabul edilir | Kullanıcı |
| 4 | Favori anahtarı **`itemId:storeId:hash`** biçimindedir. Hash tek başına ürün kimliği değildir; otomatik ürün eşleştirmesi yapılmaz, adaylar `confirmed:false` olarak döner | Kullanıcı, Rev2–Rev3 |
| 5 | İlk sürümde **Firebase ve gerçek push bildirimi yok**; yerel bildirim (WorkManager) kullanılır | Kullanıcı |
| 6 | Mevcut **WebView projesine** (`MarketlerAndroid/`) dokunulmaz. Native proje `MarketlerNative/` altındadır | Kullanıcı |
| 7 | Canlı siteye ve veritabanına **izinsiz müdahale edilmez** | Kullanıcı |
| 8 | Gerçek veritabanı dökümü gereksiz yere istenmez. Önce D.3 sayım sorgularının güvenli biçimde çalıştırılması değerlendirilir | Kullanıcı |
| 9 | Gerçek sunucu bilgileri doğrulanmadan PHP, MariaDB veya Cloudflare uyumluluğu hakkında **kesin hüküm verilmez** | Kullanıcı |
| 10 | Hedef: API'yi uzatmak değil; **çalışan, hızlı, güvenilir ve yayınlanabilir bir Android uygulaması** | Kullanıcı |
| 11 | Faz adlandırması güncellendi: **FAZ 2 = Android uygulaması geliştirme** (FAZ 0 raporundaki "FAZ 2: API testleri" maddesi FAZ 1 içinde tamamlandı) | Kullanıcı |
| 12 | Önceki onaylı davranışlar geçerlidir: `ara.php` eşitlikte `i.id`; hız sınırı ve HTTPS yalnızca güvenilir vekil başlığıyla; hız sınırı dosya hatasında 503 | Rev2–Rev3 |
| 13 | **Kabul edilmiş temel sürüm (Android): FAZ 2.2.2 düzeltme 1.** Kod `ac50096` (git etiketi `faz2.2.2-d1-kabul`), versionCode 4, `0.2.2.2-d1`. APK SHA-256: mock `cbe8e84b…3d3c`, yerel `e9e185fa…aab4`, canli `9b8bce93…30fd` (tam değerler `FAZ2.2.2-D1-RAPORU.md`). Telefonda bütün testler başarılı. Canlı API erişimi bu sürümde **kapalı** | Kullanıcı (telefon kabul testi) |
| 14 | Canlı API, FAZ 1 canlı ortam uygunluk kontrolleri (`CANLI-UYGUNLUK-KONTROLLERI.md`) tamamlanmadan **açılmaz** | Kullanıcı |

**Açık (karar bekleyen) konular:**
- `cmp_groups()` sıralama düzeltmesi (`inc.php` değişikliği gerektirir).
- API için ayrı, yalnızca `SELECT` yetkili veritabanı kullanıcısı (kod değişikliği gerektirir).
- Uygulama için gizlilik metninin güncellenmesi.
