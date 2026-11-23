<?php
/* SENTETİK test verisi. Gerçek ürün, fiyat veya kişisel veri İÇERMEZ (marka adları uydurmadır).
   Kullanım (yalnızca tests/run.php çağırır): mk_seed(PDO $pdo, string $scenario, array $siteDirs)
   Senaryolar: 'bos' (katalog yok), '0' (indirimli ürün yok), '2' (2 indirimli), '5' (5 indirimli).
   Tarihler bugüne göre hesaplanır: her markette 1 süresi dolmuş, 1 aktif, 1 yaklaşan katalog; ayrıca onay bekleyen (hidden=2) ve gizli (hidden=1) katalog. */

const MK_SEED_KATS = ['elektronik'=>'Elektronik', 'beyaz-esya'=>'Beyaz Eşya', 'ev-yasam'=>'Ev ve Yaşam', 'mutfak'=>'Mutfak', 'gida'=>'Gıda', 'temizlik'=>'Temizlik', 'kisisel-bakim'=>'Kişisel Bakım', 'giyim'=>'Giyim', 'anne-bebek'=>'Anne ve Bebek', 'oyuncak'=>'Oyuncak', 'spor'=>'Spor', 'otomotiv'=>'Otomotiv', 'kitap'=>'Kitap', 'diger'=>'Diğer'];
const MK_SEED_SECRET = 'GIZLI-CRON-ANAHTARI-SIZMAMALI-7f3c';

function mk_seed_img($siteDirs, $rel, $w = 60, $h = 80) {
  foreach ($siteDirs as $d) {
    $f = $d.'/'.$rel; if (!is_dir(dirname($f))) mkdir(dirname($f), 0777, true);
    $im = imagecreatetruecolor($w, $h); imagefill($im, 0, 0, imagecolorallocate($im, 200, 30, 40));
    $ext = pathinfo($rel, PATHINFO_EXTENSION);
    if ($ext === 'webp') imagewebp($im, $f, 80); else imagepng($im, $f);
    imagedestroy($im); touch($f, 1700000000);   /* sabit tarih: ?v=filemtime iki sitede aynı olsun */
  }
}

