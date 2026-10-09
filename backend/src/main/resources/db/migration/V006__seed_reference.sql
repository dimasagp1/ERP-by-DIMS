-- =====================================================================
-- Data referensi awal. Semua nilai bisa diubah lewat menu Pengaturan.
-- Nilai batas approval mengikuti contoh PRD §13 (masih asumsi awal).
-- =====================================================================

-- ---------- Peran standar (PRD §2) ----------
INSERT INTO sys.role (code, name, description, view_scope, actions, builtin) VALUES
 ('OPERATOR',   'Operator / Staf',          'Buat & ajukan dokumen; melihat dokumen sendiri',                  'OWN',        'VIEW,CREATE,EDIT,SUBMIT', TRUE),
 ('SUPERVISOR', 'Supervisor',               'Approval level 1; melihat dokumen seksinya',                      'SECTION',    'VIEW,CREATE,EDIT,SUBMIT,APPROVE,EXPORT', TRUE),
 ('MANAGER',    'Manager',                  'Approval level 2, posting & pembatalan dengan alasan',            'DEPARTMENT', 'VIEW,CREATE,EDIT,SUBMIT,APPROVE,POST,CANCEL,EXPORT', TRUE),
 ('DIRECTOR',   'Kepala Divisi / Direktur', 'Approval level 3 di atas batas nilai; pembatalan',                'ALL',        'VIEW,APPROVE,CANCEL,EXPORT', TRUE),
 ('QA_RELEASE', 'QA Release Officer',       'Release / reject batch & bahan (QMS, SCM, PRE)',                  'ALL',        'VIEW,RELEASE,EXPORT', TRUE),
 ('AUDITOR',    'Auditor',                  'Baca saja semua aplikasi, termasuk audit trail',                  'ALL',        'VIEW,EXPORT,AUDIT', TRUE),
 ('ADMIN',      'Admin Sistem',             'Master & konfigurasi SYS, bukan transaksi',                       'ALL',        'VIEW,CREATE,EDIT,EXPORT,ADMIN,AUDIT', TRUE),
 ('PAYROLL',    'Payroll',                  'Akses data gaji (HC-09); hanya Payroll & Direktur',               'DEPARTMENT', 'VIEW,CREATE,EDIT,SUBMIT,POST,EXPORT,PAYROLL', TRUE);

-- ---------- Perusahaan & plant (SYS-01) ----------
INSERT INTO sys.company (code, name) VALUES ('HBT', 'Herbatech');
INSERT INTO sys.plant (company_id, code, name)
SELECT id, 'P1', 'Plant 1' FROM sys.company WHERE code = 'HBT'
UNION ALL SELECT id, 'P2', 'Plant 2' FROM sys.company WHERE code = 'HBT';

-- ---------- Departemen (SYS-02) ----------
INSERT INTO sys.department (code, name, app_code) VALUES
 ('DIR', 'Direksi', NULL),
 ('PRE', 'Produksi & Engineering', 'PRE'),
 ('PRC', 'Procurement', 'PRC'),
 ('FIN', 'Finance & Tax', 'FIN'),
 ('GA',  'General Affairs', 'GA'),
 ('HC',  'Human Capital', 'HC'),
 ('QMS', 'Quality (QA & QC)', 'QMS'),
 ('SCM', 'Supply Chain', 'SCM'),
 ('RND', 'Research & Development', 'RND');
INSERT INTO sys.department (code, name, parent_id, app_code)
SELECT v.code, v.name, d.id, d.app_code FROM (VALUES
 ('PRE-PRD', 'Produksi', 'PRE'), ('PRE-ENG', 'Engineering', 'PRE'),
 ('QMS-QA', 'Quality Assurance', 'QMS'), ('QMS-QC', 'Quality Control', 'QMS'),
 ('SCM-PPIC', 'PPIC', 'SCM'), ('SCM-WH', 'Warehouse', 'SCM'), ('SCM-IC', 'Inventory Control', 'SCM'),
 ('FIN-ACC', 'Akuntansi', 'FIN'), ('FIN-TAX', 'Pajak', 'FIN')
) AS v(code, name, parent) JOIN sys.department d ON d.code = v.parent;

