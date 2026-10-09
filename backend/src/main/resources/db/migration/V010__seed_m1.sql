-- =====================================================================
-- Data referensi M1. Nilai tarif & batas mengikuti ketentuan yang berlaku saat disusun;
-- WAJIB diverifikasi tim HR/pajak sebelum go-live dan diperbarui lewat menu Pengaturan.
-- =====================================================================

-- Data gaji hanya terlihat oleh peran Payroll dan Direktur (PRD HC aturan 5).
UPDATE sys.role SET actions = actions || ',PAYROLL' WHERE code = 'DIRECTOR';

-- ---------- Akun tambahan ----------
INSERT INTO fin.account (code, name, type, normal_balance, parent_id, requires_cost_center)
SELECT v.code, v.name, v.type, v.nb, p.id, FALSE FROM (VALUES
 ('1230', 'Uang muka karyawan',          'ASSET', 'D', '1'),
 ('1240', 'Piutang karyawan (pinjaman)', 'ASSET', 'D', '1'),
 ('1504', 'Peralatan kantor & IT',       'ASSET', 'D', '1500'),
 ('7102', 'Laba/rugi pelepasan aset',    'REVENUE', 'C', '7')
) AS v(code, name, type, nb, parent) JOIN fin.account p ON p.code = v.parent;

-- ---------- Jenis dokumen baru & menu layanan mandiri ----------
INSERT INTO sys.doc_type (code, name, app_code, menu_code, prefix, requires_esign) VALUES
 ('PAYR', 'Payroll',                    'HC',  'HC-09',  'PYR',  FALSE),
 ('LOAN', 'Pinjaman & Kasbon',          'HC',  'HC-14',  'LOAN', FALSE),
 ('OB',   'On/Offboarding',             'HC',  'HC-05',  'OB',   FALSE),
 ('SRM',  'Input SARMUT Manual',        'HC',  'HC-13',  'SRM',  FALSE),
 ('RCV',  'Penerimaan Pembayaran',      'FIN', 'FIN-21', 'RCV',  FALSE),
 ('KK',   'Kas Kecil & Uang Muka Kerja','FIN', 'FIN-30', 'KK',   FALSE),
 ('AST',  'Kapitalisasi Aset Tetap',    'FIN', 'FIN-40', 'AST',  FALSE),
 ('DEP',  'Penyusutan Aset',            'FIN', 'FIN-40', 'DEP',  FALSE),
 ('BGT',  'Anggaran',                   'FIN', 'FIN-50', 'BGT',  FALSE),
 ('HP',   'Hasil Produksi',             'PRE', 'PRE-08', 'HP',   FALSE);
UPDATE sys.doc_type SET ess_menu_code = 'ESS-01' WHERE code = 'LV';
UPDATE sys.doc_type SET ess_menu_code = 'ESS-02' WHERE code = 'OT';
UPDATE sys.doc_type SET ess_menu_code = 'ESS-09' WHERE code = 'SPD';
UPDATE sys.doc_type SET menu_code = 'FIN-12' WHERE code = 'PAY';

