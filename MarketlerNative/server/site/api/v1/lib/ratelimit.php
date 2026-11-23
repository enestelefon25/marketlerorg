<?php
/* Dosya tabanlı hız sınırı (veritabanı kullanmaz). Ziyaretçi IP'si (lib/proxy.php) başına, sınıf başına, dakikalık pencere.
   GÜVENLİ HATA DAVRANIŞI: sayaç klasörü/dosyası oluşturulamaz, açılamaz, kilitlenemez veya yazılamazsa sınırlama sessizce
   DEVRE DIŞI KALMAZ; istek 503 ile reddedilir ve sunucu günlüğüne kayıt düşülür. */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_rate_fail($why) {
  error_log('MARKETLER API hız sınırı hatası: '.$why.' (istek güvenlik için reddedildi)');
  mk_fail(503, 'SERVICE_UNAVAILABLE', 'Hizmet geçici olarak kullanılamıyor.', ['Retry-After: 60']);
}

function mk_rate_limit($class) {
  $limit = MK_API_RATE[$class] ?? MK_API_RATE['std'];
  $dir = sys_get_temp_dir().'/mk_api_rl';
  if (!is_dir($dir) && !@mkdir($dir, 0700, true) && !is_dir($dir)) mk_rate_fail('sayaç klasörü oluşturulamadı');
  $win = (int)floor(time() / 60);
  $f = $dir.'/'.md5(mk_client_ip().'|'.$class.'|'.$win).'.c';
  $h = @fopen($f, 'c+'); if (!$h) mk_rate_fail('sayaç dosyası açılamadı');
  if (!flock($h, LOCK_EX)) { fclose($h); mk_rate_fail('sayaç dosyası kilitlenemedi'); }
  $n = (int)stream_get_contents($h) + 1;
  $ok = ftruncate($h, 0) && rewind($h) && fwrite($h, (string)$n) === strlen((string)$n) && fflush($h);
  flock($h, LOCK_UN); fclose($h);
  if (!$ok) mk_rate_fail('sayaç dosyasına yazılamadı');
  if (mt_rand(1, 100) === 1) {   /* eski sayaçları ara sıra temizle */
    foreach ((array)@glob($dir.'/*.c') as $old) if (is_file($old) && filemtime($old) < time() - 300) @unlink($old);
  }
  if ($n > $limit) mk_fail(429, 'RATE_LIMITED', 'Çok fazla istek. Lütfen biraz bekleyin.', ['Retry-After: '.(60 - time() % 60)]);
}
