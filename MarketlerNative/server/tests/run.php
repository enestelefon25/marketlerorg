<?php
/* MARKETLER.ORG API v1 — yerel test betiği (tek komut).
   Kullanım:  php MarketlerNative/server/tests/run.php --site=/yol/marketler.org [--rapor=/yol/rapor.md]
     --site : sitenin DEĞİŞTİRİLMEMİŞ kopyası (canlı sunucu değil). Betik bu klasöre YAZMAZ; geçici klasöre kopyalar.
   Gereken: PHP CLI (curl, gd, pdo_mysql, mbstring), yerel MariaDB/MySQL (varsayılan: localhost, root, parolasız; ortam değişkenleri:
            MK_TEST_DB_HOST, MK_TEST_DB_USER, MK_TEST_DB_PASS). Test veritabanı adı: mk_api_test (her çalıştırmada SİLİNİP yeniden kurulur).
   Aşamalar: A kurulum · B ortak-veri.php bayt karşılaştırma · B2 ara.php eşitlik düzeltmesi ve sayfalama · C işlev (C3: kaldırılan uç noktaların karşılığı)
             · D web↔API eşdeğerlik · T Türkçe karakter · E güvenlik (Cloudflare IP, hata JSON'u) · F salt okunurluk (SELECT-yetkili kullanıcı, sağlama toplamı) · G statik · H hash/hkey
   Canlı sisteme hiçbir bağlantı yapılmaz. */

error_reporting(E_ALL); ini_set('display_errors', '1'); date_default_timezone_set('Europe/Istanbul');
$opt = getopt('', ['site:', 'rapor::']);
$SRC = rtrim($opt['site'] ?? '', '/'); if (!$SRC || !is_file("$SRC/inc.php")) { fwrite(STDERR, "--site=<orijinal site klasörü> gerekli\n"); exit(2); }
$REPO = dirname(__DIR__); $SITE_NEW_FILES = "$REPO/site";
$WORK = sys_get_temp_dir().'/mk_api_test'; $DB = 'mk_api_test';
$DBH = getenv('MK_TEST_DB_HOST') ?: 'localhost'; $DBU = getenv('MK_TEST_DB_USER') ?: 'root'; $DBP = getenv('MK_TEST_DB_PASS') ?: '';
require __DIR__.'/fixtures/seed.php';

/* ---------------- yardımcılar ---------------- */
$R = []; $CUR = '';
function grp($g) { global $CUR; $CUR = $g; echo "\n== $g\n"; }
function check($name, $ok, $detail = '') { global $R, $CUR; $R[] = [$CUR, $name, (bool)$ok, $detail]; echo ($ok ? '  ✓ ' : '  ✗ ').$name.($ok || $detail === '' ? '' : "  → $detail")."\n"; return (bool)$ok; }
function sh($c) { exec($c.' 2>&1', $o, $rc); return [$rc, implode("\n", $o)]; }
$ALLBODY = ''; $APIRESP = [];
$INST = ['old' => 8081, 'new' => 8082, 'newrl' => 8083, 'newcf' => 8084, 'newro' => 8085, 'hata1' => 8086, 'hata2' => 8087, 'hata3' => 8088];
function http($port, $method, $path, $body = null, $hdr = []) {
  global $ALLBODY; $c = curl_init("http://127.0.0.1:$port$path");
  $h = array_merge(['Host: marketler.org'], $hdr); $rh = [];
  curl_setopt_array($c, [CURLOPT_CUSTOMREQUEST => $method, CURLOPT_RETURNTRANSFER => true, CURLOPT_HTTPHEADER => $h, CURLOPT_TIMEOUT => 60,
    CURLOPT_HEADERFUNCTION => function ($ch, $l) use (&$rh) { $p = strpos($l, ':'); if ($p) $rh[strtolower(trim(substr($l, 0, $p)))] = trim(substr($l, $p + 1)); return strlen($l); }]);
  if ($body !== null) curl_setopt($c, CURLOPT_POSTFIELDS, $body);
  $b = curl_exec($c); $st = curl_getinfo($c, CURLINFO_HTTP_CODE); curl_close($c);
  if (strpos($path, '/api/v1/') === 0) { $ALLBODY .= "\n".$b; $GLOBALS['APIRESP'][] = [$port, $method, $path, $st, $rh, (string)$b]; }   /* sızıntı taraması ve zarf denetimi yalnızca API yanıtları */
  return [$st, $rh, (string)$b];
}
function api($method, $path, $body = null, $hdr = []) { list($s, $h, $b) = http(8082, $method, '/api/v1/'.$path, $body, $hdr); return [$s, json_decode($b, true), $h, $b]; }
function web($port, $path) { return http($port, 'GET', $path)[2]; }
function pdo() { global $DBH, $DBU, $DBP, $DB; static $p; if (!$p) { $p = new PDO("mysql:host=$DBH;dbname=$DB;charset=utf8mb4", $DBU, $DBP, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]); } return $p; }
function clear_caches() {
  global $WORK, $INST; foreach (array_keys($INST) as $d) @unlink("$WORK/$d/img/cmpcache.json");
  foreach (array_merge(glob(sys_get_temp_dir().'/mk_feat_v2_*') ?: [], glob(sys_get_temp_dir().'/mk_oneri_*') ?: [], glob(sys_get_temp_dir().'/mk_api_rl/*') ?: []) as $f) @unlink($f);
}
function seed($sc) { global $WORK, $INST; clear_caches(); return mk_seed(pdo(), $sc, array_map(function ($d) use ($WORK) { return "$WORK/$d"; }, array_keys($INST))); }
function apiat($port, $method, $path, $body = null, $hdr = []) { list($s, $h, $b) = http($port, $method, '/api/v1/'.$path, $body, $hdr); return [$s, json_decode($b, true), $h, $b]; }
function ids($items) { return array_column($items, 'id'); }
function dom($html) { $d = new DOMDocument(); @$d->loadHTML('<?xml encoding="utf-8"?>'.$html); return new DOMXPath($d); }
function txt($n) { return trim(preg_replace('/\s+/u', ' ', $n->textContent)); }
/* sitedeki pcard() kartları: [ad, fiyat metni] */
function web_cards($html) { $x = dom($html); $o = []; foreach ($x->query('//article[contains(@class,"pc")]') as $a) { $h = $x->query('.//h3', $a)->item(0); $b = $x->query('.//div[@class="pp"]/b', $a)->item(0); $o[] = [txt($h), $b ? txt($b) : '']; } return $o; }
function tie_norm($h) {   /* ardışık, aynı <h3> adlı ürün kartlarını kendi aralarında sırala */
  return preg_replace_callback('~(?:<article class="pc">.*?</article>)+~s', function ($m) {
    preg_match_all('~<article class="pc">.*?</article>~s', $m[0], $a); $o = []; $run = []; $last = null;
    foreach ($a[0] as $art) { preg_match('~<h3>(.*?)</h3>~s', $art, $n); $nm = $n[1] ?? ''; if ($nm !== $last && $run) { sort($run); $o = array_merge($o, $run); $run = []; } $run[] = $art; $last = $nm; }
    sort($run); return implode('', array_merge($o, $run)); }, $h);
}
function msort($a) { sort($a); return $a; }
function tie_cards($c) { $o = []; $run = []; $last = null; foreach ($c as $x) { if ($x[0] !== $last && $run) { sort($run); $o = array_merge($o, $run); $run = []; } $run[] = $x; $last = $x[0]; } sort($run); return array_merge($o, $run); }
function api_cards($items) { return array_map(function ($i) { return [$i['name'], $i['price_text']]; }, $items); }

/* ---------------- A. kurulum ---------------- */
grp('A. Kurulum');
sh('rm -rf '.escapeshellarg($WORK)); mkdir($WORK, 0777, true);
foreach (array_keys($INST) as $d) { sh('cp -a '.escapeshellarg($SRC).' '.escapeshellarg("$WORK/$d")); @unlink("$WORK/$d/img/cmpcache.json"); }
foreach (array_keys($INST) as $d) if ($d !== 'old') sh('cp -a '.escapeshellarg($SITE_NEW_FILES).'/. '.escapeshellarg("$WORK/$d/"));
$cfg = "<?php\ndefine('DB_HOST','".addslashes($DBH)."');\ndefine('DB_NAME','$DB');\ndefine('DB_USER','".addslashes($DBU)."');\ndefine('DB_PASS','".addslashes($DBP)."');\n";
foreach (array_keys($INST) as $d) file_put_contents("$WORK/$d/config.php", $cfg);
/* SELECT dışında hiçbir yetkisi olmayan kullanıcıyla çalışan örnek (bağımsız salt okunurluk doğrulaması) */
file_put_contents("$WORK/newro/config.php", "<?php\ndefine('DB_HOST','".addslashes($DBH)."');\ndefine('DB_NAME','$DB');\ndefine('DB_USER','mk_test_ro');\ndefine('DB_PASS','yerel-test-ro');\n");
$ac = file_get_contents("$WORK/new/api/v1/config.php");
$open = str_replace(["MK_API_ALLOW_HTTP = false", "'std' => 60, 'heavy' => 20, 'suggest' => 120"], ["MK_API_ALLOW_HTTP = true", "'std' => 100000, 'heavy' => 100000, 'suggest' => 100000"], $ac);
foreach (['new', 'newro', 'hata1', 'hata2', 'hata3'] as $d) file_put_contents("$WORK/$d/api/v1/config.php", $open);
file_put_contents("$WORK/newrl/api/v1/config.php", str_replace("'std' => 60", "'std' => 5", $ac));   /* HTTP kapalı, düşük sınır, varsayılan Cloudflare listesi (127.0.0.1 GÜVENİLİR DEĞİL) */
file_put_contents("$WORK/newcf/api/v1/config.php", str_replace(["MK_API_ALLOW_HTTP = false", "'std' => 60", "const MK_API_TRUSTED_PROXIES = ["], ["MK_API_ALLOW_HTTP = true", "'std' => 5", "const MK_API_TRUSTED_PROXIES = ['127.0.0.1/32', "], $ac));   /* yerel adres Cloudflare'miş gibi güvenilir */
/* HATA ENJEKSİYONU (yalnızca geçici test kopyalarında): */
file_put_contents("$WORK/hata1/ortak-veri.php", "<?php\ntanimsiz_fonksiyon_olumcul_hata();\n");   /* ölümcül PHP hatası */
file_put_contents("$WORK/hata2/ortak-veri.php", "<?php\nhttp_response_code(503); header('Content-Type: text/plain; charset=utf-8'); exit('Site kısa süreliğine bakımda. Lütfen birkaç dakika sonra tekrar deneyin.');\n");   /* inc.php db() hatasının birebir davranışı */
file_put_contents("$WORK/hata3/config.php", "<?php\ndefine('DB_HOST','".addslashes($DBH)."');\ndefine('DB_NAME','olmayan_veritabani_xyz');\ndefine('DB_USER','".addslashes($DBU)."');\ndefine('DB_PASS','".addslashes($DBP)."');\n");   /* veritabanına ulaşılamıyor */
$m = "mariadb -h".escapeshellarg($DBH)." -u".escapeshellarg($DBU).($DBP !== '' ? ' -p'.escapeshellarg($DBP) : '');
list($rc, $o) = sh("$m -e 'DROP DATABASE IF EXISTS $DB; CREATE DATABASE $DB CHARACTER SET utf8mb4'"); check('test veritabanı oluşturuldu', $rc === 0, $o);
list($rc, $o) = sh("$m $DB < ".escapeshellarg(__DIR__.'/fixtures/schema.sql')); check('kullanıcının şema dökümü yüklendi (yalnızca yapı)', $rc === 0, $o);
list($rc, $o) = sh("$m -e \"CREATE USER IF NOT EXISTS 'mk_test_ro'@'localhost' IDENTIFIED BY 'yerel-test-ro'; REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'mk_test_ro'@'localhost'; GRANT SELECT ON $DB.* TO 'mk_test_ro'@'localhost'; SHOW GRANTS FOR 'mk_test_ro'@'localhost';\"");
check('yalnızca SELECT yetkili test kullanıcısı (mk_test_ro) hazır', $rc === 0 && strpos($o, 'GRANT SELECT ON') !== false && !preg_match('/INSERT|UPDATE|DELETE|ALL PRIVILEGES ON `?'.$DB.'/', $o), $o);
$router = __DIR__.'/router.php'; $SRV = [];
foreach ($INST as $d => $port) {
  $SRV[$d] = proc_open(['php', '-S', "127.0.0.1:$port", '-t', "$WORK/$d", $router], [1 => ['file', "$WORK/$d.out.log", 'w'], 2 => ['file', "$WORK/$d.err.log", 'w']], $pp, "$WORK/$d");
}
register_shutdown_function(function () use (&$SRV) { foreach ($SRV as $p) { if (is_resource($p)) proc_terminate($p); } });
for ($i = 0; $i < 50; $i++) { $f = @fsockopen('127.0.0.1', 8088); if ($f) { fclose($f); break; } usleep(100000); }
check('test sunucuları başladı (eski site, yeni site+API, hız sınırı, Cloudflare, SELECT-yetkili, 3 hata enjeksiyonu)', $i < 50);
check('PHP sürümü (yerel test)', true, PHP_VERSION);
echo '    PHP '.PHP_VERSION.' · '.pdo_version()."\n";
function pdo_version() { try { return pdo()->query('SELECT VERSION()')->fetchColumn(); } catch (Throwable $e) { return '?'; } }

