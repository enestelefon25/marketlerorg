<?php
/* Android uygulamasının MOCK verisini Rev3 API'den üretir (API kodu DEĞİŞTİRİLMEZ, canlı sisteme bağlanılmaz).
   ⚠ Yalnızca izole yerel geliştirme ortamında çalıştırın (tests/run.php ile aynı güvenlik kuralları: yerel sunucu, benzersiz veritabanı,
     işaret tablosu ile silme güvencesi, sonunda temizlik).
   Kullanım: php MarketlerNative/tools/mock_veri_uret.php --site=/yol/marketler.org
   Çıktı: MarketlerNative/android/core/data/src/main/resources/mock/*.json
   Veri: tests/fixtures/seed.php içindeki SENTETİK veri (uydurma markalar; gerçek fiyat/kişisel veri yok), senaryo '2'. */

error_reporting(E_ALL); ini_set('display_errors', '1'); date_default_timezone_set('Europe/Istanbul');
$SERVER = dirname(__DIR__).'/server';
require "$SERVER/tests/guard.php"; require "$SERVER/tests/fixtures/seed.php";
$opt = getopt('', ['site:']); $SRC = rtrim($opt['site'] ?? '', '/');
if (!$SRC || !is_file("$SRC/inc.php")) { fwrite(STDERR, "--site=<orijinal site klasörü> gerekli\n"); exit(2); }
$DBH = getenv('MK_TEST_DB_HOST') ?: 'localhost'; $DBU = getenv('MK_TEST_DB_USER') ?: 'root'; $DBP = getenv('MK_TEST_DB_PASS') ?: '';
if (!mk_guard_local_host($DBH)) { fwrite(STDERR, "GÜVENLİK: yalnızca yerel veritabanı sunucusu. Hiçbir bağlantı kurulmadı.\n"); exit(3); }
$TOKEN = bin2hex(random_bytes(6)); $DB = 'mk_api_test_'.$TOKEN; $WORK = rtrim(sys_get_temp_dir(), '/').'/mk_api_test_'.$TOKEN;
$OUT = dirname(__DIR__).'/android/core/data/src/main/resources/mock';
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
file_put_contents("$WORK/site/config.php", "<?php\ndefine('DB_HOST','".addslashes($DBH)."');\ndefine('DB_NAME','$DB');\ndefine('DB_USER','".addslashes($DBU)."');\ndefine('DB_PASS','".addslashes($DBP)."');\n");
$ac = file_get_contents("$WORK/site/api/v1/config.php");
file_put_contents("$WORK/site/api/v1/config.php", str_replace(["MK_API_ALLOW_HTTP = false", "'std' => 60, 'heavy' => 20, 'suggest' => 120"], ["MK_API_ALLOW_HTTP = true", "'std' => 100000, 'heavy' => 100000, 'suggest' => 100000"], $ac));
$pdo = new PDO("mysql:host=$DBH;dbname=$DB;charset=utf8mb4", $DBU, $DBP, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
mk_seed($pdo, '2', ["$WORK/site"]);
$srv = proc_open(['php', '-S', '127.0.0.1:8099', '-t', "$WORK/site", "$SERVER/tests/router.php"], [1 => ['file', "$WORK/srv.out", 'w'], 2 => ['file', "$WORK/srv.err", 'w']], $pp, "$WORK/site", ['TMPDIR' => "$WORK/tmp", 'PATH' => getenv('PATH')]);
for ($i = 0; $i < 50; $i++) { $f = @fsockopen('127.0.0.1', 8099); if ($f) { fclose($f); break; } usleep(100000); }

function get($path) {
  $c = curl_init('http://127.0.0.1:8099/api/v1/'.$path); curl_setopt_array($c, [CURLOPT_RETURNTRANSFER => true, CURLOPT_HTTPHEADER => ['Host: marketler.org'], CURLOPT_TIMEOUT => 60]);
  $b = curl_exec($c); $s = curl_getinfo($c, CURLINFO_HTTP_CODE); curl_close($c);
  $j = json_decode((string)$b, true); if ($s !== 200 || !is_array($j) || ($j['success'] ?? false) !== true) { fwrite(STDERR, "HATA: $path → $s\n"); exit(1); }
  return $j;
}
if (!is_dir($OUT)) mkdir($OUT, 0755, true);
foreach (glob("$OUT/*.json") as $f) unlink($f);   /* yalnızca bu klasördeki eski mock dosyaları */
$w = function ($name, $data) use ($OUT) { file_put_contents("$OUT/$name.json", json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT)."\n"); };
$w('home', get('home')); $w('markets', get('markets')); $w('categories', get('categories'));
$items = []; for ($p = 1; ; $p++) { $j = get("products?status=hepsi&limit=50&page=$p"); $items = array_merge($items, $j['data']); if ($p * 50 >= $j['meta']['total']) break; }
$w('items', ['success' => true, 'data' => $items, 'meta' => ['generated_at' => $j['meta']['generated_at'], 'api_version' => $j['meta']['api_version'], 'total' => count($items)]]);
$details = []; foreach ($items as $it) $details[] = get('products/'.$it['id'])['data'];
$w('product_details', ['success' => true, 'data' => $details, 'meta' => ['generated_at' => $j['meta']['generated_at'], 'api_version' => '1.0']]);
$w('_kaynak', ['aciklama' => 'Bu klasördeki JSON dosyaları Rev3 API (commit e95404e) yerel test kopyasından, tests/fixtures/seed.php SENTETİK verisiyle (senaryo 2) üretildi. Marka adları uydurmadır; gerçek fiyat veya kişisel veri içermez.', 'uretim' => date('c'), 'urun_sayisi' => count($items)]);
echo "Mock veri üretildi: ".count($items)." ürün, ".count($details)." ürün detayı → $OUT\n";
