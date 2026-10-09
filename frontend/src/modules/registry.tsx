import type { ComponentType } from 'react';
import { ApprovalInbox } from '../pages/ApprovalInbox';
import type { DocConfig, MenuTab } from './docs/types';
import { BatchCostPage, StandardCostPage, ValuationPage } from './fin/costing';
import { advanceDoc, apInvoiceDoc, arInvoiceDoc, assetDoc, budgetDoc, cashVoucherDoc, depreciationDoc, overheadDoc, paymentDoc, receiptDoc as finReceiptDoc } from './fin/docs';
import { JournalForm, JournalList } from './fin/Journals';
import { LedgerPage, PeriodPage } from './fin/Ledger';
import { ApAgingPage, ArAgingPage, BankReconPage, BudgetControlPage, CashForecastPage, ManagementPage, StatementsPage } from './fin/reports';
import { FiscalPage, PphPage, PpnPage, SptPage } from './fin/tax';
import { leaveDoc, loanDoc, onboardingDoc, overtimeDoc, payrollDoc, sarmutInputDoc, tripDoc } from './hc/docs';
import { AttendancePage, LeaveBalancePage, MySlipsPage, SalaryPage, SarmutPage } from './hc/pages';
import { MASTER_MENUS } from './master/configs';
import * as m1 from './master/m1';
import * as m2 from './master/m2';
import { MasterPage } from './master/MasterPage';
import type { ResourceDef } from './master/types';
import { outputDoc, workOrderDoc } from './pre/docs';
import { LaborPage } from './pre/pages';
import { bastDoc, landedCostDoc, purchaseOrderDoc, requisitionDoc, rfqDoc, supplierReturnDoc } from './prc/docs';
import { adjustmentDoc, countDoc, customerReturnDoc, deliveryDoc, goodsIssueDoc, receiptDoc as scmReceiptDoc, salesOrderDoc, scrapDoc, transferDoc } from './scm/docs';
import { CapacityPage, ExpiryPage, FgReceiptPage, ForecastPage, LotStatusPage, MpsPage, MrpPage, PickingPage, PlanActualPage, PutawayPage, ReleasePage, ScmReportPage, StockCardPage, TracePage } from './scm/pages';
import { AuditTrailPage, IntegrationLogPage } from './sys/SysPages';

import * as qmsDocs from './qms/docs';
import * as qmsPages from './qms/pages';

export interface MenuImpl {
  /** Halaman tunggal (M0). */
  list?: ComponentType;
  /** Formulir dokumen M0 (rute /m/KODE/:id dan /m/KODE/new). */
  form?: ComponentType;
  /** Tab halaman menu: daftar dokumen generik, halaman khusus, atau master. */
  tabs?: MenuTab[];
}

const doc = (cfg: DocConfig, label = cfg.title): MenuTab => ({ key: cfg.docType, label, doc: cfg });
const page = (key: string, label: string, component: ComponentType): MenuTab => ({ key, label, component });
const master = (res: ResourceDef, label = res.title): MenuTab => ({ key: res.key, label, component: () => <MasterPage resources={[res]} /> });
const mine = (cfg: DocConfig): DocConfig => ({ ...cfg, mine: true });

