<?php
/* Veritabanı satırlarını belgelenen JSON nesnelerine (Market, CatalogSummary, Item, CompareGroup) çevirir.
   Hesaplamaların hepsi sitedeki fonksiyonlarla aynıdır (cstatus, range_label, tl, as_uprice, item_img, img_small). Yazma yoktur. */
if (!defined('MK_API')) { http_response_code(404); exit; }

/* --- yardımcılar --- */
function mk_money($v) { return $v === null || $v === '' ? null : number_format((float)$v, 2, '.', ''); }
/* site içi yol ('/katalog/x', '/img/..') -> mutlak adres */
function mk_abs($path) { return preg_match('~^https?://~', $path) ? $path : site_url().$path; }
/* Yerel ve gerçekten var olan görsel mi? ('img/...' veya '/img/...?v=..') -> site köküne göre göreli yol ya da null */
function mk_local_img($u) {
  if (!is_string($u) || $u === '') return null;
  $p = ltrim((string)strtok($u, '?'), '/');
  if (strpos($p, 'img/') !== 0 || strpos($p, '..') !== false || !is_file(MK_SITE.'/'.$p)) return null;
  return $p;
}
function mk_img_url($u) {   /* yerel dosya ise mutlak URL (sorgu kısmı korunur), değilse null */
  $p = mk_local_img($u); if ($p === null) return null;
  $q = strpos($u, '?') !== false ? substr($u, strpos($u, '?')) : '';
  return mk_abs(bpath().'/'.$p.$q);
}

/* --- Market --- */
function mk_store_counts() {   /* ana sayfadaki $cnt hesabının aynısı: CSEL ... LIMIT 400 + by_status */
  static $c = null; if ($c !== null) return $c;
  $per = []; foreach (q(CSEL.' ORDER BY c.cdate DESC,c.id DESC LIMIT 400')->fetchAll() as $r) $per[$r['ss']][] = $r;
  $c = []; foreach ($per as $ss => $l) { $g = by_status($l); $c[$ss] = ['live' => count($g['live']), 'soon' => count($g['soon'])]; }
  return $c;
}
function mk_market($s) {
  $cnt = mk_store_counts()[$s['slug']] ?? ['live' => 0, 'soon' => 0];
  return ['id' => (int)$s['id'], 'slug' => $s['slug'], 'name' => $s['name'], 'color' => $s['color'],
    'logo_url' => mk_img_url((string)setting('logo_'.$s['slug'])), 'live_count' => $cnt['live'], 'soon_count' => $cnt['soon']];
}
function mk_market_ref($id, $slug, $name, $color) { return ['id' => (int)$id, 'slug' => $slug, 'name' => $name, 'color' => $color]; }

/* --- CatalogSummary (CSEL satırı) --- */
function mk_catalog($c) {
  $s = cstatus($c);
  $im = json_decode($c['images'] ?? '[]', true); $cover = null;
  if (is_array($im) && $im && is_string($im[0]) && mk_local_img($im[0]) !== null)
    $cover = ['url' => mk_img_url(img_small($im[0], 'c')), 'full_url' => mk_img_url($im[0])];
  return ['id' => (int)$c['id'], 'slug' => $c['slug'], 'title' => $c['title'], 'label' => $c['label'],
    'market' => mk_market_ref($c['store_id'], $c['ss'], $c['sn'], $c['color']),
    'start_date' => $c['cdate'], 'end_date' => $c['cend'], 'status' => $s, 'status_text' => STL[$s],
    'date_text' => $s === 'soon' ? 'Başlangıç: '.dshort($c['cdate']).' '.date('Y', strtotime($c['cdate'])) : range_label($c['cdate'], $c['cend']),   /* ccard() kuralı */
    'item_count' => (int)$c['n'], 'cover' => $cover, 'web_url' => mk_abs(u_kat($c['slug']))];
}

/* --- Item ---
   PSEL satırında market id, hash, kategori slug'ı, katalog id/başlığı yok; PSEL değiştirilmeden bu alanlar id ile toplu okunur. */
