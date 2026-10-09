import type { DocConfig } from '../docs/types';

const STATUS_COL = { key: 'status', label: 'Status', type: 'status' as const, sortable: false };
const DOCNO_COL = { key: 'docNo', label: 'No. Dokumen', mono: true };

export const projectDoc: DocConfig = {
  docType: 'PRJ',
  title: 'Proyek Pengembangan Produk',
  endpoint: '/rnd/projects',
  listColumns: [
    DOCNO_COL,
    { key: 'name', label: 'Nama Proyek' },
    { key: 'category', label: 'Kategori' },
    { key: 'stage', label: 'Tahap' },
    { key: 'targetLaunchDate', label: 'Target Launch', type: 'date' },
    STATUS_COL,
  ],
  header: [
    { key: 'name', label: 'Nama Proyek', required: true },
    { key: 'category', label: 'Kategori Produk', type: 'select', options: [
      { value: 'HERBAL_MEDICINE', label: 'Obat Herbal Terstandar' },
      { value: 'JAMU', label: 'Jamu Tradisional' },
      { value: 'FITOFARMAKA', label: 'Fitofarmaka' },
      { value: 'SUPPLEMENT', label: 'Suplemen Kesehatan' },
      { value: 'BEVERAGE', label: 'Minuman Herbal' },
    ], required: true },
    { key: 'stage', label: 'Tahap Proyek', type: 'select', options: [
      { value: 'IDEA', label: '1. Ide & Konsep' },
      { value: 'FORMULA', label: '2. Pengembangan Formula' },
      { value: 'TRIAL_LAB', label: '3. Trial Skala Lab' },
      { value: 'SCALE_UP', label: '4. Scale-up Pilot Plant' },
      { value: 'REGISTRATION', label: '5. Registrasi BPOM' },
      { value: 'LAUNCH', label: '6. Peluncuran Komersial' },
    ], required: true },
    { key: 'picId', label: 'PIC RnD', type: 'lookup', lookup: 'employees' },
    { key: 'targetLaunchDate', label: 'Target Tanggal Launch', type: 'date' },
    { key: 'description', label: 'Deskripsi & Sasaran', type: 'textarea' },
  ],
};

export const formulaDoc: DocConfig = {
  docType: 'FML',
  title: 'Master Formula & Versi',
  endpoint: '/rnd/formulas',
  listColumns: [
    DOCNO_COL,
    { key: 'formulaVersion', label: 'Versi', mono: true },
    { key: 'batchSize', label: 'Ukuran Batch', type: 'number' },
    { key: 'targetYieldPct', label: 'Target Yield %', type: 'number' },
    STATUS_COL,
  ],
  header: [
    { key: 'productItemId', label: 'Item Produk', type: 'lookup', lookup: 'items', required: true },
    { key: 'formulaVersion', label: 'Nomor Versi Formula', required: true },
    { key: 'batchSize', label: 'Ukuran Standar Batch', type: 'number', required: true },
    { key: 'uomId', label: 'Satuan (UoM)', type: 'lookup', lookup: 'uoms', required: true },
    { key: 'targetYieldPct', label: 'Target Rendemen/Yield (%)', type: 'number' },
    { key: 'notes', label: 'Catatan & Instruksi Pembuatan', type: 'textarea' },
  ],
};

export const trialDoc: DocConfig = {
  docType: 'TRL',
  title: 'Trial Lab & Scale-up',
  endpoint: '/rnd/trials',
  listColumns: [
    DOCNO_COL,
    { key: 'trialType', label: 'Tipe Trial' },
    { key: 'trialDate', label: 'Tanggal', type: 'date' },
    { key: 'decision', label: 'Keputusan' },
    STATUS_COL,
  ],
  header: [
    { key: 'projectId', label: 'Proyek RnD', type: 'lookup', lookup: 'projects' },
    { key: 'formulaId', label: 'Formula yang Diuji', type: 'lookup', lookup: 'formulas' },
    { key: 'trialType', label: 'Jenis Trial', type: 'select', options: [
      { value: 'LAB', label: 'Trial Laboratorium (Skala Kecil)' },
      { value: 'PILOT', label: 'Scale-up Pilot Plant' },
      { value: 'PRODUCTION_TEST', label: 'Uji Coba Lini Produksi' },
    ], required: true },
    { key: 'trialDate', label: 'Tanggal Trial', type: 'date', required: true },
    { key: 'decision', label: 'Keputusan', type: 'select', options: [
      { value: 'PASS', label: 'Lolos (Lanjut Tahap Berikutnya)' },
      { value: 'FAIL', label: 'Gagal (Perlu Reformulasi)' },
      { value: 'REPEAT', label: 'Uji Ulang' },
    ] },
    { key: 'resultSummary', label: 'Ringkasan Hasil Uji', type: 'textarea' },
    { key: 'parametersJson', label: 'Parameter & Observasi Proses', type: 'textarea' },
  ],
};

