<?php
/* KULLANICI KARARI "A": asistan.php ve sayfa.php DEĞİŞTİRİLMEZ. Bu dosyalarda fonksiyon olarak değil sayfa içinde yazılı
   olan mantığın BİREBİR KOPYALARI burada tutulur. Her kopyanın kaynağı satır numarasıyla belirtilmiştir.
   Site bu kısımları değiştirirse burası da güncellenmelidir; tests/ altındaki eşdeğerlik testleri farkı yakalar. */
if (!defined('MK_API')) { http_response_code(404); exit; }

/* KOPYA: asistan.php satır 49-54, function as_hline() (asistan.php require edilemediği için yeniden adlandırıldı) */
function mk_as_hline($r, $map) {
  $h = as_histrow($r, $map); if (!$h) return ''; $o = [];
  if ($h['prev'] !== null) { $o[] = $h['pct'] < 0 ? 'Önceki katalogdan (' . dshort($h['pd']) . ', ' . tl($h['prev']) . ') %' . abs($h['pct']) . ' daha ucuz' : ($h['pct'] > 0 ? 'Önceki katalogdan (' . dshort($h['pd']) . ', ' . tl($h['prev']) . ') %' . $h['pct'] . ' daha pahalı' : 'Önceki katalogla aynı fiyat (' . dshort($h['pd']) . ')'); }
  if ($h['low']) $o[] = 'kayıtlı en düşük fiyat'; elseif ($h['high']) $o[] = 'kayıtlı en yüksek fiyat';
  return $o ? ucfirst(implode(' · ', $o)) : '';
}
/* KOPYA: asistan.php satır 55, function as_uline() */
function mk_as_uline($r) { if (empty($r['_u'])) return ''; return '≈ ' . number_format($r['_u']['v'], 2, ',', '.') . ' TL/' . $r['_u']['k']; }

/* KOPYA: asistan.php satır 44-48 — arama + arşive düşme. Dönüş: [$res, $note] */
function mk_as_run_search($qs, $all, $stores, $cats) {
  $t = today(); $res = null; $note = '';
  if ($qs !== '') {
    $sql = PSEL.($all ? '' : ' AND '.CEND.'>=?');
    $rows = as_index(q($sql, $all ? [] : [$t])->fetchAll()); $res = as_search($qs, $rows, $stores, $cats);
    if (!$res['full'] && !$res['part'] && !$all) { $rows = as_index(q(PSEL)->fetchAll()); $r2 = as_search($qs, $rows, $stores, $cats); if ($r2['full'] || $r2['part']) { $res = $r2; $note = 'Aktif ve yaklaşan kataloglarda sonuç yok; süresi dolmuş kataloglardaki kayıtlar gösteriliyor.'; } }
  }
  return [$res, $note];
}
/* KOPYA: asistan.php satır 58-59 — market adı ve "Anladığım" parçaları. Dönüş: [$sn, $chips] */
function mk_as_chips($in, $stores) {
  $sn = ''; if ($in && $in['market']) foreach ($stores as $s) if ($s['slug'] === $in['market']) $sn = $s['name'];
  $chips = []; if ($in) { if ($sn) $chips[] = 'Market: '.$sn; if ($in['cat'] !== '') $chips[] = 'Kategori: '.$in['cat']; if ($in['terms']) $chips[] = 'Ürün: '.implode(' ', $in['terms']); foreach ($in['qty'] as $x) $chips[] = 'Miktar: '.preg_replace('/(\d)(ml|g|li)$/', '$1 $2', $x); if ($in['min'] > 0) $chips[] = 'En az '.tl($in['min']); if ($in['max'] > 0) $chips[] = 'En çok '.tl($in['max']); if ($in['sort'] === 'asc') $chips[] = 'En ucuzdan'; if ($in['sort'] === 'desc') $chips[] = 'En pahalıdan'; if ($in['disc']) $chips[] = 'İndirimli'; }
  return [$sn, $chips];
}

/* KOPYA: sayfa.php satır 3-7 — varsayılan sayfa metinleri ($site = BRAND, $mail = iletişim e-postası) */
function mk_pages_default() {
  $site = BRAND; $mail = trim(setting('contact_email'));
  return ['hakkimizda'=>['Hakkımızda', "$site, BİM, A101 ve ŞOK'un haftalık aktüel kataloglarını tek yerde toplar. Broşürleri sayfa sayfa inceleyebilir, ürün adlarını ve fiyatlarını görebilir, aynı ürünün marketler arasındaki fiyatlarını karşılaştırabilirsiniz.\n\nAmacımız alışveriş öncesi zaman kazandırmak ve doğru karar vermenize yardımcı olmaktır. Site, market zincirlerinden bağımsız olarak yayınlanan kataloglardan derleme yapar."],
 'iletisim'=>['İletişim', "Görüş, öneri, hata bildirimi ve reklam talepleriniz için bize yazabilirsiniz.".($mail ? "\n\nE-posta: $mail" : "\n\nİletişim bilgileri yakında eklenecek.")],
 'gizlilik'=>['Gizlilik Politikası', "Bu site, ziyaretçilerden ad, adres gibi kişisel bilgi toplamaz. Ürün arama sırasında girilen kelimeler yalnızca sonuçları göstermek için kullanılır.\n\nSite yazı tipi için Google Fonts'u kullanır. Reklam gösterimi yapılırsa reklam sağlayıcıları çerez kullanabilir. Çerezleri tarayıcı ayarlarınızdan yönetebilirsiniz.\n\nBu politika zaman zaman güncellenebilir."],
 'kosullar'=>['Kullanım Koşulları', "Sitedeki fiyat ve ürün bilgileri yayınlanan kataloglardan derlenmiştir. Mağazalardaki güncel fiyat, stok ve kampanya koşulları farklı olabilir; alışveriş öncesi ilgili marketten teyit etmeniz önerilir.\n\nBİM, A101 ve ŞOK ilgili şirketlerin markalarıdır. $site bu şirketlerle bağlantılı değildir. Sitedeki bilgilerin kullanımından doğabilecek sonuçlardan sorumluluk kabul edilmez."]];
}
