<?php
/* GET /api/v1/markets/{slug} — index.php?m={slug} ile aynı veri. */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_endpoint($slug) {
  $st = null; foreach (q('SELECT * FROM stores')->fetchAll() as $s) if ($s['slug'] === $slug) $st = $s;
  if (!$st) mk_404('Market');
  $g = by_status(q(CSEL.' AND s.id=? ORDER BY c.cdate DESC,c.id DESC LIMIT 120', [$st['id']])->fetchAll());   /* index.php sorgusu */
  mk_ok(['market' => mk_market($st), 'catalogs' => ['live' => array_map('mk_catalog', $g['live']), 'soon' => array_map('mk_catalog', $g['soon']), 'past' => array_map('mk_catalog', $g['past'])]], [], 'public, max-age=900');
}
