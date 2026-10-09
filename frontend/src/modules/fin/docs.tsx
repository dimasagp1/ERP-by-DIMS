import { DataTable } from '../../components/DataTable';
import { api } from '../../api/client';
import { fmtRp } from '../../lib/format';
import { useToast } from '../../components/ui';
import type { Option } from '../master/types';
import type { Doc, DocConfig, ExtraProps } from '../docs/types';
import { ReadTable } from '../scm/docs';

const opt = (...pairs: [string, string][]): Option[] => pairs.map(([value, label]) => ({ value, label }));
const STATUS_COL = { key: 'status', label: 'Status', type: 'status' as const, sortable: false };
const DOCNO_COL = { key: 'docNo', label: 'No. Dokumen', mono: true };
const DATE_COL = { key: 'docDate', label: 'Tanggal', type: 'date' as const };
const DOC_DATE = { key: 'docDate', label: 'Tanggal', type: 'date' as const, required: true };
const PARTNER_COL = { key: 'partnerName', label: 'Mitra', sortable: false };
const PPH = opt(['PPH23-JASA', 'PPh 23 jasa 2%'], ['PPH42-SEWA', 'PPh 4(2) sewa 10%']);
const byPartner = (d: Doc): Record<string, string> => (d.partnerId ? { partner_id: String(d.partnerId) } : { partner_id: '-1' });

// ---------------------------------------------------------------- FIN-10 Faktur supplier

function ApPoFill({ doc, editing, setDoc }: ExtraProps) {
  const toast = useToast();
  if (!editing || doc.poId == null) return null;
  return (
    <div className="card row" style={{ padding: '10px 16px' }}>
      <span className="small muted" style={{ flex: 1 }}>Ambil baris PO yang sudah diterima (GR/BAST) tetapi belum difakturkan.</span>
      <button className="btn btn-sm" type="button" onClick={async () => {
        try {
          const rows = await api.get<Record<string, number>[]>('/fin/ap-invoices/po-lines', { poId: Number(doc.poId) });
          setDoc((d) => ({ ...d, lines: rows.filter((r) => r.received_qty > r.invoiced)
            .map((r) => ({ poLineId: r.po_line_id, qty: r.received_qty - r.invoiced, unitPrice: r.po_price })) }));
        } catch (e) {
          toast.error(e);
        }
      }}>Ambil baris diterima</button>
    </div>
  );
}

