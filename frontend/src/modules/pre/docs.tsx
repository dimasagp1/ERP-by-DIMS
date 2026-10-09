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

// ---------------------------------------------------------------- PRE Phase 3: eBMR, Dispensing, IPC, Maintenance

export const batchRecordDoc: DocConfig = {
  docType: 'BMR',
  title: 'Batch Record Elektronik (eBMR)',
  endpoint: '/pre/batch-records',
  subtitle: (d) => (d.batchNo ? `Batch ${d.batchNo} · ${d.stageName}` : undefined),
  listColumns: [
    DOCNO_COL,
    { key: 'batchNo', label: 'Nomor Batch', mono: true },
    { key: 'stageName', label: 'Tahapan Proses' },
    STATUS_COL,
  ],
  header: [
    { key: 'woId', label: 'Work Order', type: 'lookup', lookup: 'work-orders', required: true },
    { key: 'batchNo', label: 'Nomor Batch', required: true },
    { key: 'stageName', label: 'Tahap Proses (mis. Ekstraksi, Pengayakan, Mixing, Kapsulasi)', required: true },
    { key: 'operatorId', label: 'Operator Pelaksana', type: 'lookup', lookup: 'employees' },
    { key: 'checkerId', label: 'Pemeriksa / In-Process QA', type: 'lookup', lookup: 'employees' },
    { key: 'startTime', label: 'Waktu Mulai', type: 'date' },
    { key: 'endTime', label: 'Waktu Selesai', type: 'date' },
    { key: 'notes', label: 'Catatan & Observasi Proses', type: 'textarea' },
  ],
};

export const dispensingDoc: DocConfig = {
  docType: 'DSP',
  title: 'Penimbangan & Dispensing',
  endpoint: '/pre/dispensings',
  listColumns: [
    DOCNO_COL,
    { key: 'targetQty', label: 'Target', type: 'number' },
    { key: 'actualQty', label: 'Aktual', type: 'number' },
    STATUS_COL,
  ],
  header: [
    { key: 'woId', label: 'Work Order', type: 'lookup', lookup: 'work-orders', required: true },
    { key: 'itemId', label: 'Bahan Baku', type: 'lookup', lookup: 'items', required: true },
    { key: 'lotId', label: 'Lot Released', type: 'lookup', lookup: 'lots', required: true },
    { key: 'targetQty', label: 'Jumlah Target Formula', type: 'number', required: true },
    { key: 'actualQty', label: 'Hasil Timbang Aktual', type: 'number', required: true },
    { key: 'barcodeScanned', label: 'Barcode Lot Terverifikasi' },
    { key: 'weighedBy', label: 'Petugas Timbang', type: 'lookup', lookup: 'employees' },
    { key: 'verifiedBy', label: 'Verifikator (Dual Sign)', type: 'lookup', lookup: 'employees' },
  ],
};

export const ipcEntryDoc: DocConfig = {
  docType: 'IPC',
  title: 'In-Process Control (IPC)',
  endpoint: '/pre/ipc-entries',
  listColumns: [
    DOCNO_COL,
    { key: 'stageName', label: 'Tahap' },
    { key: 'paramName', label: 'Parameter' },
    { key: 'measuredVal', label: 'Hasil Ukur' },
    STATUS_COL,
  ],
  header: [
    { key: 'woId', label: 'Work Order', type: 'lookup', lookup: 'work-orders', required: true },
    { key: 'stageName', label: 'Tahapan Proses', required: true },
    { key: 'paramName', label: 'Parameter Uji (mis. Kadar Air, pH, Viskositas, Bobot Rata-rata)', required: true },
    { key: 'targetVal', label: 'Rentang Standar' },
    { key: 'measuredVal', label: 'Hasil Pengukuran', required: true },
    { key: 'operatorId', label: 'Petugas IPC', type: 'lookup', lookup: 'employees' },
  ],
};

export const downtimeDoc: DocConfig = {
  docType: 'DT',
  title: 'Downtime & Kendala Lini',
  endpoint: '/pre/downtimes',
  listColumns: [
    DOCNO_COL,
    { key: 'machineName', label: 'Mesin' },
    { key: 'reasonCategory', label: 'Penyebab' },
    { key: 'durationMin', label: 'Durasi (Menit)', type: 'number' },
    STATUS_COL,
  ],
  header: [
    { key: 'lineId', label: 'Lini Produksi', type: 'lookup', lookup: 'lines', required: true },
    { key: 'woId', label: 'Work Order Terdampak', type: 'lookup', lookup: 'work-orders' },
    { key: 'machineName', label: 'Nama Mesin / Peralatan' },
    { key: 'reasonCategory', label: 'Kategori Downtime', type: 'select', options: [
      { value: 'BREAKDOWN', label: 'Kerusakan Mesin (Breakdown)' },
      { value: 'CHANGEOVER', label: 'Changeover / Bersih Lini' },
      { value: 'WAIT_MATERIAL', label: 'Menunggu Bahan / Kemasan' },
      { value: 'UTILITY_OFF', label: 'Gangguan Utilitas (Listrik/Steam)' },
    ], required: true },
    { key: 'durationMin', label: 'Durasi Henti (Menit)', type: 'number' },
    { key: 'actionTaken', label: 'Tindakan Penanganan', type: 'textarea' },
  ],
};

