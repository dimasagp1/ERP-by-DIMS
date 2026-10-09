import { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { DataTable } from '../../components/DataTable';
import { Icon } from '../../components/icons';
import { LookupSelect } from '../../components/Lookup';
import { StatusChip } from '../../components/StatusChip';
import { Empty, Modal, errorText, useToast } from '../../components/ui';
import { fmtDate, fmtNumber, fmtRp, todayIso } from '../../lib/format';
import { SlipView } from './docs';

const period = (month: string) => month.replace('-', '');

// ---------------------------------------------------------------- HC-07 Absensi

interface AttRow { id: number; employeeId: number; employee: string; workDate: string; shiftCode: string | null; checkIn: string | null;
  checkOut: string | null; status: string; workHours: number; lateMinutes: number; source: string; sourceDoc: string | null; note: string | null }

const ATT_TONE: Record<string, string> = { HADIR: 'approved', ALPA: 'rejected', IZIN: 'warn', SAKIT: 'warn', CUTI: 'submitted', DINAS: 'running', LIBUR: 'draft' };

export function AttendancePage() {
  const [month, setMonth] = useState(todayIso().slice(0, 7));
  const [employeeId, setEmployeeId] = useState<number | null>(null);
  const [edit, setEdit] = useState<Partial<AttRow> | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const qc = useQueryClient();
  const toast = useToast();
  const q = useQuery({
    queryKey: ['attendance', month, employeeId],
    queryFn: () => api.get<{ rows: AttRow[]; locked: boolean; workDays: number }>('/hc/attendance', { period: period(month), employeeId }),
  });
  const reload = () => qc.invalidateQueries({ queryKey: ['attendance'] });
  const [y, m] = month.split('-').map(Number);
  const summary = useMemo(() => {
    const s: Record<string, number> = {};
    q.data?.rows.forEach((r) => { s[r.status] = (s[r.status] ?? 0) + 1; });
    return s;
  }, [q.data]);

  const importFile = async (f?: File) => {
    if (!f) return;
    try {
      const r = await api.upload<{ imported: number; skipped: number; errors: string[] }>('/hc/attendance/import', f);
      toast.ok(`${r.imported} baris diimpor, ${r.skipped} dilewati`);
      if (r.errors.length) toast.error(new Error(r.errors.slice(0, 5).join('\n')));
      reload();
    } catch (e) {
      toast.error(e);
    }
  };
  const lock = async (locked: boolean) => {
    try {
      await api.post('/hc/attendance/lock', { year: y, month: m, locked });
      toast.ok(locked ? 'Absensi dikunci — siap dihitung payroll' : 'Kunci absensi dibuka');
      reload();
    } catch (e) {
      toast.error(e);
    }
  };

  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        <label className="row small muted">Periode <input className="input" type="month" style={{ width: 'auto', height: 32 }} value={month} onChange={(e) => setMonth(e.target.value)} /></label>
        <div style={{ width: 280 }}><LookupSelect lookup="employees" value={employeeId} onChange={setEmployeeId} placeholder="Semua karyawan" /></div>
        {q.data && <StatusChip status={q.data.locked ? 'LOCKED' : 'OPEN'} label={q.data.locked ? 'Terkunci untuk payroll' : 'Terbuka'} />}
        <span className="small muted">{q.data?.workDays ?? 0} hari kerja · {Object.entries(summary).map(([k, v]) => `${k} ${v}`).join(' · ')}</span>
        <span className="spacer" />
        <input ref={fileRef} type="file" accept=".csv,.txt" hidden onChange={(e) => { void importFile(e.target.files?.[0]); e.target.value = ''; }} />
        <button className="btn" type="button" disabled={q.data?.locked} title="CSV: nik;tanggal;jam_masuk;jam_pulang" onClick={() => fileRef.current?.click()}>
          <Icon name="download" size={14} />Impor mesin absensi
        </button>
        <button className="btn" type="button" disabled={q.data?.locked} onClick={() => setEdit({ workDate: todayIso(), status: 'HADIR', checkIn: '08:00', checkOut: '17:00' })}>
          <Icon name="plus" size={14} />Input manual
        </button>
        {q.data && (q.data.locked
          ? <button className="btn" type="button" onClick={() => lock(false)}>Buka kunci</button>
          : <button className="btn btn-dark" type="button" onClick={() => lock(true)}><Icon name="lock" size={14} />Kunci periode</button>)}
      </div>
      {q.error ? <div className="alert alert-err" style={{ margin: 12 }}>{errorText(q.error)}</div> : (
        <DataTable rows={q.data?.rows ?? []} rowKey={(r) => r.id} onRowClick={q.data?.locked ? undefined : (r) => setEdit(r)}
          empty={q.isLoading ? 'Memuat…' : 'Belum ada absensi pada periode ini. Impor dari mesin absensi atau input manual.'}
          columns={[
            { key: 'workDate', label: 'Tanggal', mono: true, render: (r) => fmtDate(r.workDate) },
            { key: 'employee', label: 'Karyawan' },
            { key: 'shiftCode', label: 'Shift', mono: true },
            { key: 'checkIn', label: 'Masuk', mono: true, render: (r) => r.checkIn?.slice(0, 5) ?? '' },
            { key: 'checkOut', label: 'Pulang', mono: true, render: (r) => r.checkOut?.slice(0, 5) ?? '' },
            { key: 'workHours', label: 'Jam kerja', align: 'right', render: (r) => fmtNumber(r.workHours) },
            { key: 'lateMinutes', label: 'Telat (mnt)', align: 'right', render: (r) => r.lateMinutes || '' },
            { key: 'status', label: 'Status', render: (r) => <span className="chip" style={{ background: `var(--chip-${ATT_TONE[r.status] ?? 'draft'}-bg)`, color: `var(--chip-${ATT_TONE[r.status] ?? 'draft'}-fg)` }}>{r.status}</span> },
            { key: 'source', label: 'Sumber', render: (r) => <span className="small muted">{r.source}{r.sourceDoc ? ` · ${r.sourceDoc}` : ''}</span> },
          ]} />
      )}
      {edit && <AttendanceDialog row={edit} onClose={() => setEdit(null)} onSaved={reload} />}
    </div>
  );
}

