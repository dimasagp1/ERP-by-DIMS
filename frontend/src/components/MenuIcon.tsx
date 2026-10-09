/** Ikon per menu (garis 1,6px, gaya sama dengan ikon departemen). Dipilih dari kode menu lalu kata kunci nama menu. */
import type { ReactNode } from 'react';

const G: Record<string, ReactNode> = {
  doc: <><path d="M6 3h8l4 4v14H6z" /><path d="M14 3v4h4M9 12h6M9 16h6" /></>,
  book: <><path d="M4 5a2 2 0 0 1 2-2h13v15H6a2 2 0 0 0-2 2z" /><path d="M4 20a2 2 0 0 0 2 2h13v-4M9 7h6" /></>,
  scale: <><path d="M12 4v17M7 21h10M5 7h14" /><path d="M5 7l-3 6a3 3 0 0 0 6 0zM19 7l-3 6a3 3 0 0 0 6 0z" /></>,
  chart: <><path d="M4 20V4M4 20h16" /><path d="M8 16v-4M12 16V8M16 16v-6" /></>,
  trend: <><path d="M4 20h16M4 20V4" /><path d="M7 15l4-4 3 3 5-6" /><path d="M15 8h4v4" /></>,
  pie: <><path d="M12 3a9 9 0 1 0 9 9h-9z" /><path d="M15 3.5A9 9 0 0 1 20.5 9H15z" /></>,
  lock: <><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></>,
  percent: <><path d="M19 5L5 19" /><circle cx="7" cy="7" r="2.3" /><circle cx="17" cy="17" r="2.3" /></>,
  receipt: <><path d="M6 3h12v18l-3-2-3 2-3-2-3 2z" /><path d="M9 8h6M9 12h6M9 16h3" /></>,
  wallet: <><path d="M4 7a2 2 0 0 1 2-2h12v4" /><path d="M4 7v11a2 2 0 0 0 2 2h14V9H6a2 2 0 0 1-2-2z" /><circle cx="16" cy="14.5" r="1.2" /></>,
  bank: <><path d="M3 9l9-5 9 5M5 9v9M9.5 9v9M14.5 9v9M19 9v9M3 21h18" /></>,
  coins: <><ellipse cx="9" cy="7" rx="5" ry="2.5" /><path d="M4 7v4c0 1.4 2.2 2.5 5 2.5s5-1.1 5-2.5V7" /><path d="M10 15.5c.6 1.2 2.5 2 5 2 2.8 0 5-1.1 5-2.5v-4c0-1.4-2.2-2.5-5-2.5" /></>,
  calculator: <><rect x="5" y="3" width="14" height="18" rx="2" /><path d="M8 7h8M8 11h.01M12 11h.01M16 11h.01M8 15h.01M12 15h.01M16 15v3M8 18h4" /></>,
  building: <><path d="M4 21V5l8-2v18M12 8l8 3v10M3 21h18" /><path d="M7 8h2M7 12h2M7 16h2M15 13h2M15 17h2" /></>,
  cart: <><path d="M3 4h2l2.4 11h11.1l2-8H6.3" /><circle cx="9" cy="19.5" r="1.5" /><circle cx="17" cy="19.5" r="1.5" /></>,
  clipList: <><rect x="5" y="4" width="14" height="17" rx="2" /><path d="M9 4V3h6v1M9 10h6M9 14h6M9 18h3" /></>,
  clipCheck: <><rect x="5" y="4" width="14" height="17" rx="2" /><path d="M9 4V3h6v1M9 13l2 2 4-4" /></>,
  compare: <><path d="M5 5h6v14H5zM13 9h6v10h-6z" /><path d="M8 9v2M16 13v2" /></>,
  handshake: <><path d="M3 11l4-4 5 2 5-2 4 4" /><path d="M7 7l-1 8 4 4 2-2 2 2 4-4-1-8" /><path d="M10 13l2 2" /></>,
  badge: <><path d="M12 3l2.4 1.8 3-.2.9 2.9 2.4 1.8-1 2.8 1 2.8-2.4 1.8-.9 2.9-3-.2L12 21l-2.4-1.8-3 .2-.9-2.9-2.4-1.8 1-2.8-1-2.8 2.4-1.8.9-2.9 3 .2z" /><path d="M9 12l2 2 4-4" /></>,
  tag: <><path d="M3 12V4h8l9 9-8 8z" /><circle cx="7.5" cy="8.5" r="1.3" /></>,
  truck: <><path d="M3 6h11v10H3zM14 9h4l3 3v4h-7" /><circle cx="7" cy="18" r="1.8" /><circle cx="17" cy="18" r="1.8" /></>,
  globe: <><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c2.5 2.7 3.8 5.7 3.8 9S14.5 18.3 12 21c-2.5-2.7-3.8-5.7-3.8-9S9.5 5.7 12 3z" /></>,
  undo: <><path d="M9 14L4 9l5-5" /><path d="M4 9h10a6 6 0 0 1 0 12h-3" /></>,
  wrench: <path d="M14.5 6.5a4 4 0 0 0 5 5L21 13l-8 8-3-3 8-8-1.5-1.5a4 4 0 0 1-5-5l2.5 2.5 2-2zM3 21l6-6" />,
  star: <path d="M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3 6.4 20.2l1.1-6.2L3 9.6l6.2-.9z" />,
  bag: <><path d="M5 8h14l-1 13H6z" /><path d="M9 8V6a3 3 0 0 1 6 0v2" /></>,
  calendar: <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M3 10h18M8 3v4M16 3v4M7 14h3M14 14h3M7 17h3" /></>,
  layers: <><path d="M12 3l9 5-9 5-9-5z" /><path d="M3 13l9 5 9-5" /><path d="M3 17.5l9 4.5 9-4.5" /></>,
  gauge: <><path d="M4 18a8 8 0 1 1 16 0" /><path d="M12 18l4-6" /><path d="M8 18h.01M16 18h.01" /></>,
  factory: <><path d="M3 21V10l5 3v-3l5 3V6l8 4v11z" /><path d="M7 17h2M12 17h2M17 17h1" /></>,
  target: <><circle cx="12" cy="12" r="8.5" /><circle cx="12" cy="12" r="5" /><circle cx="12" cy="12" r="1.5" /></>,
  inbox: <><path d="M3 13l3-8h12l3 8v6H3z" /><path d="M3 13h5l1 2h6l1-2h5" /></>,
  shield: <><path d="M12 3l8 3v6c0 4.5-3.4 8-8 9-4.6-1-8-4.5-8-9V6z" /><path d="M9 12l2 2 4-4" /></>,
  pin: <><path d="M12 21s7-6 7-11a7 7 0 0 0-14 0c0 5 7 11 7 11z" /><circle cx="12" cy="10" r="2.5" /></>,
  hand: <><path d="M7 11V6a1.5 1.5 0 0 1 3 0v5M10 10V4.5a1.5 1.5 0 0 1 3 0V10M13 10V5.5a1.5 1.5 0 0 1 3 0V11M16 11V8.5a1.5 1.5 0 0 1 3 0V14c0 4-3 7-7 7-3 0-4.5-1.5-6-3.5L4 14.5a1.5 1.5 0 0 1 2.4-1.8L7 13" /></>,
  boxCheck: <><path d="M3 7l9-4 9 4v10l-9 4-9-4z" /><path d="M3 7l9 4 9-4M12 11v10" /><path d="M15.5 15.5l1.5 1.5 3-3" /></>,
  box: <><path d="M3 7l9-4 9 4v10l-9 4-9-4z" /><path d="M3 7l9 4 9-4M12 11v10" /></>,
  arrows: <><path d="M4 8h14l-3-3M20 16H6l3 3" /></>,
  boxOut: <><path d="M3 7l9-4 9 4v6" /><path d="M3 7l9 4 9-4M12 11v10l-9-4V7" /><path d="M16 18h6M19 15l3 3-3 3" /></>,
  barcode: <><path d="M4 5v14M7 5v14M10 5v14M14 5v14M16 5v14M20 5v14" /></>,
  sliders: <><path d="M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1" /><circle cx="15" cy="6" r="2" /><circle cx="9" cy="12" r="2" /><circle cx="17" cy="18" r="2" /></>,
  hourglass: <><path d="M6 3h12M6 21h12M7 3c0 5 5 6 5 9s-5 4-5 9M17 3c0 5-5 6-5 9s5 4 5 9" /></>,
  network: <><circle cx="12" cy="5" r="2.2" /><circle cx="5" cy="19" r="2.2" /><circle cx="19" cy="19" r="2.2" /><path d="M12 7.2v4.3M12 11.5l-6 5.5M12 11.5l6 5.5" /></>,
  trash: <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6" />,
  flask: <><path d="M9 3h6M10 3v6l-5.2 9.2A2 2 0 0 0 6.5 21h11a2 2 0 0 0 1.7-2.8L14 9V3" /><path d="M7.5 15h9" /></>,
  clock: <><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></>,
  alert: <><path d="M12 3l9.5 17h-19z" /><path d="M12 10v4M12 17.5h.01" /></>,
  refresh: <><path d="M20 11a8 8 0 0 0-14.6-4.5L4 8M4 4v4h4" /><path d="M4 13a8 8 0 0 0 14.6 4.5L20 16M20 20v-4h-4" /></>,
  search: <><circle cx="11" cy="11" r="6.5" /><path d="M20 20l-4.2-4.2" /></>,
  message: <path d="M4 5h16v11H9l-5 4z" />,
  thermo: <><path d="M10 4a2 2 0 0 1 4 0v10a4 4 0 1 1-4 0z" /><path d="M12 9v7" /></>,
  ruler: <><path d="M3 17L17 3l4 4L7 21z" /><path d="M7 13l2 2M10 10l2 2M13 7l2 2" /></>,
  zap: <path d="M13 3L4 14h7l-1 7 9-11h-7z" />,
  folder: <path d="M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />,
  sitemap: <><rect x="9" y="3" width="6" height="5" rx="1" /><rect x="3" y="16" width="6" height="5" rx="1" /><rect x="15" y="16" width="6" height="5" rx="1" /><path d="M12 8v4M6 16v-4h12v4" /></>,
  idcard: <><rect x="3" y="5" width="18" height="14" rx="2" /><circle cx="9" cy="11" r="2" /><path d="M6 16c.5-1.5 1.7-2.3 3-2.3s2.5.8 3 2.3M14 10h4M14 13h3" /></>,
  userPlus: <><circle cx="9" cy="8" r="3.5" /><path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M19 8v6M16 11h6" /></>,
  users: <><circle cx="9" cy="8" r="3.2" /><path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6" /><circle cx="17" cy="9" r="2.4" /><path d="M16.5 14.2c2.6.3 4.5 2.4 4.5 5.1" /></>,
  sign: <><path d="M6 3h8l4 4v14H6z" /><path d="M14 3v4h4M8.5 17c1.5-2 2.5-2 3 0s1.5 1 3-1" /></>,
  plane: <path d="M10.5 13.5L3 11l1.5-1.5 8 .5 4.5-5a2 2 0 0 1 3 3l-5 4.5.5 8L14 22l-2.5-7.5-4 3.5v3L6 22l-1-4-4-1 1-1.5h3z" />,
  heart: <><path d="M12 20s-8-4.8-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 9c0 6.2-8 11-8 11z" /><path d="M7.5 11h2l1.2-2 1.8 4 1.2-2h2.8" /></>,
  grad: <><path d="M2 9l10-5 10 5-10 5z" /><path d="M6 11v5c2 2 10 2 12 0v-5M22 9v6" /></>,
  award: <><circle cx="12" cy="9" r="6" /><path d="M8.5 13.8L7 21l5-3 5 3-1.5-7.2" /></>,
  paperclip: <path d="M20 11.5l-8.1 8.1a5 5 0 0 1-7.1-7.1l8.5-8.5a3.3 3.3 0 0 1 4.7 4.7l-8.5 8.5a1.7 1.7 0 0 1-2.4-2.4l7.8-7.8" />,
  car: <><path d="M5 16l1.5-6a2 2 0 0 1 2-1.5h7a2 2 0 0 1 2 1.5L19 16" /><rect x="3" y="16" width="18" height="4" rx="1" /><path d="M6.5 20v1.5M17.5 20v1.5M3.5 13h17" /></>,
  door: <><path d="M5 21V3h11v18M3 21h18" /><path d="M13 12h.01M16 5l3 1v15" /></>,
  utensils: <><path d="M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10" /><path d="M17 21V3c-2 1.5-3 4-3 7h3" /></>,
  recycle: <><path d="M7 19H4.5a1.5 1.5 0 0 1-1.3-2.2l1.6-2.8M17 19h2.5a1.5 1.5 0 0 0 1.3-2.2L17 10M10 5.2l-1.3 2.2M14 5.2a1.5 1.5 0 0 0-2.6 0L9 9.5" /><path d="M10 19h7M13 16l-3 3 3 3M6 12L3.5 7.6 8 6.7" /></>,
  sparkle: <path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM18 15l.8 2.2L21 18l-2.2.8L18 21l-.8-2.2L15 18l2.2-.8z" />,
  image: <><rect x="3" y="4" width="18" height="16" rx="2" /><circle cx="8.5" cy="9.5" r="1.8" /><path d="M21 16l-5-5-9 9" /></>,
  leaf: <><path d="M5 19c0-9 6-14 15-14 0 9-5 15-14 15z" /><path d="M5 19l8-8" /></>,
  key: <><circle cx="8" cy="15" r="4" /><path d="M11 12l9-9M16 7l3 3M14 9l2 2" /></>,
  hash: <path d="M5 9h15M4 15h15M10 4L8 20M16 4l-2 16" />,
  currency: <><circle cx="12" cy="12" r="8.5" /><path d="M14.8 9.5c-.5-1-1.6-1.5-2.8-1.5-1.7 0-3 .9-3 2s1.3 1.7 3 2 3 1 3 2-1.3 2-3 2c-1.2 0-2.3-.5-2.8-1.5M12 6.5v11" /></>,
  plug: <><path d="M9 3v5M15 3v5M6 8h12v3a6 6 0 0 1-12 0zM12 17v4" /></>,
  history: <><path d="M3 12a9 9 0 1 0 2.6-6.4L3 8" /><path d="M3 3v5h5M12 7.5V12l3 2" /></>,
  mail: <><rect x="3" y="5" width="18" height="14" rx="2" /><path d="M3 7l9 6 9-6" /></>,
  gear: <><circle cx="12" cy="12" r="3" /><path d="M12 2.8v2.4M12 18.8v2.4M4.2 7.5l2.1 1.2M17.7 15.3l2.1 1.2M4.2 16.5l2.1-1.2M17.7 8.7l2.1-1.2" /><circle cx="12" cy="12" r="7" /></>,
  broom: <><path d="M19 3l-7.5 7.5" /><path d="M11.5 10.5c-3 0-5 1.5-6.5 3.5l5 5c2-1.5 3.5-3.5 3.5-6.5z" /><path d="M5 14l-2 7 7-2" /></>,
  beaker: <><path d="M5 3h14M7 3v14a4 4 0 0 0 4 4h2a4 4 0 0 0 4-4V3" /><path d="M7 11h10" /></>,
};

