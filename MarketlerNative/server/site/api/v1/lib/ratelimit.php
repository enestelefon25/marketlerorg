<?php
/* Dosya tabanlı hız sınırı (yönetici girişindeki yöntemle aynı yaklaşım; veritabanı kullanmaz).
   IP başına, sınıf başına, dakikalık pencere. Sayaçlar sistemin geçici klasöründe tutulur. */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_rate_limit($class) {
  $limit = MK_API_RATE[$class] ?? MK_API_RATE['std'];
  $dir = sys_get_temp_dir().'/mk_api_rl'; if (!is_dir($dir)) @mkdir($dir, 0700, true);
  $win = (int)floor(time() / 60);
  $ip = (string)($_SERVER['REMOTE_ADDR'] ?? '0');
  $f = $dir.'/'.md5($ip.'|'.$class.'|'.$win).'.c';
  $n = 0; $h = @fopen($f, 'c+');
  if ($h) {
    if (flock($h, LOCK_EX)) { $n = (int)stream_get_contents($h) + 1; ftruncate($h, 0); rewind($h); fwrite($h, (string)$n); fflush($h); flock($h, LOCK_UN); }
    fclose($h);
  }
  if (mt_rand(1, 100) === 1) {   /* eski sayaçları ara sıra temizle */
    foreach ((array)@glob($dir.'/*.c') as $old) if (is_file($old) && filemtime($old) < time() - 300) @unlink($old);
  }
  if ($n > $limit) mk_fail(429, 'RATE_LIMITED', 'Çok fazla istek. Lütfen biraz bekleyin.', ['Retry-After: '.(60 - time() % 60)]);
}
