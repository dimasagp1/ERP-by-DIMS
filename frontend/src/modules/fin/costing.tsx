import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { DataTable, exportCsv } from '../../components/DataTable';
import { Icon } from '../../components/icons';
import { errorText, useToast } from '../../components/ui';
import { fmtDate, fmtRp } from '../../lib/format';

type Row = Record<string, unknown>;
const n = (v: unknown) => Number(v ?? 0);
const money = (v: unknown) => <span className="mono">{fmtRp(n(v))}</span>;
const num = (v: unknown) => <span className="mono">{n(v)}</span>;

export function StandardCostPage() {
  const [period, setPeriod] = useState(new Date().toISOString().slice(0, 7).replace('-', ''));
  const toast = useToast();
  const q = useQuery({ queryKey: ['std-costs', period], queryFn: () => api.get<Row[]>('/fin/costing/std-costs', { period }) });
  const rows = q.data ?? [];

  const calc = async () => {
    if (!window.confirm(`Hitung ulang biaya standar produksi untuk periode ${period}? (Proses ini bisa memakan waktu)`)) return;
    try {
      await api.post('/fin/costing/std-costs/calculate', { period });
      toast.ok('Biaya standar berhasil dihitung ulang');
      q.refetch();
    } catch (e) {
      toast.error(errorText(e));
    }
  };

  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        <span style={{ fontWeight: 600 }}>Standard Cost (Biaya Standar Produksi)</span>
        <label className="row small muted">Periode <input className="input" type="text" style={{ width: 80 }} value={period} onChange={(e) => setPeriod(e.target.value)} placeholder="YYYYMM" /></label>
        <span className="spacer" />
        <button className="btn btn-primary" type="button" onClick={calc}><Icon name="refresh-cw" size={14} />Hitung Ulang</button>
        <button className="btn" type="button" onClick={() => exportCsv(`std-cost-${period}.csv`,
          ['Item', 'Base Qty', 'Material', 'Labor', 'Overhead', 'Total', 'Unit Cost', 'Status', 'Tanggal'],
          rows.map((r) => [String(r.itemCode), n(r.baseQty), n(r.material), n(r.labor), n(r.overhead), n(r.total), n(r.unitCost), String(r.status), String(r.calculatedAt)]))}><Icon name="download" size={14} />Ekspor</button>
      </div>
      {q.error && <div className="alert alert-err" style={{ margin: 12 }}>{errorText(q.error)}</div>}
      <DataTable rows={rows} rowKey={(r) => String(r.id)} empty={q.isLoading ? 'Memuat...' : 'Tidak ada data'}
        columns={[
          { key: 'itemCode', label: 'Kode Barang', mono: true },
          { key: 'itemName', label: 'Nama Barang' },
          { key: 'baseQty', label: 'Base Qty', align: 'right', render: (r) => num(r.baseQty) },
          { key: 'material', label: 'Material', align: 'right', render: (r) => money(r.material) },
          { key: 'labor', label: 'Labor', align: 'right', render: (r) => money(r.labor) },
          { key: 'overhead', label: 'Overhead', align: 'right', render: (r) => money(r.overhead) },
          { key: 'total', label: 'Total', align: 'right', render: (r) => money(r.total) },
          { key: 'unitCost', label: 'Unit Cost', align: 'right', render: (r) => <b>{fmtRp(n(r.unitCost))}</b> },
          { key: 'status', label: 'Status' },
          { key: 'calculatedAt', label: 'Waktu Hitung', render: (r) => fmtDate(r.calculatedAt as string) },
        ]} />
    </div>
  );
}