/** Kode menu yang ikonnya ditetapkan langsung (selebihnya dari kata kunci nama). */
const BY_CODE: Record<string, string> = {
  'FIN-02': 'sitemap', 'FIN-03': 'book', 'FIN-04': 'scale', 'FIN-10': 'receipt', 'FIN-11': 'coins', 'FIN-12': 'wallet', 'FIN-20': 'receipt',
  'FIN-21': 'coins', 'FIN-22': 'hourglass', 'FIN-30': 'wallet', 'FIN-31': 'bank', 'FIN-32': 'trend', 'FIN-40': 'building', 'FIN-50': 'pie',
  'FIN-51': 'gauge', 'FIN-52': 'layers', 'FIN-53': 'calculator', 'FIN-54': 'scale', 'FIN-55': 'sitemap', 'FIN-60': 'percent', 'FIN-61': 'percent',
  'FIN-62': 'sign', 'FIN-63': 'calculator', 'FIN-70': 'lock', 'FIN-71': 'chart', 'FIN-72': 'target',
  'PRC-02': 'clipList', 'PRC-03': 'userPlus', 'PRC-04': 'badge', 'PRC-05': 'mail', 'PRC-06': 'compare', 'PRC-07': 'cart', 'PRC-08': 'tag',
  'PRC-09': 'truck', 'PRC-10': 'globe', 'PRC-11': 'undo', 'PRC-12': 'handshake', 'PRC-13': 'star', 'PRC-90': 'chart',
  'SCM-02': 'bag', 'SCM-03': 'trend', 'SCM-04': 'calendar', 'SCM-05': 'layers', 'SCM-06': 'gauge', 'SCM-07': 'factory', 'SCM-08': 'target',
  'SCM-20': 'inbox', 'SCM-21': 'shield', 'SCM-22': 'pin', 'SCM-23': 'hand', 'SCM-24': 'boxCheck', 'SCM-25': 'arrows', 'SCM-26': 'truck',
  'SCM-27': 'undo', 'SCM-28': 'undo', 'SCM-29': 'boxOut', 'SCM-40': 'barcode', 'SCM-41': 'clipCheck', 'SCM-42': 'sliders', 'SCM-43': 'sliders',
  'SCM-44': 'hourglass', 'SCM-45': 'network', 'SCM-46': 'trash', 'SCM-90': 'chart',
  'PRE-02': 'factory', 'PRE-04': 'clipList', 'PRE-08': 'boxCheck', 'PRE-10': 'undo', 'PRE-12': 'clock',
  'HC-02': 'sitemap', 'HC-03': 'idcard', 'HC-05': 'userPlus', 'HC-06': 'sign', 'HC-07': 'clock', 'HC-08': 'calendar', 'HC-09': 'wallet',
  'HC-10': 'heart', 'HC-12': 'award', 'HC-13': 'target', 'HC-14': 'coins',
  'ESS-01': 'calendar', 'ESS-02': 'clock', 'ESS-03': 'wallet', 'ESS-04': 'cart', 'ESS-09': 'plane', 'ESS-10': 'inbox',
  'SYS-01': 'building', 'SYS-02': 'sitemap', 'SYS-03': 'key', 'SYS-04': 'sign', 'SYS-05': 'hash', 'SYS-06': 'box', 'SYS-07': 'ruler',
  'SYS-08': 'handshake', 'SYS-09': 'pin', 'SYS-10': 'calendar', 'SYS-11': 'currency', 'SYS-12': 'percent', 'SYS-13': 'plug',
  'SYS-14': 'history', 'SYS-15': 'mail',
};

