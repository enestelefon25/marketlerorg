<?php
/* GET /api/v1/home — modern ana sayfanın (tema-modern-ana.php) verisi. */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_endpoint($param) {
  $stores = q('SELECT * FROM stores')->fetchAll();
  /* KOPYA: tema-modern-ana.php "---- veri ----" bloğu (katalog gruplama ve sıralama) */
  $per = []; foreach (q(CSEL.' ORDER BY c.cdate DESC,c.id DESC LIMIT 400')->fetchAll() as $c) $per[$c['ss']][] = $c;
  $live = []; $soon = []; $cnt = [];
  foreach ($stores as $s) { $g = by_status($per[$s['slug']] ?? []); $cnt[$s['slug']] = count($g['live']); $live = array_merge($live, $g['live']); $soon = array_merge($soon, $g['soon']); }
  usort($live, function($a, $b) { return strcmp($b['cdate'], $a['cdate']); }); usort($soon, function($a, $b) { return strcmp($a['cdate'], $b['cdate']); });
  list($ftitle, $fsub, $feat) = mt_featured();   /* ortak-veri.php: sitedeki fonksiyonun kendisi */

  $fi = []; foreach (mk_enrich($feat) as $r) { $x = mk_item($r); $x['note'] = $r['_note'] ?? null; $fi[] = $x; }
  $g = cmp_groups(1);
  /* KOPYA: tema-modern-ana.php kategori kısayolları (aynı sıra ve adlar) */
  $short = [];
  foreach (['gida'=>'Gıda', 'temizlik'=>'Temizlik', 'kisisel-bakim'=>'Kişisel bakım', 'ev-yasam'=>'Ev & yaşam', 'elektronik'=>'Elektronik', 'anne-bebek'=>'Anne & bebek', 'oyuncak'=>'Oyuncak', 'mutfak'=>'Mutfak'] as $sl => $n) $short[] = ['slug' => $sl, 'name' => $n];

  $src = trim((string)setting('src_run'));
  mk_ok([
    'data_updated_at' => $src !== '' ? $src : null,
    'markets' => array_map('mk_market', $stores),
    'featured' => ['title' => $ftitle, 'subtitle' => $fsub, 'items' => $fi],
    'live_catalogs' => array_map('mk_catalog', array_slice($live, 0, 6)),
    'soon_catalogs' => array_map('mk_catalog', array_slice($soon, 0, 4)),
    'top_compare' => $g ? mk_group($g[0]) : null,
    'category_shortcuts' => $short,
  ]);
}
