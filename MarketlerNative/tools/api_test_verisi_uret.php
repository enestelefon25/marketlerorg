<?php
/* FAZ 2.2.1: Android ağ katmanı testleri için Rev3 API'nin GERÇEK HTTP yanıtlarını kaydeder (API kodu DEĞİŞTİRİLMEZ, canlıya bağlanılmaz).
   ⚠ Yalnızca izole yerel geliştirme ortamında çalıştırın (tests/run.php ile aynı güvenlik kuralları: yalnızca yerel veritabanı sunucusu,
     benzersiz veritabanı, işaret tablosu ile silme güvencesi, sonunda temizlik).
   Kullanım: php MarketlerNative/tools/api_test_verisi_uret.php --site=/yol/marketler.org [--acik-kal=SANİYE]
   --acik-kal: kayıttan sonra yerel API'yi (http://127.0.0.1:8099/api/v1/) belirtilen süre açık tutar; Android uçtan uca testi
               (core/data, MK_YEREL_API ortam değişkeni) bu sürede çalıştırılır. Süre bitince her şey temizlenir.
   Çıktı: MarketlerNative/android/core/data/src/test/resources/api-yerel/  (index.json + yanıt gövdeleri)
   Veri: tests/fixtures/seed.php SENTETİK verisi (senaryo '2') + bilerek eklenmiş "kirli" kayıtlar (NULL ad/fiyat/renk/başlık/kategori).
   Kirli kayıtlar, veritabanı şemasında DEFAULT NULL olan kolonların API'den nasıl çıktığını belgelemek içindir. */

