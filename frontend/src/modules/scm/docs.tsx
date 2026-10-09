import { api } from '../../api/client';
import { DataTable } from '../../components/DataTable';
import { useToast } from '../../components/ui';
import { formatCell } from '../docs/DocList';
import { printAction } from '../docs/print';
import type { Doc, DocConfig, ExtraProps } from '../docs/types';
import type { Option } from '../master/types';

const opt = (...pairs: [string, string][]): Option[] => pairs.map(([value, label]) => ({ value, label }));
const STATUS_COL = { key: 'status', label: 'Status', type: 'status' as const, sortable: false };
const DOCNO_COL = { key: 'docNo', label: 'No. Dokumen', mono: true };
const DATE_COL = { key: 'docDate', label: 'Tanggal', type: 'date' as const };
const DOC_DATE = { key: 'docDate', label: 'Tanggal', type: 'date' as const, required: true };
const PARTNER_COL = { key: 'partnerName', label: 'Mitra', sortable: false };
const byItem = (r: Doc): Record<string, string> => (r.itemId ? { item_id: String(r.itemId) } : { item_id: '-1' });
const lotField = { key: 'lotId', label: 'Lot', type: 'lookup' as const, lookup: 'stock-lots', rowFilters: byItem };
const binField = (label = 'Lokasi'): DocConfig['header'][number] => ({ key: 'locationId', label, type: 'lookup', lookup: 'bins' });

/** Tombol isi baris dari dokumen sumber (PO, pesanan) saat draft sedang diedit. */
function FillLines({ doc, editing, setDoc, sourceKey, label, load, map }: ExtraProps & {
  sourceKey: string; label: string; load: (sourceId: number) => Promise<Record<string, unknown>[]>; map: (r: Record<string, unknown>) => Doc;
}) {
  const toast = useToast();
  if (!editing || doc[sourceKey] == null) return null;
  return (
    <div className="card row" style={{ padding: '10px 16px' }}>
      <span className="small muted" style={{ flex: 1 }}>Ambil semua baris yang masih terbuka dari dokumen sumber, lalu sesuaikan qty.</span>
      <button className="btn btn-sm" type="button" onClick={async () => {
        try {
          const rows = await load(Number(doc[sourceKey]));
          setDoc((d) => ({ ...d, lines: rows.map(map) }));
          toast.ok(`${rows.length} baris diambil`);
        } catch (e) {
          toast.error(e);
        }
      }}>{label}</button>
    </div>
  );
}

// ---------------------------------------------------------------- SCM-05 BOM

export const bomDoc: DocConfig = {
  docType: 'BOM', title: 'Bill of Materials', endpoint: '/scm/boms',
  subtitle: (d) => d.itemName as string | undefined,
  listColumns: [DOCNO_COL, { key: 'itemName', label: 'Produk', sortable: false }, { key: 'revision', label: 'Rev', type: 'number' },
    { key: 'baseQty', label: 'Jumlah dasar', type: 'number' }, { key: 'effectiveFrom', label: 'Berlaku', type: 'date' }, STATUS_COL],
  header: [
    { key: 'itemId', label: 'Produk (FG/WIP)', type: 'lookup', lookup: 'fg-items', required: true },
    { key: 'baseQty', label: 'Jumlah dasar (per batch standar)', type: 'number', required: true },
    { key: 'stdHours', label: 'Jam orang standar per jumlah dasar', type: 'number', help: 'Dasar biaya tenaga kerja & overhead standar (FIN-52)' },
    { key: 'effectiveFrom', label: 'Berlaku mulai', type: 'date' },
    { key: 'revision', label: 'Revisi', type: 'number', readOnly: true },
    { key: 'notes', label: 'Catatan', type: 'textarea' },
  ],
  lines: [{
    key: 'lines', title: 'Komponen', addLabel: 'Tambah komponen', newRow: () => ({ scrapPct: 0 }),
    fields: [
      { key: 'componentItemId', label: 'Komponen', type: 'lookup', lookup: 'items', lookupFilters: { type: 'RM,PM,WIP' } },
      { key: 'qty', label: 'Qty per jumlah dasar', type: 'number' },
      { key: 'scrapPct', label: 'Susut %', type: 'number' },
      { key: 'notes', label: 'Catatan' },
    ],
    display: [{ key: 'uom', label: 'Satuan' }],
  }],
  defaults: { stdHours: 0 },
};

