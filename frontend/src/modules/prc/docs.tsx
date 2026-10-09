import { api } from '../../api/client';
import { useToast } from '../../components/ui';
import { formatCell } from '../docs/DocList';
import { printAction } from '../docs/print';
import type { Doc, DocConfig, ExtraProps } from '../docs/types';
import type { Option } from '../master/types';
import { ReadTable } from '../scm/docs';

const opt = (...pairs: [string, string][]): Option[] => pairs.map(([value, label]) => ({ value, label }));
const STATUS_COL = { key: 'status', label: 'Status', type: 'status' as const, sortable: false };
const DOCNO_COL = { key: 'docNo', label: 'No. Dokumen', mono: true };
const DATE_COL = { key: 'docDate', label: 'Tanggal', type: 'date' as const };
const DOC_DATE = { key: 'docDate', label: 'Tanggal', type: 'date' as const, required: true };
const PARTNER_COL = { key: 'partnerName', label: 'Supplier', sortable: false };
const byItem = (r: Doc): Record<string, string> => (r.itemId ? { item_id: String(r.itemId) } : { item_id: '-1' });

// ---------------------------------------------------------------- PRC-02 Purchase Requisition (juga ESS-04)

export const requisitionDoc: DocConfig = {
  docType: 'PR', title: 'Purchase Requisition', endpoint: '/prc/requisitions',
  subtitle: (d) => d.purpose as string | undefined,
  listColumns: [DOCNO_COL, DATE_COL, { key: 'source', label: 'Sumber' }, { key: 'purpose', label: 'Keperluan' },
    { key: 'needDate', label: 'Dibutuhkan', type: 'date' }, { key: 'totalEst', label: 'Perkiraan', type: 'money' }, STATUS_COL],
  listFilters: [{ key: 'source', label: 'Sumber', options: opt(['MANUAL', 'Manual'], ['MRP', 'MRP'], ['ESS', 'Layanan Saya']) }],
  header: [
    { key: 'purpose', label: 'Keperluan', type: 'textarea', required: true },
    DOC_DATE,
    { key: 'needDate', label: 'Dibutuhkan tanggal', type: 'date' },
    { key: 'costCenterId', label: 'Cost center (kosong = cost center Anda)', type: 'lookup', lookup: 'cost-centers' },
  ],
  lines: [{
    key: 'lines', title: 'Kebutuhan', addLabel: 'Tambah item',
    fields: [
      { key: 'itemId', label: 'Item / jasa', type: 'lookup', lookup: 'items' },
      { key: 'description', label: 'Spesifikasi / keterangan' },
      { key: 'qty', label: 'Qty', type: 'number' },
      { key: 'needDate', label: 'Dibutuhkan', type: 'date' },
      { key: 'estPrice', label: 'Perkiraan harga (kosong = kontrak)', type: 'money' },
      { key: 'accountId', label: 'Akun beban (jasa/non-stok)', type: 'lookup', lookup: 'accounts', lookupFilters: { type: 'EXPENSE' } },
      { key: 'amount', label: 'Perkiraan nilai', type: 'money', readOnly: true },
    ],
    display: [{ key: 'uom', label: 'Satuan' }, { key: 'qtyOrdered', label: 'Sudah di-PO', type: 'number' }],
  }],
  summary: [{ key: 'totalEst', label: 'Perkiraan total', type: 'money' }],
};

// ---------------------------------------------------------------- PRC-05 RFQ

export const rfqDoc: DocConfig = {
  docType: 'RFQ', title: 'Permintaan Penawaran', endpoint: '/prc/rfqs',
  listColumns: [DOCNO_COL, DATE_COL, { key: 'dueDate', label: 'Batas penawaran', type: 'date' },
    { key: 'awardedPartnerName', label: 'Pemenang', sortable: false }, { key: 'poNo', label: 'PO', mono: true, sortable: false }, STATUS_COL],
  header: [
    DOC_DATE,
    { key: 'dueDate', label: 'Batas waktu penawaran', type: 'date' },
    { key: 'notes', label: 'Catatan untuk supplier', type: 'textarea' },
    { key: 'awardReason', label: 'Alasan pemilihan', readOnly: true, show: (d) => Boolean(d.awardReason) },
  ],
  lines: [{
    key: 'lines', title: 'Item yang diminta penawarannya',
    fields: [
      { key: 'prLineId', label: 'Dari PR (opsional)', type: 'lookup', lookup: 'pr-lines-open' },
      { key: 'itemId', label: 'Item', type: 'lookup', lookup: 'items' },
      { key: 'description', label: 'Spesifikasi' },
      { key: 'qty', label: 'Qty', type: 'number' },
    ],
  }, {
    key: 'suppliers', title: 'Supplier diundang (minimal 3 untuk nilai di atas batas)', addLabel: 'Tambah supplier',
    fields: [{ key: 'partnerId', label: 'Supplier', type: 'lookup', lookup: 'suppliers', lookupFilters: { qual: 'QUALIFIED,CONDITIONAL' } }],
  }],
  actions: [{
    label: 'Bandingkan & pilih pemenang', primary: true, show: (_d, m) => m.status === 'APPROVED',
    run: async (d) => { window.location.assign(`/app/PRC/m/PRC-06?rfq=${d.id}`); },
  }],
};