export const lineClearanceDoc: DocConfig = {
  docType: 'LCL',
  title: 'Line Clearance',
  endpoint: '/pre/line-clearances',
  listColumns: [
    DOCNO_COL,
    { key: 'statusResult', label: 'Hasil Verifikasi' },
    STATUS_COL,
  ],
  header: [
    { key: 'lineId', label: 'Lini Produksi', type: 'lookup', lookup: 'lines', required: true },
    { key: 'woId', label: 'Work Order yang Akan Mulai', type: 'lookup', lookup: 'work-orders', required: true },
    { key: 'checkedBy', label: 'Petugas Produksi', type: 'lookup', lookup: 'employees' },
    { key: 'qaInspectorId', label: 'Inspector QA', type: 'lookup', lookup: 'employees' },
    { key: 'statusResult', label: 'Keputusan Line Clearance', type: 'select', options: [
      { value: 'PASS', label: 'Lolos (Lini Siap Digunakan)' },
      { value: 'FAIL', label: 'Belum Lolos (Perlu Pembersihan Ulang)' },
    ], required: true },
  ],
};

export const workRequestDoc: DocConfig = {
  docType: 'WRQ',
  title: 'Work Request Kerusakan',
  endpoint: '/pre/work-requests',
  listColumns: [
    DOCNO_COL,
    { key: 'priority', label: 'Prioritas' },
    { key: 'description', label: 'Kerusakan' },
    STATUS_COL,
  ],
  header: [
    { key: 'machineId', label: 'Mesin / Peralatan', type: 'lookup', lookup: 'machines' },
    { key: 'requesterId', label: 'Pelapor', type: 'lookup', lookup: 'employees', required: true },
    { key: 'priority', label: 'Tingkat Urgensi', type: 'select', options: [
      { value: 'LOW', label: 'Rendah' },
      { value: 'NORMAL', label: 'Normal' },
      { value: 'HIGH', label: 'Tinggi (Lini Terhenti)' },
    ], required: true },
    { key: 'description', label: 'Deskripsi Gejala Kerusakan', type: 'textarea', required: true },
  ],
};

export const maintenanceOrderDoc: DocConfig = {
  docType: 'MNT',
  title: 'Work Order Maintenance',
  endpoint: '/pre/maintenance-orders',
  listColumns: [
    DOCNO_COL,
    { key: 'maintenanceType', label: 'Jenis' },
    { key: 'costAmount', label: 'Biaya', type: 'money' },
    STATUS_COL,
  ],
  header: [
    { key: 'machineId', label: 'Mesin', type: 'lookup', lookup: 'machines', required: true },
    { key: 'maintenanceType', label: 'Jenis Maintenance', type: 'select', options: [
      { value: 'CORRECTIVE', label: 'Perbaikan Korektif (Breakdown)' },
      { value: 'PREVENTIVE', label: 'Perawatan Berkala (PM)' },
    ], required: true },
    { key: 'technicianId', label: 'Teknisi Pelaksana', type: 'lookup', lookup: 'employees' },
    { key: 'costAmount', label: 'Biaya Perbaikan / Jasa (Rp)', type: 'money' },
    { key: 'causeAnalysis', label: 'Analisis Penyebab (Root Cause)', type: 'textarea' },
    { key: 'sparepartUsedJson', label: 'Sparepart & Komponen Terpakai', type: 'textarea' },
  ],
};

export const calibrationDoc: DocConfig = {
  docType: 'CAL',
  title: 'Kalibrasi & Kualifikasi',
  endpoint: '/pre/calibrations',
  listColumns: [
    DOCNO_COL,
    { key: 'certNo', label: 'No. Sertifikat' },
    { key: 'calibrationDate', label: 'Tgl Kalibrasi', type: 'date' },
    { key: 'nextDueDate', label: 'Jatuh Tempo', type: 'date' },
    STATUS_COL,
  ],
  header: [
    { key: 'machineId', label: 'Instrumen / Timbangan / Mesin', type: 'lookup', lookup: 'machines', required: true },
    { key: 'certNo', label: 'Nomor Sertifikat Kalibrasi' },
    { key: 'calibrationDate', label: 'Tanggal Kalibrasi', type: 'date', required: true },
    { key: 'nextDueDate', label: 'Jadwal Kalibrasi Berikutnya', type: 'date', required: true },
    { key: 'certFileUrl', label: 'Tautan File Sertifikat PDF' },
  ],
};

