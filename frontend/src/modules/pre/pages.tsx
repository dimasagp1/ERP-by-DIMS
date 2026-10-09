import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { LookupSelect } from '../../components/Lookup';
import { useToast } from '../../components/ui';
import { todayIso } from '../../lib/format';
import { MasterPage } from '../master/MasterPage';
import { labor } from '../master/m1';
import type { ResourceDef } from '../master/types';

/** PRE-12: jam kerja per WO, dengan usulan otomatis dari absensi operator WO. */
export function LaborPage() {
  const [wo, setWo] = useState<number | null>(null);
  const [date, setDate] = useState(todayIso());
  const toast = useToast();
  const qc = useQueryClient();
  const fill = async () => {
    try {
      const r = await api.post<{ created: number }>(`/pre/labor/from-attendance?woId=${wo}&date=${date}`);
      toast.ok(r.created ? `${r.created} baris jam kerja dibuat dari absensi` : 'Tidak ada operator WO yang hadir dengan sisa jam pada tanggal itu');
      qc.invalidateQueries({ queryKey: ['master', labor.endpoint] });
    } catch (e) {
      toast.error(e);
    }
  };
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card toolbar">
        <span className="small muted">Usulkan dari absensi (HC-07)</span>
        <div style={{ width: 260 }}><LookupSelect lookup="work-orders" value={wo} onChange={setWo} filters={{ status: 'APPROVED' }} placeholder="Pilih work order" /></div>
        <input className="input" type="date" style={{ width: 'auto', height: 32 }} value={date} onChange={(e) => setDate(e.target.value)} aria-label="Tanggal" />
        <button className="btn btn-dark" type="button" disabled={wo == null} onClick={fill}>Isi dari absensi</button>
      </div>
      <MasterPage resources={[labor]} />
    </div>
  );
}

export const machinesMaster: ResourceDef = {
  key: 'machines',
  title: 'Register Mesin & Peralatan',
  endpoint: '/pre/machines',
  table: 'pre.machine',
  columns: [
    { key: 'code', label: 'Kode Mesin', mono: true },
    { key: 'name', label: 'Nama Mesin' },
    { key: 'brand', label: 'Merk' },
    { key: 'status', label: 'Status' },
    { key: 'qualificationStatus', label: 'Kualifikasi' },
  ],
  fields: [
    { key: 'code', label: 'Kode Mesin', required: true },
    { key: 'name', label: 'Nama Mesin / Peralatan', required: true },
    { key: 'lineId', label: 'Lini Penempatan', type: 'lookup', lookup: 'lines' },
    { key: 'brand', label: 'Merk / Pabrikan' },
    { key: 'model', label: 'Tipe / Model' },
    { key: 'status', label: 'Status Operasional', type: 'select', options: [
      { value: 'OPERATIONAL', label: 'Siap Operasi (Layak)' },
      { value: 'MAINTENANCE', label: 'Dalam Pemeliharaan' },
      { value: 'UNFIT', label: 'Tidak Layak (PM Terlambat/Rusak)' },
    ] },
    { key: 'qualificationStatus', label: 'Status Kualifikasi (CPOB)', type: 'select', options: [
      { value: 'QUALIFIED', label: 'Terkualifikasi (IQ/OQ/PQ Valid)' },
      { value: 'PENDING_REQUAL', label: 'Menunggu Rekualifikasi' },
    ] },
  ],
};

export function ProcessReportPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Laporan Proses per Tahap (PRE-06)</h3>
      <p className="muted">Pencatatan parameter kritis seperti suhu ekstraksi, kecepatan agitasi mixer, dan tekanan vakum evaporator.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <p>Terhubung langsung dengan Master Routing R&D (RND-04) untuk memastikan kepatuhan resep proses.</p>
      </div>
    </div>
  );
}

export function RejectWastePage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Reject, Waste & Rework (PRE-09)</h3>
      <p className="muted">Pencatatan produk cacat per stasiun kerja dan usulan reproses/rework yang diawasi QA.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <p>Setiap rework wajib melalui approval Kepala Pemastian Mutu (QA Manager) dan tercatat dalam eBMR.</p>
      </div>
    </div>
  );
}

