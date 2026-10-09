import type { Option, ResourceDef } from './types';

/** Master data M1 milik aplikasi (HC, FIN, PRE); endpoint MasterController di backend. */

const opt = (...pairs: [string, string][]): Option[] => pairs.map(([value, label]) => ({ value, label }));
const ACTIVE = { key: 'active', label: 'Status', type: 'active' as const, sortable: false };

// ---------------------------------------------------------------- HC

export const leaveTypes: ResourceDef = {
  key: 'leave-types', title: 'Jenis Cuti & Izin', endpoint: '/hc/leave-types', table: 'hc.leave_type', defaultSort: 'code',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'attendanceStatus', label: 'Status absensi' },
    { key: 'paid', label: 'Dibayar', type: 'bool' }, { key: 'deductsAnnual', label: 'Potong cuti tahunan', type: 'bool' },
    { key: 'maxDays', label: 'Maks. hari', type: 'number' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'attendanceStatus', label: 'Status di absensi', type: 'select', required: true, options: opt(['CUTI', 'Cuti'], ['IZIN', 'Izin'], ['SAKIT', 'Sakit'], ['DINAS', 'Dinas']) },
    { key: 'maxDays', label: 'Maksimum hari per pengajuan', type: 'number' },
    { key: 'paid', label: 'Tetap dibayar', type: 'bool' }, { key: 'deductsAnnual', label: 'Memotong saldo cuti tahunan', type: 'bool' },
    { key: 'requiresAttachment', label: 'Wajib lampiran (mis. surat dokter)', type: 'bool' }],
  defaults: { paid: true, attendanceStatus: 'CUTI' },
};

export const leaveEntitlements: ResourceDef = {
  key: 'leave-entitlements', title: 'Hak Cuti Tahunan', endpoint: '/hc/leave-entitlements', table: 'hc.leave_entitlement', defaultSort: '-year',
  titleOf: (r) => `Hak cuti ${r.year}`,
  columns: [{ key: 'employeeId', label: 'Karyawan', lookup: 'employees' }, { key: 'year', label: 'Tahun', mono: true },
    { key: 'days', label: 'Hak (hari)', type: 'number' }, { key: 'carriedOver', label: 'Carry over', type: 'number' }, ACTIVE],
  fields: [{ key: 'employeeId', label: 'Karyawan', type: 'lookup', lookup: 'employees', required: true, immutable: true },
    { key: 'year', label: 'Tahun', type: 'number', required: true, immutable: true },
    { key: 'days', label: 'Hak cuti (hari)', type: 'number', required: true }, { key: 'carriedOver', label: 'Sisa tahun lalu (carry over)', type: 'number' }],
  defaults: { year: new Date().getFullYear(), days: 12, carriedOver: 0 },
};