// ---------------------------------------------------------------- SCM-02 Pesanan pelanggan

export const salesOrderDoc: DocConfig = {
  docType: 'SO', title: 'Pesanan Pelanggan', endpoint: '/scm/sales-orders',
  subtitle: (d) => d.partnerName as string | undefined,
  listColumns: [DOCNO_COL, DATE_COL, PARTNER_COL, { key: 'customerPo', label: 'PO customer', mono: true },
    { key: 'deliveryDate', label: 'Tgl kirim', type: 'date' }, { key: 'total', label: 'Total', type: 'money' },
    { key: 'creditHold', label: 'Kredit tertahan', type: 'bool' }, STATUS_COL],
  header: [
    { key: 'partnerId', label: 'Customer', type: 'lookup', lookup: 'partners', lookupFilters: { type: 'CUSTOMER,BOTH' }, required: true },
    { key: 'customerPo', label: 'No. PO customer' },
    DOC_DATE,
    { key: 'deliveryDate', label: 'Tanggal kirim', type: 'date', help: 'Kosong = 7 hari dari tanggal pesanan' },
    { key: 'shipTo', label: 'Alamat kirim', type: 'textarea' },
    { key: 'notes', label: 'Catatan', type: 'textarea' },
    { key: 'holdReason', label: 'Kredit tertahan (butuh approval Finance)', type: 'textarea', readOnly: true, show: (d) => Boolean(d.creditHold) },
    { key: 'withPpn', label: 'Kena PPN', type: 'bool' },
  ],
  defaults: { withPpn: true },
  lines: [{
    key: 'lines', title: 'Produk', addLabel: 'Tambah produk', newRow: () => ({ discountPct: 0 }),
    fields: [
      { key: 'itemId', label: 'Produk', type: 'lookup', lookup: 'items', lookupFilters: { type: 'FG' } },
      { key: 'qty', label: 'Qty', type: 'number' },
      { key: 'unitPrice', label: 'Harga', type: 'money' },
      { key: 'discountPct', label: 'Disk %', type: 'number' },
      { key: 'deliveryDate', label: 'Tgl kirim', type: 'date' },
      { key: 'amount', label: 'Jumlah', type: 'money', readOnly: true },
    ],
    display: [{ key: 'available', label: 'Stok siap kirim', type: 'number' }, { key: 'qtyShipped', label: 'Terkirim', type: 'number' }],
  }],
  summary: [
    { key: 'subtotal', label: 'Subtotal', type: 'money' },
    { key: 'ppnAmount', label: 'PPN', type: 'money' },
    { key: 'total', label: 'Total', type: 'money' },
  ],
  actions: [{
    label: 'Tutup sisa pesanan', show: (_d, m) => m.status === 'APPROVED',
    run: async (d) => {
      const reason = window.prompt('Alasan menutup sisa pesanan yang tidak dikirim:');
      if (!reason) throw new Error('Dibatalkan: alasan wajib diisi');
      return api.post(`/scm/sales-orders/${d.id}/close`, { reason });
    },
  }],
};

// ---------------------------------------------------------------- SCM-20 Penerimaan barang

function GrFill(p: ExtraProps) {
  return <FillLines {...p} sourceKey="poId" label="Ambil baris PO"
    load={(po) => api.get(`/scm/receipts/po-lines`, { poId: po })}
    map={(r) => ({ poLineId: r.poLineId, qty: r.remaining, itemLabel: r.itemLabel })} />;
}

