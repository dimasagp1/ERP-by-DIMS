/**
 * Engine Mock Data Komprehensif Herbatech ERP.
 * Menyediakan data realistis dan lengkap untuk seluruh 167 menu & 10 modul:
 * - Master data lengkap dengan semua kolom spesifik (SYS, HC, FIN, PRE, PRC, SCM, GA, QMS, RND).
 * - Generator dokumen spesifik dengan semua kolom list dan form header/lines terisi (bukan generik).
 * - 45 Lookup data lengkap agar dropdown tidak kosong dan tidak error.
 * - Halaman custom spesifik (stok lot, kartu stok, pergerakan, trace, laporan).
 */

import type { LookupOption, Page } from './types';
import type { Doc } from '../modules/docs/types';
import type { Row } from '../modules/master/types';

export function wrapPage<T>(items: T[], page = 0, size = 50): Page<T> {
  const totalElements = items.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  const start = page * size;
  const content = items.slice(start, start + size);
  return {
    content,
    totalElements,
    totalPages,
    page,
    size,
  };
}

// --------------------------------------------------------------------------------
// 45 LOOKUPS MASTER LENGKAP
// --------------------------------------------------------------------------------

export const MOCK_LOOKUPS: Record<string, LookupOption[]> = {
  items: [
    { id: 1, code: 'RM-SMP-001', name: 'Simplisia Temulawak Kering (Curcuma)', extra: null },
    { id: 2, code: 'RM-SMP-002', name: 'Simplisia Daun Meniran Kering', extra: null },
    { id: 3, code: 'RM-SMP-003', name: 'Simplisia Rimpang Jahe Merah', extra: null },
    { id: 4, code: 'RM-EKS-001', name: 'Ekstrak Kental Temulawak Standar 25%', extra: null },
    { id: 5, code: 'RM-EKS-002', name: 'Ekstrak Kering Meniran 50%', extra: null },
    { id: 6, code: 'EXC-AV-101', name: 'Avicel PH-102 (Microcrystalline Cellulose)', extra: null },
    { id: 7, code: 'PM-BTL-001', name: 'Botol HDPE Putih 100ml Segel Induksi', extra: null },
    { id: 8, code: 'PM-CAP-001', name: 'Tutup Botol Segel Ulir 38mm Amber', extra: null },
    { id: 9, code: 'PM-LBL-001', name: 'Label Stiker TemuCurcuma 60s BPOM', extra: null },
    { id: 10, code: 'FG-TC-001', name: 'HerbaCurcuma Forte 500mg Kapsul 60s', extra: null },
    { id: 11, code: 'FG-IM-002', name: 'ImunoHerba Meniran 250mg Kapsul 30s', extra: null },
  ],
  'fg-items': [
    { id: 10, code: 'FG-TC-001', name: 'HerbaCurcuma Forte 500mg Kapsul 60s', extra: null },
    { id: 11, code: 'FG-IM-002', name: 'ImunoHerba Meniran 250mg Kapsul 30s', extra: null },
  ],
  partners: [
    { id: 1, code: 'VND-001', name: 'PT Agro Herbal Nusantara (Supplier Simplisia)', extra: null },
    { id: 2, code: 'VND-002', name: 'PT Ekstraksi Alam Sejahtera (Supplier Ekstrak)', extra: null },
    { id: 3, code: 'VND-003', name: 'PT Kemasan Farma Plastik (Supplier Botol & Tutup)', extra: null },
    { id: 4, code: 'CUST-001', name: 'PT Kimia Farma Trading & Distribution', extra: null },
    { id: 5, code: 'CUST-002', name: 'PT Anugerah Pharmindo Lestari (APL)', extra: null },
    { id: 6, code: 'CUST-003', name: 'PT Mensa Binasukses (MBS)', extra: null },
  ],
  suppliers: [
    { id: 1, code: 'VND-001', name: 'PT Agro Herbal Nusantara (Supplier Simplisia)', extra: null },
    { id: 2, code: 'VND-002', name: 'PT Ekstraksi Alam Sejahtera (Supplier Ekstrak)', extra: null },
    { id: 3, code: 'VND-003', name: 'PT Kemasan Farma Plastik (Supplier Botol & Tutup)', extra: null },
  ],
  companies: [
    { id: 1, code: 'HTG', name: 'PT Herbatech Group Holding', extra: null },
    { id: 2, code: 'HTF', name: 'PT Herbatech Farmasi Alami (Plant 1 & 2)', extra: null },
  ],
  plants: [
    { id: 1, code: 'P1', name: 'Plant 1 Cikarang (Ekstraksi & Kapsulasi)', extra: null },
    { id: 2, code: 'P2', name: 'Plant 2 Ungaran (Kemasan & Distribusi)', extra: null },
  ],
  departments: [
    { id: 1, code: 'DEP-PRE', name: 'Produksi & Engineering', extra: null },
    { id: 2, code: 'DEP-SCM', name: 'Supply Chain & Logistik', extra: null },
    { id: 3, code: 'DEP-QA', name: 'Quality Assurance (Pemastian Mutu)', extra: null },
    { id: 4, code: 'DEP-QC', name: 'Quality Control (Pengawasan Mutu)', extra: null },
    { id: 5, code: 'DEP-RND', name: 'R&D Formulasi & Analitik', extra: null },
    { id: 6, code: 'DEP-FIN', name: 'Finance & Accounting', extra: null },
    { id: 7, code: 'DEP-HC', name: 'Human Capital', extra: null },
    { id: 8, code: 'DEP-GA', name: 'General Affairs', extra: null },
  ],
  positions: [
    { id: 1, code: 'POS-GM', name: 'General Manager Operasional Pabrik', extra: null },
    { id: 2, code: 'POS-MGR-PRE', name: 'Manajer Produksi', extra: null },
    { id: 3, code: 'POS-MGR-QA', name: 'Manajer QA (Apoteker Penanggung Jawab)', extra: null },
    { id: 4, code: 'POS-SPV-EXTR', name: 'Supervisor Ekstraksi', extra: null },
    { id: 5, code: 'POS-SPV-QC', name: 'Supervisor Laboratorium QC', extra: null },
    { id: 6, code: 'POS-OP-EXTR', name: 'Operator Ekstraksi & Evaporasi', extra: null },
    { id: 7, code: 'POS-OP-CAPS', name: 'Operator Kapsulasi & Blister', extra: null },
  ],
  employees: [
    { id: 1, code: 'HT-2022-001', name: 'Dimas Pratama, S.Farm (Superuser / GM)', extra: null },
    { id: 2, code: 'HT-2023-015', name: 'Apt. Dewi Lestari, S.Farm (QA Lead)', extra: null },
    { id: 3, code: 'HT-2023-042', name: 'Budi Santoso, S.T. (SCM Manager)', extra: null },
    { id: 4, code: 'HT-2024-088', name: 'Hendro Wijaya (Production Manager)', extra: null },
    { id: 5, code: 'HT-2024-102', name: 'Apt. Siti Rahmawati, S.Farm (QC Supervisor)', extra: null },
    { id: 6, code: 'HT-2024-115', name: 'Sri Wahyuni, S.E. (Finance Manager)', extra: null },
  ],
  'cost-centers': [
    { id: 1, code: 'CC-EXTR', name: 'Cost Center Ekstraksi Alami (Produksi)', extra: null },
    { id: 2, code: 'CC-FORM', name: 'Cost Center Formulasi & Kapsulasi', extra: null },
    { id: 3, code: 'CC-PACK', name: 'Cost Center Packaging & Blister', extra: null },
    { id: 4, code: 'CC-ENG', name: 'Cost Center Engineering & Utilitas', extra: null },
    { id: 5, code: 'CC-QC', name: 'Cost Center Laboratorium QC', extra: null },
  ],
  users: [
    { id: 1, code: 'dimas', name: 'Dimas Pratama (Superuser)', extra: null },
    { id: 2, code: 'admin', name: 'Administrator Sistem', extra: null },
    { id: 3, code: 'budi.scm', name: 'Budi Santoso (SCM Manager)', extra: null },
    { id: 4, code: 'hendro.pre', name: 'Hendro Wijaya (Production Manager)', extra: null },
    { id: 5, code: 'sri.fin', name: 'Sri Wahyuni (Finance Manager)', extra: null },
    { id: 6, code: 'dewi.qa', name: 'Apt. Dewi Lestari (QA Lead)', extra: null },
  ],
  roles: [
    { id: 1, code: 'ADMIN', name: 'Super Administrator', extra: null },
    { id: 2, code: 'MANAGER', name: 'Manajer Departemen', extra: null },
    { id: 3, code: 'SUPERVISOR', name: 'Supervisor Operasional', extra: null },
    { id: 4, code: 'OPERATOR', name: 'Operator Pelaksana', extra: null },
    { id: 5, code: 'QA_RELEASE', name: 'QA Release Officer (Apoteker)', extra: null },
  ],
  warehouses: [
    { id: 1, code: 'WH-RM', name: 'Gudang Bahan Baku (RM Warehouse)', extra: null },
    { id: 2, code: 'WH-PM', name: 'Gudang Bahan Kemas (PM Warehouse)', extra: null },
    { id: 3, code: 'WH-FG', name: 'Gudang Produk Jadi (FG Warehouse)', extra: null },
    { id: 4, code: 'WH-QA', name: 'Gudang Karantina Quality Control', extra: null },
    { id: 5, code: 'WH-REJ', name: 'Gudang Afkir & Reject Terkunci', extra: null },
  ],
  locations: [
    { id: 1, code: 'WH-RM-A1', name: 'Gudang Bahan Baku · Rak A-01 (Simplisia)', extra: null },
    { id: 2, code: 'WH-RM-B2', name: 'Gudang Bahan Baku · Cold Room B-02 (Ekstrak 2-8°C)', extra: null },
    { id: 3, code: 'WH-PM-C1', name: 'Gudang Bahan Kemas · Rak C-01 (Botol & Tutup)', extra: null },
    { id: 4, code: 'WH-FG-D1', name: 'Gudang Produk Jadi · Rak D-01 (Siap Kirim)', extra: null },
    { id: 5, code: 'WH-QA-Q1', name: 'Area Karantina Bahan Baku · Zona Q-01', extra: null },
    { id: 6, code: 'WH-REJ-R1', name: 'Ruang Barang Reject / Afkir · Zona R-01', extra: null },
  ],
  bins: [
    { id: 1, code: 'RM-A1-01', name: 'Bin RM-A1-01 (Simplisia)', extra: null },
    { id: 2, code: 'RM-A1-02', name: 'Bin RM-A1-02 (Simplisia)', extra: null },
    { id: 3, code: 'RM-CS-01', name: 'Bin RM-CS-01 (Cold Storage Ekstrak)', extra: null },
    { id: 4, code: 'FG-D1-01', name: 'Bin FG-D1-01 (HerbaCurcuma Box)', extra: null },
    { id: 5, code: 'QA-Q1-01', name: 'Bin QA-Q1-01 (Karantina Masuk)', extra: null },
  ],
  lines: [
    { id: 1, code: 'LINE-EKS-01', name: 'Lini Ekstraksi & Evaporasi Pelarut', extra: null },
    { id: 2, code: 'LINE-DRY-01', name: 'Lini Pengeringan Vacuum & Fluid Bed', extra: null },
    { id: 3, code: 'LINE-CAP-01', name: 'Lini Pengisian & Polishing Kapsul', extra: null },
    { id: 4, code: 'LINE-PAC-01', name: 'Lini Botol, Induction Sealing & Pelabelan', extra: null },
    { id: 5, code: 'LINE-CRT-01', name: 'Lini Pengemasan Sekunder & Karton Box', extra: null },
  ],
  machines: [
    { id: 1, code: 'MCH-EXT-01', name: 'Tangki Ekstraktor Stainless 316L 1000L', extra: null },
    { id: 2, code: 'MCH-EVAP-01', name: 'Falling Film Evaporator Konsentrator', extra: null },
    { id: 3, code: 'MCH-FBD-01', name: 'Fluid Bed Dryer Granulator Glatt', extra: null },
    { id: 4, code: 'MCH-CAP-01', name: 'Automatic Capsule Filling Machine NJP-1200', extra: null },
    { id: 5, code: 'MCH-IND-01', name: 'Induction Cap Sealer Otomatis', extra: null },
  ],
  lots: [
    { id: 1, code: 'LOT-SMP-2609-01', name: 'LOT-SMP-2609-01 (Temulawak Simplisia · Exp 2028-09)', extra: null },
    { id: 2, code: 'LOT-SMP-2609-02', name: 'LOT-SMP-2609-02 (Meniran Simplisia · Exp 2028-09)', extra: null },
    { id: 3, code: 'LOT-EKS-2610-01', name: 'LOT-EKS-2610-01 (Ekstrak Temulawak 25% · Exp 2027-10)', extra: null },
    { id: 4, code: 'LOT-FG-2610-01', name: 'LOT-FG-2610-01 (HerbaCurcuma Batch CR-2610-A)', extra: null },
    { id: 5, code: 'LOT-FG-2610-02', name: 'LOT-FG-2610-02 (ImunoHerba Batch IM-2610-B)', extra: null },
  ],
  'stock-lots': [
    { id: 1, code: 'LOT-SMP-2609-01', name: 'LOT-SMP-2609-01 (Temulawak Simplisia · Exp 2028-09)', extra: null },
    { id: 2, code: 'LOT-SMP-2609-02', name: 'LOT-SMP-2609-02 (Meniran Simplisia · Exp 2028-09)', extra: null },
    { id: 3, code: 'LOT-EKS-2610-01', name: 'LOT-EKS-2610-01 (Ekstrak Temulawak 25% · Exp 2027-10)', extra: null },
    { id: 4, code: 'LOT-FG-2610-01', name: 'LOT-FG-2610-01 (HerbaCurcuma Batch CR-2610-A)', extra: null },
  ],
  'work-orders': [
    { id: 1, code: 'WO/P1/2610/00001', name: 'WO/P1/2610/00001 · HerbaCurcuma 500mg · Batch CR-2610-A', extra: null },
    { id: 2, code: 'WO/P1/2610/00002', name: 'WO/P1/2610/00002 · ImunoHerba 250mg · Batch IM-2610-B', extra: null },
    { id: 3, code: 'WO/P1/2610/00003', name: 'WO/P1/2610/00003 · Ekstrak Temulawak Kental · Batch EX-2610-C', extra: null },
  ],
  shifts: [
    { id: 1, code: 'SHF-1', name: 'Shift 1 Pagi (07:00 - 15:30)', extra: null },
    { id: 2, code: 'SHF-2', name: 'Shift 2 Sore (15:00 - 23:30)', extra: null },
    { id: 3, code: 'SHF-NON', name: 'Non-Shift Kantor & QC (08:00 - 17:00)', extra: null },
  ],
  uoms: [
    { id: 1, code: 'KG', name: 'Kilogram (kg)', extra: null },
    { id: 2, code: 'G', name: 'Gram (g)', extra: null },
    { id: 3, code: 'L', name: 'Liter (l)', extra: null },
    { id: 4, code: 'ML', name: 'Mililiter (ml)', extra: null },
    { id: 5, code: 'PCS', name: 'Pieces / Buah', extra: null },
    { id: 6, code: 'BOTOL', name: 'Botol 100ml', extra: null },
    { id: 7, code: 'BOX', name: 'Box 30 Kapsul', extra: null },
  ],
  currencies: [
    { id: 1, code: 'IDR', name: 'Rupiah Indonesia (Rp)', extra: null },
    { id: 2, code: 'USD', name: 'US Dollar ($)', extra: null },
    { id: 3, code: 'EUR', name: 'Euro (€)', extra: null },
  ],
  'bank-accounts': [
    { id: 1, code: 'MDR-OPR', name: 'Bank Mandiri 137-00-1928374-1 (Operasional)', extra: null },
    { id: 2, code: 'BCA-UTM', name: 'Bank BCA 800-1122334 (Penerimaan Pelanggan)', extra: null },
    { id: 3, code: 'BNI-PAY', name: 'Bank BNI 029-3847261 (Payroll Karyawan)', extra: null },
  ],
  accounts: [
    { id: 1, code: '1101', name: '1101 · Kas Kecil Operasional', extra: null },
    { id: 2, code: '1102', name: '1102 · Bank Mandiri Operasional', extra: null },
    { id: 3, code: '1130', name: '1130 · Piutang Usaha Pihak Ketiga', extra: null },
    { id: 4, code: '1201', name: '1201 · Persediaan Bahan Baku Simplisia', extra: null },
    { id: 5, code: '1202', name: '1202 · Persediaan Bahan Kemas', extra: null },
    { id: 6, code: '1203', name: '1203 · Persediaan Barang Dalam Proses (WIP)', extra: null },
    { id: 7, code: '1204', name: '1204 · Persediaan Barang Jadi Herbal', extra: null },
    { id: 8, code: '2101', name: '2101 · Hutang Usaha Supplier', extra: null },
    { id: 9, code: '4101', name: '4101 · Pendapatan Penjualan Produk Herbal', extra: null },
    { id: 10, code: '5101', name: '5101 · Harga Pokok Penjualan (HPP)', extra: null },
    { id: 11, code: '6101', name: '6101 · Beban Gaji & Upah Karyawan', extra: null },
  ],
  'account-headers': [
    { id: 1, code: '1000', name: '1000 · ASET', extra: null },
    { id: 2, code: '1100', name: '1100 · Aset Lancar', extra: null },
    { id: 3, code: '1200', name: '1200 · Persediaan', extra: null },
    { id: 4, code: '2000', name: '2000 · LIABILITAS', extra: null },
    { id: 5, code: '3000', name: '3000 · EKUITAS', extra: null },
    { id: 6, code: '4000', name: '4000 · PENDAPATAN', extra: null },
    { id: 7, code: '5000', name: '5000 · HPP & BEBAN POKOK', extra: null },
    { id: 8, code: '6000', name: '6000 · BEBAN OPERASIONAL', extra: null },
  ],
  'asset-categories': [
    { id: 1, code: 'CAT-MCH', name: 'Mesin Produksi & Pabrik (8 Tahun)', extra: null },
    { id: 2, code: 'CAT-LAB', name: 'Alat Instrumentasi QC & R&D (4 Tahun)', extra: null },
    { id: 3, code: 'CAT-VHC', name: 'Kendaraan Operasional & Delivery (5 Tahun)', extra: null },
    { id: 4, code: 'CAT-BLD', name: 'Bangunan Pabrik & Cleanroom (20 Tahun)', extra: null },
  ],
  'reject-reasons': [
    { id: 1, code: 'REJ-01', name: 'Kapsul Rusak / Penyok Mesin', extra: null },
    { id: 2, code: 'REJ-02', name: 'Bobot Isi Di Luar Toleransi Spesifikasi', extra: null },
    { id: 3, code: 'REJ-03', name: 'Segel Induksi Aluminium Bocor / Terkelupas', extra: null },
    { id: 4, code: 'REJ-04', name: 'Label Stiker Miring / Cacat Cetak', extra: null },
  ],
  'leave-types': [
    { id: 1, code: 'LV-ANN', name: 'Cuti Tahunan (Hak 12 Hari)', extra: null },
    { id: 2, code: 'LV-SICK', name: 'Cuti Sakit (Surat Dokter)', extra: null },
    { id: 3, code: 'LV-MAT', name: 'Cuti Melahirkan (3 Bulan)', extra: null },
    { id: 4, code: 'LV-SPEC', name: 'Izin Menikah / Berduka (Khusus)', extra: null },
  ],
  qualifications: [
    { id: 1, code: 'QLF-CPOB', name: 'Kualifikasi Personal CPOB / GMP BPOM', extra: null },
    { id: 2, code: 'QLF-DISP', name: 'Kualifikasi Penimbangan & Dispensing Terkalibrasi', extra: null },
    { id: 3, code: 'QLF-CAPS', name: 'Kualifikasi Operator Mesin Kapsulasi NJP', extra: null },
    { id: 4, code: 'QLF-HPLC', name: 'Kualifikasi Analis Instrumentasi HPLC & Spektro', extra: null },
  ],
  'sarmut-kpis': [
    { id: 1, code: 'KPI-OEE', name: 'Efisiensi OEE Lini Produksi (Target >= 85%)', extra: null },
    { id: 2, code: 'KPI-STK-ACC', name: 'Akurasi Stok Gudang (Target >= 99%)', extra: null },
    { id: 3, code: 'KPI-CAPA', name: 'Penyelesaian CAPA Tepat Waktu (Target 100%)', extra: null },
  ],
  projects: [
    { id: 1, code: 'PRJ-26-01', name: 'Formula Baru Ekstrak Kombinasi Temulawak Meniran', extra: null },
    { id: 2, code: 'PRJ-26-02', name: 'Herbal Effervescent Jahe Merah Imunostimulan', extra: null },
  ],
  formulas: [
    { id: 1, code: 'FORM-HC-500', name: 'HerbaCurcuma Forte 500mg (Rev. 03)', extra: null },
    { id: 2, code: 'FORM-IM-250', name: 'ImunoHerba Meniran 250mg (Rev. 02)', extra: null },
  ],
  'po-open': [
    { id: 1, code: 'PO/P1/2610/00001', name: 'PO/P1/2610/00001 · PT Agro Herbal Nusantara (Rp 67.500.000)', extra: null },
    { id: 2, code: 'PO/P1/2610/00002', name: 'PO/P1/2610/00002 · PT Kemasan Farma Plastik (Rp 32.000.000)', extra: null },
  ],
  'po-lines': [
    { id: 1, code: 'POL-01', name: 'Simplisia Temulawak 500kg @ Rp 75.000', extra: null },
    { id: 2, code: 'POL-02', name: 'Simplisia Meniran 250kg @ Rp 120.000', extra: null },
  ],
  'po-lines-open': [
    { id: 1, code: 'POL-01', name: 'Simplisia Temulawak 500kg (Tersedia untuk GRN)', extra: null },
    { id: 2, code: 'POL-02', name: 'Simplisia Meniran 250kg (Tersedia untuk GRN)', extra: null },
  ],
  'po-received': [
    { id: 1, code: 'PO/P1/2610/00001', name: 'PO/P1/2610/00001 · Diterima di Gudang WH-RM', extra: null },
  ],
  'so-approved': [
    { id: 1, code: 'SO/2610/00045', name: 'SO/2610/00045 · PT Kimia Farma Trading (Rp 185.000.000)', extra: null },
    { id: 2, code: 'SO/2610/00049', name: 'SO/2610/00049 · PT Anugerah Pharmindo Lestari (Rp 120.000.000)', extra: null },
  ],
  'so-lines-open': [
    { id: 1, code: 'SOL-01', name: 'HerbaCurcuma 500mg 2500 Box @ Rp 74.000', extra: null },
  ],
  'pr-lines-open': [
    { id: 1, code: 'PRL-01', name: 'Simplisia Jahe Merah 500kg (Disetujui untuk PO)', extra: null },
  ],
  'deliveries-posted': [
    { id: 1, code: 'DO/P1/2610/00018', name: 'DO/P1/2610/00018 · PT Kimia Farma (800 Box)', extra: null },
  ],
  'delivery-lines': [
    { id: 1, code: 'DOL-01', name: 'HerbaCurcuma 500mg Lot FG-2610-01 (800 Box)', extra: null },
  ],
  'ap-advances': [
    { id: 1, code: 'ADV-01', name: 'Uang Muka Pembelian Bahan PT Agro Herbal (Rp 20.000.000)', extra: null },
  ],
  'ap-open': [
    { id: 1, code: 'INV-AGRO-2610-091', name: 'Tagihan PT Agro Herbal Nusantara (Rp 67.500.000)', extra: null },
  ],
  'ar-open': [
    { id: 1, code: 'INV/2610/00042', name: 'Faktur PT Kimia Farma Trading (Rp 185.000.000)', extra: null },
  ],
  'kk-advances': [
    { id: 1, code: 'KK-01', name: 'Kas Bon Operasional Tim Lapangan (Rp 2.000.000)', extra: null },
  ],
};