export const salaryComponents: ResourceDef = {
  key: 'salary-components', title: 'Komponen Gaji', endpoint: '/hc/salary-components', table: 'hc.salary_component', defaultSort: 'seq',
  columns: [{ key: 'seq', label: 'Urut', type: 'number' }, { key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' },
    { key: 'kind', label: 'Jenis' }, { key: 'fixed', label: 'Tetap', type: 'bool' }, { key: 'taxable', label: 'Objek PPh 21', type: 'bool' },
    { key: 'accountCode', label: 'Akun', mono: true }, { key: 'system', label: 'Sistem', type: 'bool' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'kind', label: 'Jenis', type: 'select', required: true, options: opt(['EARNING', 'Pendapatan'], ['DEDUCTION', 'Potongan'], ['EMPLOYER', 'Beban perusahaan']) },
    { key: 'seq', label: 'Urutan di slip', type: 'number' }, { key: 'accountCode', label: 'Kode akun GL (kosong = beban gaji)', help: 'Mis. 6101' },
    { key: 'fixed', label: 'Komponen tetap bulanan', type: 'bool' }, { key: 'taxable', label: 'Objek PPh 21', type: 'bool' }],
  defaults: { kind: 'EARNING', fixed: true, taxable: true, seq: 100 },
};

export const payrollParams: ResourceDef = {
  key: 'payroll-params', title: 'Parameter BPJS & Payroll', endpoint: '/hc/payroll-params', table: 'hc.payroll_param', defaultSort: 'key',
  titleOf: (r) => String(r.key),
  columns: [{ key: 'key', label: 'Parameter', mono: true }, { key: 'value', label: 'Nilai', mono: true }, { key: 'description', label: 'Keterangan' }, ACTIVE],
  fields: [{ key: 'key', label: 'Parameter', required: true, immutable: true }, { key: 'value', label: 'Nilai', required: true },
    { key: 'description', label: 'Keterangan', type: 'textarea', full: true }],
};

export const ptkp: ResourceDef = {
  key: 'ptkp', title: 'PTKP', endpoint: '/hc/ptkp', table: 'hc.ptkp', defaultSort: 'status',
  titleOf: (r) => `PTKP ${r.status}`,
  columns: [{ key: 'status', label: 'Status', mono: true }, { key: 'amount', label: 'PTKP setahun', type: 'money' }, { key: 'terCategory', label: 'Kategori TER', mono: true }, ACTIVE],
  fields: [{ key: 'status', label: 'Status (mis. TK/0, K/1)', required: true, immutable: true }, { key: 'amount', label: 'PTKP setahun', type: 'money', required: true },
    { key: 'terCategory', label: 'Kategori TER', type: 'select', required: true, options: opt(['A', 'A'], ['B', 'B'], ['C', 'C']) }],
};

export const pph21Ter: ResourceDef = {
  key: 'pph21-ter', title: 'Tarif Efektif (TER) PPh 21', endpoint: '/hc/pph21-ter', table: 'hc.pph21_ter', defaultSort: 'upperLimit',
  titleOf: (r) => `TER ${r.category} s.d. ${r.upperLimit}`,
  columns: [{ key: 'category', label: 'Kategori', mono: true }, { key: 'upperLimit', label: 'Bruto bulanan s.d.', type: 'money' },
    { key: 'rate', label: 'Tarif %', type: 'number' }, ACTIVE],
  fields: [{ key: 'category', label: 'Kategori', type: 'select', required: true, options: opt(['A', 'A'], ['B', 'B'], ['C', 'C']) },
    { key: 'upperLimit', label: 'Batas atas bruto bulanan', type: 'money', required: true, help: 'Lapisan terakhir: isi angka sangat besar' },
    { key: 'rate', label: 'Tarif (%)', type: 'number', required: true }],
};

export const qualifications: ResourceDef = {
  key: 'qualifications', title: 'Kualifikasi Operator', endpoint: '/hc/qualifications', table: 'hc.qualification', defaultSort: '-validUntil',
  titleOf: (r) => `Kualifikasi ${r.processCode}`,
  columns: [{ key: 'employeeId', label: 'Karyawan', lookup: 'employees' }, { key: 'processCode', label: 'Proses', mono: true },
    { key: 'lineId', label: 'Lini', lookup: 'lines' }, { key: 'validFrom', label: 'Berlaku', type: 'date' }, { key: 'validUntil', label: 's.d.', type: 'date' },
    { key: 'basis', label: 'Dasar' }, ACTIVE],
  fields: [{ key: 'employeeId', label: 'Karyawan', type: 'lookup', lookup: 'employees', required: true },
    { key: 'processCode', label: 'Kode proses (mis. FILLING, MIXING)', required: true },
    { key: 'lineId', label: 'Lini (kosong = semua lini)', type: 'lookup', lookup: 'lines' },
    { key: 'validFrom', label: 'Berlaku mulai', type: 'date', required: true }, { key: 'validUntil', label: 'Berlaku sampai', type: 'date', required: true },
    { key: 'basis', label: 'Dasar', type: 'select', options: opt(['TRAINING', 'Training'], ['ASESMEN', 'Asesmen'], ['SERTIFIKASI', 'Sertifikasi']) },
    { key: 'note', label: 'Catatan', type: 'textarea', full: true }],
  defaults: { basis: 'ASESMEN' },
};

export const contracts: ResourceDef = {
  key: 'contracts', title: 'Kontrak Kerja', endpoint: '/hc/contracts', table: 'hc.employment_contract', defaultSort: 'endDate',
  titleOf: (r) => String(r.contractNo),
  columns: [{ key: 'contractNo', label: 'No. kontrak', mono: true }, { key: 'employeeId', label: 'Karyawan', lookup: 'employees' },
    { key: 'type', label: 'Jenis' }, { key: 'startDate', label: 'Mulai', type: 'date' }, { key: 'endDate', label: 'Berakhir', type: 'date' },
    { key: 'positionId', label: 'Posisi', lookup: 'positions' }, ACTIVE],
  fields: [{ key: 'employeeId', label: 'Karyawan', type: 'lookup', lookup: 'employees', required: true },
    { key: 'contractNo', label: 'Nomor kontrak', required: true },
    { key: 'type', label: 'Jenis', type: 'select', required: true, options: opt(['PKWT', 'PKWT (kontrak)'], ['PKWTT', 'PKWTT (tetap)'], ['OS', 'Outsourcing'], ['MAGANG', 'Magang']) },
    { key: 'positionId', label: 'Posisi', type: 'lookup', lookup: 'positions' },
    { key: 'startDate', label: 'Mulai', type: 'date', required: true }, { key: 'endDate', label: 'Berakhir (kosong untuk PKWTT)', type: 'date' },
    { key: 'note', label: 'Catatan', type: 'textarea', full: true }],
  defaults: { type: 'PKWT' },
};

export const sarmutKpis: ResourceDef = {
  key: 'sarmut-kpis', title: 'KPI Sasaran Mutu', endpoint: '/hc/sarmut-kpis', table: 'hc.sarmut_kpi', defaultSort: 'code',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'appCode', label: 'Aplikasi', mono: true },
    { key: 'target', label: 'Target', type: 'number' }, { key: 'unit', label: 'Satuan' }, { key: 'direction', label: 'Arah' },
    { key: 'source', label: 'Sumber' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'appCode', label: 'Aplikasi pemilik', type: 'select', required: true,
      options: opt(['FIN', 'FIN'], ['HC', 'HC'], ['PRE', 'PRE'], ['SCM', 'SCM'], ['PRC', 'PRC'], ['QMS', 'QMS'], ['RND', 'RND'], ['GA', 'GA']) },
    { key: 'target', label: 'Target', type: 'number', required: true }, { key: 'unit', label: 'Satuan (%, hari, Rp)' },
    { key: 'direction', label: 'Arah baik', type: 'select', required: true, options: opt(['HIGHER', 'Makin tinggi makin baik'], ['LOWER', 'Makin rendah makin baik']) },
    { key: 'weight', label: 'Bobot', type: 'number' },
    { key: 'source', label: 'Sumber nilai', type: 'select', required: true, options: opt(['AUTO', 'Otomatis dari modul'], ['MANUAL', 'Input manual']) },
    { key: 'autoKey', label: 'Kunci KPI otomatis', help: 'Mis. FIN_DSO, HC_ATTENDANCE, PRE_YIELD' },
    { key: 'formula', label: 'Rumus / definisi', type: 'textarea', full: true }],
  defaults: { direction: 'HIGHER', source: 'MANUAL', weight: 1 },
};