export const receiptDoc: DocConfig = {
  docType: 'GR', title: 'Penerimaan Barang', endpoint: '/scm/receipts',
  subtitle: (d) => (d.partnerName ? `${d.partnerName} · ${d.poNo}` : undefined),
  listColumns: [DOCNO_COL, DATE_COL, PARTNER_COL, { key: 'poNo', label: 'PO', mono: true, sortable: false },
    { key: 'deliveryNoteNo', label: 'Surat jalan supplier' }, { key: 'total', label: 'Nilai', type: 'money' }, STATUS_COL],
  header: [
    { key: 'poId', label: 'Purchase order', type: 'lookup', lookup: 'po-open', lookupFilters: { kind: 'GOODS' }, required: true },
    DOC_DATE,
    { key: 'deliveryNoteNo', label: 'No. surat jalan supplier', required: true },
    { key: 'vehicleNo', label: 'No. kendaraan' },
    { key: 'notes', label: 'Catatan', type: 'textarea' },
  ],
  lines: [{
    key: 'lines', title: 'Barang diterima', addLabel: 'Tambah baris',
    fields: [
      { key: 'poLineId', label: 'Baris PO (sisa)', type: 'lookup', lookup: 'po-lines-open', lookupFiltersFrom: (d) => ({ po_id: String(d.poId ?? -1) }) },
      { key: 'qty', label: 'Qty diterima', type: 'number' },
      { key: 'supplierLot', label: 'Lot supplier' },
      { key: 'manufacturer', label: 'Pabrikan' },
      { key: 'mfgDate', label: 'Tgl produksi', type: 'date' },
      { key: 'expDate', label: 'Kedaluwarsa', type: 'date' },
      binField('Lokasi (kosong = karantina)'),
    ],
    display: [{ key: 'lotNo', label: 'Lot internal' }, { key: 'uom', label: 'Satuan' }, { key: 'amount', label: 'Nilai', type: 'money' }],
  }],
  summary: [{ key: 'total', label: 'Nilai penerimaan', type: 'money' }],
  extra: GrFill,
};

// ---------------------------------------------------------------- SCM-25 Transfer

export const transferDoc: DocConfig = {
  docType: 'TRF', title: 'Transfer Antar Gudang', endpoint: '/scm/transfers',
  listColumns: [DOCNO_COL, DATE_COL, { key: 'notes', label: 'Catatan' }, STATUS_COL],
  header: [DOC_DATE, { key: 'notes', label: 'Catatan / alasan', type: 'textarea' }],
  lines: [{
    key: 'lines', title: 'Barang dipindah',
    fields: [
      { key: 'itemId', label: 'Item', type: 'lookup', lookup: 'items' },
      lotField,
      { key: 'fromLocationId', label: 'Dari lokasi', type: 'lookup', lookup: 'bins' },
      { key: 'toLocationId', label: 'Ke lokasi', type: 'lookup', lookup: 'bins' },
      { key: 'qty', label: 'Qty', type: 'number' },
    ],
  }],
};

// ---------------------------------------------------------------- SCM-29 Pengeluaran non-produksi

export const goodsIssueDoc: DocConfig = {
  docType: 'GI', title: 'Pengeluaran Non-Produksi', endpoint: '/scm/goods-issues',
  listColumns: [DOCNO_COL, DATE_COL, { key: 'purpose', label: 'Keperluan' }, { key: 'total', label: 'Nilai', type: 'money' }, STATUS_COL],
  header: [
    { key: 'costCenterId', label: 'Cost center penerima', type: 'lookup', lookup: 'cost-centers', required: true },
    DOC_DATE,
    { key: 'purpose', label: 'Keperluan', type: 'textarea', required: true },
  ],
  lines: [{
    key: 'lines', title: 'Barang dikeluarkan',
    fields: [
      { key: 'itemId', label: 'Item', type: 'lookup', lookup: 'items', lookupFilters: { type: 'SP,ATK,RM,PM' } },
      lotField,
      binField('Dari lokasi'),
      { key: 'qty', label: 'Qty', type: 'number' },
      { key: 'accountId', label: 'Akun beban (kosong = bawaan SP/ATK)', type: 'lookup', lookup: 'accounts', lookupFilters: { type: 'EXPENSE' } },
    ],
    display: [{ key: 'unitCost', label: 'Biaya satuan', type: 'money' }, { key: 'amount', label: 'Nilai', type: 'money' }],
  }],
  summary: [{ key: 'total', label: 'Total nilai', type: 'money' }],
};