/** Kata kunci nama menu → ikon, untuk menu fase berikutnya (M3) yang belum ditetapkan per kode. */
const BY_WORD: [RegExp, string][] = [
  [/deviasi|insiden|k3|recall/i, 'alert'], [/capa/i, 'shield'], [/change control|perubahan/i, 'refresh'], [/audit/i, 'search'],
  [/keluhan|komplain/i, 'message'], [/pelulusan|release|kualifikasi|validasi/i, 'badge'], [/stabilitas|suhu/i, 'thermo'],
  [/sampling|pengujian|laborator|ipc|qc|uji/i, 'flask'], [/kalibrasi/i, 'ruler'], [/maintenance|perbaikan|kerusakan|work request|sparepart/i, 'wrench'],
  [/utilitas|energi|listrik/i, 'zap'], [/batch record|ebmr|bmr/i, 'clipCheck'], [/dispensing|penimbangan/i, 'scale'], [/line clearance|kebersihan/i, 'broom'],
  [/downtime|oee|kapasitas/i, 'gauge'], [/mesin|peralatan/i, 'gear'], [/formula|bahan baku baru/i, 'beaker'], [/proyek/i, 'folder'],
  [/artwork|desain/i, 'image'], [/registrasi|izin|legal|perizinan/i, 'sign'], [/trial|percobaan/i, 'flask'], [/herbal|simplisia/i, 'leaf'],
  [/pelatihan|training/i, 'grad'], [/kompetensi/i, 'award'], [/rekrutmen/i, 'userPlus'], [/hubungan industrial|disiplin/i, 'scale'],
  [/atk/i, 'paperclip'], [/kendaraan/i, 'car'], [/ruang|booking/i, 'door'], [/katering|konsumsi/i, 'utensils'], [/gate pass|keamanan|tamu/i, 'shield'],
  [/limbah/i, 'recycle'], [/inventaris|aset/i, 'box'], [/dokumen|sop|spesifikasi/i, 'doc'], [/laporan/i, 'chart'], [/approval/i, 'inbox'],
  [/slip|gaji/i, 'wallet'], [/cuti|jadwal|kalender/i, 'calendar'], [/lembur|jam/i, 'clock'], [/perjalanan/i, 'plane'], [/pembelian|order/i, 'cart'],
  [/supplier|mitra/i, 'handshake'], [/stok|gudang|lot/i, 'box'], [/produksi/i, 'factory'], [/biaya|harga/i, 'calculator'],
];

export function menuIconName(code: string, name = ''): string {
  return BY_CODE[code] ?? BY_WORD.find(([re]) => re.test(name))?.[1] ?? 'doc';
}

/** Ikon menu dalam kotak berwarna aplikasi. */
export function MenuIcon({ code, name, color, size = 26 }: { code: string; name?: string; color?: string; size?: number }) {
  const c = color ?? 'var(--text-2)';
  return (
    <span className="menu-ic" style={{ width: size, height: size, color: c, background: color ? `${color}1A` : 'var(--surface-2)' }} aria-hidden="true">
      <svg className="ic" width={Math.round(size * 0.62)} height={Math.round(size * 0.62)} viewBox="0 0 24 24">{G[menuIconName(code, name)]}</svg>
    </span>
  );
}
