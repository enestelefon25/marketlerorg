<?php
/* GET /api/v1/compare?q=&page=&limit= — cmp_groups() (aynı img/cmpcache.json). Yeni eşleştirme YOK; yalnızca kesin eşleşmeler. */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_endpoint($param) {
  $qf = as_fold(trim(mk_in_str('q', 120))); list($page, $lim, $off) = mk_in_page(50, 20);
  $groups = cmp_groups();
  if ($qf !== '') $groups = array_values(array_filter($groups, function ($g) use ($qf) { foreach ($g as $r) if (strpos(as_fold($r['name']), $qf) !== false) return true; return false; }));
  mk_ok(array_map('mk_group', array_slice($groups, $off, $lim)), ['page' => $page, 'limit' => $lim, 'total' => count($groups)]);
}
