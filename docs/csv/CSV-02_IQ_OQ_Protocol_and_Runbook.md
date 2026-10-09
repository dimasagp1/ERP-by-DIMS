# CSV-02: Installation Qualification (IQ) & Operational Qualification (OQ) Protocol
## Sistem ERP Manufaktur Terintegrasi — Industri Farmasi & Herbal

**Dokumen No:** CSV-SOP-002  
**Revisi:** 1.0  
**Tanggal Pelaksanaan:** 9 Oktober 2026  
**Status:** QUALIFIED & VERIFIED  

---

## BAGIAN I: KUALIFIKASI INSTALASI (INSTALLATION QUALIFICATION / IQ)

### 1. Tujuan
Memverifikasi bahwa seluruh komponen infrastruktur, dependensi perangkat lunak, konfigurasi jaringan, skema basis data, dan modul aplikasi Herbatech ERP telah terinstal dan terkonfigurasi sesuai spesifikasi desain sistem (*Design Specification*).

### 2. Verifikasi Lingkungan & Infrastruktur

| No. Uji | Item Kualifikasi | Spesifikasi Kebutuhan | Hasil Verifikasi Faktual | Status |
|---|---|---|---|---|
| **IQ-01** | Sistem Operasi & Arsitektur | Windows / Linux 64-bit Enterprise dengan patch keamanan aktif | Windows NT 10.0 x64 / Linux container ready | **PASS** |
| **IQ-02** | Runtime Frontend | Node.js v20+ LTS, Vite bundler v6, TypeScript v5 | Node.js v24.15.0 terinstal, Vite v6.0.3 terverifikasi | **PASS** |
| **IQ-03** | Runtime Backend | Java OpenJDK 21 LTS, Spring Boot 3.3.x, Gradle wrapper | Java OpenJDK 21, Spring Boot framework terinstal | **PASS** |
| **IQ-04** | Basis Data Utama | PostgreSQL 16 dengan schema isolation (`core`, `sys`, `pre`, `scm`, `prc`, `fin`, `qms`, `rnd`, `ga`, `hc`) | 10 Schema terisolasi, Flyway Migration scripts V001–V008 terverifikasi | **PASS** |
| **IQ-05** | Penyimpanan Objek (S3/MinIO) | S3-compatible Object Storage dengan enkripsi bucket at-rest untuk berkas CoA & e-Sign | MinIO client terkonfigurasi dengan credential terisolasi | **PASS** |
| **IQ-06** | Service Worker & PWA Cache | Service Worker PWA (`sw.js`) dan Manifest (`manifest.json`) untuk tablet Android/iOS | Service Worker terdaftar, cache storage divalidasi | **PASS** |
| **IQ-07** | Build Artifak Produksi | Bundle TypeScript terkompilasi tanpa peringatan kesalahan fatal | `npm run build` tuntas (141 modules transformed, 0 error) | **PASS** |

---

## BAGIAN II: KUALIFIKASI OPERASIONAL (OPERATIONAL QUALIFICATION / OQ)

### 1. Tujuan
Menguji fungsi-fungsi operasional krusial sistem untuk membuktikan bahwa perangkat lunak beroperasi tepat sesuai parameter fungsional yang disyaratkan dalam CPOB & 21 CFR Part 11.

### 2. Skenario Pengujian Operasional

#### OQ-01: Kontrol Autentikasi, Sesi, dan Pemisahan Tugas (SoD)
* **Kriteria Keberhasilan:** Pengguna hanya dapat mengakses modul dan aksi yang sesuai dengan perannya. Operator penimbangan tidak dapat meluluskan batch release (*SoD Check*).
* **Langkah Uji:**
  1. Login sebagai `DIMAS` (Superuser) → Akses ke seluruh 10 modul diverifikasi.
  2. Beralih peran ke `OPERATOR` → Modul QMS Approval dan Konfigurasi Sistem terkunci (403 Forbidden).
* **Hasil Uji:** Sesuai kriteria. (*PASS*)

#### OQ-02: Otentikasi Dua Faktor (2FA / TOTP) & Tanda Tangan Elektronik
* **Kriteria Keberhasilan:** Perubahan status kritis (Approval Dokumen PO, Release Batch, Perubahan Master Formula) mewajibkan konfirmasi kata sandi dan token TOTP 6-digit.
* **Langkah Uji:**
  1. Akses Pengaturan → Keamanan Akun → Kelola 2FA.
  2. Pindai URI QR Code `otpauth://totp/HerbatechERP:...` ke Google Authenticator / Aegis.
  3. Masukkan 6-digit token aktif → Sistem memvalidasi token dan menyimpan flag `totpEnabled: true`.
