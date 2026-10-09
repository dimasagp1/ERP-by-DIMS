import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { DataTable } from '../../components/DataTable';
import { errorText, useToast } from '../../components/ui';
import { fmtDate, fmtRp } from '../../lib/format';

type Row = Record<string, unknown>;
const n = (v: unknown) => Number(v ?? 0);
const money = (v: unknown) => <span className="mono">{fmtRp(n(v))}</span>;
const num = (v: unknown) => <span className="mono">{n(v)}</span>;

export function PpnPage() {
  const [period, setPeriod] = useState(new Date().toISOString().slice(0, 7).replace('-', ''));
  const q = useQuery({ queryKey: ['tax-ppn', period], queryFn: () => api.get<Record<string, unknown>>('/fin/tax/ppn', { period }) });
  
  const d = q.data;
  const input = (d?.input as Row[]) ?? [];
  const output = (d?.output as Row[]) ?? [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card">
        <div className="toolbar">
          <span style={{ fontWeight: 600 }}>PPN & e-Faktur (Masa Pajak)</span>
          <label className="row small muted">Periode <input className="input" type="text" style={{ width: 80 }} value={period} onChange={(e) => setPeriod(e.target.value)} placeholder="YYYYMM" /></label>
        </div>
        {q.error && <div className="alert alert-err" style={{ margin: 12 }}>{errorText(q.error)}</div>}
        <div style={{ padding: 16, display: 'flex', gap: 32 }}>
          <div>
            <div className="small muted">Pajak Masukan (Pembelian + Impor)</div>
            <div style={{ fontSize: 24, fontWeight: 600 }}>{fmtRp(n(d?.totalInput))}</div>
          </div>
          <div>
            <div className="small muted">Pajak Keluaran (Penjualan - Retur)</div>
            <div style={{ fontSize: 24, fontWeight: 600 }}>{fmtRp(n(d?.totalOutput))}</div>
          </div>
          <div>
            <div className="small muted">PPN Kurang/(Lebih) Bayar</div>
            <div style={{ fontSize: 24, fontWeight: 600, color: n(d?.payable) > 0 ? 'red' : 'green' }}>{fmtRp(n(d?.payable))}</div>
          </div>
        </div>
      </div>

      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">Pajak Keluaran (Faktur Penjualan)</div>
        <DataTable rows={output} rowKey={(r) => String(r.id)} empty="Tidak ada PPN keluaran"
          columns={[
            { key: 'doc_date', label: 'Tanggal', render: (r) => fmtDate(r.doc_date as string) },
            { key: 'doc_no', label: 'Invoice', mono: true },
            { key: 'partner', label: 'Customer' },
            { key: 'npwp', label: 'NPWP', mono: true },
            { key: 'tax_invoice_no', label: 'No Faktur Pajak', mono: true, render: (r) => r.tax_invoice_no ? String(r.tax_invoice_no) : <span style={{ color: 'red' }}>Belum ada</span> },
            { key: 'dpp', label: 'DPP', align: 'right', render: (r) => money(r.dpp) },
            { key: 'ppn_amount', label: 'PPN', align: 'right', render: (r) => money(r.ppn_amount) },
          ]} />
      </div>

      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">Pajak Masukan (Faktur Pembelian)</div>
        <DataTable rows={input} rowKey={(r) => String(r.id)} empty="Tidak ada PPN masukan"
          columns={[
            { key: 'doc_date', label: 'Tanggal', render: (r) => fmtDate(r.doc_date as string) },
            { key: 'doc_no', label: 'AP Invoice', mono: true },
            { key: 'partner', label: 'Supplier' },
            { key: 'npwp', label: 'NPWP', mono: true },
            { key: 'tax_invoice_no', label: 'No Faktur Pajak', mono: true, render: (r) => r.tax_invoice_no ? String(r.tax_invoice_no) : <span style={{ color: 'red' }}>Belum ada</span> },
            { key: 'dpp', label: 'DPP', align: 'right', render: (r) => money(r.dpp) },
            { key: 'ppn_amount', label: 'PPN', align: 'right', render: (r) => money(r.ppn_amount) },
          ]} />
      </div>
    </div>
  );
}

export function PphPage() {
  const [period, setPeriod] = useState(new Date().toISOString().slice(0, 7).replace('-', ''));
  const q = useQuery({ queryKey: ['tax-pph', period], queryFn: () => api.get<Record<string, unknown>>('/fin/tax/pph', { period }) });
  
  const d = q.data;
  const withheld = (d?.withheld as Row[]) ?? [];
  const pph21 = (d?.pph21 as Row[]) ?? [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card">
        <div className="toolbar">
          <span style={{ fontWeight: 600 }}>PPh 21/23/4(2)/22</span>
          <label className="row small muted">Periode <input className="input" type="text" style={{ width: 80 }} value={period} onChange={(e) => setPeriod(e.target.value)} placeholder="YYYYMM" /></label>
        </div>
      </div>
      
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">PPh 23 / 4(2) Dipotong dari Supplier</div>
        <DataTable rows={withheld} rowKey={(r) => String(r.id)} empty="Tidak ada PPh dipotong"
          columns={[
            { key: 'doc_date', label: 'Tanggal', render: (r) => fmtDate(r.doc_date as string) },
            { key: 'doc_no', label: 'AP Invoice', mono: true },
            { key: 'partner', label: 'Supplier' },
            { key: 'tax_type', label: 'Jenis Pajak' },
            { key: 'tax_code', label: 'Kode Pajak' },
            { key: 'dpp', label: 'DPP', align: 'right', render: (r) => money(r.dpp) },
            { key: 'rate', label: 'Tarif', align: 'right', render: (r) => num(r.rate) + '%' },
            { key: 'tax', label: 'PPh', align: 'right', render: (r) => money(r.tax) },
            { key: 'slip_no', label: 'No Bupot', mono: true },
          ]} />
      </div>

      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">PPh 21 (Gaji Karyawan)</div>
        <DataTable rows={pph21} rowKey={(r) => String(r.nik)} empty="Tidak ada data PPh 21"
          columns={[
            { key: 'nik', label: 'NIK', mono: true },
            { key: 'name', label: 'Nama' },
            { key: 'ptkp_status', label: 'PTKP' },
            { key: 'taxable_gross', label: 'Bruto Kena Pajak', align: 'right', render: (r) => money(r.taxable_gross) },
            { key: 'pph21', label: 'PPh 21', align: 'right', render: (r) => money(r.pph21) },
          ]} />
      </div>
    </div>
  );
}

