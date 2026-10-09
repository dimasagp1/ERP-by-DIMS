import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { DataTable } from '../../components/DataTable';
import { Modal, errorText, useToast } from '../../components/ui';
import { fmtNumber, fmtRp } from '../../lib/format';
import type { Option } from '../master/types';
import type { Doc, DocConfig, ExtraProps } from '../docs/types';

const opt = (...pairs: [string, string][]): Option[] => pairs.map(([value, label]) => ({ value, label }));
const STATUS_COL = { key: 'status', label: 'Status', type: 'status' as const, sortable: false };
const DOCNO_COL = { key: 'docNo', label: 'No. Dokumen', mono: true };
const DATE_COL = { key: 'docDate', label: 'Tanggal', type: 'date' as const };
const DOC_DATE = { key: 'docDate', label: 'Tanggal dokumen', type: 'date' as const };
const thisPeriod = () => new Date().toISOString().slice(0, 7).replace('-', '');

// ---------------------------------------------------------------- Cuti & izin (HC-08 / ESS-01)

export const leaveDoc: DocConfig = {
  docType: 'LV', title: 'Cuti & Izin', endpoint: '/hc/leaves',
  subtitle: (d) => d.employeeName as string | undefined,
  listColumns: [DOCNO_COL, { key: 'employeeName', label: 'Karyawan', sortable: false }, { key: 'leaveTypeName', label: 'Jenis', sortable: false },
    { key: 'startDate', label: 'Mulai', type: 'date' }, { key: 'endDate', label: 'Selesai', type: 'date' }, { key: 'days', label: 'Hari', type: 'number' }, STATUS_COL],
  header: [
    { key: 'employeeId', label: 'Karyawan', type: 'lookup', lookup: 'employees', staffOnly: true, help: 'Kosongkan untuk diri sendiri' },
    { key: 'leaveTypeId', label: 'Jenis cuti/izin', type: 'lookup', lookup: 'leave-types', required: true },
    { key: 'startDate', label: 'Mulai', type: 'date', required: true },
    { key: 'endDate', label: 'Selesai', type: 'date', required: true },
    { key: 'days', label: 'Hari kerja', type: 'number', readOnly: true },
    { key: 'delegateEmployeeId', label: 'Pengganti selama cuti', type: 'lookup', lookup: 'employees' },
    { key: 'reason', label: 'Keperluan', type: 'textarea' },
  ],
};

// ---------------------------------------------------------------- Lembur (HC-08 / ESS-02)

const DAY_TYPES = opt(['WORKDAY', 'Hari kerja'], ['RESTDAY', 'Hari istirahat'], ['HOLIDAY', 'Hari libur resmi']);
export const overtimeDoc: DocConfig = {
  docType: 'OT', title: 'Surat Perintah Lembur', endpoint: '/hc/overtimes',
  listColumns: [DOCNO_COL, { key: 'workDate', label: 'Tanggal lembur', type: 'date' }, { key: 'dayType', label: 'Hari', type: 'select', options: DAY_TYPES },
    { key: 'totalHours', label: 'Total jam', type: 'number' }, { key: 'reason', label: 'Alasan' }, { key: 'createdByName', label: 'Dibuat oleh', sortable: false }, STATUS_COL],
  header: [
    { key: 'workDate', label: 'Tanggal lembur', type: 'date', required: true },
    { key: 'dayType', label: 'Jenis hari', type: 'select', options: DAY_TYPES, required: true, help: 'Menentukan pengali upah lembur (PP 35/2021)' },
    { key: 'reference', label: 'Referensi (mis. nomor WO)' },
    { key: 'totalHours', label: 'Total jam', type: 'number', readOnly: true },
    { key: 'reason', label: 'Alasan lembur', type: 'textarea', required: true },
  ],
  defaults: { dayType: 'WORKDAY' },
  lines: [{
    key: 'lines', title: 'Karyawan', addLabel: 'Tambah karyawan',
    newRow: () => ({ startTime: '17:00', endTime: '19:00' }),
    fields: [
      { key: 'employeeId', label: 'Karyawan', type: 'lookup', lookup: 'employees', staffOnly: true },
      { key: 'startTime', label: 'Mulai', type: 'time' },
      { key: 'endTime', label: 'Selesai', type: 'time' },
      { key: 'hours', label: 'Jam', type: 'number', readOnly: true },
      { key: 'actualHours', label: 'Jam aktual', type: 'number' },
    ],
  }],
};

