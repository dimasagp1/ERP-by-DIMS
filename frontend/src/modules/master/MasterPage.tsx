import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../api/client';
import type { Page } from '../../api/types';
import { DataTable, exportCsv, Pager, type Column } from '../../components/DataTable';
import { Icon } from '../../components/icons';
import { useLookupLabels } from '../../components/Lookup';
import { StatusChip } from '../../components/StatusChip';
import { errorText, ReasonDialog, useDebounced, useToast } from '../../components/ui';
import { fmtDate, fmtDateTime, fmtNumber, fmtRp } from '../../lib/format';
import { FieldInput } from './FieldInput';
import type { ColumnDef, ResourceDef, Row } from './types';

/** Satu menu master: satu atau beberapa sumber data dalam tab. */
export function MasterPage({ resources }: { resources: ResourceDef[] }) {
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') ?? resources[0].key;
  const res = resources.find((r) => r.key === tab) ?? resources[0];
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      {resources.length > 1 && (
        <div className="tabs" role="tablist">
          {resources.map((r) => (
            <button key={r.key} type="button" role="tab" aria-selected={r.key === res.key} className={r.key === res.key ? 'on' : ''}
              onClick={() => setParams({ tab: r.key })}>{r.title}</button>
          ))}
        </div>
      )}
      <ResourceList key={res.key} res={res} initialQ={params.get('q') ?? ''} />
    </div>
  );
}

