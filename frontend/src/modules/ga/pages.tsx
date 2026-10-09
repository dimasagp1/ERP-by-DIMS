import type { ResourceDef } from '../master/types';

export const inventoryAssets: ResourceDef = {
  key: 'ga-assets',
  title: 'Inventaris & Aset Non-Produksi',
  endpoint: '/ga/assets',
  table: 'ga.inventory_asset',
  columns: [
    { key: 'assetCode', label: 'Kode Aset', mono: true },
    { key: 'name', label: 'Nama Aset' },
    { key: 'category', label: 'Kategori' },
    { key: 'location', label: 'Lokasi' },
    { key: 'condition', label: 'Kondisi' },
  ],
  fields: [
    { key: 'assetCode', label: 'Kode Aset', required: true },
    { key: 'name', label: 'Nama Barang / Aset', required: true },
    { key: 'category', label: 'Kategori', type: 'select', options: [
      { value: 'IT', label: 'Perangkat IT & Komputer' },
      { value: 'FURNITURE', label: 'Furnitur & Meja Kantor' },
      { value: 'VEHICLE', label: 'Kendaraan Operasional' },
      { value: 'ELECTRONIC', label: 'Elektronik & AC' },
    ], required: true },
    { key: 'employeeId', label: 'Pemegang Aset (Karyawan)', type: 'lookup', lookup: 'employees' },
    { key: 'location', label: 'Lokasi Penempatan' },
    { key: 'condition', label: 'Kondisi', type: 'select', options: [
      { value: 'GOOD', label: 'Baik' },
      { value: 'FAIR', label: 'Cukup' },
      { value: 'BROKEN', label: 'Rusak / Perlu Perbaikan' },
    ] },
    { key: 'purchaseDate', label: 'Tanggal Pembelian', type: 'date' },
  ],
};

export function GaDashboardPage() {
  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 20 }}>
        <h2>Dashboard General Affairs (GA)</h2>
        <p className="muted">Layanan umum, aset non-produksi, kendaraan operasional, perizinan, dan K3.</p>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16, marginBottom: 24 }}>
        <div className="card" style={{ padding: 16 }}>
          <div className="lbl">Permintaan Layanan Aktif</div>
          <div style={{ fontSize: 28, fontWeight: 700, marginTop: 4 }}>6</div>
          <div className="small muted">4 ATK, 2 perbaikan fasilitas</div>
        </div>
        <div className="card" style={{ padding: 16 }}>
          <div className="lbl">Kendaraan Bertugas Hari Ini</div>
          <div style={{ fontSize: 28, fontWeight: 700, marginTop: 4, color: 'var(--pre-accent, #9a3412)' }}>2 / 4</div>
          <div className="small muted">2 unit tersedia di pool</div>
        </div>
        <div className="card" style={{ padding: 16 }}>
          <div className="lbl">Izin Kedaluwarsa &lt; 60 Hari</div>
          <div style={{ fontSize: 28, fontWeight: 700, marginTop: 4, color: '#dc2626' }}>1</div>
          <div className="small muted">Izin Lingkungan Plant 1</div>
        </div>
        <div className="card" style={{ padding: 16 }}>
          <div className="lbl">K3 Zero Accident</div>
          <div style={{ fontSize: 28, fontWeight: 700, marginTop: 4, color: 'var(--qms-accent, #047857)' }}>412 Hari</div>
          <div className="small muted">Sejak insiden terakhir</div>
        </div>
      </div>
    </div>
  );
}

export function RoomBookingPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Booking Ruang Rapat (GA-05)</h3>
      <p className="muted">Jadwal penggunaan ruang rapat internal, proyektor, dan fasilitas konferensi audio-visual.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <table className="t" style={{ width: '100%' }}>
          <thead>
            <tr>
              <th>Nama Ruang</th>
              <th>Kapasitas</th>
              <th>Fasilitas</th>
              <th>Status Hari Ini</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Ruang Kemuning (Lantai 2)</td>
              <td>16 Orang</td>
              <td>Smart TV 75", Video Conference, Glassboard</td>
              <td><span className="chip" style={{ background: '#dcfce7', color: '#166534' }}>Tersedia</span></td>
            </tr>
            <tr>
              <td>Ruang Pegagan (Lantai 1)</td>
              <td>8 Orang</td>
              <td>TV Display, Whiteboard</td>
              <td><span className="chip" style={{ background: '#fee2e2', color: '#991b1b' }}>Digunakan s/d 15:00</span></td>
            </tr>
            <tr>
              <td>Auditorium HerbaTech</td>
              <td>100 Orang</td>
              <td>Sound System, Proyektor Laser, Stage</td>
              <td><span className="chip" style={{ background: '#dcfce7', color: '#166534' }}>Tersedia</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function FacilityMaintenancePage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Pemeliharaan Gedung & Fasilitas (GA-06)</h3>
      <p className="muted">Perbaikan non-mesin produksi seperti atap kantor, sistem AC sentral gedung, kelistrikan dan plumbing.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <p>Permintaan pekerjaan dari seluruh departemen dikonsolidasikan di sini dengan SLA pengerjaan terukur.</p>
      </div>
    </div>
  );
}