/* ---------------- B. ortak-veri.php: eski ve yeni sitenin HTML çıktısı bayt bayt aynı mı ---------------- */
grp('B. mt_featured() taşıması — eski/yeni HTML bayt karşılaştırması (4 senaryo)');
$failB = 0; $NONDET = [];
foreach (['0' => '0 indirimli', '2' => '1-3 indirimli (2)', '5' => '4+ indirimli (5)', 'bos' => 'ürünsüz / katalogsuz'] as $sc => $lbl) {
  $S = seed($sc);
  $paths = ['/', '/', '/?tema=modern', '/?tema=klasik', '/market/bim', '/kataloglar.php', '/kataloglar.php?m=sok', '/ara.php', '/ara.php?q=s%C3%BCt', '/fiyat-karsilastirma.php',
    '/asistan.php', '/asistan.php?q=en+ucuz+s%C3%BCt', '/asistan.php?oneri=1&q=s%C3%BC', '/sayfa.php?s=gizlilik', '/sitemap.php'];
  if ($S) { $paths[] = '/katalog/'.$S['catalogs']['bim']['live'][1]; $paths[] = '/katalog/'.$S['catalogs']['a101']['past'][1]; }
  $diff = []; $nondet = [];
  foreach ($paths as $p) {
    list($so, , $bo) = http(8081, 'GET', $p);
    /* cmp_groups() (inc.php, DEĞİŞTİRİLMEDİ) eşit kayıtlarda belirsiz sıra üretir ve sonucu her site klasörünün kendi img/cmpcache.json dosyasına yazar.
       Değişikliğin etkisini yalıtmak için yeni site, eski sitenin ürettiği karşılaştırma önbelleğini kullanır (veri ve anahtar aynı). */
    if (is_file("$WORK/old/img/cmpcache.json")) copy("$WORK/old/img/cmpcache.json", "$WORK/new/img/cmpcache.json");
    list($sn, , $bn) = http(8082, 'GET', $p);
    if ($so === $sn && $bo === $bn) continue;
    /* Bayt farkı varsa: ardışık ve ADI BİREBİR AYNI ürün kartları (<article class="pc">) kendi aralarında sıralanıp yeniden karşılaştırılır.
       Bu durum sitedeki ORDER BY eşitliğinden (ör. ara.php: c.cdate DESC,i.name ASC) kaynaklanır; başka her fark BAŞARISIZDIR. */
    if ($so === $sn && tie_norm($bo) === tie_norm($bn)) { $nondet[$p] = 1; continue; }
    $diff[] = "$p ($so/$sn, ".strlen($bo).'/'.strlen($bn).' bayt)';
  }
  $ok = check("senaryo '$lbl': ".count($paths).' sayfa eski ve yeni sitede aynı (ana sayfa iki kez: önbelleksiz + önbellekli)', !$diff, implode('; ', $diff)); if (!$ok) $failB++;
  foreach ($nondet as $p => $n) { check("  bilgi: $p içerik aynı; yalnızca aynı adlı kartların sırası farklı (onaylanan ara.php eşitlik düzeltmesi + eski sitenin belirsiz sırası)", strpos($p, '/ara.php') === 0); $NONDET[$p] = 1; }
  $exact = array_diff(array_unique($paths), array_keys($nondet));
  check("  '$lbl': ".count($exact).' sayfa BAYT BAYT aynı (ana sayfa dahil: '.(in_array('/', $exact, true) ? 'evet' : 'HAYIR').')', in_array('/', $exact, true) || $sc === 'x');
  $home = web(8082, '/');
  if ($sc === '0') check("  '$lbl' ana sayfa başlığı 'Uygun Fiyatlı Ürünler'", strpos($home, 'Uygun Fiyatlı Ürünler') !== false);
  if ($sc === '5') check("  '$lbl' ana sayfa başlığı 'Öne çıkan fırsatlar'", strpos($home, 'Öne çıkan fırsatlar') !== false);
  if ($sc === 'bos') check("  '$lbl' ana sayfa boş durum kartı", strpos($home, 'Şu an listelenecek ürün yok.') !== false);
}
$errs = trim(@file_get_contents("$WORK/new.err.log").@file_get_contents("$WORK/old.err.log"));
$errs = preg_replace('/^.*(Accepted|Closing|\[200\]|\[404\]|\[30\d\]|\[4\d\d\]|\[50\d\]|Development Server|started).*$/m', '', $errs);
check('eski ve yeni sitede PHP hata/uyarısı yok (sunucu günlüğü)', !preg_match('/(Fatal|Warning|Notice|Deprecated|Parse error)/', $errs), substr($errs, 0, 400));
check('SONUÇ: ortak-veri.php değişikliği kabul koşulu (tüm senaryolar aynı)', $failB === 0);
seed('2'); $v = []; for ($k = 0; $k < 8; $k++) { @unlink("$WORK/old/img/cmpcache.json"); $v[md5(web(8081, '/fiyat-karsilastirma.php'))] = 1; }
check('  bilgi (mevcut site): karşılaştırma önbelleği her seferinde silinirse orijinal fiyat-karsilastirma.php 8 denemede '.count($v).' farklı çıktı verir (cmp_groups eşitlik sırası; inc.php değiştirilmedi)', true);

