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
  /* KOPYA: ara.php satır 7-16 (FAZ 1 düzeltmesi dahil: eşitlikte i.id) (WHERE/ORDER kurgusu; $lim ve $off sayfadan gelir) */
  $mn = pnum($mnRaw); $mx = pnum($mxRaw);
  $w = ''; $a = [];
  if ($m) { $w .= ' AND s.slug=?'; $a[] = $m; } if ($k) { $w .= ' AND k.slug=?'; $a[] = $k; }
  foreach (preg_split('/\s+/', $qs, -1, PREG_SPLIT_NO_EMPTY) as $wd) { $w .= ' AND i.name LIKE ?'; $a[] = '%'.$wd.'%'; }
  if ($mn > 0) { $w .= ' AND i.price>=?'; $a[] = $mn; } if ($mx > 0) { $w .= ' AND i.price<=?'; $a[] = $mx; }
  if ($d) { $w .= ' AND c.cdate<=? AND '.CEND.'>=?'; array_push($a, $d, $d); }      /* seçilen tarihte geçerli kampanyalar */
  elseif ($st === 'aktif') { $w .= ' AND c.cdate<=? AND '.CEND.'>=?'; array_push($a, $t, $t); }
  elseif ($st === 'yaklasan') { $w .= ' AND c.cdate>?'; $a[] = $t; }
  elseif ($st === 'dolmus') { $w .= ' AND '.CEND.'<?'; $a[] = $t; }
  $ord = (['asc'=>'i.price ASC', 'desc'=>'i.price DESC'][$o] ?? 'c.cdate DESC,i.name ASC').',i.id ASC';   /* eşitlikte kararlı sıra: sayfalamada tekrar/kayıp olmaz */
  $total = (int)q('SELECT COUNT(*) FROM items i JOIN catalogs c ON c.id=i.catalog_id JOIN stores s ON s.id=c.store_id JOIN categories k ON k.id=i.cat_id WHERE c.hidden=0'.$w, $a)->fetchColumn();
  $rows = q(PSEL.$w." ORDER BY $ord LIMIT ".(int)$lim.' OFFSET '.(int)$off, $a)->fetchAll();
  mk_ok(mk_items($rows), ['page' => $page, 'limit' => $lim, 'total' => $total]);
}

function mk_products_fav() {
  /* Favori: ?fav=itemId:storeId:hash,... (en çok 50).
     - Favorinin KENDİSİ her zaman item_id ile bulunur (kalıcı kimlik değil ama kaydedilen satırın kendisi).
     - hash YALNIZCA ARAMA İPUCUDUR: aynı market + aynı hash taşıyan başka kalemler "aday" olarak döner, confirmed=false.
       Aynı ürün olduğu kesin doğrulanamadığı için favori OTOMATİK DEĞİŞTİRİLMEZ; karar kullanıcıya bırakılır.
     - Kalem bulunamazsa uygulama, kaydettiği ilk adı gösterir; API açık bir durum ve mesaj döner. */
  $raw = $_GET['fav']; if (!is_string($raw) || strlen($raw) > 50 * 56) mk_bad('fav');
  $keys = array_values(array_unique(array_filter(explode(',', $raw), 'strlen')));
  if (!$keys || count($keys) > 50) mk_bad('fav');
  $fav = []; $ids = []; $cond = []; $a = [];
  foreach ($keys as $x) {
    if (!preg_match('~^([1-9][0-9]{0,9}):([1-9][0-9]{0,9}):([0-9a-f]{32})$~', $x, $mm)) mk_bad('fav');
    $fav[] = ['key' => $x, 'id' => (int)$mm[1], 'sid' => (int)$mm[2], 'hash' => $mm[3]]; $ids[(int)$mm[1]] = 1;
    $cond[] = '(c.store_id=? AND i.hash=?)'; $a[] = (int)$mm[2]; $a[] = $mm[3];
  }
  $orig = []; foreach (mk_enrich(q(PSEL.' AND i.id IN ('.implode(',', array_fill(0, count($ids), '?')).')', array_keys($ids))->fetchAll()) as $r) $orig[(int)$r['id']] = $r;
  $byKey = []; foreach (mk_enrich(q(PSEL.' AND ('.implode(' OR ', $cond).')', $a)->fetchAll()) as $r) $byKey[(int)$r['sid'].':'.$r['hash']][] = $r;
  $rank = ['live' => 0, 'soon' => 1, 'past' => 2];
  $out = [];
  foreach ($fav as $f) {
    $o = $orig[$f['id']] ?? null; if ($o && (int)$o['sid'] !== $f['sid']) $o = null;   /* kayıtlı market uyuşmuyorsa aynı kalem sayılmaz */
    $cands = array_values(array_filter($byKey[$f['sid'].':'.$f['hash']] ?? [], function ($r) use ($f) { return (int)$r['id'] !== $f['id']; }));
    usort($cands, function ($x, $y) use ($rank) { return [$rank[cstatus($x)], $y['cdate'], (int)$y['id']] <=> [$rank[cstatus($y)], $x['cdate'], (int)$x['id']]; });
    $cands = array_slice($cands, 0, 3);
    $state = $o ? ['live' => 'aktif', 'soon' => 'yaklasan', 'past' => 'suresi_doldu'][cstatus($o)] : 'bulunamadi';
    $msg = ['aktif' => null, 'yaklasan' => 'Bu ürünün kampanyası henüz başlamadı.', 'suresi_doldu' => 'Bu ürünün kampanyası sona erdi.', 'bulunamadi' => 'Bu ürün artık kataloglarda bulunamadı.'][$state];
    if ($state !== 'aktif' && $cands) $msg .= ' Başka bir katalogda aynı adla kayıtlı ürün var; aynı ürün olduğu doğrulanamadığı için favoriniz değiştirilmedi.';
    $out[] = ['fav_key' => $f['key'], 'state' => $state, 'message' => $msg, 'item' => $o ? mk_item($o) : null,
      'name_changed' => $o ? $o['hash'] !== $f['hash'] : null,
      'candidates' => array_map(function ($r) { return ['match' => 'ayni_market_ayni_ad', 'confirmed' => false, 'item' => mk_item($r)]; }, $cands)];
  }
  mk_ok($out);
}