// ---------------------------------------------------------------- FIN

export const bankAccounts: ResourceDef = {
  key: 'bank-accounts', title: 'Rekening Kas & Bank', endpoint: '/fin/bank-accounts', table: 'fin.bank_account', defaultSort: 'code',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'kind', label: 'Jenis' },
    { key: 'bankName', label: 'Bank' }, { key: 'accountNo', label: 'No. rekening', mono: true }, { key: 'glAccountId', label: 'Akun GL', lookup: 'accounts' },
    { key: 'plantId', label: 'Plant', lookup: 'plants' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'kind', label: 'Jenis', type: 'select', required: true, options: opt(['BANK', 'Bank'], ['KAS', 'Kas']) },
    { key: 'bankName', label: 'Nama bank' }, { key: 'accountNo', label: 'Nomor rekening' },
    { key: 'currencyCode', label: 'Mata uang' },
    { key: 'glAccountId', label: 'Akun GL', type: 'lookup', lookup: 'accounts', lookupFilters: { type: 'ASSET' }, required: true },
    { key: 'plantId', label: 'Plant', type: 'lookup', lookup: 'plants', required: true }],
  defaults: { kind: 'BANK', currencyCode: 'IDR' },
};

export const finParams: ResourceDef = {
  key: 'fin-params', title: 'Parameter Keuangan', endpoint: '/fin/params', table: 'fin.fin_param', defaultSort: 'key',
  titleOf: (r) => String(r.key),
  columns: [{ key: 'key', label: 'Parameter', mono: true }, { key: 'value', label: 'Nilai', mono: true }, { key: 'description', label: 'Keterangan' }, ACTIVE],
  fields: [{ key: 'key', label: 'Parameter', required: true, immutable: true }, { key: 'value', label: 'Nilai', required: true },
    { key: 'description', label: 'Keterangan', type: 'textarea', full: true }],
};

export const assetCategories: ResourceDef = {
  key: 'asset-categories', title: 'Kategori Aset', endpoint: '/fin/asset-categories', table: 'fin.asset_category', defaultSort: 'code',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'usefulLifeMonths', label: 'Umur (bln)', type: 'number' },
    { key: 'method', label: 'Metode' }, { key: 'fiscalGroup', label: 'Kelompok fiskal', mono: true }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'usefulLifeMonths', label: 'Umur manfaat komersial (bulan)', type: 'number', required: true },
    { key: 'method', label: 'Metode', type: 'select', options: opt(['SL', 'Garis lurus'], ['DDB', 'Saldo menurun ganda']) },
    { key: 'fiscalGroup', label: 'Kelompok fiskal', type: 'select', required: true,
      options: opt(['KEL1', 'Kelompok 1 (4 th)'], ['KEL2', 'Kelompok 2 (8 th)'], ['KEL3', 'Kelompok 3 (16 th)'], ['KEL4', 'Kelompok 4 (20 th)'],
        ['BANGUNAN_P', 'Bangunan permanen (20 th)'], ['BANGUNAN_NP', 'Bangunan non-permanen (10 th)']) },
    { key: 'assetAccountId', label: 'Akun aset', type: 'lookup', lookup: 'accounts', required: true },
    { key: 'accumAccountId', label: 'Akun akumulasi penyusutan', type: 'lookup', lookup: 'accounts', required: true },
    { key: 'expenseAccountId', label: 'Akun beban penyusutan', type: 'lookup', lookup: 'accounts', required: true }],
  defaults: { method: 'SL' },
};