/* ---------------- B2. ara.php eşitlik düzeltmesi ---------------- */
grp('B2. ara.php: eşitlikte i.id (tekrarlanabilirlik, içerik korunumu, sayfalama)');
$S = seed('2');
$ARAQ = ['', 'q=s%C3%BCt', 'q=s%C3%BCt&o=asc', 'q=s%C3%BCt&o=desc', 'o=asc', 'o=desc', 'm=bim', 'm=sok&o=asc', 'k=diger', 'k=gida&st=hepsi', 'st=yaklasan', 'st=dolmus', 'st=hepsi', 'min=40&max=60', 'q=e%C5%9Fitlik', 'q=e%C5%9Fitlik&o=desc', 'q=%25', 'q=_'];
$oldVar = 0; $newVar = 0; $contentOk = true; $badC = []; $oldPagBug = 0; $newPagBug = 0;
foreach ($ARAQ as $qs) {
  $o = []; $n = [];
  for ($k = 0; $k < 6; $k++) { $o[md5(web(8081, '/ara.php?ajax=1&'.$qs))] = 1; $n[md5(web(8082, '/ara.php?ajax=1&'.$qs))] = 1; }
  if (count($o) > 1) $oldVar++; if (count($n) > 1) $newVar++;
  $wo = json_decode(web(8081, '/ara.php?ajax=1&'.$qs), true); $wn = json_decode(web(8082, '/ara.php?ajax=1&'.$qs), true);
  /* toplam (COUNT sorgusu sıralamadan bağımsız) her sorguda aynı; tek sayfaya sığan sonuçlarda ürün kümesi aynı */
  if ($wo['total'] !== $wn['total'] || ($wn['total'] <= 24 && msort(web_cards($wo['html'])) !== msort(web_cards($wn['html'])))) { $contentOk = false; $badC[] = $qs; }
  /* sayfalarla gezinme: kart kimliği = ad + fiyat + market rengi + katalog bağlantısı */
  foreach ([8081 => 'o', 8082 => 'n'] as $port => $who) {
    $all = []; for ($off = 0; $off < $wn['total']; $off += 24) { preg_match_all('~<article class="pc"><div class="pt[^"]*" style="--c:([^"]+)">.*?<h3>([^<]+)</h3>.*?<b>([^<]+)</b>.*?href="([^"]+)"~s', json_decode(web($port, '/ara.php?ajax=1&'.$qs.'&off='.$off), true)['html'], $mm, PREG_SET_ORDER); foreach ($mm as $x) $all[] = $x[1].'|'.$x[2].'|'.$x[3].'|'.$x[4]; }
    $bug = count($all) !== $wn['total'] || count(array_unique($all)) !== count($all);
    if ($who === 'o' && $bug) $oldPagBug++; if ($who === 'n' && $bug) { $newPagBug++; $badC[] = "$qs (sayfalama)"; }
  }
}
check('düzeltme sonrası '.count($ARAQ).' sorgunun her biri 6 tekrarda bayt bayt aynı', $newVar === 0, "$newVar sorgu değişken");
check("  karşılaştırma: düzeltme öncesi (orijinal ara.php) $oldVar sorgu tekrarlarda farklı çıktı verdi", true);
check('içerik korunumu: tüm sorgularda toplam aynı; tek sayfalık sonuçlarda ürün kümesi aynı', $contentOk, implode(', ', $badC));
check("web sayfalaması (off=0,24,…): düzeltme sonrası $newPagBug sorguda tekrar/kayıp (0 beklenir)", $newPagBug === 0, implode(', ', $badC));
check("  karşılaştırma: düzeltme öncesi $oldPagBug sorguda sayfalar arası tekrar/kayıp görüldü (giderilen hata)", true);
$d = []; exec('diff '.escapeshellarg("$SRC/ara.php").' '.escapeshellarg("$SITE_NEW_FILES/ara.php"), $d);
check('ara.php farkı yalnızca ORDER satırı (filtreler, WHERE ve sayfalama kodu aynı)', count($d) === 4 && $d[0] === '15c15' && strpos($d[3], ".',i.id ASC'") !== false && str_replace(['(', ").',i.id ASC';"], ['', ';'], preg_replace('~\s*/\*.*\*/$~', '', substr($d[3], 2))) === substr($d[1], 2), implode("\n", $d));
/* sayfalama (API, ara.php ile aynı sorgu): küçük sayfalarla gezildiğinde tekrar/kayıp yok, sıra tek sayfadaki ile aynı */
$pagOk = true; $badP = [];
foreach (['', 'status=hepsi', 'sort=asc', 'sort=desc', 'q=e%C5%9Fitlik', 'q=e%C5%9Fitlik&sort=asc', 'category=diger&sort=desc', 'market=a101'] as $qs) {
  list(, $all) = api('GET', 'products?limit=50&'.$qs); $tot = $all['meta']['total']; $full = [];
  for ($pg = 1; ($pg - 1) * 50 < $tot; $pg++) { list(, $x) = api('GET', "products?limit=50&page=$pg&$qs"); $full = array_merge($full, ids($x['data'])); }
  foreach ([3, 5, 7] as $lim) { $got = []; for ($pg = 1; ($pg - 1) * $lim < $tot; $pg++) { list(, $x) = api('GET', "products?limit=$lim&page=$pg&$qs"); $got = array_merge($got, ids($x['data'])); }
    if ($got !== $full || count(array_unique($got)) !== $tot || count($got) !== $tot) { $pagOk = false; $badP[] = "$qs (limit $lim)"; } }
}
check('sayfalama: limit 3/5/7 ile gezilen sayfalar = tek liste; tekrar yok, kayıp yok (8 filtre/sıralama)', $pagOk, implode(', ', $badP));
/* sıralama kuralı korunumu: mevcut ölçütler aynı, eşitlikte i.id artan */
$ordOk = true;
foreach (['' => 'new', 'sort=asc' => 'asc', 'sort=desc' => 'desc'] as $qs => $mode) {
  list(, $x) = api('GET', 'products?limit=50&status=hepsi&'.$qs); $it = $x['data'];
  for ($k = 1; $k < count($it); $k++) { $a = $it[$k - 1]; $b = $it[$k];
    $ka = $mode === 'new' ? [-strtotime($a['catalog']['start_date']), $a['name']] : [$mode === 'asc' ? (float)$a['price'] : -(float)$a['price']];
    $kb = $mode === 'new' ? [-strtotime($b['catalog']['start_date']), $b['name']] : [$mode === 'asc' ? (float)$b['price'] : -(float)$b['price']];
    $c = $mode === 'new' ? ($ka[0] <=> $kb[0] ?: strcmp(mb_strtolower($ka[1]), mb_strtolower($kb[1])) <=> 0) : ($ka <=> $kb);
    if ($c > 0 && $mode !== 'new') $ordOk = false; if ($c === 0 && $a['id'] > $b['id']) $ordOk = false; if ($mode === 'new' && $ka[0] > $kb[0]) $ordOk = false; }
}
check('sıralama kuralları korunuyor (tarih↓ad↑ / fiyat↑ / fiyat↓) ve eşitlikte kalem no artan', $ordOk);