export const productSpecDoc: DocConfig = {
  docType: 'SPEC',
  title: 'Draf Spesifikasi Produk',
  endpoint: '/rnd/specs',
  listColumns: [
    DOCNO_COL,
    { key: 'specCategory', label: 'Kategori' },
    STATUS_COL,
  ],
  header: [
    { key: 'itemId', label: 'Item / Bahan', type: 'lookup', lookup: 'items', required: true },
    { key: 'specCategory', label: 'Kategori Spesifikasi', type: 'select', options: [
      { value: 'RAW_MATERIAL', label: 'Bahan Baku Herbal (Simplisia/Ekstrak)' },
      { value: 'PACKAGING', label: 'Bahan Kemas' },
      { value: 'BULK', label: 'Produk Ruahan' },
      { value: 'FINISHED_GOOD', label: 'Produk Jadi' },
    ], required: true },
    { key: 'parametersJson', label: 'Parameter Uji & Batas Mutu', type: 'textarea', required: true },
    { key: 'notes', label: 'Metode Pengujian & Referensi', type: 'textarea' },
  ],
};

export const productRegistrationDoc: DocConfig = {
  docType: 'REG',
  title: 'Registrasi Produk',
  endpoint: '/rnd/registrations',
  listColumns: [
    DOCNO_COL,
    { key: 'regType', label: 'Jenis' },
    { key: 'registrationNo', label: 'No. Izin Edar / BPOM' },
    { key: 'expiryDate', label: 'Kedaluwarsa', type: 'date' },
    STATUS_COL,
  ],
  header: [
    { key: 'itemId', label: 'Produk', type: 'lookup', lookup: 'items', required: true },
    { key: 'regType', label: 'Jenis Izin', type: 'select', options: [
      { value: 'BPOM_TR', label: 'BPOM Obat Tradisional (TR)' },
      { value: 'BPOM_SD', label: 'BPOM Suplemen Diet (SD)' },
      { value: 'BPOM_MD', label: 'BPOM Makanan/Minuman (MD)' },
      { value: 'HALAL', label: 'Sertifikasi Halal BPJPH' },
    ], required: true },
    { key: 'registrationNo', label: 'Nomor Registrasi / Izin Edar' },
    { key: 'halalNo', label: 'Nomor Ketetapan Halal' },
    { key: 'submissionDate', label: 'Tanggal Pengajuan', type: 'date' },
    { key: 'approvalDate', label: 'Tanggal Terbit', type: 'date' },
    { key: 'expiryDate', label: 'Masa Berlaku', type: 'date' },
  ],
};

export const artworkDoc: DocConfig = {
  docType: 'ART',
  title: 'Desain Kemasan & Artwork',
  endpoint: '/rnd/artworks',
  listColumns: [
    DOCNO_COL,
    { key: 'artworkCode', label: 'Kode Artwork' },
    { key: 'packagingVersion', label: 'Versi Kemasan' },
    STATUS_COL,
  ],
  header: [
    { key: 'itemId', label: 'Produk', type: 'lookup', lookup: 'items', required: true },
    { key: 'artworkCode', label: 'Kode Artwork', required: true },
    { key: 'packagingVersion', label: 'Versi Kemasan (mis. v1.2)' },
    { key: 'fileUrl', label: 'Tautan File Master Artwork / PDF' },
  ],
};

export const costEstimateDoc: DocConfig = {
  docType: 'CE',
  title: 'Estimasi Biaya Formula',
  endpoint: '/rnd/cost-estimates',
  listColumns: [
    DOCNO_COL,
    { key: 'materialCost', label: 'Biaya Bahan', type: 'money' },
    { key: 'totalCost', label: 'Total HPP', type: 'money' },
    { key: 'suggestedPrice', label: 'Saran Harga', type: 'money' },
    STATUS_COL,
  ],
  header: [
    { key: 'formulaId', label: 'Formula Acuan', type: 'lookup', lookup: 'formulas', required: true },
    { key: 'materialCost', label: 'Biaya Bahan Baku (Rp)', type: 'money' },
    { key: 'packagingCost', label: 'Biaya Kemasan (Rp)', type: 'money' },
    { key: 'overheadCost', label: 'Estimasi Biaya Proses & Overhead (Rp)', type: 'money' },
    { key: 'totalCost', label: 'Total Estimasi HPP per Batch/Unit (Rp)', type: 'money' },
    { key: 'targetMarginPct', label: 'Target Margin Kotor (%)', type: 'number' },
    { key: 'suggestedPrice', label: 'Saran Harga Jual (Rp)', type: 'money' },
  ],
};