// ---------------------------------------------------------------- SCM-26 Surat jalan

function DoFill(p: ExtraProps) {
  return <FillLines {...p} sourceKey="soId" label="Ambil sisa pesanan"
    load={(so) => api.get(`/scm/sales-orders/${so}/open-lines`)}
    map={(r) => ({ soLineId: r.soLineId, qty: r.remaining, itemLabel: r.itemLabel })} />;
}

export const deliveryDoc: DocConfig = {
  docType: 'DO', title: 'Surat Jalan', endpoint: '/scm/deliveries',
  subtitle: (d) => (d.partnerName ? `${d.partnerName} · ${d.soNo}` : undefined),
  listColumns: [DOCNO_COL, DATE_COL, PARTNER_COL, { key: 'soNo', label: 'Pesanan', mono: true, sortable: false },
    { key: 'totalValue', label: 'Nilai', type: 'money' }, { key: 'arInvoiceNo', label: 'Faktur', mono: true, sortable: false }, STATUS_COL],
  header: [
    { key: 'soId', label: 'Pesanan pelanggan', type: 'lookup', lookup: 'so-approved', required: true },
    DOC_DATE,
    { key: 'expeditionPartnerId', label: 'Ekspedisi', type: 'lookup', lookup: 'partners', lookupFilters: { type: 'EXPEDITION' } },
    { key: 'vehicleNo', label: 'No. kendaraan' },
    { key: 'driver', label: 'Pengemudi' },
    { key: 'shipTo', label: 'Alamat kirim (kosong = dari pesanan)', type: 'textarea' },
    { key: 'notes', label: 'Catatan', type: 'textarea' },
    { key: 'holdReason', label: 'Kredit tertahan (butuh approval Finance)', type: 'textarea', readOnly: true, show: (d) => Boolean(d.creditHold) },
    { key: 'arInvoiceNo', label: 'Faktur penjualan (FIN-20)', readOnly: true, show: (d) => Boolean(d.arInvoiceNo) },
  ],
  lines: [{
    key: 'lines', title: 'Barang dikirim (lot kosong = dipilih otomatis FEFO)',
    fields: [
      { key: 'soLineId', label: 'Baris pesanan (sisa)', type: 'lookup', lookup: 'so-lines-open', lookupFiltersFrom: (d) => ({ so_id: String(d.soId ?? -1) }) },
      { key: 'qty', label: 'Qty', type: 'number' },
      { key: 'lotId', label: 'Lot (opsional)', type: 'lookup', lookup: 'stock-lots', rowFilters: (r) => ({ item_id: String(r.itemId ?? -1), qc_status: 'RELEASED' }) },
      binField('Lokasi'),
    ],
    display: [{ key: 'itemLabel', label: 'Produk' }, { key: 'lotNo', label: 'Lot' }, { key: 'expDate', label: 'ED', type: 'date' }],
  }],
  summary: [{ key: 'totalValue', label: 'Nilai jual', type: 'money' }, { key: 'totalCost', label: 'HPP', type: 'money' }],
  extra: DoFill,
  actions: [printAction({ title: 'Surat Jalan' }, (d) => ({
    title: 'Surat Jalan',
    head: [['Customer', d.partnerName], ['Pesanan', d.soNo], ['Tanggal', formatCell('date', d.docDate)], ['Alamat kirim', d.shipTo],
      ['Kendaraan', d.vehicleNo], ['Pengemudi', d.driver]],
    columns: [['Produk', 'itemLabel'], ['Lot', 'lotNo'], ['Kedaluwarsa', 'expDate', 'date'], ['Qty', 'qty', 'number']],
    rows: (d.lines as Doc[]) ?? [],
    signatures: ['Gudang', 'Pengemudi', 'Penerima'],
  }), 'Cetak surat jalan')],
};

// ---------------------------------------------------------------- SCM-27 Retur pelanggan