// --------------------------------------------------------------------------------
// MASTER DATA REPOSITORIES (LENGKAP SEMUA KOLOM)
// --------------------------------------------------------------------------------

export const MOCK_MASTER_DATA: Record<string, Row[]> = {
  companies: [
    { id: 1, code: 'HTG', name: 'PT Herbatech Group Holding', npwp: '01.234.567.8-012.000', address: 'Kawasan Industri Jababeka V Blok C-12, Cikarang', active: true },
    { id: 2, code: 'HTF', name: 'PT Herbatech Farmasi Alami', npwp: '02.345.678.9-023.000', address: 'Jl. Raya Industri Utama No. 88, Cikarang Timur', active: true },
  ],
  plants: [
    { id: 1, companyId: 1, code: 'P1', name: 'Plant 1 Cikarang (Ekstraksi & Kapsulasi)', address: 'Kawasan Industri Jababeka V Blok C-12, Cikarang', active: true },
    { id: 2, companyId: 1, code: 'P2', name: 'Plant 2 Ungaran (Kemasan Sekunder & Logistik)', address: 'Jl. Raya Semarang-Solo KM 28, Ungaran', active: true },
  ],
  departments: [
    { id: 1, code: 'DEP-PRE', name: 'Produksi & Engineering', parentId: null, appCode: 'PRE', active: true },
    { id: 2, code: 'DEP-SCM', name: 'Supply Chain & Logistik', parentId: null, appCode: 'SCM', active: true },
    { id: 3, code: 'DEP-QA', name: 'Quality Assurance (Pemastian Mutu)', parentId: null, appCode: 'QMS', active: true },
    { id: 4, code: 'DEP-QC', name: 'Quality Control (Pengawasan Mutu)', parentId: null, appCode: 'QMS', active: true },
    { id: 5, code: 'DEP-RND', name: 'R&D Formulasi & Analitik', parentId: null, appCode: 'RND', active: true },
    { id: 6, code: 'DEP-FIN', name: 'Finance & Accounting', parentId: null, appCode: 'FIN', active: true },
    { id: 7, code: 'DEP-HC', name: 'Human Capital', parentId: null, appCode: 'HC', active: true },
    { id: 8, code: 'DEP-GA', name: 'General Affairs', parentId: null, appCode: 'GA', active: true },
  ],
  'cost-centers': [
    { id: 1, code: 'CC-EXTR', name: 'Cost Center Ekstraksi Alami', departmentId: 1, plantId: 1, production: true, active: true },
    { id: 2, code: 'CC-FORM', name: 'Cost Center Formulasi & Kapsul', departmentId: 1, plantId: 1, production: true, active: true },
    { id: 3, code: 'CC-PACK', name: 'Cost Center Packaging & Blister', departmentId: 1, plantId: 1, production: true, active: true },
    { id: 4, code: 'CC-ENG', name: 'Cost Center Utility & Engineering', departmentId: 1, plantId: 1, production: false, active: true },
    { id: 5, code: 'CC-QC', name: 'Cost Center Laboratorium QC', departmentId: 4, plantId: 1, production: false, active: true },
  ],
  users: [
    { id: 1, username: 'dimas', fullName: 'Dimas Pratama, S.Farm', email: 'dimas@herbatech.co.id', employeeId: 1, defaultPlantId: 1, lastLoginAt: '2026-10-09T08:00:00', active: true },
    { id: 2, username: 'admin', fullName: 'Administrator Sistem ERP', email: 'admin@herbatech.co.id', employeeId: 1, defaultPlantId: 1, lastLoginAt: '2026-10-09T07:30:00', active: true },
    { id: 3, username: 'budi.scm', fullName: 'Budi Santoso, S.T.', email: 'budi.scm@herbatech.co.id', employeeId: 3, defaultPlantId: 1, lastLoginAt: '2026-10-09T08:15:00', active: true },
    { id: 4, username: 'hendro.pre', fullName: 'Hendro Wijaya', email: 'hendro.pre@herbatech.co.id', employeeId: 4, defaultPlantId: 1, lastLoginAt: '2026-10-09T06:50:00', active: true },
    { id: 5, username: 'sri.fin', fullName: 'Sri Wahyuni, S.E.', email: 'sri.fin@herbatech.co.id', employeeId: 6, defaultPlantId: 1, lastLoginAt: '2026-10-09T08:05:00', active: true },
    { id: 6, username: 'dewi.qa', fullName: 'Apt. Dewi Lestari, S.Farm', email: 'dewi.qa@herbatech.co.id', employeeId: 2, defaultPlantId: 1, lastLoginAt: '2026-10-09T08:30:00', active: true },
  ],
  roles: [
    { id: 1, code: 'ADMIN', name: 'Super Administrator', viewScope: 'ALL', actions: 'VIEW, CREATE, EDIT, SUBMIT, APPROVE, POST, CANCEL, EXPORT, RELEASE, AUDIT, ADMIN, PAYROLL', builtin: true, active: true },
    { id: 2, code: 'MANAGER', name: 'Manajer Departemen', viewScope: 'DEPARTMENT', actions: 'VIEW, CREATE, EDIT, SUBMIT, APPROVE, POST, CANCEL, EXPORT', builtin: true, active: true },
    { id: 3, code: 'SUPERVISOR', name: 'Supervisor Operasional', viewScope: 'SECTION', actions: 'VIEW, CREATE, EDIT, SUBMIT, APPROVE', builtin: true, active: true },
    { id: 4, code: 'OPERATOR', name: 'Operator Pelaksana', viewScope: 'OWN', actions: 'VIEW, CREATE', builtin: true, active: true },
    { id: 5, code: 'QA_RELEASE', name: 'QA Release Officer (Apoteker)', viewScope: 'ALL', actions: 'VIEW, APPROVE, RELEASE, AUDIT', builtin: true, active: true },
  ],
  'approval-rules': [
    { id: 1, docTypeCode: 'PO', level: 1, minAmount: 10000000, approverType: 'ROLE', approverRole: 'SUPERVISOR', label: 'Supervisor Pengadaan', plantId: 1, active: true },
    { id: 2, docTypeCode: 'PO', level: 2, minAmount: 50000000, approverType: 'ROLE', approverRole: 'MANAGER', label: 'Manajer SCM & Finance', plantId: 1, active: true },
    { id: 3, docTypeCode: 'PR', level: 1, minAmount: 0, approverType: 'DIRECT_SUPERIOR', label: 'Atasan Langsung Pemohon', active: true },
    { id: 4, docTypeCode: 'BMR', level: 1, minAmount: 0, approverType: 'ROLE', approverRole: 'QA_RELEASE', label: 'Apoteker QA Release', active: true },
  ],
  'doc-types': [
    { id: 1, code: 'WO', name: 'Work Order Produksi', prefix: 'WO', appCode: 'PRE', menuCode: 'PRE-02', resetPeriod: 'MONTHLY', requiresEsign: false, active: true },
    { id: 2, code: 'BMR', name: 'Batch Record Elektronik (eBMR)', prefix: 'BMR', appCode: 'PRE', menuCode: 'PRE-03', resetPeriod: 'MONTHLY', requiresEsign: true, active: true },
    { id: 3, code: 'PO', name: 'Purchase Order', prefix: 'PO', appCode: 'PRC', menuCode: 'PRC-07', resetPeriod: 'MONTHLY', requiresEsign: true, active: true },
    { id: 4, code: 'SO', name: 'Sales Order', prefix: 'SO', appCode: 'SCM', menuCode: 'SCM-02', resetPeriod: 'MONTHLY', requiresEsign: false, active: true },
    { id: 5, code: 'INV', name: 'Sales Invoice', prefix: 'INV', appCode: 'FIN', menuCode: 'FIN-20', resetPeriod: 'YEARLY', requiresEsign: false, active: true },
    { id: 6, code: 'DEV', name: 'Penyimpangan Bets (Deviasi)', prefix: 'DEV', appCode: 'QMS', menuCode: 'QMS-02', resetPeriod: 'YEARLY', requiresEsign: true, active: true },
  ],
  items: [
    { id: 1, code: 'RM-SMP-001', name: 'Simplisia Temulawak Organik Grade A', type: 'RM', uomId: 1, category: 'Simplisia Tanaman Obat', shelfLifeDays: 730, storageClass: 'AMBIENT', status: 'ACTIVE', lotTracked: true, halalCritical: false, active: true },
    { id: 2, code: 'RM-SMP-002', name: 'Simplisia Meniran Kering Standar BPOM', type: 'RM', uomId: 1, category: 'Simplisia Tanaman Obat', shelfLifeDays: 730, storageClass: 'AMBIENT', status: 'ACTIVE', lotTracked: true, halalCritical: false, active: true },
    { id: 3, code: 'WIP-EKS-001', name: 'Ekstrak Temulawak Kental Curcumin 25%', type: 'WIP', uomId: 1, category: 'Ekstrak Herbal', shelfLifeDays: 365, storageClass: 'COOL', status: 'ACTIVE', lotTracked: true, halalCritical: false, active: true },
    { id: 4, code: 'FG-HC-001', name: 'HerbaCurcuma 500mg (Box 30 Kapsul)', type: 'FG', uomId: 7, category: 'Obat Herbal Terstandar (OHT)', shelfLifeDays: 1095, storageClass: 'AMBIENT', status: 'ACTIVE', lotTracked: true, halalCritical: true, active: true },
    { id: 5, code: 'PM-BOT-001', name: 'Botol HDPE 100ml Segel Induksi Amber', type: 'PM', uomId: 6, category: 'Bahan Kemas Primer', shelfLifeDays: 1825, storageClass: 'AMBIENT', status: 'ACTIVE', lotTracked: true, halalCritical: false, active: true },
  ],
  uoms: [
    { id: 1, code: 'KG', name: 'Kilogram', category: 'MASS', active: true },
    { id: 2, code: 'G', name: 'Gram', category: 'MASS', active: true },
    { id: 3, code: 'L', name: 'Liter', category: 'VOLUME', active: true },
    { id: 4, code: 'ML', name: 'Mililiter', category: 'VOLUME', active: true },
    { id: 5, code: 'PCS', name: 'Pieces / Buah', category: 'COUNT', active: true },
    { id: 6, code: 'BOTOL', name: 'Botol', category: 'COUNT', active: true },
    { id: 7, code: 'BOX', name: 'Box / Kotak', category: 'COUNT', active: true },
  ],
  'uom-conversions': [
    { id: 1, fromUomId: 1, toUomId: 2, factor: 1000, itemId: null, active: true },
    { id: 2, fromUomId: 3, toUomId: 4, factor: 1000, itemId: null, active: true },
    { id: 3, fromUomId: 7, toUomId: 5, factor: 30, itemId: 4, active: true },
  ],
  partners: [
    { id: 1, code: 'SUP-AGRO-01', name: 'PT Agro Herbal Nusantara Lestari', type: 'SUPPLIER', npwp: '01.888.777.6-054.000', city: 'Boyolali', paymentTermDays: 30, creditLimit: 250000000, currencyCode: 'IDR', active: true },
    { id: 2, code: 'CUST-KF-01', name: 'PT Kimia Farma Trading & Distribution', type: 'CUSTOMER', npwp: '01.555.444.3-011.000', city: 'Jakarta Pusat', paymentTermDays: 45, creditLimit: 1500000000, currencyCode: 'IDR', active: true },
    { id: 3, code: 'SUP-KEMAS-01', name: 'PT Kemas Prima Sentosa', type: 'SUPPLIER', npwp: '02.111.222.3-045.000', city: 'Cikarang', paymentTermDays: 30, creditLimit: 150000000, currencyCode: 'IDR', active: true },
    { id: 4, code: 'EXP-CARGO-01', name: 'PT Trans Logistik Cepat', type: 'EXPEDITION', npwp: '03.222.333.4-098.000', city: 'Bekasi', paymentTermDays: 14, creditLimit: 50000000, currencyCode: 'IDR', active: true },
  ],
  warehouses: [
    { id: 1, code: 'WH-RM', name: 'Gudang Bahan Baku (RM Warehouse)', plantId: 1, type: 'RM', active: true },
    { id: 2, code: 'WH-PM', name: 'Gudang Bahan Kemas (PM Warehouse)', plantId: 1, type: 'PM', active: true },
    { id: 3, code: 'WH-FG', name: 'Gudang Barang Jadi (FG Warehouse)', plantId: 1, type: 'FG', active: true },
    { id: 4, code: 'WH-QA', name: 'Gudang Karantina Quality Control', plantId: 1, type: 'TRANSIT', active: true },
  ],
  locations: [
    { id: 1, warehouseId: 1, binCode: 'RM-A1-01', zone: 'Zona Simplisia Kering', tempClass: 'AMBIENT', quarantine: false, b3: false, active: true },
    { id: 2, warehouseId: 1, binCode: 'RM-A1-02', zone: 'Zona Simplisia Kering', tempClass: 'AMBIENT', quarantine: false, b3: false, active: true },
    { id: 3, warehouseId: 1, binCode: 'RM-CS-01', zone: 'Ruang Dingin Ekstrak 2-8°C', tempClass: 'COOL', quarantine: false, b3: false, active: true },
    { id: 4, warehouseId: 3, binCode: 'FG-C1-01', zone: 'Pallet Rak Siap Kirim', tempClass: 'AMBIENT', quarantine: false, b3: false, active: true },
    { id: 5, warehouseId: 4, binCode: 'QA-Q1-01', zone: 'Karantina Masuk (Inbound QC)', tempClass: 'AMBIENT', quarantine: true, b3: false, active: true },
  ],
  holidays: [
    { id: 1, date: '2026-01-01', name: 'Tahun Baru Masehi 2026', plantId: null, active: true },
    { id: 2, date: '2026-03-20', name: 'Hari Raya Idul Fitri 1447 H', plantId: null, active: true },
    { id: 3, date: '2026-08-17', name: 'Hari Kemerdekaan RI Ke-81', plantId: null, active: true },
  ],
  shifts: [
    { id: 1, code: 'SHF-1', name: 'Shift 1 Pagi Produksi', startTime: '07:00', endTime: '15:30', plantId: 1, active: true },
    { id: 2, code: 'SHF-2', name: 'Shift 2 Sore Produksi', startTime: '15:00', endTime: '23:30', plantId: 1, active: true },
    { id: 3, code: 'SHF-NON', name: 'Non-Shift Kantor & QC', startTime: '08:00', endTime: '17:00', plantId: 1, active: true },
  ],
  currencies: [
    { id: 1, code: 'IDR', name: 'Rupiah Indonesia', symbol: 'Rp', decimals: 0, active: true },
    { id: 2, code: 'USD', name: 'US Dollar', symbol: '$', decimals: 2, active: true },
    { id: 3, code: 'EUR', name: 'Euro', symbol: '€', decimals: 2, active: true },
  ],
  'exchange-rates': [
    { id: 1, currencyCode: 'USD', rateDate: '2026-10-09', rate: 15850, taxRate: 15820 },
    { id: 2, currencyCode: 'EUR', rateDate: '2026-10-09', rate: 17200, taxRate: 17180 },
  ],
  'tax-codes': [
    { id: 1, code: 'PPN11', name: 'PPN Tarif 11%', type: 'PPN', rate: 11, active: true },
    { id: 2, code: 'PPN12', name: 'PPN Tarif 12%', type: 'PPN', rate: 12, active: true },
    { id: 3, code: 'PPH23', name: 'PPh 23 Jasa 2%', type: 'PPH23', rate: 2, active: true },
  ],
  'notification-templates': [
    { id: 1, code: 'NOTIF-PO', name: 'Template Notifikasi PO Siap Review', channel: 'BELL', subject: 'Persetujuan Purchase Order {docNo}', body: 'Dokumen {docNo} menunggu approval Anda.', active: true },
  ],
  templates: [
    { id: 1, code: 'NOTIF-PO', name: 'Template Notifikasi PO Siap Review', channel: 'BELL', subject: 'Persetujuan Purchase Order {docNo}', body: 'Dokumen {docNo} menunggu approval Anda.', active: true },
  ],
  positions: [
    { id: 1, code: 'POS-GM', title: 'General Manager Operasional Pabrik', departmentId: 1, reportsToId: null, grade: 'M1', active: true },
    { id: 2, code: 'POS-MGR-PRE', title: 'Manajer Produksi & Teknik', departmentId: 1, reportsToId: 1, grade: 'M2', active: true },
    { id: 3, code: 'POS-MGR-QA', title: 'Manajer QA (Apoteker Penanggung Jawab)', departmentId: 3, reportsToId: 1, grade: 'M2', active: true },
    { id: 4, code: 'POS-SPV-EXTR', title: 'Supervisor Ekstraksi', departmentId: 1, reportsToId: 2, grade: 'S1', active: true },
    { id: 5, code: 'POS-SPV-QC', title: 'Supervisor Laboratorium QC', departmentId: 4, reportsToId: 3, grade: 'S1', active: true },
    { id: 6, code: 'POS-OP-EXTR', title: 'Operator Ekstraksi & Evaporasi', departmentId: 1, reportsToId: 4, grade: 'O1', active: true },
    { id: 7, code: 'POS-OP-CAPS', title: 'Operator Kapsulasi & Blister', departmentId: 1, reportsToId: 4, grade: 'O1', active: true },
  ],
  employees: [
    { id: 1, nik: 'HT-2022-001', name: 'Dimas Pratama, S.Farm', email: 'dimas@herbatech.co.id', phone: '081234567890', positionId: 1, departmentId: 1, costCenterId: 1, plantId: 1, joinDate: '2022-01-10', employment: 'PKWTT', status: 'ACTIVE', active: true },
    { id: 2, nik: 'HT-2023-015', name: 'Apt. Dewi Lestari, S.Farm', email: 'dewi.qa@herbatech.co.id', phone: '081398765432', positionId: 3, departmentId: 3, costCenterId: 5, plantId: 1, joinDate: '2023-03-01', employment: 'PKWTT', status: 'ACTIVE', active: true },
    { id: 3, nik: 'HT-2023-042', name: 'Budi Santoso, S.T.', email: 'budi.scm@herbatech.co.id', phone: '081512345678', positionId: 2, departmentId: 2, costCenterId: 1, plantId: 1, joinDate: '2023-06-15', employment: 'PKWTT', status: 'ACTIVE', active: true },
    { id: 4, nik: 'HT-2024-088', name: 'Hendro Wijaya', email: 'hendro.pre@herbatech.co.id', phone: '081787654321', positionId: 6, departmentId: 1, costCenterId: 1, plantId: 1, joinDate: '2024-02-01', employment: 'PKWTT', status: 'ACTIVE', active: true },
    { id: 5, nik: 'HT-2024-102', name: 'Apt. Siti Rahmawati, S.Farm', email: 'siti.qc@herbatech.co.id', phone: '081876543210', positionId: 5, departmentId: 4, costCenterId: 5, plantId: 1, joinDate: '2024-05-10', employment: 'PKWTT', status: 'ACTIVE', active: true },
    { id: 6, nik: 'HT-2024-115', name: 'Sri Wahyuni, S.E.', email: 'sri.fin@herbatech.co.id', phone: '081987654321', positionId: 2, departmentId: 6, costCenterId: 1, plantId: 1, joinDate: '2024-06-01', employment: 'PKWTT', status: 'ACTIVE', active: true },
  ],
  accounts: [
    { id: 1, code: '1101', name: 'Kas Kecil Operasional', type: 'ASSET', normalBalance: 'D', parentId: 2, postable: true, requiresCostCenter: false, active: true },
    { id: 2, code: '1102', name: 'Bank Mandiri Rekening Operasional Pabrik', type: 'ASSET', normalBalance: 'D', parentId: 2, postable: true, requiresCostCenter: false, active: true },
    { id: 3, code: '1130', name: 'Piutang Usaha Pihak Ketiga', type: 'ASSET', normalBalance: 'D', parentId: 2, postable: true, requiresCostCenter: false, active: true },
    { id: 4, code: '1201', name: 'Persediaan Bahan Baku Simplisia', type: 'ASSET', normalBalance: 'D', parentId: 3, postable: true, requiresCostCenter: true, active: true },
    { id: 5, code: '1202', name: 'Persediaan Bahan Kemas', type: 'ASSET', normalBalance: 'D', parentId: 3, postable: true, requiresCostCenter: true, active: true },
    { id: 6, code: '1203', name: 'Persediaan Barang Dalam Proses (WIP)', type: 'ASSET', normalBalance: 'D', parentId: 3, postable: true, requiresCostCenter: true, active: true },
    { id: 7, code: '1204', name: 'Persediaan Barang Jadi Herbal', type: 'ASSET', normalBalance: 'D', parentId: 3, postable: true, requiresCostCenter: false, active: true },
    { id: 8, code: '2101', name: 'Hutang Usaha Supplier', type: 'LIABILITY', normalBalance: 'C', parentId: 4, postable: true, requiresCostCenter: false, active: true },
    { id: 9, code: '4101', name: 'Pendapatan Penjualan Produk Herbal', type: 'REVENUE', normalBalance: 'C', parentId: 6, postable: true, requiresCostCenter: false, active: true },
    { id: 10, code: '5101', name: 'Harga Pokok Penjualan (HPP)', type: 'EXPENSE', normalBalance: 'D', parentId: 7, postable: true, requiresCostCenter: true, active: true },
    { id: 11, code: '6101', name: 'Beban Gaji & Upah Karyawan', type: 'EXPENSE', normalBalance: 'D', parentId: 8, postable: true, requiresCostCenter: true, active: true },
    { id: 12, code: '6102', name: 'Beban Listrik, Air & Gas Utilitas Pabrik', type: 'EXPENSE', normalBalance: 'D', parentId: 8, postable: true, requiresCostCenter: true, active: true },
  ],
  'account-mappings': [
    { id: 1, txnType: 'GR_RM', name: 'Penerimaan Bahan Baku Gudang', debitAccountId: 4, creditAccountId: 8, active: true },
    { id: 2, txnType: 'FG_RECEIPT', name: 'Penyelesaian Barang Jadi Produksi', debitAccountId: 7, creditAccountId: 6, active: true },
    { id: 3, txnType: 'PAYROLL', name: 'Alokasi Beban Gaji Bulanan', debitAccountId: 11, creditAccountId: 2, active: true },
  ],
  'bank-accounts': [
    { id: 1, code: 'MDR-OPR', name: 'Bank Mandiri Operasional Pabrik', bankName: 'Bank Mandiri', accountNumber: '137-00-1928374-1', currencyCode: 'IDR', active: true },
    { id: 2, code: 'BCA-UTM', name: 'Bank BCA Rekening Penerimaan', bankName: 'Bank BCA', accountNumber: '800-1122334', currencyCode: 'IDR', active: true },
  ],
  'asset-categories': [
    { id: 1, code: 'CAT-MCH', name: 'Mesin Produksi & Pabrik', usefulLifeYears: 8, method: 'STRAIGHT_LINE', active: true },
    { id: 2, code: 'CAT-LAB', name: 'Alat Instrumentasi QC', usefulLifeYears: 4, method: 'STRAIGHT_LINE', active: true },
    { id: 3, code: 'CAT-BLD', name: 'Bangunan Cleanroom CPOB', usefulLifeYears: 20, method: 'STRAIGHT_LINE', active: true },
  ],
  'leave-types': [
    { id: 1, code: 'LV-ANN', name: 'Cuti Tahunan', attendanceStatus: 'CUTI', maxDays: 12, paid: true, deductsAnnual: true, requiresAttachment: false, active: true },
    { id: 2, code: 'LV-SICK', name: 'Cuti Sakit', attendanceStatus: 'SAKIT', maxDays: 14, paid: true, deductsAnnual: false, requiresAttachment: true, active: true },
    { id: 3, code: 'LV-MAT', name: 'Cuti Melahirkan', attendanceStatus: 'CUTI', maxDays: 90, paid: true, deductsAnnual: false, requiresAttachment: true, active: true },
  ],
  'leave-entitlements': [
    { id: 1, employeeId: 1, year: 2026, days: 12, carriedOver: 2, active: true },
    { id: 2, employeeId: 4, year: 2026, days: 12, carriedOver: 0, active: true },
  ],
  'salary-components': [
    { id: 1, seq: 10, code: 'BASE', name: 'Gaji Pokok', kind: 'EARNING', fixed: true, taxable: true, accountCode: '6101', system: true, active: true },
    { id: 2, seq: 20, code: 'TJ-POS', name: 'Tunjangan Jabatan', kind: 'EARNING', fixed: true, taxable: true, accountCode: '6101', system: false, active: true },
    { id: 3, seq: 30, code: 'TJ-SHF', name: 'Tunjangan Shift Malam', kind: 'EARNING', fixed: false, taxable: true, accountCode: '6101', system: false, active: true },
    { id: 4, seq: 50, code: 'BPJS-TK-EE', name: 'Iuran JHT Karyawan (2%)', kind: 'DEDUCTION', fixed: false, taxable: false, accountCode: '2101', system: true, active: true },
  ],
  'payroll-params': [
    { id: 1, key: 'BPJS_KES_MAX_CAP', value: '12000000', description: 'Batas atas upah perhitungan BPJS Kesehatan', active: true },
    { id: 2, key: 'BPJS_TK_JHT_PCT', value: '5.7', description: 'Total iuran JHT (3.7% perusahaan + 2% karyawan)', active: true },
  ],
  ptkp: [
    { id: 1, status: 'TK/0', amount: 54000000, terCategory: 'A', active: true },
    { id: 2, status: 'K/0', amount: 58500000, terCategory: 'A', active: true },
    { id: 3, status: 'K/1', amount: 63000000, terCategory: 'B', active: true },
    { id: 4, status: 'K/2', amount: 67500000, terCategory: 'B', active: true },
  ],
  'pph21-ter': [
    { id: 1, category: 'A', minGross: 0, maxGross: 5400000, ratePct: 0, active: true },
    { id: 2, category: 'A', minGross: 5400001, maxGross: 5650000, ratePct: 0.25, active: true },
    { id: 3, category: 'A', minGross: 5650001, maxGross: 5950000, ratePct: 0.5, active: true },
    { id: 4, category: 'A', minGross: 5950001, maxGross: 6300000, ratePct: 0.75, active: true },
  ],
  qualifications: [
    { id: 1, code: 'QLF-CPOB', name: 'Kualifikasi Personal CPOB BPOM', scope: 'Seluruh Lini Produksi & QA', validMonths: 24, active: true },
    { id: 2, code: 'QLF-DISP', name: 'Kualifikasi Penimbangan & Dispensing', scope: 'Ruang Dispensing Farmasi', validMonths: 12, active: true },
  ],
  'sarmut-kpis': [
    { id: 1, code: 'KPI-OEE', title: 'Efisiensi OEE Produksi', deptCode: 'PRE', targetVal: '85', unit: '%', active: true },
    { id: 2, code: 'KPI-STK', title: 'Akurasi Stok Opname Fisik', deptCode: 'SCM', targetVal: '99', unit: '%', active: true },
  ],
  contracts: [
    { id: 1, employeeId: 4, contractNo: 'PKWTT/2024/088', startDate: '2024-02-01', endDate: null, status: 'ACTIVE', active: true },
    { id: 2, employeeId: 5, contractNo: 'PKWTT/2024/102', startDate: '2024-05-10', endDate: null, status: 'ACTIVE', active: true },
  ],
  lines: [
    { id: 1, code: 'LIN-EXT-01', name: 'Lini Ekstraksi & Evaporasi', plantId: 1, capacityPerHour: 200, active: true },
    { id: 2, code: 'LIN-CAP-01', name: 'Lini Kapsulasi Otomatis', plantId: 1, capacityPerHour: 15000, active: true },
    { id: 3, code: 'LIN-PCK-01', name: 'Lini Blister & Botol', plantId: 1, capacityPerHour: 1200, active: true },
  ],
  'product-params': [
    { id: 1, itemCode: 'FG-HC-001', paramName: 'Kadar Air Granul', stdVal: '<= 5.0%', uom: '%', active: true },
    { id: 2, itemCode: 'FG-HC-001', paramName: 'Keseragaman Bobot', stdVal: '500 mg ± 5%', uom: 'mg', active: true },
    { id: 3, itemCode: 'FG-HC-001', paramName: 'Waktu Hancur Kapsul', stdVal: '<= 15 menit', uom: 'menit', active: true },
  ],
  'reject-reasons': [
    { id: 1, code: 'REJ-01', name: 'Kapsul Rusak / Penyok Mesin', severity: 'MINOR', active: true },
    { id: 2, code: 'REJ-02', name: 'Bobot Di Luar Batas Toleransi', severity: 'MAJOR', active: true },
    { id: 3, code: 'REJ-03', name: 'Segel Induksi Tidak Rapat', severity: 'CRITICAL', active: true },
  ],
  machines: [
    { id: 1, code: 'MCH-EXT-01', name: 'Tangki Ekstraktor Stainless 316L 1000L', lineId: 1, serialNo: 'EXT-2022-88', status: 'RUNNING', active: true },
    { id: 2, code: 'MCH-CAP-01', name: 'Automatic Capsule Filling Machine NJP-1200', lineId: 2, serialNo: 'CAP-2023-14', status: 'RUNNING', active: true },
    { id: 3, code: 'MCH-IND-01', name: 'Induction Cap Sealer Otomatis', lineId: 3, serialNo: 'IND-2024-02', status: 'RUNNING', active: true },
  ],
  'supplier-profiles': [
    { id: 1, partnerId: 1, auditScore: 94.5, gmpCert: true, halalCert: true, isoCert: true, status: 'APPROVED', active: true },
    { id: 2, partnerId: 2, auditScore: 92.0, gmpCert: true, halalCert: true, isoCert: true, status: 'APPROVED', active: true },
  ],
  asls: [
    { id: 1, partnerId: 1, itemCategory: 'Simplisia Tanaman Obat', status: 'QUALIFIED', validUntil: '2027-12-31', active: true },
    { id: 2, partnerId: 2, itemCategory: 'Ekstrak Herbal Terstandar', status: 'QUALIFIED', validUntil: '2027-10-31', active: true },
  ],
  'price-lists': [
    { id: 1, partnerId: 1, itemId: 1, unitPrice: 75000, currency: 'IDR', validFrom: '2026-01-01', active: true },
    { id: 2, partnerId: 1, itemId: 2, unitPrice: 120000, currency: 'IDR', validFrom: '2026-01-01', active: true },
  ],
  boms: [
    { id: 1, fgItemId: 4, bomVersion: 'V3.2', version: 1, batchSize: 50000, status: 'APPROVED', active: true },
  ],
  'stock-params': [
    { id: 1, itemId: 1, minStock: 200, maxStock: 2000, reorderPoint: 500, safetyStock: 250, active: true },
    { id: 2, itemId: 2, minStock: 100, maxStock: 1000, reorderPoint: 250, safetyStock: 120, active: true },
  ],
  'inventory-assets': [
    { id: 1, code: 'AST-AC-01', name: 'Sistem HVAC Cleanroom Grey Area', category: 'FASILITAS', location: 'Lantai 2 Produksi', condition: 'BAIK', active: true },
    { id: 2, code: 'AST-GEN-01', name: 'Genset Cummins 500 KVA Otomatis', category: 'UTILITAS', location: 'Power House Luar', condition: 'BAIK', active: true },
  ],
};