INSERT INTO sys.cost_center (code, name, department_id, plant_id, production)
SELECT v.code, v.name, d.id, p.id, v.prod FROM (VALUES
 ('1100', 'Direksi',              'DIR',      FALSE),
 ('2100', 'Produksi Lini 1',      'PRE-PRD',  TRUE),
 ('2110', 'Produksi Lini 2',      'PRE-PRD',  TRUE),
 ('2200', 'Engineering',          'PRE-ENG',  TRUE),
 ('3100', 'Procurement',          'PRC',      FALSE),
 ('4100', 'Finance & Accounting', 'FIN',      FALSE),
 ('5100', 'General Affairs',      'GA',       FALSE),
 ('6100', 'Human Capital',        'HC',       FALSE),
 ('7100', 'Quality Assurance',    'QMS-QA',   FALSE),
 ('7200', 'Quality Control',      'QMS-QC',   TRUE),
 ('8100', 'PPIC',                 'SCM-PPIC', FALSE),
 ('8200', 'Warehouse',            'SCM-WH',   FALSE),
 ('9100', 'Research & Development','RND',     FALSE)
) AS v(code, name, dept, prod)
JOIN sys.department d ON d.code = v.dept
JOIN sys.plant p ON p.code = 'P1';

-- ---------- Posisi & karyawan (HC-02, HC-03) ----------
INSERT INTO hc.position (code, title, department_id)
SELECT v.code, v.title, d.id FROM (VALUES
 ('DIR-UT',     'Direktur Utama',              'DIR'),
 ('DIR-FIN',    'Direktur Keuangan',           'DIR'),
 ('DIR-OPS',    'Direktur Operasional',        'DIR'),
 ('MGR-FIN',    'Finance Manager',             'FIN'),
 ('SPV-ACC',    'Supervisor Akuntansi',        'FIN-ACC'),
 ('STF-ACC',    'Staf Akuntansi',              'FIN-ACC'),
 ('MGR-PRC',    'Procurement Manager',         'PRC'),
 ('STF-PRC',    'Staf Pembelian',              'PRC'),
 ('MGR-PRE',    'Production Manager',          'PRE'),
 ('SPV-L2',     'Supervisor Produksi Lini 2',  'PRE-PRD'),
 ('OPR-L2',     'Operator Produksi Lini 2',    'PRE-PRD'),
 ('MGR-QA',     'QA Manager',                  'QMS'),
 ('QA-REL',     'QA Release Officer',          'QMS-QA'),
 ('MGR-SCM',    'Supply Chain Manager',        'SCM'),
 ('SPV-IC',     'Supervisor Inventory Control','SCM-IC'),
 ('MGR-HC',     'Human Capital Manager',       'HC'),
 ('STF-PAY',    'Staf Payroll',                'HC'),
 ('MGR-GA',     'General Affairs Manager',     'GA'),
 ('STF-GA',     'Staf General Affairs',        'GA'),
 ('MGR-RND',    'RnD Manager',                 'RND'),
 ('AUD-INT',    'Auditor Internal',            'DIR'),
 ('ADM-SYS',    'Admin Sistem',                'FIN')
) AS v(code, title, dept) JOIN sys.department d ON d.code = v.dept;

UPDATE hc.position p SET reports_to_id = s.id
FROM (VALUES
 ('DIR-FIN','DIR-UT'), ('DIR-OPS','DIR-UT'), ('MGR-FIN','DIR-FIN'), ('SPV-ACC','MGR-FIN'), ('STF-ACC','SPV-ACC'),
 ('MGR-PRC','DIR-FIN'), ('STF-PRC','MGR-PRC'), ('MGR-PRE','DIR-OPS'), ('SPV-L2','MGR-PRE'), ('OPR-L2','SPV-L2'),
 ('MGR-QA','DIR-OPS'), ('QA-REL','MGR-QA'), ('MGR-SCM','DIR-OPS'), ('SPV-IC','MGR-SCM'), ('MGR-HC','DIR-UT'),
 ('STF-PAY','MGR-HC'), ('MGR-GA','DIR-FIN'), ('STF-GA','MGR-GA'), ('MGR-RND','DIR-OPS'), ('AUD-INT','DIR-UT'),
 ('ADM-SYS','DIR-FIN')
) AS m(child, parent)
JOIN hc.position s ON s.code = m.parent
WHERE p.code = m.child;