// ---------------------------------------------------------------- SPD (HC-08 / ESS-09)

export const tripDoc: DocConfig = {
  docType: 'SPD', title: 'Perjalanan Dinas', endpoint: '/hc/trips',
  subtitle: (d) => d.employeeName as string | undefined,
  listColumns: [DOCNO_COL, { key: 'employeeName', label: 'Karyawan', sortable: false }, { key: 'destination', label: 'Tujuan' },
    { key: 'startDate', label: 'Berangkat', type: 'date' }, { key: 'endDate', label: 'Kembali', type: 'date' },
    { key: 'advanceAmount', label: 'Uang muka', type: 'money' }, { key: 'advanceDocNo', label: 'Voucher FIN-30', mono: true }, STATUS_COL],
  header: [
    { key: 'employeeId', label: 'Karyawan', type: 'lookup', lookup: 'employees', staffOnly: true, help: 'Kosongkan untuk diri sendiri' },
    { key: 'destination', label: 'Tujuan', required: true },
    { key: 'startDate', label: 'Berangkat', type: 'date', required: true },
    { key: 'endDate', label: 'Kembali', type: 'date', required: true },
    { key: 'transport', label: 'Transportasi', type: 'select', options: opt(['DARAT', 'Darat'], ['UDARA', 'Udara'], ['LAUT', 'Laut']), required: true },
    { key: 'perDiem', label: 'Uang harian (Rp)', type: 'money' },
    { key: 'advanceAmount', label: 'Uang muka diminta (Rp)', type: 'money', help: 'Setelah disetujui menjadi draft uang muka kerja di FIN-30' },
    { key: 'costCenterId', label: 'Cost center', type: 'lookup', lookup: 'cost-centers', staffOnly: true },
    { key: 'advanceDocNo', label: 'Voucher uang muka (FIN-30)', readOnly: true },
    { key: 'abroad', label: 'Perjalanan luar negeri (butuh approval Manager HC)', type: 'bool' },
    { key: 'purpose', label: 'Keperluan', type: 'textarea', required: true },
  ],
  defaults: { transport: 'DARAT', perDiem: 0, advanceAmount: 0 },
};

// ---------------------------------------------------------------- Payroll (HC-09)

interface SlipRow { id: number; nik: string; name: string; costCenter: string | null; ptkpStatus: string; terCategory: string; terRate: number;
  workDays: number; presentDays: number; absentDays: number; overtimeHours: number; gross: number; taxableGross: number;
  totalDeduction: number; net: number; employerCost: number; pph21: number; bankName: string | null; bankAccountNo: string | null }
interface SlipDetail { slip: SlipRow & { period: string; runDocNo: string }; lines: { code: string; name: string; kind: string; quantity: number | null; amount: number }[] }

export function SlipView({ slipId, onClose }: { slipId: number; onClose: () => void }) {
  const q = useQuery({ queryKey: ['slip', slipId], queryFn: () => api.get<SlipDetail>(`/hc/payroll-slips/${slipId}`) });
  const s = q.data?.slip;
  const group = (kind: string) => q.data?.lines.filter((l) => l.kind === kind) ?? [];
  const section = (title: string, kind: string) => (
    <div>
      <div className="lbl" style={{ margin: '10px 0 4px' }}>{title}</div>
      {group(kind).map((l) => (
        <div key={l.code} className="row" style={{ justifyContent: 'space-between', fontSize: 13, padding: '3px 0' }}>
          <span>{l.name}{l.quantity != null ? <span className="muted"> ({fmtNumber(l.quantity)})</span> : null}</span>
          <span className="mono">{fmtRp(l.amount)}</span>
        </div>
      ))}
    </div>
  );
  return (
    <Modal title={s ? `Slip gaji ${s.period} · ${s.name}` : 'Slip gaji'} onClose={onClose} width={560}
      footer={<button className="btn" type="button" onClick={() => window.print()}>Cetak</button>}>
      {q.error && <div className="alert alert-err">{errorText(q.error)}</div>}
      {s && <>
        <div className="small muted">{s.nik} · {s.runDocNo} · PTKP {s.ptkpStatus} (TER {s.terCategory}) · hadir {s.presentDays}/{s.workDays} hari</div>
        {section('Pendapatan', 'EARNING')}
        {section('Potongan', 'DEDUCTION')}
        <div className="row" style={{ justifyContent: 'space-between', borderTop: '1px solid var(--line)', paddingTop: 10, fontWeight: 600 }}>
          <span>Gaji bersih</span><span className="mono">{fmtRp(s.net)}</span>
        </div>
        <div className="small muted">Transfer ke {s.bankName ?? '-'} {s.bankAccountNo ?? ''}</div>
        {section('Ditanggung perusahaan (bukan potongan)', 'EMPLOYER')}
      </>}
    </Modal>
  );
}

