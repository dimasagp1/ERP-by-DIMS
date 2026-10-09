import { useMemo, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { DataTable, exportCsv } from '../../components/DataTable';
import { Icon } from '../../components/icons';
import { LookupSelect } from '../../components/Lookup';
import { StatusChip } from '../../components/StatusChip';
import { errorText, useToast } from '../../components/ui';
import { fmtDate, fmtNumber, fmtRp, todayIso } from '../../lib/format';

type Row = Record<string, unknown>;
const n = (v: unknown) => Number(v ?? 0);
const money = (v: unknown) => <span className="mono">{fmtRp(n(v))}</span>;

function monthRange(month: string) {
  const [y, m] = month.split('-').map(Number);
  return { from: `${month}-01`, to: `${month}-${String(new Date(y, m, 0).getDate()).padStart(2, '0')}`, year: y, month: m };
}

// ---------------------------------------------------------------- FIN-22 umur piutang & umur hutang

function AgingTable({ path, title, withLimit }: { path: string; title: string; withLimit?: boolean }) {
  const [asOf, setAsOf] = useState(todayIso());
  const q = useQuery({ queryKey: ['aging', path, asOf], queryFn: () => api.get<Row[]>(path, { asOf }) });
  const rows = q.data ?? [];
  const total = (k: string) => rows.reduce((s, r) => s + n(r[k]), 0);
  const cols = ['current', 'd1_30', 'd31_60', 'd61_90', 'd90'];
  const labels = ['Belum jatuh tempo', '1–30', '31–60', '61–90', '> 90 hari'];
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        <span style={{ fontWeight: 600 }}>{title}</span>
        <label className="row small muted">Per tanggal <input className="input" type="date" style={{ width: 'auto', height: 32 }} value={asOf} onChange={(e) => setAsOf(e.target.value)} /></label>
        <span className="spacer" />
        <button className="btn" type="button" onClick={() => exportCsv(`umur-${asOf}.csv`, ['Kode', 'Nama', ...labels, 'Total'],
          rows.map((r) => [r.code as string, r.name as string, ...cols.map((c) => n(r[c])), n(r.total)]))}><Icon name="download" size={14} />Ekspor</button>
      </div>
      {q.error ? <div className="alert alert-err" style={{ margin: 12 }}>{errorText(q.error)}</div> : (
        <DataTable rows={rows} rowKey={(r) => String(r.partner_id)} empty={q.isLoading ? 'Memuat…' : 'Tidak ada saldo terbuka'}
          columns={[
            { key: 'code', label: 'Kode', mono: true },
            { key: 'name', label: 'Nama' },
            ...cols.map((c, i) => ({ key: c, label: labels[i], align: 'right' as const, render: (r: Row) => n(r[c]) ? money(r[c]) : '' })),
            { key: 'total', label: 'Total', align: 'right', render: (r) => <b>{fmtRp(n(r.total))}</b> },
            ...(withLimit ? [{ key: 'credit_limit', label: 'Limit kredit', align: 'right' as const, render: (r: Row) => r.credit_limit == null ? '–' : money(r.credit_limit) }] : []),
          ]}
          footer={rows.length ? <tr><td colSpan={2}>Total</td>{cols.map((c) => <td key={c} className="num mono">{fmtRp(total(c))}</td>)}<td className="num mono">{fmtRp(total('total'))}</td>{withLimit && <td />}</tr> : undefined} />
      )}
    </div>
  );
}

export function ArAgingPage() {
  return <AgingTable path="/fin/ar-aging" title="Umur piutang per customer" withLimit />;
}

export function ApAgingPage() {
  return <AgingTable path="/fin/ap-aging" title="Umur hutang per supplier (jadwal pembayaran)" />;
}

// ---------------------------------------------------------------- FIN-31 Rekonsiliasi bank