function mk_seed($pdo, $scenario, $siteDirs) {
  $tables = ['admin_log','catalogs','categories','items','item_media','link_blocks','logs','prices','product_links','products','settings','stores','users'];
  foreach ($tables as $t) $pdo->exec("TRUNCATE TABLE `$t`");
  foreach ($siteDirs as $d) foreach (['img/k', 'img/u'] as $sub) if (is_dir("$d/$sub")) exec('rm -rf '.escapeshellarg("$d/$sub"));
  $q = function ($sql, $a = []) use ($pdo) { $s = $pdo->prepare($sql); $s->execute($a); return $s; };

  $n = 0; foreach (MK_SEED_KATS as $sl => $nm) $q('INSERT INTO categories(id,slug,name) VALUES(?,?,?)', [++$n, $sl, $nm]);
  $cat = array_flip(array_keys(MK_SEED_KATS)); foreach ($cat as $k => $v) $cat[$k] = $v + 1;
  foreach ([[1,'bim','BİM','#e30613'], [2,'a101','A101','#00a0e3'], [3,'sok','ŞOK','#ffd100']] as $s) $q('INSERT INTO stores(id,slug,name,color) VALUES(?,?,?,?)', $s);
  mk_seed_img($siteDirs, 'img/logo-bim.png', 40, 20);
  $set = ['schema_v'=>'5', 'theme'=>'modern', 'pretty'=>'1', 'site_url'=>'https://marketler.org', 'site_title'=>'MARKETLER.ORG', 'site_desc'=>'Test açıklaması',
    'contact_email'=>'iletisim@example.com', 'cron_key'=>MK_SEED_SECRET, 'src_run'=>'2026-11-23 06:00:12', 'logo_bim'=>'img/logo-bim.png',
    'page_gizlilik'=>"Birinci paragraf: kişisel veri toplanmaz.\n\nİkinci paragraf\nalt satır ile.", 'ad_banner'=>'', 'ad_adsense'=>''];
  foreach ($set as $k => $v) $q('INSERT INTO settings(k,v) VALUES(?,?)', [$k, $v]);
  $q('INSERT INTO users(username,pass) VALUES(?,?)', ['yonetici', password_hash('test-parola-sizmamali', PASSWORD_DEFAULT)]);
  $q('INSERT INTO products(id,store_id,name) VALUES(1,1,?)', ['eski tablo satırı']);
  if ($scenario === 'bos') return [];

  $T = strtotime(date('Y-m-d')); $d = function ($o) use ($T) { return date('Y-m-d', $T + $o * 86400); };
  $AY = ['', 'ocak','subat','mart','nisan','mayis','haziran','temmuz','agustos','eylul','ekim','kasim','aralik'];
  $mkCat = function ($sid, $ss, $sname, $off, $hidden = 0, $imgs = null) use ($q, $d, $AY) {
    $dt = $d($off); $t = strtotime($dt); $slug = $ss.'-'.(int)date('j', $t).'-'.$AY[(int)date('n', $t)].'-'.date('Y', $t).'-aktuel-urunler-katalogu'.($hidden ? '-h'.$hidden : '');
    $q('INSERT INTO catalogs(store_id,slug,title,label,cdate,source,hidden,images) VALUES(?,?,?,?,?,?,?,?)', [$sid, $slug, "$sname ".date('j', $t).' Test Aktüel Ürünler Kataloğu', date('j', $t).' Test', $dt, 'test', $hidden, $imgs === null ? null : json_encode($imgs)]);
    return [(int)$q('SELECT LAST_INSERT_ID()')->fetchColumn(), $slug];
  };
  $C = [];
  foreach ([[1,'bim','BİM'], [2,'a101','A101'], [3,'sok','ŞOK']] as $s) {
    $C[$s[1]]['past'] = $mkCat($s[0], $s[1], $s[2], -9);
    $C[$s[1]]['live'] = $mkCat($s[0], $s[1], $s[2], -2);
    $C[$s[1]]['soon'] = $mkCat($s[0], $s[1], $s[2], 3);
  }
  $C['bim']['pending'] = $mkCat(1, 'bim', 'BİM', -1, 2);
  $C['a101']['hidden'] = $mkCat(2, 'a101', 'A101', -2, 1);
  /* broşür görselleri: BİM aktif kataloğunda 2 yerel + 1 uzak adres; ilk sayfanın c/ ve t/ küçük kopyaları var */
  $bs = $C['bim']['live'][1];
  $imgs = ["img/k/$bs/1.webp", "img/k/$bs/2.webp", 'https://aktuel-urunler.com/wp-diger/uploads/2026/11/ornek-3.webp'];
  foreach (["img/k/$bs/1.webp", "img/k/$bs/2.webp", "img/k/$bs/c/1.webp", "img/k/$bs/t/1.webp"] as $f) mk_seed_img($siteDirs, $f);
  $q('UPDATE catalogs SET images=? WHERE id=?', [json_encode($imgs), $C['bim']['live'][0]]);
  $as = $C['a101']['live'][1]; mk_seed_img($siteDirs, "img/k/$as/1.webp");
  $q('UPDATE catalogs SET images=? WHERE id=?', [json_encode(["img/k/$as/1.webp"]), $C['a101']['live'][0]]);

  $I = [];
  $add = function ($store, $which, $name, $price, $catSlug, $old = null) use ($q, $C, $cat, &$I) {
    $q('INSERT INTO items(catalog_id,cat_id,name,hash,price,old_price) VALUES(?,?,?,?,?,?)', [$C[$store][$which][0], $cat[$catSlug], $name, md5(mb_strtolower($name)), $price, $old]);
    $id = (int)$q('SELECT LAST_INSERT_ID()')->fetchColumn(); $I["$store|$which|$name"] = $id; return $id;
  };
  /* --- aynı ürün birden çok markette (kesin eşleşme) --- */
  $add('bim', 'live', 'Isıtek Airfryer XA4200 4.2 L', 4999, 'elektronik');
  $add('a101', 'live', 'Isıtek Airfryer XA4200 4.2 L', 4599, 'elektronik');
  $add('sok', 'live', 'Isıtek Airfryer XA4200 4.2 L', 5199, 'elektronik');
  $add('bim', 'live', 'Yaylaköy Süt 1 L', 32.50, 'gida');
  $add('a101', 'live', 'Yaylaköy Süt 1 L', 31.90, 'gida');
  /* engellenmiş eşleşme (link_blocks): aynı ad ama yönetici reddetmiş */
  $b1 = $add('bim', 'live', 'Dereli Süt 1 L', 29.90, 'gida');
  $b2 = $add('sok', 'live', 'Dereli Süt 1 L', 30.50, 'gida');
  $q('INSERT INTO link_blocks(a,b) VALUES(?,?)', [min($b1, $b2), max($b1, $b2)]);
  /* elle kurulan grup (product_links): farklı yazım */
  $m1 = $add('bim', 'live', 'Kırçiçek Ayçiçek Yağı 5 L', 289, 'gida');
  $m2 = $add('a101', 'live', 'Kirçiçek Ayçiçek Yağ 5 Lt', 279, 'gida');
  $q('INSERT INTO product_links(grp,item_id) VALUES(1,?),(1,?)', [$m1, $m2]);
  /* emsal grupları (birim fiyat): süt ve ayçiçek yağı, farklı markalar */
  $add('sok', 'live', 'Ovaçiftlik Süt 1 L', 27.75, 'gida');
  $add('a101', 'live', 'Tepeli Süt 1 L', 35.00, 'gida');
  $add('sok', 'live', 'Göl Kenarı Süt 1 L', 41.00, 'gida');
  $add('bim', 'live', 'Bereket Ayçiçek Yağı 5 L', 299, 'gida');
  $add('sok', 'live', 'Sarıtarla Ayçiçek Yağı 5 L', 265, 'gida');
  $add('a101', 'live', 'Güneşli Ayçiçek Yağı 5 L', 310, 'gida');
  $add('a101', 'live', 'Altınbaşak Ayçiçek Yağı 5 L', 275, 'gida');
  $add('sok', 'live', 'Köpüklü Toz Deterjan 4 kg', 189, 'temizlik');
  $add('bim', 'live', 'Beyazgül Toz Deterjan 6 kg', 249, 'temizlik');
  $add('a101', 'live', 'Temizköy Toz Deterjan 5 kg', 219, 'temizlik');
  $add('bim', 'live', 'Kavruk Türk Kahvesi 100 g', 54.90, 'gida');
  $add('sok', 'live', 'Kavruk Türk Kahvesi 250 g', 119.90, 'gida');
  $add('a101', 'live', 'Demlik Siyah Çay 1000 g', 159, 'gida');
  $add('bim', 'live', 'Ekranix 55 inç 4K Smart TV EX55U800', 17999, 'elektronik');
  $add('a101', 'live', 'Sesyolu Kablosuz Kulaklık SK-210', 499, 'elektronik');
  $add('sok', 'live', 'Minik Bebek Bezi 4 Numara 40 Adet', 299, 'anne-bebek');
  /* fiyat geçmişi: aynı market + aynı ad, önceki katalogda farklı fiyat */
  $add('bim', 'past', 'Kavruk Türk Kahvesi 100 g', 59.90, 'gida');
  $add('bim', 'past', 'Yaylaköy Süt 1 L', 32.50, 'gida');
  $add('a101', 'past', 'Tepeli Süt 1 L', 33.00, 'gida');
  /* yaklaşan ve gizli/onay bekleyen kataloglarda ürünler (gizliler hiçbir yerde görünmemeli) */
  $add('sok', 'soon', 'Isıtek Airfryer XA4200 4.2 L', 4899, 'elektronik');
  $add('bim', 'soon', 'Yaylaköy Süt 1 L', 33.25, 'gida');
  $add('bim', 'pending', 'GİZLİ ONAY BEKLEYEN ÜRÜN 1 L', 1.50, 'gida');
  $add('a101', 'hidden', 'GİZLİ YÖNETİCİ ÜRÜNÜ 500 g', 2.50, 'gida');
  /* LIKE joker karakteri içeren ad */
  $add('a101', 'live', 'Yüzde%50 Kampanya_Sepeti 1 kg', 99, 'diger');
  /* ürün görselleri: onaylı + dosya var / kaldırılmış / onaylı ama dosya yok */
  $u1 = $I['a101|live|Isıtek Airfryer XA4200 4.2 L']; $u2 = $I['bim|live|Ekranix 55 inç 4K Smart TV EX55U800']; $u3 = $I['sok|live|Minik Bebek Bezi 4 Numara 40 Adet'];
  mk_seed_img($siteDirs, "img/u/$u1.webp");
  $q('INSERT INTO item_media(item_id,catalog_id,page_idx,x,y,w,h,file,status) VALUES(?,?,0,1,1,10,10,?,1),(?,?,0,1,1,10,10,?,2),(?,?,0,1,1,10,10,?,1)',
    [$u1, $C['a101']['live'][0], "img/u/$u1.webp", $u2, $C['bim']['live'][0], "img/u/$u2.webp", $u3, $C['sok']['live'][0], "img/u/$u3-yok.webp"]);
  /* indirim senaryosu: aktif kataloglarda N üründe old_price > price */
  $disc = ['bim|live|Isıtek Airfryer XA4200 4.2 L', 'a101|live|Demlik Siyah Çay 1000 g', 'sok|live|Köpüklü Toz Deterjan 4 kg', 'bim|live|Kavruk Türk Kahvesi 100 g', 'a101|live|Sesyolu Kablosuz Kulaklık SK-210'];
  foreach (array_slice($disc, 0, (int)$scenario) as $k) $q('UPDATE items SET old_price=ROUND(price*1.25,2) WHERE id=?', [$I[$k]]);
  /* indirim değil: old_price < price (gösterilmemeli) */
  $q('UPDATE items SET old_price=price-5 WHERE id=?', [$I['sok|live|Ovaçiftlik Süt 1 L']]);
  return ['catalogs' => $C, 'items' => $I];
}