/* ---------------- C. işlev testleri ---------------- */
grp('C. Uç nokta işlev testleri (senaryo: 2 indirimli)');
$S = seed('2'); $I = $S['items']; $C = $S['catalogs'];
list($s, $j, $h) = api('GET', 'home');
check('/home 200 + zarf', $s === 200 && $j['success'] === true && isset($j['meta']['generated_at']));
check('/home markets=3, live=3, soon=3', count($j['data']['markets']) === 3 && count($j['data']['live_catalogs']) === 3 && count($j['data']['soon_catalogs']) === 3);
check('/home featured başlık (2 indirimli → "Öne çıkan fırsatlar")', $j['data']['featured']['title'] === 'Öne çıkan fırsatlar' && count($j['data']['featured']['items']) > 0, $j['data']['featured']['title']);
check('/home top_compare var, data_updated_at', $j['data']['top_compare'] !== null && $j['data']['data_updated_at'] === '2026-11-23 06:00:12');
$bim = $j['data']['markets'][0]; check('/home BİM logo yerel dosyadan mutlak URL', $bim['logo_url'] === 'https://marketler.org/img/logo-bim.png', (string)$bim['logo_url']);
check('/home A101 logosu ayarı yok → null', $j['data']['markets'][1]['logo_url'] === null);
check('ETag ve Cache-Control başlıkları', isset($h['etag']) && strpos($h['cache-control'] ?? '', 'max-age=300') !== false);
list($s2) = api('GET', 'home', null, ['If-None-Match: '.$h['etag']]); check('If-None-Match → 304', $s2 === 304);
list($s, $j) = api('GET', 'markets'); check('/markets 3 market', $s === 200 && count($j['data']) === 3);
list($s, $j) = api('GET', 'markets/bim'); check('/markets/bim live/soon/past = 1/1/1 (gizli/onay bekleyen yok)', $s === 200 && count($j['data']['catalogs']['live']) === 1 && count($j['data']['catalogs']['soon']) === 1 && count($j['data']['catalogs']['past']) === 1);
list($s) = api('GET', 'markets/yok'); check('/markets/yok → 404', $s === 404);
list($s, $j) = api('GET', 'catalogs'); check('/catalogs all: live 3 soon 3 past 3', $s === 200 && count($j['data']['live']) === 3 && count($j['data']['soon']) === 3 && $j['meta']['total'] === 3);
list($s, $j) = api('GET', 'catalogs?status=past&limit=2&page=2'); check('/catalogs sayfalama (past, limit 2, sayfa 2 → 1 kayıt)', $s === 200 && count($j['data']) === 1 && $j['meta']['total'] === 3);
list($s, $j) = api('GET', 'catalogs/'.$C['bim']['live'][1]); $cd = $j['data'] ?? [];
check('/catalogs/{bim aktif}: 2 yerel sayfa, 1 uzak sayfa atlandı', $s === 200 && count($cd['pages']) === 2 && $cd['pages_skipped'] === 1);
check('  sayfa 1 küçük kopya t/, sayfa 2 küçük kopya yok → asıl görsel', strpos($cd['pages'][0]['thumb_url'], '/t/1.webp') !== false && $cd['pages'][1]['thumb_url'] === $cd['pages'][1]['url']);
check('  kapak c/ küçük kopyası', strpos($cd['catalog']['cover']['url'], '/c/1.webp') !== false);
check('  ürün grupları KORDER sırasında (elektronik önce)', ($cd['groups'][0]['category']['slug'] ?? '') === 'elektronik');
list($s) = api('GET', 'catalogs/'.$C['bim']['pending'][1]); check('onay bekleyen katalog → 404', $s === 404);
list($s) = api('GET', 'catalogs/'.$C['a101']['hidden'][1]); check('gizli katalog → 404', $s === 404);
list($s, $j) = api('GET', 'categories'); check('/categories 14, KORDER sırası', $s === 200 && count($j['data']) === 14 && $j['data'][0]['slug'] === 'elektronik' && $j['data'][13]['slug'] === 'diger');
list($s, $j) = api('GET', 'products'); check('/products varsayılan (aktif), toplam > 0', $s === 200 && $j['meta']['total'] > 0);
$names = array_column($j['data'], 'name'); check('/products gizli/onay bekleyen ürün yok', !preg_grep('/GİZLİ/u', $names));
list($s, $j) = api('GET', 'products?q='.urlencode('süt').'&sort=asc'); $pr = array_map('floatval', array_column($j['data'], 'price'));
$sorted = $pr; sort($sorted); check('/products?q=süt&sort=asc artan fiyat', $s === 200 && $pr === $sorted && count($pr) >= 4);
$ovac = null; foreach ($j['data'] as $it) if (strpos($it['name'], 'Ovaçiftlik') === 0) $ovac = $it;
check('old_price < price → eski fiyat/indirim gösterilmez', $ovac && $ovac['old_price'] === null && $ovac['discount_pct'] === null);
check('fiyat metin olarak (ör. "27.75") ve sitedeki biçimle', $ovac && $ovac['price'] === '27.75' && $ovac['price_text'] === '27,75 TL');
check('birim fiyat (gıda, 1 L) → TL/L', $ovac && $ovac['unit_price']['unit'] === 'L' && $ovac['unit_price']['value'] === '27.75');
/* Favori: itemId:storeId:hash. Favorinin kendisi item_id ile; hash yalnızca ADAY ipucu, otomatik eşleştirme yok. */
$fav = function ($key, $name) use ($I) { list($st, $w) = explode('|', $key); return $I[$key].':'.['bim' => 1, 'a101' => 2, 'sok' => 3][$st].':'.md5(mb_strtolower($name)); };
$kPast = $fav('bim|past|Yaylaköy Süt 1 L', 'Yaylaköy Süt 1 L'); $kLive = $fav('bim|live|Yaylaköy Süt 1 L', 'Yaylaköy Süt 1 L');
list($s, $j) = api('GET', "products?fav=$kPast,$kLive,999999:2:".str_repeat('a', 32)); $F = $j['data'] ?? [];
check('favori (geçen haftaki kalem): kendisi döner, durum "suresi_doldu", favori değiştirilmez', $s === 200 && $F[0]['state'] === 'suresi_doldu' && $F[0]['item']['id'] === $I['bim|past|Yaylaköy Süt 1 L']);
check('  aynı market+hash taşıyan kalemler yalnızca ADAY (confirmed=false), otomatik eşleşme yok', count($F[0]['candidates']) === 2 && !array_filter($F[0]['candidates'], function ($c) { return $c['confirmed'] !== false || $c['match'] !== 'ayni_market_ayni_ad'; }) && $F[0]['candidates'][0]['item']['catalog']['status'] === 'live', json_encode(array_map(function ($c) { return $c['item']['catalog']['status']; }, $F[0]['candidates'] ?? [])));
check('  kullanıcıya açık mesaj: süre doldu + aday doğrulanmadı', strpos((string)$F[0]['message'], 'sona erdi') !== false && strpos((string)$F[0]['message'], 'doğrulanamadığı için favoriniz değiştirilmedi') !== false, (string)$F[0]['message']);
check('favori (aktif kalem): durum "aktif", mesaj yok', $F[1]['state'] === 'aktif' && $F[1]['message'] === null && $F[1]['name_changed'] === false);
check('favori (silinmiş/olmayan kalem): durum "bulunamadi", item=null, açık mesaj', $F[2]['state'] === 'bulunamadi' && $F[2]['item'] === null && strpos($F[2]['message'], 'bulunamadı') !== false);
list($s, $j) = api('GET', 'products?fav='.$I['bim|pending|GİZLİ ONAY BEKLEYEN ÜRÜN 1 L'].':1:'.md5(mb_strtolower('GİZLİ ONAY BEKLEYEN ÜRÜN 1 L'))); check('favori: gizli/onay bekleyen katalogdaki kalem gösterilmez (bulunamadi)', $s === 200 && $j['data'][0]['state'] === 'bulunamadi' && $j['data'][0]['item'] === null);
list($s, $j) = api('GET', 'products?fav='.$I['bim|live|Yaylaköy Süt 1 L'].':2:'.md5(mb_strtolower('Yaylaköy Süt 1 L'))); check('favori: kayıtlı market uyuşmazsa aynı kalem sayılmaz', $s === 200 && $j['data'][0]['state'] === 'bulunamadi');
$hid = $I['bim|live|Kavruk Türk Kahvesi 100 g']; pdo()->prepare("UPDATE items SET name='Kavruk Türk Kahvesi 100 g Yeni Ad', hash=? WHERE id=?")->execute([md5(mb_strtolower('Kavruk Türk Kahvesi 100 g Yeni Ad')), $hid]); clear_caches();
list($s, $j) = api('GET', 'products?fav='.$hid.':1:'.md5(mb_strtolower('Kavruk Türk Kahvesi 100 g'))); check('favori: yönetici adı değiştirdiyse kalem yine bulunur, name_changed=true', $s === 200 && $j['data'][0]['state'] === 'aktif' && $j['data'][0]['name_changed'] === true);
pdo()->prepare("UPDATE items SET name='Kavruk Türk Kahvesi 100 g', hash=? WHERE id=?")->execute([md5(mb_strtolower('Kavruk Türk Kahvesi 100 g')), $hid]); clear_caches();
list($s, $j) = api('GET', 'products/'.$I['bim|live|Kavruk Türk Kahvesi 100 g']); $hs = $j['data']['history'] ?? null;
check('/products/{id} fiyat geçmişi: önceki 59,90 TL, %-8', $s === 200 && $hs && $hs['prev_price'] === '59.90' && $hs['change_pct'] === -8, json_encode($hs, JSON_UNESCAPED_UNICODE));
check('  geçmiş cümlesi sitedeki biçimde', $hs && strpos($hs['text'], 'Önceki katalogdan (') === 0 && strpos($hs['text'], '%8 daha ucuz') !== false, $hs['text'] ?? '');
check('  eşleşme yoksa compare=null + açıklama', $j['data']['compare'] === null && $j['data']['compare_reason'] !== null);
list($s, $j) = api('GET', 'products/'.$I['a101|live|Isıtek Airfryer XA4200 4.2 L']); $g = $j['data']['compare'] ?? null;
check('Airfryer: 3 marketlik kesin eşleşme grubu, en ucuz A101', $g && count($g['rows']) === 3 && $g['rows'][0]['market']['slug'] === 'a101' && $g['rows'][0]['is_best'] === true);
check('  fark 600,00 TL, %12, sitedeki tasarruf cümlesi', $g && $g['diff_text'] === '600,00 TL' && $g['diff_pct'] === 12 && $g['saving_text'] === 'En ucuz markette 600,00 TL (%12) daha az.', $g['saving_text'] ?? '');
check('  onaylı + dosyası olan görsel döner', $j['data']['item']['image'] !== null && strpos($j['data']['item']['image']['url'], 'https://marketler.org/img/u/') === 0);
list($s, $j) = api('GET', 'products/'.$I['bim|live|Ekranix 55 inç 4K Smart TV EX55U800']); check('kaldırılmış (status=2) görsel → null', $j['data']['item']['image'] === null);
list($s, $j) = api('GET', 'products/'.$I['sok|live|Minik Bebek Bezi 4 Numara 40 Adet']); check('onaylı ama dosyası olmayan görsel → null', $j['data']['item']['image'] === null);
list($s) = api('GET', 'products/'.$I['bim|pending|GİZLİ ONAY BEKLEYEN ÜRÜN 1 L']); check('/products/{gizli katalogdaki ürün} → 404', $s === 404);
list($s) = api('GET', 'products/999999'); check('/products/{olmayan} → 404', $s === 404);
list($s, $j) = api('GET', 'search?q='.urlencode('en ucuz süt')); check('/search en ucuz süt → full, sort asc, chips', $s === 200 && $j['data']['match'] === 'full' && $j['data']['understood']['sort'] === 'asc' && in_array('En ucuzdan', $j['data']['chips'], true));
list($s, $j) = api('GET', 'search?q='.urlencode('kahwe')); check('/search yazım hatası → sonuç veya öneri', $s === 200 && ($j['meta']['total'] > 0 || $j['data']['did_you_mean'] !== null));
list($s, $j) = api('GET', 'suggest?q='.urlencode('sü')); check('/suggest ≤5 öneri', $s === 200 && count($j['data']) > 0 && count($j['data']) <= 5);
list($s, $j) = api('GET', 'suggest?q=s'); check('/suggest tek harf → boş liste (hata değil)', $s === 200 && $j['data'] === []);
list($s, $j) = api('GET', 'compare'); $titles = array_column($j['data'], 'title');
check('/compare: Airfryer ve Yaylaköy grupları var', $s === 200 && preg_grep('/Airfryer/', $titles) && preg_grep('/Yaylaköy/u', $titles), implode(' | ', $titles));
$man = array_values(array_filter($j['data'], function ($g) { return $g['is_manual']; })); check('/compare: elle kurulan grup (product_links) is_manual', count($man) === 1);
check('/compare: engellenen eşleşme (link_blocks) yok', !preg_grep('/Dereli/u', $titles));
list($s, $j) = api('GET', 'compare?q='.urlencode('airfryer')); check('/compare?q= süzgeç', $s === 200 && $j['meta']['total'] === 1);
list($s, $j) = api('GET', 'compare/similar'); check('/compare/similar gruplar + uyarı metni', $s === 200 && count($j['data']['groups']) > 0 && strpos($j['data']['notice'], 'aynı ürün değildir') !== false);
list($s, $j) = api('POST', 'assistant', json_encode(['message' => 'merhaba'])); check('/assistant merhaba → chat_only', $s === 200 && $j['data']['chat_only'] === true && $j['data']['say'] !== '');
list($s, $j) = api('POST', 'assistant', json_encode(['message' => 'bu hafta hangi kataloglar var'])); check('/assistant katalog sorusu → links', $s === 200 && count($j['data']['links']) === 3);
list($s, $j) = api('POST', 'assistant', json_encode(['message' => 'süt'])); check('/assistant süt → ≤5 ürün', $s === 200 && count($j['data']['items']) > 0 && count($j['data']['items']) <= 5);
list($s, $j) = api('POST', 'assistant', json_encode(['message' => "peki a101'de?", 'last_query' => 'süt'])); check('/assistant devam sorusu önceki aramayla birleşir', $s === 200 && in_array('Market: A101', $j['data']['chips'], true) && $j['data']['effective_query'] === "süt peki a101'de?", json_encode($j['data']['chips'] ?? null, JSON_UNESCAPED_UNICODE));
list($s, $j) = api('GET', 'pages/gizlilik'); check('/pages/gizlilik ayardaki metin, 2 paragraf', $s === 200 && count($j['data']['paragraphs']) === 2);
list($s, $j) = api('GET', 'pages/iletisim'); check('/pages/iletisim varsayılan metin + e-posta', $s === 200 && strpos(implode("\n", $j['data']['paragraphs']), 'iletisim@example.com') !== false);
list($s) = api('GET', 'pages/yok'); check('/pages/yok → 404', $s === 404);
grp('C2. Boş site (katalog yok)');
seed('bos');
list($s, $j) = api('GET', 'home'); check('/home boş: featured boş, başlık "Uygun Fiyatlı Ürünler", top_compare null', $s === 200 && $j['data']['featured']['items'] === [] && $j['data']['featured']['title'] === 'Uygun Fiyatlı Ürünler' && $j['data']['top_compare'] === null);
list($s, $j) = api('GET', 'products'); check('/products boş: total 0', $s === 200 && $j['meta']['total'] === 0 && $j['data'] === []);
list($s, $j) = api('GET', 'compare'); check('/compare boş', $s === 200 && $j['data'] === []);
list($s, $j) = api('GET', 'search?q=s%C3%BCt'); check('/search boş: match none', $s === 200 && $j['data']['match'] === 'none');
list($s, $j) = api('POST', 'assistant', json_encode(['message' => 'süt'])); check('/assistant boş: ürün yok, hata yok', $s === 200 && $j['data']['items'] === []);

/* ---------------- C3. kaldırılan uç noktaların (/meta, /compare/{id}) verisi başka yerden eksiksiz alınabiliyor mu ---------------- */
grp('C3. Kaldırılan uç noktaların karşılığı');
$S = seed('2');
list($s) = api('GET', 'meta'); check('/meta yok (404)', $s === 404); list($s) = api('GET', 'compare/'.reset($S['items'])); check('/compare/{id} yok (404)', $s === 404);
list(, $h) = api('GET', 'home'); check('/meta.data_updated_at → /home data.data_updated_at', $h['data']['data_updated_at'] === '2026-11-23 06:00:12');
$okv = true; foreach ($APIRESP as $r) { $jj = json_decode($r[5], true); if ($r[3] !== 304 && (!is_array($jj) || ($jj['meta']['api_version'] ?? null) !== '1.0' || empty($jj['meta']['generated_at']))) $okv = false; }
check('/meta.api_version ve server_time → HER yanıtta meta.api_version ve meta.generated_at (şu ana kadarki '.count($APIRESP).' yanıt)', $okv);
list(, $h, $hd) = api('GET', 'categories'); check('/meta.ttl → her yanıtta Cache-Control max-age', strpos($hd['cache-control'] ?? '', 'max-age=3600') !== false);
/* /compare/{id} yerine /products/{id}.compare: TÜM gruplardaki TÜM kalemler için birebir aynı grup */
$all = []; for ($pg = 1; ; $pg++) { list(, $x) = api('GET', "compare?limit=50&page=$pg"); $all = array_merge($all, $x['data']); if ($pg * 50 >= $x['meta']['total']) break; }
$miss = []; $inGroup = [];
foreach ($all as $g) foreach ($g['rows'] as $r) { $inGroup[$r['id']] = 1; list(, $x) = api('GET', 'products/'.$r['id']); if (($x['data']['compare'] ?? null) !== $g) $miss[] = $r['id']; }
check('/products/{id}.compare, /compare listesindeki grupla birebir aynı ('.count($inGroup).' kalem, '.count($all).' grup)', count($all) > 0 && !$miss, implode(',', $miss));
$no = 0; $bad = 0; foreach ($S['items'] as $k => $id) { if (isset($inGroup[$id]) || strpos($k, '|live|') === false) continue; $no++; list(, $x) = api('GET', 'products/'.$id); if ($x['data']['compare'] !== null || !$x['data']['compare_reason']) $bad++; }
check("  grupta olmayan $no aktif kalemde compare=null ve açıklama var", $no > 0 && $bad === 0);