// ---------------------------------------------------------------- PRC-07 Purchase Order

export const purchaseOrderDoc: DocConfig = {
  docType: 'PO', title: 'Purchase Order', endpoint: '/prc/purchase-orders',
  subtitle: (d) => d.partnerName as string | undefined,
  listColumns: [DOCNO_COL, DATE_COL, PARTNER_COL, { key: 'kind', label: 'Jenis' }, { key: 'deliveryDate', label: 'Kirim', type: 'date' },
    { key: 'totalIdr', label: 'Total (Rp)', type: 'money' }, STATUS_COL],
  listFilters: [{ key: 'kind', label: 'Jenis', options: opt(['GOODS', 'Barang'], ['SERVICE', 'Jasa']) }],
  header: [
    { key: 'partnerId', label: 'Supplier', type: 'lookup', lookup: 'suppliers', lookupFilters: { qual: 'QUALIFIED,CONDITIONAL' }, required: true },
    DOC_DATE,
    { key: 'deliveryDate', label: 'Tanggal kirim', type: 'date' },
    { key: 'warehouseId', label: 'Gudang tujuan', type: 'lookup', lookup: 'warehouses' },
    { key: 'currencyCode', label: 'Mata uang', type: 'lookup', lookup: 'currencies' },
    { key: 'exchangeRate', label: 'Kurs', type: 'number', show: (d) => Boolean(d.currencyCode) && d.currencyCode !== 'IDR', help: 'Kosong = kurs SYS-11' },
    { key: 'paymentTermDays', label: 'Termin (hari)', type: 'number' },
    { key: 'singleSourceReason', label: 'Alasan pemasok tunggal (bila tanpa perbandingan penawaran)', type: 'textarea' },
    { key: 'notes', label: 'Catatan', type: 'textarea' },
    { key: 'closedReason', label: 'Ditutup', readOnly: true, show: (d) => Boolean(d.closedReason) },
    { key: 'withPpn', label: 'Kena PPN', type: 'bool' },
    { key: 'importPo', label: 'PO impor (PPN & PPh 22 impor lewat PIB di PRC-10)', type: 'bool' },
  ],
  defaults: { withPpn: true, currencyCode: 'IDR' },
  lines: [{
    key: 'lines', title: 'Barang / jasa', addLabel: 'Tambah baris',
    fields: [
      { key: 'prLineId', label: 'Dari PR', type: 'lookup', lookup: 'pr-lines-open' },
      { key: 'itemId', label: 'Item (kosong = dari PR)', type: 'lookup', lookup: 'items' },
      { key: 'description', label: 'Keterangan' },
      { key: 'qty', label: 'Qty', type: 'number' },
      { key: 'unitPrice', label: 'Harga (kosong = kontrak)', type: 'money' },
      { key: 'discountPct', label: 'Disk %', type: 'number' },
      { key: 'needDate', label: 'Dibutuhkan', type: 'date' },
      { key: 'costCenterId', label: 'Cost center', type: 'lookup', lookup: 'cost-centers' },
      { key: 'accountId', label: 'Akun beban (jasa)', type: 'lookup', lookup: 'accounts', lookupFilters: { type: 'EXPENSE' } },
      { key: 'amount', label: 'Jumlah', type: 'money', readOnly: true },
    ],
    display: [{ key: 'uom', label: 'Satuan' }, { key: 'receivedQty', label: 'Diterima', type: 'number' }, { key: 'prNo', label: 'PR' }],
  }],
  summary: [
    { key: 'subtotal', label: 'Subtotal', type: 'money' },
    { key: 'ppnAmount', label: 'PPN', type: 'money' },
    { key: 'total', label: 'Total', type: 'money' },
    { key: 'totalIdr', label: 'Total (Rp)', type: 'money', show: (d) => d.currencyCode !== 'IDR' },
  ],
  actions: [
    {
      label: 'Tutup sisa PO', show: (_d, m) => m.status === 'APPROVED',
      run: async (d) => {
        const reason = window.prompt('Alasan menutup sisa PO yang tidak akan dikirim supplier:');
        if (!reason) throw new Error('Dibatalkan: alasan wajib diisi');
        return api.post(`/prc/purchase-orders/${d.id}/close`, { reason });
      },
    },
    printAction({ title: 'Purchase Order' }, (d) => ({
      title: 'Purchase Order',
      head: [['Supplier', d.partnerName], ['Tanggal', formatCell('date', d.docDate)], ['Tanggal kirim', formatCell('date', d.deliveryDate)],
        ['Termin', `${d.paymentTermDays} hari`], ['Mata uang', d.currencyCode]],
      columns: [['Item', 'itemLabel'], ['Keterangan', 'description'], ['Qty', 'qty', 'number'], ['Satuan', 'uom'], ['Harga', 'unitPrice', 'money'],
        ['Disk %', 'discountPct', 'number'], ['Jumlah', 'amount', 'money']],
      rows: (d.lines as Doc[]) ?? [],
      totals: [['Subtotal', d.subtotal], ['PPN', d.ppnAmount], ['Total', d.total]],
      signatures: ['Dibuat', 'Disetujui', 'Supplier'],
      note: 'Cantumkan nomor PO pada surat jalan dan faktur. Barang diterima ke karantina dan diperiksa QC sebelum digunakan.',
    }), 'Cetak PO'),
  ],
};

