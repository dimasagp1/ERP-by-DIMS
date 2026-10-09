-- =====================================================================
-- Data referensi M2. Semua nilai bisa diubah lewat menu Pengaturan masing-masing aplikasi.
-- =====================================================================

-- ---------- Akun tambahan (FIN-02) ----------
INSERT INTO fin.account (code, name, type, normal_balance, parent_id, requires_cost_center)
SELECT v.code, v.name, v.type, v.nb, p.id, v.cc FROM (VALUES
 ('1225', 'PPh 22 dibayar dimuka',                    'ASSET',     'D', '1',    FALSE),
 ('1306', 'Persediaan ATK & barang umum',             'ASSET',     'D', '1300', FALSE),
 ('2109', 'Biaya impor belum difakturkan',            'LIABILITY', 'C', '2',    FALSE),
 ('5204', 'Selisih biaya produksi batch',             'EXPENSE',   'D', '5',    FALSE),
 ('5301', 'Biaya tenaga kerja langsung diserap',      'EXPENSE',   'D', '5',    FALSE),
 ('5302', 'Biaya overhead pabrik diserap',            'EXPENSE',   'D', '5',    FALSE)
) AS v(code, name, type, nb, parent, cc) JOIN fin.account p ON p.code = v.parent;

-- ---------- Parameter FIN & PRC (FIN-99) ----------
INSERT INTO fin.fin_param (key, value, description) VALUES
 ('INV_ACC_RM',          '1301', 'Akun persediaan item bahan baku'),
 ('INV_ACC_PM',          '1302', 'Akun persediaan item bahan kemas'),
 ('INV_ACC_WIP',         '1303', 'Akun barang dalam proses'),
 ('INV_ACC_FG',          '1304', 'Akun persediaan barang jadi'),
 ('INV_ACC_SP',          '1305', 'Akun persediaan sparepart'),
 ('INV_ACC_ATK',         '1306', 'Akun persediaan ATK & barang umum'),
 ('EXP_ACC_SP',          '6202', 'Beban pemakaian sparepart (SCM-29)'),
 ('EXP_ACC_ATK',         '6301', 'Beban pemakaian ATK (SCM-29)'),
 ('MATCH_TOLERANCE_PCT', '2',    '3-way match: selisih harga faktur vs PO maksimum (%) sebelum butuh approval khusus'),
 ('GR_OVER_PCT',         '2',    'Toleransi penerimaan melebihi qty PO (%)'),
 ('RFQ_MIN_QUOTES',      '3',    'Jumlah penawaran minimum untuk PO di atas batas (PRC aturan)'),
 ('RFQ_THRESHOLD',       '50000000', 'Batas nilai PO (Rp) yang mewajibkan perbandingan penawaran'),
 ('PPH_BADAN_RATE',      '0.22', 'Tarif PPh Badan untuk estimasi rekonsiliasi fiskal (FIN-63)'),
 ('MRP_HORIZON_WEEKS',   '12',   'Horizon MRP (minggu)'),
 ('MRP_NIGHTLY',         'true', 'Jalankan MRP otomatis setiap malam pukul 01.00');

-- ---------- Jenis dokumen M2 (SYS-05) ----------
INSERT INTO sys.doc_type (code, name, app_code, menu_code, prefix, requires_esign) VALUES
 ('BOM',  'Bill of Materials',              'SCM', 'SCM-05', 'BOM',  FALSE),
 ('BAST', 'Berita Acara Serah Terima Jasa', 'PRC', 'PRC-12', 'BAST', FALSE),
 ('LC',   'Impor & Landed Cost',            'PRC', 'PRC-10', 'LC',   FALSE),
 ('MR',   'Permintaan Bahan',               'PRE', 'PRE-04', 'MR',   FALSE),
 ('MRT',  'Retur Sisa Bahan',               'PRE', 'PRE-10', 'MRT',  FALSE),
 ('TRF',  'Transfer Antar Gudang',          'SCM', 'SCM-25', 'TRF',  FALSE),
 ('CRT',  'Retur Pelanggan',                'SCM', 'SCM-27', 'CRT',  FALSE),
 ('GI',   'Pengeluaran Non-Produksi',       'SCM', 'SCM-29', 'GI',   FALSE),
 ('SCR',  'Pemusnahan Barang',              'SCM', 'SCM-46', 'BAP',  TRUE),
 ('OHA',  'Alokasi Overhead',               'FIN', 'FIN-55', 'OHA',  FALSE),
 ('BPOT', 'Bukti Potong PPh',               'FIN', 'FIN-62', 'BP',   FALSE);
