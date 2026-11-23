<?php
/* MARKETLER.ORG API v1 ayarları. Gizli bilgi İÇERMEZ (veritabanı bilgileri sitenin config.php dosyasından okunur). */
if (!defined('MK_API')) { http_response_code(404); exit; }

const MK_API_VERSION = '1.0';
/* Hız sınırı: istek / dakika / IP, sınıf bazında. */
const MK_API_RATE = ['std' => 60, 'heavy' => 20, 'suggest' => 120];
/* Yerel geliştirmede HTTP'ye izin (canlıda MUTLAKA false). */
const MK_API_ALLOW_HTTP = false;
