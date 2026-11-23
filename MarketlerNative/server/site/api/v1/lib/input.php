<?php
/* Girdi doğrulama. Hatalı girdi 400 INVALID_PARAMETER ile reddedilir; hiçbir değer SQL'e doğrudan eklenmez. */
if (!defined('MK_API')) { http_response_code(404); exit; }

/* İsteğe bağlı metin parametresi; $max karakterden uzunsa 400. */
function mk_in_str($name, $max = 120) {
  $v = $_GET[$name] ?? null; if ($v === null) return '';
  if (!is_string($v) || !mb_check_encoding($v, 'UTF-8') || mb_strlen($v) > $max) mk_bad($name);
  return $v;
}
/* İsteğe bağlı kalıplı parametre (ör. slug). Boşsa ''. */
function mk_in_pat($name, $re, $max = 190) {
  $v = $_GET[$name] ?? null; if ($v === null || $v === '') return '';
  if (!is_string($v) || strlen($v) > $max || !preg_match($re, $v)) mk_bad($name);
  return $v;
}
function mk_in_enum($name, $allowed, $default) {
  $v = $_GET[$name] ?? null; if ($v === null || $v === '') return $default;
  if (!is_string($v) || !in_array($v, $allowed, true)) mk_bad($name);
  return $v;
}
function mk_in_bool($name) {
  $v = $_GET[$name] ?? null; if ($v === null || $v === '' || $v === '0') return false;
  if ($v === '1') return true; mk_bad($name);
}
/* sayfa/limit -> [page, limit, offset] */
function mk_in_page($maxLimit, $defLimit) {
  $p = $_GET['page'] ?? '1'; $l = $_GET['limit'] ?? (string)$defLimit;
  if (!is_string($p) || !ctype_digit($p) || (int)$p < 1 || (int)$p > 100000) mk_bad('page');
  if (!is_string($l) || !ctype_digit($l) || (int)$l < 1 || (int)$l > $maxLimit) mk_bad('limit');
  return [(int)$p, (int)$l, ((int)$p - 1) * (int)$l];
}
/* yol parçası doğrulama */
function mk_seg($v, $re, $max = 190) { if (!is_string($v) || strlen($v) > $max || !preg_match($re, $v)) mk_bad('yol'); return $v; }