const impl: Record<string, MenuImpl> = {
  'FIN-03': { list: JournalList, form: JournalForm },
  'FIN-04': { list: LedgerPage },
  'FIN-70': { list: PeriodPage },
  'SYS-13': { list: IntegrationLogPage },
  'SYS-14': { list: AuditTrailPage },
  'ESS-10': { list: ApprovalInbox },

  // ---------------------------------------------------------------- FIN (M1 & M2)
  'FIN-10': { tabs: [doc(apInvoiceDoc)] },
  'FIN-11': { tabs: [doc(advanceDoc)] },
  'FIN-12': { tabs: [doc(paymentDoc), page('ap-aging', 'Jadwal & umur hutang', ApAgingPage)] },
  'FIN-20': { tabs: [doc(arInvoiceDoc)] },
  'FIN-21': { tabs: [doc(finReceiptDoc)] },
  'FIN-22': { tabs: [page('ar-aging', 'Umur piutang', ArAgingPage)] },
  'FIN-30': { tabs: [doc(cashVoucherDoc)] },
  'FIN-31': { tabs: [page('recon', 'Rekonsiliasi', BankReconPage), master(m1.bankAccounts)] },
  'FIN-32': { tabs: [page('forecast', 'Proyeksi 13 minggu', CashForecastPage)] },
  'FIN-40': { tabs: [doc(assetDoc), doc(depreciationDoc), master(m1.assetCategories)] },
  'FIN-50': { tabs: [doc(budgetDoc)] },
  'FIN-51': { tabs: [page('control', 'Anggaran vs realisasi', BudgetControlPage)] },
  'FIN-52': { tabs: [page('standard-cost', 'Standard Cost', StandardCostPage)] },
  'FIN-53': { tabs: [page('batch-cost', 'Biaya Aktual Batch', BatchCostPage)] },
  'FIN-54': { tabs: [page('valuation', 'Valuasi & HPP', ValuationPage)] },
  'FIN-55': { tabs: [doc(overheadDoc)] },
  'FIN-60': { tabs: [page('ppn', 'PPN / e-Faktur', PpnPage)] },
  'FIN-61': { tabs: [page('pph', 'PPh 21/23/4(2)/22', PphPage)] },
  'FIN-62': { tabs: [page('spt', 'Bukti Potong & SPT', SptPage)] },
  'FIN-63': { tabs: [page('fiscal', 'Rekonsiliasi Fiskal', FiscalPage)] },
  'FIN-71': { tabs: [page('statements', 'Laporan keuangan', StatementsPage)] },
  'FIN-72': { tabs: [page('management', 'Laporan manajemen', ManagementPage)] },

  // ---------------------------------------------------------------- HC (M1)
  'HC-05': { tabs: [doc(onboardingDoc)] },
  'HC-06': { tabs: [master(m1.contracts)] },
  'HC-07': { tabs: [page('attendance', 'Absensi', AttendancePage)] },
  'HC-08': { tabs: [doc(leaveDoc), doc(overtimeDoc), doc(tripDoc), master(m1.leaveTypes), master(m1.leaveEntitlements)] },
  'HC-09': { tabs: [doc(payrollDoc), page('salaries', 'Gaji karyawan', SalaryPage), master(m1.salaryComponents), master(m1.ptkp), master(m1.pph21Ter)] },
  'HC-10': { tabs: [master(m1.payrollParams)] },
  'HC-12': { tabs: [master(m1.qualifications)] },
  'HC-13': { tabs: [page('sarmut', 'Capaian SARMUT', SarmutPage), doc(sarmutInputDoc), master(m1.sarmutKpis)] },
  'HC-14': { tabs: [doc(loanDoc)] },

  // ---------------------------------------------------------------- PRE (M1)
  'PRE-02': { tabs: [doc(workOrderDoc), master(m1.lines), master(m1.productParams)] },
  'PRE-08': { tabs: [doc(outputDoc), master(m1.rejectReasons)] },
  'PRE-12': { tabs: [page('labor', 'Jam kerja', LaborPage)] },

  // ---------------------------------------------------------------- PRC (M2)
  'PRC-02': { tabs: [doc(requisitionDoc)] },
  'PRC-03': { tabs: [master(m2.supplierProfiles), master(m2.asls)] },
  'PRC-05': { tabs: [doc(rfqDoc)] },
  'PRC-06': { tabs: [doc(purchaseOrderDoc)] },
  'PRC-08': { tabs: [master(m2.priceLists)] },
  'PRC-10': { tabs: [doc(landedCostDoc)] },
  'PRC-30': { tabs: [doc(supplierReturnDoc)] },
  'PRC-40': { tabs: [doc(bastDoc)] },

  // ---------------------------------------------------------------- SCM (M2)
  'SCM-02': { tabs: [doc(salesOrderDoc)] },
  'SCM-03': { tabs: [page('forecast', 'Forecast', ForecastPage)] },
  'SCM-04': { tabs: [page('mps', 'MPS', MpsPage)] },
  'SCM-05': { tabs: [page('mrp', 'MRP', MrpPage)] },
  'SCM-06': { tabs: [page('capacity', 'Kapasitas', CapacityPage)] },
  'SCM-10': { tabs: [page('release', 'Rilis WO', ReleasePage)] },
  'SCM-11': { tabs: [page('plan-actual', 'Rencana vs Aktual', PlanActualPage)] },
  'SCM-20': { tabs: [doc(scmReceiptDoc)] },
  'SCM-21': { tabs: [page('lot-status', 'Status Lot & Karantina', LotStatusPage)] },
  'SCM-22': { tabs: [page('putaway', 'Putaway', PutawayPage)] },
  'SCM-23': { tabs: [page('picking', 'Picking FEFO', PickingPage)] },
  'SCM-24': { tabs: [page('fg-receipt', 'Terima Barang Jadi', FgReceiptPage)] },
  'SCM-25': { tabs: [doc(transferDoc)] },
  'SCM-26': { tabs: [doc(goodsIssueDoc)] },
  'SCM-27': { tabs: [doc(deliveryDoc)] },
  'SCM-30': { tabs: [doc(customerReturnDoc)] },
  'SCM-40': { tabs: [page('stock-card', 'Kartu Stok', StockCardPage)] },
  'SCM-41': { tabs: [doc(countDoc)] },
  'SCM-42': { tabs: [doc(adjustmentDoc)] },
  'SCM-43': { tabs: [master(m2.stockParams)] },
  'SCM-44': { tabs: [page('expiry', 'Kedaluwarsa & Slow Moving', ExpiryPage)] },
  'SCM-45': { tabs: [page('trace', 'Penelusuran Lot', TracePage)] },
  'SCM-46': { tabs: [doc(scrapDoc)] },
  'SCM-90': { tabs: [page('report', 'Laporan Supply Chain', ScmReportPage)] },


    // ---------------------------------------------------------------- QMS (M3)
  'QMS-01': { tabs: [doc(qmsDocs.qmsDocumentDoc)] },
  'QMS-02': { tabs: [doc(qmsDocs.changeControlDoc)] },
  'QMS-03': { tabs: [doc(qmsDocs.deviationDoc)] },
  'QMS-04': { tabs: [doc(qmsDocs.capaDoc)] },
  'QMS-05': { tabs: [doc(qmsDocs.complaintDoc)] },
  'QMS-06': { tabs: [page('audit', 'Audit Internal & Eksternal', qmsPages.AuditPage)] },
  'QMS-07': { tabs: [page('risk', 'Manajemen Risiko Mutu', qmsPages.RiskPage)] },
  'QMS-08': { tabs: [page('specs', 'Spesifikasi & Metode Uji', qmsPages.SpecsPage)] },
  'QMS-09': { tabs: [doc(qmsDocs.batchReleaseDoc)] },
  'QMS-10': { tabs: [page('sampling', 'Pengambilan Sampel', qmsPages.SamplingPage)] },
  'QMS-11': { tabs: [page('test-result', 'Hasil Pengujian', qmsPages.TestResultPage)] },
  'QMS-12': { tabs: [page('ipc', 'Review IPC', qmsPages.IpcPage)] },
  'QMS-13': { tabs: [page('coa', 'Certificate of Analysis (CoA)', qmsPages.CoaPage)] },
  'QMS-14': { tabs: [page('instrument', 'Instrumen & Kalibrasi', qmsPages.InstrumentPage)] },
  'QMS-15': { tabs: [doc(qmsDocs.stabilityStudyDoc)] },
  // ---------------------------------------------------------------- ESS (M1 & M2)
  'ESS-01': { tabs: [doc(mine(leaveDoc), 'Pengajuan saya'), page('balance', 'Saldo cuti', LeaveBalancePage)] },
  'ESS-02': { tabs: [doc(mine(overtimeDoc), 'Lembur saya')] },
  'ESS-03': { tabs: [page('slips', 'Slip gaji', MySlipsPage)] },
  'ESS-04': { tabs: [doc(mine(requisitionDoc), 'Permintaan saya')] },
  'ESS-05': { tabs: [page('atk', 'Permintaan ATK', () => <div style={{padding: 20}}>Segera hadir</div>)] },
  'ESS-06': { tabs: [page('rapat', 'Booking Ruang Rapat', () => <div style={{padding: 20}}>Segera hadir</div>)] },
  'ESS-07': { tabs: [page('kendaraan', 'Booking Kendaraan', () => <div style={{padding: 20}}>Segera hadir</div>)] },
  'ESS-08': { tabs: [page('kerusakan', 'Lapor Kerusakan', () => <div style={{padding: 20}}>Segera hadir</div>)] },
  'ESS-09': { tabs: [doc(mine(tripDoc), 'Perjalanan dinas saya')] },
};

Object.entries(MASTER_MENUS).forEach(([code, resources]) => {
  impl[code] = { list: () => <MasterPage resources={resources} /> };
});

/** Menu yang sudah dibangun. Menu lain menampilkan halaman rencana fase (PLANNING.md). */
export const MENU_IMPL = impl;

/** Menu utama tiap jenis dokumen (untuk tautan dari kotak approval, pencarian, dan dokumen terkait). */
export const DOC_MENU: Record<string, string> = (() => {
  const out: Record<string, string> = { JV: 'FIN-03' };
  Object.entries(impl).forEach(([code, m]) => {
    if (code.startsWith('ESS-')) return;
    m.tabs?.forEach((t) => { if (t.doc && !out[t.doc.docType]) out[t.doc.docType] = code; });
  });
  return out;
})();
