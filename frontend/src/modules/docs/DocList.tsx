import { useEffect, useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { Page } from '../../api/types';
import { DataTable, exportCsv, Pager, type Column } from '../../components/DataTable';
import { Icon } from '../../components/icons';
import { useLookupLabels } from '../../components/Lookup';
import { StatusChip } from '../../components/StatusChip';
import { errorText, useDebounced, useToast } from '../../components/ui';
import { fmtDate, fmtNumber, fmtRp, todayIso } from '../../lib/format';
import type { ColumnDef } from '../master/types';
import type { Doc, DocConfig } from './types';

const STATUS = [['', 'Semua'], ['DRAFT', 'Draft'], ['SUBMITTED', 'Diajukan'], ['APPROVED', 'Disetujui'], ['POSTED', 'Diposting'],
  ['DONE', 'Selesai'], ['REJECTED', 'Ditolak'], ['CANCELLED', 'Dibatalkan']];

export function formatCell(type: string | undefined, v: unknown): string {
  if (v == null || v === '') return '';
  switch (type) {
    case 'money': return fmtRp(v as number);
    case 'number': return fmtNumber(v as number);
    case 'date': return fmtDate(String(v));
    case 'bool': return v ? 'Ya' : '–';
    case 'pct': return `${fmtNumber(v as number)}%`;
    default: return String(v);
  }
}

/** Kolom daftar dengan label lookup (maks. 2 kolom FK per daftar). */
function useColumns(defs: ColumnDef[], rows: Doc[]): Column<Doc>[] {
  const fk = defs.filter((c) => c.lookup);
  const m0 = useLookupLabels(fk[0]?.lookup, rows.map((r) => (fk[0] ? (r[fk[0].key] as number) : null)));
  const m1 = useLookupLabels(fk[1]?.lookup, rows.map((r) => (fk[1] ? (r[fk[1].key] as number) : null)));
  return useMemo(() => defs.map((c) => ({
    key: c.key,
    label: c.label,
    mono: c.mono,
    align: c.type === 'money' || c.type === 'number' ? 'right' as const : undefined,
    sortKey: c.sortable === false || c.lookup ? undefined : c.key,
    render: (r: Doc) => {
      const v = r[c.key];
      if (c.lookup) {
        const o = (c.key === fk[0]?.key ? m0 : m1).get(v as number);
        return o ? `${o.code} · ${o.name}` : v == null ? '' : `#${v}`;
      }
      if (c.type === 'status') return v ? <StatusChip status={String(v)} /> : '';
      if (c.type === 'select') return c.options?.find((o) => o.value === v)?.label ?? String(v ?? '');
      return formatCell(c.type, v);
    },
  })), [defs, fk, m0, m1]);
}

/** Daftar dokumen (PRD §15.2 level 2): filter status & periode, cari, ekspor, Baru. */
export function DocList({ cfg, onOpen, onNew }: { cfg: DocConfig; onOpen: (id: number) => void; onNew: () => void }) {
  const toast = useToast();
  const [q, setQ] = useState('');
  const term = useDebounced(q, 300);
  const [status, setStatus] = useState('');
  const [month, setMonth] = useState('');
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState('-docDate');
  useEffect(() => setPage(0), [term, status, month, filters, sort]);
  const range = useMemo(() => {
    if (!month) return {};
    const [y, m] = month.split('-').map(Number);
    return { from: `${month}-01`, to: `${month}-${String(new Date(y, m, 0).getDate()).padStart(2, '0')}` };
  }, [month]);
  const params = { q: term, status, page, size: 50, sort, mine: cfg.mine ? 'true' : undefined, ...cfg.listParams, ...filters, ...range };
  const query = useQuery({
    queryKey: ['docs', cfg.endpoint, params],
    queryFn: () => api.get<Page<Doc>>(cfg.endpoint, params),
    placeholderData: keepPreviousData,
  });
  const rows = query.data?.content ?? [];
  const columns = useColumns(cfg.listColumns, rows);

  useEffect(() => {
    const h = (e: KeyboardEvent) => { if (e.altKey && e.key.toLowerCase() === 'n') { e.preventDefault(); onNew(); } };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [onNew]);

  const doExport = async () => {
    try {
      const all = await api.get<Page<Doc>>(cfg.endpoint, { ...params, page: 0, size: 10000 });
      exportCsv(`${cfg.docType.toLowerCase()}-${todayIso()}.csv`, cfg.listColumns.map((c) => c.label),
        all.content.map((r) => cfg.listColumns.map((c) => {
          const v = r[c.key];
          return v == null ? null : typeof v === 'number' ? v : String(v);
        })));
    } catch (e) {
      toast.error(e);
    }
  };

  return (
    <>
      <div className="toolbar">
        <select className="input" style={{ width: 'auto', height: 32 }} value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
          {STATUS.map(([v, l]) => <option key={v} value={v}>Status: {l}</option>)}
        </select>
        {cfg.listFilters?.map((f) => (
          <select key={f.key} className="input" style={{ width: 'auto', height: 32 }} aria-label={f.label} value={filters[f.key] ?? ''}
            onChange={(e) => setFilters((s) => ({ ...s, [f.key]: e.target.value }))}>
            <option value="">{f.label}: Semua</option>
            {f.options?.map((o) => <option key={o.value} value={o.value}>{f.label}: {o.label}</option>)}
          </select>
        ))}
        <label className="row small muted">Periode <input className="input" type="month" style={{ width: 'auto', height: 32 }} value={month} onChange={(e) => setMonth(e.target.value)} /></label>
        <div style={{ flex: '1 1 200px', maxWidth: 320, marginLeft: 'auto' }}>
          <input className="input" style={{ height: 32 }} type="search" placeholder="Cari nomor atau uraian" aria-label="Cari" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <button className="btn" type="button" onClick={doExport}><Icon name="download" size={14} />Ekspor</button>
        <button className="btn btn-dark" type="button" title="Alt+N" onClick={onNew}><Icon name="plus" size={14} />Baru</button>
      </div>
      {query.error ? <div className="alert alert-err" style={{ margin: 12 }}>{errorText(query.error)}</div> : (
        <DataTable columns={columns} rows={rows} rowKey={(r) => r.id as number} onRowClick={(r) => onOpen(r.id as number)}
          sort={sort} onSort={setSort} empty={query.isLoading ? 'Memuat…' : 'Belum ada dokumen'} />
      )}
      {query.data && <Pager page={page} totalPages={query.data.totalPages} total={query.data.totalElements} size={50} onPage={setPage} />}
    </>
  );
}