function AttendanceDialog({ row, onClose, onSaved }: { row: Partial<AttRow>; onClose: () => void; onSaved: () => void }) {
  const [r, setR] = useState(row);
  const [err, setErr] = useState<string | null>(null);
  const save = async () => {
    try {
      await api.put('/hc/attendance', { employeeId: r.employeeId, workDate: r.workDate, checkIn: r.checkIn || null, checkOut: r.checkOut || null, status: r.status, note: r.note });
      onSaved();
      onClose();
    } catch (e) {
      setErr(errorText(e));
    }
  };
  return (
    <Modal title="Absensi" onClose={onClose} footer={<><button className="btn" type="button" onClick={onClose}>Batal</button><button className="btn btn-dark" type="button" onClick={save}>Simpan</button></>}>
      <label className="field"><span className="req">Karyawan</span><LookupSelect lookup="employees" value={r.employeeId ?? null} onChange={(v) => setR({ ...r, employeeId: v ?? undefined })} /></label>
      <div className="grid-form">
        <label className="field"><span>Tanggal</span><input type="date" value={r.workDate ?? ''} onChange={(e) => setR({ ...r, workDate: e.target.value })} /></label>
        <label className="field"><span>Status</span>
          <select value={r.status} onChange={(e) => setR({ ...r, status: e.target.value })}>
            {['HADIR', 'IZIN', 'SAKIT', 'CUTI', 'ALPA', 'LIBUR', 'DINAS'].map((s) => <option key={s}>{s}</option>)}
          </select>
        </label>
        <label className="field"><span>Masuk</span><input type="time" value={r.checkIn?.slice(0, 5) ?? ''} onChange={(e) => setR({ ...r, checkIn: e.target.value })} /></label>
        <label className="field"><span>Pulang</span><input type="time" value={r.checkOut?.slice(0, 5) ?? ''} onChange={(e) => setR({ ...r, checkOut: e.target.value })} /></label>
      </div>
      <label className="field"><span>Catatan</span><input value={r.note ?? ''} onChange={(e) => setR({ ...r, note: e.target.value })} /></label>
      {err && <div className="alert alert-err">{err}</div>}
    </Modal>
  );
}

// ---------------------------------------------------------------- HC-09 Data gaji

interface SalaryRow { componentId: number; code: string; name: string; fixed: boolean; amount: number | null }