/* ---------------- D. web ↔ API eşdeğerlik ---------------- */
foreach (['2', '5'] as $sc) {
  grp("D. Web ↔ API eşdeğerlik (senaryo: $sc indirimli)");
  $S = seed($sc); $C = $S['catalogs'];
  /* ana sayfa */
  $x = dom(web(8082, '/')); list(, $j) = api('GET', 'home');
  $wf = []; foreach ($x->query('//section[@class="ms"][2]//article[@class="mc2"]') as $a) $wf[] = [txt($x->query('.//h3', $a)->item(0)), txt($x->query('.//div[@class="mc2-p"]/b', $a)->item(0))];
  check('home: öne çıkanlar (ad, fiyat, sıra) aynı', $wf === api_cards($j['data']['featured']['items']), json_encode([$wf, api_cards($j['data']['featured']['items'])], JSON_UNESCAPED_UNICODE));
  check('home: öne çıkanlar başlığı aynı', txt($x->query('//section[@class="ms"][2]//h2')->item(0)) === $j['data']['featured']['title']);
  $wl = []; foreach ($x->query('//a[@class="mk2"]//h3') as $n) $wl[] = txt($n); check('home: güncel kataloglar aynı', $wl === array_column($j['data']['live_catalogs'], 'title'));
  $wc = []; foreach ($x->query('//div[@class="mcp-b"]/a') as $a) $wc[] = [txt($x->query('.//span', $a)->item(0)), txt($x->query('.//b', $a)->item(0))];
  $ac = $j['data']['top_compare'] ? array_map(function ($r) { return [$r['market']['name'], $r['price_text']]; }, $j['data']['top_compare']['rows']) : [];
  check('home: öne çıkan karşılaştırma aynı', $wc === $ac);
  /* ürün arama (ara.php ajax) */
  foreach (['', 'q=s%C3%BCt', 'q=s%C3%BCt&o=asc', 'q=s%C3%BCt&o=desc', 'm=bim', 'k=gida&st=hepsi', 'st=yaklasan', 'st=dolmus', 'min=100&max=500', 'q=%25', 'q=_', 'q=Kampanya_Sepeti', 'st=hepsi&off=24', 'q=e%C5%9Fitlik&o=asc', 'k=diger&off=24'] as $qs) {
    $ap = strtr($qs, ['m=' => 'market=', 'k=' => 'category=', 'st=' => 'status=', 'o=' => 'sort=', 'off=24' => 'page=2']);
    $w = json_decode(web(8082, '/ara.php?ajax=1&'.$qs), true); list(, $j) = api('GET', 'products?'.$ap);
    $wc = web_cards($w['html']); $acs = api_cards($j['data']);
    check("products ↔ ara.php [$qs]: toplam ve kart sırası BİREBİR aynı", $w['total'] === $j['meta']['total'] && $wc === $acs, "web {$w['total']} / api {$j['meta']['total']}");
  }
  /* kategoriler */
  $x = dom(web(8082, '/ara.php')); $wo = []; foreach ($x->query('//select[@id="k"]/option[@value!=""]') as $o) $wo[] = $o->getAttribute('value');
  list(, $j) = api('GET', 'categories'); check('categories ↔ ara.php kategori seçimi (sıra)', $wo === array_column($j['data'], 'slug'));
  /* akıllı arama sayfası */
  foreach (['en ucuz süt', 'bim süt 1 lt', 'kahwe', 'airfryer', '500 tl altı kulaklık', 'deterjan', 'indirimli', 'zzzqqq'] as $q) {
    $h = web(8082, '/asistan.php?q='.urlencode($q)); list(, $j) = api('GET', 'search?q='.urlencode($q));
    preg_match('~<b>(\d+) ürün bulundu</b>~', $h, $mm); $wt = (int)($mm[1] ?? 0);
    preg_match('~<b>Anladığım:</b> ([^<]*)</p>~u', $h, $mc); $wch = isset($mc[1]) ? html_entity_decode($mc[1], ENT_QUOTES, 'UTF-8') : '';
    check("search ↔ asistan.php [$q]: toplam, kartlar, 'Anladığım'", $wt === $j['meta']['total'] && web_cards($h) === api_cards($j['data']['items']) && $wch === implode(' · ', $j['data']['chips']), "web $wt / api {$j['meta']['total']}");
  }
  /* öneri */
  foreach (['sü', 'süt 1', 'airf', 'kav', 'xx'] as $q) {
    $w = json_decode(web(8082, '/asistan.php?oneri=1&q='.urlencode($q)), true); list(, $j) = api('GET', 'suggest?q='.urlencode($q));
    $wa = array_map(function ($i) { return [$i['n'], $i['p'], $i['m'], 'https://marketler.org'.$i['u']]; }, $w['items']);
    $aa = array_map(function ($i) { return [$i['name'], $i['price_text'], $i['market_name'], $i['web_url']]; }, $j['data']);
    check("suggest ↔ asistan.php?oneri=1 [$q]", $wa === $aa);
  }
  /* sohbet */
  foreach ([['süt', ''], ['en ucuz kahve', ''], ["peki a101'de?", 'süt'], ['daha ucuzu', 'kahve'], ['merhaba', ''], ['bu hafta hangi kataloglar var', ''], ['yaklaşan kataloglar', ''], ['kahwe', ''], ['500 tl altı tv', ''], ['zzzqqq', '']] as $t) {
    $w = json_decode(web(8082, '/asistan.php?ajax=1&q='.urlencode($t[0]).'&lq='.urlencode($t[1])), true);
    list(, $j) = api('POST', 'assistant', json_encode(['message' => $t[0], 'last_query' => $t[1]])); $d = $j['data'];
    $wi = array_map(function ($i) { return [$i['n'], $i['p'], $i['m'], $i['s'], $i['up'], $i['h']]; }, $w['items'] ?? []);
    $ai = array_map(function ($i) { return [$i['name'], $i['price_text'], $i['market']['name'], $i['status_note'], $i['unit_text'], $i['history_text']]; }, $d['items']);
    $wl = array_map(function ($l) { return [$l['t'], $l['d']]; }, $w['links'] ?? []); $al = array_map(function ($l) { return [$l['title'], $l['date_text']]; }, $d['links']);
    $same = $wi === $ai && $wl === $al && ($w['qr'] ?? []) === $d['quick_replies'] && ($w['chips'] ?? []) === $d['chips'] && (int)($w['total'] ?? 0) === $d['total']
      && (bool)($w['part'] ?? false) === $d['partial'] && ($w['note'] ?? '') === $d['note'] && ($w['fixq'] ?? '') === $d['did_you_mean'] && ($w['fix'] ?? []) === $d['similar_words']
      && (($w['say'] ?? '') === '') === ($d['say'] === '') && (isset($w['eq']) ? $w['eq'] === $d['effective_query'] : $d['effective_query'] === '');
    check("assistant ↔ asistan.php?ajax=1 [{$t[0]}".($t[1] ? " / önceki: {$t[1]}" : '').']', $same, json_encode(['web' => $w, 'api_items' => $ai], JSON_UNESCAPED_UNICODE));
  }
  /* karşılaştırma */
  $x = dom(web(8082, '/fiyat-karsilastirma.php')); list(, $j) = api('GET', 'compare?limit=50');
  $wg = []; foreach ($x->query('(//div[@class="cmps"])[1]/article') as $a) { $rows = []; foreach ($x->query('.//div[contains(@class,"cr")]', $a) as $r) $rows[] = [txt($x->query('.//span[@class="mk"]', $r)->item(0)), txt($x->query('./b', $r)->item(0)), strpos($r->getAttribute('class'), 'best') !== false]; $sv = $x->query('.//p[@class="save"]', $a)->item(0); $wg[] = [txt($x->query('.//h3', $a)->item(0)), $rows, $sv ? txt($sv) : null]; }
  $ag = array_map(function ($g) { return [$g['title'], array_map(function ($r) { return [$r['market']['name'], $r['price_text'], $r['is_best']]; }, $g['rows']), $g['saving_text']]; }, $j['data']);
  check('compare ↔ fiyat-karsilastirma.php: gruplar, sıra, fiyatlar, en düşük, tasarruf cümlesi', $wg === $ag && count($wg) > 0, json_encode([$wg, $ag], JSON_UNESCAPED_UNICODE));
  $wsim = []; foreach ($x->query('//section[@id="benzer"]//article') as $a) { $rows = []; foreach ($x->query('.//div[contains(@class,"cr")]', $a) as $r) $rows[] = txt($x->query('./b', $r)->item(0)); $wsim[] = [txt($x->query('.//h3', $a)->item(0)), $rows]; }
  list(, $j) = api('GET', 'compare/similar');
  $asim = array_map(function ($g) { return [$g['label'].' (TL/'.$g['unit'].')', array_column(array_column($g['rows'], 'unit_price'), 'text')]; }, $j['data']['groups']);
  check('compare/similar ↔ #benzer bölümü', $wsim === $asim, json_encode([$wsim, $asim], JSON_UNESCAPED_UNICODE));
  /* kataloglar, katalog, market */
  $x = dom(web(8082, '/kataloglar.php')); list(, $j) = api('GET', 'catalogs?limit=50');
  $ok = true; foreach (['aktif' => 'live', 'yaklasan' => 'soon', 'arsiv' => 'past'] as $id => $k) { $wt = []; foreach ($x->query('//section[@id="'.$id.'"]//a[@class="cc"]//h3') as $n) $wt[] = txt($n); if ($wt !== array_column($j['data'][$k], 'title')) $ok = false; }
  check('catalogs ↔ kataloglar.php (üç bölüm, sıra)', $ok);
  $x = dom(web(8082, '/market/sok')); list(, $j) = api('GET', 'markets/sok');
  $ok = true; foreach (['aktif' => 'live', 'yaklasan' => 'soon', 'arsiv' => 'past'] as $id => $k) { $wt = []; foreach ($x->query('//section[@id="'.$id.'"]//a[@class="cc"]//h3') as $n) $wt[] = txt($n); if ($wt !== array_column($j['data']['catalogs'][$k], 'title')) $ok = false; }
  check('markets/sok ↔ /market/sok', $ok);
  foreach ([$C['bim']['live'][1], $C['a101']['past'][1], $C['sok']['soon'][1]] as $slug) {
    $h = web(8082, '/katalog/'.$slug); list(, $j) = api('GET', 'catalogs/'.$slug);
    $ai = []; foreach ($j['data']['groups'] as $g) $ai = array_merge($ai, api_cards($g['items']));
    $wth = substr_count($h, 'data-full=');
    check("catalogs/{slug} ↔ katalog.php [$slug]: ürünler, sayfa sayısı", web_cards($h) === $ai && $wth === count($j['data']['pages']) + $j['data']['pages_skipped']);
  }
  foreach (['gizlilik', 'hakkimizda', 'iletisim', 'kosullar'] as $p) {
    $x = dom(web(8082, '/sayfa.php?s='.$p)); $wp = []; foreach ($x->query('//div[@class="page"]/p') as $n) $wp[] = preg_replace('/\s+/u', ' ', trim($n->textContent));
    list(, $j) = api('GET', 'pages/'.$p); check("pages/$p ↔ sayfa.php", $wp === array_map(function ($t) { return preg_replace('/\s+/u', ' ', $t); }, $j['data']['paragraphs']));
  }
}