UPDATE sys.doc_type SET ess_menu_code = 'ESS-04' WHERE code = 'PR';

-- ---------- Matriks approval M2 (SYS-04) ----------
INSERT INTO sys.approval_rule (doc_type_code, level, min_amount, approver_type, approver_role, approver_app, label, condition_key) VALUES
 ('PO',   4, 0, 'ROLE',            'DIRECTOR',   '*',   'Direktur (pemasok tunggal di atas batas)', 'SINGLE_SOURCE'),
 ('PO',   5, 0, 'ROLE',            'DIRECTOR',   '*',   'Direktur (melebihi anggaran)',             'OVER_BUDGET'),
 ('PR',   4, 0, 'ROLE',            'DIRECTOR',   '*',   'Direktur (melebihi anggaran)',             'OVER_BUDGET'),
 ('SO',   1, 0, 'ROLE',            'MANAGER',    'FIN', 'Manager Finance (kredit tertahan)',        'CREDIT_HOLD'),
 ('DO',   1, 0, 'ROLE',            'MANAGER',    'FIN', 'Manager Finance (kredit tertahan)',        'CREDIT_HOLD'),
 ('CRT',  1, 0, 'ROLE',            'SUPERVISOR', 'SCM', 'Supervisor Warehouse',                     NULL),
 ('GI',   1, 0, 'DIRECT_SUPERIOR', NULL,         NULL,  'Atasan peminta',                           NULL),
 ('MR',   1, 0, 'ROLE',            'SUPERVISOR', 'PRE', 'Supervisor Produksi',                      NULL),
 ('MRT',  1, 0, 'ROLE',            'SUPERVISOR', 'PRE', 'Supervisor Produksi',                      NULL),
 ('CNT',  1, 0, 'ROLE',            'SUPERVISOR', 'SCM', 'Supervisor Inventory Control',             NULL),
 ('CNT',  2, 0, 'ROLE',            'MANAGER',    'SCM', 'Manager SCM',                              NULL),
 ('SCR',  1, 0, 'ROLE',            'MANAGER',    'QMS', 'Manager QA',                               NULL),
 ('SCR',  2, 0, 'ROLE',            'MANAGER',    'FIN', 'Manager Finance',                          NULL),
 ('RTS',  1, 0, 'ROLE',            'MANAGER',    'PRC', 'Manager Procurement',                      NULL),
 ('BAST', 1, 0, 'DIRECT_SUPERIOR', NULL,         NULL,  'Atasan peminta jasa',                      NULL),
 ('LC',   1, 0, 'ROLE',            'MANAGER',    'FIN', 'Manager Finance',                          NULL),
 ('OHA',  1, 0, 'ROLE',            'MANAGER',    'FIN', 'Manager Finance',                          NULL),
 ('BOM',  1, 0, 'ROLE',            'MANAGER',    'RND', 'Manager RnD (formula)',                    NULL),
 ('BOM',  2, 0, 'ROLE',            'MANAGER',    'QMS', 'Manager QA',                               NULL),
 ('INV-AP', 3, 0, 'ROLE',          'MANAGER',    'PRC', 'Manager Procurement (selisih harga > toleransi)', 'PRICE_VARIANCE');