export function generateMockMaster(resource: string): Row[] {
  const norm = resource.toLowerCase().replace(/_/g, '-');
  if (MOCK_MASTER_DATA[norm]) {
    return MOCK_MASTER_DATA[norm];
  }
  if (MOCK_LOOKUPS[norm]) {
    return MOCK_LOOKUPS[norm].map((o) => ({
      id: o.id,
      code: o.code,
      name: o.name,
      active: true,
      category: 'UTAMA',
    }));
  }
  const result: Row[] = [];
  for (let i = 1; i <= 6; i++) {
    result.push({
      id: i,
      code: `${norm.toUpperCase()}-00${i}`,
      name: `Master Data ${norm} #${i}`,
      category: i % 2 === 0 ? 'UTAMA' : 'PENDUKUNG',
      active: true,
    });
  }
  return result;
}

// --------------------------------------------------------------------------------
// GENERATOR DOKUMEN SPESIFIK & REALISTIS DENGAN SEMUA FIELD TERISI
// --------------------------------------------------------------------------------

export function generateMockDocs(docType: string, count = 8): Doc[] {
  const today = new Date();
  const res: Doc[] = [];
  const dt = docType.toUpperCase().replace(/-/g, '_');

  for (let i = 1; i <= count; i++) {
    const d = new Date(today.getTime() - i * 86400000 * 2);
    const dateStr = d.toISOString().slice(0, 10);
    const numStr = String(i).padStart(5, '0');
    const docNo = `${dt}/P1/2610/${numStr}`;
    const status = i === 1 ? 'APPROVED' : i === 2 ? 'SUBMITTED' : i === 3 ? 'POSTED' : i === 4 ? 'DONE' : 'DRAFT';

    let item: Doc = {
      id: i,
      docNo,
      docDate: dateStr,
      status,
      remarks: `Operasional ${dt} nomor 2610-${numStr}`,
      totalAmount: (i * 12500000) % 85000000 + 15000000,
    };

    // Tambahkan field spesifik berdasarkan docType/endpoint agar kolom list & form header terisi
    if (dt.includes('BMR') || dt.includes('BATCH_RECORD')) {
      item = {
        ...item,
        woId: 1,
        batchNo: i % 2 === 1 ? 'CR-2610-A' : 'IM-2610-B',
        stageName: i === 1 ? 'Granulasi & Pencampuran Kering' : i === 2 ? 'Pengisian Kapsul NJP' : 'Ekstraksi Pelarut Etanol',
        operatorId: 4,
        checkerId: 2,
        startTime: `${dateStr}T08:00:00`,
        endTime: `${dateStr}T16:00:00`,
        notes: 'Parameter proses suhu dan tekanan steam sesuai standar validasi proses',
      };
    } else if (dt.includes('WO') || dt.includes('WORK_ORDER')) {
      item = {
        ...item,
        batchNo: i % 2 === 1 ? 'CR-2610-A' : 'IM-2610-B',
        productName: i % 2 === 1 ? 'HerbaCurcuma 500mg (Box 30 Kapsul)' : 'ImunoHerba 250mg',
        productItemId: 4,
        lineId: 3,
        qtyPlan: 50000,
        qtyGood: 49500,
        qtyReject: 500,
        yieldPct: 99.0,
        shiftId: 1,
        plannedStart: dateStr,
        plannedEnd: dateStr,
        expDate: '2028-10-09',
        started: true,
        notes: 'Prioritas pemenuhan pesanan PBF Kimia Farma Trading',
        operators: [
          { employeeId: 4, employeeName: 'Hendro Wijaya', role: 'OPERATOR', processCode: 'CAPSULATING', qualified: true },
        ],
      };
    } else if (dt.includes('DSP') || dt.includes('DISPENSING')) {
      item = {
        ...item,
        woId: 1,
        itemId: 1,
        lotId: 1,
        targetQty: 125.0,
        actualQty: 125.02,
        barcodeScanned: 'BC-LOT-SMP-2609-01',
        weighedBy: 4,
        verifiedBy: 2,
      };
    } else if (dt.includes('IPC') || dt.includes('IPC_ENTR')) {
      item = {
        ...item,
        woId: 1,
        stageName: 'Pengisian Kapsul Lini 3',
        paramName: 'Keseragaman Bobot Kapsul',
        targetVal: '500 mg ± 5%',
        measuredVal: '503.2 mg',
        operatorId: 4,
      };
    } else if (dt.includes('HP') || dt.includes('OUTPUT')) {
      item = {
        ...item,
        woId: 1,
        woDocNo: 'WO/P1/2610/00001',
        batchNo: 'CR-2610-A',
        productName: 'HerbaCurcuma 500mg',
        qtyGood: 49500,
        qtyReject: 500,
        yieldPct: 99.0,
        rejectReasonId: 2,
        toLocationId: 4,
        lotStatus: 'QUARANTINE',
        yieldExplanation: 'Penyusutan proses 1.0% dalam batas toleransi standar 2.0%',
      };
    } else if (dt.includes('MR') || dt.includes('MATERIAL_REQ')) {
      item = {
        ...item,
        woId: 1,
        woNo: 'WO/P1/2610/00001',
        batchNo: 'CR-2610-A',
        productName: 'HerbaCurcuma 500mg',
        neededAt: dateStr,
        issuedValue: 45000000,
        notes: 'Pengeluaran bahan baku Bets CR-2610-A',
        lines: [
          { id: 1, itemId: 1, qtyBom: 125, qty: 125, qtyIssued: 125, uom: 'KG', note: 'Simplisia Temulawak Organik' },
          { id: 2, itemId: 6, qtyBom: 25, qty: 25, qtyIssued: 25, uom: 'KG', note: 'Avicel PH-102 Microcrystalline' },
        ],
      };
    } else if (dt.includes('MRT') || dt.includes('MATERIAL_RET')) {
      item = {
        ...item,
        woId: 1,
        woNo: 'WO/P1/2610/00001',
        batchNo: 'CR-2610-A',
        returnedValue: 1250000,
        lines: [
          { id: 1, itemId: 1, qty: 2.5, uom: 'KG', note: 'Sisa simplisia kemasan utuh' },
        ],
      };
    } else if (dt.includes('DT') || dt.includes('DOWNTIME')) {
      item = {
        ...item,
        machineId: 3,
        machineName: 'Automatic Capsule Filling NJP-1200',
        reasonId: 1,
        reason: 'Pembersihan tumpahan serbuk hopper',
        startTime: `${dateStr}T10:15`,
        endTime: `${dateStr}T11:00`,
        durationMinutes: 45,
        notes: 'Downtime operasional wajar',
      };
    } else if (dt.includes('LC') || dt.includes('LINE_CLEAR')) {
      item = {
        ...item,
        lineId: 3,
        lineName: 'Lini Kapsul 1',
        passed: true,
        notes: 'Area bersih, residu bets nihil, label terverifikasi QA',
      };
    } else if (dt.includes('WRQ') || dt.includes('WORK_REQ')) {
      item = {
        ...item,
        machineId: 1,
        priority: 'HIGH',
        problemDesc: 'Pemanas heater tangki ekstraksi suhu naik lambat',
        departmentId: 1,
      };
    } else if (dt.includes('MNT') || dt.includes('MAINTENANCE')) {
      item = {
        ...item,
        machineId: 1,
        maintType: 'PREVENTIVE',
        notes: 'PM Bulanan: Pelumasan bearing dan penggantian gasket sanitary',
      };
    } else if (dt.includes('CAL') || dt.includes('CALIBRAT')) {
      item = {
        ...item,
        instrumentId: 1,
        instrumentName: 'Timbangan Analitik Mettler Toledo',
        dueDate: '2027-04-05',
        result: 'PASSED',
        notes: 'Kalibrasi 5 titik beban standar lolos uji',
      };
    } else if (dt.includes('PR') || dt.includes('REQUISITION')) {
      item = {
        ...item,
        requesterId: 1,
        requesterName: 'Dimas Pratama',
        departmentId: 1,
        neededAt: dateStr,
        totalAmount: 67500000,
        purpose: 'Pengadaan bahan baku simplisia jahe merah untuk produksi triwulan 4',
        lines: [
          { id: 1, itemId: 3, qty: 500, unitCost: 135000, amount: 67500000, notes: 'Simplisia Jahe Merah Kering Organik' },
        ],
      };
    } else if (dt.includes('PO') || dt.includes('PURCHASE_ORDER')) {
      item = {
        ...item,
        partnerId: 1,
        orderDate: dateStr,
        expectedDate: dateStr,
        totalAmount: 67500000,
        paymentTermDays: 30,
        currencyCode: 'IDR',
        lines: [
          { id: 1, itemId: 1, qty: 500, unitCost: 75000, amount: 37500000, notes: 'Simplisia Temulawak Organik' },
          { id: 2, itemId: 2, qty: 250, unitCost: 120000, amount: 30000000, notes: 'Simplisia Meniran Kering' },
        ],
      };
    } else if (dt.includes('SO') || dt.includes('SALES_ORDER')) {
      item = {
        ...item,
        customerPartnerId: 2,
        partnerId: 2,
        orderDate: dateStr,
        deliveryDate: dateStr,
        totalAmount: 185000000,
        lines: [
          { id: 1, itemId: 4, qty: 2500, unitCost: 74000, amount: 185000000, notes: 'HerbaCurcuma 500mg (Box 30 Kapsul)' },
        ],
      };
    } else if (dt.includes('DO') || dt.includes('DELIVER')) {
      item = {
        ...item,
        soNo: 'SO/2610/00045',
        partnerId: 2,
        driverName: 'Suroso (PT Trans Logistik Cepat)',
        vehicleNo: 'B 9123 KCA',
        lines: [
          { id: 1, itemId: 4, qty: 800, lotNo: 'LOT-FG-2610-01', notes: 'Pengiriman Batch 1' },
        ],
      };
    } else if (dt.includes('GRN') || dt.includes('RECEIPT')) {
      item = {
        ...item,
        poNo: 'PO/P1/2610/00001',
        partnerId: 1,
        warehouseId: 1,
        lines: [
          { id: 1, itemId: 1, qty: 500, lotNo: 'LOT-SMP-2609-01' },
        ],
      };
    } else if (dt.includes('DEV') || dt.includes('DEVIATION')) {
      item = {
        ...item,
        classification: 'MINOR',
        batchNo: 'CR-2610-A',
        title: 'Fluktuasi suhu ruang pengeringan 2 menit',
        investigation: 'Siklus otomatis pembersihan blower, suhu kembali stabil dalam 120 detik, mutu granul tidak terpengaruh',
        status: 'APPROVED',
      };
    } else if (dt.includes('CAPA')) {
      item = {
        ...item,
        source: 'DEV/2610/00001',
        actionPlan: 'Pemasangan relay cadangan dan kalibrasi sensor thermocouple redundan',
        targetDate: dateStr,
        pic: 'Hendro Wijaya',
      };
    } else if (dt.includes('COA')) {
      item = {
        ...item,
        lotNo: 'LOT-FG-2610-01',
        productName: 'HerbaCurcuma Forte 500mg Kapsul',
        result: 'MEMENUHI SYARAT (PASSED)',
      };
    } else if (dt.includes('REL') || dt.includes('RELEASE')) {
      item = {
        ...item,
        batchNo: 'CR-2610-A',
        productName: 'HerbaCurcuma Forte 500mg Kapsul',
        decision: 'RELEASED',
        qaOfficer: 'Apt. Dewi Lestari',
      };
    } else if (dt.includes('PAY') || dt.includes('PAYROLL')) {
      item = {
        ...item,
        period: '2026-09',
        totalEmployees: 48,
        totalNetPay: 342500000,
        status: 'DONE',
      };
    } else if (dt.includes('AP') || dt.includes('AP_INVOICE')) {
      item = {
        ...item,
        partnerId: 1,
        invoiceNo: `INV-AGRO-2610-${numStr}`,
        dueDate: dateStr,
        totalAmount: 67500000,
      };
    } else if (dt.includes('AR') || dt.includes('AR_INVOICE')) {
      item = {
        ...item,
        partnerId: 2,
        invoiceNo: `INV/2610/${numStr}`,
        dueDate: dateStr,
        totalAmount: 185000000,
      };
    }

    res.push(item);
  }

  return res;
}