/* ---------------- T. Türkçe karakter ve ürün adı farkları ---------------- */
grp('T. Türkçe karakter ve ürün adı farkları');
$S = seed('2'); $I = $S['items'];
$names = ['İnci Kaynak Su 5 L', 'ISITEK Mini Fırın 20 L', 'Şekerci Toz Şeker 1 kg', 'Çaydağı Siyah Çay 1 kg', 'Öğün Süzme Yoğurt 1 kg', 'DAĞKÖY PEYNİR 500 G', 'Eşitlik İota Paketi 1 kg'];
list(, $j) = api('GET', 'products?status=hepsi&limit=50&q='); $got = []; for ($pg = 1; ($pg - 1) * 50 < $j['meta']['total']; $pg++) { list(, $x) = api('GET', "products?status=hepsi&limit=50&page=$pg"); $got = array_merge($got, array_column($x['data'], 'name')); }
check('UTF-8: Türkçe adlar API\'de baytı baytına korunuyor ('.count($names).' ad)', !array_diff($names, $got));
$raw = http(8082, 'GET', '/api/v1/products/'.$I['bim|live|İnci Kaynak Su 5 L'])[2]; check('  JSON, Türkçe karakterleri \\u kaçışı olmadan UTF-8 taşıyor', strpos($raw, 'İnci Kaynak Su 5 L') !== false && strpos($raw, '\\'.'u0130') === false && strpos($raw, '\\'.'u0131') === false);
foreach (['İnci', 'inci', 'INCI', 'ISITEK', 'ısıtek', 'Isıtek', 'şeker', 'ŞEKER', 'Şeker', 'çay', 'ÇAY', 'dağköy', 'DAĞKÖY', 'yoğurt', 'YOĞURT', 'kırmızı', 'KIRMIZI', 'eşitlik iota'] as $q) {
  $w = json_decode(web(8082, '/ara.php?ajax=1&st=hepsi&q='.urlencode($q)), true); list(, $a) = api('GET', 'products?status=hepsi&q='.urlencode($q));
  $h = web(8082, '/asistan.php?q='.urlencode($q)); list(, $sx) = api('GET', 'search?q='.urlencode($q)); preg_match('~<b>(\d+) ürün bulundu</b>~', $h, $mm);
  $wo = json_decode(web(8082, '/asistan.php?oneri=1&q='.urlencode($q)), true); list(, $su) = api('GET', 'suggest?q='.urlencode($q));
  check("[$q] ara.php / asistan.php / öneri ile API aynı (".$w['total'].' / '.(int)($mm[1] ?? 0).' / '.count($wo['items']).' sonuç)',
    $w['total'] === $a['meta']['total'] && web_cards($w['html']) === api_cards($a['data']) && (int)($mm[1] ?? 0) === $sx['meta']['total'] && web_cards($h) === api_cards($sx['data']['items'])
    && array_column($wo['items'], 'n') === array_column($su['data'], 'name'));
}
$kp = $I['bim|past|Dağköy Peynir 500 g'].':1:'.md5(mb_strtolower('Dağköy Peynir 500 g')); $km = $I['bim|past|Kırmızı Mercimek 1 kg'].':1:'.md5(mb_strtolower('Kırmızı Mercimek 1 kg'));
list(, $j) = api('GET', "products?fav=$kp,$km"); $F = $j['data'];
check('favori "Dağköy Peynir 500 g" (geçen hafta) → bu hafta "DAĞKÖY PEYNİR 500 G" ile EŞLEŞTİRİLMEZ (hash farklı, aday yok)', $F[0]['state'] === 'suresi_doldu' && $F[0]['candidates'] === []);
check('favori "Kırmızı Mercimek 1 kg" → aynı yazımlı yeni kalem yalnızca ADAY, confirmed=false', $F[1]['state'] === 'suresi_doldu' && count($F[1]['candidates']) === 1 && $F[1]['candidates'][0]['confirmed'] === false && $F[1]['candidates'][0]['item']['id'] === $I['bim|live|Kırmızı Mercimek 1 kg']);

/* ---------------- E. güvenlik ---------------- */
grp('E. Güvenlik testleri');
seed('2');
foreach (["products?market=bim'%20OR%20'1'='1", "products?category=gida';--", 'products?page=0', 'products?page=-1', 'products?limit=51', 'products?limit=abc', 'products?status=x', 'products?sort=price;drop',
  'products?q='.str_repeat('a', 121), 'products?q=%ff%fe', 'products?fav=1:xyz', 'products?fav=1:1:xyz', 'products?fav=0:1:'.md5('a'), 'products?fav=1:'.md5('a'), 'products?fav='.implode(',', array_map(function ($i) { return "$i:1:".md5((string)$i); }, range(1, 51))), 'catalogs?market=../etc', 'search?q=', 'search?q='.urlencode(str_repeat('ş', 121)), 'products?date=2026-1-1'] as $p) {
  list($s, $j) = api('GET', $p); check("400 INVALID_PARAMETER: $p", $s === 400 && ($j['error']['code'] ?? '') === 'INVALID_PARAMETER', (string)$s);
}
foreach (["products/1%20OR%201=1", "catalogs/x'%3B", 'products/0', 'products/abc', 'markets/BIM', 'bilinmeyen', 'compare/x', 'home/1', '', 'lib/bootstrap.php', 'endpoints/home.php', 'config.php'] as $p) { list($s, $j) = api('GET', $p); check("404: /api/v1/$p", $s === 404, (string)$s); }
list($s, $j) = api('GET', 'products?q='.urlencode("' OR 1=1 -- ")); check("SQL enjeksiyon metni q içinde düz metin olarak aranır (200, 0 sonuç)", $s === 200 && $j['meta']['total'] === 0);
list($s, $j) = api('GET', 'search?q='.urlencode("'; DROP TABLE items; --")); check('search SQL metni: 200, tablo yerinde', $s === 200 && (int)pdo()->query('SELECT COUNT(*) FROM items')->fetchColumn() > 0);
foreach ([['POST', 'home'], ['PUT', 'products'], ['DELETE', 'compare'], ['GET', 'assistant'], ['PATCH', 'pages/gizlilik']] as $t) { list($s) = api($t[0], $t[1]); check("405: {$t[0]} {$t[1]}", $s === 405, (string)$s); }
list($s) = api('POST', 'assistant', str_repeat('x', 3000)); check('assistant gövde > 2 KB → 413', $s === 413, (string)$s);
foreach (['{bozuk', '[]', json_encode(['message' => '']), json_encode(['message' => str_repeat('a', 121)]), json_encode(['message' => 'süt', 'include_expired' => 'evet']), json_encode(['message' => ['dizi']]), json_encode(['message' => 'a', 'last_query' => str_repeat('b', 121)])] as $b) {
  list($s, $j) = api('POST', 'assistant', $b); check('assistant geçersiz gövde → 400: '.substr($b, 0, 40), $s === 400, (string)$s);
}
list($s, $j, $h) = api('GET', 'products'); check('başlıklar: application/json, nosniff, no-referrer', strpos($h['content-type'] ?? '', 'application/json') === 0 && ($h['x-content-type-options'] ?? '') === 'nosniff' && ($h['referrer-policy'] ?? '') === 'no-referrer');
list($s, $j) = api('GET', 'products?q=%3Cscript%3Ealert(1)%3C/script%3E'); check('HTML/JS girdisi JSON içinde çalıştırılabilir çıktı üretmez', $s === 200);
/* HTTPS zorunluluğu ve hız sınırı (:8083, std=5/dk) */
list($s, , $b) = http(8083, 'GET', '/api/v1/markets'); $j = json_decode($b, true); check('HTTPS yoksa 400 HTTPS_REQUIRED', $s === 400 && ($j['error']['code'] ?? '') === 'HTTPS_REQUIRED');
$got429 = false; for ($try = 0; $try < 2 && !$got429; $try++) { if (time() % 60 > 50) sleep(61 - time() % 60); $st = []; for ($i = 0; $i < 7; $i++) { list($s, $h) = http(8083, 'GET', '/api/v1/markets', null, ['X-Forwarded-Proto: https']); $st[] = $s; if ($s === 429) { $got429 = isset($h['retry-after']); break; } } }
check('hız sınırı: 5 istek/dk sonrası 429 + Retry-After', $got429, implode(',', $st));
/* Cloudflare: güvenilmeyen kaynaktan gelen CF-Connecting-IP YOK SAYILIR */
clear_caches(); if (time() % 60 > 45) sleep(61 - time() % 60);
$st = []; for ($i = 1; $i <= 7; $i++) { list($s) = http(8083, 'GET', '/api/v1/markets', null, ['X-Forwarded-Proto: https', "CF-Connecting-IP: 198.51.100.$i"]); $st[] = $s; }
check('Cloudflare dışı kaynak: her istekte farklı sahte CF-Connecting-IP gönderilse de sınır aşılamaz (6. istek 429)', $st[5] === 429, implode(',', $st));
/* güvenilir kaynak (test: 127.0.0.1 Cloudflare listesine eklenmiş örnek) */
clear_caches(); if (time() % 60 > 45) sleep(61 - time() % 60);
$a = []; $b = []; for ($i = 0; $i < 6; $i++) { $a[] = http(8084, 'GET', '/api/v1/markets', null, ['CF-Connecting-IP: 203.0.113.10'])[0]; } for ($i = 0; $i < 5; $i++) $b[] = http(8084, 'GET', '/api/v1/markets', null, ['CF-Connecting-IP: 2001:db8::7'])[0];
check('güvenilir Cloudflare kaynağı: sınır ziyaretçi IP\'sine göre (A: 6. istek 429; B (IPv6) etkilenmez)', $a[5] === 429 && !in_array(429, $b, true), implode(',', $a).' | '.implode(',', $b));
clear_caches(); if (time() % 60 > 45) sleep(61 - time() % 60);
$c = []; for ($i = 0; $i < 7; $i++) $c[] = http(8084, 'GET', '/api/v1/markets', null, ['CF-Connecting-IP: '.($i % 2 ? 'gecersiz-ip' : '999.1.1.1')])[0];
check('güvenilir kaynak + geçersiz CF-Connecting-IP değeri → başlık yok sayılır, bağlantı adresi sayılır (6. istek 429)', $c[5] === 429, implode(',', $c));
$u = 'define("MK_API",1); const MK_API_TRUSTED_PROXIES=[]; require '.var_export("$WORK/new/api/v1/lib/ratelimit.php", true).'; $t=[["173.245.48.1","173.245.48.0/20",1],["173.245.64.0","173.245.48.0/20",0],["104.16.0.1","104.16.0.0/13",1],["104.24.0.1","104.16.0.0/13",0],["162.159.255.255","162.158.0.0/15",1],["2606:4700:10::1","2606:4700::/32",1],["2606:4701::1","2606:4700::/32",0],["2a06:98c7:ffff::1","2a06:98c0::/29",1],["2a06:98c8::1","2a06:98c0::/29",0],["10.0.0.1","2606:4700::/32",0],["gecersiz","10.0.0.0/8",0],["10.1.2.3","10.0.0.0/8",1]]; $ok=1; foreach($t as $x) if((int)mk_ip_in_cidr($x[0],$x[1])!==$x[2]){$ok=0; echo "HATA ".$x[0]." ".$x[1]."\n";} echo $ok?"TAMAM":"";';
list($rc, $o) = sh('php -r '.escapeshellarg($u)); check('CIDR denetimi birim testi (IPv4/IPv6, sınır değerleri, geçersiz adres) — 12 durum', trim($o) === 'TAMAM', $o);
/* hata durumlarında JSON korunur */
list($s, , $hd, $b) = apiat(8086, 'GET', 'home'); $j = json_decode($b, true);
check('ölümcül PHP hatası → JSON 500 INTERNAL_ERROR (ayrıntı sızmaz)', $s === 500 && ($j['error']['code'] ?? '') === 'INTERNAL_ERROR' && strpos($hd['content-type'] ?? '', 'application/json') === 0 && strpos($b, 'tanimsiz') === false && strpos($b, '/tmp') === false, "$s ".substr($b, 0, 120));
list($s, , $hd, $b) = apiat(8087, 'GET', 'home'); $j = json_decode($b, true);
check('site kodunun düz metin basıp çıkması (inc.php db() davranışı) → JSON 503 SERVICE_UNAVAILABLE', $s === 503 && ($j['error']['code'] ?? '') === 'SERVICE_UNAVAILABLE' && strpos($hd['content-type'] ?? '', 'application/json') === 0 && strpos($b, 'bakımda') === false, "$s ".substr($b, 0, 120));
list($s, , $hd, $b) = apiat(8088, 'GET', 'products'); $j = json_decode($b, true);
check('veritabanına ulaşılamıyor → JSON 503 + Retry-After', $s === 503 && ($j['error']['code'] ?? '') === 'SERVICE_UNAVAILABLE' && isset($hd['retry-after']), "$s ".substr($b, 0, 120));
list($s, , $hd, $b) = apiat(8088, 'POST', 'assistant', json_encode(['message' => 'süt'])); check('  POST /assistant aynı durumda da JSON 503', $s === 503 && (json_decode($b, true)['success'] ?? null) === false);
$bad = []; $nErr = 0;
foreach ($APIRESP as $r) { if ($r[3] < 400) continue; $nErr++; $jj = json_decode($r[5], true);
  if (!is_array($jj) || $jj['success'] !== false || !preg_match('/^[A-Z_]+$/', $jj['error']['code'] ?? '') || ($jj['error']['message'] ?? '') === '' || empty($jj['meta']['generated_at']) || strpos($r[4]['content-type'] ?? '', 'application/json') !== 0) $bad[] = $r[3].' '.$r[2]; }