-- ---------- SARMUT otomatis dari transaksi M2 ----------
UPDATE hc.sarmut_kpi SET source = 'AUTO', auto_key = 'PRC_LT',   formula = 'Rata-rata hari dari PR disetujui sampai PO disetujui' WHERE code = 'PRC-LT';
UPDATE hc.sarmut_kpi SET source = 'AUTO', auto_key = 'PRC_OTD',  formula = '% baris PO diterima penuh paling lambat tanggal kebutuhan' WHERE code = 'PRC-OTD';
UPDATE hc.sarmut_kpi SET source = 'AUTO', auto_key = 'SCM_OTIF', formula = '% baris pesanan terkirim penuh paling lambat tanggal kirim' WHERE code = 'SCM-OTIF';
UPDATE hc.sarmut_kpi SET source = 'AUTO', auto_key = 'SCM_ACC',  formula = '% baris opname tanpa selisih' WHERE code = 'SCM-ACC';

-- ---------- Data contoh PRC: profil supplier, ASL, daftar harga ----------
INSERT INTO prc.supplier_profile (partner_id, legal_name, bank_name, bank_account_no, qualification_status, qualified_at, halal_cert_no, halal_cert_expiry)
SELECT p.id, p.name, 'Bank Demo', '10000' || p.id, 'QUALIFIED', DATE '2025-01-15', v.halal, v.exp FROM (VALUES
 ('SUP-0001', 'ID00110000001', DATE '2028-12-31'),
 ('SUP-0002', 'ID00110000002', DATE '2028-06-30'),
 ('SUP-0003', NULL,            NULL)
) AS v(code, halal, exp) JOIN sys.partner p ON p.code = v.code;

INSERT INTO prc.asl (item_id, partner_id, manufacturer, status, valid_until)
SELECT i.id, p.id, v.mfr, 'APPROVED', DATE '2027-12-31' FROM (VALUES
 ('RM-SIM-001', 'SUP-0001', 'Petani binaan Jawa Tengah'),
 ('RM-SIM-002', 'SUP-0001', 'Petani binaan Jawa Tengah'),
 ('RM-EKS-001', 'SUP-0001', 'Pabrik ekstrak (Demo)'),
 ('RM-EXC-001', 'SUP-0001', 'Produsen maltodekstrin (Demo)'),
 ('PM-KPS-000', 'SUP-0002', 'Produsen kapsul (Demo)'),
 ('PM-BTL-100', 'SUP-0003', 'Produsen botol (Demo)'),
 ('PM-DUS-060', 'SUP-0003', 'Percetakan (Demo)')
) AS v(item, sup, mfr) JOIN sys.item i ON i.code = v.item JOIN sys.partner p ON p.code = v.sup;

INSERT INTO prc.price_list (partner_id, item_id, contract_no, price, min_qty, lead_time_days, valid_from, valid_until)
SELECT p.id, i.id, 'KTR/' || v.sup || '/2026', v.price, 0, v.lt, DATE '2026-01-01', DATE '2026-12-31' FROM (VALUES
 ('RM-SIM-001', 'SUP-0001',  65000, 14),
 ('RM-SIM-002', 'SUP-0001',  70000, 14),
 ('RM-EKS-001', 'SUP-0001', 450000, 21),
 ('RM-EXC-001', 'SUP-0001',  28000, 7),
 ('PM-KPS-000', 'SUP-0002',     95, 30),
 ('PM-BTL-100', 'SUP-0003',   1800, 21),
 ('PM-DUS-060', 'SUP-0003',   1200, 14)
) AS v(item, sup, price, lt) JOIN sys.item i ON i.code = v.item JOIN sys.partner p ON p.code = v.sup;

-- ---------- Parameter stok (SCM-43) ----------
INSERT INTO scm.stock_param (plant_id, item_id, min_qty, max_qty, reorder_point, safety_stock, lead_time_days, moq, lot_size)
SELECT pl.id, i.id, v.mn, v.mx, v.rop, v.ss, v.lt, v.moq, v.lot FROM (VALUES
 ('RM-SIM-001',   50,   500,   80,   30, 14,   25,   25),
 ('RM-SIM-002',   30,   300,   50,   20, 14,   25,   25),
 ('RM-EKS-001',   10,   100,   15,    5, 21,    5,    5),
 ('RM-EXC-001',   50,   400,   60,   25,  7,   25,   25),
 ('PM-KPS-000', 50000, 600000, 80000, 30000, 30, 50000, 50000),
 ('PM-BTL-100', 2000, 30000, 4000, 1500, 21, 1000, 1000),
 ('PM-DUS-060', 1000, 15000, 2000,  800, 14, 1000, 1000),
 ('FG-KP500',    500,  5000, 800,   300,  7,    0,    0),
 ('FG-SRP100',   500,  5000, 800,   300,  7,    0,    0)
) AS v(item, mn, mx, rop, ss, lt, moq, lot)
JOIN sys.item i ON i.code = v.item JOIN sys.plant pl ON pl.code = 'P1';

