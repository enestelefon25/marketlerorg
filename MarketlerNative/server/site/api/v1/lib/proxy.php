<?php
/* Güvenilir vekil (Cloudflare) doğrulaması. Hız sınırı (ziyaretçi IP'si) ve HTTPS algılaması bu dosyayı kullanır.
   Vekil başlıkları (CF-Connecting-IP, X-Forwarded-Proto, CF-Visitor) YALNIZCA isteği doğrudan gönderen adres (REMOTE_ADDR)
   config.php içindeki MK_API_TRUSTED_PROXIES aralıklarındaysa dikkate alınır; aksi halde yok sayılır. */
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
/* İstek güvenilir bir vekilden mi geldi? */
function mk_from_trusted_proxy() {
  $remote = (string)($_SERVER['REMOTE_ADDR'] ?? '');
  if ($remote === '' || filter_var($remote, FILTER_VALIDATE_IP) === false) return false;
  foreach (MK_API_TRUSTED_PROXIES as $c) if (mk_ip_in_cidr($remote, $c)) return true;
  return false;
}
/* Gerçek ziyaretçi IP'si: CF-Connecting-IP yalnızca güvenilir vekilden gelen istekte ve geçerli bir IP ise. */
function mk_client_ip() {
  $remote = (string)($_SERVER['REMOTE_ADDR'] ?? '');
  if ($remote === '' || filter_var($remote, FILTER_VALIDATE_IP) === false) return '0';
  $cf = trim((string)($_SERVER['HTTP_CF_CONNECTING_IP'] ?? ''));
  if ($cf === '' || filter_var($cf, FILTER_VALIDATE_IP) === false) return $remote;
  return mk_from_trusted_proxy() ? $cf : $remote;
}
/* İstek HTTPS ile mi geldi? Doğrudan HTTPS (sunucu değişkeni) ya da YALNIZCA güvenilir vekilin bildirdiği şema.
   Not: .htaccess yönlendirmesi başlıklara doğrulamasız bakar; sahte başlık orada yalnızca yönlendirmeyi atlatır,
   istek buraya ulaştığında başlık doğrulanmadığı için yine reddedilir (çelişki yok, PHP daha katıdır). */
function mk_is_https() {
  if (!empty($_SERVER['HTTPS']) && strtolower((string)$_SERVER['HTTPS']) !== 'off') return true;
  if (!mk_from_trusted_proxy()) return false;
  $xfp = strtolower(trim(explode(',', (string)($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? ''))[0]));
  if ($xfp === 'https') return true;
  $cfv = json_decode((string)($_SERVER['HTTP_CF_VISITOR'] ?? ''), true);
  return is_array($cfv) && ($cfv['scheme'] ?? '') === 'https';
}