-- Satu karyawan contoh per posisi (data demo; ganti dengan data HC sebenarnya).
INSERT INTO hc.employee (nik, name, position_id, department_id, cost_center_id, plant_id, join_date)
SELECT v.nik, v.name, p.id, p.department_id, cc.id, pl.id, DATE '2020-01-02' FROM (VALUES
 ('E0001', 'Direktur Utama (Demo)',      'DIR-UT',  '1100'),
 ('E0002', 'Direktur Keuangan (Demo)',   'DIR-FIN', '1100'),
 ('E0003', 'Direktur Operasional (Demo)','DIR-OPS', '1100'),
 ('E0010', 'Finance Manager (Demo)',     'MGR-FIN', '4100'),
 ('E0011', 'Supervisor Akuntansi (Demo)','SPV-ACC', '4100'),
 ('E0012', 'Staf Akuntansi (Demo)',      'STF-ACC', '4100'),
 ('E0020', 'Procurement Manager (Demo)', 'MGR-PRC', '3100'),
 ('E0021', 'Staf Pembelian (Demo)',      'STF-PRC', '3100'),
 ('E0030', 'Production Manager (Demo)',  'MGR-PRE', '2110'),
 ('E0031', 'Supervisor Lini 2 (Demo)',   'SPV-L2',  '2110'),
 ('E0032', 'Operator Lini 2 (Demo)',     'OPR-L2',  '2110'),
 ('E0040', 'QA Manager (Demo)',          'MGR-QA',  '7100'),
 ('E0041', 'QA Release Officer (Demo)',  'QA-REL',  '7100'),
 ('E0050', 'Supply Chain Manager (Demo)','MGR-SCM', '8100'),
 ('E0051', 'Supervisor IC (Demo)',       'SPV-IC',  '8200'),
 ('E0060', 'HC Manager (Demo)',          'MGR-HC',  '6100'),
 ('E0061', 'Staf Payroll (Demo)',        'STF-PAY', '6100'),
 ('E0070', 'GA Manager (Demo)',          'MGR-GA',  '5100'),
 ('E0071', 'Staf GA (Demo)',             'STF-GA',  '5100'),
 ('E0080', 'RnD Manager (Demo)',         'MGR-RND', '9100'),
 ('E0090', 'Auditor Internal (Demo)',    'AUD-INT', '1100'),
 ('E0099', 'Admin Sistem (Demo)',        'ADM-SYS', '4100')
) AS v(nik, name, pos, cc)
JOIN hc.position p ON p.code = v.pos
JOIN sys.cost_center cc ON cc.code = v.cc
JOIN sys.plant pl ON pl.code = 'P1';

