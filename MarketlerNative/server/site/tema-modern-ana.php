<?php
/* MARKETLER.ORG - MODERN TEMA ANA SAYFASI
   Yalnızca index.php içinden, tema "modern" iken çağrılır. Klasik ana sayfa bu dosyayı hiç yüklemez.
   Veritabanına yazmaz; tüm içerik mevcut tablolardan (items, catalogs, stores) okunur.
   Ürün görseli kullanılmaz; sahte indirim/tasarruf iddiası üretilmez.
   Beklenen değişkenler (index.php'den): $stores, $canon. */
require_once __DIR__.'/asistan-motor.php'; require_once __DIR__.'/eslestirme.php';

/* ---- yardımcılar ---- */
function mt_card($r, $note = '') {   /* modern ürün kartı: market logosu, kategori, ad, fiyat, (gerçekse) indirim, birim fiyat, tarih */
  $d = ($r['old_price'] && $r['old_price'] > $r['price']) ? (int)round(100 - $r['price'] / $r['old_price'] * 100) : 0;
  $up = as_uprice($r['name'], (float)$r['price'], $r['cn']);
  $o = '<article class="mc2"><div class="mc2-h">'.store_badge($r['ss'], $r['sn'], $r['color']).'<span class="mc2-k">'.e($r['cn']).'</span>'.($d ? '<span class="mc2-d">-%'.$d.'</span>' : '').'</div>';
  $im = !empty($r['id']) ? item_img($r['id']) : null; if ($im) $o .= '<div class="mc2-i"><img src="'.e($im).'" alt="'.e($r['name']).'" loading="lazy" decoding="async"></div>';
  $o .= '<h3 class="mc2-n">'.e($r['name']).'</h3><div class="mc2-p"><b>'.tl($r['price']).'</b>'.($d ? '<s>'.tl($r['old_price']).'</s>' : '').'</div>';
  if ($up) $o .= '<p class="mc2-u">≈ '.tl($up['v']).' / '.$up['k'].'</p>';
  if (is_array($note)) { foreach ($note as $i => $n) $o .= '<p class="mc2-u">'.($i === 0 ? '<b>'.e($n).'</b>' : e($n)).'</p>'; } elseif ($note) $o .= '<p class="mc2-u">'.e($note).'</p>';
  return $o.'<p class="mc2-t">Kampanya: '.e(range_label($r['cdate'], $r['cend'])).'</p><a class="mc2-a" href="'.e(u_kat($r['slug']).'#viewer').'">Ürünü incele</a></article>';
}

require_once __DIR__.'/ortak-veri.php';   /* mt_featured() ve MT_MIN_GAIN buraya taşındı (içerik değişmedi); API de aynı fonksiyonu kullanır */

/* ---- veri ---- */
$per = []; foreach (q(CSEL.' ORDER BY c.cdate DESC,c.id DESC LIMIT 400')->fetchAll() as $c) $per[$c['ss']][] = $c;
$live = []; $soon = []; $cnt = [];
foreach ($stores as $s) { $g = by_status($per[$s['slug']] ?? []); $cnt[$s['slug']] = count($g['live']); $live = array_merge($live, $g['live']); $soon = array_merge($soon, $g['soon']); }
usort($live, function($a, $b) { return strcmp($b['cdate'], $a['cdate']); }); usort($soon, function($a, $b) { return strcmp($a['cdate'], $b['cdate']); });
list($ftitle, $fsub, $feat) = mt_featured();

/* ---- sayfa ---- */
echo '</main><section class="mh"><div class="wrap mh-in"><h1>Aradığın ürünün en uygun fiyatını bul.</h1><p>Marketlerdeki güncel ürünleri ve fiyatları tek yerde karşılaştır.</p>';
echo '<form class="mh-f" action="'.e(u_pg('ara.php')).'" role="search" data-oneri="'.e(u_pg('asistan.php')).'"><div class="mh-s">'.ico('search').'<input type="search" name="q" autocomplete="off" placeholder="Ürün adı yazın... Örn: Ayçiçek yağı, süt, deterjan" aria-label="Ürün ara"><button class="btn">Ara</button></div></form></div></section><div class="wrap mw">';