function PayrollExtra({ doc, meta }: ExtraProps) {
  const [slip, setSlip] = useState<number | null>(null);
  const q = useQuery({
    queryKey: ['payroll-slips', doc.id, meta?.version, doc.calculatedAt],
    queryFn: () => api.get<SlipRow[]>(`/hc/payroll-runs/${doc.id}/slips`),
    enabled: doc.id != null,
    retry: false,
  });
  const warnings = (doc.warnings as string | null)?.split('\n') ?? [];
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="card-h">Slip per karyawan
        <span className="small muted" style={{ fontWeight: 400 }}>{doc.calculatedAt ? `Dihitung ${String(doc.calculatedAt).slice(0, 16).replace('T', ' ')}` : 'Belum dihitung — klik "Hitung payroll"'}</span>
      </div>
      {warnings.length > 0 && (
        <div className="alert alert-warn" style={{ margin: 12, whiteSpace: 'pre-wrap' }}>{warnings.join('\n')}</div>
      )}
      {q.error ? <div className="alert alert-info" style={{ margin: 12 }}>{errorText(q.error)}</div> : (
        <DataTable rows={q.data ?? []} rowKey={(r) => r.id} onRowClick={(r) => setSlip(r.id)} empty={q.isLoading ? 'Memuat…' : 'Belum ada slip'}
          columns={[
            { key: 'nik', label: 'NIK', mono: true },
            { key: 'name', label: 'Nama' },
            { key: 'costCenter', label: 'CC', mono: true },
            { key: 'presentDays', label: 'Hadir', align: 'right', render: (r) => `${r.presentDays}/${r.workDays}` },
            { key: 'overtimeHours', label: 'Lembur (jam)', align: 'right', render: (r) => fmtNumber(r.overtimeHours) },
            { key: 'gross', label: 'Bruto', align: 'right', render: (r) => fmtRp(r.gross) },
            { key: 'pph21', label: 'PPh 21', align: 'right', render: (r) => fmtRp(r.pph21) },
            { key: 'totalDeduction', label: 'Potongan', align: 'right', render: (r) => fmtRp(r.totalDeduction) },
            { key: 'net', label: 'Bersih', align: 'right', render: (r) => <b>{fmtRp(r.net)}</b> },
          ]} />
      )}
      {slip != null && <SlipView slipId={slip} onClose={() => setSlip(null)} />}
    </div>
  );
}

export const payrollDoc: DocConfig = {
  docType: 'PAYR', title: 'Payroll', endpoint: '/hc/payroll-runs',
  listColumns: [DOCNO_COL, { key: 'period', label: 'Periode', mono: true }, { key: 'employeeCount', label: 'Karyawan', type: 'number' },
    { key: 'totalGross', label: 'Bruto', type: 'money' }, { key: 'totalNet', label: 'Bersih', type: 'money' }, STATUS_COL],
  header: [
    DOC_DATE,
    { key: 'period', label: 'Periode (YYYYMM)', required: true, help: 'Absensi periode ini harus sudah dikunci di HC-07' },
    { key: 'description', label: 'Uraian', full: true },
  ],
  defaults: { period: thisPeriod() },
  summary: [
    { key: 'employeeCount', label: 'Karyawan', type: 'number' },
    { key: 'totalGross', label: 'Total bruto', type: 'money' },
    { key: 'totalDeduction', label: 'Total potongan', type: 'money' },
    { key: 'totalPph21', label: 'PPh 21', type: 'money' },
    { key: 'totalEmployer', label: 'Tanggungan perusahaan (BPJS)', type: 'money' },
    { key: 'totalNet', label: 'Total gaji bersih', type: 'money' },
  ],
  actions: [{
    label: 'Hitung payroll', primary: true,
    show: (_d, m) => m.status === 'DRAFT' || m.status === 'REJECTED',
    run: (d) => api.post(`/hc/payroll-runs/${d.id}/calculate`),
  }],
  extra: PayrollExtra,
};