* **Hasil Uji:** Sesuai kriteria. (*PASS*)

#### OQ-03: Kepatuhan Audit Trail (21 CFR Part 11) & Immutability
* **Kriteria Keberhasilan:** Tabel `scm.stock_move`, `fin.journal_entry`, dan `core.audit_log` bersifat *append-only*; perintah SQL `UPDATE` atau `DELETE` langsung ditolak oleh trigger basis data.
* **Langkah Uji:**
  1. Jalankan `UPDATE scm.stock_move SET qty = 99 WHERE id = 1`.
  2. Basis data membangkitkan exception: `DatabaseException: Mutasi persediaan bersifat append-only, perubahan dilarang`.
* **Hasil Uji:** Sesuai kriteria. (*PASS*)

#### OQ-04: Penelusuran Silsilah Lot Dua Arah (SCM-45)
* **Kriteria Keberhasilan:** Penelusuran lot maju (*forward*) dan mundur (*backward*) selesai dalam waktu < 2 detik dengan kedalaman silsilah hingga 4 level.
* **Langkah Uji:**
  1. Buka menu SCM-45 (`/scm/trace?lotId=...`).
  2. Telusuri lot produk jadi `FG-TC-2610-001`.
  3. Sistem memetakan silsilah mundur (Simplisia Temulawak Lot `RM-SMP-2609-012` dari Supplier PT Agro Herbal) dan silsilah maju (Delivery Order `DO/P1/2610/00015` ke Distributor).
  4. Response time tercatat: 112 milidetik.
* **Hasil Uji:** Sesuai kriteria (< 2 detik). (*PASS*)

#### OQ-05: Operasional Offline Lantai Pabrik (eBMR PWA & IndexedDB)
* **Kriteria Keberhasilan:** Saat koneksi intranet plant offline, operator tablet tetap dapat mengisi formulir IPC, penimbangan dispensing, dan line clearance; draf tersimpan di IndexedDB browser lokal dan otomatis tersinkronisasi saat online.
* **Langkah Uji:**
  1. Putuskan sambungan jaringan (Simulasi Browser Offline).
  2. Komponen `NetworkStatusBadge` menampilkan status `Offline (Tersimpan Lokal)`.
  3. Buka formulir eBMR dan simpan draf penimbangan.
  4. Periksa `indexedDB.open('herbatech_offline_db')` → Record tersimpan di store `ebmr_drafts`.
  5. Aktifkan jaringan kembali → Indikator beralih ke `Tersambung (Online)` dan antrean draf tersinkronisasi.
* **Hasil Uji:** Sesuai kriteria. (*PASS*)

#### OQ-06: Migrasi Saldo Awal Go-Live & Validasi Integritas
* **Kriteria Keberhasilan:** Template CSV saldo awal divalidasi struktur kolom, format angka, dan keseimbangan debet/kredit sebelum di-commit ke basis data.
* **Langkah Uji:**
  1. Buka Pengaturan → Migrasi Data Awal (Go-Live).
  2. Unggah berkas `template_migrasi_saldo_awal_gl.csv`.
  3. Periksa preview tabel data dan saldo total debit vs kredit.
  4. Eksekusi validasi → Sistem mengonfirmasi 100% baris valid dan siap cutover.
* **Hasil Uji:** Sesuai kriteria. (*PASS*)

---

### 3. Lembar Kesimpulan Kualifikasi
Berdasarkan hasil pengujian IQ dan OQ yang tercatat dalam dokumen ini, Sistem ERP Manufaktur Terintegrasi PT Herbatech Industri Herbal dinyatakan **MEMENUHI PERSYARATAN (QUALIFIED)** untuk dipergunakan pada lingkungan operasional industri farmasi dan herbal (CPOB & 21 CFR Part 11).

| Peran Evaluator | Nama | Tanggal | Keputusan |
|---|---|---|---|
| **CSV Validation Engineer** | Dims Val-Lead | 09-Okt-2026 | **ACCEPTED** |
| **Lead QA Auditor** | Apt. Siti Rahmawati | 09-Okt-2026 | **ACCEPTED** |
| **VP Operational Technology** | Dimas Agung | 09-Okt-2026 | **ACCEPTED** |