export const apInvoiceDoc: DocConfig = {
  docType: 'INV-AP', title: 'Faktur Supplier', endpoint: '/fin/ap-invoices',
  subtitle: (d) => d.partnerName as string | undefined,
  listColumns: [DOCNO_COL, DATE_COL, PARTNER_COL, { key: 'supplierInvoiceNo', label: 'No. faktur supplier', mono: true },
    { key: 'dueDate', label: 'Jatuh tempo', type: 'date' }, { key: 'total', label: 'Total', type: 'money' },
    { key: 'outstanding', label: 'Sisa hutang', type: 'money', sortable: false }, STATUS_COL],
  header: [
    { key: 'poId', label: 'PO (faktur barang/jasa — 3-way match)', type: 'lookup', lookup: 'po-received', help: 'Kosongkan untuk faktur biaya non-PO' },
    { key: 'partnerId', label: 'Supplier', type: 'lookup', lookup: 'partners', lookupFilters: { type: 'SUPPLIER,BOTH,EXPEDITION' }, show: (d) => d.poId == null },
    { key: 'supplierInvoiceNo', label: 'No. faktur supplier', required: true },
    DOC_DATE,
    { key: 'dueDate', label: 'Jatuh tempo', type: 'date', help: 'Kosong = tanggal + termin supplier' },
    { key: 'taxInvoiceNo', label: 'No. faktur pajak', help: 'Wajib bila ada PPN' },
    { key: 'pphTaxCode', label: 'PPh dipotong', type: 'select', options: PPH },
    { key: 'advancePaymentId', label: 'Potong uang muka', type: 'lookup', lookup: 'ap-advances', lookupFiltersFrom: byPartner },
    { key: 'advanceApplied', label: 'Nilai uang muka dipotong', type: 'money', show: (d) => d.advancePaymentId != null },
    { key: 'description', label: 'Uraian', full: true },
    { key: 'withPpn', label: 'Kena PPN (DPP nilai lain 11/12 × harga, tarif 12%)', type: 'bool' },
  ],
  defaults: { withPpn: true },
  lines: [{
    key: 'lines', title: 'Baris biaya', newRow: () => ({ qty: 1 }), show: (d) => d.poId == null,
    fields: [
      { key: 'accountId', label: 'Akun', type: 'lookup', lookup: 'accounts' },
      { key: 'costCenterId', label: 'Cost center', type: 'lookup', lookup: 'cost-centers' },
      { key: 'description', label: 'Keterangan' },
      { key: 'qty', label: 'Qty', type: 'number' },
      { key: 'unitPrice', label: 'Harga', type: 'money' },
      { key: 'amount', label: 'Jumlah', type: 'money', readOnly: true },
    ],
  }, {
    key: 'lines', title: 'Baris PO (qty ≤ diterima belum difakturkan; selisih harga > toleransi butuh approval Procurement)', show: (d) => d.poId != null,
    fields: [
      { key: 'poLineId', label: 'Baris PO', type: 'lookup', lookup: 'po-lines', lookupFiltersFrom: (d) => ({ po_id: String(d.poId ?? -1) }) },
      { key: 'qty', label: 'Qty faktur', type: 'number' },
      { key: 'unitPrice', label: 'Harga faktur (kosong = harga PO)', type: 'money' },
      { key: 'amount', label: 'Jumlah', type: 'money', readOnly: true },
    ],
    display: [{ key: 'poPrice', label: 'Harga PO', type: 'money' }, { key: 'priceVarPct', label: 'Selisih %', type: 'number' }],
  }],
  extra: ApPoFill,
  summary: [
    { key: 'subtotal', label: 'Subtotal (DPP)', type: 'money' },
    { key: 'ppnAmount', label: 'PPN masukan', type: 'money' },
    { key: 'total', label: 'Total faktur', type: 'money' },
    { key: 'pphAmount', label: 'PPh dipotong', type: 'money' },
    { key: 'payable', label: 'Hutang ke supplier', type: 'money' },
    { key: 'advanceApplied', label: 'Uang muka dipotong', type: 'money' },
    { key: 'paidAmount', label: 'Sudah dibayar', type: 'money' },
    { key: 'outstanding', label: 'Sisa hutang', type: 'money' },
  ],
};

// ---------------------------------------------------------------- FIN-11/12 Pembayaran & uang muka

const paymentBase: Omit<DocConfig, 'title' | 'listParams' | 'defaults' | 'header' | 'lines'> = {
  docType: 'PAY', endpoint: '/fin/payments',
  subtitle: (d) => d.partnerName as string | undefined,
  listColumns: [DOCNO_COL, DATE_COL, PARTNER_COL, { key: 'reference', label: 'Referensi' }, { key: 'amount', label: 'Nilai', type: 'money' },
    { key: 'reconciled', label: 'Rekonsiliasi', type: 'bool' }, STATUS_COL],
  summary: [{ key: 'amount', label: 'Total dibayar', type: 'money' }],
};
const payHeader = [
  { key: 'partnerId', label: 'Supplier', type: 'lookup' as const, lookup: 'partners', lookupFilters: { type: 'SUPPLIER,BOTH,EXPEDITION' }, required: true },
  DOC_DATE,
  { key: 'bankAccountId', label: 'Dari rekening', type: 'lookup' as const, lookup: 'bank-accounts', required: true },
  { key: 'method', label: 'Metode', type: 'select' as const, options: opt(['TRANSFER', 'Transfer'], ['CEK', 'Cek/Giro'], ['TUNAI', 'Tunai']), required: true },
  { key: 'reference', label: 'Referensi bank' },
  { key: 'description', label: 'Uraian', full: true },
];

