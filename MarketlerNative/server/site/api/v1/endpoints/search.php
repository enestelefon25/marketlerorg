<?php
/* GET /api/v1/search?q=&all=&page=&limit= — asistan.php?q=... sayfasıyla aynı akıllı arama. */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_endpoint($param) {
  $qs = trim(mk_in_str('q', 120)); if ($qs === '') mk_bad('q');
  $all = mk_in_bool('all'); list($page, $lim, $off) = mk_in_page(50, 24);
  $stores = q('SELECT * FROM stores')->fetchAll(); $cats = q('SELECT slug,name FROM categories')->fetchAll();
  list($res, $note) = mk_as_run_search($qs, $all, $stores, $cats);
  /* KOPYA: asistan.php satır 56-57 */
  $in = $res['in'] ?? null; $list = $res ? ($res['full'] ?: $res['part']) : []; $isPart = $res && !$res['full'] && $res['part']; $total = count($list); $pg = array_slice($list, $off, $lim);
  $hmap = $list ? as_hist(q(PSEL)->fetchAll()) : null;
  list($sn, $chips) = mk_as_chips($in, $stores);
  $items = []; foreach (mk_enrich($pg) as $r) { $x = mk_item($r); $x['history_text'] = mk_as_hline($r, $hmap); $x['unit_text'] = mk_as_uline($r); $items[] = $x; }
  mk_ok(['understood' => $in ? ['market' => $in['market'] ?: null, 'category' => $in['cat'] !== '' ? $in['cat'] : null, 'terms' => $in['terms'], 'qty' => $in['qty'],
      'min' => $in['min'] > 0 ? $in['min'] : null, 'max' => $in['max'] > 0 ? $in['max'] : null, 'sort' => $in['sort'] !== '' ? $in['sort'] : null, 'discounted' => (bool)$in['disc']] : null,
    'chips' => $chips, 'match' => $res && $res['full'] ? 'full' : ($isPart ? 'partial' : 'none'), 'items' => $items,
    'by_unit' => !empty($res['byunit']) ? $res['byunit'] : null, 'note' => $note !== '' ? $note : null,
    'did_you_mean' => ($res['fixq'] ?? '') !== '' ? $res['fixq'] : null, 'similar_words' => $res['fix'] ?? []],
    ['page' => $page, 'limit' => $lim, 'total' => $total], 'no-store');
}
