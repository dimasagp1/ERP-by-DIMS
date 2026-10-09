import { Fragment, useEffect, useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable, exportCsv } from '../../components/DataTable';
import { Icon } from '../../components/icons';
import { LookupSelect } from '../../components/Lookup';
import { StatusChip } from '../../components/StatusChip';
import { Modal, ReasonDialog, errorText, useToast } from '../../components/ui';
import { fmtDate, fmtNumber, fmtRp, todayIso } from '../../lib/format';

type Row = Record<string, unknown>;
const n = (v: unknown) => Number(v ?? 0);
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];
const numInput = { width: 84, height: 30, textAlign: 'right' as const };

function Err({ e }: { e: unknown }) {
  return e ? <div className="alert alert-err" style={{ margin: 12 }}>{errorText(e)}</div> : null;
}

// ---------------------------------------------------------------- SCM-03 Forecast & S&OP

export function ForecastPage() {
  const [year, setYear] = useState(new Date().getFullYear());
  const [cells, setCells] = useState<Record<string, number>>({});
  const [note, setNote] = useState<{ period: string; meetingOn: string; decisions: string }>({ period: '', meetingOn: '', decisions: '' });
  const toast = useToast();
  const q = useQuery({ queryKey: ['forecast', year], queryFn: () => api.get<{ items: Row[]; forecast: Row[]; actual: Row[]; notes: Row[] }>('/scm/forecast', { year }) });
  useEffect(() => {
    const m: Record<string, number> = {};
    q.data?.forecast.forEach((f) => { m[`${f.item_id}:${f.period}`] = n(f.qty); });
    setCells(m);
  }, [q.data]);
  const actual = useMemo(() => {
    const m: Record<string, number> = {};
    q.data?.actual.forEach((a) => { m[`${a.item_id}:${a.period}`] = n(a.qty); });
    return m;
  }, [q.data]);
  const periods = MONTHS.map((_, i) => `${year}${String(i + 1).padStart(2, '0')}`);
  const save = async () => {
    try {
      await api.put('/scm/forecast', Object.entries(cells).map(([k, qty]) => ({ itemId: Number(k.split(':')[0]), period: k.split(':')[1], qty })));
      toast.ok('Forecast disimpan');
      q.refetch();
    } catch (e) {
      toast.error(e);
    }
  };
  const pickNote = (period: string) => {
    const x = q.data?.notes.find((r) => r.period === period);
    setNote({ period, meetingOn: (x?.meeting_on as string) ?? '', decisions: (x?.decisions as string) ?? '' });
  };
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="toolbar">
          <label className="row small muted">Tahun <input className="input" type="number" style={{ width: 90, height: 32 }} value={year} onChange={(e) => setYear(Number(e.target.value))} /></label>
          <span className="small muted">Baris atas = forecast (bisa diubah), baris bawah = realisasi kirim. Dasar MPS (SCM-04).</span>
          <span className="spacer" />
          <button className="btn btn-dark" type="button" onClick={save}>Simpan forecast</button>
        </div>
        <Err e={q.error} />
        <div className="table-wrap">
          <table className="t">
            <thead><tr><th>Produk</th>{MONTHS.map((m, i) => <th key={m} className="num"><button className="link" type="button" onClick={() => pickNote(periods[i])}>{m}</button></th>)}<th className="num">Total</th></tr></thead>
            <tbody>
              {q.data?.items.map((it) => {
                const id = it.id as number;
                const total = periods.reduce((s, p) => s + (cells[`${id}:${p}`] ?? 0), 0);
                return (
                  <Fragment key={id}>
                    <tr>
                      <td><span className="mono small">{String(it.code)}</span> {String(it.name)}</td>
                      {periods.map((p) => (
                        <td key={p} className="num">
                          <input className="input mono" style={numInput} value={cells[`${id}:${p}`] ?? ''} aria-label={`Forecast ${it.code} ${p}`}
                            onChange={(e) => setCells((c) => ({ ...c, [`${id}:${p}`]: Number(e.target.value || 0) }))} />
                        </td>
                      ))}
                      <td className="num mono"><b>{fmtNumber(total)}</b></td>
                    </tr>
                    <tr>
                      <td className="small muted" style={{ paddingLeft: 24 }}>Realisasi kirim</td>
                      {periods.map((p) => <td key={p} className="num mono small muted">{actual[`${id}:${p}`] ? fmtNumber(actual[`${id}:${p}`]) : ''}</td>)}
                      <td className="num mono small muted">{fmtNumber(periods.reduce((s, p) => s + (actual[`${id}:${p}`] ?? 0), 0))}</td>
                    </tr>
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
      {note.period && (
        <div className="card" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div className="row"><b>Catatan rapat S&OP · {MONTHS[Number(note.period.slice(4)) - 1]} {note.period.slice(0, 4)}</b><span className="spacer" />
            <label className="row small muted">Tanggal rapat <input className="input" type="date" style={{ width: 'auto', height: 32 }} value={note.meetingOn}
              onChange={(e) => setNote({ ...note, meetingOn: e.target.value })} /></label></div>
          <textarea className="input" rows={4} value={note.decisions} placeholder="Keputusan: kapasitas, prioritas produk, stok pengaman…"
            onChange={(e) => setNote({ ...note, decisions: e.target.value })} />
          <div><button className="btn btn-dark" type="button" onClick={async () => {
            try { await api.put('/scm/sop-notes', { ...note, meetingOn: note.meetingOn || null }); toast.ok('Catatan S&OP disimpan'); q.refetch(); } catch (e) { toast.error(e); }
          }}>Simpan catatan</button></div>
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- SCM-04 MPS

interface MpsCell { weekStart: string; forecast: number; orders: number; qty: number; firm: boolean; suggest: number; projected: number }

export function MpsPage() {
  const [weeks, setWeeks] = useState(12);
  const [edit, setEdit] = useState<Record<string, { qty: number; firm: boolean }>>({});
  const toast = useToast();
  const q = useQuery({ queryKey: ['mps', weeks], queryFn: () => api.get<{ weeks: string[]; rows: (Row & { cells: MpsCell[] })[] }>('/scm/mps', { weeks }) });
  useEffect(() => {
    const m: Record<string, { qty: number; firm: boolean }> = {};
    q.data?.rows.forEach((r) => r.cells.forEach((c) => { m[`${r.id}:${c.weekStart}`] = { qty: n(c.qty), firm: c.firm }; }));
    setEdit(m);
  }, [q.data]);
  const useSuggest = () => setEdit((e) => {
    const out = { ...e };
    q.data?.rows.forEach((r) => r.cells.forEach((c) => {
      const k = `${r.id}:${c.weekStart}`;
      if (!out[k]?.firm && n(c.suggest) > 0) out[k] = { qty: n(out[k]?.qty) + n(c.suggest), firm: false };
    }));
    return out;
  });
  const save = async () => {
    try {
      await api.put('/scm/mps', Object.entries(edit).map(([k, v]) => ({ itemId: Number(k.split(':')[0]), weekStart: k.slice(k.indexOf(':') + 1), ...v })));
      toast.ok('MPS disimpan');
      q.refetch();
    } catch (e) {
      toast.error(e);
    }
  };
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        <label className="row small muted">Horizon <select className="input" style={{ width: 'auto', height: 32 }} value={weeks} onChange={(e) => setWeeks(Number(e.target.value))}>
          {[8, 12, 16, 26].map((w) => <option key={w} value={w}>{w} minggu</option>)}</select></label>
        <span className="small muted">Kebutuhan = maks(forecast mingguan, pesanan). Usulan menjaga proyeksi stok ≥ stok pengaman, dibulatkan per batch BOM.</span>
        <span className="spacer" />
        <button className="btn" type="button" onClick={useSuggest}>Pakai usulan</button>
        <button className="btn btn-dark" type="button" onClick={save}>Simpan MPS</button>
      </div>
      <Err e={q.error} />
      <div className="table-wrap">
        <table className="t">
          <thead><tr><th>Produk</th>{q.data?.weeks.map((w) => <th key={w} className="num">{fmtDate(w).slice(0, 5)}</th>)}</tr></thead>
          <tbody>
            {q.data?.rows.map((r) => (
              <tr key={String(r.id)}>
                <td style={{ minWidth: 200 }}><span className="mono small">{String(r.code)}</span> {String(r.name)}
                  <div className="small muted">Stok pengaman {fmtNumber(n(r.ss))} · batch {fmtNumber(n(r.batch))} {String(r.uom)}</div></td>
                {r.cells.map((c) => {
                  const k = `${r.id}:${c.weekStart}`;
                  const v = edit[k] ?? { qty: 0, firm: false };
                  return (
                    <td key={c.weekStart} className="num" style={{ verticalAlign: 'top' }} title={`Forecast ${fmtNumber(c.forecast)} · pesanan ${fmtNumber(c.orders)} · proyeksi ${fmtNumber(c.projected)}`}>
                      <input className="input mono" style={{ ...numInput, fontWeight: v.firm ? 600 : 400 }} value={v.qty || ''} aria-label={`MPS ${r.code} ${c.weekStart}`}
                        onChange={(e) => setEdit((x) => ({ ...x, [k]: { ...v, qty: Number(e.target.value || 0) } }))} />
                      <div className="small muted mono">{n(c.suggest) > 0 ? `+${fmtNumber(c.suggest)}` : ''}</div>
                      <label className="small muted"><input type="checkbox" checked={v.firm} onChange={(e) => setEdit((x) => ({ ...x, [k]: { ...v, firm: e.target.checked } }))} /> tetap</label>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- SCM-05 MRP

export function MrpPage() {
  const [sel, setSel] = useState<number[]>([]);
  const [busy, setBusy] = useState(false);
  const toast = useToast();
  const q = useQuery({ queryKey: ['mrp'], queryFn: () => api.get<{ run?: Row; lines?: Row[] }>('/scm/mrp/latest') });
  const run = async () => {
    setBusy(true);
    try {
      await api.post('/scm/mrp/run');
      toast.ok('MRP selesai dijalankan');
      setSel([]);
      q.refetch();
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };
  const makePr = async () => {
    try {
      const r = await api.post<{ meta: { docNo: string } }>('/prc/requisitions/from-mrp', { lineIds: sel });
      toast.ok(`PR ${r.meta.docNo} dibuat dari ${sel.length} usulan beli`);
      setSel([]);
      q.refetch();
    } catch (e) {
      toast.error(e);
    }
  };
  const lines = q.data?.lines ?? [];
  const r = q.data?.run;
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        {r ? <span className="small">Terakhir: <b>{fmtDate(String(r.run_at))}</b> · {String(r.trigger) === 'NIGHTLY' ? 'otomatis malam' : `manual oleh ${r.triggered_by_name ?? '-'}`}
          · {fmtNumber(n(r.lines))} baris · {fmtNumber(n(r.duration_ms))} ms · horizon {String(r.horizon_weeks)} minggu</span>
          : <span className="small muted">Belum pernah dijalankan. Isi MPS (SCM-04) lalu jalankan MRP.</span>}
        <span className="spacer" />
        <button className="btn" type="button" disabled={sel.length === 0} onClick={makePr}>Buat PR dari {sel.length || ''} usulan</button>
        <button className="btn btn-dark" type="button" disabled={busy} onClick={run}>{busy ? 'Menjalankan…' : 'Jalankan MRP'}</button>
      </div>
      <Err e={q.error} />
      <DataTable rows={lines} rowKey={(x) => String(x.id)} empty={q.isLoading ? 'Memuat…' : 'Tidak ada kebutuhan'}
        columns={[
          { key: 'sel', label: '', width: 32, render: (x) => x.action === 'BUY' && !x.pr_line_id
            ? <input type="checkbox" aria-label="Pilih" checked={sel.includes(x.id as number)} onChange={(e) => setSel((s) => e.target.checked ? [...s, x.id as number] : s.filter((y) => y !== x.id))} />
            : null },
          { key: 'action', label: 'Aksi', render: (x) => <StatusChip status={x.action === 'MAKE' ? 'SUBMITTED' : 'DRAFT'} label={x.action === 'MAKE' ? 'Buat' : 'Beli'} /> },
          { key: 'item', label: 'Item', render: (x) => <span><span className="mono small">{String(x.item_code)}</span> {String(x.item_name)}</span> },
          { key: 'gross_req', label: 'Kebutuhan kotor', align: 'right', render: (x) => fmtNumber(n(x.gross_req)) },
          { key: 'on_hand', label: 'Stok siap', align: 'right', render: (x) => fmtNumber(n(x.on_hand)) },
          { key: 'quarantine', label: 'Karantina', align: 'right', render: (x) => fmtNumber(n(x.quarantine)) },
          { key: 'on_order', label: 'Dipesan/WO', align: 'right', render: (x) => fmtNumber(n(x.on_order)) },
          { key: 'planned_qty', label: 'Usulan', align: 'right', render: (x) => <b>{fmtNumber(n(x.planned_qty))} {String(x.uom)}</b> },
          { key: 'need_date', label: 'Dibutuhkan', render: (x) => fmtDate(String(x.need_date)) },
          { key: 'order_date', label: 'Pesan paling lambat', render: (x) => <span style={{ color: String(x.order_date) < todayIso() ? 'var(--danger)' : undefined }}>{fmtDate(String(x.order_date))}</span> },
          { key: 'partner_name', label: 'Supplier saran', render: (x) => String(x.partner_name ?? '') },
          { key: 'pr_no', label: 'PR', render: (x) => <span className="mono small">{String(x.pr_no ?? '')}</span> },
          { key: 'pegging', label: 'Untuk', render: (x) => <span className="small muted">{String(x.pegging ?? '')}</span> },
        ]} />
    </div>
  );
}

// ---------------------------------------------------------------- SCM-06 Kapasitas

export function CapacityPage() {
  const q = useQuery({ queryKey: ['capacity'], queryFn: () => api.get<{ rows: (Row & { cells: Row[] })[]; note: string }>('/scm/capacity', { weeks: 8 }) });
  const weeks = q.data?.rows[0]?.cells.map((c) => String(c.weekStart)) ?? [];
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar"><span className="small muted">{q.data?.note}</span></div>
      <Err e={q.error} />
      <div className="table-wrap">
        <table className="t">
          <thead><tr><th>Lini</th><th className="num">Kapasitas/shift</th>{weeks.map((w) => <th key={w} className="num">{fmtDate(w).slice(0, 5)}</th>)}</tr></thead>
          <tbody>
            {q.data?.rows.map((r) => (
              <tr key={String(r.id)}>
                <td><span className="mono small">{String(r.code)}</span> {String(r.name)}</td>
                <td className="num mono">{fmtNumber(n(r.capacity_per_shift))} {String(r.capacity_uom ?? '')}</td>
                {r.cells.map((c) => {
                  const pct = n(c.pct);
                  return (
                    <td key={String(c.weekStart)} className="num" title={`MPS ${fmtNumber(n(c.mps))} · WO ${fmtNumber(n(c.wo))} · ${fmtNumber(n(c.shifts))} shift`}>
                      <div className="mono" style={{ color: pct > 100 ? 'var(--danger)' : undefined }}>{pct}%</div>
                      <div style={{ height: 4, background: 'var(--line)', borderRadius: 2 }}>
                        <div style={{ width: `${Math.min(pct, 100)}%`, height: 4, borderRadius: 2, background: pct > 100 ? 'var(--danger)' : 'var(--accent)' }} />
                      </div>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- SCM-07 Rilis WO

export function ReleasePage() {
  const toast = useToast();
  const q = useQuery({ queryKey: ['plan-board'], queryFn: () => api.get<{ mps: Row[]; workOrders: Row[] }>('/pre/work-orders/plan-board') });
  const release = async (r: Row) => {
    try {
      const res = await api.post<{ meta: { docNo: string } }>('/pre/work-orders/from-plan', {
        itemId: r.item_id, qty: n(r.qty) - n(r.released), plannedStart: r.week_start,
      });
      toast.ok(`WO ${res.meta.docNo} dibuat; Produksi menetapkan operator lalu merilis (PRE-02)`);
      q.refetch();
    } catch (e) {
      toast.error(e);
    }
  };
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">MPS yang belum menjadi WO</div>
        <Err e={q.error} />
        <DataTable rows={(q.data?.mps ?? []).filter((r) => n(r.qty) > n(r.released))} rowKey={(r) => `${r.item_id}-${r.week_start}`} empty="Semua rencana sudah dirilis"
          columns={[
            { key: 'week_start', label: 'Minggu', render: (r) => fmtDate(String(r.week_start)) },
            { key: 'item', label: 'Produk', render: (r) => <span><span className="mono small">{String(r.code)}</span> {String(r.name)}</span> },
            { key: 'qty', label: 'MPS', align: 'right', render: (r) => fmtNumber(n(r.qty)) },
            { key: 'released', label: 'Sudah WO', align: 'right', render: (r) => fmtNumber(n(r.released)) },
            { key: 'firm', label: 'Tetap', render: (r) => (r.firm ? 'Ya' : '–') },
            { key: 'act', label: '', render: (r) => <button className="btn btn-sm btn-dark" type="button" onClick={() => release(r)}>Buat WO {fmtNumber(n(r.qty) - n(r.released))}</button> },
          ]} />
      </div>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">Work order 30 hari terakhir & mendatang</div>
        <DataTable rows={q.data?.workOrders ?? []} rowKey={(r) => String(r.id)} empty="Belum ada WO"
          columns={[
            { key: 'doc_no', label: 'WO', mono: true },
            { key: 'planned_start', label: 'Mulai', render: (r) => fmtDate(String(r.planned_start)) },
            { key: 'item', label: 'Produk', render: (r) => `${r.item_code} · ${r.item_name}` },
            { key: 'line_code', label: 'Lini', mono: true },
            { key: 'qty_plan', label: 'Qty', align: 'right', render: (r) => fmtNumber(n(r.qty_plan)) },
            { key: 'batch_no', label: 'Batch', mono: true },
            { key: 'exp_date', label: 'ED', render: (r) => fmtDate(r.exp_date as string) },
            { key: 'source', label: 'Sumber' },
            { key: 'status', label: 'Status', render: (r) => <StatusChip status={String(r.status)} /> },
          ]} />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- SCM-08 Rencana vs aktual

export function PlanActualPage() {
  const [from, setFrom] = useState(todayIso().slice(0, 8) + '01');
  const [to, setTo] = useState(todayIso());
  const q = useQuery({ queryKey: ['plan-actual', from, to], queryFn: () => api.get<{ workOrders: Row[]; orders: Row[]; otifPct: number | null; lateWo: number }>('/scm/plan-actual', { from, to }) });
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card toolbar">
        <label className="row small muted">Dari <input className="input" type="date" style={{ width: 'auto', height: 32 }} value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label className="row small muted">s.d. <input className="input" type="date" style={{ width: 'auto', height: 32 }} value={to} onChange={(e) => setTo(e.target.value)} /></label>
        <span className="spacer" />
        <span>OTIF <b>{q.data?.otifPct == null ? '–' : `${fmtNumber(q.data.otifPct)}%`}</b></span>
        <span>WO terlambat <b>{q.data?.lateWo ?? 0}</b></span>
      </div>
      <Err e={q.error} />
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">Work order</div>
        <DataTable rows={q.data?.workOrders ?? []} rowKey={(r) => String(r.id)} empty="Tidak ada WO pada periode ini"
          columns={[
            { key: 'doc_no', label: 'WO', mono: true },
            { key: 'item', label: 'Produk', render: (r) => `${r.item_code} · ${r.item_name}` },
            { key: 'line_code', label: 'Lini', mono: true },
            { key: 'planned', label: 'Rencana', render: (r) => `${fmtDate(String(r.planned_start))} – ${fmtDate(String(r.planned_end))}` },
            { key: 'qty_plan', label: 'Rencana', align: 'right', render: (r) => fmtNumber(n(r.qty_plan)) },
            { key: 'qty_good', label: 'Hasil', align: 'right', render: (r) => fmtNumber(n(r.qty_good)) },
            { key: 'yield_pct', label: 'Yield', align: 'right', render: (r) => (r.yield_pct == null ? '–' : `${fmtNumber(n(r.yield_pct))}%`) },
            { key: 'late', label: 'Ketepatan', render: (r) => <StatusChip status={r.late ? 'REJECTED' : 'APPROVED'} label={r.late ? 'Terlambat' : 'Tepat'} /> },
            { key: 'status', label: 'Status', render: (r) => <StatusChip status={String(r.status)} /> },
          ]} />
      </div>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">Pemenuhan pesanan (OTIF)</div>
        <DataTable rows={q.data?.orders ?? []} rowKey={(r) => `${r.doc_no}-${r.item_code}-${r.delivery_date}`} empty="Tidak ada pesanan jatuh tempo"
          columns={[
            { key: 'doc_no', label: 'Pesanan', mono: true },
            { key: 'customer', label: 'Customer' },
            { key: 'item_code', label: 'Produk', mono: true },
            { key: 'delivery_date', label: 'Tgl kirim', render: (r) => fmtDate(String(r.delivery_date)) },
            { key: 'qty', label: 'Qty', align: 'right', render: (r) => fmtNumber(n(r.qty)) },
            { key: 'on_time', label: 'Terkirim tepat waktu', align: 'right', render: (r) => fmtNumber(n(r.on_time)) },
            { key: 'ok', label: 'OTIF', render: (r) => <StatusChip status={n(r.on_time) >= n(r.qty) ? 'APPROVED' : 'REJECTED'} label={n(r.on_time) >= n(r.qty) ? 'Ya' : 'Tidak'} /> },
          ]} />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- SCM-21 Status stok & karantina

const QC = [['', 'Semua'], ['QUARANTINE', 'Karantina'], ['RELEASED', 'Released'], ['HOLD', 'Hold'], ['REJECTED', 'Rejected']];
const QC_TONE: Record<string, string> = { QUARANTINE: 'SUBMITTED', RELEASED: 'APPROVED', HOLD: 'DRAFT', REJECTED: 'REJECTED' };

export function LotStatusPage() {
  const [status, setStatus] = useState('QUARANTINE');
  const [term, setTerm] = useState('');
  const [act, setAct] = useState<{ lot: Row; to: string } | null>(null);
  const { me } = useAuth();
  const qa = Boolean(me?.apps?.includes('QMS'));
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['lots', status, term], queryFn: () => api.get<Row[]>('/scm/lots', { status, q: term }) });
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        <select className="input" style={{ width: 'auto', height: 32 }} value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status lot">
          {QC.map(([v, l]) => <option key={v} value={v}>Status: {l}</option>)}
        </select>
        <input className="input" type="search" style={{ maxWidth: 280, height: 32 }} placeholder="Cari lot, lot supplier, item" value={term} onChange={(e) => setTerm(e.target.value)} />
        <span className="small muted">{qa ? 'Perubahan status butuh tanda tangan elektronik (QA Release).' : 'Perubahan status hanya oleh QA Release Officer (QMS).'}</span>
      </div>
      <Err e={q.error} />
      <DataTable rows={q.data ?? []} rowKey={(r) => String(r.id)} empty={q.isLoading ? 'Memuat…' : 'Tidak ada lot'}
        columns={[
          { key: 'lot_no', label: 'Lot', mono: true },
          { key: 'item', label: 'Item', render: (r) => <span><span className="mono small">{String(r.item_code)}</span> {String(r.item_name)}</span> },
          { key: 'supplier_lot', label: 'Lot supplier', mono: true },
          { key: 'exp_date', label: 'Kedaluwarsa', render: (r) => fmtDate(r.exp_date as string) },
          { key: 'qty', label: 'Qty', align: 'right', render: (r) => `${fmtNumber(n(r.qty))} ${r.uom}` },
          { key: 'locations', label: 'Lokasi', render: (r) => <span className="small">{String(r.locations)}</span> },
          { key: 'qc_status', label: 'Status', render: (r) => <StatusChip status={QC_TONE[String(r.qc_status)] ?? 'DRAFT'} label={String(r.qc_status)} /> },
          { key: 'act', label: '', render: (r) => qa && r.qc_status !== 'REJECTED' ? (
            <span className="row" style={{ gap: 4 }}>
              {r.qc_status !== 'RELEASED' && <button className="btn btn-sm" type="button" onClick={() => setAct({ lot: r, to: 'RELEASED' })}>Release</button>}
              {r.qc_status !== 'HOLD' && <button className="btn btn-sm" type="button" onClick={() => setAct({ lot: r, to: 'HOLD' })}>Hold</button>}
              <button className="btn btn-sm" type="button" onClick={() => setAct({ lot: r, to: 'REJECTED' })}>Reject</button>
            </span>) : null },
        ]} />
      {act && <ReasonDialog title={`${act.to} lot ${act.lot.lot_no}`} confirmLabel="Tanda tangani" needReason needPassword danger={act.to === 'REJECTED'}
        description="Status lot menentukan apakah stok boleh dipakai produksi atau dikirim ke customer."
        onClose={() => setAct(null)}
        onConfirm={async (v) => {
          await api.post(`/scm/lots/${act.lot.id}/status`, { status: act.to, reason: v.reason, password: v.password });
          qc.invalidateQueries({ queryKey: ['lots'] });
        }} />}
    </div>
  );
}

// ---------------------------------------------------------------- SCM-22 Putaway

export function PutawayPage() {
  const toast = useToast();
  const [target, setTarget] = useState<Record<string, number | null>>({});
  const q = useQuery({ queryKey: ['putaway'], queryFn: () => api.get<Row[]>('/scm/putaway') });
  const key = (r: Row) => `${r.item_id}-${r.lot_id}-${r.location_id}`;
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar"><span className="small muted">Stok Released yang masih di lokasi karantina. Saran bin mengikuti kelas suhu item & lokasi B3; bisa diganti.</span></div>
      <Err e={q.error} />
      <DataTable rows={q.data ?? []} rowKey={key} empty={q.isLoading ? 'Memuat…' : 'Tidak ada stok yang menunggu putaway'}
        columns={[
          { key: 'item', label: 'Item', render: (r) => <span><span className="mono small">{String(r.item_code)}</span> {String(r.item_name)}</span> },
          { key: 'lot_no', label: 'Lot', mono: true },
          { key: 'exp_date', label: 'ED', render: (r) => fmtDate(r.exp_date as string) },
          { key: 'qty', label: 'Qty', align: 'right', render: (r) => `${fmtNumber(n(r.qty))} ${r.uom}` },
          { key: 'from_label', label: 'Dari', mono: true },
          { key: 'to', label: 'Ke bin', render: (r) => (
            <div style={{ width: 220 }}>
              <LookupSelect lookup="bins" value={target[key(r)] ?? (r.suggest_location_id as number | null)} filters={{ warehouse_id: String(r.warehouse_id), is_quarantine: 'false' }}
                onChange={(v) => setTarget((t) => ({ ...t, [key(r)]: v }))} />
            </div>) },
          { key: 'act', label: '', render: (r) => (
            <button className="btn btn-sm btn-dark" type="button" onClick={async () => {
              try {
                await api.post('/scm/putaway', { itemId: r.item_id, lotId: r.lot_id, fromLocationId: r.location_id,
                  toLocationId: target[key(r)] ?? r.suggest_location_id, qty: r.qty });
                toast.ok('Dipindahkan');
                q.refetch();
              } catch (e) {
                toast.error(e);
              }
            }}>Pindahkan</button>) },
        ]} />
    </div>
  );
}

// ---------------------------------------------------------------- SCM-23 Picking & serah bahan

interface Pick { lineId: number; itemId: number; itemLabel: string; uom: string; lotId: number | null; lotNo: string | null; expDate: string | null;
  locationId: number | null; binCode: string | null; qty: number; shortage: string | null }

export function PickingPage() {
  const toast = useToast();
  const [open, setOpen] = useState<Row | null>(null);
  const [picks, setPicks] = useState<(Pick & { take: number; on: boolean })[]>([]);
  const mr = useQuery({ queryKey: ['to-issue'], queryFn: () => api.get<Row[]>('/pre/material-requests/to-issue') });
  const mrt = useQuery({ queryKey: ['to-receive'], queryFn: () => api.get<Row[]>('/pre/material-returns/to-receive') });
  const load = async (r: Row) => {
    try {
      const p = await api.get<Pick[]>(`/pre/material-requests/${r.id}/picks`);
      setPicks(p.map((x) => ({ ...x, take: x.qty, on: !x.shortage })));
      setOpen(r);
    } catch (e) {
      toast.error(e);
    }
  };
  const issue = async () => {
    try {
      await api.post(`/pre/material-requests/${open!.id}/issue`, {
        picks: picks.filter((p) => p.on && p.lotId !== undefined && p.take > 0).map((p) => ({ lineId: p.lineId, lotId: p.lotId, locationId: p.locationId, qty: p.take })),
      });
      toast.ok('Bahan diserahkan ke produksi');
      setOpen(null);
      mr.refetch();
    } catch (e) {
      toast.error(e);
    }
  };
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">Permintaan bahan siap disiapkan (PRE-04)</div>
        <Err e={mr.error} />
        <DataTable rows={mr.data ?? []} rowKey={(r) => String(r.id)} onRowClick={load} empty="Tidak ada permintaan"
          columns={[
            { key: 'doc_no', label: 'Permintaan', mono: true },
            { key: 'needed_at', label: 'Dibutuhkan', render: (r) => fmtDate(r.needed_at as string) },
            { key: 'wo_no', label: 'WO', mono: true },
            { key: 'batch_no', label: 'Batch', mono: true },
            { key: 'product', label: 'Produk' },
            { key: 'line_code', label: 'Lini', mono: true },
            { key: 'open_lines', label: 'Baris terbuka', align: 'right' },
          ]} />
      </div>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">Retur sisa bahan menunggu diterima (PRE-10)</div>
        <DataTable rows={mrt.data ?? []} rowKey={(r) => String(r.id)} empty="Tidak ada retur"
          columns={[
            { key: 'doc_no', label: 'Retur', mono: true },
            { key: 'doc_date', label: 'Tanggal', render: (r) => fmtDate(r.doc_date as string) },
            { key: 'wo_no', label: 'WO', mono: true },
            { key: 'batch_no', label: 'Batch', mono: true },
            { key: 'lines', label: 'Baris', align: 'right' },
            { key: 'act', label: '', render: (r) => <button className="btn btn-sm btn-dark" type="button" onClick={async () => {
              try { await api.post(`/pre/material-returns/${r.id}/receive`, []); toast.ok('Sisa bahan diterima ke lokasi simpan'); mrt.refetch(); } catch (e) { toast.error(e); }
            }}>Terima</button> },
          ]} />
      </div>
      {open && (
        <Modal title={`Serah bahan ${open.doc_no} · batch ${open.batch_no}`} width={900} onClose={() => setOpen(null)}
          footer={<><button className="btn" type="button" onClick={() => setOpen(null)}>Batal</button>
            <button className="btn btn-dark" type="button" onClick={issue}>Serahkan ke produksi</button></>}>
          <div className="small muted" style={{ marginBottom: 8 }}>Pick FEFO: lot Released dengan kedaluwarsa paling awal. Qty bisa dikurangi bila serah sebagian.</div>
          <div className="table-wrap">
            <table className="t">
              <thead><tr><th /><th>Bahan</th><th>Lot</th><th>ED</th><th>Bin</th><th className="num">Qty</th></tr></thead>
              <tbody>
                {picks.map((p, i) => (
                  <tr key={i}>
                    <td><input type="checkbox" disabled={Boolean(p.shortage)} checked={p.on} onChange={(e) => setPicks((x) => x.map((y, j) => (j === i ? { ...y, on: e.target.checked } : y)))} /></td>
                    <td>{p.itemLabel}</td>
                    <td className="mono">{p.shortage ? <span style={{ color: 'var(--danger)' }}>{p.shortage}</span> : p.lotNo}</td>
                    <td>{fmtDate(p.expDate)}</td>
                    <td className="mono">{p.binCode}</td>
                    <td className="num"><input className="input mono" style={numInput} value={p.take} disabled={Boolean(p.shortage)}
                      onChange={(e) => setPicks((x) => x.map((y, j) => (j === i ? { ...y, take: Number(e.target.value || 0) } : y)))} /> {p.uom}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Modal>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- SCM-24 Terima barang jadi

export function FgReceiptPage() {
  const toast = useToast();
  const [pending, setPending] = useState(true);
  const [conf, setConf] = useState<{ row: Row; qty: string; note: string } | null>(null);
  const q = useQuery({ queryKey: ['fg-receipts', pending], queryFn: () => api.get<Row[]>('/scm/fg-receipts', { pendingOnly: pending }) });
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        <label className="row small"><input type="checkbox" checked={pending} onChange={(e) => setPending(e.target.checked)} />Hanya yang belum dikonfirmasi</label>
        <span className="small muted">Hasil produksi masuk lokasi karantina gudang barang jadi; gudang mengonfirmasi jumlah fisik.</span>
      </div>
      <Err e={q.error} />
      <DataTable rows={q.data ?? []} rowKey={(r) => String(r.id)} empty="Tidak ada serah terima"
        columns={[
          { key: 'doc_no', label: 'Hasil produksi', mono: true },
          { key: 'doc_date', label: 'Tanggal', render: (r) => fmtDate(r.doc_date as string) },
          { key: 'item', label: 'Produk', render: (r) => `${r.item_code} · ${r.item_name}` },
          { key: 'batch_no', label: 'Batch', mono: true },
          { key: 'qty_good', label: 'Dilaporkan', align: 'right', render: (r) => fmtNumber(n(r.qty_good)) },
          { key: 'received_qty', label: 'Diterima', align: 'right', render: (r) => (r.received_qty == null ? '–' : fmtNumber(n(r.received_qty))) },
          { key: 'location', label: 'Lokasi', mono: true },
          { key: 'qc_status', label: 'Status QC', render: (r) => <StatusChip status={QC_TONE[String(r.qc_status)] ?? 'DRAFT'} label={String(r.qc_status)} /> },
          { key: 'act', label: '', render: (r) => (r.received_at ? <span className="small muted">{String(r.received_by_name ?? '')}</span>
            : <button className="btn btn-sm btn-dark" type="button" onClick={() => setConf({ row: r, qty: String(r.qty_good), note: '' })}>Konfirmasi</button>) },
        ]} />
      {conf && (
        <Modal title={`Terima ${conf.row.batch_no}`} onClose={() => setConf(null)}
          footer={<><button className="btn" type="button" onClick={() => setConf(null)}>Batal</button>
            <button className="btn btn-dark" type="button" onClick={async () => {
              try {
                await api.post(`/pre/outputs/${conf.row.id}/receive`, { qty: Number(conf.qty), note: conf.note || null });
                toast.ok('Serah terima dikonfirmasi');
                setConf(null);
                q.refetch();
              } catch (e) {
                toast.error(e);
              }
            }}>Konfirmasi</button></>}>
          <div className="field"><label>Qty fisik diterima</label><input className="input" value={conf.qty} onChange={(e) => setConf({ ...conf, qty: e.target.value })} /></div>
          <div className="field" style={{ marginTop: 10 }}><label>Catatan (wajib bila berbeda)</label><textarea className="input" rows={3} value={conf.note} onChange={(e) => setConf({ ...conf, note: e.target.value })} /></div>
        </Modal>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- SCM-40 Kartu stok & saldo per lot

export function StockCardPage() {
  const [itemId, setItemId] = useState<number | null>(null);
  const [warehouseId, setWarehouseId] = useState<number | null>(null);
  const [lot, setLot] = useState<number | null>(null);
  const stock = useQuery({ queryKey: ['stock', itemId, warehouseId], queryFn: () => api.get<Row[]>('/scm/stock', { itemId, warehouseId }) });
  const moves = useQuery({ queryKey: ['moves', itemId, lot], queryFn: () => api.get<Row[]>('/scm/moves', { itemId, lotId: lot }), enabled: itemId != null || lot != null });
  const stockRows: Row[] = (Array.isArray(stock.data) ? stock.data : (stock.data as any)?.content) ?? [];
  const movesRows: Row[] = (Array.isArray(moves.data) ? moves.data : (moves.data as any)?.content) ?? [];
  const total = stockRows.reduce((s: number, r: Row) => s + n(r.qty) * n(r.unitCost), 0);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="toolbar">
          <div style={{ width: 280 }}><LookupSelect lookup="items" value={itemId} onChange={(v) => { setItemId(v); setLot(null); }} placeholder="Semua item" /></div>
          <div style={{ width: 220 }}><LookupSelect lookup="warehouses" value={warehouseId} onChange={setWarehouseId} placeholder="Semua gudang" /></div>
          <span className="spacer" />
          <span>Nilai <b className="mono">{fmtRp(total)}</b></span>
          <button className="btn" type="button" onClick={() => exportCsv(`stok-${todayIso()}.csv`, ['Item', 'Lot', 'Status', 'ED', 'Gudang', 'Bin', 'Qty', 'Biaya'],
            stockRows.map((r) => [r.itemCode as string, r.lotNo as string, r.qcStatus as string, r.expDate as string, r.warehouse as string, r.binCode as string, n(r.qty), n(r.unitCost)]))}>
            <Icon name="download" size={14} />Ekspor</button>
        </div>
        <Err e={stock.error} />
        <DataTable rows={stockRows} rowKey={(r) => `${r.itemId}-${r.lotId}-${r.locationId}`} onRowClick={(r) => { setItemId(r.itemId as number); setLot(r.lotId as number | null); }}
          empty={stock.isLoading ? 'Memuat…' : 'Tidak ada stok'}
          columns={[
            { key: 'item', label: 'Item', render: (r) => <span><span className="mono small">{String(r.itemCode)}</span> {String(r.itemName)}</span> },
            { key: 'lotNo', label: 'Lot', mono: true },
            { key: 'qcStatus', label: 'Status', render: (r) => (r.qcStatus ? <StatusChip status={QC_TONE[String(r.qcStatus)] ?? 'DRAFT'} label={String(r.qcStatus)} /> : '') },
            { key: 'expDate', label: 'ED', render: (r) => fmtDate(r.expDate as string) },
            { key: 'loc', label: 'Lokasi', render: (r) => <span className="mono small">{String(r.warehouse)}/{String(r.binCode)}</span> },
            { key: 'qty', label: 'Qty', align: 'right', render: (r) => fmtNumber(n(r.qty)) },
            { key: 'qtyReserved', label: 'Dipesan', align: 'right', render: (r) => (n(r.qtyReserved) ? fmtNumber(n(r.qtyReserved)) : '') },
            { key: 'unitCost', label: 'Biaya satuan', align: 'right', render: (r) => fmtRp(n(r.unitCost)) },
          ]} />
      </div>
      {(itemId != null || lot != null) && (
        <div className="card" style={{ overflow: 'hidden' }}>
          <div className="card-h">Kartu stok {lot ? '(lot terpilih)' : '(item)'} <span className="small muted" style={{ fontWeight: 400 }}>1.000 gerak terakhir</span></div>
          <DataTable rows={movesRows} rowKey={(r) => String(r.id)} empty="Belum ada gerak"
            columns={[
              { key: 'moveDate', label: 'Tanggal', render: (r) => fmtDate(r.moveDate as string) },
              { key: 'moveType', label: 'Gerak', mono: true },
              { key: 'refDocNo', label: 'Dokumen', mono: true },
              { key: 'lotNo', label: 'Lot', mono: true },
              { key: 'fromBin', label: 'Dari', mono: true },
              { key: 'toBin', label: 'Ke', mono: true },
              { key: 'qty', label: 'Qty', align: 'right', render: (r) => fmtNumber(n(r.qty)) },
              { key: 'unitCost', label: 'Biaya', align: 'right', render: (r) => fmtRp(n(r.unitCost)) },
              { key: 'reason', label: 'Keterangan', render: (r) => <span className="small muted">{String(r.reason ?? '')}</span> },
            ]} />
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- SCM-44 Kedaluwarsa & slow moving

export function ExpiryPage() {
  const [months, setMonths] = useState(6);
  const [idle, setIdle] = useState(90);
  const q = useQuery({ queryKey: ['expiry', months, idle], queryFn: () => api.get<{ expiring: Row[]; slowMoving: Row[] }>('/scm/expiry', { months, idleDays: idle }) });
  const cols = [
    { key: 'lot_no', label: 'Lot', mono: true },
    { key: 'item', label: 'Item', render: (r: Row) => `${r.item_code} · ${r.item_name}` },
    { key: 'exp_date', label: 'ED', render: (r: Row) => <span style={{ color: String(r.exp_date ?? '') < todayIso() ? 'var(--danger)' : undefined }}>{fmtDate(r.exp_date as string)}</span> },
    { key: 'qc_status', label: 'Status', render: (r: Row) => <StatusChip status={QC_TONE[String(r.qc_status)] ?? 'DRAFT'} label={String(r.qc_status)} /> },
    { key: 'qty', label: 'Qty', align: 'right' as const, render: (r: Row) => `${fmtNumber(n(r.qty))} ${r.uom}` },
    { key: 'value', label: 'Nilai', align: 'right' as const, render: (r: Row) => fmtRp(n(r.value)) },
    { key: 'last_move', label: 'Gerak terakhir', render: (r: Row) => fmtDate(r.last_move as string) },
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card toolbar">
        <label className="row small muted">ED kurang dari <input className="input" type="number" style={{ width: 70, height: 32 }} value={months} onChange={(e) => setMonths(Number(e.target.value))} /> bulan</label>
        <label className="row small muted">Tidak bergerak lebih dari <input className="input" type="number" style={{ width: 70, height: 32 }} value={idle} onChange={(e) => setIdle(Number(e.target.value))} /> hari</label>
        <span className="small muted">Tindak lanjut: prioritas kirim/produksi (MPS) atau usulan pemusnahan (SCM-46).</span>
      </div>
      <Err e={q.error} />
      <div className="card" style={{ overflow: 'hidden' }}><div className="card-h">Mendekati kedaluwarsa</div>
        <DataTable rows={q.data?.expiring ?? []} rowKey={(r) => String(r.id)} empty="Tidak ada" columns={cols} /></div>
      <div className="card" style={{ overflow: 'hidden' }}><div className="card-h">Slow moving</div>
        <DataTable rows={q.data?.slowMoving ?? []} rowKey={(r) => String(r.id)} empty="Tidak ada" columns={cols} /></div>
    </div>
  );
}

// ---------------------------------------------------------------- SCM-45 Penelusuran lot

const TRACE_KIND: Record<string, string> = { GR: 'Penerimaan', WO: 'Batch produksi', MATERIAL: 'Bahan dipakai', DO: 'Dikirim ke' };

export function TracePage() {
  const [lot, setLot] = useState<number | null>(null);
  const q = useQuery({ queryKey: ['trace', lot], queryFn: () => api.get<{ lot: Row; backward: Row[]; forward: Row[] }>('/scm/trace', { lotId: lot }), enabled: lot != null });
  const list = (rows: Row[]) => (
    <div style={{ padding: '6px 0' }}>
      {rows.length === 0 && <div className="empty small">Tidak ada jejak</div>}
      {rows.map((r, i) => (
        <div key={i} className="row" style={{ padding: '7px 16px', paddingLeft: 16 + n(r.depth) * 22, borderTop: '1px solid var(--line-soft)' }}>
          <span className="lbl" style={{ width: 110, flex: 'none' }}>{TRACE_KIND[String(r.kind)] ?? String(r.kind)}</span>
          <span className="mono small" style={{ width: 150, flex: 'none' }}>{String(r.doc_no ?? '')}</span>
          <span style={{ flex: 1 }}>{String(r.party ?? '')} {r.ref ? <span className="mono small muted">· {String(r.ref)}</span> : null}</span>
          <span className="mono small">{r.qty != null ? fmtNumber(n(r.qty)) : ''}</span>
          <span className="small muted" style={{ width: 90, textAlign: 'right' }}>{fmtDate(r.doc_date as string)}</span>
        </div>
      ))}
    </div>
  );
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card toolbar">
        <div style={{ width: 320 }}><LookupSelect lookup="lots" value={lot} onChange={setLot} placeholder="Pilih nomor lot / batch" /></div>
        {q.data && <span><b className="mono">{String(q.data.lot.lot_no)}</b> · {String(q.data.lot.item_code)} {String(q.data.lot.item_name)} · <StatusChip status={QC_TONE[String(q.data.lot.qc_status)] ?? 'DRAFT'} label={String(q.data.lot.qc_status)} /></span>}
      </div>
      <Err e={q.error} />
      {q.data && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'flex-start' }}>
          <div className="card" style={{ flex: '1 1 420px', minWidth: 0, overflow: 'hidden' }}><div className="card-h">Mundur: asal bahan & supplier</div>{list(q.data.backward)}</div>
          <div className="card" style={{ flex: '1 1 420px', minWidth: 0, overflow: 'hidden' }}><div className="card-h">Maju: batch & customer</div>{list(q.data.forward)}</div>
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- SCM-90 Laporan supply chain

export function ScmReportPage() {
  const [month, setMonth] = useState(todayIso().slice(0, 7));
  const [y, m] = month.split('-').map(Number);
  const q = useQuery({ queryKey: ['scm-report', month], queryFn: () => api.get<{ forecastAccuracy: Row[]; aging: Row[]; otifPct: number | null; stockAccuracyPct: number | null }>('/scm/report', { year: y, month: m }) });
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card toolbar"><label className="row small muted">Bulan <input className="input" type="month" style={{ width: 'auto', height: 32 }} value={month} onChange={(e) => setMonth(e.target.value)} /></label></div>
      <Err e={q.error} />
      <div className="kpis">
        <div className="card kpi"><span className="small muted">OTIF</span><span className="v">{q.data?.otifPct == null ? '–' : `${fmtNumber(q.data.otifPct)}%`}</span><span className="small muted">pesanan jatuh tempo bulan ini</span></div>
        <div className="card kpi"><span className="small muted">Akurasi stok</span><span className="v">{q.data?.stockAccuracyPct == null ? '–' : `${fmtNumber(q.data.stockAccuracyPct)}%`}</span><span className="small muted">baris opname tanpa selisih</span></div>
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'flex-start' }}>
        <div className="card" style={{ flex: '2 1 480px', minWidth: 0, overflow: 'hidden' }}><div className="card-h">Akurasi forecast</div>
          <DataTable rows={q.data?.forecastAccuracy ?? []} rowKey={(r) => String(r.code)} empty="Tidak ada data"
            columns={[
              { key: 'code', label: 'Produk', render: (r) => `${r.code} · ${r.name}` },
              { key: 'forecast', label: 'Forecast', align: 'right', render: (r) => fmtNumber(n(r.forecast)) },
              { key: 'actual', label: 'Aktual', align: 'right', render: (r) => fmtNumber(n(r.actual)) },
              { key: 'accuracy_pct', label: 'Akurasi', align: 'right', render: (r) => (r.accuracy_pct == null ? '–' : `${fmtNumber(n(r.accuracy_pct))}%`) },
            ]} /></div>
        <div className="card" style={{ flex: '1 1 300px', minWidth: 0, overflow: 'hidden' }}><div className="card-h">Umur stok (per lot)</div>
          <DataTable rows={q.data?.aging ?? []} rowKey={(r) => String(r.bucket)} empty="Tidak ada stok"
            columns={[{ key: 'bucket', label: 'Umur' }, { key: 'lots', label: 'Lot', align: 'right' }, { key: 'value', label: 'Nilai', align: 'right', render: (r) => fmtRp(n(r.value)) }]} /></div>
      </div>
    </div>
  );
}