// ---------------------------------------------------------------- PRC-12 BAST jasa

function BastFill({ doc, editing, setDoc }: ExtraProps) {
  const toast = useToast();
  if (!editing || doc.poId == null) return null;
  return (
    <div className="card row" style={{ padding: '10px 16px' }}>
      <span className="small muted" style={{ flex: 1 }}>Ambil semua baris jasa PO yang belum diterima.</span>
      <button className="btn btn-sm" type="button" onClick={async () => {
        try {
          const rows = await api.get<Record<string, unknown>[]>('/prc/service-acceptances/po-lines', { poId: Number(doc.poId) });
          setDoc((d) => ({ ...d, lines: rows.map((r) => ({ poLineId: r.po_line_id, qty: r.remaining })) }));
        } catch (e) {
          toast.error(e);
        }
      }}>Ambil baris jasa</button>
    </div>
  );
}

export const bastDoc: DocConfig = {
  docType: 'BAST', title: 'BAST Jasa', endpoint: '/prc/service-acceptances',
  subtitle: (d) => (d.partnerName ? `${d.partnerName} · ${d.poNo}` : undefined),
  listColumns: [DOCNO_COL, DATE_COL, { key: 'poNo', label: 'PO', mono: true, sortable: false }, PARTNER_COL,
    { key: 'total', label: 'Nilai', type: 'money' }, STATUS_COL],
  header: [
    { key: 'poId', label: 'PO jasa', type: 'lookup', lookup: 'po-open', lookupFilters: { kind: 'SERVICE' }, required: true },
    DOC_DATE,
    { key: 'serviceFrom', label: 'Pekerjaan mulai', type: 'date' },
    { key: 'serviceTo', label: 'Pekerjaan selesai', type: 'date' },
    { key: 'notes', label: 'Catatan hasil pekerjaan', type: 'textarea' },
  ],
  lines: [{
    key: 'lines', title: 'Jasa diterima',
    fields: [
      { key: 'poLineId', label: 'Baris PO', type: 'lookup', lookup: 'po-lines-open',
        lookupFiltersFrom: (d) => ({ po_id: String(d.poId ?? -1), item_type: 'SVC' }) },
      { key: 'qty', label: 'Qty diterima', type: 'number' },
      { key: 'acceptanceNote', label: 'Catatan penerimaan' },
      { key: 'amount', label: 'Nilai', type: 'money', readOnly: true },
    ],
    display: [{ key: 'ordered', label: 'Dipesan', type: 'number' }, { key: 'received', label: 'Sudah diterima', type: 'number' }],
  }],
  summary: [{ key: 'total', label: 'Nilai jasa diterima', type: 'money' }],
  extra: BastFill,
};

// ---------------------------------------------------------------- PRC-10 Impor & landed cost

function Allocations({ doc }: ExtraProps) {
  const rows = (doc.allocations as Doc[]) ?? [];
  if (doc.id == null) return null;
  return <ReadTable title="Alokasi ke penerimaan barang (dihitung saat simpan)" rows={rows}
    columns={[['Item', 'itemLabel'], ['Lot', 'lotNo'], ['Qty', 'qty', 'number'], ['Nilai dasar', 'baseValue', 'money'], ['Dialokasikan', 'allocated', 'money']]} />;
}

