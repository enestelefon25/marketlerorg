<?php
require __DIR__.'/inc.php';
$m = preg_replace('/[^a-z0-9]/', '', $_GET['m'] ?? ''); $k = preg_replace('/[^a-z0-9-]/', '', $_GET['k'] ?? ''); $qs = trim($_GET['q'] ?? '');
$mn = pnum($_GET['min'] ?? ''); $mx = pnum($_GET['max'] ?? ''); $d = preg_match('/^\d{4}-\d{2}-\d{2}$/', $_GET['d'] ?? '') ? $_GET['d'] : '';
$st = $_GET['st'] ?? (!empty($_GET['all']) ? 'hepsi' : 'aktif'); if (!in_array($st, ['aktif', 'yaklasan', 'dolmus', 'hepsi'])) $st = 'aktif';
$o = $_GET['o'] ?? ''; $off = max(0, (int)($_GET['off'] ?? 0)); $lim = 24; $t = today();
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
$rows = q(PSEL.$w." ORDER BY $ord LIMIT $lim OFFSET $off", $a)->fetchAll();
$html = ''; foreach ($rows as $r) $html .= pcard($r);
if (!$rows && $off === 0) $html = '<div class="empty">'.ico('search').'<h3>Sonuç bulunamadı</h3><p>Aramana uyan ürün yok. Yazımı kontrol et, daha genel bir kelime dene, katalog durumunu değiştir veya filtreleri temizle.</p><p><a class="btn sm" href="'.e(u_pg('ara.php')).'">Filtreleri temizle</a></p></div>';
$more = $off + $lim < $total;
if (isset($_GET['ajax'])) { header('Content-Type: application/json; charset=utf-8'); header('Cache-Control: no-store'); echo json_encode(['html'=>$html, 'total'=>$total, 'more'=>$more, 'count'=>count($rows)], JSON_UNESCAPED_UNICODE); exit; }

$stores = q('SELECT * FROM stores')->fetchAll(); $cats = q('SELECT slug,name FROM categories ORDER BY FIELD(slug,'.KORDER.')')->fetchAll(); $filtered = $qs !== '' || $m || $k || $mn || $mx || $d || $st !== 'aktif';
head('Ürün ara'.($qs !== '' ? ': '.$qs : '').' | '.BRAND, 'BİM, A101 ve ŞOK aktüel ürünlerinde ara, market, kategori ve fiyata göre filtrele. '.SLOGAN, $filtered ? '<meta name="robots" content="noindex,follow">' : '<link rel="canonical" href="'.e(site_url().u_pg('ara.php')).'">', 'ara');
echo '<div class="pgh"><h1>Ürün ara</h1><p>Varsayılan olarak yalnızca şu an aktif kataloglardaki ürünler aranır. Yaklaşan veya süresi dolmuş kataloglar için durum filtresini değiştir. Sonuçlar sayfa yenilenmeden güncellenir. Yazım hatalı veya doğal yazılmış aramalar için <a href="'.e(u_pg('asistan.php')).'"><b>Akıllı Ürün Asistanı</b></a>nı dene.</p></div><div class="srch"><aside class="flt"><details class="fd" open><summary>Filtreler</summary><form class="fb" id="sf" action="'.e(u_pg('ara.php')).'" role="search">';
echo '<label for="q">Ürün adı</label><input type="search" id="q" name="q" value="'.e($qs).'" placeholder="Örn. kahve makinesi"><label>Katalog durumu</label><div class="chips">';
foreach (['aktif'=>'Aktif', 'yaklasan'=>'Yaklaşan', 'dolmus'=>'Süresi dolmuş', 'hepsi'=>'Tümü'] as $v => $l) echo '<label class="chip"><input type="radio" name="st" value="'.$v.'"'.($st === $v ? ' checked' : '').'><span>'.$l.'</span></label>';
echo '</div><label>Market</label><div class="chips"><label class="chip"><input type="radio" name="m" value=""'.(!$m ? ' checked' : '').'><span>Tümü</span></label>';
foreach ($stores as $s) echo '<label class="chip" style="--c:'.e($s['color']).'"><input type="radio" name="m" value="'.e($s['slug']).'"'.($m === $s['slug'] ? ' checked' : '').'><span>'.e($s['name']).'</span></label>';
echo '</div><label for="k">Kategori</label><select id="k" name="k"><option value="">Tüm kategoriler</option>'; foreach ($cats as $c) echo '<option value="'.e($c['slug']).'"'.($k === $c['slug'] ? ' selected' : '').'>'.e($c['name']).'</option>';
echo '</select><label>Fiyat aralığı (TL)</label><div class="row2"><input type="number" name="min" min="0" step="any" inputmode="decimal" placeholder="En az" value="'.($mn ?: '').'" aria-label="En düşük fiyat"><input type="number" name="max" min="0" step="any" inputmode="decimal" placeholder="En çok" value="'.($mx ?: '').'" aria-label="En yüksek fiyat"></div>';
echo '<label for="d">Kampanya tarihi (seçilirse durum filtresi yerine geçer)</label><input type="date" id="d" name="d" value="'.e($d).'"><p style="margin:14px 0 0"><button class="btn" style="width:100%">Ara</button></p></form></details></aside>';
echo '<section aria-live="polite"><div class="rh"><b id="cnt">'.$total.' ürün bulundu</b><select class="sel" id="o" aria-label="Sırala"><option value="">En yeni kataloglar</option><option value="asc"'.($o === 'asc' ? ' selected' : '').'>En düşük fiyat</option><option value="desc"'.($o === 'desc' ? ' selected' : '').'>En yüksek fiyat</option></select></div>';
echo '<div class="g4" id="res">'.$html.'</div><div class="more"><button class="btn ghost" id="more" data-off="'.($off + count($rows)).'"'.($more ? '' : ' hidden').'>Daha fazla ürün göster</button></div></section></div>';
foot();