-- ---------- Jenis dokumen & penomoran (SYS-05) ----------
INSERT INTO sys.doc_type (code, name, app_code, menu_code, prefix, requires_esign) VALUES
 ('JV',     'Jurnal Umum',                 'FIN', 'FIN-03', 'JV',     FALSE),
 ('INV-AP', 'Faktur Supplier',             'FIN', 'FIN-10', 'INV-AP', FALSE),
 ('INV-AR', 'Faktur Penjualan',            'FIN', 'FIN-20', 'INV-AR', FALSE),
 ('PAY',    'Pembayaran Hutang',           'FIN', 'FIN-12', 'PAY',    FALSE),
 ('PR',     'Purchase Requisition',        'PRC', 'PRC-02', 'PR',     FALSE),
 ('RFQ',    'Permintaan Penawaran',        'PRC', 'PRC-05', 'RFQ',    FALSE),
 ('PO',     'Purchase Order',              'PRC', 'PRC-07', 'PO',     FALSE),
 ('RTS',    'Retur ke Supplier',           'PRC', 'PRC-11', 'RTS',    FALSE),
 ('SO',     'Pesanan Pelanggan',           'SCM', 'SCM-02', 'SO',     FALSE),
 ('GR',     'Penerimaan Barang',           'SCM', 'SCM-20', 'GR',     FALSE),
 ('DO',     'Surat Jalan',                 'SCM', 'SCM-26', 'SJ',     FALSE),
 ('ADJ',    'Penyesuaian Stok',            'SCM', 'SCM-42', 'ADJ',    FALSE),
 ('CNT',    'Stock Opname',                'SCM', 'SCM-41', 'CNT',    FALSE),
 ('WO',     'Work Order Produksi',         'PRE', 'PRE-02', 'WO',     FALSE),
 ('BMR',    'Batch Record',                'PRE', 'PRE-03', 'BMR',    TRUE),
 ('WRQ',    'Work Request',                'PRE', 'PRE-22', 'WRQ',    FALSE),
 ('MNT',    'WO Maintenance',              'PRE', 'PRE-23', 'MNT',    FALSE),
 ('CAL',    'Kalibrasi',                   'PRE', 'PRE-25', 'CAL',    TRUE),
 ('DEV',    'Deviasi',                     'QMS', 'QMS-04', 'DEV',    TRUE),
 ('CAPA',   'CAPA',                        'QMS', 'QMS-05', 'CAPA',   TRUE),
 ('CC',     'Change Control',              'QMS', 'QMS-03', 'CC',     TRUE),
 ('BR',     'Pelulusan Batch',             'QMS', 'QMS-09', 'BR',     TRUE),
 ('LV',     'Cuti & Izin',                 'HC',  'HC-08',  'LV',     FALSE),
 ('OT',     'Lembur',                      'HC',  'HC-08',  'OT',     FALSE),
 ('SPD',    'Perjalanan Dinas',            'HC',  'HC-08',  'SPD',    FALSE),
 ('TRN',    'Training',                    'HC',  'HC-11',  'TRN',    FALSE),
 ('ATK',    'Permintaan ATK',              'GA',  'GA-03',  'ATK',    FALSE),
 ('VHB',    'Booking Kendaraan',           'GA',  'GA-04',  'VHB',    FALSE),
 ('GP',     'Gate Pass',                   'GA',  'GA-08',  'GP',     FALSE),
 ('K3',     'Laporan Insiden K3',          'GA',  'GA-13',  'K3',     FALSE),
 ('PRJ',    'Proyek RnD',                  'RND', 'RND-02', 'PRJ',    FALSE),
 ('FML',    'Formula',                     'RND', 'RND-03', 'FML',    FALSE),
 ('ART',    'Artwork',                     'RND', 'RND-10', 'ART',    FALSE),
 ('REG',    'Registrasi Produk',           'RND', 'RND-09', 'REG',    FALSE);

-- ---------- Matriks approval (SYS-04, PRD §13) ----------
INSERT INTO sys.approval_rule (doc_type_code, level, min_amount, approver_type, approver_role, approver_app, label) VALUES
 ('PR',  1, 0,          'DIRECT_SUPERIOR', NULL,         NULL,  'Atasan peminta'),
 ('PR',  2, 50000000,   'ROLE',            'MANAGER',    'PRC', 'Manager Procurement'),
 ('PR',  3, 250000000,  'ROLE',            'DIRECTOR',   '*',   'Direktur'),
 ('PO',  1, 0,          'DIRECT_SUPERIOR', NULL,         NULL,  'Atasan pembuat'),
 ('PO',  2, 50000000,   'ROLE',            'MANAGER',    'PRC', 'Manager Procurement'),
 ('PO',  3, 250000000,  'ROLE',            'DIRECTOR',   '*',   'Direktur'),
 ('ADJ', 1, 0,          'ROLE',            'SUPERVISOR', 'SCM', 'Supervisor Inventory Control'),
 ('ADJ', 2, 5000000,    'ROLE',            'MANAGER',    'SCM', 'Manager SCM'),
 ('ADJ', 3, 5000000,    'ROLE',            'MANAGER',    'FIN', 'Manager Finance'),
 ('PAY', 1, 0,          'ROLE',            'MANAGER',    'FIN', 'Manager Finance'),
 ('PAY', 2, 100000000,  'ROLE',            'DIRECTOR',   '*',   'Direktur Keuangan'),
 ('JV',  1, 0,          'DIRECT_SUPERIOR', NULL,         NULL,  'Atasan pembuat jurnal'),
 ('JV',  2, 100000000,  'ROLE',            'MANAGER',    'FIN', 'Manager Finance'),
 ('DEV', 1, 0,          'ROLE',            'MANAGER',    'QMS', 'Manager QA'),
 ('CC',  1, 0,          'DIRECT_SUPERIOR', NULL,         NULL,  'Pemilik proses'),
 ('CC',  2, 0,          'ROLE',            'MANAGER',    'QMS', 'Manager QA'),
 ('LV',  1, 0,          'DIRECT_SUPERIOR', NULL,         NULL,  'Atasan langsung'),
 ('OT',  1, 0,          'DIRECT_SUPERIOR', NULL,         NULL,  'Atasan langsung'),
 ('SPD', 1, 0,          'DIRECT_SUPERIOR', NULL,         NULL,  'Atasan langsung');