export const landedCostDoc: DocConfig = {
  docType: 'LC', title: 'Impor & Landed Cost', endpoint: '/prc/landed-costs',
  subtitle: (d) => d.poNo as string | undefined,
  listColumns: [DOCNO_COL, DATE_COL, { key: 'poNo', label: 'PO', mono: true, sortable: false }, { key: 'pibNo', label: 'PIB', mono: true },
    { key: 'capitalized', label: 'Dikapitalisasi', type: 'money' }, { key: 'taxCredit', label: 'Pajak impor', type: 'money' }, STATUS_COL],
  header: [
    { key: 'poId', label: 'PO', type: 'lookup', lookup: 'po-received', required: true },
    DOC_DATE,
    { key: 'shipmentNo', label: 'No. shipment' },
    { key: 'blNo', label: 'No. B/L / AWB' },
    { key: 'pibNo', label: 'No. PIB' },
    { key: 'pibDate', label: 'Tanggal PIB', type: 'date' },
    { key: 'arrivalDate', label: 'Tanggal tiba', type: 'date' },
    { key: 'allocBasis', label: 'Dasar alokasi', type: 'select', options: opt(['VALUE', 'Nilai barang'], ['QTY', 'Kuantitas']) },
    { key: 'notes', label: 'Catatan', type: 'textarea' },
  ],
  defaults: { allocBasis: 'VALUE' },
  lines: [{
    key: 'charges', title: 'Komponen biaya', addLabel: 'Tambah biaya',
    fields: [
      { key: 'kind', label: 'Jenis', type: 'select', options: opt(['FREIGHT', 'Freight'], ['INSURANCE', 'Asuransi'], ['DUTY', 'Bea masuk'],
        ['HANDLING', 'Handling & dokumen'], ['OTHER', 'Lain-lain'], ['PPN_IMPORT', 'PPN impor (kredit pajak)'], ['PPH22_IMPORT', 'PPh 22 impor (kredit pajak)']) },
      { key: 'partnerId', label: 'Vendor', type: 'lookup', lookup: 'partners' },
      { key: 'reference', label: 'Referensi' },
      { key: 'amount', label: 'Nilai (Rp)', type: 'money' },
    ],
  }],
  summary: [{ key: 'capitalized', label: 'Dikapitalisasi ke persediaan', type: 'money' }, { key: 'taxCredit', label: 'PPN & PPh 22 impor', type: 'money' },
    { key: 'total', label: 'Total', type: 'money' }],
  extra: Allocations,
};

// ---------------------------------------------------------------- PRC-11 / SCM-28 Retur supplier

export const supplierReturnDoc: DocConfig = {
  docType: 'RTS', title: 'Retur & Klaim Supplier', endpoint: '/prc/supplier-returns',
  subtitle: (d) => d.partnerName as string | undefined,
  listColumns: [DOCNO_COL, DATE_COL, PARTNER_COL, { key: 'claimType', label: 'Klaim' }, { key: 'total', label: 'Nilai', type: 'money' }, STATUS_COL],
  header: [
    { key: 'poId', label: 'PO asal', type: 'lookup', lookup: 'po-received' },
    { key: 'partnerId', label: 'Supplier (bila tanpa PO)', type: 'lookup', lookup: 'suppliers', show: (d) => d.poId == null },
    DOC_DATE,
    { key: 'claimType', label: 'Jenis klaim', type: 'select', options: opt(['REPLACE', 'Minta barang pengganti'], ['DEBIT_NOTE', 'Nota debet (tidak diganti)']) },
    { key: 'debitNoteNo', label: 'No. nota debet' },
    { key: 'reason', label: 'Alasan (mis. hasil QC reject)', type: 'textarea', required: true },
  ],
  defaults: { claimType: 'REPLACE' },
  lines: [{
    key: 'lines', title: 'Barang dikembalikan',
    fields: [
      { key: 'itemId', label: 'Item', type: 'lookup', lookup: 'items' },
      { key: 'lotId', label: 'Lot', type: 'lookup', lookup: 'lots', rowFilters: byItem },
      { key: 'locationId', label: 'Dari lokasi', type: 'lookup', lookup: 'bins' },
      { key: 'qty', label: 'Qty', type: 'number' },
      { key: 'amount', label: 'Nilai', type: 'money', readOnly: true },
    ],
    display: [{ key: 'lotNo', label: 'Lot (status)' }],
  }],
  summary: [{ key: 'total', label: 'Nilai retur', type: 'money' }],
};
