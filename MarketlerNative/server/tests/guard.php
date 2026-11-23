<?php
/* tests/run.php için GÜVENLİK KORUMALARI. Test betiği yalnızca izole bir YEREL test ortamında çalışır.
   - Veritabanı sunucusu yalnızca yerel olabilir (localhost / 127.0.0.1 / ::1). Uzak sunucu MUTLAKA reddedilir; geçersiz kılma seçeneği yoktur.
   - Her çalıştırma benzersiz bir test veritabanı (mk_api_test_<12 hex>) ve benzersiz bir test kullanıcısı (mk_ro_<8 hex>) kullanır.
   - Aynı adda veritabanı veya kullanıcı zaten varsa betik DURUR; var olan hiçbir kullanıcının yetkisine dokunulmaz.
   - Silme yalnızca: ad kalıba uyuyorsa + bu çalıştırma oluşturduysa + veritabanındaki işaret tablosu bu çalıştırmanın belirtecini taşıyorsa yapılır.
   Bu dosyadaki fonksiyonlar R3-S test grubunda birim testine tabidir. */

const MK_GUARD_DB_RE = '/^mk_api_test_[0-9a-f]{12}$/';
const MK_GUARD_USER_RE = '/^mk_ro_[0-9a-f]{8}$/';
const MK_GUARD_MARKER = '_mk_test_isareti';

/* Yalnızca yerel veritabanı sunucusu (port, önek veya başka bir adres kabul edilmez). */
function mk_guard_local_host($h) { return in_array(strtolower(trim((string)$h)), ['localhost', '127.0.0.1', '::1'], true); }
function mk_guard_db_name_ok($n) { return is_string($n) && preg_match(MK_GUARD_DB_RE, $n) === 1; }
function mk_guard_user_name_ok($n) { return is_string($n) && preg_match(MK_GUARD_USER_RE, $n) === 1; }

/* Veritabanı mevcut mu? (yalnızca okuma) */
function mk_guard_db_exists($pdo, $db) { $s = $pdo->prepare('SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?'); $s->execute([$db]); return (int)$s->fetchColumn() > 0; }
/* Kullanıcı mevcut mu? (yalnızca okuma) */
function mk_guard_user_exists($pdo, $u) { $s = $pdo->prepare('SELECT COUNT(*) FROM mysql.user WHERE User=?'); $s->execute([$u]); return (int)$s->fetchColumn() > 0; }

/* Silinebilir mi? Ad kalıbı + işaret tablosu + bu çalıştırmanın belirteci. Herhangi biri tutmazsa false. */
function mk_guard_can_drop_db($pdo, $db, $token) {
  if (!mk_guard_db_name_ok($db) || !is_string($token) || !preg_match('/^[0-9a-f]{12}$/', $token)) return false;
  if (!mk_guard_db_exists($pdo, $db)) return false;
  try { $s = $pdo->query('SELECT belirtec FROM `'.$db.'`.`'.MK_GUARD_MARKER.'` LIMIT 2'); $r = $s->fetchAll(PDO::FETCH_COLUMN); }
  catch (Throwable $e) { return false; }
  return count($r) === 1 && hash_equals($token, (string)$r[0]);
}

/* Geçici çalışma klasörü yalnızca sistem geçici klasörü altında ve belirteçli adda ise silinebilir. */
function mk_guard_can_rm_dir($dir, $token) {
  $base = rtrim(sys_get_temp_dir(), '/').'/mk_api_test_';
  return is_string($dir) && preg_match('/^[0-9a-f]{12}$/', (string)$token) && $dir === $base.$token && is_dir($dir) && !is_link($dir);
}
function mk_guard_rmtree($dir) {
  foreach (new RecursiveIteratorIterator(new RecursiveDirectoryIterator($dir, FilesystemIterator::SKIP_DOTS), RecursiveIteratorIterator::CHILD_FIRST) as $f) { if ($f->isDir() && !$f->isLink()) rmdir($f->getPathname()); else unlink($f->getPathname()); }
  rmdir($dir);
}