-- ---------- Satuan (SYS-07) ----------
INSERT INTO sys.uom (code, name, category) VALUES
 ('kg', 'Kilogram', 'MASS'), ('g', 'Gram', 'MASS'), ('mg', 'Miligram', 'MASS'),
 ('L', 'Liter', 'VOLUME'), ('mL', 'Mililiter', 'VOLUME'),
 ('pcs', 'Pieces', 'COUNT'), ('box', 'Box', 'COUNT'), ('dus', 'Dus', 'COUNT'),
 ('roll', 'Roll', 'COUNT'), ('rim', 'Rim', 'COUNT'), ('unit', 'Unit', 'COUNT'),
 ('jam', 'Jam', 'TIME'), ('m', 'Meter', 'LENGTH');
INSERT INTO sys.uom_conversion (from_uom_id, to_uom_id, factor)
SELECT f.id, t.id, v.factor FROM (VALUES ('kg','g',1000), ('g','mg',1000), ('L','mL',1000)) AS v(f, t, factor)
JOIN sys.uom f ON f.code = v.f JOIN sys.uom t ON t.code = v.t;

-- ---------- Mata uang & pajak (SYS-11, SYS-12) ----------
INSERT INTO sys.currency (code, name, symbol, decimals) VALUES
 ('IDR', 'Rupiah', 'Rp', 0), ('USD', 'US Dollar', '$', 2), ('EUR', 'Euro', '€', 2), ('CNY', 'Yuan Renminbi', '¥', 2);
INSERT INTO sys.tax_code (code, name, type, rate) VALUES
 ('PPN',        'PPN',                          'PPN',    12.0000),
 ('PPH21',      'PPh 21 (tarif efektif/TER)',   'PPH21',   0.0000),
 ('PPH23-JASA', 'PPh 23 jasa',                  'PPH23',   2.0000),
 ('PPH42-SEWA', 'PPh 4(2) sewa tanah/bangunan', 'PPH4_2', 10.0000),
 ('PPH22-IMP',  'PPh 22 impor (ber-API)',       'PPH22',   2.5000);

-- ---------- Gudang & lokasi (SYS-09) ----------
INSERT INTO sys.warehouse (code, name, plant_id, type)
SELECT v.code, v.name, p.id, v.type FROM (VALUES
 ('WH-RM', 'Gudang Bahan Baku',  'RM'),
 ('WH-PM', 'Gudang Bahan Kemas', 'PM'),
 ('WH-FG', 'Gudang Barang Jadi', 'FG'),
 ('WH-SP', 'Gudang Sparepart',   'SP'),
 ('WH-GA', 'Gudang ATK & Umum',  'GENERAL')
) AS v(code, name, type) JOIN sys.plant p ON p.code = 'P1';
INSERT INTO sys.location (warehouse_id, bin_code, zone, temp_class, is_quarantine)
SELECT w.id, v.bin, v.zone, v.temp, v.qrn FROM (VALUES
 ('WH-RM', 'QRN-01',  'Karantina', 'AMBIENT', TRUE),
 ('WH-RM', 'A-01-01', 'A',         'AMBIENT', FALSE),
 ('WH-RM', 'A-01-02', 'A',         'AMBIENT', FALSE),
 ('WH-RM', 'COOL-01', 'Sejuk',     'COOL',    FALSE),
 ('WH-PM', 'QRN-01',  'Karantina', 'AMBIENT', TRUE),
 ('WH-PM', 'B-01-01', 'B',         'AMBIENT', FALSE),
 ('WH-FG', 'QRN-01',  'Karantina', 'AMBIENT', TRUE),
 ('WH-FG', 'C-01-01', 'C',         'AMBIENT', FALSE),
 ('WH-FG', 'C-01-02', 'C',         'AMBIENT', FALSE),
 ('WH-SP', 'S-01-01', 'S',         'AMBIENT', FALSE),
 ('WH-GA', 'G-01-01', 'G',         'AMBIENT', FALSE)
) AS v(wh, bin, zone, temp, qrn) JOIN sys.warehouse w ON w.code = v.wh;