// ---------------------------------------------------------------- PRE

export const lines: ResourceDef = {
  key: 'lines', title: 'Lini Produksi', endpoint: '/pre/lines', table: 'pre.line', defaultSort: 'code',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'processCode', label: 'Proses', mono: true },
    { key: 'capacityPerShift', label: 'Kapasitas/shift', type: 'number' }, { key: 'capacityUom', label: 'Satuan' },
    { key: 'costCenterId', label: 'Cost center', lookup: 'cost-centers' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'plantId', label: 'Plant', type: 'lookup', lookup: 'plants', required: true },
    { key: 'costCenterId', label: 'Cost center', type: 'lookup', lookup: 'cost-centers', required: true },
    { key: 'processCode', label: 'Kode proses (kualifikasi operator)', required: true },
    { key: 'capacityPerShift', label: 'Kapasitas per shift', type: 'number' }, { key: 'capacityUom', label: 'Satuan kapasitas' }],
};

export const productParams: ResourceDef = {
  key: 'product-params', title: 'Parameter Produk', endpoint: '/pre/product-params', table: 'pre.product_param',
  titleOf: (r) => `Parameter ${r.batchPrefix ?? ''}`,
  columns: [{ key: 'itemId', label: 'Produk', lookup: 'items' }, { key: 'batchPrefix', label: 'Prefiks batch', mono: true },
    { key: 'stdBatchSize', label: 'Batch standar', type: 'number' }, { key: 'minYieldPct', label: 'Yield min. %', type: 'number' },
    { key: 'defaultLineId', label: 'Lini default', lookup: 'lines' }, ACTIVE],
  fields: [{ key: 'itemId', label: 'Produk', type: 'lookup', lookup: 'items', lookupFilters: { type: 'FG,WIP' }, required: true, immutable: true },
    { key: 'batchPrefix', label: 'Prefiks nomor batch', required: true, help: 'Nomor batch = prefiks + yyMM + urut, mis. KP26100001' },
    { key: 'stdBatchSize', label: 'Ukuran batch standar', type: 'number' },
    { key: 'minYieldPct', label: 'Yield minimum (%)', type: 'number', required: true },
    { key: 'defaultLineId', label: 'Lini default', type: 'lookup', lookup: 'lines' }],
};

export const rejectReasons: ResourceDef = {
  key: 'reject-reasons', title: 'Alasan Reject', endpoint: '/pre/reject-reasons', table: 'pre.reject_reason', defaultSort: 'code',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'category', label: 'Kategori (4M)' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'category', label: 'Kategori', type: 'select', options: opt(['BAHAN', 'Bahan'], ['MESIN', 'Mesin'], ['PROSES', 'Proses / metode'], ['MANUSIA', 'Manusia']) }],
  defaults: { category: 'PROSES' },
};

export const labor: ResourceDef = {
  key: 'labor', title: 'Jam Kerja Lini', endpoint: '/pre/labor', table: 'pre.labor_entry', defaultSort: '-workDate',
  titleOf: (r) => `Jam kerja ${r.workDate}`,
  columns: [{ key: 'workDate', label: 'Tanggal', type: 'date' }, { key: 'woId', label: 'Work order', lookup: 'work-orders' },
    { key: 'employeeId', label: 'Karyawan', lookup: 'employees' }, { key: 'hours', label: 'Jam', type: 'number' }, { key: 'activity', label: 'Aktivitas' }, ACTIVE],
  fields: [{ key: 'woId', label: 'Work order (dirilis)', type: 'lookup', lookup: 'work-orders', required: true },
    { key: 'employeeId', label: 'Karyawan', type: 'lookup', lookup: 'employees', required: true },
    { key: 'workDate', label: 'Tanggal', type: 'date', required: true },
    { key: 'hours', label: 'Jam', type: 'number', required: true, help: 'Total per hari ≤ jam hadir di absensi (HC-07)' },
    { key: 'activity', label: 'Aktivitas' }],
};