check("şu ana kadarki TÜM hata yanıtları ($nErr adet: 400/404/405/413/429/500/503) geçerli JSON hata zarfı", $nErr > 30 && !$bad, implode(' | ', array_slice($bad, 0, 5)));
/* lib dosyaları doğrudan yüklenirse hiçbir şey yapmaz */
$out = ''; foreach (glob("$WORK/new/api/v1/{lib,endpoints}/*.php", GLOB_BRACE) as $f) { list(, $o) = sh('php -r '.escapeshellarg('include '.var_export($f, true).';')); $out .= $o; }
check('lib/ ve endpoints/ dosyaları MK_API olmadan çıktı üretmez/çalışmaz', $out === '', substr($out, 0, 200));
/* sızıntı taraması: bu ana kadarki TÜM API yanıtları */
$leaks = []; foreach ([MK_SEED_SECRET => 'cron_key değeri', 'cron_key' => 'cron_key adı', 'yonetici' => 'yönetici kullanıcı adı', '$2y$' => 'parola özeti', 'GİZLİ' => 'gizli/onay bekleyen ürün', 'Stack trace' => 'yığın izi', 'PDOException' => 'istisna adı', 'SQLSTATE' => 'SQL hata metni', '/tmp/' => 'sunucu yolu', 'Warning:' => 'PHP uyarısı', 'Notice:' => 'PHP notu', 'Fatal' => 'PHP hatası', 'aktuel-urunler.com' => 'uzak görsel adresi'] as $needle => $what) if (strpos($ALLBODY, $needle) !== false) $leaks[] = $what;
check('sızıntı yok (gizli ayar, yönetici, gizli katalog, hata ayrıntısı, sunucu yolu, uzak görsel)', !$leaks, implode(', ', $leaks));

/* ---------------- F. salt okunurluk ---------------- */
grp('F. Salt okunurluk (inc.php değiştirilmeden)');
$p = pdo(); $me = (int)$p->query('SELECT CONNECTION_ID()')->fetchColumn();
$p->exec("SET GLOBAL log_output='TABLE'"); $p->exec('TRUNCATE TABLE mysql.general_log'); $p->exec("SET GLOBAL general_log='ON'");
$S = seed('2'); $p->exec('TRUNCATE TABLE mysql.general_log');
$TBL = ['admin_log','catalogs','categories','items','item_media','link_blocks','logs','prices','product_links','products','settings','stores','users'];
$cks = function () use ($p, $TBL) { $o = []; foreach ($p->query('CHECKSUM TABLE '.implode(',', $TBL).' EXTENDED')->fetchAll(PDO::FETCH_NUM) as $r) $o[$r[0]] = $r[1]; return $o; };
$CK0 = $cks();
foreach (['home', 'markets', 'markets/bim', 'catalogs', 'catalogs/'.$S['catalogs']['bim']['live'][1], 'categories', 'products', 'products?q=s%C3%BCt', 'products?fav=1:'.str_repeat('a', 32), 'products/'.reset($S['items']), 'search?q=s%C3%BCt', 'suggest?q=s%C3%BC', 'compare', 'compare/similar', 'pages/gizlilik'] as $c) api('GET', $c);
foreach (['süt', 'merhaba', 'bu hafta hangi kataloglar var', "peki a101'de?"] as $mm) api('POST', 'assistant', json_encode(['message' => $mm, 'last_query' => 'süt']));
/* aynı çağrılar SELECT-yetkili kullanıcıyla çalışan örnekte (:8085) */
$roBad = [];
foreach (['home', 'markets', 'markets/bim', 'catalogs', 'catalogs/'.$S['catalogs']['bim']['live'][1], 'categories', 'products', 'products?q=s%C3%BCt&sort=asc', 'products?fav='.reset($S['items']).':1:'.str_repeat('a', 32), 'products/'.reset($S['items']), 'search?q=s%C3%BCt', 'suggest?q=s%C3%BC', 'compare', 'compare/similar', 'pages/gizlilik'] as $c) { list($s, , , $b1) = apiat(8085, 'GET', $c); list(, , , $b2) = api('GET', $c); if ($s !== 200 || preg_replace('/"generated_at":"[^"]+"/', '', $b1) !== preg_replace('/"generated_at":"[^"]+"/', '', $b2)) $roBad[] = "$c ($s)"; }
foreach (['süt', 'merhaba', "peki a101'de?"] as $mm) { list($s) = apiat(8085, 'POST', 'assistant', json_encode(['message' => $mm, 'last_query' => 'süt'])); if ($s !== 200) $roBad[] = "assistant $mm ($s)"; }
$p->exec("SET GLOBAL general_log='OFF'");
$CK1 = $cks();
$rows = $p->query("SELECT thread_id, command_type, CONVERT(argument USING utf8mb4) a FROM mysql.general_log WHERE thread_id<>$me AND command_type IN ('Query','Execute','Prepare')")->fetchAll(PDO::FETCH_ASSOC);
$bad = []; $nSel = 0; $nRo = 0; $threads = [];
foreach ($rows as $r) { $a = trim($r['a']); $threads[$r['thread_id']] = 1;
  if (preg_match('/^SELECT\b/i', $a)) $nSel++; elseif (preg_match('/^SET SESSION TRANSACTION READ ONLY$/i', $a)) $nRo++; else $bad[] = substr($a, 0, 80); }
