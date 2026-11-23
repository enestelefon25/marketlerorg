<?php
/* GET /api/v1/pages/{hakkimizda|iletisim|gizlilik|kosullar} — sayfa.php metinleri (düz metin paragraflar). */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_endpoint($s) {
  $P = mk_pages_default(); if (!isset($P[$s])) mk_404('Sayfa');
  $body = trim(setting('page_'.$s)) ?: $P[$s][1];   /* KOPYA: sayfa.php satır 9 */
  $par = []; foreach (preg_split('/\n\s*\n/', $body) as $p) $par[] = trim($p);   /* KOPYA: sayfa.php satır 11 bölme kuralı */
  mk_ok(['title' => $P[$s][0], 'paragraphs' => $par], [], 'public, max-age=3600');
}