export function BankReconPage() {
  const [bank, setBank] = useState<number | null>(null);
  const [month, setMonth] = useState(todayIso().slice(0, 7));
  const r = monthRange(month);
  const fileRef = useRef<HTMLInputElement>(null);
  const toast = useToast();
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: ['recon', bank, month],
    queryFn: () => api.get<{ lines: Row[]; unmatchedBook: Row[]; glBalance: number; statementBalance: number }>('/fin/bank-statements', { bankAccountId: bank, from: r.from, to: r.to }),
    enabled: bank != null,
  });
  const reload = () => qc.invalidateQueries({ queryKey: ['recon'] });
  const call = async (fn: () => Promise<unknown>, msg: string) => {
    try {
      await fn();
      toast.ok(msg);
      reload();
    } catch (e) {
      toast.error(e);
    }
  };
  const upload = async (f?: File) => {
    if (!f || bank == null) return;
    try {
      const res = await api.upload<{ imported: number; autoMatched: number; errors: string[] }>(`/fin/bank-statements/import?bankAccountId=${bank}`, f);
      toast.ok(`${res.imported} mutasi diimpor, ${res.autoMatched} cocok otomatis${res.errors?.length ? ` · ${res.errors.length} baris ditolak` : ''}`);
      reload();
    } catch (e) {
      toast.error(e);
    }
  };
  const [pick, setPick] = useState<number | null>(null);
  const diff = q.data ? n(q.data.glBalance) - n(q.data.statementBalance) : 0;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="toolbar">
          <div style={{ width: 280 }}><LookupSelect lookup="bank-accounts" value={bank} onChange={setBank} filters={{ kind: 'BANK' }} placeholder="Pilih rekening bank" /></div>
          <label className="row small muted">Periode <input className="input" type="month" style={{ width: 'auto', height: 32 }} value={month} onChange={(e) => setMonth(e.target.value)} /></label>
          <span className="spacer" />
          <input ref={fileRef} type="file" accept=".csv,.txt" hidden onChange={(e) => { void upload(e.target.files?.[0]); e.target.value = ''; }} />
          <button className="btn" type="button" disabled={bank == null} title="CSV: tanggal;keterangan;referensi;masuk;keluar" onClick={() => fileRef.current?.click()}>Impor mutasi</button>
          <button className="btn btn-dark" type="button" disabled={bank == null} onClick={() => call(() => api.post(`/fin/bank-statements/auto-match?bankAccountId=${bank}`), 'Pencocokan otomatis selesai')}>Cocokkan otomatis</button>
        </div>
        {q.data && (
          <div className="kpis" style={{ padding: 12 }}>
            <div className="card kpi"><span className="small muted">Saldo buku (GL) s.d. {fmtDate(r.to)}</span><span className="v">{fmtRp(q.data.glBalance)}</span></div>
            <div className="card kpi"><span className="small muted">Saldo menurut mutasi bank</span><span className="v">{fmtRp(q.data.statementBalance)}</span></div>
            <div className="card kpi"><span className="small muted">Selisih</span><span className="v">{fmtRp(diff)}</span><span className="small muted">{q.data.unmatchedBook.length} transaksi buku belum cocok</span></div>
          </div>
        )}
      </div>
      {bank == null ? <div className="card"><div className="empty">Pilih rekening bank untuk mulai rekonsiliasi.</div></div> : (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'flex-start' }}>
          <div className="card" style={{ flex: '2 1 520px', minWidth: 0, overflow: 'hidden' }}>
            <div className="card-h">Mutasi bank</div>
            <DataTable rows={q.data?.lines ?? []} rowKey={(x) => String(x.id)} empty="Belum ada mutasi diimpor"
              onRowClick={(x) => !x.matched_doc_id && setPick(x.id as number)}
              columns={[
                { key: 'txn_date', label: 'Tanggal', mono: true, render: (x) => fmtDate(String(x.txn_date)) },
                { key: 'description', label: 'Keterangan' },
                { key: 'money_in', label: 'Masuk', align: 'right', render: (x) => n(x.money_in) ? money(x.money_in) : '' },
                { key: 'money_out', label: 'Keluar', align: 'right', render: (x) => n(x.money_out) ? money(x.money_out) : '' },
                { key: 'matched_doc_no', label: 'Cocok dengan', render: (x) => x.matched_doc_no
                  ? <span className="row"><span className="mono small">{String(x.matched_doc_no)}</span>
                    <button className="btn btn-sm" type="button" onClick={(e) => { e.stopPropagation(); void call(() => api.post(`/fin/bank-statements/lines/${x.id}/unmatch`), 'Pencocokan dilepas'); }}>Lepas</button></span>
                  : pick === x.id ? <StatusChip status="SUBMITTED" label="Pilih transaksi buku →" /> : <span className="small muted">Klik untuk cocokkan</span> },
              ]} />
          </div>
          <div className="card" style={{ flex: '1 1 320px', minWidth: 0, overflow: 'hidden' }}>
            <div className="card-h">Transaksi buku belum cocok</div>
            <DataTable rows={q.data?.unmatchedBook ?? []} rowKey={(x) => `${x.doc_type}-${x.doc_id}`} empty="Semua sudah cocok"
              onRowClick={(x) => pick != null && call(() => api.post(`/fin/bank-statements/lines/${pick}/match`, { docType: x.doc_type, docId: x.doc_id }), 'Dicocokkan').then(() => setPick(null))}
              columns={[
                { key: 'doc_no', label: 'Dokumen', mono: true },
                { key: 'doc_date', label: 'Tanggal', mono: true, render: (x) => fmtDate(String(x.doc_date)) },
                { key: 'amt', label: 'Nilai', align: 'right', render: (x) => money(n(x.money_in) || -n(x.money_out)) },
              ]} />
          </div>
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- FIN-32 Proyeksi arus kas

