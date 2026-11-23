<?php
/* GET /api/v1/markets — market listesi (stores tablosu sırasıyla). */
if (!defined('MK_API')) { http_response_code(404); exit; }

function mk_endpoint($param) {
  mk_ok(array_map('mk_market', q('SELECT * FROM stores')->fetchAll()), [], 'public, max-age=3600');
}
