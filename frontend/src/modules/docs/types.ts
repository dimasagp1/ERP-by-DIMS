import type { ComponentType } from 'react';
import type { ColumnDef, FieldDef } from '../master/types';

export type Doc = Record<string, unknown> & { id?: number };

export interface DocMeta {
  docType: string;
  docNo: string;
  status: string;
  actions: string[];
  createdByName: string | null;
  createdAt: string;
  submittedAt: string | null;
  approvedByName: string | null;
  approvedAt: string | null;
  postedByName: string | null;
  postedAt: string | null;
  cancelReason: string | null;
  version: number;
  canEditAll: boolean;
}

export interface Envelope {
  doc: Doc;
  meta: DocMeta;
}

export interface DocField extends FieldDef {
  /** Selalu tampil baca-saja (nilai hitungan server). */
  readOnly?: boolean;
  /** Tampilkan hanya bila kondisi terpenuhi. */
  show?: (doc: Doc) => boolean;
  /** Hanya untuk staf yang membuat atas nama orang lain (layanan mandiri mengisi otomatis). */
  staffOnly?: boolean;
  /** Filter lookup yang bergantung pada isi dokumen, mis. faktur milik supplier terpilih. */
  lookupFiltersFrom?: (doc: Doc) => Record<string, string>;
  /** Filter lookup per baris, mis. lot milik item di baris yang sama. */
  rowFilters?: (row: Doc, doc: Doc) => Record<string, string>;
}

export interface LineSection {
  key: string;
  title: string;
  fields: DocField[];
  newRow?: () => Doc;
  addLabel?: string;
  /** Kolom tampilan dari server, mis. nama karyawan. */
  display?: { key: string; label: string; type?: 'money' | 'number' | 'date' | 'text' | 'bool' }[];
  show?: (doc: Doc) => boolean;
}

export interface SummaryItem {
  key: string;
  label: string;
  type?: 'money' | 'number' | 'date' | 'text' | 'pct';
  show?: (doc: Doc) => boolean;
}

export interface ExtraProps {
  doc: Doc;
  meta?: DocMeta;
  editing: boolean;
  reload: () => void;
  setDoc: (updater: (d: Doc) => Doc) => void;
}

export interface DocAction {
  label: string;
  show: (doc: Doc, meta: DocMeta) => boolean;
  run: (doc: Doc) => Promise<unknown>;
  primary?: boolean;
}

export interface DocConfig {
  docType: string;
  title: string;
  endpoint: string;
  listColumns: ColumnDef[];
  listFilters?: FieldDef[];
  /** Parameter tetap untuk daftar, mis. kind=ADVANCE. */
  listParams?: Record<string, string>;
  /** Daftar "milik saya" (Layanan Saya). */
  mine?: boolean;
  header: DocField[];
  lines?: LineSection[];
  summary?: SummaryItem[];
  defaults?: Doc;
  extra?: ComponentType<ExtraProps>;
  actions?: DocAction[];
  /** Judul di header form, mis. nama karyawan. */
  subtitle?: (doc: Doc) => string | undefined;
}

export interface MenuTab {
  key: string;
  label: string;
  doc?: DocConfig;
  component?: ComponentType;
}
