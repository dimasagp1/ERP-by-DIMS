# CSV-01: Requirements Traceability Matrix (RTM)
## Sistem ERP Manufaktur Terintegrasi — Industri Farmasi & Herbal (CPOB / 21 CFR Part 11)

**Versi Dokumen:** 1.0  
**Tanggal:** 9 Oktober 2026  
**Status:** VALIDATED & READY FOR AUDIT  
**Penyusun:** Tim Validasi Sistem Komputerisasi (CSV) & QA  

---

### 1. Ruang Lingkup Validasi
Matriks Ketertelusuran Persyaratan (*Requirements Traceability Matrix* / RTM) ini memetakan seluruh **User Requirement Specification (URS)** tingkat regulasi CPOB (Cara Pembuatan Obat yang Baik) dan FDA 21 CFR Part 11 ke dalam **Functional Specification (FS) / Modul Teknis**, serta **Kualifikasi Operasional (OQ) / Unit & Integration Test Case**.

---

### 2. Matriks Ketertelusuran (URS → FS/Modul → Verifikasi Test Case)

| URS ID | Kategori Regulasi | Deskripsi Kebutuhan Bisnis & Kepatuhan | Modul / Layanan Teknis | Endpoint / File Implementasi | Bukti Verifikasi / Test Suite | Status Kualifikasi |
|---|---|---|---|---|---|---|
| **URS-SEC-01** | 21 CFR Part 11 §11.10(d) | Pembatasan akses berbasis peran terkontrol (RBAC) dengan pemisahan tugas (*Segregation of Duties* / SoD). | SYS / CORE | `core.rbac_role`, `core.user_role` | `AuthServiceTest.java`, `RbacTest.java` | **PASS (OQ-01)** |
| **URS-SEC-02** | 21 CFR Part 11 §11.200 | Tanda tangan elektronik mengikat secara hukum dengan re-autentikasi (kata sandi + 2FA TOTP) dan penangkapan alasan (*manifestation of intent*). | SYS / AUTH | `api/auth/me/verify-password`, `TotpSecurityModal.tsx` | `TotpSecurityModal.test.tsx`, `AuthSecurityTest.java` | **PASS (OQ-02)** |
| **URS-AUD-01** | 21 CFR Part 11 §11.10(e) | Jejak audit (*Audit Trail*) append-only yang aman, mencatat stempel waktu UTC, ID operator, nilai sebelum/sesudah, dan alasan perubahan. | CORE / AUDIT | `core.audit_log`, `@AuditTrail`, PostgreSQL immutable trigger | `AuditTrailIntegrationTest.java`, `InventoryServiceTest.java` | **PASS (OQ-03)** |
| **URS-PRE-01** | CPOB Bab 5 (Produksi) | Catatan Pengolahan Bets Elektronik (*Electronic Batch Manufacturing Record* / eBMR) dengan kontrol urutan langkah per fase. | PRE (Produksi) | `pre.ebmr_record`, `pre.ebmr_step`, `PRE-03` | `ProductionWorkflowTest.java`, `eBmrExecutionTest.ts` | **PASS (OQ-04)** |
| **URS-PRE-02** | CPOB Aneks 1 (Dispensing) | Verifikasi gravimetrik dispensing bahan baku dengan penimbangan saksi ganda (*dual sign-off*). | PRE (Dispensing) | `pre.dispensing_log`, `PRE-05` | `DispensingValidationTest.java` | **PASS (OQ-05)** |
| **URS-PRE-03** | CPOB Sanitasi & Higiene | Pemeriksaan kesiapan lini (*Line Clearance*) sebelum bets dimulai, wajib disetujui supervisor sebelum mesin start. | PRE (Clearance) | `pre.line_clearance`, `PRE-13` | `LineClearanceTest.java` | **PASS (OQ-06)** |
| **URS-QMS-01** | CPOB Pengawasan Mutu | Pelulusan Bets (*Batch Release*) oleh Penanggung Jawab QA hanya dapat dieksekusi bila seluruh pengujian QC, IPC, dan review deviasi berstatus COMPLETED. | QMS (Pelulusan) | `qms.batch_release`, `QMS-09` | `BatchReleaseGateTest.java` | **PASS (OQ-07)** |
| **URS-QMS-02** | CPOB Dokumentasi | Penerbitan Sertifikat Analisis (*Certificate of Analysis* / CoA) berdasar spesifikasi resmi bahan baku & produk jadi. | QMS (QC) | `qms.coa_document`, `QMS-13` | `QcTestingTest.java` | **PASS (OQ-08)** |
| **URS-SCM-01** | CPOB Pergudangan | Alokasi pengeluaran bahan baku menganut sistem *First Expired, First Out* (FEFO) dan pencegahan otomatis lot karantina/reject. | SCM (Inventory) | `scm.stock_quant`, `scm.picking_rule`, `SCM-23` | `FefoPickingTest.java`, `OrderToCashTest.java` | **PASS (OQ-09)** |
| **URS-SCM-02** | CPOB Penarikan Bets | Penelusuran silsilah lot (*Traceability*) dua arah (backward: produk → supplier; forward: lot bahan → batch → customer) selesai < 2 detik. | SCM (Traceability) | `scm.stock_move`, `/scm/trace`, `SCM-45` | `WarehouseControllers.java`, `LotTracePerformanceTest.java` | **PASS (OQ-10)** |
| **URS-OFF-01** | Integritas Data Lantai Pabrik | Tablet PWA beroperasi tanpa jeda saat koneksi intranet terputus; data draf tersimpan di IndexedDB terenkripsi lokal dan otomatis sinkron saat online. | PRE / PWA | `sw.js`, `offlineStorage.ts`, `NetworkStatusBadge.tsx` | `offlineStorage.test.ts`, PWA Service Worker Audit | **PASS (OQ-11)** |
| **URS-FIN-01** | Standar Akuntansi (PSAK) | Jurnal berimbang (*Double-entry balancing*) otomatis dari setiap mutasi stok fisik, tanpa mutasi GL gantung. | FIN (GL) | `fin.journal_entry`, `fin.journal_line`, `FIN-01` | `JournalWorkflowTest.java`, `FinFlowTest.java` | **PASS (OQ-12)** |

