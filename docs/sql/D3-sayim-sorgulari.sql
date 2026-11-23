-- Marketler.org – D.3 sayım sorguları
-- YALNIZCA SELECT. Veri değiştirmez. Ürün adı veya kişisel veri döndürmez; yalnızca sayı,
-- sürüm metni ve market adı (BİM/A101/ŞOK) döner. users, settings, admin_log, logs tablolarına dokunmaz.

-- T1: products / prices kayıt sayıları ve veritabanı sürümü
SELECT 'products' AS tablo, COUNT(*) AS adet FROM products
UNION ALL SELECT 'prices', COUNT(*) FROM prices
UNION ALL SELECT CONCAT('surum: ', VERSION()), 0;

-- T2: yayındaki kalem sayısı, farklı (market+hash) anahtar sayısı, 2+ ve 3+ katalogda tekrar eden anahtar
SELECT
  (SELECT COUNT(*) FROM items i JOIN catalogs c ON c.id=i.catalog_id WHERE c.hidden=0) AS kalem,
  (SELECT COUNT(DISTINCT c.store_id, i.hash) FROM items i JOIN catalogs c ON c.id=i.catalog_id WHERE c.hidden=0) AS anahtar,
  (SELECT COUNT(*) FROM (SELECT 1 FROM items i JOIN catalogs c ON c.id=i.catalog_id WHERE c.hidden=0
     GROUP BY c.store_id, i.hash HAVING COUNT(DISTINCT i.catalog_id) >= 2) x) AS tekrar_2plus,
  (SELECT COUNT(*) FROM (SELECT 1 FROM items i JOIN catalogs c ON c.id=i.catalog_id WHERE c.hidden=0
     GROUP BY c.store_id, i.hash HAVING COUNT(DISTINCT i.catalog_id) >= 3) x) AS tekrar_3plus;

-- T3: tutarlılık — aynı market+hash altında birden fazla ad yazımı
--     harf_farki    : yalnızca büyük/küçük harf farkı (beklenen; hash zaten küçük harften üretiliyor)
--     farkli_yazim  : harf farkı dışında da farklı ad (0 beklenir)
SELECT SUM(x.b > 1) AS harf_farki, SUM(x.ci > 1) AS farkli_yazim FROM (
  SELECT COUNT(DISTINCT BINARY i.name) AS b, COUNT(DISTINCT i.name) AS ci
  FROM items i JOIN catalogs c ON c.id=i.catalog_id
  GROUP BY c.store_id, i.hash) x;

-- T4: market bazında tekrar oranı
SELECT s.name AS market, COUNT(*) AS anahtar,
       SUM(k.n >= 2) AS tekrar_eden, ROUND(100*SUM(k.n >= 2)/COUNT(*),1) AS yuzde
FROM (SELECT c.store_id, i.hash, COUNT(DISTINCT i.catalog_id) AS n
      FROM items i JOIN catalogs c ON c.id=i.catalog_id WHERE c.hidden=0
      GROUP BY c.store_id, i.hash) k
JOIN stores s ON s.id=k.store_id GROUP BY s.name;