-- ---------- Shift & hari libur (SYS-10) ----------
INSERT INTO sys.shift (code, name, start_time, end_time) VALUES
 ('S1', 'Shift 1', '07:00', '15:00'), ('S2', 'Shift 2', '15:00', '23:00'),
 ('S3', 'Shift 3', '23:00', '07:00'), ('NS', 'Non-shift', '08:00', '17:00');
-- Hanya libur bertanggal tetap. Libur bergerak (Idul Fitri, Imlek, dst.) diisi sesuai SKB tahunan.
INSERT INTO sys.holiday (date, name) VALUES
 ('2026-01-01', 'Tahun Baru Masehi'), ('2026-05-01', 'Hari Buruh Internasional'),
 ('2026-06-01', 'Hari Lahir Pancasila'), ('2026-08-17', 'Hari Kemerdekaan RI'),
 ('2026-12-25', 'Hari Raya Natal'), ('2027-01-01', 'Tahun Baru Masehi');

-- ---------- Item master contoh (SYS-06) ----------
INSERT INTO sys.item (code, name, type, uom_id, category, lot_tracked, shelf_life_days, storage_class, halal_critical)
SELECT v.code, v.name, v.type, u.id, v.cat, v.lot, v.shelf, v.storage, v.halal FROM (VALUES
 ('RM-SIM-001', 'Simplisia kunyit',               'RM',  'kg',  'Simplisia',     TRUE,  730,  'AMBIENT', FALSE),
 ('RM-SIM-002', 'Simplisia temulawak',            'RM',  'kg',  'Simplisia',     TRUE,  730,  'AMBIENT', FALSE),
 ('RM-EKS-001', 'Ekstrak kering jahe',            'RM',  'kg',  'Ekstrak',       TRUE,  1095, 'COOL',    FALSE),
 ('RM-EXC-001', 'Maltodekstrin',                  'RM',  'kg',  'Eksipien',      TRUE,  1095, 'AMBIENT', FALSE),
 ('PM-KPS-000', 'Cangkang kapsul no. 0',          'PM',  'pcs', 'Kemasan primer',TRUE,  1095, 'AMBIENT', TRUE),
 ('PM-BTL-100', 'Botol HDPE 100 ml',              'PM',  'pcs', 'Kemasan primer',TRUE,  1825, 'AMBIENT', FALSE),
 ('PM-DUS-060', 'Dus 60 kapsul',                  'PM',  'pcs', 'Kemasan sekunder',TRUE,1825, 'AMBIENT', FALSE),
 ('WIP-KP500',  'Massa kapsul herbal 500 mg',     'WIP', 'kg',  'Antara',        TRUE,  90,   'AMBIENT', FALSE),
 ('FG-KP500',   'Kapsul herbal 500 mg isi 60',    'FG',  'box', 'Kapsul',        TRUE,  730,  'AMBIENT', FALSE),
 ('FG-SRP100',  'Sirup herbal 100 ml',            'FG',  'pcs', 'Sirup',         TRUE,  730,  'AMBIENT', FALSE),
 ('SP-BRG-6204','Bearing 6204',                   'SP',  'pcs', 'Sparepart',     FALSE, NULL, 'AMBIENT', FALSE),
 ('ATK-A4',     'Kertas A4 80 gsm',               'ATK', 'rim', 'ATK',           FALSE, NULL, 'AMBIENT', FALSE),
 ('SVC-PEST',   'Jasa pest control',              'SVC', 'unit','Jasa',          FALSE, NULL, NULL,      FALSE)
) AS v(code, name, type, uom, cat, lot, shelf, storage, halal) JOIN sys.uom u ON u.code = v.uom;

-- ---------- Mitra bisnis contoh (SYS-08) ----------
INSERT INTO sys.partner (code, name, type, city, currency_code, payment_term_days, credit_limit) VALUES
 ('SUP-0001', 'Supplier Simplisia (Demo)',  'SUPPLIER',   'Semarang', 'IDR', 30, NULL),
 ('SUP-0002', 'Supplier Cangkang Kapsul (Demo)', 'SUPPLIER', 'Jakarta', 'IDR', 45, NULL),
 ('SUP-0003', 'Supplier Botol HDPE (Demo)', 'SUPPLIER',   'Tangerang','IDR', 30, NULL),
 ('CUS-0001', 'Distributor Nasional (Demo)','CUSTOMER',   'Jakarta',  'IDR', 30, 2500000000),
 ('EXP-0001', 'Ekspedisi Darat (Demo)',     'EXPEDITION', 'Jakarta',  'IDR', 14, NULL);

