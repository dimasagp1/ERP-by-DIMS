import type { ReactNode } from 'react';

export type FieldType = 'text' | 'number' | 'money' | 'bool' | 'select' | 'lookup' | 'date' | 'time' | 'textarea' | 'email';

export interface Option { value: string; label: string }

export interface FieldDef {
  key: string;
  label: string;
  type?: FieldType;
  options?: Option[];
  /** Nama lookup backend (/api/lookup/{lookup}). */
  lookup?: string;
  lookupFilters?: Record<string, string>;
  required?: boolean;
  /** Tidak bisa diubah setelah dibuat (mis. kode). */
  immutable?: boolean;
  help?: string;
  full?: boolean;
}

export interface ColumnDef {
  key: string;
  label: string;
  type?: FieldType | 'status' | 'active';
  lookup?: string;
  options?: Option[];
  mono?: boolean;
  sortable?: boolean;
}

export type Row = Record<string, unknown> & { id: number; version?: number; active?: boolean };

export interface ResourceDef {
  key: string;
  title: string;
  endpoint: string;
  /** Nama tabel untuk tab riwayat perubahan (audit trail). */
  table: string;
  columns: ColumnDef[];
  fields: FieldDef[];
  filters?: FieldDef[];
  defaultSort?: string;
  defaults?: Record<string, unknown>;
  /** Judul baris di drawer. */
  titleOf?: (row: Row) => string;
  /** Tab tambahan di drawer untuk data yang sudah tersimpan. */
  extraTabs?: { key: string; label: string; render: (row: Row, reload: () => void) => ReactNode }[];
}