error_reporting(E_ALL); ini_set('display_errors', '1'); date_default_timezone_set('Europe/Istanbul');
$SERVER = dirname(__DIR__).'/server';
require "$SERVER/tests/guard.php"; require "$SERVER/tests/fixtures/seed.php";
$opt = getopt('', ['site:', 'acik-kal:']); $SRC = rtrim($opt['site'] ?? '', '/');
if (!$SRC || !is_file("$SRC/inc.php")) { fwrite(STDERR, "--site=<orijinal site klasörü> gerekli\n"); exit(2); }
$DBH = getenv('MK_TEST_DB_HOST') ?: 'localhost'; $DBU = getenv('MK_TEST_DB_USER') ?: 'root'; $DBP = getenv('MK_TEST_DB_PASS') ?: '';
if (!mk_guard_local_host($DBH)) { fwrite(STDERR, "GÜVENLİK: yalnızca yerel veritabanı sunucusu. Hiçbir bağlantı kurulmadı.\n"); exit(3); }
$TOKEN = bin2hex(random_bytes(6)); $DB = 'mk_api_test_'.$TOKEN; $WORK = rtrim(sys_get_temp_dir(), '/').'/mk_api_test_'.$TOKEN;
$OUT = dirname(__DIR__).'/android/core/data/src/test/resources/api-yerel';
if (file_exists($WORK) || !mkdir("$WORK/tmp", 0700, true)) { fwrite(STDERR, "geçici klasör oluşturulamadı\n"); exit(3); }
$adm = new PDO("mysql:host=$DBH;charset=utf8mb4", $DBU, $DBP, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
$made = false; $srv = null;
register_shutdown_function(function () use (&$srv, &$made, $adm, $DB, $TOKEN, $WORK) {
  if (is_resource($srv)) { proc_terminate($srv); proc_close($srv); }
  if ($made && mk_guard_can_drop_db($adm, $DB, $TOKEN)) $adm->exec("DROP DATABASE `$DB`");
  if (mk_guard_can_rm_dir($WORK, $TOKEN)) mk_guard_rmtree($WORK);
});
if (mk_guard_db_exists($adm, $DB)) exit(3);
$adm->exec("CREATE DATABASE `$DB` CHARACTER SET utf8mb4"); $made = true;
$adm->exec("CREATE TABLE `$DB`.`".MK_GUARD_MARKER."` (belirtec CHAR(12) NOT NULL)"); $adm->prepare("INSERT INTO `$DB`.`".MK_GUARD_MARKER."` VALUES (?)")->execute([$TOKEN]);
exec('mariadb -h'.escapeshellarg($DBH).' -u'.escapeshellarg($DBU).($DBP !== '' ? ' -p'.escapeshellarg($DBP) : '').' '.escapeshellarg($DB).' < '.escapeshellarg("$SERVER/tests/fixtures/schema.sql"), $o, $rc);
if ($rc !== 0) { fwrite(STDERR, "şema yüklenemedi\n"); exit(1); }
exec('cp -a '.escapeshellarg($SRC).' '.escapeshellarg("$WORK/site").' && cp -a '.escapeshellarg("$SERVER/site").'/. '.escapeshellarg("$WORK/site/"));
$cfgOk = "<?php\ndefine('DB_HOST','".addslashes($DBH)."');\ndefine('DB_NAME','$DB');\ndefine('DB_USER','".addslashes($DBU)."');\ndefine('DB_PASS','".addslashes($DBP)."');\n";
file_put_contents("$WORK/site/config.php", $cfgOk);
$apiCfg = "$WORK/site/api/v1/config.php"; $ac0 = file_get_contents($apiCfg);
$setRate = function ($std) use ($apiCfg, $ac0) {
  file_put_contents($apiCfg, str_replace(["MK_API_ALLOW_HTTP = false", "'std' => 60, 'heavy' => 20, 'suggest' => 120"], ["MK_API_ALLOW_HTTP = true", "'std' => $std, 'heavy' => 100000, 'suggest' => 100000"], $ac0));
};
$setRate(100000);
$pdo = new PDO("mysql:host=$DBH;dbname=$DB;charset=utf8mb4", $DBU, $DBP, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
$seed = mk_seed($pdo, '2', ["$WORK/site"]);
$q = function ($sql, $a = []) use ($pdo) { $s = $pdo->prepare($sql); $s->execute($a); return $s; };
$last = function () use ($q) { return (int)$q('SELECT LAST_INSERT_ID()')->fetchColumn(); };
$live = function ($ss) use ($seed) { return $seed['catalogs'][$ss]['live'][0]; };
$catId = function ($slug) use ($q) { return (int)$q('SELECT id FROM categories WHERE slug=?', [$slug])->fetchColumn(); };
$item = function ($cid, $kid, $name, $price, $old = null) use ($q, $last) {
  $q('INSERT INTO items(catalog_id,cat_id,name,hash,price,old_price) VALUES(?,?,?,?,?,?)', [$cid, $kid, $name, $name === null ? null : md5(mb_strtolower($name)), $price, $old]); return $last();
};
/* --- kirli kayıtlar (yalnızca bu geçici test veritabanında) --- */
$K = [];
$K['ad_null'] = $item($live('bim'), $catId('gida'), null, '10.00');
$K['ad_bos'] = $item($live('a101'), $catId('gida'), '', '11.00');
$K['fiyat_null_bim'] = $item($live('bim'), $catId('temizlik'), 'Kirlitest Deterjan 3 kg', null);
$K['fiyat_var_a101'] = $item($live('a101'), $catId('temizlik'), 'Kirlitest Deterjan 3 kg', '89.90');
$K['fiyat_null_eski_var'] = $item($live('sok'), $catId('gida'), 'Kirlitest Bisküvi 200 g', null, '25.00');
$K['hash_null'] = $item($live('sok'), $catId('gida'), 'Kirlitest Hashsiz Çay 500 g', '45.00');
$q('UPDATE items SET hash=NULL WHERE id=?', [$K['hash_null']]);
$q('INSERT INTO categories(id,slug,name) VALUES(99,NULL,NULL)');
$K['kategori_null'] = $item($live('bim'), 99, 'Kirlitest Kategorisiz Ürün', '12.00');
$q('INSERT INTO stores(id,slug,name,color) VALUES(4,?,NULL,NULL)', ['kirli']);
$q('INSERT INTO catalogs(store_id,slug,title,label,cdate,source,hidden,images) VALUES(4,?,NULL,NULL,?,?,0,NULL)', ['kirli-test-katalogu', date('Y-m-d', time() - 86400), 'test']);
$kc = $last();
$K['market_null'] = $item($kc, $catId('gida'), 'Kirlitest Renksiz Market Ürünü', '15.00');

$srv = proc_open(['php', '-S', '127.0.0.1:8099', '-t', "$WORK/site", "$SERVER/tests/router.php"], [1 => ['file', "$WORK/srv.out", 'w'], 2 => ['file', "$WORK/srv.err", 'w']], $pp, "$WORK/site", ['TMPDIR' => "$WORK/tmp", 'PATH' => getenv('PATH')]);
for ($i = 0; $i < 50; $i++) { $f = @fsockopen('127.0.0.1', 8099); if ($f) { fclose($f); break; } usleep(100000); }

if (!is_dir($OUT)) mkdir($OUT, 0755, true);
foreach (glob("$OUT/*") as $f) if (is_file($f)) unlink($f);   /* yalnızca bu klasördeki eski kayıtlar */
$index = [];
$rec = function ($name, $path, $method = 'GET') use ($OUT, &$index) {
  $c = curl_init('http://127.0.0.1:8099/api/v1/'.$path); $hdr = [];
  curl_setopt_array($c, [CURLOPT_RETURNTRANSFER => true, CURLOPT_HTTPHEADER => ['Host: marketler.org'], CURLOPT_TIMEOUT => 60, CURLOPT_CUSTOMREQUEST => $method,
    CURLOPT_HEADERFUNCTION => function ($ch, $h) use (&$hdr) { $p = explode(':', $h, 2); if (count($p) === 2) $hdr[strtolower(trim($p[0]))] = trim($p[1]); return strlen($h); }]);
  $b = (string)curl_exec($c); $s = curl_getinfo($c, CURLINFO_HTTP_CODE); curl_close($c);
  file_put_contents("$OUT/$name.json", $b);
  $keep = array_intersect_key($hdr, array_flip(['content-type', 'cache-control', 'etag', 'retry-after']));
  $index[] = ['ad' => $name, 'yol' => $path, 'yontem' => $method, 'http' => $s, 'basliklar' => $keep, 'dosya' => "$name.json"];
  return [$s, json_decode($b, true)];
};
$rec('home', 'home'); $rec('markets', 'markets'); $rec('categories', 'categories');
for ($p = 1; ; $p++) { list($s, $j) = $rec("products_hepsi_s$p", "products?status=hepsi&limit=50&page=$p"); if ($s !== 200 || $p * 50 >= ($j['meta']['total'] ?? 0)) break; }
$rec('products_aktif_ilk', 'products?limit=24');
$rec('products_q_kirlitest', 'products?q=Kirlitest&status=hepsi');
$rec('products_q_bos_sonuc', 'products?q=zzzqqq');
$rec('products_sirala_asc', 'products?status=hepsi&sort=asc&limit=50');
foreach ($K as $k => $id) $rec("product_$k", "products/$id");
$rec('product_normal_karsilastirmali', 'products/'.$seed['items']['a101|live|Isıtek Airfryer XA4200 4.2 L']);
$rec('suggest_kirlitest', 'suggest?q='.rawurlencode('kirlitest'));
$rec('suggest_su', 'suggest?q='.rawurlencode('sü'));
$rec('suggest_kisa', 'suggest?q=s');
$rec('hata_404_urun', 'products/99999999');
$rec('hata_404_yol', 'yok');
$rec('hata_400_limit', 'products?limit=51');
$rec('hata_400_status', 'products?status=bilinmeyen');
$rec('hata_405_yontem', 'products', 'POST');
$setRate(1); $rec('hata_429_hiz', 'markets'); $setRate(100000);   /* bu dakikada zaten istek yapıldığı için sınır 1 iken hemen 429 */
file_put_contents("$WORK/site/config.php", "<?php\ndefine('DB_HOST','127.0.0.1');\ndefine('DB_NAME','$DB');\ndefine('DB_USER','mk_yok_kullanici');\ndefine('DB_PASS','yanlis');\n");
$rec('hata_503_veritabani', 'home');
file_put_contents("$WORK/site/config.php", $cfgOk);
file_put_contents("$OUT/index.json", json_encode(['aciklama' => 'Rev3 API yerel kopyasının gerçek HTTP yanıtları (tools/api_test_verisi_uret.php). SENTETİK veri + bilerek eklenmiş NULL alanlı kirli kayıtlar. Canlı sisteme bağlanılmadı.',
  'uretim' => date('c'), 'kirli_kayitlar' => $K, 'yanitlar' => $index], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT)."\n");
$err = trim((string)@file_get_contents("$WORK/srv.err"));
echo "Kaydedildi: ".count($index)." yanıt → $OUT\n";
foreach ($index as $r) printf("  %-34s HTTP %d\n", $r['ad'], $r['http']);
$keep = (int)($opt['acik-kal'] ?? 0);
if ($keep > 0) { echo "YEREL API HAZIR: http://127.0.0.1:8099/api/v1/ ($keep sn açık)\n"; flush(); sleep(min($keep, 3600)); }
if ($err !== '') { echo "--- sunucu günlüğü (son 20 satır) ---\n"; echo implode("\n", array_slice(explode("\n", $err), -20)), "\n"; }
