<?php
/* GET /api/v1/catalogs?market=&status=&page=&limit= — kataloglar.php ile aynı sorgu ve gruplama. */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_endpoint($param) {
  $m = mk_in_pat('market', '~^[a-z0-9]{1,30}$~');
  $st = mk_in_enum('status', ['live', 'soon', 'past', 'all'], 'all');
  list($page, $lim, $off) = mk_in_page(50, 20);
  $all = q(CSEL.($m ? ' AND s.slug=?' : '').' ORDER BY c.cdate DESC,c.id DESC LIMIT 300', $m ? [$m] : [])->fetchAll();   /* kataloglar.php sorgusu */
  $g = by_status($all);
  if ($st === 'all') {
    mk_ok(['live' => array_map('mk_catalog', $g['live']), 'soon' => array_map('mk_catalog', $g['soon']), 'past' => array_map('mk_catalog', array_slice($g['past'], $off, $lim))],
      ['page' => $page, 'limit' => $lim, 'total' => count($g['past']), 'total_live' => count($g['live']), 'total_soon' => count($g['soon'])], 'public, max-age=900');
  }
  mk_ok(array_map('mk_catalog', array_slice($g[$st], $off, $lim)), ['page' => $page, 'limit' => $lim, 'total' => count($g[$st])], 'public, max-age=900');
}
