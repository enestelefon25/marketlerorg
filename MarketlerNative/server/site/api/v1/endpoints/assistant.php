<?php
/* POST /api/v1/assistant — asistan.php?ajax=1 (sohbet kutusu) ile aynı akış. Sunucu sohbet SAKLAMAZ.
   Gövde (JSON, en çok 2 KB): {"message": "...", "last_query": "...", "include_expired": false} */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_endpoint($param) {
  $raw = file_get_contents('php://input', false, null, 0, 2049);
  if ($raw === false || strlen($raw) > 2048) mk_fail(413, 'PAYLOAD_TOO_LARGE', 'İstek çok büyük.');
  $b = json_decode($raw, true); if (!is_array($b)) mk_bad('gövde');
  $msg = $b['message'] ?? null; $lqIn = $b['last_query'] ?? ''; $allIn = $b['include_expired'] ?? false;
  if (!is_string($msg) || !mb_check_encoding($msg, 'UTF-8') || trim($msg) === '' || mb_strlen($msg) > 120) mk_bad('message');
  if (!is_string($lqIn) || !mb_check_encoding($lqIn, 'UTF-8') || mb_strlen($lqIn) > 120) mk_bad('last_query');
  if (!is_bool($allIn)) mk_bad('include_expired');

  /* KOPYA: asistan.php satır 5, 30-43 (ajax sohbet katmanı) */
  $qs = trim(mb_substr($msg, 0, 120)); $all = $allIn;
  $stores = q('SELECT * FROM stores')->fetchAll(); $cats = q('SELECT slug,name FROM categories')->fetchAll();
  $say = ''; $qr = []; $links = []; $lq = trim(mb_substr($lqIn, 0, 120));
  $ch = as_chat($qs);
  if ($ch) { $say = $ch['say']; $qr = $ch['qr']; $qs = $ch['rest'];
    if (($ch['act'] ?? '') !== '') {
      $g = by_status(q(CSEL)->fetchAll()); $ls = $ch['act'] === 'cat_soon' ? $g['soon'] : $g['live'];
      if ($ls) { $say = ($ch['act'] === 'cat_soon' ? 'Yaklaşan kataloglar:' : 'Şu an geçerli kataloglar:'); foreach (array_slice($ls, 0, 8) as $c) $links[] = ['title' => $c['title'], 'date_text' => range_label($c['cdate'], $c['cend']), 'catalog_slug' => $c['slug'], 'web_url' => mk_abs(u_kat($c['slug']))]; }
      else $say = $ch['act'] === 'cat_soon' ? 'Şu an yaklaşan bir katalog görünmüyor. Yeni kataloglar eklendikçe burada listelenir.' : 'Şu an aktif katalog görünmüyor.';
    }
  }
  if ($qs !== '' && $lq !== '') { $pp = as_parse($qs, $stores, $cats); if (!$pp['terms'] && $pp['cat'] === '' && ($pp['market'] || $pp['qty'] || $pp['min'] || $pp['max'] || $pp['sort'] || $pp['disc'])) { $qs = $lq.' '.$qs; $say = $say ?: as_pick(['Tamam, aynı ürün için bakıyorum…', 'Olur, aramayı buna göre daraltıyorum…']); } }
  if ($qs === '') mk_ok(['say' => $say, 'chat_only' => true, 'effective_query' => '', 'chips' => [], 'items' => [], 'links' => $links, 'quick_replies' => $qr, 'note' => '', 'partial' => false, 'total' => 0, 'did_you_mean' => '', 'similar_words' => []], [], 'no-store');

  list($res, $note) = mk_as_run_search($qs, $all, $stores, $cats);
  /* KOPYA: asistan.php satır 56-64 */
  $in = $res['in'] ?? null; $list = $res ? ($res['full'] ?: $res['part']) : []; $isPart = $res && !$res['full'] && $res['part']; $total = count($list);
  $hmap = $list ? as_hist(q(PSEL)->fetchAll()) : null;
  list($sn, $chips) = mk_as_chips($in, $stores);
  $it = []; $top = mk_enrich(array_slice($list, 0, 5));
  foreach ($top as $r) { $x = mk_item($r); $x['unit_text'] = mk_as_uline($r); $x['history_text'] = mk_as_hline($r, $hmap); $x['status_note'] = cstatus($r) === 'live' ? '' : STL[cstatus($r)]; $it[] = $x; }
  if ($it) $say = trim(($say && !$isPart ? $say.' ' : '').(!empty($res['byunit']) && !$isPart ? $total.' ürün buldum; birim fiyata (TL/'.$res['byunit'].') göre sıraladım:' : as_say($total, $isPart, $in, $sn)));
  mk_ok(['say' => $say, 'chat_only' => false, 'effective_query' => $qs, 'chips' => $chips, 'items' => $it, 'links' => $links,
    'quick_replies' => $it ? ['Daha ucuzu', 'A101\'de', 'BİM\'de', 'ŞOK\'ta'] : ['Neler yapabilirsin?', 'İndirimli ürünler'],
    'note' => $note, 'partial' => (bool)$isPart, 'total' => $total, 'did_you_mean' => $res['fixq'] ?? '', 'similar_words' => $res['fix'] ?? []], [], 'no-store');
}