export function CashForecastPage() {
  const q = useQuery({ queryKey: ['forecast'], queryFn: () => api.get<{ opening: number; weeks: Row[]; note: string }>('/fin/cash-forecast', { weeks: 13 }) });
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar"><span>Saldo kas & bank saat ini <b className="mono">{fmtRp(q.data?.opening)}</b></span><span className="small muted">{q.data?.note}</span></div>
      <DataTable rows={q.data?.weeks ?? []} rowKey={(r) => String(r.week)} empty={q.isLoading ? 'Memuat…' : 'Tidak ada data'}
        columns={[
          { key: 'week', label: 'Minggu', mono: true },
          { key: 'from', label: 'Periode', render: (r) => `${fmtDate(String(r.from))} – ${fmtDate(String(r.to))}` },
          { key: 'inflow', label: 'Penerimaan piutang', align: 'right', render: (r) => money(r.inflow) },
          { key: 'outflowAp', label: 'Pembayaran hutang', align: 'right', render: (r) => money(r.outflowAp) },
          { key: 'outflowPayroll', label: 'Payroll', align: 'right', render: (r) => money(r.outflowPayroll) },
          { key: 'closing', label: 'Saldo akhir', align: 'right', render: (r) => <b style={{ color: n(r.closing) < 0 ? 'var(--danger)' : undefined }}>{fmtRp(n(r.closing))}</b> },
        ]} />
    </div>
  );
}

// ---------------------------------------------------------------- FIN-51 Kontrol anggaran

export function BudgetControlPage() {
  const [month, setMonth] = useState(todayIso().slice(0, 7));
  const r = monthRange(month);
  const q = useQuery({ queryKey: ['budget-control', month], queryFn: () => api.get<Row[]>('/fin/budget-control', { year: r.year, month: r.month }) });
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        <label className="row small muted">Kumulatif s.d. <input className="input" type="month" style={{ width: 'auto', height: 32 }} value={month} onChange={(e) => setMonth(e.target.value)} /></label>
        <span className="small muted">Sisa = anggaran − realisasi (jurnal terposting) − komitmen terbuka (PR/PO, M2).</span>
      </div>
      <DataTable rows={q.data ?? []} rowKey={(x) => `${x.cost_center_id}-${x.account_id}`} empty={q.isLoading ? 'Memuat…' : 'Belum ada anggaran berlaku atau realisasi beban'}
        columns={[
          { key: 'cc_code', label: 'Cost center', render: (x) => <span><span className="mono small">{String(x.cc_code)}</span> {String(x.cc_name)}</span> },
          { key: 'account_code', label: 'Akun', render: (x) => <span><span className="mono small">{String(x.account_code)}</span> {String(x.account_name)}</span> },
          { key: 'budget', label: 'Anggaran', align: 'right', render: (x) => money(x.budget) },
          { key: 'actual', label: 'Realisasi', align: 'right', render: (x) => money(x.actual) },
          { key: 'committed', label: 'Komitmen', align: 'right', render: (x) => money(x.committed) },
          { key: 'sisa', label: 'Sisa', align: 'right', render: (x) => { const s = n(x.budget) - n(x.actual) - n(x.committed); return <b style={{ color: s < 0 ? 'var(--danger)' : undefined }}>{fmtRp(s)}</b>; } },
          { key: 'pct', label: '%', align: 'right', render: (x) => n(x.budget) ? `${fmtNumber(n(x.actual) * 100 / n(x.budget))}%` : '–' },
        ]} />
    </div>
  );
}

// ---------------------------------------------------------------- FIN-71 Laporan keuangan