export const customerReturnDoc: DocConfig = {
  docType: 'CRT', title: 'Retur Pelanggan', endpoint: '/scm/customer-returns',
  subtitle: (d) => (d.partnerName ? `${d.partnerName} · ${d.deliveryNo}` : undefined),
  listColumns: [DOCNO_COL, DATE_COL, PARTNER_COL, { key: 'deliveryNo', label: 'Surat jalan', mono: true, sortable: false },
    { key: 'total', label: 'Nota kredit', type: 'money' }, STATUS_COL],
  header: [
    { key: 'deliveryId', label: 'Surat jalan', type: 'lookup', lookup: 'deliveries-posted', required: true },
    DOC_DATE,
    { key: 'reason', label: 'Alasan retur', type: 'textarea', required: true },
  ],
  lines: [{
    key: 'lines', title: 'Barang diretur (masuk karantina menunggu QA)',
    fields: [
      { key: 'deliveryLineId', label: 'Baris surat jalan (lot)', type: 'lookup', lookup: 'delivery-lines',
        lookupFiltersFrom: (d) => ({ delivery_id: String(d.deliveryId ?? -1) }) },
      { key: 'qty', label: 'Qty retur', type: 'number' },
      { key: 'locationId', label: 'Lokasi karantina', type: 'lookup', lookup: 'bins', lookupFilters: { is_quarantine: 'true' } },
    ],
    display: [{ key: 'itemLabel', label: 'Produk' }, { key: 'lotNo', label: 'Lot' }, { key: 'amount', label: 'Nilai', type: 'money' }],
  }],
  summary: [{ key: 'subtotal', label: 'Retur penjualan', type: 'money' }, { key: 'ppnAmount', label: 'PPN', type: 'money' },
    { key: 'total', label: 'Nota kredit', type: 'money' }],
};

// ---------------------------------------------------------------- SCM-41 Opname, SCM-42 Penyesuaian, SCM-46 Pemusnahan

export const countDoc: DocConfig = {
  docType: 'CNT', title: 'Stock Opname', endpoint: '/scm/counts',
  listColumns: [DOCNO_COL, DATE_COL, { key: 'kind', label: 'Jenis' }, { key: 'linesTotal', label: 'Baris', type: 'number' },
    { key: 'linesAccurate', label: 'Akurat', type: 'number' }, { key: 'diffValue', label: 'Selisih', type: 'money' }, STATUS_COL],
  header: [
    { key: 'warehouseId', label: 'Gudang', type: 'lookup', lookup: 'warehouses', required: true },
    { key: 'kind', label: 'Jenis', type: 'select', options: opt(['CYCLE', 'Cycle count'], ['FULL', 'Opname penuh']) },
    DOC_DATE,
    { key: 'notes', label: 'Catatan', type: 'textarea' },
  ],
  defaults: { kind: 'CYCLE' },
  lines: [{
    key: 'lines', title: 'Lembar hitung (saldo sistem dibekukan saat diambil)', addLabel: 'Tambah temuan',
    fields: [
      { key: 'itemId', label: 'Item', type: 'lookup', lookup: 'items' },
      { key: 'lotId', label: 'Lot', type: 'lookup', lookup: 'lots', rowFilters: byItem },
      binField(),
      { key: 'systemQty', label: 'Saldo sistem', type: 'number', readOnly: true },
      { key: 'countedQty', label: 'Hasil hitung', type: 'number' },
      { key: 'diffQty', label: 'Selisih', type: 'number', readOnly: true },
      { key: 'note', label: 'Penyebab selisih' },
    ],
    display: [{ key: 'diffValue', label: 'Nilai selisih', type: 'money' }],
  }],
  summary: [{ key: 'linesTotal', label: 'Baris dihitung', type: 'number' }, { key: 'linesAccurate', label: 'Baris akurat', type: 'number' },
    { key: 'diffValue', label: 'Nilai selisih bersih', type: 'money' }],
  actions: [{
    label: 'Ambil saldo sistem', primary: true, show: (_d, m) => m.status === 'DRAFT',
    run: (d) => api.post(`/scm/counts/${d.id}/snapshot`),
  }],
};

