import type { DocConfig } from '../docs/types';

const STATUS_COL = { key: 'status', label: 'Status', type: 'status' as const, sortable: false };
const DOCNO_COL = { key: 'docNo', label: 'No. Dokumen', mono: true };

export const qmsDocumentDoc: DocConfig = {
  docType: 'QMSDOC',
  title: 'Dokumen Mutu & SOP',
  endpoint: '/qms/documents',
  listColumns: [DOCNO_COL, { key: 'title', label: 'Judul Dokumen' }, STATUS_COL],
  header: [
    { key: 'title', label: 'Judul Dokumen', required: true },
  ],
};

export const changeControlDoc: DocConfig = {
  docType: 'CC',
  title: 'Change Control',
  endpoint: '/qms/change-controls',
  listColumns: [
    DOCNO_COL,
    { key: 'type', label: 'Jenis' },
    { key: 'description', label: 'Deskripsi' },
    { key: 'riskScore', label: 'Skor Risiko', type: 'number' },
    STATUS_COL,
  ],
  header: [
    { key: 'type', label: 'Jenis Perubahan', required: true },
    { key: 'riskScore', label: 'Skor Risiko', type: 'number' },
    { key: 'description', label: 'Deskripsi', type: 'textarea', required: true },
    { key: 'impact', label: 'Dampak Perubahan', type: 'textarea', required: true },
  ],
};

export const deviationDoc: DocConfig = {
  docType: 'DEV',
  title: 'Deviasi',
  endpoint: '/qms/deviations',
  listColumns: [
    DOCNO_COL,
    { key: 'deviationClass', label: 'Kelas' },
    { key: 'sourceType', label: 'Sumber' },
    STATUS_COL,
  ],
  header: [
    {
      key: 'deviationClass',
      label: 'Kelas Deviasi',
      type: 'select',
      options: [
        { value: 'MINOR', label: 'Minor' },
        { value: 'MAJOR', label: 'Major' },
        { value: 'CRITICAL', label: 'Kritis' },
      ],
      required: true,
    },
    { key: 'sourceType', label: 'Tipe Sumber' },
    { key: 'lotIds', label: 'Nomor Lot Terdampak' },
    { key: 'rootCause', label: 'Akar Masalah', type: 'textarea' },
  ],
};

export const capaDoc: DocConfig = {
  docType: 'CAPA',
  title: 'CAPA',
  endpoint: '/qms/capas',
  listColumns: [
    DOCNO_COL,
    { key: 'dueDate', label: 'Tenggat', type: 'date' },
    { key: 'effectiveness', label: 'Efektivitas' },
    STATUS_COL,
  ],
  header: [
    { key: 'dueDate', label: 'Tenggat Waktu', type: 'date', required: true },
    { key: 'picId', label: 'Penanggung Jawab', type: 'lookup', lookup: 'employees' },
    { key: 'effectiveness', label: 'Evaluasi Efektivitas', type: 'textarea' },
  ],
};

export const complaintDoc: DocConfig = {
  docType: 'CMP',
  title: 'Keluhan & Recall',
  endpoint: '/qms/complaints',
  listColumns: [
    DOCNO_COL,
    { key: 'description', label: 'Deskripsi Keluhan' },
    { key: 'decision', label: 'Keputusan' },
    STATUS_COL,
  ],
  header: [
    { key: 'partnerId', label: 'Pelanggan / Pelapor', type: 'lookup', lookup: 'partners' },
    { key: 'lotId', label: 'Lot Produk', type: 'lookup', lookup: 'lots' },
    { key: 'description', label: 'Deskripsi Keluhan', type: 'textarea', required: true },
    { key: 'decision', label: 'Tindakan / Keputusan', type: 'textarea' },
  ],
};

export const batchReleaseDoc: DocConfig = {
  docType: 'BR',
  title: 'Pelulusan Batch',
  endpoint: '/qms/batch-releases',
  listColumns: [
    DOCNO_COL,
    { key: 'decision', label: 'Keputusan' },
    { key: 'reviewedBy', label: 'Di-review Oleh' },
    STATUS_COL,
  ],
  header: [
    { key: 'woId', label: 'Work Order', type: 'lookup', lookup: 'work-orders' },
    { key: 'lotId', label: 'Lot Produk', type: 'lookup', lookup: 'lots' },
    {
      key: 'decision',
      label: 'Keputusan',
      type: 'select',
      options: [
        { value: 'RELEASED', label: 'Diluluskan (Released)' },
        { value: 'REJECTED', label: 'Ditolak (Rejected)' },
        { value: 'QUARANTINE', label: 'Karantina Ulang' },
      ],
      required: true,
    },
    { key: 'reviewedBy', label: 'Di-review oleh' },
  ],
};

export const stabilityStudyDoc: DocConfig = {
  docType: 'STB',
  title: 'Uji Stabilitas',
  endpoint: '/qms/stability-studies',
  listColumns: [
    DOCNO_COL,
    { key: 'studyCondition', label: 'Kondisi' },
    { key: 'timepoints', label: 'Titik Waktu' },
    STATUS_COL,
  ],
  header: [
    { key: 'itemId', label: 'Produk', type: 'lookup', lookup: 'items', required: true },
    { key: 'lotId', label: 'Nomor Lot', type: 'lookup', lookup: 'lots' },
    { key: 'studyCondition', label: 'Kondisi Penyimpanan', required: true },
    { key: 'timepoints', label: 'Titik Waktu Pengujian' },
  ],
};