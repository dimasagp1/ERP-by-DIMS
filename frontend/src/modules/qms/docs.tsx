import { type DocConfig } from '../docs/types';
import { DocPage } from '../docs/DocPage';

export const qmsDocumentDoc: DocConfig = {
  docType: 'QMSDOC',
  title: 'Dokumen Mutu',
  apiPath: '/api/qms/documents',
  statusConfig: 'standard',
  schema: {
    title: { type: 'string', label: 'Judul Dokumen', required: true }
  }
};

export const changeControlDoc: DocConfig = {
  docType: 'CC',
  title: 'Change Control',
  apiPath: '/api/qms/change-controls',
  statusConfig: 'standard',
  schema: {
    type: { type: 'string', label: 'Jenis Perubahan', required: true },
    description: { type: 'text', label: 'Deskripsi', required: true },
    impact: { type: 'text', label: 'Dampak Perubahan', required: true },
    riskScore: { type: 'number', label: 'Skor Risiko' }
  }
};

export const deviationDoc: DocConfig = {
  docType: 'DEV',
  title: 'Deviasi',
  apiPath: '/api/qms/deviations',
  statusConfig: 'standard',
  schema: {
    sourceType: { type: 'string', label: 'Sumber' },
    deviationClass: { type: 'string', label: 'Kelas' },
    rootCause: { type: 'text', label: 'Akar Masalah' },
    lotIds: { type: 'string', label: 'Lot Terdampak' }
  }
};

export const capaDoc: DocConfig = {
  docType: 'CAPA',
  title: 'CAPA',
  apiPath: '/api/qms/capas',
  statusConfig: 'standard',
  schema: {
    dueDate: { type: 'date', label: 'Tenggat Waktu' },
    effectiveness: { type: 'text', label: 'Evaluasi Efektivitas' }
  }
};

export const complaintDoc: DocConfig = {
  docType: 'CMP',
  title: 'Keluhan & Recall',
  apiPath: '/api/qms/complaints',
  statusConfig: 'standard',
  schema: {
    description: { type: 'text', label: 'Deskripsi Keluhan', required: true },
    decision: { type: 'text', label: 'Keputusan' }
  }
};

export const batchReleaseDoc: DocConfig = {
  docType: 'BRL',
  title: 'Pelulusan Batch',
  apiPath: '/api/qms/batch-releases',
  statusConfig: 'standard',
  schema: {
    decision: { type: 'string', label: 'Keputusan (Rilis/Tolak)', required: true },
    reviewedBy: { type: 'string', label: 'Di-review oleh' }
  }
};

export const stabilityStudyDoc: DocConfig = {
  docType: 'STB',
  title: 'Uji Stabilitas',
  apiPath: '/api/qms/stability-studies',
  statusConfig: 'standard',
  schema: {
    studyCondition: { type: 'string', label: 'Kondisi Penyimpanan', required: true },
    timepoints: { type: 'string', label: 'Titik Waktu' }
  }
};