-- ---------- Template notifikasi (SYS-15) ----------
INSERT INTO sys.notification_template (code, name, channel, subject, body) VALUES
 ('APPROVAL_REQUEST',  'Permintaan approval',   'BELL', 'Menunggu approval: {docNo}', '{docType} {docNo} menunggu keputusan Anda (level {level}).'),
 ('APPROVAL_REMINDER', 'Pengingat approval',    'BELL', 'Pengingat: {docNo}',         '{docNo} menunggu approval lebih dari 2 hari kerja.'),
 ('APPROVAL_ESCALATE', 'Eskalasi approval',     'BELL', 'Eskalasi: {docNo}',          '{docNo} belum diputuskan lebih dari 4 hari kerja oleh {approver}.'),
 ('DOC_APPROVED',      'Dokumen disetujui',     'BELL', '{docNo} disetujui',          'Dokumen Anda {docNo} telah disetujui.'),
 ('DOC_REJECTED',      'Dokumen ditolak',       'BELL', '{docNo} ditolak',            'Dokumen Anda {docNo} ditolak: {reason}');

-- ---------- Bagan akun (FIN-02) ----------
INSERT INTO fin.account (code, name, type, normal_balance, postable) VALUES
 ('1',    'ASET',                     'ASSET',     'D', FALSE),
 ('1100', 'Kas & Bank',               'ASSET',     'D', FALSE),
 ('1300', 'Persediaan',               'ASSET',     'D', FALSE),
 ('1500', 'Aset Tetap',               'ASSET',     'D', FALSE),
 ('2',    'LIABILITAS',               'LIABILITY', 'C', FALSE),
 ('3',    'EKUITAS',                  'EQUITY',    'C', FALSE),
 ('4',    'PENDAPATAN',               'REVENUE',   'C', FALSE),
 ('5',    'HARGA POKOK PENJUALAN',    'EXPENSE',   'D', FALSE),
 ('6',    'BEBAN OPERASIONAL',        'EXPENSE',   'D', FALSE),
 ('7',    'PENDAPATAN & BEBAN LAIN',  'EXPENSE',   'D', FALSE);