export const paymentDoc: DocConfig = {
  ...paymentBase, title: 'Pembayaran Hutang', listParams: { kind: 'AP' }, defaults: { kind: 'AP', method: 'TRANSFER' },
  header: payHeader,
  lines: [{
    key: 'allocations', title: 'Faktur yang dibayar', addLabel: 'Tambah faktur',
    fields: [
      { key: 'invoiceId', label: 'Faktur (sisa hutang)', type: 'lookup', lookup: 'ap-open', lookupFiltersFrom: byPartner },
      { key: 'amount', label: 'Dibayar', type: 'money' },
    ],
    display: [{ key: 'supplierInvoiceNo', label: 'No. faktur supplier' }, { key: 'dueDate', label: 'Jatuh tempo', type: 'date' }],
  }],
};

export const advanceDoc: DocConfig = {
  ...paymentBase, title: 'Uang Muka Pembelian', listParams: { kind: 'ADVANCE' }, defaults: { kind: 'ADVANCE', method: 'TRANSFER' },
  header: [...payHeader, { key: 'amount', label: 'Nilai uang muka', type: 'money', required: true },
    { key: 'advanceUsed', label: 'Sudah dipotong ke faktur', type: 'money', readOnly: true }],
};

// ---------------------------------------------------------------- FIN-20 Faktur penjualan, FIN-21 Penerimaan

export const arInvoiceDoc: DocConfig = {
  docType: 'INV-AR', title: 'Faktur Penjualan', endpoint: '/fin/ar-invoices',
  subtitle: (d) => d.partnerName as string | undefined,
  listColumns: [DOCNO_COL, DATE_COL, PARTNER_COL, { key: 'dueDate', label: 'Jatuh tempo', type: 'date' }, { key: 'total', label: 'Total', type: 'money' },
    { key: 'outstanding', label: 'Sisa piutang', type: 'money', sortable: false }, { key: 'creditHold', label: 'Tertahan', type: 'bool' }, STATUS_COL],
  header: [
    { key: 'partnerId', label: 'Customer', type: 'lookup', lookup: 'partners', lookupFilters: { type: 'CUSTOMER,BOTH' }, required: true },
    DOC_DATE,
    { key: 'dueDate', label: 'Jatuh tempo', type: 'date', help: 'Kosong = tanggal + termin customer' },
    { key: 'taxInvoiceNo', label: 'No. faktur pajak' },
    { key: 'customerPo', label: 'No. PO customer' },
    { key: 'description', label: 'Uraian', full: true },
    { key: 'holdReason', label: 'Alasan ditahan (cek kredit FIN-22)', readOnly: true, full: true, show: (d) => Boolean(d.holdReason) },
    { key: 'withPpn', label: 'Kena PPN keluaran', type: 'bool' },
  ],
  defaults: { withPpn: true },
  lines: [{
    key: 'lines', title: 'Barang / jasa', newRow: () => ({ qty: 1, discountPct: 0 }),
    fields: [
      { key: 'itemId', label: 'Produk', type: 'lookup', lookup: 'items', lookupFilters: { type: 'FG' } },
      { key: 'description', label: 'Keterangan' },
      { key: 'qty', label: 'Qty', type: 'number' },
      { key: 'unitPrice', label: 'Harga', type: 'money' },
      { key: 'discountPct', label: 'Diskon %', type: 'number' },
      { key: 'amount', label: 'Jumlah', type: 'money', readOnly: true },
    ],
  }],
  summary: [
    { key: 'subtotal', label: 'Subtotal (DPP)', type: 'money' },
    { key: 'ppnAmount', label: 'PPN keluaran', type: 'money' },
    { key: 'total', label: 'Total faktur', type: 'money' },
    { key: 'receivedAmount', label: 'Sudah diterima', type: 'money' },
    { key: 'outstanding', label: 'Sisa piutang', type: 'money' },
  ],
};