check('T-RO-1 sorgu günlüğü: API bağlantılarında yalnızca SELECT + oturum kilidi', count($rows) > 0 && !$bad, implode(' | ', array_slice($bad, 0, 5)));
echo "    (".count($threads)." bağlantı, $nSel SELECT, $nRo kilit ifadesi, ".count($bad)." diğer)\n";
check('  her API bağlantısı kilitlendi (bağlantı başına 1 kilit; ön kontrol + inc.php bağlantısı)', $nRo === count($threads), "$nRo / ".count($threads));
list($rc, $o) = sh('php '.escapeshellarg(__DIR__.'/readonly_probe.php').' '.escapeshellarg("$WORK/new"));
check('T-RO-2 API önyüklemesinden sonra q("INSERT …") veritabanınca reddedilir (1792)', $rc === 0 && strpos($o, 'REDDEDILDI 1792') !== false, $o);
check('T-RO-4 (bağımsız) YALNIZCA SELECT yetkili kullanıcıyla bütün uç noktalar çalışıyor ve root ile aynı yanıtı veriyor (18 çağrı)', !$roBad, implode(', ', $roBad));
check('T-RO-5 (bağımsız) bütün API çağrılarından önce/sonra 13 tablonun sağlama toplamı (CHECKSUM TABLE) aynı', $CK0 === $CK1 && count($CK0) === 13, json_encode(array_diff_assoc($CK1, $CK0)));
$before = [(int)$p->query('SELECT COUNT(*) FROM categories')->fetchColumn(), (int)$p->query('SELECT COUNT(*) FROM settings')->fetchColumn()];
$p->exec("UPDATE settings SET v='4' WHERE k='schema_v'"); $p->exec('TRUNCATE TABLE mysql.general_log'); $p->exec("SET GLOBAL general_log='ON'");
list($s, $j) = api('GET', 'home'); list($s2) = api('POST', 'assistant', json_encode(['message' => 'süt']));
$p->exec("SET GLOBAL general_log='OFF'");
$w = $p->query("SELECT COUNT(*) FROM mysql.general_log WHERE thread_id<>$me AND command_type IN ('Query','Execute') AND CONVERT(argument USING utf8mb4) NOT REGEXP '^(SELECT|SET SESSION TRANSACTION READ ONLY)'")->fetchColumn();
$after = [(int)$p->query('SELECT COUNT(*) FROM categories')->fetchColumn(), (int)$p->query('SELECT COUNT(*) FROM settings')->fetchColumn()];
$sv = $p->query("SELECT v FROM settings WHERE k='schema_v'")->fetchColumn();
check("T-RO-3 schema_v='4' iken API 503 döner, inc.php yüklenmez, hiçbir yazma olmaz", $s === 503 && $s2 === 503 && (int)$w === 0 && $before === $after && $sv === '4', "durum $s/$s2, yazma $w, schema_v=$sv");
web(8081, '/'); $sv2 = $p->query("SELECT v FROM settings WHERE k='schema_v'")->fetchColumn();
check("  karşılaştırma: aynı durumda SİTE (inc.php) şemayı günceller (schema_v '4'→'$sv2') — korumanın neden gerekli olduğunu gösterir", $sv2 === '5');

/* ---------------- G. statik denetimler ---------------- */
grp('G. Statik denetimler');
$apiFiles = array_merge(glob("$SITE_NEW_FILES/api/v1/*.php"), glob("$SITE_NEW_FILES/api/v1/*/*.php"), ["$SITE_NEW_FILES/ortak-veri.php"]);
$hits = []; foreach ($apiFiles as $f) foreach (file($f) as $n => $l) if (preg_match('/\b(INSERT|UPDATE|DELETE|REPLACE|ALTER|CREATE|DROP|TRUNCATE|GRANT|RENAME)\b/', $l)) $hits[] = basename($f).':'.($n + 1);
check('API ve ortak-veri.php içinde yazma SQL anahtar kelimesi yok', !$hits, implode(', ', $hits));
$lint = []; foreach (array_merge($apiFiles, ["$SITE_NEW_FILES/tema-modern-ana.php"]) as $f) { list($rc, $o) = sh('php -l '.escapeshellarg($f)); if ($rc !== 0) $lint[] = $o; }
check('php -l (PHP '.PHP_VERSION.') tüm dosyalar', !$lint, implode("\n", $lint));
/* PHP 7.4 söz dizimi sınırı: 8.0+ özellikleri kullanılmıyor */
$feat = []; foreach ($apiFiles as $f) {
  $code = ''; foreach (token_get_all(file_get_contents($f)) as $t) { if (is_array($t) && in_array($t[0], [T_CONSTANT_ENCAPSED_STRING, T_ENCAPSED_AND_WHITESPACE, T_COMMENT, T_DOC_COMMENT, T_INLINE_HTML], true)) { $code .= ' "" '; continue; } $code .= is_array($t) ? $t[1] : $t; }
  foreach (['/\bmatch\s*\(/' => 'match', '/\?->/' => 'nullsafe', '/\benum\s+\w/' => 'enum', '/\breadonly\b/' => 'readonly', '/\bstr_(contains|starts_with|ends_with)\s*\(/' => 'str_contains/starts/ends', '/#\[/' => 'attribute', '/\bnever\b/' => 'never', '/[(,]\s*[A-Za-z_]\w*\s*:(?!:)/' => 'adlandırılmış argüman'] as $re => $nm) if (preg_match($re, $code)) $feat[] = basename($f).": $nm"; }
check('PHP 8.0+ özel söz dizimi kullanılmıyor (7.4 uyumluluğu hedefi)', !$feat, implode(', ', $feat));
/* kopya bütünlüğü: kopyalanan kaynak satırları hedefte birebir var mı */
$copies = [
  ['ara.php', range(7, 16), 'api/v1/endpoints/products.php', []],
  ['asistan.php', range(11, 25), 'api/v1/endpoints/suggest.php', ['md5(__DIR__)' => 'md5(MK_SITE)']],
  ['asistan.php', [33, 34, 35, 36, 38, 39, 40, 41], 'api/v1/endpoints/assistant.php', []],
  ['asistan.php', [63], 'api/v1/endpoints/assistant.php', []],
  ['asistan.php', [45, 46, 47, 50, 51, 52, 53, 58, 59], 'api/v1/lib/weblogic.php', []],
  ['asistan.php', [55], 'api/v1/lib/weblogic.php', ['function as_uline(' => 'function mk_as_uline(']],
  ['sayfa.php', [3, 5, 6, 7], 'api/v1/lib/weblogic.php', []],
  ['sayfa.php', [4], 'api/v1/lib/weblogic.php', ['$P = [' => 'return [']],
  ['sayfa.php', [9], 'api/v1/endpoints/pages.php', []],
  ['tema-modern-ana.php', [66, 67, 68, 69, 70], 'api/v1/endpoints/home.php', []],
];
foreach ($copies as $c) { $src = file(is_file("$SITE_NEW_FILES/{$c[0]}") && $c[0] === 'ara.php' ? "$SITE_NEW_FILES/{$c[0]}" : "$SRC/{$c[0]}");   /* ara.php: onaylanan düzeltilmiş sürümle karşılaştırılır */ $dst = file_get_contents("$SITE_NEW_FILES/{$c[2]}"); $miss = [];
  foreach ($c[1] as $ln) { $l = strtr(trim($src[$ln - 1]), $c[3]); if ($l !== '' && strpos($dst, $l) === false) $miss[] = $ln; }
  check("kopya bütünlüğü: {$c[0]} satır ".min($c[1]).'-'.max($c[1])." → {$c[2]}", !$miss, 'eksik/değişmiş satır: '.implode(',', $miss)); }
/* ortak-veri.php: taşınan blok orijinalde aynen var mı */
$orig = file_get_contents("$SRC/tema-modern-ana.php"); $ov = file_get_contents("$SITE_NEW_FILES/ortak-veri.php");
$blk = substr($ov, strpos($ov, '/* Öne çıkan fırsatlar')); check('ortak-veri.php bloğu orijinal tema-modern-ana.php içinde karakteri karakterine mevcut', strpos($orig, $blk) !== false && strlen($blk) > 4000, strlen($blk).' bayt');
$nt = file_get_contents("$SITE_NEW_FILES/tema-modern-ana.php"); $rebuilt = str_replace("require_once __DIR__.'/ortak-veri.php';   /* mt_featured() ve MT_MIN_GAIN buraya taşındı (içerik değişmedi); API de aynı fonksiyonu kullanır */\n\n", $blk, $nt);
check('yeni tema-modern-ana.php = orijinal − blok + tek require_once satırı', $rebuilt === $orig);
list($rc, $o) = sh('cd '.escapeshellarg(dirname($REPO, 2)).' && git status --porcelain MarketlerAndroid && git diff --stat HEAD -- MarketlerAndroid');
check('WebView projesine (MarketlerAndroid/) dokunulmadı', $rc === 0 && trim($o) === '', $o);

/* ---------------- H. items.hash ↔ as_hkey ---------------- */
grp('H. Favori anahtarı: items.hash ile as_hkey karşılaştırması (birim testi)');
require_once "$SRC/asistan-motor.php";
$pairs = [['Yaylaköy Süt 1 L', 'YAYLAKÖY SÜT 1 L'], ['Yaylaköy Süt 1 L', 'Yaylaköy  Süt 1 L'], ['Yaylaköy Süt 1 L', ' Yaylaköy Süt 1 L '], ['Yaylaköy Süt 1 L', 'Yaylakoy Sut 1 L'],
  ['Kahve 1,5 kg', 'Kahve 1.5 kg'], ['Çay 1000 g', 'Çay 1 kg'], ['Çay 1 L', 'Çay 1 Lt'], ['Isıtek Airfryer', 'ISITEK AIRFRYER'], ['İnci Su 5 L', 'inci Su 5 L'], ['Coca-Cola 1 L', 'Coca Cola 1 L']];
$tbl = []; $sub = true;
foreach ($pairs as $pr) { $h = md5(mb_strtolower($pr[0])) === md5(mb_strtolower($pr[1])); $k = as_hkey(['ss' => 'bim', 'name' => $pr[0]]) === as_hkey(['ss' => 'bim', 'name' => $pr[1]]); $tbl[] = [$pr, $h, $k]; if ($h && !$k) $sub = false; }
foreach ($tbl as $t) echo '    '.str_pad('"'.$t[0][0].'" ~ "'.$t[0][1].'"', 58).' hash:'.($t[1] ? 'AYNI ' : 'farklı').'  hkey:'.($t[2] ? 'AYNI' : 'farklı')."\n";
check('hash ile as_hkey AYNI DEĞİL: as_hkey daha toleranslı (boşluk, Türkçe karakter, ondalık ayırıcı)', count(array_filter($tbl, function ($t) { return $t[1] !== $t[2]; })) > 0);
check('bu örneklerde: hash aynıysa hkey de aynı (hash daha katı)', $sub);
check('ikisi de birim farkını (1000 g / 1 kg, L / Lt) ve marka yazımını (Coca-Cola / Coca Cola) ayırır', !$tbl[5][1] && !$tbl[5][2] && !$tbl[6][1] && !$tbl[6][2] && !$tbl[9][1] && !$tbl[9][2]);
echo "    Not: Gerçek verideki haftalar arası kararlılık bu testle ÖLÇÜLEMEZ; docs/sql/D3-sayim-sorgulari.sql sonuçları gerekir.\n";

/* ---------------- rapor ---------------- */
$pass = count(array_filter($R, function ($r) { return $r[2]; })); $fail = count($R) - $pass;
echo "\n========================================\nTOPLAM: ".count($R)." kontrol · $pass geçti · $fail başarısız\n";
if (!empty($opt['rapor'])) {
  $md = "| Grup | Kontrol | Sonuç |\n|---|---|---|\n";
  foreach ($R as $r) $md .= '| '.$r[0].' | '.str_replace('|', '\|', $r[1]).' | '.($r[2] ? '✓' : '✗ '.str_replace(['|', "\n"], ['\|', ' '], mb_substr($r[3], 0, 200))).' |'."\n";
  file_put_contents($opt['rapor'], "Ortam: PHP ".PHP_VERSION.' · '.pdo_version().' · '.date('Y-m-d H:i')."\n\nToplam ".count($R)." kontrol · $pass geçti · $fail başarısız\n\n".$md);
}
exit($fail ? 1 : 0);
