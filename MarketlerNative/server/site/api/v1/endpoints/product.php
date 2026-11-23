<?php
/* GET /api/v1/products/{id} — ürün + fiyat geçmişi (as_histrow) + kesin eşleşme grubu (cmp_groups). */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_endpoint($id) {
  $r = q(PSEL.' AND i.id=?', [(int)$id])->fetch();
  if (!$r) mk_404('Ürün');
  $r = mk_enrich([$r])[0];
  /* Fiyat geçmişi: as_hkey market slug'ını içerdiği için yalnızca aynı marketin satırları sonucu etkiler;
     sitedeki as_hist(q(PSEL)) ile aynı sonucu daha az veriyle üretir. */
  $map = as_hist(q(PSEL.' AND s.slug=?', [$r['ss']])->fetchAll()); $h = as_histrow($r, $map); $hist = null;
  if ($h) $hist = ['prev_price' => $h['prev'] === null ? null : mk_money($h['prev']), 'prev_price_text' => $h['prev'] === null ? null : tl($h['prev']),
    'prev_date' => $h['pd'] !== '' ? $h['pd'] : null, 'change_pct' => (int)$h['pct'], 'is_lowest' => (bool)$h['low'], 'is_highest' => (bool)$h['high'],
    'other_count' => (int)$h['n'], 'text' => mk_as_hline($r, $map)];
  $grp = null; foreach (cmp_groups() as $g) foreach ($g as $x) if ((int)$x['id'] === (int)$r['id']) { $grp = $g; break 2; }
  $c = q(CSEL.' AND c.id=?', [$r['cid']])->fetch();
  mk_ok(['item' => mk_item($r), 'history' => $hist, 'compare' => $grp ? mk_group($grp) : null,
    'compare_reason' => $grp ? null : 'Bu ürün için şu an geçerli kataloglarda kesin eşleşen ürün yok.', 'catalog' => $c ? mk_catalog($c) : null]);
}