export const receiptDoc: DocConfig = {
  docType: 'RCV', title: 'Penerimaan Pembayaran', endpoint: '/fin/receipts',
  subtitle: (d) => d.partnerName as string | undefined,
  listColumns: [DOCNO_COL, DATE_COL, PARTNER_COL, { key: 'reference', label: 'Referensi' }, { key: 'amount', label: 'Nilai', type: 'money' },
    { key: 'reconciled', label: 'Rekonsiliasi', type: 'bool' }, STATUS_COL],
  header: [
    { key: 'partnerId', label: 'Customer', type: 'lookup', lookup: 'partners', lookupFilters: { type: 'CUSTOMER,BOTH' }, required: true },
    DOC_DATE,
    { key: 'bankAccountId', label: 'Ke rekening', type: 'lookup', lookup: 'bank-accounts', required: true },
    { key: 'reference', label: 'Referensi bank' },
    { key: 'description', label: 'Uraian', full: true },
  ],
  lines: [{
    key: 'allocations', title: 'Pelunasan faktur', addLabel: 'Tambah faktur',
    fields: [
      { key: 'invoiceId', label: 'Faktur (sisa piutang)', type: 'lookup', lookup: 'ar-open', lookupFiltersFrom: byPartner },
      { key: 'amount', label: 'Dilunasi', type: 'money' },
    ],
    display: [{ key: 'dueDate', label: 'Jatuh tempo', type: 'date' }],
  }],
  summary: [{ key: 'amount', label: 'Total diterima', type: 'money' }],
};

// ---------------------------------------------------------------- FIN-30 Kas kecil & uang muka kerja

const KK_KINDS = opt(['EXPENSE', 'Pengeluaran kas kecil'], ['ADVANCE', 'Uang muka kerja'], ['SETTLEMENT', 'Pertanggungjawaban uang muka']);
export const cashVoucherDoc: DocConfig = {
  docType: 'KK', title: 'Kas Kecil & Uang Muka Kerja', endpoint: '/fin/cash-vouchers',
  subtitle: (d) => d.employeeName as string | undefined,
  listColumns: [DOCNO_COL, DATE_COL, { key: 'kind', label: 'Jenis', type: 'select', options: KK_KINDS }, { key: 'description', label: 'Uraian' },
    { key: 'amount', label: 'Nilai', type: 'money' }, { key: 'sourceDocNo', label: 'Sumber', mono: true }, STATUS_COL],
  listFilters: [{ key: 'kind', label: 'Jenis', options: KK_KINDS }],
  header: [
    { key: 'kind', label: 'Jenis', type: 'select', options: KK_KINDS, required: true },
    DOC_DATE,
    { key: 'cashAccountId', label: 'Kas/bank', type: 'lookup', lookup: 'bank-accounts', required: true },
    { key: 'employeeId', label: 'Karyawan penerima', type: 'lookup', lookup: 'employees', show: (d) => d.kind === 'ADVANCE' },
    { key: 'advanceId', label: 'Uang muka yang dipertanggungjawabkan', type: 'lookup', lookup: 'kk-advances', show: (d) => d.kind === 'SETTLEMENT' },
    { key: 'amount', label: 'Nilai uang muka', type: 'money', show: (d) => d.kind === 'ADVANCE' },
    { key: 'sourceDocNo', label: 'Dokumen sumber', readOnly: true, show: (d) => Boolean(d.sourceDocNo) },
    { key: 'description', label: 'Uraian', required: true, full: true },
  ],
  defaults: { kind: 'EXPENSE' },
  lines: [{
    key: 'lines', title: 'Rincian biaya', show: (d) => d.kind !== 'ADVANCE',
    fields: [
      { key: 'accountId', label: 'Akun', type: 'lookup', lookup: 'accounts' },
      { key: 'costCenterId', label: 'Cost center', type: 'lookup', lookup: 'cost-centers' },
      { key: 'description', label: 'Keterangan' },
      { key: 'amount', label: 'Nilai', type: 'money' },
    ],
  }],
  summary: [
    { key: 'amount', label: 'Total', type: 'money' },
    { key: 'advanceAmount', label: 'Nilai uang muka', type: 'money', show: (d) => d.kind === 'SETTLEMENT' },
    { key: 'outstanding', label: 'Belum dipertanggungjawabkan', type: 'money', show: (d) => d.kind === 'ADVANCE' },
  ],
};

