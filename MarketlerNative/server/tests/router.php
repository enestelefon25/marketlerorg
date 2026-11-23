<?php
/* Yalnızca yerel test (php -S) için: Apache .htaccess kurallarının karşılığı. Canlı sunucuya yüklenmez. */
$uri = (string)parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH); $root = $_SERVER['DOCUMENT_ROOT'];
if (strpos($uri, '/api/v1/') === 0 || $uri === '/api/v1') { $_SERVER['SCRIPT_NAME'] = '/api/v1/index.php'; chdir("$root/api/v1"); require "$root/api/v1/index.php"; return true; }
if (preg_match('~^/katalog/([a-z0-9-]+)/?$~', $uri, $m)) { $_GET['s'] = $m[1]; $_SERVER['SCRIPT_NAME'] = '/katalog.php'; chdir($root); require "$root/katalog.php"; return true; }
if (preg_match('~^/market/([a-z0-9-]+)/?$~', $uri, $m)) { $_GET['m'] = $m[1]; $_SERVER['SCRIPT_NAME'] = '/index.php'; chdir($root); require "$root/index.php"; return true; }
return false;
