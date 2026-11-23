<?php
/* MARKETLER.ORG - ORTAK VERÄ° FONKSÄ°YONLARI
   Hem sitenin modern ana sayfasÄ± (tema-modern-ana.php) hem de mobil API (api/v1) tarafÄ±ndan kullanÄ±lÄ±r.
   Bu dosya ÃIKTI ÃRETMEZ ve veritabanÄ±na YAZMAZ. AÅaÄÄ±daki blok tema-modern-ana.php iÃ§inden karakteri karakterine aynÄ± taÅÄ±ndÄ±.
   inc.php Ã¶nceden yÃ¼klenmiÅ olmalÄ±dÄ±r. */
require_once __DIR__.'/asistan-motor.php'; require_once __DIR__.'/eslestirme.php';

/* Öne çıkan fırsatlar (kullanıcı kararı):
   - indirimli (old_price>price) ürün >=4  -> gerçek indirim oranına göre ilk 4, başlık "Öne çıkan fırsatlar"
   - 1..3 indirimli                        -> indirimliler + kalan yerler EMSAL medyanına göre uygun ürünlerle
   - 0 indirimli                           -> başlık "Uygun Fiyatlı Ürünler", yalnızca emsale göre uygun ürünler
   Emsal: aynı ürün türü + aynı ölçü sınıfı (kg veya L); en az 3 gerçek emsal şart; yüzde yalnızca bu koşulda gösterilir.
   Seçim: önce her marketin en iyi adayı, sonra genel skor; market başına en çok 2, emsal grubu başına en çok 1 ürün.
   Yüzde = (emsal medyanı - ürün birim fiyatı) / emsal medyanı; kendisi ve aynı ürünün kopyaları medyana girmez.
   Skoru olumlu olmayan ürün (medyanın en az %5 altında değil) sırf market dengesi için öne çıkarılmaz.
   Emsalsiz ürünler yalnızca son çare olarak (bölüm boş kalmasın diye) ve yüzde gösterilmeden eklenir. */
const MT_MIN_GAIN = 0.05;
function mt_featured() {
  $live = ' AND c.cdate<=? AND '.CEND.'>=?'; $t = today(); $cf = sys_get_temp_dir().'/mk_feat_v2_'.md5(__DIR__).'.json';
  if (empty($_GET['tema']) && is_file($cf) && time() - (int)@filemtime($cf) < 600) { $c = json_decode((string)@file_get_contents($cf), true); if (is_array($c) && ($c['d'] ?? '') === $t) return [$c['t'], $c['s'], $c['r']]; }
  $disc = q(PSEL.$live.' AND i.old_price>i.price ORDER BY (1-i.price/i.old_price) DESC,i.id DESC LIMIT 12', [$t, $t])->fetchAll();
  $out = []; $seen = [];
  foreach ($disc as $r) { $k = as_fold($r['name']).'|'.$r['ss']; if (isset($seen[$k])) continue; $seen[$k] = 1; $out[] = $r; if (count($out) >= 4) break; }
  $nd = count($out); $title = 'Öne çıkan fırsatlar'; $sub = 'Güncel kataloglardan gerçek indirimli ürünler';
  if ($nd < 4) {
    $pool = q(PSEL.$live.' LIMIT 6000', [$t, $t])->fetchAll(); $scored = mk_peer_scores(mk_peer_groups($pool), 3);
    $perM = []; $perG = []; foreach ($out as $r) $perM[$r['ss']] = ($perM[$r['ss']] ?? 0) + 1;
    $cand = array_values(array_filter($scored, function ($r) use ($seen) { return $r['_s'] !== null && $r['_s'] >= MT_MIN_GAIN && !isset($seen[$r['_dk']]); }));
    usort($cand, function ($a, $b) { return $b['_s'] <=> $a['_s']; });
    $take = function ($r) use (&$out, &$perM, &$perG, &$seen) { $r['_note'] = ['%'.round($r['_s'] * 100).' daha uygun', $r['_n'].' emsal ürünün medyan birim fiyatına göre']; $out[] = $r; $perM[$r['ss']] = ($perM[$r['ss']] ?? 0) + 1; $perG[$r['_g']] = 1; $seen[$r['_dk']] = 1; };
    $ok = function ($r) use (&$perM, &$perG, &$seen) { return ($perM[$r['ss']] ?? 0) < 2 && !isset($perG[$r['_g']]) && !isset($seen[$r['_dk']]); };
    $done = [];   /* A: her marketin en iyi adayı (adaylar skor sırasında olduğundan ilk uygun olan) */
    foreach ($cand as $r) { if (count($out) >= 4) break; if (isset($done[$r['ss']]) || !$ok($r)) continue; $done[$r['ss']] = 1; $take($r); }
    foreach ($cand as $r) { if (count($out) >= 4) break; if ($ok($r)) $take($r); }   /* B: genel skor */
    if (count($out) < 4) {   /* C: son çare — emsalsiz/yetersiz emsalli ürünler, yüzde yok; kg ve L ayrı, birim fiyata göre */
      $by = ['kg'=>[], 'L'=>[]]; $dd = [];
      foreach ($pool as $r) { $u = as_uprice($r['name'], (float)$r['price'], $r['cn']); if (!$u) continue; $dk = as_fold($r['name']).'|'.$r['ss']; if (isset($seen[$dk]) || isset($dd[$dk])) continue; $dd[$dk] = 1;
        $r['_u'] = $u['v']; $r['_k'] = $u['k']; $r['_dk'] = $dk; $r['_g'] = 'x|'.$dk; $by[$u['k']][] = $r; } foreach ($by as &$l) usort($l, function ($a, $b) { return $a['_u'] <=> $b['_u']; }); unset($l);
      foreach ($by as $k => $l) { $md = mk_median(array_column($l, '_u')); $by[$k] = array_values(array_filter($l, function ($r) use ($md) { return $r['_u'] <= $md; })); }   /* pahalı ürün yalnızca yer doldurmak için eklenmez: sınıf medyanının üstü alınmaz */
      foreach ($by as &$l) usort($l, function ($a, $b) { return $a['_u'] <=> $b['_u']; }); unset($l);
      $ix = ['kg'=>0, 'L'=>0]; $guard = 0;
      while (count($out) < 4 && $guard++ < 600) { $tk = false;
        foreach (['kg', 'L'] as $k) { if (count($out) >= 4) break; while (isset($by[$k][$ix[$k]])) { $r = $by[$k][$ix[$k]++]; if (!$ok($r)) continue; $r['_note'] = 'Birim fiyatı (aynı ölçüdeki ürünler arasında düşük)'; $out[] = $r; $perM[$r['ss']] = ($perM[$r['ss']] ?? 0) + 1; $perG[$r['_g']] = 1; $seen[$r['_dk']] = 1; $tk = true; break; } }
        if (!$tk) break; } }
    if ($nd === 0) { $title = 'Uygun Fiyatlı Ürünler'; $sub = 'Aynı türdeki ürünlerin birim fiyat medyanına göre'; }
    else { $sub = 'İndirimli ürünler ve emsallerine göre uygun fiyatlı ürünler'; }
  }
  @file_put_contents($cf, json_encode(['d'=>$t, 't'=>$title, 's'=>$sub, 'r'=>$out], JSON_UNESCAPED_UNICODE));
  return [$title, $sub, $out];
}