-- ---------- Matriks approval tambahan (SYS-04) ----------
INSERT INTO sys.approval_rule (doc_type_code, level, min_amount, approver_type, approver_role, approver_app, label, condition_key) VALUES
 ('SPD',    2, 0,        'ROLE',            'MANAGER',    'HC',  'Manager HC (SPD luar negeri)',      'ABROAD'),
 ('PAYR',   1, 0,        'ROLE',            'MANAGER',    'HC',  'Manager HC',                        NULL),
 ('PAYR',   2, 0,        'ROLE',            'DIRECTOR',   '*',   'Direktur',                          NULL),
 ('LOAN',   1, 0,        'DIRECT_SUPERIOR', NULL,         NULL,  'Atasan langsung',                   NULL),
 ('LOAN',   2, 0,        'ROLE',            'MANAGER',    'HC',  'Manager HC',                        NULL),
 ('OB',     1, 0,        'ROLE',            'MANAGER',    'HC',  'Manager HC',                        NULL),
 ('SRM',    1, 0,        'DIRECT_SUPERIOR', NULL,         NULL,  'Atasan penginput',                  NULL),
 ('INV-AP', 1, 0,        'ROLE',            'MANAGER',    'FIN', 'Manager Finance (verifikasi)',      NULL),
 ('INV-AP', 2, 0,        'ROLE',            'DIRECTOR',   '*',   'Direktur (melebihi anggaran)',      'OVER_BUDGET'),
 ('INV-AR', 1, 0,        'ROLE',            'MANAGER',    'FIN', 'Manager Finance (kredit tertahan)', 'CREDIT_HOLD'),
 ('KK',     1, 0,        'DIRECT_SUPERIOR', NULL,         NULL,  'Atasan pemohon',                    NULL),
 ('KK',     2, 5000000,  'ROLE',            'MANAGER',    'FIN', 'Manager Finance',                   NULL),
 ('KK',     3, 0,        'ROLE',            'DIRECTOR',   '*',   'Direktur (melebihi anggaran)',      'OVER_BUDGET'),
 ('AST',    1, 0,        'ROLE',            'MANAGER',    'FIN', 'Manager Finance',                   NULL),
 ('BGT',    1, 0,        'ROLE',            'MANAGER',    'FIN', 'Manager Finance',                   NULL),
 ('BGT',    2, 0,        'ROLE',            'DIRECTOR',   '*',   'Direktur',                          NULL),
 ('WO',     1, 0,        'ROLE',            'MANAGER',    'PRE', 'Manager Produksi (rilis ke lini)',  NULL),
 ('HP',     1, 0,        'ROLE',            'SUPERVISOR', 'PRE', 'Supervisor Produksi (pemeriksa)',   NULL);

-- ---------- HC: jenis cuti ----------
INSERT INTO hc.leave_type (code, name, attendance_status, paid, deducts_annual, max_days, requires_attachment) VALUES
 ('CT',  'Cuti tahunan',                        'CUTI',  TRUE,  TRUE,  NULL, FALSE),
 ('SKT', 'Sakit dengan surat dokter',           'SAKIT', TRUE,  FALSE, NULL, TRUE),
 ('IZN', 'Izin tidak dibayar',                  'IZIN',  FALSE, FALSE, NULL, FALSE),
 ('CMN', 'Cuti menikah',                        'CUTI',  TRUE,  FALSE, 3,    TRUE),
 ('CMA', 'Cuti melahirkan',                     'CUTI',  TRUE,  FALSE, 90,   TRUE),
 ('CDK', 'Cuti duka (keluarga inti meninggal)', 'CUTI',  TRUE,  FALSE, 2,    FALSE);

-- ---------- HC: komponen gaji ----------
INSERT INTO hc.salary_component (code, name, kind, fixed, taxable, account_code, seq, system) VALUES
 ('GAPOK',      'Gaji pokok',                      'EARNING',   TRUE,  TRUE, '6101', 10,  FALSE),
 ('TJAB',       'Tunjangan jabatan',               'EARNING',   TRUE,  TRUE, '6101', 20,  FALSE),
 ('TTRANS',     'Tunjangan transport',             'EARNING',   FALSE, TRUE, '6101', 30,  FALSE),
 ('TMAKAN',     'Tunjangan makan',                 'EARNING',   FALSE, TRUE, '6101', 40,  FALSE),
 ('LEMBUR',     'Upah lembur',                     'EARNING',   FALSE, TRUE, '6102', 50,  TRUE),
 ('POT_ALPA',   'Potongan tidak hadir',            'EARNING',   FALSE, TRUE, '6101', 60,  TRUE),
 ('BPJSKES_E',  'Iuran BPJS Kesehatan (karyawan)', 'DEDUCTION', FALSE, FALSE,'2107', 70,  TRUE),
 ('JHT_E',      'Iuran JHT (karyawan)',            'DEDUCTION', FALSE, FALSE,'2107', 71,  TRUE),
 ('JP_E',       'Iuran Jaminan Pensiun (karyawan)','DEDUCTION', FALSE, FALSE,'2107', 72,  TRUE),
 ('PPH21',      'PPh 21',                          'DEDUCTION', FALSE, FALSE,'2104', 80,  TRUE),
 ('PINJAMAN',   'Cicilan pinjaman/kasbon',         'DEDUCTION', FALSE, FALSE,'1240', 90,  TRUE),
 ('BPJSKES_P',  'BPJS Kesehatan (perusahaan)',     'EMPLOYER',  FALSE, TRUE, '6103', 100, TRUE),
 ('JHT_P',      'JHT (perusahaan)',                'EMPLOYER',  FALSE, FALSE,'6103', 101, TRUE),
 ('JP_P',       'Jaminan Pensiun (perusahaan)',    'EMPLOYER',  FALSE, FALSE,'6103', 102, TRUE),
 ('JKK',        'Jaminan Kecelakaan Kerja',        'EMPLOYER',  FALSE, TRUE, '6103', 103, TRUE),
 ('JKM',        'Jaminan Kematian',                'EMPLOYER',  FALSE, TRUE, '6103', 104, TRUE);

