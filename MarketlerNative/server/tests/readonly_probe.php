<?php
/* T-RO-2: API önyüklemesini komut satırından çalıştırır ve bir yazma dener. Beklenen: veritabanı reddeder (1792). Yalnızca test veritabanında çalışır. */
$site = rtrim($argv[1] ?? '', '/');
define('MK_API', 1); $_SERVER['SCRIPT_NAME'] = '/api/v1/index.php'; $_SERVER['REQUEST_METHOD'] = 'GET';
ob_start();
require "$site/api/v1/config.php"; require "$site/api/v1/lib/response.php"; require "$site/api/v1/lib/bootstrap.php";
ob_end_clean();
try { q("INSERT INTO logs(msg) VALUES('salt-okunurluk-denemesi')"); echo "YAZDI (HATA)\n"; exit(1); }
catch (PDOException $e) { echo 'REDDEDILDI '.($e->errorInfo[1] ?? '?')."\n"; exit(0); }
