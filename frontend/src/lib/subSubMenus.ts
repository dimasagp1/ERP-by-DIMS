/**
/**
 * Taksonomi Sub-Sub Menu untuk seluruh 10 modul Herbatech ERP.
 * Mengelompokkan 167 menu ke dalam kategori sub-sub menu hierarkis bergaya Odoo/SAP.
 */

export interface SubSubGroupDef {
  title: string;
  icon: string;
  codes: string[];
}

export const SUB_SUB_TAXONOMY: Record<string, Record<string, SubSubGroupDef[]>> = {
  PRE: {
    Produksi: [
      {
        title: 'Perintah & Catatan Bets',
        icon: '📋',
        codes: ['PRE-02', 'PRE-03', 'PRE-13'],
      },
      {
        title: 'Bahan & Penimbangan',
        icon: '⚖️',
        codes: ['PRE-04', 'PRE-05', 'PRE-10'],
      },
      {
        title: 'Pengawasan Proses (IPC)',
        icon: '🔬',
        codes: ['PRE-06', 'PRE-07'],
      },
      {
        title: 'Output & Kinerja Lini',
        icon: '⚙️',
        codes: ['PRE-08', 'PRE-09', 'PRE-11', 'PRE-12'],
      },
    ],
    Engineering: [
      {
        title: 'Aset & Kualifikasi Mesin',
        icon: '🏭',
        codes: ['PRE-20', 'PRE-25', 'PRE-27'],
      },
      {
        title: 'Perawatan & Work Order',
        icon: '🔧',
        codes: ['PRE-21', 'PRE-22', 'PRE-23'],
      },
      {
        title: 'Fasilitas & Log Utilitas',
        icon: '⚡',
        codes: ['PRE-24', 'PRE-26'],
      },
    ],
    Laporan: [
      {
        title: 'Laporan Produksi & Maintenance',
        icon: '📊',
        codes: ['PRE-90'],
      },
    ],
  },
  SCM: {
    PPIC: [
      {
        title: 'Pesanan & Peramalan',
        icon: '📈',
        codes: ['SCM-02', 'SCM-03'],
      },
      {
        title: 'Jadwal Induk & Kapasitas',
        icon: '🗓️',
        codes: ['SCM-04', 'SCM-06'],
      },
      {
        title: 'Kebutuhan Material & Rilis',
        icon: '📦',
        codes: ['SCM-05', 'SCM-07', 'SCM-08'],
      },
    ],
    Warehouse: [
      {
        title: 'Penerimaan (Inbound)',
        icon: '📥',
        codes: ['SCM-20', 'SCM-22', 'SCM-24'],
      },
      {
        title: 'Pengeluaran & Delivery',
        icon: '📤',
        codes: ['SCM-23', 'SCM-25', 'SCM-26'],
      },
      {
        title: 'Retur & Pengeluaran Khusus',
        icon: '🔄',
        codes: ['SCM-27', 'SCM-28', 'SCM-29'],
      },
    ],
    'Inventory Control': [
      {
        title: 'Status Stok & Karantina',
        icon: '🛡️',
        codes: ['SCM-21', 'SCM-44'],
      },
      {
        title: 'Opname Fisik & Mutasi',
        icon: '📝',
        codes: ['SCM-40', 'SCM-41', 'SCM-42', 'SCM-43'],
      },
      {
        title: 'Penelusuran Lot & Pemusnahan',
        icon: '🔍',
        codes: ['SCM-45', 'SCM-46'],
      },
    ],
    Laporan: [
      {
        title: 'Laporan Supply Chain',
        icon: '📊',
        codes: ['SCM-90'],
      },
    ],
  },
  PRC: {
    Permintaan: [
      {
        title: 'Pengajuan Kebutuhan',
        icon: '📝',
        codes: ['PRC-02'],
      },
    ],
    Supplier: [
      {
        title: 'Kualifikasi & Vendor Terdaftar',
        icon: '🤝',
        codes: ['PRC-03', 'PRC-04'],
      },
    ],
    Sourcing: [
      {
        title: 'Permintaan & Evaluasi Penawaran',
        icon: '💼',
        codes: ['PRC-05', 'PRC-06'],
      },
    ],
    Pembelian: [
      {
        title: 'Purchase Order & Monitoring',
        icon: '🛒',
        codes: ['PRC-07', 'PRC-08', 'PRC-09'],
      },
      {
        title: 'Impor, BAST & Retur',
        icon: '🚢',
        codes: ['PRC-10', 'PRC-11', 'PRC-12'],
      },
    ],
    Evaluasi: [
      {
        title: 'Penilaian Kinerja',
        icon: '⭐',
        codes: ['PRC-13'],
      },
    ],
    Laporan: [
      {
        title: 'Laporan Pengadaan',
        icon: '📊',
        codes: ['PRC-90'],
      },
    ],
  },
  FIN: {
    Akuntansi: [
      {
        title: 'Bagan Akun & Master GL',
        icon: '📚',
        codes: ['FIN-02'],
      },
      {
        title: 'Jurnal & Buku Besar',
        icon: '📖',
        codes: ['FIN-03', 'FIN-04'],
      },
    ],
    'Hutang & Piutang': [
      {
        title: 'Hutang Usaha (AP)',
        icon: '💳',
        codes: ['FIN-10', 'FIN-11', 'FIN-12'],
      },
      {
        title: 'Piutang Usaha (AR)',
        icon: '💰',
        codes: ['FIN-20', 'FIN-21', 'FIN-22'],
      },
    ],
    'Kas, Aset & Anggaran': [
      {
        title: 'Treasury & Kas Bank',
        icon: '🏦',
        codes: ['FIN-30', 'FIN-31', 'FIN-32'],
      },
      {
        title: 'Aset Tetap & Anggaran',
        icon: '🏢',
        codes: ['FIN-40', 'FIN-50', 'FIN-51'],
      },
    ],
    Biaya: [
      {
        title: 'Standard Costing & HPP',
        icon: '🏷️',
        codes: ['FIN-52', 'FIN-53', 'FIN-54', 'FIN-55'],
      },
    ],
    Pajak: [
      {
        title: 'PPN & PPh',
        icon: '🧾',
        codes: ['FIN-60', 'FIN-61'],
      },
      {
        title: 'Bukti Potong & Fiskal',
        icon: '📁',
        codes: ['FIN-62', 'FIN-63'],
      },
    ],
    'Closing & Laporan': [
      {
        title: 'Tutup Buku & Laporan Keuangan',
        icon: '📑',
        codes: ['FIN-70', 'FIN-71', 'FIN-72'],
      },
    ],
  },
  QMS: {
    'QA & Compliance': [
      {
        title: 'Dokumen Mutu & Perubahan',
        icon: '📄',
        codes: ['QMS-01', 'QMS-02'],
      },
      {
        title: 'Deviasi & Tindakan Korektif',
        icon: '⚠️',
        codes: ['QMS-03', 'QMS-04'],
      },
      {
        title: 'Keluhan, Audit & Risiko',
        icon: '🛡️',
        codes: ['QMS-05', 'QMS-06', 'QMS-07'],
      },
    ],
    'Pengawasan Mutu (QC)': [
      {
        title: 'Spesifikasi & Pelulusan Bets',
        icon: '✅',
        codes: ['QMS-08', 'QMS-09'],
      },
      {
        title: 'Pengujian & Laboratorium',
        icon: '🧪',
        codes: ['QMS-10', 'QMS-11', 'QMS-12'],
      },
      {
        title: 'CoA, Instrumen & Stabilitas',
        icon: '🔬',
        codes: ['QMS-13', 'QMS-14', 'QMS-15'],
      },
    ],
  },
  RND: {
    Proyek: [
      {
        title: 'Manajemen Proyek R&D',
        icon: '🚀',
        codes: ['RND-01', 'RND-02'],
      },
    ],
    'Formula & Trial': [
      {
        title: 'Master Formula & BOM',
        icon: '🧬',
        codes: ['RND-03', 'RND-04'],
      },
      {
        title: 'Bahan Trial & Scale-up',
        icon: '🧪',
        codes: ['RND-05', 'RND-06'],
      },
    ],
    'Spesifikasi & Regulasi': [
      {
        title: 'Spesifikasi & Stabilitas',
        icon: '📋',
        codes: ['RND-07', 'RND-08'],
      },
      {
        title: 'Registrasi BPOM & Halal',
        icon: '🏛️',
        codes: ['RND-09'],
      },
    ],
    'Kemasan & Master': [
      {
        title: 'Artwork & Item Baru',
        icon: '🎨',
        codes: ['RND-10', 'RND-11'],
      },
    ],
    'Biaya & Perubahan': [
      {
        title: 'Kalkulasi & Usulan Perubahan',
        icon: '💡',
        codes: ['RND-12', 'RND-13', 'RND-14'],
      },
    ],
    Laporan: [
      {
        title: 'Laporan RnD',
        icon: '📊',
        codes: ['RND-90'],
      },
    ],
  },
  HC: {
    Organisasi: [
      {
        title: 'Struktur Organisasi & Pegawai',
        icon: '👥',
        codes: ['HC-02', 'HC-03'],
      },
      {
        title: 'Onboarding & Kontrak Kerja',
        icon: '📝',
        codes: ['HC-05', 'HC-06'],
      },
    ],
    Rekrutmen: [
      {
        title: 'Lowongan & Penerimaan',
        icon: '🎯',
        codes: ['HC-04'],
      },
    ],
    Waktu: [
      {
        title: 'Kehadiran & Cuti Lembur',
        icon: '⏱️',
        codes: ['HC-07', 'HC-08'],
      },
    ],
    Payroll: [
      {
        title: 'Gaji, Benefit & Kasbon',
        icon: '💵',
        codes: ['HC-09', 'HC-10', 'HC-14'],
      },
    ],
    'Pengembangan & Kinerja': [
      {
        title: 'Training & Kualifikasi',
        icon: '🎓',
        codes: ['HC-11', 'HC-12'],
      },
      {
        title: 'Sasaran Mutu (SARMUT) & Disiplin',
        icon: '🏆',
        codes: ['HC-13', 'HC-15'],
      },
    ],
    Laporan: [
      {
        title: 'Laporan Human Capital',
        icon: '📊',
        codes: ['HC-90'],
      },
    ],
  },
  GA: {
    Aset: [
      {
        title: 'Inventaris & Aset Fasilitas',
        icon: '🪑',
        codes: ['GA-02'],
      },
    ],
    Layanan: [
      {
        title: 'Konsumabel & Transportasi',
        icon: '🚗',
        codes: ['GA-03', 'GA-04'],
      },
      {
        title: 'Ruang Rapat, Katering & Perjalanan',
        icon: '🏢',
        codes: ['GA-05', 'GA-11', 'GA-12'],
      },
    ],
    Fasilitas: [
      {
        title: 'Gedung & Sanitasi Lingkungan',
        icon: '🧹',
        codes: ['GA-06', 'GA-07'],
      },
    ],
    'Keamanan & Legal': [
      {
        title: 'Buku Tamu, Legal & Kontrak',
        icon: '📜',
        codes: ['GA-08', 'GA-09', 'GA-10'],
      },
    ],
    K3: [
      {
        title: 'Keselamatan Kerja (HSSE)',
        icon: '🦺',
        codes: ['GA-13'],
      },
    ],
    Laporan: [
      {
        title: 'Laporan Umum & Fasilitas',
        icon: '📊',
        codes: ['GA-90'],
      },
    ],
  },
  SYS: {
    'Organisasi & Akses': [
      {
        title: 'Perusahaan & Struktur',
        icon: '🏛️',
        codes: ['SYS-01', 'SYS-02'],
      },
      {
        title: 'Keamanan, Peran & Penomoran',
        icon: '🔐',
        codes: ['SYS-03', 'SYS-04', 'SYS-05'],
      },
    ],
    'Master Data': [
      {
        title: 'Katalog Barang & Satuan',
        icon: '📦',
        codes: ['SYS-06', 'SYS-07'],
      },
      {
        title: 'Mitra Bisnis & Lokasi Gudang',
        icon: '📍',
        codes: ['SYS-08', 'SYS-09'],
      },
      {
        title: 'Kalender, Kurs & Pajak',
        icon: '⚙️',
        codes: ['SYS-10', 'SYS-11', 'SYS-12'],
      },
    ],
    'Integrasi & Audit': [
      {
        title: 'API, Audit Trail & Template',
        icon: '🔌',
        codes: ['SYS-13', 'SYS-14', 'SYS-15'],
      },
    ],
  },
  ESS: {
    'Waktu & Gaji': [
      {
        title: 'Kehadiran & Slip Mandiri',
        icon: '👤',
        codes: ['ESS-01', 'ESS-02', 'ESS-03', 'ESS-09'],
      },
    ],
    Permintaan: [
      {
        title: 'Permintaan Fasilitas & Barang',
        icon: '📥',
        codes: ['ESS-04', 'ESS-05', 'ESS-06', 'ESS-07', 'ESS-08'],
      },
    ],
    Approval: [
      {
        title: 'Persetujuan Dokumen',
        icon: '✍️',
        codes: ['ESS-10'],
      },
    ],
  },
};

/**
 * Mencari nama sub-sub kategori berdasarkan kode menu dan modul.
 */
export function findSubSubCategory(appCode: string, menuCode: string): { groupName: string; subSubTitle: string; icon: string } | null {
  const appTax = SUB_SUB_TAXONOMY[appCode];
  if (!appTax) return null;

  for (const [groupName, subSubs] of Object.entries(appTax)) {
    for (const sub of subSubs) {
      if (sub.codes.includes(menuCode)) {
        return { groupName, subSubTitle: sub.title, icon: sub.icon };
      }
    }
  }
  return null;
}
