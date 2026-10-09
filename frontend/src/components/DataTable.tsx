import type { ReactNode } from 'react';
import { Icon } from './icons';

export interface Column<T> {
  key: string;
  label: string;
  render?: (row: T) => ReactNode;
  align?: 'left' | 'right';
  mono?: boolean;
  /** Kolom urut server, mis. "docDate". Kosong = tidak bisa diurutkan. */
  sortKey?: string;
  width?: number | string;
}

/** Tabel padat dengan urut server & paginasi (PRD §15.2 level 2). */
export function DataTable<T>({ columns, rows, rowKey, onRowClick, sort, onSort, empty, footer }: {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string | number;
  onRowClick?: (row: T) => void;
  sort?: string;
  onSort?: (sort: string) => void;
  empty?: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <div className="table-wrap">
      <table className="t">
        <thead>
          <tr>
            {columns.map((c) => {
              const active = Boolean(c.sortKey) && (sort === c.sortKey || sort === `-${c.sortKey}`);
              const desc = sort === `-${c.sortKey}`;
              return (
                <th key={c.key} style={{ textAlign: c.align, width: c.width }}
                  className={c.sortKey && onSort ? 'sortable' : undefined}
                  aria-sort={active ? (desc ? 'descending' : 'ascending') : undefined}
                  onClick={c.sortKey && onSort ? () => onSort(active && !desc ? `-${c.sortKey}` : c.sortKey!) : undefined}>
                  {c.label}{active && <span aria-hidden="true"> {desc ? '↓' : '↑'}</span>}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 && (
            <tr><td colSpan={columns.length} style={{ height: 'auto' }}><div className="empty">{empty ?? 'Belum ada data'}</div></td></tr>
          )}
          {rows.map((r) => (
            <tr key={rowKey(r)} className={onRowClick ? 'clickable' : undefined} onClick={onRowClick ? () => onRowClick(r) : undefined}
              tabIndex={onRowClick ? 0 : undefined} onKeyDown={onRowClick ? (e) => e.key === 'Enter' && onRowClick(r) : undefined}>
              {columns.map((c) => (
                <td key={c.key} className={[c.mono ? 'mono' : '', c.align === 'right' ? 'num' : ''].join(' ').trim() || undefined}
                  style={c.mono ? { fontSize: 12.5, whiteSpace: 'nowrap' } : undefined}>
                  {c.render ? c.render(r) : String((r as Record<string, unknown>)[c.key] ?? '')}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
        {footer && <tfoot>{footer}</tfoot>}
      </table>
    </div>
  );
}

export function Pager({ page, totalPages, total, size, onPage }: {
  page: number; totalPages: number; total: number; size: number; onPage: (p: number) => void;
}) {
  const from = total === 0 ? 0 : page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  return (
    <div className="row" style={{ padding: '10px 16px', fontSize: 12.5, color: 'var(--text-3)' }}>
      <span className="mono">{from}–{to} dari {total.toLocaleString('id-ID')} baris</span>
      <span className="spacer" />
      <button className="btn btn-sm" type="button" disabled={page <= 0} onClick={() => onPage(page - 1)}>
        <Icon name="arrowLeft" size={14} />Sebelumnya
      </button>
      <span className="mono">{page + 1}/{Math.max(totalPages, 1)}</span>
      <button className="btn btn-sm" type="button" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Berikutnya</button>
    </div>
  );
}

/** Ekspor CSV (dibuka Excel) dari baris yang sudah diambil. */
export function exportCsv(filename: string, headers: string[], rows: (string | number | null | undefined)[][]) {
  const esc = (v: string | number | null | undefined) => {
    const s = v == null ? '' : String(v);
    return /[";\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const csv = '﻿' + [headers, ...rows].map((r) => r.map(esc).join(';')).join('\r\n');
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