-- ---------- HC: parameter payroll ----------
INSERT INTO hc.payroll_param (key, value, description) VALUES
 ('BPJSKES_RATE_ER', '0.04',     'BPJS Kesehatan ditanggung perusahaan (4%)'),
 ('BPJSKES_RATE_EE', '0.01',     'BPJS Kesehatan ditanggung karyawan (1%)'),
 ('BPJSKES_CAP',     '12000000', 'Batas atas upah dasar iuran BPJS Kesehatan'),
 ('JHT_RATE_ER',     '0.037',    'JHT perusahaan (3,7%)'),
 ('JHT_RATE_EE',     '0.02',     'JHT karyawan (2%)'),
 ('JP_RATE_ER',      '0.02',     'Jaminan Pensiun perusahaan (2%)'),
 ('JP_RATE_EE',      '0.01',     'Jaminan Pensiun karyawan (1%)'),
 ('JP_CAP',          '10547400', 'Batas upah JP — nilai 2025; PERBARUI sesuai pengumuman BPJS Ketenagakerjaan tahun berjalan'),
 ('JKK_RATE',        '0.0024',   'JKK sesuai tingkat risiko (0,24%–1,74%); sesuaikan kelompok risiko perusahaan'),
 ('JKM_RATE',        '0.003',    'JKM (0,3%)'),
 ('OT_DIVISOR',      '173',      'Upah sejam = 1/173 × upah sebulan (PP 35/2021)'),
 ('JOB_EXPENSE_RATE','0.05',     'Biaya jabatan 5% (perhitungan tahunan PPh 21)'),
 ('JOB_EXPENSE_MAX', '6000000',  'Biaya jabatan maksimum setahun'),
 ('DEDUCT_ALPA',     'true',     'Potong upah untuk hari ALPA (tidak hadir tanpa keterangan)'),
 ('ANNUAL_LEAVE',    '12',       'Hak cuti tahunan bawaan (hari)');

-- ---------- HC: PTKP & kategori TER ----------
INSERT INTO hc.ptkp (status, amount, ter_category) VALUES
 ('TK/0', 54000000, 'A'), ('TK/1', 58500000, 'A'), ('TK/2', 63000000, 'B'), ('TK/3', 67500000, 'B'),
 ('K/0',  58500000, 'A'), ('K/1',  63000000, 'B'), ('K/2',  67500000, 'B'), ('K/3',  72000000, 'C');

