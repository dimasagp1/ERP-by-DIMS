# Herbatech ERP Manufaktur

ERP terintegrasi untuk 8 departemen (PRE, PRC, FIN, GA, HC, QMS, SCM, RND) + Layanan Saya + Pengaturan Sistem,
sesuai [PRD](docs/PRD%20ERP%20Manufaktur%20Terintegrasi.md) dan [prototipe UI](docs/reference/). Rencana & status: [PLANNING.md](PLANNING.md).

| Bagian | Teknologi |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1, Spring Modulith (modular monolith), JPA/Hibernate 7, Flyway, PostgreSQL 16 |
| Frontend | React 18 + TypeScript + Vite, TanStack Query, desain dari prototipe (IBM Plex) |
| Uji | JUnit 5 + Testcontainers (PostgreSQL asli), verifikasi batas modul Spring Modulith |

## Menjalankan (pengembangan)

Prasyarat: JDK 21, Node.js 20+, Docker.

```bash
docker compose up -d db
```

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
cd frontend && npm install && npm run dev
```

Buka http://localhost:5173. Backend di http://localhost:8081 (dokumentasi API: `/api/swagger`).

Semua layanan dalam container: `docker compose --profile app up -d --build` (frontend di port 5173).

### Akun demo (profil `dev`)

Dibuat otomatis saat database kosong. Kata sandi semua akun ada di `backend/src/main/resources/application-dev.yml`
(`erp.demo.password`) — hanya untuk lingkungan lokal.

| Pengguna | Peran | Aplikasi |
| --- | --- | --- |
| `admin` | Admin Sistem | SYS |
| `direktur`, `dir.keuangan`, `dir.ops` | Direktur | semua |
| `auditor` | Auditor (baca saja + audit trail) | semua |
| `fin.staf` → `fin.spv` → `fin.manager` | Operator → Supervisor → Manager | FIN |
| `prc.manager`, `prc.staf` | Manager, Operator | PRC |
| `pre.manager`, `pre.spv`, `pre.operator` | Manager, Supervisor, Operator | PRE |
| `qa.manager`, `qa.release` | Manager, QA Release Officer | QMS (+SCM, PRE untuk release) |
| `scm.manager`, `scm.spv` | Manager, Supervisor | SCM |
| `hc.manager`, `hc.payroll` | Manager, Payroll | HC |
| `ga.manager`, `ga.staf` | Manager, Operator | GA |
| `rnd.manager` | Manager | RND |

Skenario cepat: login `fin.staf`, buat Jurnal Umum ≥ Rp 100 jt → Ajukan → login `fin.spv` setujui L1 →
login `fin.manager` setujui L2 → Posting → Reversal. Lihat jejaknya di SYS-14 (login `auditor`).

Skenario M1: login `hc.manager`, HC-08 › Cuti & Izin › Baru untuk karyawan E0032 → Ajukan → login `pre.spv`
(atasan langsung) setujui di Kotak Approval → login `pre.operator`, buka ESS-01 › Saldo cuti. Payroll (HC-09) dihitung
oleh `hc.payroll` setelah absensi periode dikunci di HC-07.

## Uji

```bash
cd backend && ./mvnw test
```

## Struktur

```
backend/src/main/java/id/herbatech/erp/
  shared/   kernel: dokumen & status, penomoran, approval, audit trail, TTE, kunci periode, hak akses,
            notifikasi, lampiran, registry menu, dashboard, pencarian
  sys/      SYS-01..15 master bersama        hc/   HC-02/03 organisasi & karyawan
  fin/      FIN-02/03/04/70 + jurnal otomatis scm/  mesin inventory per lot (ITEM/LOT/STOCK_MOVE)
backend/src/main/resources/db/migration/   skema per modul (core, sys, hc, fin, scm) + data referensi
backend/src/main/resources/meta/apps.json  10 aplikasi & 177 menu (sumber: prototipe UI)
frontend/src/  shell (launcher, navbar, Ctrl+K), components, modules/{master,fin,sys}, pages
```

### Catatan lingkungan Windows dengan sandbox

Bila Tomcat gagal start dengan `Unable to establish loopback connection`, arahkan direktori socket Java ke folder
yang bisa ditulis: `set JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Temp`.
