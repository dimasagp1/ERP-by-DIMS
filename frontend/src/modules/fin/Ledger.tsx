import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { DataTable, exportCsv } from '../../components/DataTable';
import { Icon } from '../../components/icons';
import { StatusChip } from '../../components/StatusChip';
import { errorText, Modal, useToast } from '../../components/ui';
import { fmtDate, fmtDateTime, fmtNumber, todayIso } from '../../lib/format';
import { menuPath } from '../../lib/meta';

interface TbRow { accountId: number; code: string; name: string; type: string; opening: number; debit: number; credit: number; closing: number }
interface TrialBalance { rows: TbRow[]; totalDebit: number; totalCredit: number }
interface LedgerLine { entryId: number; docNo: string; docDate: string; description: string; sourceDocNo: string | null; costCenter: string | null; debit: number; credit: number; balance: number }
interface AccountLedger { code: string; name: string; opening: number; lines: LedgerLine[]; closing: number }

function monthRange(month: string) {
  const [y, m] = month.split('-').map(Number);
  return { from: `${month}-01`, to: `${month}-${String(new Date(y, m, 0).getDate()).padStart(2, '0')}` };
}

/** FIN-04 Buku besar & neraca saldo dengan drill-down ke dokumen sumber. */
export function LedgerPage() {
  const [month, setMonth] = useState(todayIso().slice(0, 7));
  const range = useMemo(() => monthRange(month), [month]);
  const [account, setAccount] = useState<TbRow | null>(null);
  const tb = useQuery({ queryKey: ['tb', range], queryFn: () => api.get<TrialBalance>('/fin/ledger/trial-balance', range) });
  const rows = tb.data?.rows ?? [];
  const balanced = tb.data && Math.abs(tb.data.totalDebit - tb.data.totalCredit) < 0.005;

  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        <label className="row small muted">Periode <input className="input" type="month" style={{ width: 'auto', height: 32 }} value={month} onChange={(e) => setMonth(e.target.value)} /></label>
        <span className="small muted">Hanya jurnal terposting · plant aktif</span>
        <span className="spacer" />
        {tb.data && <StatusChip status={balanced ? 'APPROVED' : 'REJECTED'} label={balanced ? 'Seimbang' : 'Tidak seimbang'} />}
        <button className="btn" type="button" onClick={() => exportCsv(`neraca-saldo-${month}.csv`, ['Kode', 'Akun', 'Saldo awal', 'Debit', 'Kredit', 'Saldo akhir'],
          rows.map((r) => [r.code, r.name, r.opening, r.debit, r.credit, r.closing]))}><Icon name="download" size={14} />Ekspor</button>
      </div>
      {tb.error ? <div className="alert alert-err" style={{ margin: 12 }}>{errorText(tb.error)}</div> : (
        <DataTable rows={rows} rowKey={(r) => r.accountId} onRowClick={setAccount} empty={tb.isLoading ? 'Memuat…' : 'Belum ada jurnal terposting'}
          columns={[
            { key: 'code', label: 'Kode', mono: true },
            { key: 'name', label: 'Akun' },
            { key: 'opening', label: 'Saldo awal', align: 'right', render: (r) => <span className="mono">{fmtNumber(r.opening)}</span> },
            { key: 'debit', label: 'Debit', align: 'right', render: (r) => <span className="mono">{fmtNumber(r.debit)}</span> },
            { key: 'credit', label: 'Kredit', align: 'right', render: (r) => <span className="mono">{fmtNumber(r.credit)}</span> },
            { key: 'closing', label: 'Saldo akhir (D−K)', align: 'right', render: (r) => <span className="mono">{fmtNumber(r.closing)}</span> },
          ]}
          footer={tb.data && rows.length > 0 ? <tr><td colSpan={3}>Total mutasi</td><td className="num mono">{fmtNumber(tb.data.totalDebit)}</td><td className="num mono">{fmtNumber(tb.data.totalCredit)}</td><td /></tr> : undefined} />
      )}
      {account && <AccountLedgerModal account={account} range={range} onClose={() => setAccount(null)} />}
    </div>
  );
}