-- TER kategori A (Lampiran PMK 168/2023). Kategori B & C diisi tim pajak di HC-99 sebelum go-live;
-- payroll menolak karyawan berkategori yang tabelnya belum lengkap.
INSERT INTO hc.pph21_ter (category, upper_limit, rate) VALUES
 ('A', 5400000, 0), ('A', 5650000, 0.0025), ('A', 5950000, 0.005), ('A', 6300000, 0.0075), ('A', 6750000, 0.01),
 ('A', 7500000, 0.0125), ('A', 8550000, 0.015), ('A', 9650000, 0.0175), ('A', 10050000, 0.02), ('A', 10350000, 0.0225),
 ('A', 10700000, 0.025), ('A', 11050000, 0.03), ('A', 11600000, 0.035), ('A', 12500000, 0.04), ('A', 13750000, 0.05),
 ('A', 15100000, 0.06), ('A', 16950000, 0.07), ('A', 19750000, 0.08), ('A', 24150000, 0.09), ('A', 26450000, 0.10),
 ('A', 28000000, 0.11), ('A', 30050000, 0.12), ('A', 32400000, 0.13), ('A', 35400000, 0.14), ('A', 39100000, 0.15),
 ('A', 43850000, 0.16), ('A', 47800000, 0.17), ('A', 51400000, 0.18), ('A', 56300000, 0.19), ('A', 62200000, 0.20),
 ('A', 68600000, 0.21), ('A', 77500000, 0.22), ('A', 89000000, 0.23), ('A', 103000000, 0.24), ('A', 125000000, 0.25),
 ('A', 157000000, 0.26), ('A', 206000000, 0.27), ('A', 337000000, 0.28), ('A', 454000000, 0.29), ('A', 550000000, 0.30),
 ('A', 695000000, 0.31), ('A', 910000000, 0.32), ('A', 1400000000, 0.33), ('A', NULL, 0.34);

-- ---------- HC: data payroll karyawan demo (semua kategori TER A) ----------
UPDATE hc.employee e SET ptkp_status = v.ptkp, bank_name = 'Bank Demo', bank_account_no = '000' || substr(e.nik, 2),
       bank_account_name = e.name, gender = 'L', default_shift_id = (SELECT id FROM sys.shift WHERE code = 'NS')
FROM (VALUES ('E0001','K/0'),('E0002','K/0'),('E0003','K/0'),('E0010','K/0'),('E0011','TK/1'),('E0012','TK/0'),
             ('E0020','K/0'),('E0021','TK/0'),('E0030','K/0'),('E0031','TK/1'),('E0032','TK/0'),('E0040','K/0'),
             ('E0041','TK/0'),('E0050','K/0'),('E0051','TK/0'),('E0060','K/0'),('E0061','TK/0'),('E0070','K/0'),
             ('E0071','TK/0'),('E0080','K/0'),('E0090','TK/0'),('E0099','TK/0')) AS v(nik, ptkp)
WHERE e.nik = v.nik;

INSERT INTO hc.employee_salary (employee_id, component_id, amount)
SELECT e.id, c.id, CASE
        WHEN p.code LIKE 'DIR-%' THEN CASE c.code WHEN 'GAPOK' THEN 35000000 WHEN 'TJAB' THEN 10000000 WHEN 'TTRANS' THEN 2000000 ELSE 1500000 END
        WHEN p.code LIKE 'MGR-%' THEN CASE c.code WHEN 'GAPOK' THEN 18000000 WHEN 'TJAB' THEN 4000000 WHEN 'TTRANS' THEN 1000000 ELSE 900000 END
        WHEN p.code LIKE 'SPV-%' OR p.code IN ('QA-REL', 'AUD-INT', 'ADM-SYS') THEN CASE c.code WHEN 'GAPOK' THEN 9500000 WHEN 'TJAB' THEN 1500000 WHEN 'TTRANS' THEN 750000 ELSE 660000 END
        ELSE CASE c.code WHEN 'GAPOK' THEN 5600000 WHEN 'TJAB' THEN 0 WHEN 'TTRANS' THEN 500000 ELSE 550000 END END
FROM hc.employee e JOIN hc.position p ON p.id = e.position_id
CROSS JOIN hc.salary_component c WHERE c.code IN ('GAPOK', 'TJAB', 'TTRANS', 'TMAKAN');