export function StatementsPage() {
  const [tab, setTab] = useState<'income' | 'balance' | 'cashflow'>('income');
  const [month, setMonth] = useState(todayIso().slice(0, 7));
  const [consolidated, setConsolidated] = useState(false);
  const r = monthRange(month);
  const yearStart = `${r.year}-01-01`;
  const q = useQuery({
    queryKey: ['statement', tab, month, consolidated],
    queryFn: () => tab === 'balance' ? api.get<Row>('/fin/statements/balance', { asOf: r.to, consolidated })
      : tab === 'income' ? api.get<Row>('/fin/statements/income', { from: yearStart, to: r.to, consolidated })
        : api.get<Row>('/fin/statements/cashflow', { from: yearStart, to: r.to }),
  });
  const rows = (q.data?.rows as Row[] | undefined) ?? [];
  const totals: [string, unknown][] = useMemo(() => {
    const d = q.data ?? {};
    if (tab === 'income') return [['Pendapatan', d.revenue], ['HPP', d.cogs], ['Laba kotor', d.grossProfit], ['Beban operasional', d.opex], ['Laba operasi', d.operatingProfit], ['Pendapatan/beban lain', d.other], ['Laba bersih', d.netProfit]];
    if (tab === 'balance') return [['Aset', d.assets], ['Liabilitas', d.liabilities], ['Ekuitas', d.equity], ['Laba berjalan', d.currentProfit]];
    return [...Object.entries((d.sections as Record<string, number>) ?? {}), ['Kenaikan (penurunan) kas', d.net]];
  }, [q.data, tab]);
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="tabs">
        {([['income', 'Laba rugi'], ['balance', 'Neraca'], ['cashflow', 'Arus kas']] as const).map(([k, l]) => (
          <button key={k} type="button" className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>{l}</button>
        ))}
      </div>
      <div className="toolbar">
        <label className="row small muted">{tab === 'balance' ? 'Per akhir' : 'Januari s.d.'} <input className="input" type="month" style={{ width: 'auto', height: 32 }} value={month} onChange={(e) => setMonth(e.target.value)} /></label>
        {tab !== 'cashflow' && <label className="row small"><input type="checkbox" checked={consolidated} onChange={(e) => setConsolidated(e.target.checked)} />Konsolidasi semua plant</label>}
        {tab === 'balance' && q.data && <StatusChip status={q.data.balanced ? 'APPROVED' : 'REJECTED'} label={q.data.balanced ? 'Seimbang' : 'Tidak seimbang'} />}
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, padding: 12 }}>
        <div style={{ flex: '2 1 480px', minWidth: 0 }}>
          <DataTable rows={rows} rowKey={(x) => `${x.section ?? ''}-${x.code}`} empty={q.isLoading ? 'Memuat…' : 'Belum ada jurnal terposting'}
            columns={[
              ...(tab === 'cashflow' ? [{ key: 'section', label: 'Aktivitas' }] : [{ key: 'group_name', label: 'Kelompok', render: (x: Row) => <span className="muted">{String(x.group_name ?? '')}</span> }]),
              { key: 'code', label: 'Akun', mono: true },
              { key: 'name', label: 'Nama' },
              { key: 'amount', label: 'Jumlah', align: 'right', render: (x) => money(x.amount) },
            ]} />
        </div>
        <dl className="card" style={{ flex: '1 1 260px', margin: 0, padding: 16, display: 'grid', gridTemplateColumns: '1fr auto', gap: '8px 16px', alignSelf: 'flex-start' }}>
          {totals.map(([l, v]) => <div key={l} style={{ display: 'contents' }}><dt className="muted">{l}</dt><dd className="mono" style={{ margin: 0, textAlign: 'right' }}>{fmtRp(n(v))}</dd></div>)}
        </dl>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- FIN-72 Laporan manajemen & BSC

export function ManagementPage() {
  const [month, setMonth] = useState(todayIso().slice(0, 7));
  const r = monthRange(month);
  const toast = useToast();
  const q = useQuery({ queryKey: ['mgmt', month], queryFn: () => api.get<{ departments: Row[]; products: Row[]; note: string }>('/fin/management', { year: r.year, month: r.month }) });
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="toolbar">
          <label className="row small muted">Kumulatif s.d. <input className="input" type="month" style={{ width: 'auto', height: 32 }} value={month} onChange={(e) => setMonth(e.target.value)} /></label>
          <span className="small muted">{q.data?.note}</span>
          <span className="spacer" />
          <button className="btn btn-dark" type="button" onClick={async () => {
            try { await api.post(`/fin/management/send-bsc?year=${r.year}&month=${r.month}`); toast.ok('Nilai keuangan masuk antrean kirim BSC (SYS-13)'); } catch (e) { toast.error(e); }
          }}>Kirim ke BSC</button>
        </div>
        <div className="card-h" style={{ borderTop: 0 }}>Realisasi anggaran per departemen</div>
        <DataTable rows={q.data?.departments ?? []} rowKey={(x) => String(x.department)} empty="Belum ada anggaran berlaku"
          columns={[
            { key: 'department', label: 'Departemen' },
            { key: 'budget', label: 'Anggaran', align: 'right', render: (x) => money(x.budget) },
            { key: 'actual', label: 'Realisasi', align: 'right', render: (x) => money(x.actual) },
            { key: 'pct', label: '%', align: 'right', render: (x) => `${fmtNumber(n(x.pct))}%` },
          ]} />
      </div>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">Pendapatan per produk (dari faktur penjualan)</div>
        <DataTable rows={q.data?.products ?? []} rowKey={(x) => String(x.code) + String(x.name)} empty="Belum ada faktur penjualan terposting"
          columns={[
            { key: 'code', label: 'Kode', mono: true },
            { key: 'name', label: 'Produk' },
            { key: 'qty', label: 'Qty', align: 'right', render: (x) => fmtNumber(n(x.qty)) },
            { key: 'revenue', label: 'Pendapatan', align: 'right', render: (x) => money(x.revenue) },
          ]} />
      </div>
    </div>
  );
}