echo '<section class="ms" id="marketler"><div class="ms-h"><h2>Marketler</h2></div><div class="mm">';
foreach ($stores as $s) { $n = $cnt[$s['slug']] ?? 0;
  echo '<a class="mm-c" href="'.e(u_mkt($s['slug'])).'" style="--c:'.e($s['color']).'">'.store_badge($s['slug'], $s['name'], $s['color']).'<span class="mm-t"><b>'.e($s['name']).'</b><small>'.($n ? $n.' güncel katalog' : 'Şu an aktif katalog yok').'</small></span><span class="mm-g">Katalogları incele</span></a>'; }
echo '</div></section>';

echo '<section class="ms"><div class="ms-h"><h2>'.e($ftitle).'</h2><small>'.e($fsub).'</small><a class="all" href="'.e(u_pg('ara.php')).'">Tüm ürünler</a></div>';
if ($feat) { echo '<div class="mrow">'; foreach ($feat as $r) echo mt_card($r, $r['_note'] ?? ''); echo '</div>'; }
else echo '<div class="mempty"><b>Şu an listelenecek ürün yok.</b><p>Güncel kataloglarda ürün olduğunda burada gerçek fiyatlarıyla görünür.</p></div>';
echo '</section>';

echo '<section class="ms"><div class="ms-h"><h2>Güncel kataloglar</h2><a class="all" href="'.e(u_pg('kataloglar.php')).'">Tüm kataloglar</a></div>';
if ($live) { echo '<div class="mrow mrow-c">'; foreach (array_slice($live, 0, 6) as $c) echo '<a class="mk2" href="'.e(u_kat($c['slug'])).'">'.cover($c).'<div class="mk2-b"><small>'.e($c['sn']).'</small><h3>'.e($c['title']).'</h3><p>'.e(range_label($c['cdate'], $c['cend'])).'</p><span class="mc2-a">Kataloğu incele</span></div></a>'; echo '</div>'; }
else echo '<div class="mempty"><b>Şu an aktif katalog yok.</b><p>Yeni kataloglar yayınlandığında burada görünür.</p></div>';
echo '</section>';

$g = cmp_groups(1); $g = $g ? $g[0] : null;
echo '<section class="mcp"><div><h2>Aynı ürün, farklı marketler. En uygun fiyatı bul.</h2><p>Aynı anda geçerli kataloglarda bulunan aynı ürünleri yan yana karşılaştır.</p><a class="btn" href="'.e(u_pg('fiyat-karsilastirma.php')).'">Fiyatları karşılaştır</a></div>';
if ($g) { $min = min(array_column($g, 'price')); echo '<div class="mcp-b"><p>'.e($g[0]['name']).'</p>'; foreach ($g as $r) echo '<a class="'.($r['price'] == $min ? 'best' : '').'" href="'.e(u_kat($r['slug'])).'#urunler"><span>'.e($r['sn']).'</span><b>'.tl($r['price']).'</b></a>'; echo '</div>'; }
else echo '<div class="mcp-b"><p>Şu an eşleşen ürün yok.</p><small>Aynı anda geçerli kataloglarda aynı ürün bulunduğunda burada görünür.</small></div>';
echo '</section>';

echo '<section class="ms" id="kategoriler"><div class="ms-h"><h2>Kategoriler</h2></div><div class="mcat">';
foreach (['gida'=>['Gıda', 'cart'], 'temizlik'=>['Temizlik', 'tag'], 'kisisel-bakim'=>['Kişisel bakım', 'tag'], 'ev-yasam'=>['Ev & yaşam', 'home'], 'elektronik'=>['Elektronik', 'grid'], 'anne-bebek'=>['Anne & bebek', 'tag'], 'oyuncak'=>['Oyuncak', 'grid'], 'mutfak'=>['Mutfak', 'cart']] as $sl => $k)
  echo '<a href="'.e(u_pg('ara.php').'?k='.$sl).'">'.ico($k[1]).'<span>'.e($k[0]).'</span></a>';
echo '</div></section>';

if ($soon) { echo '<section class="ms"><div class="ms-h"><h2>Yaklaşan kataloglar</h2><small>Henüz başlamadı</small></div><ul class="msoon">';
  foreach (array_slice($soon, 0, 4) as $c) echo '<li><a href="'.e(u_kat($c['slug'])).'">'.store_badge($c['ss'], $c['sn'], $c['color']).'<span><b>'.e($c['title']).'</b><small>Başlangıç: '.e(dshort($c['cdate'])).' '.date('Y', strtotime($c['cdate'])).'</small></span></a></li>';
  echo '</ul></section>'; }
echo '</div>';
foot(); exit;