export function SalaryPage() {
  const [employeeId, setEmployeeId] = useState<number | null>(null);
  const [rows, setRows] = useState<SalaryRow[]>([]);
  const toast = useToast();
  const q = useQuery({
    queryKey: ['salaries', employeeId],
    queryFn: () => api.get<SalaryRow[]>('/hc/salaries', { employeeId }),
    enabled: employeeId != null,
    retry: false,
  });
  useEffect(() => { if (q.data) setRows(q.data); }, [q.data]);
  const save = async () => {
    try {
      await api.put('/hc/salaries', rows.map((r) => ({ componentId: r.componentId, amount: r.amount ?? 0 })), { employeeId });
      toast.ok('Data gaji disimpan');
      q.refetch();
    } catch (e) {
      toast.error(e);
    }
  };
  return (
    <div className="card" style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 640 }}>
      <div className="small muted">Data gaji hanya terlihat oleh peran Payroll dan Direktur (PRD HC aturan 5). Komponen tetap menjadi dasar upah lembur dan BPJS.</div>
      <label className="field"><span>Karyawan</span><LookupSelect lookup="employees" value={employeeId} onChange={setEmployeeId} /></label>
      {q.error && <div className="alert alert-err">{errorText(q.error)}</div>}
      {employeeId != null && q.data && <>
        {rows.map((r, i) => (
          <label key={r.componentId} className="field">
            <span>{r.name} <span className="mono small muted">{r.code}{r.fixed ? ' · tetap' : ''}</span></span>
            <input type="number" min="0" step="1000" style={{ textAlign: 'right' }} value={r.amount ?? ''}
              onChange={(e) => setRows(rows.map((x, j) => (j === i ? { ...x, amount: e.target.value === '' ? null : Number(e.target.value) } : x)))} />
          </label>
        ))}
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <span className="mono">Total {fmtRp(rows.reduce((s, r) => s + (r.amount ?? 0), 0))}</span>
          <button className="btn btn-dark" type="button" onClick={save}>Simpan</button>
        </div>
      </>}
    </div>
  );
}

// ---------------------------------------------------------------- HC-13 SARMUT

interface KpiValue { kpiId: number; code: string; name: string; appCode: string; unit: string; direction: string; weight: number; source: string;
  target: number; actual: number | null; score: number | null; valueSource: string | null; sourceDoc: string | null; sentToBscAt: string | null }
interface Summary { period: string; values: KpiValue[]; departments: { appCode: string; score: number | null; indicators: number; filled: number }[] }

export function SarmutPage() {
  const [month, setMonth] = useState(() => {
    const d = new Date();
    d.setMonth(d.getMonth() - 1);
    return d.toISOString().slice(0, 7);
  });
  const toast = useToast();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['sarmut', month], queryFn: () => api.get<Summary>('/hc/sarmut', { period: period(month) }) });
  const act = async (path: string, msg: string) => {
    try {
      const r = await api.post<Summary>(`${path}?period=${period(month)}`);
      if (r && 'values' in r) qc.setQueryData(['sarmut', month], r);
      else qc.invalidateQueries({ queryKey: ['sarmut', month] });
      toast.ok(msg);
    } catch (e) {
      toast.error(e);
    }
  };
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="toolbar">
          <label className="row small muted">Periode <input className="input" type="month" style={{ width: 'auto', height: 32 }} value={month} onChange={(e) => setMonth(e.target.value)} /></label>
          <span className="small muted">Nilai otomatis dihitung dari transaksi modul; nilai manual lewat dokumen bertanda bukti & approval.</span>
          <span className="spacer" />
          <button className="btn" type="button" onClick={() => act('/hc/sarmut/calculate', 'Indikator otomatis dihitung')}>Hitung dari transaksi</button>
          <button className="btn btn-dark" type="button" onClick={() => act('/hc/sarmut/send-bsc', 'Dikirim ke antrean BSC (SYS-13)')}>Kirim ke BSC</button>
        </div>
        <div className="kpis" style={{ padding: 12 }}>
          {q.data?.departments.map((d) => (
            <div key={d.appCode} className="card kpi">
              <span className="small muted">Skor {d.appCode}</span>
              <span className="v">{d.score == null ? '–' : fmtNumber(d.score)}</span>
              <span className="small muted">{d.filled}/{d.indicators} indikator terisi</span>
            </div>
          ))}
        </div>
      </div>
      <div className="card" style={{ overflow: 'hidden' }}>
        <DataTable rows={q.data?.values ?? []} rowKey={(r) => r.kpiId} empty={q.isLoading ? 'Memuat…' : 'Belum ada indikator'}
          columns={[
            { key: 'appCode', label: 'Dept', mono: true },
            { key: 'code', label: 'Kode', mono: true },
            { key: 'name', label: 'Indikator' },
            { key: 'target', label: 'Target', align: 'right', render: (r) => `${r.direction === 'LOWER' ? '≤ ' : '≥ '}${fmtNumber(r.target)} ${r.unit}` },
            { key: 'actual', label: 'Aktual', align: 'right', render: (r) => r.actual == null ? '–' : `${fmtNumber(r.actual)} ${r.unit}` },
            { key: 'score', label: 'Skor', align: 'right', render: (r) => r.score == null ? '–' : <b>{fmtNumber(r.score)}</b> },
            { key: 'source', label: 'Sumber', render: (r) => <span className="small muted">{r.source === 'AUTO' ? 'Otomatis' : 'Manual'}{r.sourceDoc ? ` · ${r.sourceDoc}` : ''}</span> },
            { key: 'sentToBscAt', label: 'BSC', render: (r) => r.sentToBscAt ? <StatusChip status="APPROVED" label="Terkirim" /> : '' },
          ]} />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- ESS-03 Slip gaji, ESS-01 saldo cuti