INSERT INTO fin.account (code, name, type, normal_balance, parent_id, requires_cost_center)
SELECT v.code, v.name, v.type, v.nb, p.id, v.cc FROM (VALUES
 ('1101', 'Kas kecil',                          'ASSET',     'D', '1100', FALSE),
 ('1111', 'Bank operasional',                   'ASSET',     'D', '1100', FALSE),
 ('1201', 'Piutang usaha',                      'ASSET',     'D', '1',    FALSE),
 ('1210', 'Uang muka pembelian',                'ASSET',     'D', '1',    FALSE),
 ('1220', 'PPN masukan',                        'ASSET',     'D', '1',    FALSE),
 ('1301', 'Persediaan bahan baku',              'ASSET',     'D', '1300', FALSE),
 ('1302', 'Persediaan bahan kemas',             'ASSET',     'D', '1300', FALSE),
 ('1303', 'Barang dalam proses (WIP)',          'ASSET',     'D', '1300', FALSE),
 ('1304', 'Persediaan barang jadi',             'ASSET',     'D', '1300', FALSE),
 ('1305', 'Persediaan sparepart',               'ASSET',     'D', '1300', FALSE),
 ('1501', 'Mesin & peralatan',                  'ASSET',     'D', '1500', FALSE),
 ('1502', 'Bangunan',                           'ASSET',     'D', '1500', FALSE),
 ('1503', 'Kendaraan',                          'ASSET',     'D', '1500', FALSE),
 ('1509', 'Akumulasi penyusutan',               'ASSET',     'C', '1500', FALSE),
 ('2101', 'Hutang usaha',                       'LIABILITY', 'C', '2',    FALSE),
 ('2102', 'Hutang belum difakturkan (GRNI)',    'LIABILITY', 'C', '2',    FALSE),
 ('2103', 'Hutang gaji',                        'LIABILITY', 'C', '2',    FALSE),
 ('2104', 'Hutang PPh 21',                      'LIABILITY', 'C', '2',    FALSE),
 ('2105', 'Hutang PPh 23',                      'LIABILITY', 'C', '2',    FALSE),
 ('2106', 'PPN keluaran',                       'LIABILITY', 'C', '2',    FALSE),
 ('2107', 'Hutang BPJS',                        'LIABILITY', 'C', '2',    FALSE),
 ('3101', 'Modal disetor',                      'EQUITY',    'C', '3',    FALSE),
 ('3201', 'Laba ditahan',                       'EQUITY',    'C', '3',    FALSE),
 ('4101', 'Penjualan',                          'REVENUE',   'C', '4',    FALSE),
 ('4102', 'Retur penjualan',                    'REVENUE',   'D', '4',    FALSE),
 ('5101', 'Harga pokok penjualan',              'EXPENSE',   'D', '5',    FALSE),
 ('5201', 'Selisih harga bahan',                'EXPENSE',   'D', '5',    FALSE),
 ('5202', 'Selisih pemakaian bahan',            'EXPENSE',   'D', '5',    FALSE),
 ('5203', 'Selisih efisiensi',                  'EXPENSE',   'D', '5',    FALSE),
 ('6101', 'Gaji & tunjangan',                   'EXPENSE',   'D', '6',    TRUE),
 ('6102', 'Lembur',                             'EXPENSE',   'D', '6',    TRUE),
 ('6103', 'BPJS perusahaan',                    'EXPENSE',   'D', '6',    TRUE),
 ('6201', 'Listrik & utilitas',                 'EXPENSE',   'D', '6',    TRUE),
 ('6202', 'Pemeliharaan & perbaikan',           'EXPENSE',   'D', '6',    TRUE),
 ('6203', 'Penyusutan',                         'EXPENSE',   'D', '6',    TRUE),
 ('6204', 'Kebersihan & pest control',          'EXPENSE',   'D', '6',    TRUE),
 ('6301', 'Alat tulis kantor',                  'EXPENSE',   'D', '6',    TRUE),
 ('6302', 'Perjalanan dinas',                   'EXPENSE',   'D', '6',    TRUE),
 ('6401', 'Beban pemusnahan barang',            'EXPENSE',   'D', '6',    FALSE),
 ('6402', 'Selisih persediaan',                 'EXPENSE',   'D', '6',    FALSE),
 ('7101', 'Pendapatan lain-lain',               'REVENUE',   'C', '7',    FALSE),
 ('7201', 'Beban bank',                         'EXPENSE',   'D', '7',    FALSE),
 ('7202', 'Selisih kurs',                       'EXPENSE',   'D', '7',    FALSE)
) AS v(code, name, type, nb, parent, cc) JOIN fin.account p ON p.code = v.parent;

-- ---------- Mapping jurnal otomatis (FIN-99) ----------
INSERT INTO fin.account_mapping (txn_type, name, debit_account_id, credit_account_id)
SELECT v.txn, v.name, d.id, c.id FROM (VALUES
 ('GR_RM',          'Penerimaan bahan baku',          '1301', '2102'),
 ('GR_PM',          'Penerimaan bahan kemas',         '1302', '2102'),
 ('GR_SP',          'Penerimaan sparepart',           '1305', '2102'),
 ('ISSUE_WIP_RM',   'Bahan baku ke produksi',         '1303', '1301'),
 ('ISSUE_WIP_PM',   'Bahan kemas ke produksi',        '1303', '1302'),
 ('FG_RECEIPT',     'Hasil produksi ke barang jadi',  '1304', '1303'),
 ('COGS',           'HPP penjualan',                  '5101', '1304'),
 ('ADJ_GAIN',       'Selisih lebih persediaan',       '1301', '6402'),
 ('ADJ_LOSS',       'Selisih kurang persediaan',      '6402', '1301'),
 ('SCRAP',          'Pemusnahan barang',              '6401', '1304'),
 ('SP_ISSUE',       'Pemakaian sparepart maintenance','6202', '1305'),
 ('PAYROLL',        'Beban gaji',                     '6101', '2103'),
 ('AP_INVOICE',     'Faktur supplier (GRNI ke hutang)','2102','2101'),
 ('AR_INVOICE',     'Faktur penjualan',               '1201', '4101')
) AS v(txn, name, dr, cr)
JOIN fin.account d ON d.code = v.dr JOIN fin.account c ON c.code = v.cr;
