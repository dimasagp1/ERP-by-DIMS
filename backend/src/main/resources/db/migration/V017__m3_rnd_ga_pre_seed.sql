-- =====================================================================
-- V017: Seed Data Dokumen & Master M3 (RND, GA, PRE Lanjutan, HC Lanjutan)
-- =====================================================================

INSERT INTO sys.doc_type (code, name, app_code, menu_code, prefix, requires_esign) VALUES
 ('PRJ',    'Proyek RnD',                  'RND', 'RND-02', 'PRJ', FALSE),
 ('FML',    'Formula & Versi',             'RND', 'RND-03', 'FML', FALSE),
 ('TRL',    'Trial RnD & Scale-up',        'RND', 'RND-06', 'TRL', FALSE),
 ('SPEC',   'Draf Spesifikasi Produk',     'RND', 'RND-07', 'SPC', FALSE),
 ('REG',    'Registrasi Produk',           'RND', 'RND-09', 'REG', FALSE),
 ('ART',    'Artwork Kemasan',             'RND', 'RND-10', 'ART', FALSE),
 ('CE',     'Estimasi Biaya Formula',      'RND', 'RND-12', 'CE',  FALSE),
 ('REQ-GA', 'Permintaan Layanan GA',       'GA',  'GA-03', 'GA',  FALSE),
 ('VHB',    'Booking Kendaraan',           'GA',  'GA-04', 'VHB', FALSE),
 ('GP',     'Gate Pass & Buku Tamu',       'GA',  'GA-08', 'GP',  FALSE),
 ('K3',     'Laporan Insiden K3',          'GA',  'GA-13', 'K3',  FALSE),
 ('BMR',    'Batch Record Elektronik',     'PRE', 'PRE-03', 'BMR', TRUE),
 ('DSP',    'Penimbangan & Dispensing',    'PRE', 'PRE-05', 'DSP', TRUE),
 ('IPC',    'In-Process Control (IPC)',    'PRE', 'PRE-07', 'IPC', TRUE),
 ('DT',     'Downtime & Kendala Lini',     'PRE', 'PRE-11', 'DT',  FALSE),
 ('LCL',    'Line Clearance',              'PRE', 'PRE-13', 'LCL', TRUE),
 ('WRQ',    'Work Request',                'PRE', 'PRE-22', 'WRQ', FALSE),
 ('MNT',    'WO Maintenance',              'PRE', 'PRE-23', 'MNT', FALSE),
 ('CAL',    'Kalibrasi & Kualifikasi',     'PRE', 'PRE-25', 'CAL', TRUE),
 ('RECR',   'Permintaan Tenaga Kerja',     'HC',  'HC-04', 'REC', FALSE),
 ('TRN',    'Pelatihan & Training',        'HC',  'HC-11', 'TRN', FALSE),
 ('SP',     'Tindakan Disiplin',           'HC',  'HC-15', 'SP',  FALSE)
ON CONFLICT (code) DO NOTHING;

-- Data Awal Mesin Produksi (PRE-20)
INSERT INTO pre.machine (code, name, brand, model, status, qualification_status) VALUES
 ('MC-EXT-01', 'Mesin Ekstraksi Herbal 1000L', 'HerbaTech Engineering', 'HT-EXT1000', 'OPERATIONAL', 'QUALIFIED'),
 ('MC-EVAP-01', 'Evaporator Vakum 500L', 'Buchi', 'V-500', 'OPERATIONAL', 'QUALIFIED'),
 ('MC-DRY-01', 'Spray Dryer Industri', 'Niro', 'SD-50', 'OPERATIONAL', 'QUALIFIED'),
 ('MC-MIX-01', 'V-Blender Pencampur Serbuk 200kg', 'Pharmatech', 'VB-200', 'OPERATIONAL', 'QUALIFIED'),
 ('MC-CAP-01', 'Mesin Kapsulasi Otomatis', 'Bosch', 'GKF-700', 'OPERATIONAL', 'QUALIFIED'),
 ('MC-BLIST-01', 'Mesin Blister & Cartoner', 'Uhlmann', 'UPS-300', 'OPERATIONAL', 'QUALIFIED')
ON CONFLICT (code) DO NOTHING;

-- Data Awal Aset Non-Produksi GA (GA-02)
INSERT INTO ga.inventory_asset (asset_code, name, category, location, condition) VALUES
 ('AST-IT-001', 'Laptop Dell XPS 15 (RnD)', 'IT', 'R&D Lab', 'GOOD'),
 ('AST-VH-001', 'Toyota Innova Reborn (Operasional)', 'VEHICLE', 'Parkiran Kantor', 'GOOD'),
 ('AST-VH-002', 'Daihatsu Gran Max Box (Logistik)', 'VEHICLE', 'Loading Dock', 'GOOD'),
 ('AST-OF-001', 'Meja Rapat Kayu Jati 12 Kursi', 'FURNITURE', 'Ruang Rapat Utama', 'GOOD')
ON CONFLICT (asset_code) DO NOTHING;