export const adjustmentDoc: DocConfig = {
  docType: 'ADJ', title: 'Penyesuaian Stok', endpoint: '/scm/adjustments',
  listColumns: [DOCNO_COL, DATE_COL, { key: 'reason', label: 'Alasan' }, { key: 'totalValue', label: 'Nilai bersih', type: 'money' }, STATUS_COL],
  header: [DOC_DATE, { key: 'reason', label: 'Alasan penyesuaian', type: 'textarea', required: true }],
  lines: [{
    key: 'lines', title: 'Baris penyesuaian (qty + menambah, − mengurangi)',
    fields: [
      { key: 'itemId', label: 'Item', type: 'lookup', lookup: 'items' },
      { key: 'lotId', label: 'Lot', type: 'lookup', lookup: 'lots', rowFilters: byItem },
      binField(),
      { key: 'qtyDelta', label: 'Qty ±', type: 'number' },
      { key: 'unitCost', label: 'Biaya satuan (opsional)', type: 'money' },
      { key: 'amount', label: 'Nilai', type: 'money', readOnly: true },
      { key: 'note', label: 'Catatan' },
    ],
  }],
  summary: [{ key: 'totalValue', label: 'Nilai bersih', type: 'money' }],
};

export const scrapDoc: DocConfig = {
  docType: 'SCR', title: 'Pemusnahan Barang', endpoint: '/scm/scraps',
  listColumns: [DOCNO_COL, DATE_COL, { key: 'reason', label: 'Alasan' }, { key: 'totalValue', label: 'Nilai', type: 'money' }, STATUS_COL],
  header: [
    DOC_DATE,
    { key: 'reason', label: 'Alasan pemusnahan', type: 'textarea', required: true },
    { key: 'method', label: 'Metode (insinerasi, pihak ketiga berizin…)' },
    { key: 'witnesses', label: 'Saksi' },
    { key: 'vendorPartnerId', label: 'Pihak ketiga pemusnah', type: 'lookup', lookup: 'partners' },
  ],
  lines: [{
    key: 'lines', title: 'Barang dimusnahkan',
    fields: [
      { key: 'itemId', label: 'Item', type: 'lookup', lookup: 'items' },
      { key: 'lotId', label: 'Lot', type: 'lookup', lookup: 'lots', rowFilters: byItem },
      binField(),
      { key: 'qty', label: 'Qty', type: 'number' },
      { key: 'amount', label: 'Nilai', type: 'money', readOnly: true },
    ],
    display: [{ key: 'lotNo', label: 'No. lot' }],
  }],
  summary: [{ key: 'totalValue', label: 'Nilai dimusnahkan', type: 'money' }],
  actions: [printAction({ title: 'Berita Acara Pemusnahan' }, (d) => ({
    title: 'Berita Acara Pemusnahan Barang',
    head: [['Tanggal', formatCell('date', d.docDate)], ['Alasan', d.reason], ['Metode', d.method], ['Saksi', d.witnesses]],
    columns: [['Item', 'itemId'], ['Lot', 'lotNo'], ['Qty', 'qty', 'number'], ['Nilai', 'amount', 'money']],
    rows: (d.lines as Doc[]) ?? [],
    totals: [['Total nilai', d.totalValue]],
    signatures: ['Warehouse', 'QA', 'Finance', 'Saksi'],
  }), 'Cetak berita acara')],
};

/** Tabel kecil read-only untuk data hasil hitung server. */
export function ReadTable({ title, rows, columns }: { title: string; rows: Doc[]; columns: [string, string, string?][] }) {
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="card-h">{title}</div>
      <DataTable rows={rows} rowKey={(r) => String(r.id ?? JSON.stringify(r))} empty="Belum ada data"
        columns={columns.map(([label, key, type]) => ({ key, label, align: type === 'money' || type === 'number' ? 'right' as const : undefined,
          render: (r: Doc) => formatCell(type, r[key]) }))} />
    </div>
  );
}