function mk_enrich($rows) {
  $ids = []; foreach ($rows as $r) if (!empty($r['id'])) $ids[(int)$r['id']] = 1;
  if (!$ids) return $rows; $x = [];
  foreach (array_chunk(array_keys($ids), 500) as $ch) {
    $sql = 'SELECT i.id,i.hash,c.store_id sid,c.id cid,c.title ct,k.slug ks FROM items i JOIN catalogs c ON c.id=i.catalog_id JOIN categories k ON k.id=i.cat_id WHERE i.id IN ('.implode(',', array_fill(0, count($ch), '?')).')';
    foreach (q($sql, $ch)->fetchAll() as $r) $x[(int)$r['id']] = $r;
  }
  foreach ($rows as &$r) if (isset($x[(int)$r['id']])) $r += $x[(int)$r['id']]; unset($r);
  return $rows;
}
function mk_uline($u) { return $u ? '≈ '.number_format($u['v'], 2, ',', '.').' TL/'.$u['k'] : ''; }   /* asistan.php as_uline() biçimi */
function mk_item($r) {
  $s = cstatus($r);
  $disc = ($r['old_price'] && $r['old_price'] > $r['price']);   /* pcard() kuralı: indirim yalnızca old_price > price ise */
  $u = as_uprice($r['name'], (float)$r['price'], $r['cn']);
  $img = item_img($r['id']);
  return ['id' => (int)$r['id'], 'fav_key' => ((int)($r['sid'] ?? 0)).':'.($r['hash'] ?? ''), 'hash' => $r['hash'] ?? null,
    'name' => $r['name'], 'category' => ['slug' => $r['ks'] ?? null, 'name' => $r['cn']],
    'market' => mk_market_ref($r['sid'] ?? 0, $r['ss'], $r['sn'], $r['color']),
    'price' => mk_money($r['price']), 'price_text' => tl($r['price']),
    'old_price' => $disc ? mk_money($r['old_price']) : null, 'old_price_text' => $disc ? tl($r['old_price']) : null,
    'discount_pct' => $disc ? (int)round(100 - $r['price'] / $r['old_price'] * 100) : null,
    'unit_price' => $u ? ['value' => mk_money($u['v']), 'unit' => $u['k'], 'text' => mk_uline($u)] : null,
    'image' => $img ? ['url' => mk_abs($img)] : null,
    'catalog' => ['id' => isset($r['cid']) ? (int)$r['cid'] : null, 'slug' => $r['slug'], 'title' => $r['ct'] ?? null,
      'start_date' => $r['cdate'], 'end_date' => $r['cend'], 'status' => $s, 'status_text' => STL[$s]],
    'campaign_text' => range_label($r['cdate'], $r['cend']), 'web_url' => mk_abs(u_kat($r['slug']).'#viewer')];
}
function mk_items($rows) { return array_map('mk_item', mk_enrich($rows)); }

/* --- CompareGroup (cmp_groups() grubu; satırlar fiyata göre artan) --- */
function mk_group($g) {
  $g = mk_enrich($g); $min = min(array_column($g, 'price')); $max = max(array_column($g, 'price'));
  $rows = []; $man = false; $ids = [];
  foreach ($g as $r) { $it = mk_item($r); $it['is_best'] = ($r['price'] == $min); $rows[] = $it; if (!empty($r['manual'])) $man = true; $ids[] = (int)$r['id']; }   /* sitede: $r['price'] == $min */
  $diff = $max - $min;
  return ['group_id' => (string)min($ids), 'title' => $g[0]['name'], 'is_manual' => $man, 'rows' => $rows,
    'min_price' => mk_money($min), 'max_price' => mk_money($max), 'diff' => mk_money($diff), 'diff_text' => tl($diff),
    'diff_pct' => $max > $min ? (int)round($diff / $max * 100) : 0,
    'saving_text' => $max > $min ? 'En ucuz markette '.tl($diff).' (%'.round($diff / $max * 100).') daha az.' : null];   /* fiyat-karsilastirma.php metni */
}
