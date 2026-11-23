<?php
/* GET /api/v1/products — ara.php ile aynı filtre/sıralama/sayfalama (liste modu)
   veya ?fav=storeId:hash,... ile favorilerin güncel karşılığı (favori modu; yalnızca birebir market+hash eşleşmesi, tahmin yok). */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_endpoint($param) {
  if (isset($_GET['fav'])) mk_products_fav();
  $m = mk_in_pat('market', '~^[a-z0-9]{1,30}$~'); $k = mk_in_pat('category', '~^[a-z0-9-]{1,60}$~');
  $qs = trim(mk_in_str('q', 120));
  $mnRaw = mk_in_str('min', 20); $mxRaw = mk_in_str('max', 20);
  $d = mk_in_pat('date', '~^\d{4}-\d{2}-\d{2}$~', 10);
  $st = mk_in_enum('status', ['aktif', 'yaklasan', 'dolmus', 'hepsi'], 'aktif');
  $o = mk_in_enum('sort', ['new', 'asc', 'desc'], 'new'); $o = $o === 'new' ? '' : $o;
  list($page, $lim, $off) = mk_in_page(50, 24); $t = today();
  /* KOPYA: ara.php satır 4-13 (WHERE/ORDER kurgusu; $lim ve $off sayfadan gelir) */
  $mn = pnum($mnRaw); $mx = pnum($mxRaw);
  $w = ''; $a = [];
  if ($m) { $w .= ' AND s.slug=?'; $a[] = $m; } if ($k) { $w .= ' AND k.slug=?'; $a[] = $k; }
  foreach (preg_split('/\s+/', $qs, -1, PREG_SPLIT_NO_EMPTY) as $wd) { $w .= ' AND i.name LIKE ?'; $a[] = '%'.$wd.'%'; }
  if ($mn > 0) { $w .= ' AND i.price>=?'; $a[] = $mn; } if ($mx > 0) { $w .= ' AND i.price<=?'; $a[] = $mx; }
  if ($d) { $w .= ' AND c.cdate<=? AND '.CEND.'>=?'; array_push($a, $d, $d); }      /* seçilen tarihte geçerli kampanyalar */
  elseif ($st === 'aktif') { $w .= ' AND c.cdate<=? AND '.CEND.'>=?'; array_push($a, $t, $t); }
  elseif ($st === 'yaklasan') { $w .= ' AND c.cdate>?'; $a[] = $t; }
  elseif ($st === 'dolmus') { $w .= ' AND '.CEND.'<?'; $a[] = $t; }
  $ord = ['asc'=>'i.price ASC', 'desc'=>'i.price DESC'][$o] ?? 'c.cdate DESC,i.name ASC';
  $total = (int)q('SELECT COUNT(*) FROM items i JOIN catalogs c ON c.id=i.catalog_id JOIN stores s ON s.id=c.store_id JOIN categories k ON k.id=i.cat_id WHERE c.hidden=0'.$w, $a)->fetchColumn();
  $rows = q(PSEL.$w." ORDER BY $ord LIMIT ".(int)$lim.' OFFSET '.(int)$off, $a)->fetchAll();
  mk_ok(mk_items($rows), ['page' => $page, 'limit' => $lim, 'total' => $total]);
}

function mk_products_fav() {
  $raw = $_GET['fav']; if (!is_string($raw) || strlen($raw) > 50 * 45) mk_bad('fav');
  $keys = array_values(array_unique(array_filter(explode(',', $raw), 'strlen')));
  if (!$keys || count($keys) > 50) mk_bad('fav');
  $cond = []; $a = [];
  foreach ($keys as $x) { if (!preg_match('~^([1-9][0-9]{0,9}):([0-9a-f]{32})$~', $x, $mm)) mk_bad('fav'); $cond[] = '(c.store_id=? AND i.hash=?)'; $a[] = (int)$mm[1]; $a[] = $mm[2]; }
  $rows = mk_enrich(q(PSEL.' AND ('.implode(' OR ', $cond).')', $a)->fetchAll());
  $rank = ['live' => 0, 'soon' => 1, 'past' => 2]; $best = [];
  foreach ($rows as $r) {   /* öncelik: aktif > yaklaşan > süresi dolmuş; eşitlikte en yeni başlangıç, sonra en büyük id */
    $fk = (int)$r['sid'].':'.$r['hash']; $r['_rk'] = $rank[cstatus($r)];
    $b = $best[$fk] ?? null;
    if (!$b || $r['_rk'] < $b['_rk'] || ($r['_rk'] === $b['_rk'] && ($r['cdate'] > $b['cdate'] || ($r['cdate'] === $b['cdate'] && (int)$r['id'] > (int)$b['id'])))) $best[$fk] = $r;
  }
  $found = []; $missing = [];
  foreach ($keys as $fk) { if (isset($best[$fk])) $found[] = ['fav_key' => $fk, 'item' => mk_item($best[$fk])]; else $missing[] = $fk; }
  mk_ok(['found' => $found, 'missing' => $missing]);
}
