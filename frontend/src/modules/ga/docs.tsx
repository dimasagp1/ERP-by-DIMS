import type { DocConfig } from '../docs/types';

const STATUS_COL = { key: 'status', label: 'Status', type: 'status' as const, sortable: false };
const DOCNO_COL = { key: 'docNo', label: 'No. Dokumen', mono: true };

export const gaServiceRequestDoc: DocConfig = {
  docType: 'REQ-GA',
  title: 'Permintaan Layanan GA & ATK',
  endpoint: '/ga/service-requests',
  listColumns: [
    DOCNO_COL,
    { key: 'reqType', label: 'Jenis Permintaan' },
    { key: 'description', label: 'Keterangan' },
    { key: 'priority', label: 'Prioritas' },
    { key: 'slaDueDate', label: 'Tenggat SLA', type: 'date' },
    STATUS_COL,
  ],
  header: [
    { key: 'reqType', label: 'Jenis Layanan', type: 'select', options: [
      { value: 'ATK', label: 'Alat Tulis Kantor & Konsumabel' },
      { value: 'FACILITY', label: 'Perbaikan Gedung & Fasilitas' },
      { value: 'CLEANING', label: 'Pembersihan Khusus & Sanitasi' },
      { value: 'WASTE', label: 'Pengelolaan Limbah Non-B3' },
    ], required: true },
    { key: 'priority', label: 'Prioritas Layanan', type: 'select', options: [
      { value: 'LOW', label: 'Rendah' },
      { value: 'NORMAL', label: 'Normal' },
      { value: 'URGENT', label: 'Mendesak / Emergency' },
    ], required: true },
    { key: 'requesterId', label: 'Pemohon', type: 'lookup', lookup: 'employees', required: true },
    { key: 'slaDueDate', label: 'Target Selesai (SLA)', type: 'date' },
    { key: 'description', label: 'Detail Permintaan & Lokasi', type: 'textarea', required: true },
    { key: 'resolutionNotes', label: 'Catatan Penyelesaian / Tindak Lanjut', type: 'textarea' },
  ],
};

export const vehicleBookingDoc: DocConfig = {
  docType: 'VHB',
  title: 'Booking Kendaraan Operasional',
  endpoint: '/ga/vehicle-bookings',
  listColumns: [
    DOCNO_COL,
    { key: 'vehicleName', label: 'Kendaraan' },
    { key: 'destination', label: 'Tujuan' },
    { key: 'startTime', label: 'Mulai', type: 'date' },
    { key: 'endTime', label: 'Selesai', type: 'date' },
    STATUS_COL,
  ],
  header: [
    { key: 'vehicleName', label: 'Pilihan Kendaraan', required: true },
    { key: 'licensePlate', label: 'Nomor Polisi (Plat)' },
    { key: 'requesterId', label: 'Pemohon / Penumpang Utama', type: 'lookup', lookup: 'employees', required: true },
    { key: 'driverName', label: 'Nama Pengemudi' },
    { key: 'startTime', label: 'Waktu Berangkat', type: 'date', required: true },
    { key: 'endTime', label: 'Perkiraan Waktu Kembali', type: 'date', required: true },
    { key: 'destination', label: 'Kota / Lokasi Tujuan', required: true },
    { key: 'purpose', label: 'Keperluan Dinas', type: 'textarea' },
  ],
};

export const gatePassDoc: DocConfig = {
  docType: 'GP',
  title: 'Buku Tamu & Gate Pass',
  endpoint: '/ga/gate-passes',
  listColumns: [
    DOCNO_COL,
    { key: 'passType', label: 'Jenis Izin' },
    { key: 'personName', label: 'Nama Tamu / Pembawa' },
    { key: 'companyName', label: 'Instansi / Vendor' },
    STATUS_COL,
  ],
  header: [
    { key: 'passType', label: 'Jenis Gate Pass', type: 'select', options: [
      { value: 'VISITOR', label: 'Tamu Perusahaan' },
      { value: 'CONTRACTOR', label: 'Pekerja Kontraktor / Teknisi Vendor' },
      { value: 'GOODS_OUT', label: 'Izin Pengeluaran Barang / Sampah' },
    ], required: true },
    { key: 'personName', label: 'Nama Lengkap', required: true },
    { key: 'companyName', label: 'Asal Perusahaan' },
    { key: 'vehiclePlate', label: 'Nomor Plat Kendaraan' },
    { key: 'goodsDescription', label: 'Rincian Barang yang Dibawa', type: 'textarea' },
    { key: 'deliveryDocNo', label: 'Nomor Surat Jalan Acuan' },
  ],
};

export const hsseIncidentDoc: DocConfig = {
  docType: 'K3',
  title: 'Laporan Insiden K3 & Lingkungan',
  endpoint: '/ga/incidents',
  listColumns: [
    DOCNO_COL,
    { key: 'severity', label: 'Tingkat Bahaya' },
    { key: 'location', label: 'Lokasi Kejadian' },
    STATUS_COL,
  ],
  header: [
    { key: 'severity', label: 'Kategori Kejadian', type: 'select', options: [
      { value: 'NEAR_MISS', label: 'Neat Miss (Hampir Celaka)' },
      { value: 'FIRST_AID', label: 'Pertolongan Pertama (Ringan)' },
      { value: 'MEDICAL_TREATMENT', label: 'Perawatan Medis' },
      { value: 'LOST_TIME', label: 'Kehilangan Jam Kerja' },
    ], required: true },
    { key: 'location', label: 'Area / Lokasi Kejadian', required: true },
    { key: 'impactProductLot', label: 'Lot Produk Terdampak (Jika ada)' },
    { key: 'description', label: 'Kronologi Kejadian', type: 'textarea', required: true },
    { key: 'correctiveAction', label: 'Tindakan Penanganan & Pencegahan', type: 'textarea' },
  ],
};