export function BatchCostPage() {
  const [period, setPeriod] = useState(new Date().toISOString().slice(0, 7).replace('-', ''));
  const toast = useToast();
  const q = useQuery({ queryKey: ['batch-costs', period], queryFn: () => api.get<Row[]>('/fin/costing/batch-costs', { period }) });
  const rows = q.data ?? [];

  const closeBatch = async (woId: number) => {
    if (!window.confirm('Tutup batch ini dan bukukan selisih (varians) produksi ke jurnal keuangan?')) return;
    try {
      await api.post(`/fin/costing/batch-costs/${woId}/close`);
      toast.ok('Batch berhasil ditutup');
      q.refetch();
    } catch (e) {
      toast.error(errorText(e));
    }
  };

  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        <span style={{ fontWeight: 600 }}>Biaya Aktual per Batch Produksi</span>
        <label className="row small muted">Periode WO <input className="input" type="text" style={{ width: 80 }} value={period} onChange={(e) => setPeriod(e.target.value)} placeholder="YYYYMM" /></label>
        <span className="spacer" />
        <button className="btn" type="button" onClick={() => exportCsv(`batch-cost-${period}.csv`,
          ['WO', 'Batch', 'Item', 'Qty Good', 'Material', 'Labor', 'Overhead', 'Total Aktual', 'Std Unit Cost', 'Nilai FG', 'Varians', 'Status'],
          rows.map((r) => [String(r.woNo), String(r.batchNo), String(r.itemCode), n(r.qtyGood), n(r.material), n(r.labor), n(r.overhead), n(r.total), n(r.stdUnitCost), n(r.fgValue), n(r.variance), r.closed ? 'TUTUP' : 'OPEN']))}><Icon name="download" size={14} />Ekspor</button>
      </div>
      {q.error && <div className="alert alert-err" style={{ margin: 12 }}>{errorText(q.error)}</div>}
      <DataTable rows={rows} rowKey={(r) => String(r.woId)} empty={q.isLoading ? 'Memuat...' : 'Tidak ada data WO di periode ini'}
        columns={[
          { key: 'woNo', label: 'WO', mono: true },
          { key: 'batchNo', label: 'Batch', mono: true },
          { key: 'itemCode', label: 'Barang' },
          { key: 'qtyGood', label: 'Qty FG', align: 'right', render: (r) => num(r.qtyGood) },
          { key: 'total', label: 'Aktual Total', align: 'right', render: (r) => money(r.total) },
          { key: 'unitCost', label: 'Aktual/Unit', align: 'right', render: (r) => money(r.unitCost) },
          { key: 'stdUnitCost', label: 'Standar/Unit', align: 'right', render: (r) => money(r.stdUnitCost) },
          { key: 'fgValue', label: 'Nilai FG', align: 'right', render: (r) => money(r.fgValue) },
          { key: 'variance', label: 'Varians', align: 'right', render: (r) => <span style={{ color: n(r.variance) > 0 ? 'red' : 'green' }}>{fmtRp(n(r.variance))}</span> },
          { key: 'action', label: 'Aksi', render: (r) => !r.closed ? <button className="btn btn-sm btn-primary" onClick={() => closeBatch(n(r.woId))}>Tutup Batch</button> : <span className="badge badge-gray">Ditutup ({String(r.journalNo)})</span> },
        ]} />
    </div>
  );
}

export function ValuationPage() {
  const [asOf, setAsOf] = useState(new Date().toISOString().slice(0, 10));
  const q1 = useQuery({ queryKey: ['valuation', asOf], queryFn: () => api.get<Row[]>('/fin/costing/valuation', { asOf }) });
  const rows = q1.data ?? [];
  const q2 = useQuery({ queryKey: ['cogs', asOf.slice(0, 7).replace('-', '')], queryFn: () => api.get<Row[]>('/fin/costing/cogs', { period: asOf.slice(0, 7).replace('-', '') }) });
  const cogsRows = q2.data ?? [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="toolbar">
          <span style={{ fontWeight: 600 }}>Valuasi Persediaan (Inventory Valuation)</span>
          <label className="row small muted">Per Tanggal <input className="input" type="date" value={asOf} onChange={(e) => setAsOf(e.target.value)} /></label>
        </div>
        {q1.error && <div className="alert alert-err" style={{ margin: 12 }}>{errorText(q1.error)}</div>}
        <DataTable rows={rows} rowKey={(r) => String(r.itemId)} empty={q1.isLoading ? 'Memuat...' : 'Tidak ada data persediaan'}
          columns={[
            { key: 'itemCode', label: 'Kode', mono: true },
            { key: 'itemName', label: 'Nama Barang' },
            { key: 'category', label: 'Kategori' },
            { key: 'qty', label: 'Total Qty', align: 'right', render: (r) => num(r.qty) },
            { key: 'unitCost', label: 'Unit Cost Avg', align: 'right', render: (r) => money(r.unitCost) },
            { key: 'totalValue', label: 'Total Valuasi', align: 'right', render: (r) => <b>{fmtRp(n(r.totalValue))}</b> },
          ]}
          footer={rows.length ? <tr><td colSpan={5}>Total Valuasi Persediaan</td><td className="num mono" style={{ fontWeight: 600 }}>{fmtRp(rows.reduce((s, r) => s + n(r.totalValue), 0))}</td></tr> : undefined} />
      </div>

      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="toolbar">
          <span style={{ fontWeight: 600 }}>Rekap HPP (Cost of Goods Sold)</span>
          <label className="row small muted">Bulan {asOf.slice(0, 7)}</label>
        </div>
        {q2.error && <div className="alert alert-err" style={{ margin: 12 }}>{errorText(q2.error)}</div>}
        <DataTable rows={cogsRows} rowKey={(r) => String(r.itemId)} empty={q2.isLoading ? 'Memuat...' : 'Tidak ada pengeluaran HPP bulan ini'}
          columns={[
            { key: 'itemCode', label: 'Kode', mono: true },
            { key: 'itemName', label: 'Nama Barang' },
            { key: 'qtySold', label: 'Qty Keluar', align: 'right', render: (r) => num(r.qtySold) },
            { key: 'cogsAmount', label: 'Nilai HPP', align: 'right', render: (r) => money(r.cogsAmount) },
          ]}
          footer={cogsRows.length ? <tr><td colSpan={3}>Total HPP Bulan Ini</td><td className="num mono" style={{ fontWeight: 600 }}>{fmtRp(cogsRows.reduce((s, r) => s + n(r.cogsAmount), 0))}</td></tr> : undefined} />
      </div>
    </div>
  );
}
