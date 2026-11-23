<?php
/* JSON yanıt zarfı: {success, data, meta} / {success:false, error:{code,message}, meta}.
   Veritabanına erişmez. Beklenmeyen çıktıları (PHP uyarısı vb.) yanıta karıştırmaz. */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_now_iso() { return date('c'); }

function mk_send($http, $payload, $cache) {
  $leak = ob_get_level() ? ob_get_clean() : '';
  if ($leak !== '' && $leak !== false) error_log('MARKETLER API: beklenmeyen çıktı bastırıldı ('.strlen($leak).' bayt)');
  $etagSrc = json_encode([$payload['data'] ?? null, $payload['error'] ?? null, array_diff_key($payload['meta'] ?? [], ['generated_at' => 1])], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PARTIAL_OUTPUT_ON_ERROR);
  $etag = '"'.md5((string)$etagSrc).'"';
  if (!headers_sent()) {
    header('Content-Type: application/json; charset=utf-8');
    header('X-Content-Type-Options: nosniff');
    header('Referrer-Policy: no-referrer');
    header('Cache-Control: '.$cache);
    if ($http === 200 && ($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'GET') {
      header('ETag: '.$etag);
      $inm = trim((string)($_SERVER['HTTP_IF_NONE_MATCH'] ?? ''));
      if ($inm !== '' && $inm === $etag) { http_response_code(304); exit; }
    }
    http_response_code($http);
  }
  $body = json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
  if ($body === false) {   /* kodlama hatası: ayrıntı yalnızca günlükte */
    error_log('MARKETLER API: JSON kodlama hatası: '.json_last_error_msg());
    if (!headers_sent()) http_response_code(500);
    $body = '{"success":false,"error":{"code":"INTERNAL_ERROR","message":"Beklenmeyen bir hata oluştu."},"meta":{"generated_at":"'.mk_now_iso().'"}}';
  }
  echo $body; exit;
}

function mk_ok($data, $meta = [], $cache = 'public, max-age=300') {
  mk_send(200, ['success' => true, 'data' => $data, 'meta' => ['generated_at' => mk_now_iso()] + $meta], $cache);
}

function mk_fail($http, $code, $message, $extraHeaders = []) {
  if (!headers_sent()) foreach ($extraHeaders as $h) header($h);
  mk_send($http, ['success' => false, 'error' => ['code' => $code, 'message' => $message], 'meta' => ['generated_at' => mk_now_iso()]], 'no-store');
}

function mk_bad($field) { mk_fail(400, 'INVALID_PARAMETER', 'Geçersiz parametre: '.$field.'.'); }
function mk_404($what) { mk_fail(404, 'NOT_FOUND', $what.' bulunamadı.'); }