-- ---------- BOM contoh berlaku (SCM-05; di M3 dikelola RnD lewat RND-04) ----------
INSERT INTO scm.bom (doc_no, plant_id, doc_date, status, item_id, revision, base_qty, std_hours, effective_from, notes, approved_at, posted_at)
SELECT v.no, pl.id, DATE '2026-01-02', 'POSTED', i.id, 1, v.base, v.hours, DATE '2026-01-02', 'BOM awal (data contoh)', now(), now()
FROM (VALUES ('BOM/P1/AWAL/00001', 'FG-KP500', 1000, 16), ('BOM/P1/AWAL/00002', 'FG-SRP100', 1000, 10)) AS v(no, item, base, hours)
JOIN sys.item i ON i.code = v.item JOIN sys.plant pl ON pl.code = 'P1';

INSERT INTO scm.bom_line (bom_id, line_no, component_item_id, qty, scrap_pct)
SELECT b.id, v.ln, c.id, v.qty, v.scrap FROM (VALUES
 ('BOM/P1/AWAL/00001', 1, 'RM-SIM-001', 15,    1),
 ('BOM/P1/AWAL/00001', 2, 'RM-EKS-001', 6,     1),
 ('BOM/P1/AWAL/00001', 3, 'RM-EXC-001', 9,     1),
 ('BOM/P1/AWAL/00001', 4, 'PM-KPS-000', 60000, 2),
 ('BOM/P1/AWAL/00001', 5, 'PM-DUS-060', 1000,  1),
 ('BOM/P1/AWAL/00002', 1, 'RM-SIM-002', 5,     1),
 ('BOM/P1/AWAL/00002', 2, 'RM-EXC-001', 2,     1),
 ('BOM/P1/AWAL/00002', 3, 'PM-BTL-100', 1000,  1)
) AS v(bom, ln, comp, qty, scrap)
JOIN scm.bom b ON b.doc_no = v.bom JOIN sys.item c ON c.code = v.comp;

INSERT INTO core.document_index (doc_type, doc_id, doc_no, app_code, menu_code, plant_id, status, summary, amount, doc_date)
SELECT 'BOM', b.id, b.doc_no, 'SCM', 'SCM-05', b.plant_id, b.status, i.name || ' · rev ' || b.revision, NULL, b.doc_date
FROM scm.bom b JOIN sys.item i ON i.id = b.item_id;

-- ---------- Tarif biaya produksi (FIN-52/53) ----------
INSERT INTO fin.cost_rate (plant_id, cost_center_id, valid_from, labor_rate, overhead_rate)
SELECT pl.id, cc.id, DATE '2026-01-01', v.labor, v.oh FROM (VALUES ('2100', 35000, 55000), ('2110', 35000, 50000)) AS v(cc, labor, oh)
JOIN sys.cost_center cc ON cc.code = v.cc JOIN sys.plant pl ON pl.code = 'P1';

-- ---------- Forecast contoh (SCM-03) ----------
INSERT INTO scm.forecast (plant_id, item_id, period, qty)
SELECT pl.id, i.id, to_char(d, 'YYYYMM'), v.qty
FROM (VALUES ('FG-KP500', 2000), ('FG-SRP100', 1500)) AS v(item, qty)
CROSS JOIN generate_series(DATE '2026-10-01', DATE '2027-03-01', INTERVAL '1 month') AS d
JOIN sys.item i ON i.code = v.item JOIN sys.plant pl ON pl.code = 'P1';
