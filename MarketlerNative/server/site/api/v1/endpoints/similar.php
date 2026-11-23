<?php
/* GET /api/v1/compare/similar — fiyat-karsilastirma.php "Benzer Ürünlerde Birim Fiyat" bölümü (FARKLI ürünler). */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_endpoint($param) {
  $sim = mk_similar_groups(q(PSEL.' AND c.cdate<=? AND '.CEND.'>=? LIMIT 6000', [today(), today()])->fetchAll(), 12);   /* sitedeki çağrının aynısı */
  $groups = [];
  foreach ($sim as $sg) {
    $rows = []; foreach (mk_enrich($sg['rows']) as $i => $r) { $x = mk_item($r); $x['unit_price'] = ['value' => mk_money($r['_u']), 'unit' => $sg['k'], 'text' => tl($r['_u']).'/'.$sg['k']]; $x['is_best'] = $i === 0; $rows[] = $x; }
    $groups[] = ['key' => $sg['key'], 'label' => mk_type_label($sg['key']), 'unit' => $sg['k'], 'rows' => $rows];
  }
  mk_ok(['notice' => 'Bu bölümdeki ürünler aynı ürün değildir. Yalnızca aynı türde (ör. ayçiçek yağı) ve aynı ölçü birimine (kg veya L) çevrilebilen ürünlerin birim fiyatı karşılaştırılır. En az 2 markette ve en az 3 ürün bulunan türler listelenir.', 'groups' => $groups]);
}
