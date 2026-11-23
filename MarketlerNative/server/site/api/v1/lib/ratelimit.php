<?php
/* Dosya tabanlı hız sınırı (yönetici girişindeki yöntemle aynı yaklaşım; veritabanı kullanmaz).
   Ziyaretçi IP'si başına, sınıf başına, dakikalık pencere. Sayaçlar sistemin geçici klasöründe tutulur. */
if (!defined('MK_API')) { http_response_code(404); exit; }

/* $ip, CIDR aralığında mı? (IPv4 ve IPv6) */
function mk_ip_in_cidr($ip, $cidr) {
  $p = strpos($cidr, '/'); if ($p === false) return false;
  $net = @inet_pton(substr($cidr, 0, $p)); $bin = @inet_pton($ip); $bits = (int)substr($cidr, $p + 1);
  if ($net === false || $bin === false || strlen($net) !== strlen($bin) || $bits < 0 || $bits > strlen($net) * 8) return false;
  $full = intdiv($bits, 8); $rest = $bits % 8;
  if ($full && substr($bin, 0, $full) !== substr($net, 0, $full)) return false;
  if ($rest) { $mask = chr((0xFF << (8 - $rest)) & 0xFF); if ((substr($bin, $full, 1) & $mask) !== (substr($net, $full, 1) & $mask)) return false; }
  return true;
}
/* Gerçek ziyaretçi IP'si: CF-Connecting-IP YALNIZCA istek güvenilir bir vekilden (Cloudflare) geliyorsa ve geçerli bir IP ise kullanılır. */
function mk_client_ip() {
  $remote = (string)($_SERVER['REMOTE_ADDR'] ?? '');
  if ($remote === '' || filter_var($remote, FILTER_VALIDATE_IP) === false) return '0';
  $cf = trim((string)($_SERVER['HTTP_CF_CONNECTING_IP'] ?? ''));
  if ($cf === '' || filter_var($cf, FILTER_VALIDATE_IP) === false) return $remote;
  foreach (MK_API_TRUSTED_PROXIES as $c) if (mk_ip_in_cidr($remote, $c)) return $cf;
  return $remote;
}

function mk_rate_limit($class) {
  $limit = MK_API_RATE[$class] ?? MK_API_RATE['std'];
  $dir = sys_get_temp_dir().'/mk_api_rl'; if (!is_dir($dir)) @mkdir($dir, 0700, true);
  $win = (int)floor(time() / 60);
  $f = $dir.'/'.md5(mk_client_ip().'|'.$class.'|'.$win).'.c';
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