// ---------------------------------------------------------------- Pinjaman (HC-14)

export const loanDoc: DocConfig = {
  docType: 'LOAN', title: 'Pinjaman & Kasbon', endpoint: '/hc/loans',
  subtitle: (d) => d.employeeName as string | undefined,
  listColumns: [DOCNO_COL, { key: 'employeeName', label: 'Karyawan', sortable: false }, { key: 'kind', label: 'Jenis' },
    { key: 'principal', label: 'Pokok', type: 'money' }, { key: 'installments', label: 'Cicilan', type: 'number' },
    { key: 'outstanding', label: 'Sisa', type: 'money', sortable: false }, STATUS_COL],
  header: [
    DOC_DATE,
    { key: 'employeeId', label: 'Karyawan', type: 'lookup', lookup: 'employees', required: true },
    { key: 'kind', label: 'Jenis', type: 'select', options: opt(['PINJAMAN', 'Pinjaman'], ['KASBON', 'Kasbon (1× potong)']), required: true },
    { key: 'principal', label: 'Nilai (Rp)', type: 'money', required: true },
    { key: 'installments', label: 'Jumlah cicilan', type: 'number', required: true },
    { key: 'startPeriod', label: 'Mulai potong (YYYYMM)', required: true },
    { key: 'installmentAmount', label: 'Cicilan per bulan', type: 'money', readOnly: true },
    { key: 'repaidAmount', label: 'Sudah dibayar', type: 'money', readOnly: true },
    { key: 'purpose', label: 'Keperluan', type: 'textarea' },
  ],
  defaults: { kind: 'PINJAMAN', installments: 6, startPeriod: thisPeriod() },
};

// ---------------------------------------------------------------- On/offboarding (HC-05)

interface Task { id: number; code: string; label: string; ownerApp: string; done: boolean; doneAt: string | null; note: string | null }

function ChecklistExtra({ doc, reload }: ExtraProps) {
  const toast = useToast();
  const tasks = (doc.tasks as Task[] | undefined) ?? [];
  if (doc.id == null) return null;
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="card-h">Checklist {String(doc.progress ?? '')}<span className="small muted" style={{ fontWeight: 400 }}>Dicentang oleh departemen pemilik tugas; posting setelah semua selesai</span></div>
      {tasks.map((t) => (
        <label key={t.id} className="check" style={{ padding: '10px 16px' }}>
          <input type="checkbox" checked={t.done} onChange={async (e) => {
            try {
              await api.put(`/hc/onboardings/${doc.id}/tasks/${t.id}`, { done: e.target.checked, note: t.note });
              reload();
            } catch (err) {
              toast.error(err);
            }
          }} />
          <span style={{ flex: 1 }}>{t.label}</span>
          <span className="mono small muted">{t.ownerApp}</span>
        </label>
      ))}
    </div>
  );
}

export const onboardingDoc: DocConfig = {
  docType: 'OB', title: 'On/Offboarding', endpoint: '/hc/onboardings',
  subtitle: (d) => d.employeeName as string | undefined,
  listColumns: [DOCNO_COL, { key: 'employeeName', label: 'Karyawan', sortable: false }, { key: 'kind', label: 'Jenis' },
    { key: 'effectiveDate', label: 'Efektif', type: 'date' }, { key: 'progress', label: 'Checklist', sortable: false }, STATUS_COL],
  header: [
    { key: 'employeeId', label: 'Karyawan', type: 'lookup', lookup: 'employees', required: true },
    { key: 'kind', label: 'Jenis', type: 'select', options: opt(['ONBOARD', 'Onboarding'], ['OFFBOARD', 'Offboarding']), required: true },
    { key: 'effectiveDate', label: 'Tanggal efektif', type: 'date', required: true, help: 'Offboarding: akun ERP dinonaktifkan otomatis pada tanggal ini' },
    { key: 'reason', label: 'Keterangan', type: 'textarea' },
  ],
  defaults: { kind: 'ONBOARD' },
  extra: ChecklistExtra,
};

// ---------------------------------------------------------------- Input SARMUT manual (HC-13)

