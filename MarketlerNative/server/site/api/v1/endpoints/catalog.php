<?php
/* GET /api/v1/catalogs/{slug} — katalog.php ile aynı sorgular. Yalnızca yayındaki (hidden=0) kataloglar; yönetici önizlemesi yok. */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_endpoint($slug) {
  $c = q(CSELB.' WHERE c.slug=? AND (c.hidden=0 OR ?=1)', [$slug, 0])->fetch();   /* katalog.php sorgusu, yönetici=0 */
  if (!$c) mk_404('Katalog');
  $items = q('SELECT i.id,i.name,i.price,i.old_price,k.name cn,k.slug ks FROM items i JOIN categories k ON k.id=i.cat_id WHERE i.catalog_id=? ORDER BY FIELD(k.slug,'.KORDER.'),i.name', [$c['id']])->fetchAll();   /* katalog.php sorgusu */
  $prices = array_column($items, 'price');
  /* katalog.php pcard() çağrısındaki gibi katalog bilgisi ürün satırına eklenir */
  $rows = []; foreach ($items as $i) $rows[] = $i + ['slug' => $c['slug'], 'cdate' => $c['cdate'], 'cend' => $c['cend'], 'sn' => $c['sn'], 'color' => $c['color'], 'ss' => $c['ss']];
  $groups = []; foreach (mk_enrich($rows) as $r) { $k = $r['ks']; if (!isset($groups[$k])) $groups[$k] = ['category' => ['slug' => $k, 'name' => $r['cn']], 'items' => []]; $groups[$k]['items'][] = mk_item($r); }
  $pages = []; $skip = 0; $n = 0;
  foreach (json_decode($c['images'] ?? '[]', true) ?: [] as $u) {
    if (!is_string($u) || mk_local_img($u) === null) { $skip++; continue; }   /* yalnızca sunucuda gerçekten bulunan yerel görseller */
    $pages[] = ['index' => ++$n, 'url' => mk_img_url($u), 'thumb_url' => mk_img_url(img_small($u, 't'))];
  }
  $o = q(CSEL.' AND c.store_id=? AND c.id<>? ORDER BY c.cdate DESC LIMIT 4', [$c['store_id'], $c['id']])->fetchAll();   /* katalog.php "Diğer kataloglar" */
  mk_ok(['catalog' => mk_catalog($c),
    'price_min' => $prices ? mk_money(min($prices)) : null, 'price_min_text' => $prices ? tl(min($prices)) : null,
    'price_max' => $prices ? mk_money(max($prices)) : null, 'price_max_text' => $prices ? tl(max($prices)) : null,
    'pages' => $pages, 'pages_skipped' => $skip, 'groups' => array_values($groups), 'other_catalogs' => array_map('mk_catalog', $o)], [], 'public, max-age=900');
}
