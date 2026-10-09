import { useEffect, useState } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { Page } from '../../api/types';
import { DataTable, exportCsv, Pager } from '../../components/DataTable';
import { Icon } from '../../components/icons';
import { StatusChip } from '../../components/StatusChip';
import { errorText, useDebounced, useToast } from '../../components/ui';
import { fmtDateTime } from '../../lib/format';

interface AuditRow { id: number; tableName: string; recordId: string; action: string; field: string | null; oldValue: string | null; newValue: string | null; reason: string | null; username: string; ts: string }

/** SYS-14 Audit trail global — baca saja, tidak ada yang bisa mengubah (ALCOA+). */
export function AuditTrailPage() {
  const [table, setTable] = useState('');
  const [recordId, setRecordId] = useState('');
  const [user, setUser] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const u = useDebounced(user, 300);
  const r = useDebounced(recordId, 300);
  useEffect(() => setPage(0), [table, r, u, from, to]);
  const params = { table, recordId: r, username: u, from, to, page, size: 100 };
  const tables = useQuery({ queryKey: ['audit-tables'], queryFn: () => api.get<string[]>('/audit/tables') });
  const q = useQuery({ queryKey: ['audit', params], queryFn: () => api.get<Page<AuditRow>>('/audit', params), placeholderData: keepPreviousData });
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        <select className="input" style={{ width: 'auto', height: 32 }} value={table} onChange={(e) => setTable(e.target.value)} aria-label="Tabel">
          <option value="">Tabel: Semua</option>
          {tables.data?.map((t) => <option key={t} value={t}>{t}</option>)}
        </select>
        <input className="input" style={{ width: 120, height: 32 }} placeholder="ID record" value={recordId} onChange={(e) => setRecordId(e.target.value)} aria-label="ID record" />
        <input className="input" style={{ width: 160, height: 32 }} placeholder="Pengguna" value={user} onChange={(e) => setUser(e.target.value)} aria-label="Pengguna" />
        <label className="row small muted">Dari <input className="input" type="date" style={{ width: 'auto', height: 32 }} value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label className="row small muted">s.d. <input className="input" type="date" style={{ width: 'auto', height: 32 }} value={to} onChange={(e) => setTo(e.target.value)} /></label>
        <span className="spacer" />
        <button className="btn" type="button" onClick={() => exportCsv('audit-trail.csv', ['Waktu', 'Tabel', 'Record', 'Aksi', 'Field', 'Nilai lama', 'Nilai baru', 'Alasan', 'Pengguna'],
          (q.data?.content ?? []).map((a) => [fmtDateTime(a.ts), a.tableName, a.recordId, a.action, a.field, a.oldValue, a.newValue, a.reason, a.username]))}>
          <Icon name="download" size={14} />Ekspor
        </button>
      </div>
      {q.error ? <div className="alert alert-err" style={{ margin: 12 }}>{errorText(q.error)}</div> : (
        <DataTable rows={q.data?.content ?? []} rowKey={(a) => a.id} empty={q.isLoading ? 'Memuat…' : 'Tidak ada catatan'}
          columns={[
            { key: 'ts', label: 'Waktu (WIB)', mono: true, render: (a) => fmtDateTime(a.ts) },
            { key: 'username', label: 'Pengguna' },
            { key: 'tableName', label: 'Tabel', mono: true },
            { key: 'recordId', label: 'ID', mono: true },
            { key: 'action', label: 'Aksi' },
            { key: 'field', label: 'Field', mono: true },
            { key: 'oldValue', label: 'Nilai lama', render: (a) => <span className="muted" style={{ wordBreak: 'break-word' }}>{a.oldValue}</span> },
            { key: 'newValue', label: 'Nilai baru', render: (a) => <span style={{ wordBreak: 'break-word' }}>{a.newValue}</span> },
            { key: 'reason', label: 'Alasan' },
          ]} />
      )}
      {q.data && <Pager page={page} totalPages={q.data.totalPages} total={q.data.totalElements} size={100} onPage={setPage} />}
    </div>
  );
}

interface IntegrationRow { id: number; system: string; direction: string; endpoint: string; refDoc: string | null; status: string; httpStatus: number | null; error: string | null; attempts: number; createdAt: string; lastAttemptAt: string | null }

/** SYS-13 Integrasi & log API: HRIS, BSC, Odoo, mesin absensi, timbangan. Log gagal bisa dicoba ulang. */
export function IntegrationLogPage() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const qc = useQueryClient();
  const toast = useToast();
  const q = useQuery({ queryKey: ['integration', status, page], queryFn: () => api.get<Page<IntegrationRow>>('/sys/integration-logs', { status, page, size: 50 }) });
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        <select className="input" style={{ width: 'auto', height: 32 }} value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
          <option value="">Status: Semua</option><option value="PENDING">Menunggu</option><option value="SUCCESS">Berhasil</option><option value="FAILED">Gagal</option>
        </select>
      </div>
      <DataTable rows={q.data?.content ?? []} rowKey={(r) => r.id}
        empty={q.isLoading ? 'Memuat…' : 'Belum ada lalu lintas integrasi. Konektor BSC, HRIS, Odoo, dan perangkat lantai produksi dipasang di fase berikutnya.'}
        columns={[
          { key: 'createdAt', label: 'Waktu', mono: true, render: (r) => fmtDateTime(r.createdAt) },
          { key: 'system', label: 'Sistem' },
          { key: 'direction', label: 'Arah' },
          { key: 'endpoint', label: 'Endpoint', mono: true },
          { key: 'refDoc', label: 'Dokumen', mono: true },
          { key: 'attempts', label: 'Percobaan', align: 'right' },
          { key: 'status', label: 'Status', render: (r) => <StatusChip status={r.status === 'PENDING' ? 'SUBMITTED' : r.status} label={r.status === 'PENDING' ? 'Menunggu' : undefined} /> },
          { key: 'act', label: '', render: (r) => r.status === 'FAILED' ? <button className="btn btn-sm" type="button" onClick={async () => {
            try { await api.post(`/sys/integration-logs/${r.id}/retry`); toast.ok('Dijadwalkan ulang'); qc.invalidateQueries({ queryKey: ['integration'] }); } catch (e) { toast.error(e); }
          }}>Coba ulang</button> : null },
        ]} />
      {q.data && <Pager page={page} totalPages={q.data.totalPages} total={q.data.totalElements} size={50} onPage={setPage} />}
    </div>
  );
}