-- ---------- HC: KPI SARMUT (PRD §16) ----------
INSERT INTO hc.sarmut_kpi (code, name, app_code, unit, direction, target, weight, source, auto_key, formula) VALUES
 ('PRE-OUT',   'Pencapaian output',            'PRE', '%',    'HIGHER', 95,  2, 'AUTO',   'PRE_OUTPUT',     'Qty hasil / qty rencana WO selesai'),
 ('PRE-YIELD', 'Yield batch rata-rata',        'PRE', '%',    'HIGHER', 97,  2, 'AUTO',   'PRE_YIELD',      'Rata-rata yield batch selesai'),
 ('PRE-OEE',   'OEE lini',                     'PRE', '%',    'HIGHER', 75,  1, 'MANUAL', NULL,             'Ketersediaan × Kinerja × Mutu (otomatis di M3)'),
 ('PRE-PM',    'Kepatuhan PM',                 'PRE', '%',    'HIGHER', 95,  1, 'MANUAL', NULL,             'PM tepat waktu / PM terjadwal (otomatis di M3)'),
 ('PRC-LT',    'Lead time PR → PO',            'PRC', 'hari', 'LOWER',  3,   1, 'MANUAL', NULL,             'Otomatis di M2'),
 ('PRC-OTD',   'Ketepatan kedatangan supplier','PRC', '%',    'HIGHER', 95,  1, 'MANUAL', NULL,             'Otomatis di M2'),
 ('FIN-CLS',   'Hari closing',                 'FIN', 'hari', 'LOWER',  5,   1, 'AUTO',   'FIN_CLOSING_DAYS','Hari kerja dari akhir bulan sampai GL dikunci (FIN-70)'),
 ('FIN-BGT',   'Realisasi anggaran',           'FIN', '%',    'LOWER',  100, 1, 'AUTO',   'FIN_BUDGET',     'Realisasi beban / anggaran periode'),
 ('FIN-DSO',   'DSO',                          'FIN', 'hari', 'LOWER',  45,  1, 'AUTO',   'FIN_DSO',        'Piutang terbuka / penjualan harian'),
 ('GA-SLA',    'SLA permintaan',               'GA',  '%',    'HIGHER', 90,  1, 'MANUAL', NULL,             'Otomatis di M3'),
 ('GA-K3',     'Insiden K3',                   'GA',  'kali', 'LOWER',  0,   1, 'MANUAL', NULL,             'Otomatis di M3'),
 ('HC-ATT',    'Tingkat kehadiran',            'HC',  '%',    'HIGHER', 97,  1, 'AUTO',   'HC_ATTENDANCE',  'Hari hadir / hari kerja tercatat'),
 ('HC-TO',     'Turnover karyawan',            'HC',  '%',    'LOWER',  2,   1, 'AUTO',   'HC_TURNOVER',    'Karyawan keluar / rata-rata karyawan'),
 ('HC-TRN',    'Kepatuhan training wajib',     'HC',  '%',    'HIGHER', 95,  1, 'MANUAL', NULL,             'Otomatis di M3'),
 ('QMS-RFT',   'Right first time',             'QMS', '%',    'HIGHER', 95,  1, 'MANUAL', NULL,             'Otomatis di M3'),
 ('QMS-CAPA',  'CAPA tepat waktu',             'QMS', '%',    'HIGHER', 90,  1, 'MANUAL', NULL,             'Otomatis di M3'),
 ('SCM-OTIF',  'OTIF pengiriman',              'SCM', '%',    'HIGHER', 95,  1, 'MANUAL', NULL,             'Otomatis di M2'),
 ('SCM-ACC',   'Akurasi stok',                 'SCM', '%',    'HIGHER', 98,  1, 'MANUAL', NULL,             'Otomatis di M2'),
 ('RND-GATE',  'Proyek tepat waktu',           'RND', '%',    'HIGHER', 80,  1, 'MANUAL', NULL,             'Otomatis di M3');

