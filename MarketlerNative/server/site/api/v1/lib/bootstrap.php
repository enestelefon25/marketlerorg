<?php
/* API önyüklemesi: veritabanını SALT OKUNUR açar, ardından sitenin kendi kodunu (inc.php vb.) DEĞİŞTİRMEDEN yükler.

   Güvenlik sırası (inc.php'ye dokunmadan):
   1) config.php ÇALIŞTIRILMADAN metin olarak okunur (çalıştırılsaydı inc.php aynı sabitleri tekrar tanımlarken uyarı verirdi).
   2) Ayrı bir bağlantı açılır, hemen SET SESSION TRANSACTION READ ONLY ile kilitlenir ve settings.schema_v okunur.
      schema_v '5' değilse inc.php HİÇ YÜKLENMEZ (inc.php'nin şema güncelleme bloğu yalnızca schema_v≠'5' iken çalışır) ve 503 döner.
   3) inc.php yüklenir; kendi bağlantısı (db()) da hemen SET SESSION TRANSACTION READ ONLY ile kilitlenir.
      Bundan sonra herhangi bir yazma girişimi MariaDB/MySQL tarafından reddedilir (hata 1792).
   Site kökü: bu dosyanın üç üst klasörü (api/v1/lib -> site). */
if (!defined('MK_API')) { http_response_code(404); exit; }

define('MK_SITE', dirname(__DIR__, 3));
ini_set('display_errors', '0');

set_exception_handler(function ($e) {
  error_log('MARKETLER API hata: '.get_class($e).': '.$e->getMessage().' @ '.basename($e->getFile()).':'.$e->getLine());
  if ($e instanceof PDOException) mk_fail(503, 'SERVICE_UNAVAILABLE', 'Hizmet geçici olarak kullanılamıyor.', ['Retry-After: 120']);
  mk_fail(500, 'INTERNAL_ERROR', 'Beklenmeyen bir hata oluştu.');
});

/* 1) config.php'yi çalıştırmadan oku: define('DB_X','değer') satırları (var_export biçimi) */
function mk_read_db_config($file) {
  $s = @file_get_contents($file); if ($s === false) return null; $o = [];
  if (preg_match_all("/define\\(\\s*'(DB_HOST|DB_NAME|DB_USER|DB_PASS)'\\s*,\\s*'((?:[^'\\\\]|\\\\.)*)'\\s*\\)/", $s, $m, PREG_SET_ORDER))
    foreach ($m as $x) $o[$x[1]] = str_replace(["\\'", '\\\\'], ["'", '\\'], $x[2]);
  return count($o) === 4 ? $o : null;
}

(function () {
  $cfg = mk_read_db_config(MK_SITE.'/config.php');
  if (!$cfg) { error_log('MARKETLER API: config.php okunamadı'); mk_fail(503, 'SERVICE_UNAVAILABLE', 'Hizmet geçici olarak kullanılamıyor.'); }
  try {
    $p = new PDO('mysql:host='.$cfg['DB_HOST'].';dbname='.$cfg['DB_NAME'].';charset=utf8mb4', $cfg['DB_USER'], $cfg['DB_PASS'], [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
    $p->exec('SET SESSION TRANSACTION READ ONLY');
    $v = $p->query("SELECT v FROM settings WHERE k='schema_v'")->fetchColumn();
  } catch (Throwable $e) {
    error_log('MARKETLER API: ön kontrol bağlantısı başarısız: '.$e->getMessage());
    mk_fail(503, 'SERVICE_UNAVAILABLE', 'Hizmet geçici olarak kullanılamıyor.', ['Retry-After: 120']);
  }
  $p = null;
  if ($v !== '5') {   /* inc.php yüklenirse şema güncellemesi (yazma) çalışırdı: API bunu asla tetiklemez */
    error_log('MARKETLER API: schema_v beklenen değer değil, inc.php yüklenmedi');
    mk_fail(503, 'SERVICE_UNAVAILABLE', 'Hizmet geçici olarak kullanılamıyor.', ['Retry-After: 300']);
  }
})();

/* Sitenin URL fonksiyonları (bpath, u_kat, imgurl, item_img) adresi SCRIPT_NAME'den üretir.
   API /api/v1/index.php altında çalıştığı için SCRIPT_NAME site köküne göre ayarlanır; böylece bu fonksiyonlar
   DEĞİŞTİRİLMEDEN sitedeki ile aynı adresleri üretir. */
define('MK_API_SCRIPT', (string)($_SERVER['SCRIPT_NAME'] ?? ''));
$_SERVER['SCRIPT_NAME'] = preg_replace('~/api/v1/index\.php$~', '', MK_API_SCRIPT).'/index.php';
unset($_GET['tema']);   /* theme() ve mt_featured() önbellek kuralı ?tema parametresine bakar; API bunu kabul etmez */

require MK_SITE.'/inc.php';
db()->exec('SET SESSION TRANSACTION READ ONLY');
require_once MK_SITE.'/asistan-motor.php';
require_once MK_SITE.'/ortak-veri.php';
