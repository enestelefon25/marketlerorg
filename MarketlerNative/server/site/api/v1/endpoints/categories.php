<?php
/* GET /api/v1/categories — ara.php kategori listesi (KORDER sırası) + aktif kataloglardaki ürün sayısı. */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_endpoint($param) {
  $t = today();
  $cnt = q('SELECT k.slug,COUNT(*) FROM items i JOIN catalogs c ON c.id=i.catalog_id JOIN stores s ON s.id=c.store_id JOIN categories k ON k.id=i.cat_id WHERE c.hidden=0 AND c.cdate<=? AND '.CEND.'>=? GROUP BY k.slug', [$t, $t])->fetchAll(PDO::FETCH_KEY_PAIR);   /* ara.php "aktif" koşulu */
  $o = []; $n = 0;
  foreach (q('SELECT slug,name FROM categories ORDER BY FIELD(slug,'.KORDER.')')->fetchAll() as $c) $o[] = ['slug' => $c['slug'], 'name' => $c['name'], 'order' => ++$n, 'live_item_count' => (int)($cnt[$c['slug']] ?? 0)];
  mk_ok($o, [], 'public, max-age=3600');
}