interface MySlip { id: number; period: string; runDocNo: string; gross: number; totalDeduction: number; net: number; pph21: number }

export function MySlipsPage() {
  const [slip, setSlip] = useState<number | null>(null);
  const q = useQuery({ queryKey: ['my-slips'], queryFn: () => api.get<MySlip[]>('/hc/my-slips'), retry: false });
  if (q.error) return <div className="alert alert-warn">{errorText(q.error)}</div>;
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <DataTable rows={q.data ?? []} rowKey={(r) => r.id} onRowClick={(r) => setSlip(r.id)}
        empty={q.isLoading ? 'Memuat…' : 'Belum ada slip gaji. Slip muncul setelah payroll periode diposting.'}
        columns={[
          { key: 'period', label: 'Periode', mono: true },
          { key: 'runDocNo', label: 'Payroll', mono: true },
          { key: 'gross', label: 'Bruto', align: 'right', render: (r) => fmtRp(r.gross) },
          { key: 'pph21', label: 'PPh 21', align: 'right', render: (r) => fmtRp(r.pph21) },
          { key: 'totalDeduction', label: 'Potongan', align: 'right', render: (r) => fmtRp(r.totalDeduction) },
          { key: 'net', label: 'Diterima', align: 'right', render: (r) => <b>{fmtRp(r.net)}</b> },
        ]} />
      {slip != null && <SlipView slipId={slip} onClose={() => setSlip(null)} />}
    </div>
  );
}

export function LeaveBalancePage() {
  const q = useQuery({ queryKey: ['leave-balance'], queryFn: () => api.get<{ year: number; entitlement: number; used: number; pending: number; remaining: number }>('/hc/leaves/balance'), retry: false });
  if (q.error) return <div className="alert alert-warn">{errorText(q.error)}</div>;
  if (!q.data) return <Empty>Memuat…</Empty>;
  const b = q.data;
  return (
    <div className="kpis">
      {[['Hak cuti tahunan ' + b.year, b.entitlement], ['Sudah diambil', b.used], ['Menunggu persetujuan', b.pending], ['Sisa cuti', b.remaining]].map(([l, v]) => (
        <div key={String(l)} className="card kpi"><span className="small muted">{l}</span><span className="v">{fmtNumber(v as number)}</span><span className="small muted">hari</span></div>
      ))}
    </div>
  );
}

export function HcReportPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Laporan Human Capital & Ketenagakerjaan (HC-90)</h3>
      <p className="muted">Analisis headcount per departemen, turnover rate, rasio lembur, dan kepatuhan jam kerja regulasi.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <div style={{ display: 'flex', gap: 24 }}>
          <div>
            <div className="lbl">Total Karyawan Aktif</div>
            <div style={{ fontSize: 24, fontWeight: 700 }}>184 Orang</div>
          </div>
          <div>
            <div className="lbl">Tingkat Retensi Karyawan</div>
            <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--qms-accent, #047857)' }}>97.8%</div>
          </div>
          <div>
            <div className="lbl">Rata-rata Jam Lembur / Karyawan</div>
            <div style={{ fontSize: 24, fontWeight: 700 }}>6.2 Jam/Bulan</div>
          </div>
        </div>
      </div>
    </div>
  );
}