export function PestWastePage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Kebersihan, Pest Control & Limbah (GA-07)</h3>
      <p className="muted">Jadwal inspeksi baiting/fogging hama, manifest pembuangan limbah B3 berizin, dan sanitasi area luar pabrik.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <p>Integrasi langsung dengan standar CPOTB/CPOB untuk memastikan area produksi bebas kontaminasi hama.</p>
      </div>
    </div>
  );
}

export function PermitsPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Perizinan & Dokumen Legal Perusahaan (GA-09)</h3>
      <p className="muted">Monitoring masa berlaku IMB/PBG, Amdal/UKL-UPL, izin genset, sertifikat laik fungsi, dan izin operasional.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <table className="t" style={{ width: '100%' }}>
          <thead>
            <tr>
              <th>Nama Izin / Dokumen</th>
              <th>Nomor Izin</th>
              <th>Instansi Penerbit</th>
              <th>Masa Berlaku</th>
              <th>Peringatan</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Izin Lingkungan (SPPL / UKL-UPL)</td>
              <td className="mono">660.1/204/DLH/2024</td>
              <td>Dinas Lingkungan Hidup</td>
              <td>31 Des 2026</td>
              <td><span className="chip" style={{ background: '#fef3c7', color: '#92400e' }}>Perpanjang &lt; 90 hari</span></td>
            </tr>
            <tr>
              <td>Sertifikat Laik Operasi (SLO) Genset</td>
              <td className="mono">SLO-GNS-0042/2025</td>
              <td>Kementerian ESDM</td>
              <td>15 Agu 2027</td>
              <td><span className="chip" style={{ background: '#dcfce7', color: '#166534' }}>Aktif</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function VendorContractPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Kontrak Vendor Jasa (GA-10)</h3>
      <p className="muted">Kontrak outsourcing satuan pengamanan (Satpam), cleaning service, katering karyawan, dan jasa angkut limbah.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <p>Nilai tagihan bulanan dicocokkan otomatis dengan BAST Jasa (PRC-12) dan daftar hadir absensi satpam/cleaner.</p>
      </div>
    </div>
  );
}

export function CateringPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Katering & Konsumsi Karyawan (GA-11)</h3>
      <p className="muted">Perhitungan porsi katering harian berdasarkan rekap kehadiran mesin absensi (HC-07).</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <p>Jumlah pesanan makan siang shift 1 dan shift 2 terkunci otomatis setiap pukul 08:30 WIB berdasarkan jam masuk tap kartu.</p>
      </div>
    </div>
  );
}

export function TravelAccomPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Akomodasi & Tiket Perjalanan Dinas (GA-12)</h3>
      <p className="muted">Pemesanan tiket pesawat/kereta dan hotel berdasarkan Surat Perintah Dinas (SPD) yang telah disetujui di HC-08.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <p>Karyawan tidak perlu menalangi biaya tiket utama; pemesanan dilakukan secara terpusat oleh tim GA melalui travel korporat.</p>
      </div>
    </div>
  );
}

export function GaReportPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Laporan & Efisiensi Layanan GA (GA-90)</h3>
      <p className="muted">Statistik kepatuhan SLA permintaan, konsumsi BBM per kendaraan, dan utilisasi ruang meeting.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <div style={{ display: 'flex', gap: 24 }}>
          <div>
            <div className="lbl">Penyelesaian Sesuai SLA</div>
            <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--qms-accent, #047857)' }}>96.4%</div>
          </div>
          <div>
            <div className="lbl">Efisiensi Konsumsi BBM</div>
            <div style={{ fontSize: 24, fontWeight: 700 }}>11.8 km/L</div>
          </div>
          <div>
            <div className="lbl">Tingkat Okupansi Ruang Meeting</div>
            <div style={{ fontSize: 24, fontWeight: 700 }}>64%</div>
          </div>
        </div>
      </div>
    </div>
  );
}