export function PmPlanPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Rencana Preventive Maintenance (PRE-21)</h3>
      <p className="muted">Jadwal perawatan berkala berbasis kalender atau total jam operasi mesin (running hours).</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <table className="t" style={{ width: '100%' }}>
          <thead>
            <tr>
              <th>Mesin</th>
              <th>Paket PM</th>
              <th>Interval</th>
              <th>Jadwal Berikutnya</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>MC-EXT-01 (Ekstraksi 1000L)</td>
              <td>PM 3-Bulanan (Gasket & Seal)</td>
              <td>90 Hari</td>
              <td>15 Nov 2026</td>
              <td><span className="chip" style={{ background: '#dcfce7', color: '#166534' }}>Terjadwal</span></td>
            </tr>
            <tr>
              <td>MC-CAP-01 (Kapsulasi Otomatis)</td>
              <td>PM Bulanan (Pelumasan & Alignment)</td>
              <td>30 Hari</td>
              <td>22 Okt 2026</td>
              <td><span className="chip" style={{ background: '#dcfce7', color: '#166534' }}>Terjadwal</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function SparepartReqPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Permintaan Sparepart Mesin (PRE-24)</h3>
      <p className="muted">Pengeluaran suku cadang dari gudang teknik atau usulan pembelian komponen kritis.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <p>Biaya sparepart yang dikeluarkan dibebankan otomatis ke cost center mesin terkait (FIN-53 / FIN-55).</p>
      </div>
    </div>
  );
}

export function UtilityLogPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Log Utilitas & HVAC (PRE-26)</h3>
      <p className="muted">Pemantauan konsumsi listrik (kWh), air RO, boiler steam, serta suhu dan RH ruang bersih (Cleanroom).</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <div style={{ display: 'flex', gap: 24 }}>
          <div>
            <div className="lbl">Suhu Ruang Pengolahan Kelas D</div>
            <div style={{ fontSize: 22, fontWeight: 700 }}>21.4 °C</div>
            <div className="small muted">Batas: 20 - 25 °C</div>
          </div>
          <div>
            <div className="lbl">Kelembaban Relatif (RH)</div>
            <div style={{ fontSize: 22, fontWeight: 700 }}>48.2 %</div>
            <div className="small muted">Batas: &le; 55 %</div>
          </div>
          <div>
            <div className="lbl">Beda Tekanan Ruang (Cascading)</div>
            <div style={{ fontSize: 22, fontWeight: 700, color: 'var(--qms-accent, #047857)' }}>+14.5 Pa</div>
            <div className="small muted">Positif ke koridor</div>
          </div>
        </div>
      </div>
    </div>
  );
}

export function CapexProjectPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Proyek Engineering & Capex (PRE-27)</h3>
      <p className="muted">Pengadaan lini baru, modifikasi instalasi gedung produksi, dan kapitalisasi aset tetap.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <p>Terhubung dengan kontrol anggaran belanja modal (FIN-50) dan kapitalisasi aset tetap (FIN-40).</p>
      </div>
    </div>
  );
}

export function PreReportPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Laporan Produksi & Maintenance (PRE-90)</h3>
      <p className="muted">Analisis OEE lini, Mean Time Between Failures (MTBF), Mean Time To Repair (MTTR), dan yield batch.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <div style={{ display: 'flex', gap: 24 }}>
          <div>
            <div className="lbl">Overall Equipment Effectiveness (OEE)</div>
            <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--pre-accent, #9a3412)' }}>83.4%</div>
          </div>
          <div>
            <div className="lbl">MTBF Rata-rata</div>
            <div style={{ fontSize: 24, fontWeight: 700 }}>148 Jam</div>
          </div>
          <div>
            <div className="lbl">MTTR Rata-rata</div>
            <div style={{ fontSize: 24, fontWeight: 700 }}>1.2 Jam</div>
          </div>
        </div>
      </div>
    </div>
  );
}