// ---------------------------------------------------------------- FIN-40 Aset & penyusutan

export const assetDoc: DocConfig = {
  docType: 'AST', title: 'Kapitalisasi Aset Tetap', endpoint: '/fin/assets',
  subtitle: (d) => d.name as string | undefined,
  listColumns: [DOCNO_COL, { key: 'name', label: 'Aset' }, { key: 'categoryName', label: 'Kategori', sortable: false },
    { key: 'acquisitionCost', label: 'Harga perolehan', type: 'money' }, { key: 'accumulated', label: 'Akum. penyusutan', type: 'money' },
    { key: 'bookValue', label: 'Nilai buku', type: 'money', sortable: false }, { key: 'assetState', label: 'Keadaan' }, STATUS_COL],
  header: [
    { key: 'name', label: 'Nama aset', required: true },
    { key: 'categoryId', label: 'Kategori', type: 'lookup', lookup: 'asset-categories', required: true },
    DOC_DATE,
    { key: 'acquisitionCost', label: 'Harga perolehan', type: 'money', required: true },
    { key: 'residualValue', label: 'Nilai residu', type: 'money' },
    { key: 'usefulLifeMonths', label: 'Umur (bulan)', type: 'number', help: 'Kosong = sesuai kategori' },
    { key: 'method', label: 'Metode', type: 'select', options: opt(['SL', 'Garis lurus'], ['DDB', 'Saldo menurun ganda']) },
    { key: 'depreciationStart', label: 'Mulai disusutkan', type: 'date', help: 'Kosong = awal bulan berikutnya' },
    { key: 'costCenterId', label: 'Cost center pemakai', type: 'lookup', lookup: 'cost-centers', required: true },
    { key: 'creditAccountId', label: 'Akun lawan (GRNI/hutang/bank)', type: 'lookup', lookup: 'accounts' },
    { key: 'location', label: 'Lokasi' },
    { key: 'serialNo', label: 'No. seri' },
    { key: 'sourceRef', label: 'Referensi (BAST/GR/PO)' },
  ],
  summary: [
    { key: 'acquisitionCost', label: 'Harga perolehan', type: 'money' },
    { key: 'accumulated', label: 'Akumulasi penyusutan', type: 'money' },
    { key: 'bookValue', label: 'Nilai buku', type: 'money' },
    { key: 'monthlyDepreciation', label: 'Penyusutan bulan berikutnya', type: 'money' },
    { key: 'fiscalAccumulated', label: 'Akumulasi fiskal', type: 'money' },
  ],
};

function DepreciationLines({ doc }: ExtraProps) {
  const lines = (doc.lines as Record<string, unknown>[] | undefined) ?? [];
  if (doc.id == null) return null;
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="card-h">Rincian per aset</div>
      <DataTable rows={lines} rowKey={(r) => String(r.line_no)} empty="Belum dihitung — klik Hitung penyusutan"
        columns={[
          { key: 'doc_no', label: 'Aset', mono: true },
          { key: 'name', label: 'Nama' },
          { key: 'amount', label: 'Komersial', align: 'right', render: (r) => fmtRp(r.amount as number) },
          { key: 'fiscal_amount', label: 'Fiskal', align: 'right', render: (r) => fmtRp(r.fiscal_amount as number) },
          { key: 'book_value_after', label: 'Nilai buku sesudah', align: 'right', render: (r) => fmtRp(r.book_value_after as number) },
        ]} />
    </div>
  );
}

export const depreciationDoc: DocConfig = {
  docType: 'DEP', title: 'Penyusutan Bulanan', endpoint: '/fin/depreciations',
  listColumns: [DOCNO_COL, { key: 'period', label: 'Periode', mono: true }, { key: 'assetCount', label: 'Aset', type: 'number' },
    { key: 'totalAmount', label: 'Komersial', type: 'money' }, { key: 'totalFiscal', label: 'Fiskal', type: 'money' }, STATUS_COL],
  header: [{ key: 'period', label: 'Periode (YYYYMM)', required: true }],
  defaults: { period: new Date().toISOString().slice(0, 7).replace('-', '') },
  summary: [
    { key: 'assetCount', label: 'Aset', type: 'number' },
    { key: 'totalAmount', label: 'Penyusutan komersial', type: 'money' },
    { key: 'totalFiscal', label: 'Penyusutan fiskal', type: 'money' },
  ],
  actions: [{
    label: 'Hitung penyusutan', primary: true,
    show: (_d, m) => m.status === 'DRAFT' || m.status === 'REJECTED',
    run: (d) => api.post(`/fin/depreciations/${d.id}/calculate`),
  }],
  extra: DepreciationLines,
};