---

### 3. Kepatuhan Kunci Regulasi ALCOA+
Sistem ERP Herbatech telah dirancang memenuhi prinsip **ALCOA+** untuk seluruh rekaman GxP:
1. **Attributable (Dapat diatribusikan):** Setiap aksi mencatat identitas unik pengguna melalui sesi terautentikasi dan JWT/Session ID.
2. **Legible (Dapat dibaca):** Seluruh data tersimpan dalam format terstruktur PostgreSQL dan UI yang jelas dengan audit trail riwayat perubahan.
3. **Contemporaneous (Tercatat seketika):** Stempel waktu otomatis dihasilkan oleh basis data server (`CURRENT_TIMESTAMP`), bukan waktu jam lokal gawai/tablet.
4. **Original (Asli):** Data draf eBMR dan log penimbangan disimpan dalam rekaman transaksi asli; revisi dibuat sebagai versi baru tanpa menimpa data awal.
5. **Accurate (Akurat):** Toleransi penimbangan, pemeriksaan batas spesifikasi QC, dan validasi debit/kredit mencegah inkonsistensi data matematis.
6. **Complete, Consistent, Enduring, Available (Lengkap, Konsisten, Tahan Lama, Tersedia):** Dukungan replikasi database, pencadangan snapshot terenkripsi, dan failover PWA IndexedDB.

---

### 4. Pengesahan (*Approval Sign-off*)

| Peran | Nama | Jabatan | Tanda Tangan & Tanggal |
|---|---|---|---|
| **System Owner** | Dimas Agung | VP Information Technology | *Approved electronically* (09-Oct-2026) |
| **Quality Assurance Lead** | Apt. Siti Rahmawati | QA Manager (Qualified Person) | *Approved electronically* (09-Oct-2026) |
| **Production Head** | Ir. Budi Santoso | Production Plant Manager | *Approved electronically* (09-Oct-2026) |
