import { api } from '../../api/client';
import { StatusChip } from '../../components/StatusChip';
import type { DocConfig, ExtraProps } from '../docs/types';

const STATUS_COL = { key: 'status', label: 'Status', type: 'status' as const, sortable: false };
const DOCNO_COL = { key: 'docNo', label: 'No. Dokumen', mono: true };

function QualificationExtra({ doc }: ExtraProps) {
  const ops = (doc.operators as { employeeName?: string; processCode?: string; qualified?: boolean }[] | undefined) ?? [];
  if (doc.id == null || ops.length === 0) return null;
  return (
    <div className="card" style={{ padding: '12px 16px', display: 'flex', flexDirection: 'column', gap: 6 }}>
      <div className="lbl">Kualifikasi operator (HC-12) per tanggal mulai</div>
      {ops.map((o, i) => (
        <div key={i} className="row" style={{ justifyContent: 'space-between', fontSize: 13 }}>
          <span>{o.employeeName} <span className="mono small muted">{o.processCode}</span></span>
          <StatusChip status={o.qualified ? 'APPROVED' : 'REJECTED'} label={o.qualified ? 'Berkualifikasi' : 'Belum berkualifikasi'} />
        </div>
      ))}
      <div className="small muted">Prasyarat lain PRE aturan 1 (BOM Approved, line clearance, bahan Released) aktif bersama PPIC/Gudang (M2) dan eBMR (M3).</div>
    </div>
  );
}

export const workOrderDoc: DocConfig = {
  docType: 'WO', title: 'Work Order Produksi', endpoint: '/pre/work-orders',
  subtitle: (d) => (d.productName ? `${d.productName}${d.batchNo ? ` · batch ${d.batchNo}` : ''}` : undefined),
  listColumns: [DOCNO_COL, { key: 'batchNo', label: 'Batch', mono: true }, { key: 'plannedStart', label: 'Mulai', type: 'date' },
    { key: 'qtyPlan', label: 'Rencana', type: 'number' }, { key: 'qtyGood', label: 'Hasil', type: 'number' },
    { key: 'yieldPct', label: 'Yield %', type: 'number' }, STATUS_COL],
  header: [
    { key: 'productItemId', label: 'Produk', type: 'lookup', lookup: 'items', lookupFilters: { type: 'FG,WIP' }, required: true },
    { key: 'lineId', label: 'Lini', type: 'lookup', lookup: 'lines', required: true },
    { key: 'qtyPlan', label: 'Jumlah rencana', type: 'number', required: true },
    { key: 'shiftId', label: 'Shift', type: 'lookup', lookup: 'shifts' },
    { key: 'plannedStart', label: 'Mulai rencana', type: 'date', required: true },
    { key: 'plannedEnd', label: 'Selesai rencana', type: 'date' },
    { key: 'batchNo', label: 'Nomor batch (terbit saat rilis)', readOnly: true },
    { key: 'expDate', label: 'Kedaluwarsa', type: 'date', readOnly: true },
    { key: 'notes', label: 'Catatan', type: 'textarea' },
  ],
  lines: [{
    key: 'operators', title: 'Operator', addLabel: 'Tambah operator', newRow: () => ({ role: 'OPERATOR' }),
    fields: [
      { key: 'employeeId', label: 'Karyawan', type: 'lookup', lookup: 'employees' },
      { key: 'role', label: 'Peran', type: 'select', options: [{ value: 'OPERATOR', label: 'Operator' }, { value: 'CHECKER', label: 'Pemeriksa' }, { value: 'LEADER', label: 'Leader' }] },
      { key: 'processCode', label: 'Proses (kosong = proses lini)' },
    ],
  }],
  summary: [
    { key: 'qtyGood', label: 'Hasil baik', type: 'number' },
    { key: 'qtyReject', label: 'Reject', type: 'number' },
    { key: 'yieldPct', label: 'Yield', type: 'pct' },
  ],
  actions: [{
    label: 'Mulai produksi', primary: true,
    show: (d, m) => m.status === 'APPROVED' && !d.started,
    run: (d) => api.post(`/pre/work-orders/${d.id}/start`),
  }],
  extra: QualificationExtra,
};