export function generateMockDocEnvelope(endpointOrType: string, id: number) {
  const list = generateMockDocs(endpointOrType, 5);
  const found = list.find((d) => d.id === id) || list[0] || {};
  const docNo = String(found.docNo || `${endpointOrType.toUpperCase()}/P1/2610/${String(id).padStart(5, '0')}`);
  const status = String(found.status || 'APPROVED');
  const doc: Doc = { ...found, id, docNo, status };

  return {
    doc,
    meta: {
      docNo,
      status,
      canEdit: true,
      canSubmit: true,
      canApprove: true,
      canCancel: true,
      version: 1,
      actions: ['PRINT', 'ATTACH', 'APPROVE'],
    },
  };
}

// --------------------------------------------------------------------------------
// DATA KHUSUS HALAMAN SPESIFIK (SCM, PRE, FIN, QMS, RND, GA, HC, SYS)
// --------------------------------------------------------------------------------

export const MOCK_CUSTOM_PAGES: Record<string, any> = {
  '/scm/moves': [
    {
      id: 101,
      move_type: 'GR',
      item_code: 'RM-SMP-001',
      lot_no: 'LOT-SMP-2609-01',
      from_bin: 'VENDOR',
      to_bin: 'WH-QA-Q1',
      qty: 500,
      unit_cost: 45000,
      move_date: '2026-10-08',
      ref_doc_type: 'GR',
      ref_doc_no: 'GR/P1/2610/00012',
      reason: 'Penerimaan Simplisia Temulawak PT Agro Herbal',
      created_at: '2026-10-08T09:30:00Z',
    },
    {
      id: 102,
      move_type: 'REL',
      item_code: 'RM-SMP-001',
      lot_no: 'LOT-SMP-2609-01',
      from_bin: 'WH-QA-Q1',
      to_bin: 'WH-RM-A1',
      qty: 500,
      unit_cost: 45000,
      move_date: '2026-10-08',
      ref_doc_type: 'QC',
      ref_doc_no: 'REL/2610/00004',
      reason: 'Pelulusan QC lolos uji organoleptik & kadar air',
      created_at: '2026-10-08T14:15:00Z',
    },
    {
      id: 103,
      move_type: 'PROD',
      item_code: 'RM-SMP-001',
      lot_no: 'LOT-SMP-2609-01',
      from_bin: 'WH-RM-A1',
      to_bin: 'PROD-EXTR',
      qty: 250,
      unit_cost: 45000,
      move_date: '2026-10-09',
      ref_doc_type: 'MR',
      ref_doc_no: 'MR/P1/2610/00031',
      reason: 'Pengeluaran ke lini ekstraksi batch CR-2610-A',
      created_at: '2026-10-09T08:00:00Z',
    },
  ],
  '/scm/quant': [
    {
      id: 1,
      item_id: 1,
      code: 'RM-SMP-001',
      name: 'Simplisia Temulawak Kering (Curcuma)',
      lot_id: 1,
      lot_no: 'LOT-SMP-2609-01',
      qc_status: 'RELEASED',
      exp_date: '2028-09-15',
      location_id: 1,
      wh: 'Gudang Bahan Baku',
      bin_code: 'WH-RM-A1',
      qty: 250,
      qty_reserved: 50,
      unit_cost: 45000,
    },
    {
      id: 2,
      item_id: 2,
      code: 'RM-SMP-002',
      name: 'Simplisia Daun Meniran Kering',
      lot_id: 2,
      lot_no: 'LOT-SMP-2609-02',
      qc_status: 'RELEASED',
      exp_date: '2028-09-20',
      location_id: 1,
      wh: 'Gudang Bahan Baku',
      bin_code: 'WH-RM-A1',
      qty: 180,
      qty_reserved: 0,
      unit_cost: 52000,
    },
    {
      id: 3,
      item_id: 4,
      code: 'RM-EKS-001',
      name: 'Ekstrak Kental Temulawak 25%',
      lot_id: 3,
      lot_no: 'LOT-EKS-2610-01',
      qc_status: 'RELEASED',
      exp_date: '2027-10-01',
      location_id: 2,
      wh: 'Gudang Bahan Baku',
      bin_code: 'WH-RM-B2',
      qty: 60,
      qty_reserved: 20,
      unit_cost: 185000,
    },
    {
      id: 4,
      item_id: 10,
      code: 'FG-TC-001',
      name: 'HerbaCurcuma Forte 500mg 60s',
      lot_id: 4,
      lot_no: 'LOT-FG-2610-01',
      qc_status: 'RELEASED',
      exp_date: '2028-10-01',
      location_id: 4,
      wh: 'Gudang Produk Jadi',
      bin_code: 'WH-FG-D1',
      qty: 2400,
      qty_reserved: 800,
      unit_cost: 65000,
    },
  ],
  '/scm/stock': [
    {
      id: 1,
      item_id: 1,
      code: 'RM-SMP-001',
      name: 'Simplisia Temulawak Kering (Curcuma)',
      lot_id: 1,
      lot_no: 'LOT-SMP-2609-01',
      qc_status: 'RELEASED',
      exp_date: '2028-09-15',
      location_id: 1,
      wh: 'Gudang Bahan Baku',
      bin_code: 'WH-RM-A1',
      qty: 250,
      qty_reserved: 50,
      unit_cost: 45000,
    },
    {
      id: 4,
      item_id: 10,
      code: 'FG-TC-001',
      name: 'HerbaCurcuma Forte 500mg 60s',
      lot_id: 4,
      lot_no: 'LOT-FG-2610-01',
      qc_status: 'RELEASED',
      exp_date: '2028-10-01',
      location_id: 4,
      wh: 'Gudang Produk Jadi',
      bin_code: 'WH-FG-D1',
      qty: 2400,
      qty_reserved: 800,
      unit_cost: 65000,
    },
  ],
  '/scm/trace': {
    lot: {
      id: 4,
      lot_no: 'LOT-FG-2610-01',
      item_code: 'FG-TC-001',
      item_name: 'HerbaCurcuma Forte 500mg Kapsul 60s',
      qc_status: 'RELEASED',
      exp_date: '2028-10-01',
    },
    backward: [
      {
        kind: 'WO',
        doc_no: 'WO/P1/2610/00052',
        party: 'Lini Kapsul · Line 3',
        ref: 'Batch CR-2610-A',
        qty: 2400,
        doc_date: '2026-10-08',
        depth: 0,
      },
      {
        kind: 'MATERIAL',
        doc_no: 'MR/P1/2610/00031',
        party: 'RM-EKS-001 · Ekstrak Temulawak 25%',
        ref: 'LOT-EKS-2610-01',
        qty: 60,
        doc_date: '2026-10-07',
        depth: 1,
      },
      {
        kind: 'GR',
        doc_no: 'GR/P1/2610/00012',
        party: 'PT Ekstraksi Alam Sejahtera',
        ref: 'PO/P1/2610/00005',
        qty: 100,
        doc_date: '2026-10-02',
        depth: 2,
      },
    ],
    forward: [
      {
        kind: 'DO',
        doc_no: 'DO/P1/2610/00018',
        party: 'PT Kimia Farma Trading & Distribution',
        ref: 'SO/2610/00045',
        qty: 800,
        doc_date: '2026-10-09',
        depth: 0,
      },
      {
        kind: 'DO',
        doc_no: 'DO/P1/2610/00021',
        party: 'PT Anugerah Pharmindo Lestari (APL)',
        ref: 'SO/2610/00049',
        qty: 1200,
        doc_date: '2026-10-09',
        depth: 0,
      },
    ],
  },
  '/scm/forecast': [
    { period: '2026-10', sku: 'HerbaCurcuma 60s', forecastQty: 5000, actualQty: 4850, accuracyPct: 97.0 },
    { period: '2026-11', sku: 'HerbaCurcuma 60s', forecastQty: 5500, actualQty: null, accuracyPct: null },
    { period: '2026-10', sku: 'ImunoHerba 30s', forecastQty: 3000, actualQty: 3120, accuracyPct: 96.0 },
  ],
  '/scm/mps': [
    { week: 'W41 (Okt)', line: 'Lini Kapsul 1', product: 'HerbaCurcuma 60s', plannedQty: 2500, capacityPct: 88 },
    { week: 'W42 (Okt)', line: 'Lini Kapsul 1', product: 'ImunoHerba 30s', plannedQty: 3000, capacityPct: 92 },
    { week: 'W41 (Okt)', line: 'Lini Ekstraksi', product: 'Ekstrak Temulawak', plannedQty: 200, capacityPct: 82 },
  ],
  '/scm/mrp': [
    { itemCode: 'RM-SMP-001', itemName: 'Simplisia Temulawak', onHand: 750, requiredQty: 500, shortfall: 0, suggestPo: false },
    { itemCode: 'PM-BTL-001', itemName: 'Botol HDPE 100ml', onHand: 1200, requiredQty: 2500, shortfall: 1300, suggestPo: true },
    { itemCode: 'PM-CAP-001', itemName: 'Tutup Botol Segel', onHand: 1500, requiredQty: 2500, shortfall: 1000, suggestPo: true },
  ],
  '/scm/capacity': [
    { line: 'Lini Ekstraksi', standardHours: 160, plannedHours: 138, utilPct: 86.2, status: 'NORMAL' },
    { line: 'Lini Kapsul', standardHours: 160, plannedHours: 152, utilPct: 95.0, status: 'HIGH' },
    { line: 'Lini Kemas', standardHours: 160, plannedHours: 124, utilPct: 77.5, status: 'OPTIMAL' },
  ],
  '/scm/expiry': [
    { lotNo: 'LOT-SMP-2608-01', itemCode: 'RM-SMP-002', itemName: 'Meniran Kering', expDate: '2026-12-15', daysLeft: 67, qty: 85, alert: 'SLOW_MOVING' },
    { lotNo: 'LOT-EKS-2605-02', itemCode: 'RM-EKS-002', itemName: 'Ekstrak Meniran', expDate: '2027-01-30', daysLeft: 113, qty: 25, alert: 'ATTENTION' },
  ],
  '/scm/report': {
    otifPct: 98.2,
    stockAccuracyPct: 99.4,
    forecastAccuracy: [
      { month: '2026-08', accuracy: 96.2 },
      { month: '2026-09', accuracy: 97.4 },
      { month: '2026-10', accuracy: 98.1 },
    ],
    aging: [
      { category: 'Bahan Baku', current: 1500000000, d30: 200000000, d60: 45000000, d90: 12000000 },
      { category: 'Produk Jadi', current: 2800000000, d30: 350000000, d60: 80000000, d90: 0 },
    ],
  },

  // PRE
  '/pre/labor': [
    { date: '2026-10-09', shift: 'Shift 1', line: 'Lini Kapsul', headcount: 8, standardHours: 56, actualHours: 56, efficiencyPct: 100 },
    { date: '2026-10-09', shift: 'Shift 1', line: 'Lini Ekstraksi', headcount: 4, standardHours: 28, actualHours: 28, efficiencyPct: 100 },
    { date: '2026-10-08', shift: 'Shift 2', line: 'Lini Kemas', headcount: 6, standardHours: 42, actualHours: 44, efficiencyPct: 95.5 },
  ],
  '/pre/process-report': [
    { batchNo: 'CR-2610-A', stage: 'Penimbangan', inputQty: 250, outputQty: 250, yieldPct: 100, operator: 'Agus P.', status: 'DONE' },
    { batchNo: 'CR-2610-A', stage: 'Granulasi & Pencampuran', inputQty: 250, outputQty: 247.5, yieldPct: 99.0, operator: 'Bambang S.', status: 'DONE' },
    { batchNo: 'CR-2610-A', stage: 'Pengisian Kapsul', inputQty: 247.5, outputQty: 244.8, yieldPct: 98.9, operator: 'Tri Wahyudi', status: 'IN_PROGRESS' },
  ],
  '/pre/reject-waste': [
    { date: '2026-10-08', batchNo: 'CR-2610-A', reason: 'Cangkang Kapsul Rusak Saat Start Mesin', qty: 2.1, unit: 'KG', disposition: 'SCRAP' },
    { date: '2026-10-07', batchNo: 'IM-2610-B', reason: 'Residu Ekstrak Penempel Dinding Tabung', qty: 1.2, unit: 'KG', disposition: 'SCRAP' },
  ],
  '/pre/pm-plan': [
    { machine: 'MCH-CAP-01 Capsule Machine', task: 'Pelumasan Cam & Penggantian O-ring', frequency: 'BULANAN', lastDone: '2026-09-15', nextDue: '2026-10-15', status: 'SCHEDULED' },
    { machine: 'MCH-EXT-01 Tangki Ekstraksi', task: 'Inspeksi Gasket Sanitary & Tekanan Steam', frequency: 'TRIWULAN', lastDone: '2026-08-01', nextDue: '2026-11-01', status: 'OK' },
  ],
  '/pre/utility-log': [
    { timestamp: '2026-10-09 08:00', zone: 'Cleanroom Kelas D', tempC: 21.4, rhPct: 44.2, diffPressurePa: 15.2, status: 'NORMAL' },
    { timestamp: '2026-10-09 12:00', zone: 'Cleanroom Kelas D', tempC: 21.8, rhPct: 45.1, diffPressurePa: 14.8, status: 'NORMAL' },
    { timestamp: '2026-10-09 08:00', zone: 'Sistem Air Purified (PW)', conductivity: 0.85, tocPpb: 120, ph: 6.8, status: 'NORMAL' },
  ],
  '/pre/capex': [
    { project: 'Modifikasi HVAC Cleanroom Grey Area', budget: 450000000, actual: 380000000, progressPct: 85, targetCompletion: '2026-11-30' },
    { project: 'Instalasi Otomasi Lini Blister Kemasan', budget: 850000000, actual: 820000000, progressPct: 95, targetCompletion: '2026-10-25' },
  ],
  '/pre/report': {
    oeePct: 88.4,
    availabilityPct: 94.2,
    performancePct: 95.8,
    qualityPct: 98.1,
    batchesCompleted: 14,
  },

  // FIN
  '/fin/statements': {
    assets: [
      { account: 'Kas & Setara Kas Bank', amount: 4850000000 },
      { account: 'Piutang Usaha', amount: 3200000000 },
      { account: 'Persediaan Bahan Baku & Barang Jadi', amount: 5600000000 },
      { account: 'Aset Tetap Pabrik & Mesin (Neto)', amount: 18400000000 },
    ],
    liabilities: [
      { account: 'Hutang Dagang Supplier', amount: 2150000000 },
      { account: 'Hutang Pajak PPN & PPh', amount: 380000000 },
      { account: 'Hutang Bank Jangka Panjang', amount: 4500000000 },
    ],
    equity: [
      { account: 'Modal Disetor', amount: 15000000000 },
      { account: 'Laba Ditahan', amount: 10020000000 },
    ],
  },
  '/fin/ap-aging': [
    { partner: 'PT Agro Herbal Nusantara', current: 280000000, d30: 45000000, d60: 0, d90: 0, total: 325000000 },
    { partner: 'PT Ekstraksi Alam Sejahtera', current: 420000000, d30: 0, d60: 0, d90: 0, total: 420000000 },
    { partner: 'PT Kemasan Farma Plastik', current: 115000000, d30: 25000000, d60: 0, d90: 0, total: 140000000 },
  ],
  '/fin/ar-aging': [
    { partner: 'PT Kimia Farma Trading', current: 850000000, d30: 120000000, d60: 0, d90: 0, total: 970000000 },
    { partner: 'PT Anugerah Pharmindo Lestari', current: 1240000000, d30: 85000000, d60: 0, d90: 0, total: 1325000000 },
  ],
  '/fin/cash-forecast': [
    { week: 'W41', opening: 4850000000, inflow: 1200000000, outflow: 950000000, closing: 5100000000 },
    { week: 'W42', opening: 5100000000, inflow: 850000000, outflow: 1100000000, closing: 4850000000 },
  ],

  // QMS
  '/qms/audit': [
    { auditNo: 'AUD-INT-2609', title: 'Audit Internal CPOB Semester 2 Lini Ekstraksi', auditor: 'Apt. Dewi Lestari', date: '2026-09-20', findings: 2, status: 'CLOSED' },
    { auditNo: 'AUD-EXT-2608', title: 'Surveilans Sertifikasi Halal LPPOM MUI', auditor: 'Auditor BPJPH', date: '2026-08-14', findings: 0, status: 'PASSED' },
  ],
  '/qms/risk': [
    { code: 'QRM-2610-01', process: 'Penyimpanan Ekstrak Peka Suhu', failureMode: 'Kerusakan Kompresor Cold Room', sev: 4, occ: 2, det: 2, rpn: 16, mitigation: 'Genset otomatis & sensor suhu IoT', status: 'MITIGATED' },
    { code: 'QRM-2609-02', process: 'Penimbangan Bahan Aktif', failureMode: 'Kesalahan Bobot Timbang', sev: 5, occ: 1, det: 1, rpn: 5, mitigation: 'Integrasi barcode scale dual sign-off', status: 'CONTROLLED' },
  ],
  '/qms/specs': [
    { itemCode: 'RM-SMP-001', parameter: 'Kadar Kurkuminoid', method: 'Spektrofotometri UV-Vis', minVal: '2.5%', maxVal: '–', status: 'ACTIVE' },
    { itemCode: 'RM-SMP-001', parameter: 'Kadar Air', method: 'Karl Fischer Titration', minVal: '–', maxVal: '10.0%', status: 'ACTIVE' },
    { itemCode: 'FG-TC-001', parameter: 'Keseragaman Bobot', method: 'Farmakope Herbal Ed. II', minVal: '480 mg', maxVal: '520 mg', status: 'ACTIVE' },
  ],
  '/qms/sampling': [
    { sampleNo: 'SMP-2610-001', lotNo: 'LOT-SMP-2609-01', item: 'Simplisia Temulawak', plan: 'AQL Normal Level II', sampleQty: '500g', sampler: 'Rudi H.', date: '2026-10-08', status: 'TESTED' },
    { sampleNo: 'SMP-2610-002', lotNo: 'LOT-FG-2610-01', item: 'HerbaCurcuma 60s', plan: 'Komposit Bets Akhir', sampleQty: '10 Botol', sampler: 'Rudi H.', date: '2026-10-09', status: 'IN_TESTING' },
  ],
  '/qms/test-result': [
    { sampleNo: 'SMP-2610-001', parameter: 'Kadar Air', result: '7.8%', spec: 'Maks. 10.0%', conclusion: 'MEMENUHI SYARAT', analyst: 'Nurul A.', verifiedBy: 'Apt. Siti R.' },
    { sampleNo: 'SMP-2610-001', parameter: 'Cemaran Logam Timbal (Pb)', result: '< 0.5 ppm', spec: 'Maks. 10.0 ppm', conclusion: 'MEMENUHI SYARAT', analyst: 'Nurul A.', verifiedBy: 'Apt. Siti R.' },
  ],
  '/qms/ipc': [
    { batchNo: 'CR-2610-A', stage: 'Pengisian Kapsul', checkTime: '09:30', weightAvgMg: 504.2, specRange: '480 - 520 mg', leakTest: 'LULUS', status: 'PASSED' },
    { batchNo: 'CR-2610-A', stage: 'Pengisian Kapsul', checkTime: '11:00', weightAvgMg: 502.8, specRange: '480 - 520 mg', leakTest: 'LULUS', status: 'PASSED' },
  ],
  '/qms/coa': [
    { coaNo: 'COA/FG/2610/004', batchNo: 'CR-2610-A', product: 'HerbaCurcuma Forte 500mg Kapsul', mfgDate: '2026-10-08', expDate: '2028-10-01', releaseStatus: 'RELEASED', releasedBy: 'Apt. Dewi Lestari' },
  ],
  '/qms/instrument': [
    { code: 'INS-HPLC-01', name: 'HPLC Waters Alliance e2695', location: 'Lab QC Kimia', calDate: '2026-06-12', calDueDate: '2027-06-12', status: 'CALIBRATED' },
    { code: 'INS-BAL-01', name: 'Timbangan Analitik Mettler Toledo MS205DU', location: 'Lab QC Fisika', calDate: '2026-08-05', calDueDate: '2027-08-05', status: 'CALIBRATED' },
  ],

  // RND
  '/rnd/dashboard': {
    activeProjects: 4,
    trialsRunning: 2,
    submissionsBpom: 1,
    pipeline: [
      { code: 'PRJ-26-01', name: 'Ekstrak Kombinasi Temulawak & Meniran Hepatoprotektor', gate: 'Scale-up Pabrik', targetLaunch: 'Q1 2027' },
      { code: 'PRJ-26-02', name: 'Herbal Effervescent Jahe Merah Imunostimulan', gate: 'Uji Stabilitas Akselerasi', targetLaunch: 'Q2 2027' },
    ],
  },
  '/rnd/ingredient-bank': [
    { id: 1, latinName: 'Curcuma xanthorrhiza Roxb.', localName: 'Temulawak', markerCompound: 'Kurkuminoid & Xanthorrhizol', partUsed: 'Rhizoma', monografi: 'FHI Ed. II hal. 421' },
    { id: 2, latinName: 'Phyllanthus niruri L.', localName: 'Meniran', markerCompound: 'Filantin & Hipofilantin', partUsed: 'Herba', monografi: 'FHI Ed. II hal. 288' },
    { id: 3, latinName: 'Zingiber officinale Roscoe var. rubrum', localName: 'Jahe Merah', markerCompound: 'Gingerol & Shogaol', partUsed: 'Rhizoma', monografi: 'FHI Ed. II hal. 195' },
  ],

  // HC
  '/hc/attendance': [
    { nik: 'HT-2022-001', name: 'Dimas Pratama', date: '2026-10-09', checkIn: '07:45', checkOut: '17:15', status: 'HADIR' },
    { nik: 'HT-2024-088', name: 'Hendro Wijaya', date: '2026-10-09', checkIn: '07:50', checkOut: '17:05', status: 'HADIR' },
    { nik: 'HT-2024-102', name: 'Siti Rahmawati', date: '2026-10-09', checkIn: '06:55', checkOut: '15:10', status: 'HADIR' },
  ],
  '/hc/salaries': [
    { nik: 'HT-2022-001', name: 'Dimas Pratama', dept: 'General Management', basicSalary: 25000000, allowance: 5000000, takeHome: 28500000 },
    { nik: 'HT-2024-088', name: 'Hendro Wijaya', dept: 'Produksi', basicSalary: 16000000, allowance: 3800000, takeHome: 18450000 },
  ],
  '/hc/sarmut': [
    { dept: 'Produksi', kpi: 'Efisiensi OEE Lini Pabrik', target: '≥ 85%', actual: '88.4%', score: 104.0, status: 'ACHIEVED' },
    { dept: 'SCM & Gudang', kpi: 'Ketepatan Stok Opname (Accuracy)', target: '≥ 99%', actual: '99.4%', score: 100.4, status: 'ACHIEVED' },
    { dept: 'Quality Assurance', kpi: 'Penyelesaian CAPA Tepat Waktu', target: '100%', actual: '100%', score: 100.0, status: 'ACHIEVED' },
  ],

  // GA
  '/ga/dashboard': {
    roomsBookedToday: 4,
    vehiclesInUse: 2,
    openTickets: 3,
    activeContracts: 6,
  },
  '/ga/room-booking': [
    { room: 'Ruang Rapat Curcuma (Kapasitas 16)', organizer: 'Dimas P.', topic: 'Review Go-Live ERP & SOP', start: '09:00', end: '11:00', status: 'CONFIRMED' },
    { room: 'Ruang Rapat Meniran (Kapasitas 8)', organizer: 'Dewi L.', topic: 'Rapat Koordinasi Release Bets', start: '13:30', end: '15:00', status: 'CONFIRMED' },
  ],

  // SYS
  '/sys/audit-trail': [
    { id: 1001, timestamp: '2026-10-09 10:15:22', user: 'dimas', table: 'scm.stock_move', action: 'INSERT', docNo: 'MR/P1/2610/00031', details: 'Mutasi bahan 250kg temulawak ke lini ekstraksi' },
    { id: 1002, timestamp: '2026-10-09 09:30:14', user: 'dewi.qa', table: 'qms.batch_release', action: 'STATUS_CHANGE', docNo: 'REL/2610/00004', details: 'Status bets CR-2610-A diluluskan (RELEASED)' },
  ],
  '/sys/integration-log': [
    { id: 501, timestamp: '2026-10-09 10:00:00', service: 'Mesin Timbangan Mettler Toledo IoT', status: 'SUCCESS', records: 12, responseTimeMs: 45 },
    { id: 502, timestamp: '2026-10-09 07:30:00', service: 'Mesin Absensi Biometrik ZKTeco', status: 'SUCCESS', records: 148, responseTimeMs: 120 },
  ],
};