function AccountLedgerModal({ account, range, onClose }: { account: TbRow; range: { from: string; to: string }; onClose: () => void }) {
  const nav = useNavigate();
  const q = useQuery({ queryKey: ['ledger', account.accountId, range], queryFn: () => api.get<AccountLedger>(`/fin/ledger/accounts/${account.accountId}`, range) });
  return (
    <Modal title={<><span className="mono">{account.code}</span> {account.name}</>} onClose={onClose} width={980}>
      <div className="row small muted">Saldo awal <b className="mono">{fmtNumber(q.data?.opening)}</b> · saldo akhir <b className="mono">{fmtNumber(q.data?.closing)}</b> · klik baris untuk membuka jurnal</div>
      <DataTable rows={q.data?.lines ?? []} rowKey={(r) => `${r.entryId}-${r.debit}-${r.credit}-${r.balance}`} onRowClick={(r) => { onClose(); nav(menuPath('FIN-03', r.entryId)); }}
        empty={q.isLoading ? 'Memuat…' : 'Tidak ada mutasi'}
        columns={[
          { key: 'docDate', label: 'Tanggal', mono: true, render: (r) => fmtDate(r.docDate) },
          { key: 'docNo', label: 'Jurnal', mono: true },
          { key: 'description', label: 'Uraian', render: (r) => <>{r.description}{r.sourceDocNo && <span className="mono small muted"> · {r.sourceDocNo}</span>}</> },
          { key: 'costCenter', label: 'CC', mono: true },
          { key: 'debit', label: 'Debit', align: 'right', render: (r) => <span className="mono">{r.debit ? fmtNumber(r.debit) : ''}</span> },
          { key: 'credit', label: 'Kredit', align: 'right', render: (r) => <span className="mono">{r.credit ? fmtNumber(r.credit) : ''}</span> },
          { key: 'balance', label: 'Saldo', align: 'right', render: (r) => <span className="mono">{fmtNumber(r.balance)}</span> },
        ]} />
    </Modal>
  );
}

interface PeriodData { modules: string[]; locks: { moduleCode: string; month: number; locked: boolean; lockedAt: string }[]; canLock: boolean }
const MODULE_LABEL: Record<string, string> = { SCM: 'Gudang', PRE: 'Produksi', COST: 'Costing', AP: 'Hutang', AR: 'Piutang', TAX: 'Pajak', FIN: 'GL' };
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];

/** FIN-70 kunci periode per modul. Urutan closing: Gudang & Produksi → Costing → Hutang/Piutang → Pajak → GL. */
export function PeriodPage() {
  const [year, setYear] = useState(Number(todayIso().slice(0, 4)));
  const qc = useQueryClient();
  const toast = useToast();
  const q = useQuery({ queryKey: ['periods', year], queryFn: () => api.get<PeriodData>('/fin/periods', { year }) });
  const toggle = useMutation({
    mutationFn: (v: { module: string; month: number; locked: boolean }) => api.post('/fin/periods/lock', { ...v, year }),
    onSuccess: (_, v) => { qc.invalidateQueries({ queryKey: ['periods', year] }); toast.ok(`${MODULE_LABEL[v.module]} ${MONTHS[v.month - 1]} ${year} ${v.locked ? 'dikunci' : 'dibuka'}`); },
    onError: (e) => toast.error(e),
  });
  const lockOf = (m: string, month: number) => q.data?.locks.find((l) => l.moduleCode === m && l.month === month);
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        <button className="btn btn-sm" type="button" onClick={() => setYear(year - 1)}><Icon name="arrowLeft" size={13} /></button>
        <span className="mono" style={{ fontWeight: 600 }}>{year}</span>
        <button className="btn btn-sm" type="button" onClick={() => setYear(year + 1)}>›</button>
        <span className="small muted">Setelah dikunci, semua modul menolak transaksi bertanggal periode itu. Membuka kembali hanya oleh Manager FIN.</span>
      </div>
      <div className="table-wrap">
        <table className="t">
          <thead><tr><th>Modul (urutan closing)</th>{MONTHS.map((m) => <th key={m} style={{ textAlign: 'center' }}>{m}</th>)}</tr></thead>
          <tbody>
            {(q.data?.modules ?? []).map((m) => (
              <tr key={m}>
                <td>{MODULE_LABEL[m] ?? m} <span className="mono small muted">{m}</span></td>
                {MONTHS.map((_, i) => {
                  const l = lockOf(m, i + 1);
                  return (
                    <td key={i} style={{ textAlign: 'center', padding: 4 }}>
                      <button type="button" className="btn btn-sm" disabled={!q.data?.canLock || toggle.isPending}
                        title={l?.locked ? `Dikunci ${fmtDateTime(l.lockedAt)}` : 'Terbuka'}
                        style={l?.locked ? { background: 'var(--chip-posted-bg)', color: 'var(--chip-posted-fg)', borderColor: 'transparent' } : undefined}
                        onClick={() => toggle.mutate({ module: m, month: i + 1, locked: !l?.locked })}>
                        {l?.locked ? <Icon name="lock" size={13} /> : '–'}
                      </button>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {q.data && !q.data.canLock && <div className="small muted" style={{ padding: 12 }}>Anda hanya bisa melihat status periode.</div>}
    </div>
  );
}
