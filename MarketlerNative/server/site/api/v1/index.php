<?php
/* MARKETLER.ORG mobil API v1: tek giriş noktası (yönlendirici). Yalnızca okuma yapar.
   Yol: /api/v1/<kaynak>[/<parametre>]. Ayrıntı: docs/FAZ1-UC-NOKTALAR.md */
define('MK_API', 1);
ob_start();
require __DIR__.'/config.php';
require __DIR__.'/lib/response.php';
require __DIR__.'/lib/input.php';
require __DIR__.'/lib/ratelimit.php';

/* HTTPS zorunlu (sitenin site_url() içindeki algılamayla aynı) */
$https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https') || strpos($_SERVER['HTTP_CF_VISITOR'] ?? '', 'https') !== false;
if (!$https && !MK_API_ALLOW_HTTP) mk_fail(400, 'HTTPS_REQUIRED', 'Yalnızca HTTPS ile erişilebilir.');

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$path = (string)parse_url((string)($_SERVER['REQUEST_URI'] ?? ''), PHP_URL_PATH);
$pos = strpos($path, '/api/v1/'); $rest = $pos === false ? '' : trim(substr($path, $pos + 8), '/');
$seg = $rest === '' ? [] : explode('/', $rest);

/* [yöntem, kaynak, parametre kalıbı (null = parametresiz), dosya, hız sınıfı] */
$routes = [
  ['GET',  'home',       null,                       'home',       'std'],
  ['GET',  'markets',    null,                       'markets',    'std'],
  ['GET',  'markets',    '~^[a-z0-9]{1,30}$~',       'market',     'std'],
  ['GET',  'catalogs',   null,                       'catalogs',   'std'],
  ['GET',  'catalogs',   '~^[a-z0-9-]{1,190}$~',     'catalog',    'std'],
  ['GET',  'categories', null,                       'categories', 'std'],
  ['GET',  'products',   null,                       'products',   'std'],
  ['GET',  'products',   '~^[1-9][0-9]{0,9}$~',      'product',    'std'],
  ['GET',  'search',     null,                       'search',     'heavy'],
  ['GET',  'suggest',    null,                       'suggest',    'suggest'],
  ['GET',  'compare',    null,                       'compare',    'std'],
  ['GET',  'compare',    '~^similar$~',              'similar',    'std'],
  ['POST', 'assistant',  null,                       'assistant',  'heavy'],
  ['GET',  'pages',      '~^(hakkimizda|iletisim|gizlilik|kosullar)$~', 'pages', 'std'],
];

$hit = null; $methodMismatch = false; $param = null;
if (count($seg) >= 1 && count($seg) <= 2) {
  foreach ($routes as $r) {
    if ($seg[0] !== $r[1]) continue;
    if ($r[2] === null ? count($seg) !== 1 : (count($seg) !== 2 || !preg_match($r[2], $seg[1]))) continue;
    if ($r[0] !== $method && !($r[0] === 'GET' && $method === 'HEAD')) { $methodMismatch = true; continue; }
    $hit = $r; $param = $seg[1] ?? null; break;
  }
}
if (!$hit && $methodMismatch) mk_fail(405, 'METHOD_NOT_ALLOWED', 'Bu yöntem desteklenmiyor.');
if (!$hit) {
  /* tanınan kaynak ama geçersiz parametre (ör. /products/abc veya bilinmeyen sayfa) -> 404 */
  mk_fail(404, 'ENDPOINT_NOT_FOUND', 'İstenen adres bulunamadı.');
}

mk_rate_limit($hit[4]);
require __DIR__.'/lib/bootstrap.php';
require __DIR__.'/lib/map.php';
require __DIR__.'/lib/weblogic.php';
require __DIR__.'/endpoints/'.$hit[3].'.php';
mk_endpoint($param);