function ResourceList({ res, initialQ }: { res: ResourceDef; initialQ: string }) {
  const [q, setQ] = useState(initialQ);
  const term = useDebounced(q, 300);
  const [filters, setFilters] = useState<Record<string, string>>({ active: 'true' });
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState(res.defaultSort ?? '');
  const [editing, setEditing] = useState<Row | 'new' | null>(null);
  const toast = useToast();
  const size = 50;
  useEffect(() => setPage(0), [term, filters, sort]);

  const query = useQuery({
    queryKey: ['master', res.endpoint, term, filters, page, sort],
    queryFn: () => api.get<Page<Row>>(res.endpoint, { q: term, page, size, sort, ...filters }),
    placeholderData: keepPreviousData,
  });
  const rows = query.data?.content ?? [];

  const columns = useMasterColumns(res.columns, rows);

  // Alt+N = dokumen/data baru (PRD §15.6)
  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      if (e.altKey && e.key.toLowerCase() === 'n') {
        e.preventDefault();
        setEditing('new');
      }
    };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, []);

  const doExport = async () => {
    try {
      const all = await api.get<Page<Row>>(res.endpoint, { q: term, page: 0, size: 10000, sort, ...filters });
      exportCsv(`${res.key}.csv`, res.columns.map((c) => c.label),
        all.content.map((r) => res.columns.map((c) => plain(r[c.key]))));
    } catch (e) {
      toast.error(e);
    }
  };

  return (
    <>
      <div className="toolbar">
        {res.filters?.map((f) => (
          <select key={f.key} className="input" style={{ width: 'auto', height: 32 }} aria-label={f.label}
            value={filters[f.key] ?? ''} onChange={(e) => setFilters((s) => ({ ...s, [f.key]: e.target.value }))}>
            <option value="">{f.label}: Semua</option>
            {f.options?.map((o) => <option key={o.value} value={o.value}>{f.label}: {o.label}</option>)}
          </select>
        ))}
        <select className="input" style={{ width: 'auto', height: 32 }} aria-label="Status aktif"
          value={filters.active ?? ''} onChange={(e) => setFilters((s) => ({ ...s, active: e.target.value }))}>
          <option value="true">Aktif</option>
          <option value="false">Nonaktif</option>
          <option value="">Aktif & nonaktif</option>
        </select>
        <div style={{ flex: '1 1 200px', maxWidth: 320, marginLeft: 'auto' }}>
          <label className="sr-only" htmlFor={`q-${res.key}`}>Cari</label>
          <input id={`q-${res.key}`} className="input" style={{ height: 32 }} type="search" placeholder="Cari kode atau nama" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <button className="btn" type="button" onClick={doExport}><Icon name="download" size={14} />Ekspor</button>
        <button className="btn btn-dark" type="button" onClick={() => setEditing('new')} title="Alt+N"><Icon name="plus" size={14} />Baru</button>
      </div>
      {query.error ? <div className="alert alert-err" style={{ margin: 12 }}>{errorText(query.error)}</div> : (
        <DataTable columns={columns} rows={rows} rowKey={(r) => r.id} onRowClick={(r) => setEditing(r)}
          sort={sort} onSort={setSort} empty={query.isLoading ? 'Memuat…' : 'Belum ada data'} />
      )}
      {query.data && <Pager page={page} totalPages={query.data.totalPages} total={query.data.totalElements} size={size} onPage={setPage} />}
      {editing && <MasterDrawer res={res} row={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function plain(v: unknown): string | number | null {
  if (v == null) return null;
  if (typeof v === 'boolean') return v ? 'Ya' : 'Tidak';
  return typeof v === 'number' ? v : String(v);
}

function useMasterColumns(defs: ColumnDef[], rows: Row[]): Column<Row>[] {
  // Satu lookup label per kolom FK (maks. beberapa kolom per tabel).
  const l0 = defs.filter((c) => c.lookup)[0];
  const l1 = defs.filter((c) => c.lookup)[1];
  const l2 = defs.filter((c) => c.lookup)[2];
  const m0 = useLookupLabels(l0?.lookup, rows.map((r) => (l0 ? (r[l0.key] as number) : null)));
  const m1 = useLookupLabels(l1?.lookup, rows.map((r) => (l1 ? (r[l1.key] as number) : null)));
  const m2 = useLookupLabels(l2?.lookup, rows.map((r) => (l2 ? (r[l2.key] as number) : null)));
  const maps = useMemo(() => new Map([[l0?.key, m0], [l1?.key, m1], [l2?.key, m2]]), [l0, l1, l2, m0, m1, m2]);

  return defs.map((c) => ({
    key: c.key,
    label: c.label,
    mono: c.mono,
    align: c.type === 'number' || c.type === 'money' ? 'right' : undefined,
    sortKey: c.sortable === false || c.lookup ? undefined : c.key,
    render: (r: Row) => {
      const v = r[c.key];
      if (c.lookup) {
        const o = maps.get(c.key)?.get(v as number);
        return o ? <span><span className="mono small">{o.code}</span> <span className="muted">{o.name}</span></span> : v == null ? '' : `#${v}`;
      }
      if (c.type === 'active') return <StatusChip status={v ? 'ACTIVE' : 'INACTIVE'} />;
      if (c.type === 'status') return v ? <StatusChip status={String(v)} /> : '';
      if (c.type === 'bool') return v ? 'Ya' : '–';
      if (c.type === 'money') return fmtRp(v as number);
      if (c.type === 'number') return fmtNumber(v as number);
      if (c.type === 'date') return fmtDate(v as string);
      if (c.type === 'select') return c.options?.find((o) => o.value === v)?.label ?? String(v ?? '');
      return v == null ? '' : String(v);
    },
  }));
}

interface AuditRow { id: number; action: string; field: string | null; oldValue: string | null; newValue: string | null; reason: string | null; username: string; ts: string }

/** Drawer form ringkas + riwayat perubahan. */
function MasterDrawer({ res, row, onClose }: { res: ResourceDef; row: Row | null; onClose: () => void }) {
  const [data, setData] = useState<Record<string, unknown>>(() => row ? { ...row } : { active: true, ...(res.defaults ?? {}) });
  const [tab, setTab] = useState('form');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<ApiError | null>(null);
  const [deactivate, setDeactivate] = useState(false);
  const qc = useQueryClient();
  const toast = useToast();
  const isNew = row == null;

  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
      if (e.altKey && e.key.toLowerCase() === 's') {
        e.preventDefault();
        void save();
      }
    };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  });

  const save = async () => {
    setBusy(true);
    setErr(null);
    try {
      if (isNew) await api.post(res.endpoint, data);
      else await api.put(`${res.endpoint}/${row!.id}`, data);
      await qc.invalidateQueries({ queryKey: ['master', res.endpoint] });
      qc.invalidateQueries({ queryKey: ['lookup'] });
      qc.invalidateQueries({ queryKey: ['lookup-ids'] });
      toast.ok(isNew ? 'Data dibuat' : 'Perubahan disimpan');
      onClose();
    } catch (e) {
      setErr(e instanceof ApiError ? e : new ApiError(0, 'ERR', errorText(e)));
    } finally {
      setBusy(false);
    }
  };

  const audit = useQuery({
    queryKey: ['audit-record', res.table, row?.id],
    queryFn: () => api.get<Page<AuditRow>>('/audit', { table: res.table, recordId: row!.id, size: 200 }),
    enabled: tab === 'history' && !isNew,
  });

  const title = isNew ? `Baru · ${res.title}` : res.titleOf?.(row!) ?? String(row!.code ?? row!.name ?? `#${row!.id}`);
  const extra = !isNew ? res.extraTabs ?? [] : [];
  const reload = () => qc.invalidateQueries({ queryKey: ['master', res.endpoint] });

  return (
    <>
      <div className="drawer-back" onClick={onClose} />
      <aside className="drawer" role="dialog" aria-label={title}>
        <div className="modal-h">
          <span><span className="muted small" style={{ display: 'block', fontWeight: 400 }}>{res.title}</span>{title}</span>
          <button className="iconbtn" type="button" aria-label="Tutup" onClick={onClose}><Icon name="x" /></button>
        </div>
        {!isNew && (
          <div className="tabs">
            <button type="button" className={tab === 'form' ? 'on' : ''} onClick={() => setTab('form')}>Data</button>
            {extra.map((t) => <button key={t.key} type="button" className={tab === t.key ? 'on' : ''} onClick={() => setTab(t.key)}>{t.label}</button>)}
            <button type="button" className={tab === 'history' ? 'on' : ''} onClick={() => setTab('history')}>Riwayat perubahan</button>
          </div>
        )}
        <div style={{ flex: 1, overflow: 'auto', padding: 18 }}>
          {tab === 'form' && (
            <div className="grid-form">
              {res.fields.map((f) => {
                const id = `f-${res.key}-${f.key}`;
                const fieldErr = err?.fields?.[f.key];
                const disabled = !isNew && f.immutable;
                if (f.type === 'bool') {
                  return <div key={f.key} style={{ gridColumn: '1 / -1' }}><FieldInput def={f} id={id} value={data[f.key]} disabled={disabled} onChange={(v) => setData((s) => ({ ...s, [f.key]: v }))} /></div>;
                }
                return (
                  <div key={f.key} className="field" style={f.full || f.type === 'textarea' ? { gridColumn: '1 / -1' } : undefined}>
                    <label htmlFor={id} className={f.required ? 'req' : undefined}>{f.label}</label>
                    <FieldInput def={f} id={id} value={data[f.key]} disabled={disabled} onChange={(v) => setData((s) => ({ ...s, [f.key]: v }))} />
                    {f.help && <span className="small muted">{f.help}</span>}
                    {fieldErr && <span className="err">{fieldErr}</span>}
                  </div>
                );
              })}
            </div>
          )}
          {extra.map((t) => tab === t.key && <div key={t.key}>{t.render(row!, reload)}</div>)}
          {tab === 'history' && (
            audit.error ? <div className="alert alert-warn">{errorText(audit.error)}</div> :
            <div className="timeline" style={{ margin: -18 }}>
              {(audit.data?.content ?? []).length === 0 && <div className="empty">{audit.isLoading ? 'Memuat…' : 'Belum ada riwayat'}</div>}
              {audit.data?.content.map((a) => (
                <div key={a.id} className="ev">
                  <span className="mono muted" style={{ width: 118, flex: 'none' }}>{fmtDateTime(a.ts)}</span>
                  <span className="m">
                    <b>{a.username}</b> {a.action === 'INSERT' ? 'membuat data' : a.action === 'DELETE' ? 'menghapus' : <>mengubah <span className="mono">{a.field}</span>: <span className="muted">{a.oldValue ?? '∅'}</span> → {a.newValue ?? '∅'}</>}
                    {a.reason && <span className="muted" style={{ display: 'block' }}>Alasan: {a.reason}</span>}
                  </span>
                </div>
              ))}
            </div>
          )}
          {err && <div className="alert alert-err" style={{ marginTop: 16 }}>{err.message}</div>}
        </div>
        {tab === 'form' && (
          <div className="modal-f">
            {!isNew && row!.active !== false && 'active' in row! && (
              <button className="btn btn-danger" type="button" style={{ marginRight: 'auto' }} onClick={() => setDeactivate(true)}>Nonaktifkan</button>
            )}
            <button className="btn" type="button" onClick={onClose}>Batal</button>
            <button className="btn btn-dark" type="button" disabled={busy} onClick={save} title="Alt+S">Simpan</button>
          </div>
        )}
      </aside>
      {deactivate && (
        <ReasonDialog title={`Nonaktifkan ${title}?`} confirmLabel="Nonaktifkan" danger needReason
          description="Data tidak dihapus agar referensi historis tetap utuh; data nonaktif tidak bisa dipilih di transaksi baru."
          onClose={() => setDeactivate(false)}
          onConfirm={async ({ reason }) => {
            await api.del(`${res.endpoint}/${row!.id}`, { reason });
            await qc.invalidateQueries({ queryKey: ['master', res.endpoint] });
            toast.ok('Data dinonaktifkan');
            onClose();
          }} />
      )}
    </>
  );
}