// ---------------------------------------------------------------- FIN-50 Anggaran

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];
export const budgetDoc: DocConfig = {
  docType: 'BGT', title: 'Anggaran', endpoint: '/fin/budgets',
  listColumns: [DOCNO_COL, { key: 'year', label: 'Tahun', mono: true }, { key: 'kind', label: 'Jenis' }, { key: 'revision', label: 'Revisi', type: 'number' },
    { key: 'description', label: 'Uraian' }, { key: 'total', label: 'Total', type: 'money' }, STATUS_COL],
  header: [
    { key: 'year', label: 'Tahun', type: 'number', required: true },
    { key: 'kind', label: 'Jenis', type: 'select', options: opt(['OPEX', 'OPEX'], ['CAPEX', 'CAPEX']), required: true },
    { key: 'revision', label: 'Revisi', type: 'number', readOnly: true },
    { key: 'description', label: 'Uraian', required: true, full: true },
  ],
  defaults: { year: new Date().getFullYear(), kind: 'OPEX' },
  lines: [{
    key: 'lines', title: 'Anggaran per cost center × akun × bulan (Rp)',
    fields: [
      { key: 'costCenterId', label: 'Cost center', type: 'lookup', lookup: 'cost-centers' },
      { key: 'accountId', label: 'Akun', type: 'lookup', lookup: 'accounts', lookupFilters: { type: 'EXPENSE' } },
      ...MONTHS.map((m, i) => ({ key: `m${String(i + 1).padStart(2, '0')}`, label: m, type: 'money' as const })),
      { key: 'total', label: 'Total', type: 'money', readOnly: true },
    ],
  }],
  summary: [{ key: 'total', label: 'Total anggaran', type: 'money' }],
};

// ---------------------------------------------------------------- FIN-55 Alokasi overhead

function OverheadLines({ doc }: ExtraProps) {
  if (doc.id == null) return null;
  return <ReadTable title="Alokasi (hasil hitung)" rows={(doc.lines as Doc[]) ?? []}
    columns={[['Dari cost center', 'sourceCc'], ['Akun', 'account'], ['Ke lini', 'targetCc'], ['Dasar', 'basisQty', 'number'],
      ['Porsi %', 'sharePct', 'number'], ['Nilai', 'amount', 'money']]} />;
}

export const overheadDoc: DocConfig = {
  docType: 'OHA', title: 'Alokasi Overhead', endpoint: '/fin/overhead-allocations',
  listColumns: [DOCNO_COL, DATE_COL, { key: 'period', label: 'Periode', mono: true }, { key: 'basis', label: 'Dasar' },
    { key: 'total', label: 'Dialokasikan', type: 'money' }, STATUS_COL],
  header: [
    { key: 'period', label: 'Periode (YYYYMM)', required: true },
    DOC_DATE,
    { key: 'basis', label: 'Dasar alokasi', type: 'select', options: opt(['LABOR_HOURS', 'Jam kerja lini (PRE-12)'], ['OUTPUT', 'Qty hasil produksi (PRE-08)']) },
    { key: 'notes', label: 'Catatan', type: 'textarea' },
  ],
  defaults: { basis: 'LABOR_HOURS' },
  summary: [{ key: 'total', label: 'Total dialokasikan', type: 'money' }],
  extra: OverheadLines,
  actions: [{
    label: 'Hitung alokasi', primary: true, show: (_d, m) => m.status === 'DRAFT' || m.status === 'REJECTED',
    run: (d) => api.post(`/fin/overhead-allocations/${d.id}/compute`),
  }],
};