export const sarmutInputDoc: DocConfig = {
  docType: 'SRM', title: 'Input SARMUT Manual', endpoint: '/hc/sarmut-inputs',
  listColumns: [DOCNO_COL, DATE_COL, { key: 'period', label: 'Periode', mono: true }, { key: 'kpiName', label: 'Indikator', sortable: false },
    { key: 'actual', label: 'Nilai', type: 'number' }, STATUS_COL],
  header: [
    { key: 'period', label: 'Periode (YYYYMM)', required: true },
    { key: 'kpiId', label: 'Indikator (sumber manual)', type: 'lookup', lookup: 'sarmut-kpis', lookupFilters: { source: 'MANUAL' }, required: true },
    { key: 'actual', label: 'Nilai aktual', type: 'number', required: true },
    { key: 'evidence', label: 'Bukti & sumber data', type: 'textarea', required: true, help: 'Wajib melampirkan file bukti sebelum diajukan (PRD HC aturan 6)' },
  ],
  defaults: { period: thisPeriod() },
};

// ---------------------------------------------------------------- HC Phase 3: Rekrutmen, Training, Disiplin

export const recruitmentDoc: DocConfig = {
  docType: 'RECR',
  title: 'Permintaan Tenaga Kerja (MPP)',
  endpoint: '/hc/recruitments',
  listColumns: [
    DOCNO_COL,
    { key: 'headcountNeeded', label: 'Kebutuhan', type: 'number' },
    { key: 'targetDate', label: 'Target Terisi', type: 'date' },
    STATUS_COL,
  ],
  header: [
    { key: 'positionId', label: 'Jabatan / Posisi', type: 'lookup', lookup: 'positions', required: true },
    { key: 'headcountNeeded', label: 'Jumlah Orang yang Dibutuhkan', type: 'number', required: true },
    { key: 'targetDate', label: 'Target Tanggal Masuk Kerja', type: 'date', required: true },
    { key: 'justification', label: 'Alasan Penambahan / Penggantian Karyawan', type: 'textarea', required: true },
  ],
};

export const trainingDoc: DocConfig = {
  docType: 'TRN',
  title: 'Pelatihan & Training Karyawan',
  endpoint: '/hc/trainings',
  listColumns: [
    DOCNO_COL,
    { key: 'topic', label: 'Topik Pelatihan' },
    { key: 'trainingDate', label: 'Tanggal', type: 'date' },
    STATUS_COL,
  ],
  header: [
    { key: 'topic', label: 'Materi / Topik Pelatihan', required: true },
    { key: 'trainer', label: 'Instruktur / Lembaga Trainer' },
    { key: 'trainingDate', label: 'Tanggal Pelaksanaan', type: 'date', required: true },
    { key: 'durationHours', label: 'Durasi (Jam)', type: 'number' },
    { key: 'qualificationId', label: 'Kualifikasi Terkait (HC-12)', type: 'lookup', lookup: 'qualifications' },
    { key: 'attendeesSummary', label: 'Daftar Peserta / Catatan Evaluasi Post-test', type: 'textarea' },
  ],
};

export const disciplineDoc: DocConfig = {
  docType: 'SP',
  title: 'Tindakan Disiplin & Sanksi',
  endpoint: '/hc/disciplines',
  listColumns: [
    DOCNO_COL,
    { key: 'actionLevel', label: 'Tingkat Sanksi' },
    { key: 'incidentDate', label: 'Tgl Pelanggaran', type: 'date' },
    STATUS_COL,
  ],
  header: [
    { key: 'employeeId', label: 'Karyawan yang Bersangkutan', type: 'lookup', lookup: 'employees', required: true },
    { key: 'actionLevel', label: 'Bentuk Sanksi', type: 'select', options: [
      { value: 'TEGURAN', label: 'Surat Teguran Lisan/Tertulis' },
      { value: 'SP1', label: 'Surat Peringatan I (SP 1)' },
      { value: 'SP2', label: 'Surat Peringatan II (SP 2)' },
      { value: 'SP3', label: 'Surat Peringatan III (SP 3 / Terakhir)' },
      { value: 'PHK', label: 'Pemutusan Hubungan Kerja (PHK)' },
    ], required: true },
    { key: 'incidentDate', label: 'Tanggal Kejadian Pelanggaran', type: 'date', required: true },
    { key: 'violationClause', label: 'Pasal Peraturan Perusahaan / PKB' },
    { key: 'validUntil', label: 'Masa Berlaku Sanksi (Bulan/Tanggal)', type: 'date' },
    { key: 'description', label: 'Uraian Pelanggaran & Berita Acara', type: 'textarea', required: true },
  ],
};

export type { Doc };

