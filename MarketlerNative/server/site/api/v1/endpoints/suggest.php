<?php
/* GET /api/v1/suggest?q= — asistan.php?oneri=1 ile aynı öneri (en çok 5). Sitenin havuz önbellek dosyası ortak kullanılır. */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_endpoint($param) {
  $qs = trim(mk_in_str('q', 120)); $t = today();
  /* KOPYA: asistan.php satır 11-26 (öneri modu). __DIR__ yerine MK_SITE: önbellek dosya adı sitedekiyle aynı olur. */
  $qf = as_fold(trim($qs)); $out = [];
  if (mb_strlen($qf) >= 2) {
    $cf = sys_get_temp_dir().'/mk_oneri_'.md5(MK_SITE).'.json'; $pool = null;
    if (is_file($cf) && time() - (int)@filemtime($cf) < 120) $pool = json_decode((string)@file_get_contents($cf), true);
    if (!is_array($pool)) {
      $pool = []; foreach (q('SELECT i.name,i.price,s.name sn,c.slug FROM items i JOIN catalogs c ON c.id=i.catalog_id JOIN stores s ON s.id=c.store_id WHERE c.hidden=0 AND '.CEND.'>=? ORDER BY c.cdate DESC,i.id DESC LIMIT 6000', [$t])->fetchAll() as $r)
        $pool[] = [$r['name'], (float)$r['price'], $r['sn'], $r['slug'], as_fold($r['name'])];
      $tmp = $cf.'.'.getmypid(); if (@file_put_contents($tmp, json_encode($pool, JSON_UNESCAPED_UNICODE)) !== false) @rename($tmp, $cf);
    }
    $tk = array_values(array_filter(preg_split('/\s+/', $qf), function($x) { return $x !== ''; })); $hit = [];
    foreach ($pool as $p) {
      $sc = 0; foreach ($tk as $x) { $pos = strpos($p[4], $x); if ($pos === false) { $sc = -1; break; } $sc += ($pos === 0 || $p[4][$pos - 1] === ' ') ? 2 : 1; $en = $pos + strlen($x); if ($sc > 0 && ($en >= strlen($p[4]) || $p[4][$en] === ' ')) $sc += 1; }
      if ($sc < 0) continue; if (strpos($p[4], $qf) === 0) $sc += 2; $hit[] = [$sc, strlen($p[0]), $p];
    }
    usort($hit, function($a, $b) { return $b[0] <=> $a[0] ?: $a[1] <=> $b[1]; }); $seen = [];
    foreach ($hit as $h) { $p = $h[2]; $k = $p[4].'|'.$p[2]; if (isset($seen[$k])) continue; $seen[$k] = 1; $out[] = ['name' => $p[0], 'price_text' => tl($p[1]), 'market_name' => $p[2], 'catalog_slug' => $p[3], 'web_url' => mk_abs(u_kat($p[3]).'#viewer')]; if (count($out) >= 5) break; }
  }
  mk_ok($out, [], 'private, max-age=60');
}