export function SptPage() {
  const [year, setYear] = useState(new Date().getFullYear());
  const toast = useToast();
  const q = useQuery({ queryKey: ['tax-periods', year], queryFn: () => api.get<Row[]>('/fin/tax/periods', { year }) });
  const rows = q.data ?? [];

  const genBupot = async (period: string) => {
    try {
      const res = await api.post<{ created: number }>('/fin/tax/withholding-slips/generate', { period });
      toast.ok(`${res.created} bukti potong berhasil diterbitkan`);
    } catch (e) {
      toast.error(errorText(e));
    }
  };

  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        <span style={{ fontWeight: 600 }}>Bukti Potong & Pelaporan SPT Masa</span>
        <label className="row small muted">Tahun <input className="input" type="number" style={{ width: 80 }} value={year} onChange={(e) => setYear(Number(e.target.value))} /></label>
      </div>
      {q.error && <div className="alert alert-err" style={{ margin: 12 }}>{errorText(q.error)}</div>}
      <DataTable rows={rows} rowKey={(r) => `${r.period}-${r.taxType}`} empty="Memuat..."
        columns={[
          { key: 'period', label: 'Masa', mono: true },
          { key: 'taxType', label: 'Jenis Pajak' },
          { key: 'computed', label: 'Estimasi Hutang', align: 'right', render: (r) => money(r.computed) },
          { key: 'amount', label: 'Dilaporkan', align: 'right', render: (r) => money(r.amount) },
          { key: 'ntpn', label: 'NTPN', mono: true },
          { key: 'bpeNo', label: 'No BPE (Lapor)', mono: true },
          { key: 'status', label: 'Status' },
          { key: 'action', label: 'Aksi', render: (r) => (r.taxType === 'PPH23' || r.taxType === 'PPH4_2') && r.status === 'OPEN' ? 
            <button className="btn btn-sm" onClick={() => genBupot(r.period as string)}>Terbitkan Bupot</button> : null },
        ]} />
    </div>
  );
}

export function FiscalPage() {
  const [year, setYear] = useState(new Date().getFullYear());
  const q = useQuery({ queryKey: ['tax-fiscal', year], queryFn: () => api.get<Record<string, unknown>>('/fin/tax/fiscal', { year }) });
  
  const d = q.data;
  const corrections = (d?.corrections as Row[]) ?? [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card">
        <div className="toolbar">
          <span style={{ fontWeight: 600 }}>Rekonsiliasi Fiskal & Estimasi PPh Badan</span>
          <label className="row small muted">Tahun <input className="input" type="number" style={{ width: 80 }} value={year} onChange={(e) => setYear(Number(e.target.value))} /></label>
        </div>
        {q.error && <div className="alert alert-err" style={{ margin: 12 }}>{errorText(q.error)}</div>}
        <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 16 }}>
            <span>Laba Komersial (Sebelum Pajak)</span>
            <span style={{ fontWeight: 600 }}>{fmtRp(n(d?.commercialProfit))}</span>
          </div>
          <hr />
          <div><b>Koreksi Fiskal:</b></div>
          <table className="table" style={{ width: '100%' }}>
            <tbody>
              {corrections.map((c, i) => (
                <tr key={i}>
                  <td>{c.description as string} {c.auto ? '(Otomatis)' : ''}</td>
                  <td>{c.category as string}</td>
                  <td align="right" style={{ color: c.kind === 'POSITIVE' ? 'red' : 'green' }}>
                    {c.kind === 'POSITIVE' ? '+' : '-'} {fmtRp(n(c.amount))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <hr />
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 16 }}>
            <span>Laba Fiskal (Dasar Pengenaan Pajak)</span>
            <span style={{ fontWeight: 600 }}>{fmtRp(n(d?.fiscalProfit))}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 16 }}>
            <span>PPh Badan Terutang ({(n(d?.rate) * 100).toFixed(0)}%)</span>
            <span style={{ fontWeight: 600, color: 'red' }}>{fmtRp(n(d?.tax))}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 16 }}>
            <span>Kredit Pajak (Uang Muka PPh 22, dll)</span>
            <span style={{ fontWeight: 600, color: 'green' }}>{fmtRp(n(d?.prepaidPph22))}</span>
          </div>
          <hr />
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 18, fontWeight: 700 }}>
            <span>Estimasi PPh Badan Kurang Bayar</span>
            <span>{fmtRp(n(d?.payable))}</span>
          </div>
          <div className="small muted" style={{ marginTop: 8 }}>Catatan: {d?.note as string}</div>
        </div>
      </div>
    </div>
  );
}