export const outputDoc: DocConfig = {
  docType: 'HP', title: 'Hasil Produksi & Serah Terima', endpoint: '/pre/outputs',
  subtitle: (d) => (d.woDocNo ? `${d.woDocNo} · ${d.productName} · batch ${d.batchNo}` : undefined),
  listColumns: [DOCNO_COL, { key: 'docDate', label: 'Tanggal', type: 'date' }, { key: 'woDocNo', label: 'WO', mono: true, sortable: false },
    { key: 'batchNo', label: 'Batch', mono: true, sortable: false }, { key: 'qtyGood', label: 'Baik', type: 'number' },
    { key: 'qtyReject', label: 'Reject', type: 'number' }, { key: 'yieldPct', label: 'Yield %', type: 'number' }, STATUS_COL],
  header: [
    { key: 'woId', label: 'Work order (sudah dimulai)', type: 'lookup', lookup: 'work-orders', lookupFilters: { status: 'APPROVED' }, required: true },
    { key: 'docDate', label: 'Tanggal', type: 'date' },
    { key: 'qtyGood', label: 'Jumlah baik', type: 'number', required: true },
    { key: 'qtyReject', label: 'Jumlah reject', type: 'number' },
    { key: 'rejectReasonId', label: 'Alasan reject', type: 'lookup', lookup: 'reject-reasons' },
    { key: 'toLocationId', label: 'Ke lokasi karantina', type: 'lookup', lookup: 'locations', lookupFilters: { is_quarantine: 'true' }, help: 'Kosong = karantina gudang barang jadi' },
    { key: 'yieldPct', label: 'Yield %', type: 'number', readOnly: true },
    { key: 'lotStatus', label: 'Status lot', readOnly: true, show: (d) => Boolean(d.lotStatus) },
    { key: 'yieldExplanation', label: 'Penjelasan yield (wajib bila di bawah minimum)', type: 'textarea' },
  ],
  defaults: { qtyReject: 0 },
};

// ---------------------------------------------------------------- PRE-04 Permintaan bahan, PRE-10 Retur sisa bahan

export const materialRequestDoc: DocConfig = {
  docType: 'MR', title: 'Permintaan Bahan', endpoint: '/pre/material-requests',
  subtitle: (d) => (d.woNo ? `${d.woNo} · ${d.productName} · batch ${d.batchNo}` : undefined),
  listColumns: [DOCNO_COL, { key: 'docDate', label: 'Tanggal', type: 'date' }, { key: 'woNo', label: 'WO', mono: true, sortable: false },
    { key: 'batchNo', label: 'Batch', mono: true, sortable: false }, { key: 'neededAt', label: 'Dibutuhkan', type: 'date' },
    { key: 'issuedValue', label: 'Nilai diserahkan', type: 'money' }, STATUS_COL],
  header: [
    { key: 'woId', label: 'Work order (dirilis)', type: 'lookup', lookup: 'work-orders', lookupFilters: { status: 'APPROVED' }, required: true },
    { key: 'neededAt', label: 'Dibutuhkan tanggal', type: 'date' },
    { key: 'notes', label: 'Catatan (wajib bila permintaan tambahan)', type: 'textarea' },
  ],
  lines: [{
    key: 'lines', title: 'Bahan (kosong saat simpan = diisi dari BOM × qty WO)', addLabel: 'Tambah bahan',
    fields: [
      { key: 'itemId', label: 'Bahan', type: 'lookup', lookup: 'items', lookupFilters: { type: 'RM,PM,WIP' } },
      { key: 'qtyBom', label: 'Kebutuhan BOM', type: 'number', readOnly: true },
      { key: 'qty', label: 'Diminta', type: 'number' },
      { key: 'note', label: 'Alasan (bila > BOM)' },
    ],
    display: [{ key: 'uom', label: 'Satuan' }, { key: 'qtyIssued', label: 'Diserahkan gudang', type: 'number' }],
  }],
  summary: [{ key: 'issuedValue', label: 'Nilai bahan diserahkan', type: 'money' }],
  defaults: { lines: [] },
  actions: [{
    label: 'Isi ulang dari BOM', show: (_d, m) => m.status === 'DRAFT' || m.status === 'REJECTED',
    run: (d) => api.post(`/pre/material-requests/${d.id}/fill-bom`),
  }],
};

export const materialReturnDoc: DocConfig = {
  docType: 'MRT', title: 'Retur Sisa Bahan', endpoint: '/pre/material-returns',
  subtitle: (d) => (d.woNo ? `${d.woNo} · batch ${d.batchNo}` : undefined),
  listColumns: [DOCNO_COL, { key: 'docDate', label: 'Tanggal', type: 'date' }, { key: 'woNo', label: 'WO', mono: true, sortable: false },
    { key: 'returnedValue', label: 'Nilai', type: 'money' }, STATUS_COL],
  header: [
    { key: 'woId', label: 'Work order', type: 'lookup', lookup: 'work-orders', lookupFilters: { status: 'APPROVED,DONE' }, required: true },
    { key: 'notes', label: 'Catatan', type: 'textarea' },
  ],
  lines: [{
    key: 'lines', title: 'Sisa bahan dikembalikan',
    fields: [
      { key: 'itemId', label: 'Bahan', type: 'lookup', lookup: 'items', lookupFilters: { type: 'RM,PM' } },
      { key: 'lotId', label: 'Lot', type: 'lookup', lookup: 'lots', rowFilters: (r) => ({ item_id: String(r.itemId ?? -1) }) },
      { key: 'qty', label: 'Qty', type: 'number' },
    ],
    display: [{ key: 'uom', label: 'Satuan' }, { key: 'unitCost', label: 'Biaya satuan', type: 'money' }],
  }],
  summary: [{ key: 'returnedValue', label: 'Nilai diterima gudang', type: 'money' }],
};