-- ---------- FIN: parameter, rekening, kategori aset ----------
INSERT INTO fin.fin_param (key, value, description) VALUES
 ('PPN_RATE',         '0.12',  'Tarif PPN'),
 ('PPN_DPP_NUM',      '11',    'DPP nilai lain = 11/12 × harga (PMK 131/2024) — pembilang'),
 ('PPN_DPP_DEN',      '12',    'DPP nilai lain — penyebut'),
 ('AR_OVERDUE_DAYS',  '60',    'Faktur tertahan bila customer punya piutang lewat N hari (PRD FIN aturan 6)'),
 ('BUDGET_CONTROL',   'WARN',  'OFF / WARN (butuh approval Direktur) / BLOCK (ditolak)'),
 ('BANK_MATCH_DAYS',  '3',     'Toleransi tanggal pencocokan mutasi bank (hari)');

INSERT INTO fin.bank_account (code, name, kind, bank_name, account_no, gl_account_id, plant_id)
SELECT v.code, v.name, v.kind, v.bank, v.no, a.id, p.id FROM (VALUES
 ('KAS-P1',  'Kas kecil Plant 1',  'KAS',  NULL,        NULL,         '1101'),
 ('BANK-OP', 'Bank operasional',   'BANK', 'Bank Demo', '0000000001', '1111')
) AS v(code, name, kind, bank, no, acc) JOIN fin.account a ON a.code = v.acc JOIN sys.plant p ON p.code = 'P1';

INSERT INTO fin.asset_category (code, name, asset_account_id, accum_account_id, expense_account_id, useful_life_months, method, fiscal_group)
SELECT v.code, v.name, a.id, acc.id, ex.id, v.life, 'SL', v.fg FROM (VALUES
 ('MESIN', 'Mesin & peralatan produksi', '1501', 96,  'KEL2'),
 ('KEND',  'Kendaraan',                  '1503', 96,  'KEL2'),
 ('IT',    'Peralatan kantor & IT',      '1504', 48,  'KEL1'),
 ('BGN',   'Bangunan permanen',          '1502', 240, 'BANGUNAN_P')
) AS v(code, name, acct, life, fg)
JOIN fin.account a ON a.code = v.acct JOIN fin.account acc ON acc.code = '1509' JOIN fin.account ex ON ex.code = '6203';

-- ---------- PRE: lini, parameter produk, alasan reject, kualifikasi operator demo ----------
INSERT INTO pre.line (code, name, plant_id, cost_center_id, process_code, capacity_per_shift, capacity_uom)
SELECT v.code, v.name, p.id, cc.id, v.proc, v.cap, v.uom FROM (VALUES
 ('LN-01', 'Lini 1 · Sirup',  '2100', 'SIRUP',   4000, 'pcs'),
 ('LN-02', 'Lini 2 · Kapsul', '2110', 'FILLING', 800,  'box')
) AS v(code, name, cc, proc, cap, uom) JOIN sys.plant p ON p.code = 'P1' JOIN sys.cost_center cc ON cc.code = v.cc;

INSERT INTO pre.product_param (item_id, batch_prefix, std_batch_size, min_yield_pct, default_line_id)
SELECT i.id, v.prefix, v.size, v.yield, l.id FROM (VALUES
 ('FG-KP500', 'KP', 1000, 97, 'LN-02'),
 ('FG-SRP100', 'SR', 5000, 96, 'LN-01')
) AS v(item, prefix, size, yield, line) JOIN sys.item i ON i.code = v.item JOIN pre.line l ON l.code = v.line;

INSERT INTO pre.reject_reason (code, name, category) VALUES
 ('R01', 'Berat/isi di luar spesifikasi', 'PROSES'), ('R02', 'Kemasan rusak/cacat', 'BAHAN'),
 ('R03', 'Mesin macet', 'MESIN'), ('R04', 'Kontaminasi/kotoran', 'PROSES'), ('R05', 'Kesalahan operator', 'MANUSIA');

INSERT INTO hc.qualification (employee_id, process_code, line_id, valid_from, valid_until, basis, note)
SELECT e.id, 'FILLING', l.id, CURRENT_DATE - 180, CURRENT_DATE + 365, 'ASESMEN', 'Data demo'
FROM hc.employee e JOIN pre.line l ON l.code = 'LN-02' WHERE e.nik IN ('E0031', 'E0032');
