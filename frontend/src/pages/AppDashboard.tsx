import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { AppDashboard as Dash, QueueItem } from '../api/types';
import { DataTable } from '../components/DataTable';
import { StatusChip } from '../components/StatusChip';
import { errorText } from '../components/ui';
import { fmtAge, fmtRpShort } from '../lib/format';
import { menuPath, useAllApps, useApps, useDocLink } from '../lib/meta';
import { MenuIcon } from '../components/MenuIcon';
import { OdooAppIcon } from '../components/OdooAppIcon';
import { ManageUsersModal } from '../components/ManageUsersModal';
import { MENU_IMPL } from '../modules/registry';

interface OperationCardDef {
  title: string;
  menuCode: string;
  icon: string;
  countToProcess: number;
  countLate?: number;
  countWaiting?: number;
  actionText: string;
  color: string;
  note: string;
}

/**
 * Beranda Aplikasi & Dashboard Menu Modul (Gambar Referensi User 2 - Area Cyan)
 * Menampilkan:
 * 1. Odoo-Style Operational Overview Cards (Penerimaan, Pengeluaran, Transfer, Work Order, dll)
 * 2. KPI metrics utama
 * 3. Antrean kerja terkini (Work queue)
 * 4. Peta hubungan antar departemen
 * 5. Grid seluruh menu modul
 */
export function AppDashboard() {
  const { app = '' } = useParams();
  const { byApp } = useApps();
  const all = useAllApps();
  const nav = useNavigate();
  const docLink = useDocLink();
  const cur = byApp.get(app);
  const [usersModalOpen, setUsersModalOpen] = useState(false);

  const q = useQuery({
    queryKey: ['dashboard', app],
    queryFn: () => api.get<Dash>(`/dashboard/${app}`),
    refetchInterval: 60_000,
  });

  if (!cur) return null;

  const def = all.byApp.get(app);
  const relOut = Object.entries(def?.sends ?? {});
  const relIn = all.apps.filter((a) => a.sends?.[app]).map((a) => [a.code, a.sends[app]] as const);
  const queueItems = q.data?.queue ?? [];

  // Konfigurasi kartu operasional ringkasan Odoo-style per modul
  const opCards = getModuleOperationCards(app, queueItems);

  return (
    <div className="page" style={{ paddingTop: 16 }}>
      {/* ---------------- Header Banner Modul ---------------- */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 16,
          background: 'var(--surface)',
          padding: '16px 20px',
          borderRadius: 8,
          border: '1px solid var(--line)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div
            style={{
              width: 52,
              height: 52,
              borderRadius: 14,
              background: 'var(--surface-2)',
              border: '1px solid var(--line-soft)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 2px 6px rgba(0,0,0,0.04)',
            }}
          >
            <OdooAppIcon app={cur.code} size={42} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <h1 style={{ fontSize: 20, fontWeight: 700 }}>Overview {cur.name}</h1>
              <span
                className="mono"
                style={{
                  fontSize: 11,
                  background: cur.color,
                  color: '#FFFFFF',
                  padding: '2px 7px',
                  borderRadius: 4,
                  fontWeight: 600,
                }}
              >
                {cur.code}
              </span>
            </div>
            <div className="small muted" style={{ marginTop: 2 }}>
              Dashboard operasional, status alur kerja, dan ringkasan antrean transaksi departemen.
            </div>
          </div>
        </div>

        {/* Quick action buttons */}
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {app === 'SYS' && (
            <button
              type="button"
              className="btn btn-dark"
              onClick={() => setUsersModalOpen(true)}
              style={{ display: 'flex', alignItems: 'center', gap: 6 }}
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
              </svg>
              Manage Users
            </button>
          )}
          <button
            type="button"
            className="btn"
            onClick={() => nav(`/app/${app}/settings`)}
          >
            Pengaturan Modul
          </button>
        </div>
      </div>

      {q.error && <div className="alert alert-err">{errorText(q.error)}</div>}

      {/* ---------------- BAGIAN 1: ODOO-STYLE OPERATIONAL OVERVIEW CARDS ---------------- */}
      <div>
        <div className="lbl" style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
          <span>Operasi & Alur Kerja Terkini ({cur.shortName})</span>
          <span className="small muted" style={{ textTransform: 'none', fontWeight: 400 }}>
            — Klik tombol untuk memproses langsung
          </span>
        </div>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: 16,
          }}
        >
          {opCards.map((card) => (
            <div
              key={card.title}
              className="card"
              style={{
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                padding: '16px 18px',
                minHeight: 140,
                borderTop: `4px solid ${card.color}`,
                transition: 'box-shadow 0.2s ease, transform 0.2s ease',
              }}
            >
              <div>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 8,
                  }}
                >
                  <span style={{ fontWeight: 600, fontSize: 15, color: 'var(--text)' }}>
                    {card.title}
                  </span>
                  <span className="mono small muted">{card.menuCode}</span>
                </div>
                <div className="small muted" style={{ marginTop: 4 }}>
                  {card.note}
                </div>
              </div>

              {/* Status metrics & Action Button Odoo-style */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'flex-end',
                  justifyContent: 'space-between',
                  marginTop: 18,
                  gap: 12,
                }}
              >
                {/* Status indicator pills */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  {card.countLate != null && card.countLate > 0 && (
                    <span className="small" style={{ color: 'var(--danger)', fontWeight: 600 }}>
                      ● {card.countLate} Terlambat
                    </span>
                  )}
                  {card.countWaiting != null && card.countWaiting > 0 && (
                    <span className="small muted">
                      ● {card.countWaiting} Menunggu
                    </span>
                  )}
                  {card.countLate === 0 && card.countWaiting === 0 && (
                    <span className="small muted" style={{ color: 'var(--chip-approved-fg)' }}>
                      ✓ Lancar
                    </span>
                  )}
                </div>

                {/* Big Action Button (Odoo "X TO PROCESS" Style) */}
                <button
                  type="button"
                  className="btn"
                  onClick={() => nav(menuPath(card.menuCode))}
                  style={{
                    background: card.countToProcess > 0 ? card.color : 'var(--surface-2)',
                    color: card.countToProcess > 0 ? '#FFFFFF' : 'var(--text)',
                    borderColor: card.countToProcess > 0 ? card.color : 'var(--line-input)',
                    fontWeight: 600,
                    fontSize: 12,
                    letterSpacing: '0.02em',
                    padding: '0 14px',
                    height: 32,
                    borderRadius: 6,
                    boxShadow: card.countToProcess > 0 ? '0 2px 6px rgba(0,0,0,0.15)' : 'none',
                  }}
                >
                  {card.actionText}
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* ---------------- BAGIAN 2: KPI UTAMA METRICS ---------------- */}
      {q.data?.kpiAvailable && (
        <div>
          <div className="lbl" style={{ marginBottom: 10 }}>Indikator Kinerja Utama</div>
          <div className="kpis">
            {q.data.kpis.map((k) => (
              <button
                key={k.label}
                type="button"
                className="card kpi"
                style={{
                  textAlign: 'left',
                  cursor: k.menuCode ? 'pointer' : 'default',
                  color: 'inherit',
                  font: 'inherit',
                }}
                onClick={() =>
                  k.menuCode && byApp.get(k.menuCode.split('-')[0]) && nav(menuPath(k.menuCode))
                }
              >
                <span className="small muted">{k.label}</span>
                <span className="v">{k.value}</span>
                <span className="small muted">{k.note}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* ---------------- BAGIAN 3: ANTREAN KERJA DOKUMEN & HUBUNGAN ---------------- */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'flex-start' }}>
        {/* Antrean Kerja Dokumen */}
        <div className="card" style={{ flex: '999 1 560px', minWidth: 0, overflow: 'hidden' }}>
          <div className="card-h">
            <span>Antrean Kerja & Dokumen Terbuka</span>
            <span className="small muted" style={{ fontWeight: 400 }}>
              Klik baris untuk membuka dokumen
            </span>
          </div>
          <DataTable
            rows={queueItems}
            rowKey={(r) => `${r.docType}-${r.docId}-${r.taskId ?? ''}`}
            onRowClick={(r) => nav(docLink(r.docType, r.docId, r.menuCode))}
            empty={q.isLoading ? 'Memuat antrean kerja…' : 'Tidak ada dokumen yang perlu ditindaklanjuti'}
            columns={[
              { key: 'docNo', label: 'Dokumen', mono: true },
              {
                key: 'summary',
                label: 'Uraian Transaksi',
                render: (r) => (
                  <>
                    {r.summary}
                    {r.amount != null && <span className="muted"> · {fmtRpShort(r.amount)}</span>}
                  </>
                ),
              },
              { key: 'status', label: 'Status', render: (r) => <StatusChip status={r.status} /> },
              {
                key: 'info',
                label: 'Waktu / Keterangan',
                render: (r) => (
                  <span className="muted">
                    {r.info} · {fmtAge(r.since)}
                  </span>
                ),
              },
            ]}
          />
        </div>

        {/* Hubungan Departemen */}
        {(relOut.length > 0 || relIn.length > 0) && (
          <div className="card" style={{ flex: '1 1 320px', minWidth: 0, overflow: 'hidden' }}>
            <div className="card-h">Hubungan Departemen</div>
            {relOut.length > 0 && (
              <>
                <div className="lbl" style={{ padding: '12px 16px 4px' }}>
                  Mengirim ke
                </div>
                {relOut.map(([k, text]) => (
                  <Rel
                    key={k}
                    k={k}
                    text={text}
                    color={all.byApp.get(k)?.color}
                    onClick={byApp.has(k) ? () => nav(`/app/${k}`) : undefined}
                  />
                ))}
              </>
            )}
            {relIn.length > 0 && (
              <>
                <div
                  className="lbl"
                  style={{
                    padding: '14px 16px 4px',
                    borderTop: '1px solid var(--line-soft)',
                    marginTop: 6,
                  }}
                >
                  Menerima dari
                </div>
                {relIn.map(([k, text]) => (
                  <Rel
                    key={k}
                    k={k}
                    text={text}
                    color={all.byApp.get(k)?.color}
                    onClick={byApp.has(k) ? () => nav(`/app/${k}`) : undefined}
                  />
                ))}
              </>
            )}
            <div style={{ height: 8 }} />
          </div>
        )}
      </div>

      {/* ---------------- BAGIAN 4: SEMUA MENU MODUL PER KATEGORI ---------------- */}
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">
          <span>Daftar Menu Lengkap {cur.name}</span>
          <span className="mono small muted">
            {cur.groups.reduce((acc, g) => acc + g.items.length, 0)} Menu Tersedia
          </span>
        </div>
        <div className="menu-grid">
          {cur.groups.map((g) => (
            <div key={g.name} className="grp">
              <div className="lbl" style={{ padding: '2px 8px 6px' }}>
                {g.name}
              </div>
              {g.items.map((it) => {
                const built = Boolean(MENU_IMPL[it.code]);
                return (
                  <button
                    key={it.code}
                    type="button"
                    className={`menu-tile${built ? '' : ' planned'}`}
                    title={it.fn}
                    onClick={() => nav(menuPath(it.code))}
                  >
                    <MenuIcon code={it.code} name={it.name} color={built ? cur.color : undefined} size={26} />
                    <span className="nm">{it.name}</span>
                    <span className="mono" style={{ fontSize: 10.5, color: 'var(--text-3)' }}>
                      {built ? it.code : it.phase}
                    </span>
                  </button>
                );
              })}
            </div>
          ))}
        </div>
      </div>

      {/* Modal Manage Users */}
      <ManageUsersModal open={usersModalOpen} onClose={() => setUsersModalOpen(false)} />
    </div>
  );
}

function Rel({
  k,
  text,
  color,
  onClick,
}: {
  k: string;
  text: string;
  color?: string;
  onClick?: () => void;
}) {
  return (
    <button
      className="rowbtn"
      type="button"
      style={{ borderTop: 0, padding: '7px 16px', cursor: onClick ? 'pointer' : 'default' }}
      onClick={onClick}
    >
      <span
        className="mono"
        style={{
          fontSize: 11,
          color: '#FFFFFF',
          borderRadius: 3,
          padding: '1px 5px',
          flex: 'none',
          background: color ?? 'var(--accent)',
        }}
      >
        {k}
      </span>
      <span style={{ fontSize: 13, color: 'var(--text-2)' }}>{text}</span>
    </button>
  );
}

/**
 * Generator Odoo-Style Operational Cards per Modul
 */
function getModuleOperationCards(app: string, queue: QueueItem[]): OperationCardDef[] {
  const code = app.toUpperCase();

  switch (code) {
    case 'SCM':
      return [
        {
          title: 'Penerimaan Bahan (Goods Receipts)',
          menuCode: 'SCM-20',
          icon: 'inbox',
          countToProcess: queue.filter((q) => q.menuCode?.startsWith('SCM-20')).length || 3,
          countLate: 1,
          countWaiting: 2,
          actionText: '3 TO PROCESS',
          color: '#D97706',
          note: 'Penerimaan RM/PM dari supplier & karantina QC',
        },
        {
          title: 'Transfer Internal Gudang',
          menuCode: 'SCM-25',
          icon: 'arrows',
          countToProcess: queue.filter((q) => q.menuCode?.startsWith('SCM-25')).length || 2,
          countLate: 0,
          countWaiting: 2,
          actionText: '2 TO PROCESS',
          color: '#2563EB',
          note: 'Perpindahan antar gudang & rak simpan',
        },
        {
          title: 'Pengeluaran ke Produksi',
          menuCode: 'SCM-22',
          icon: 'pin',
          countToProcess: queue.filter((q) => q.menuCode?.startsWith('SCM-22')).length || 4,
          countLate: 1,
          countWaiting: 3,
          actionText: '4 TO PROCESS',
          color: '#059669',
          note: 'Picking bahan penimbangan SPK manufaktur',
        },
        {
          title: 'Pengiriman Barang Jadi (Delivery Orders)',
          menuCode: 'SCM-26',
          icon: 'truck',
          countToProcess: queue.filter((q) => q.menuCode?.startsWith('SCM-26')).length || 5,
          countLate: 0,
          countWaiting: 1,
          actionText: '5 TO PROCESS',
          color: '#7C3AED',
          note: 'Surat jalan & ekspedisi produk herbal jadi',
        },
      ];

    case 'PRE':
      return [
        {
          title: 'Perintah Produksi (Work Orders)',
          menuCode: 'PRE-02',
          icon: 'factory',
          countToProcess: queue.filter((q) => q.menuCode?.startsWith('PRE-02')).length || 4,
          countLate: 1,
          countWaiting: 2,
          actionText: '4 TO PROCESS',
          color: '#0D9488',
          note: 'Work Order aktif & penugasan lini produksi',
        },
        {
          title: 'Penimbangan & Dispensing',
          menuCode: 'PRE-04',
          icon: 'scale',
          countToProcess: queue.filter((q) => q.menuCode?.startsWith('PRE-04')).length || 2,
          countLate: 0,
          countWaiting: 1,
          actionText: '2 TO PROCESS',
          color: '#D97706',
          note: 'Penimbangan bahan baku ekstrak & eksipien',
        },
        {
          title: 'Line Clearance & Sanitasi',
          menuCode: 'PRE-06',
          icon: 'broom',
          countToProcess: 1,
          countLate: 0,
          countWaiting: 1,
          actionText: '1 TO VERIFY',
          color: '#2563EB',
          note: 'Verifikasi kebersihan lini sebelum pengolahan',
        },
        {
          title: 'Catatan Batch Elektronik (e-BMR)',
          menuCode: 'PRE-08',
          icon: 'clipCheck',
          countToProcess: 3,
          countLate: 0,
          countWaiting: 3,
          actionText: '3 TO REVIEW',
          color: '#7C3AED',
          note: 'Dokumentasi riwayat batch siap rilis QA',
        },
      ];

    case 'FIN':
      return [
        {
          title: 'Faktur Penjualan (Invoices)',
          menuCode: 'FIN-10',
          icon: 'receipt',
          countToProcess: queue.filter((q) => q.menuCode?.startsWith('FIN-10')).length || 6,
          countLate: 2,
          countWaiting: 4,
          actionText: '6 TO VALIDATE',
          color: '#16A34A',
          note: 'Piutang dagang & faktur komersial distributor',
        },
        {
          title: 'Tagihan Pemasok (Vendor Bills)',
          menuCode: 'FIN-20',
          icon: 'receipt',
          countToProcess: queue.filter((q) => q.menuCode?.startsWith('FIN-20')).length || 4,
          countLate: 1,
          countWaiting: 3,
          actionText: '4 TO PAY',
          color: '#DC2626',
          note: 'Matching 3-way PO & rencana pembayaran hutang',
        },
        {
          title: 'Rekonsiliasi Kas & Bank',
          menuCode: 'FIN-31',
          icon: 'bank',
          countToProcess: 2,
          countLate: 0,
          countWaiting: 2,
          actionText: '2 REKONSILIASI',
          color: '#0284C7',
          note: 'Mutasi rekening giro koran & rekonsiliasi kas',
        },
        {
          title: 'Jurnal Umum Belum Posting',
          menuCode: 'FIN-03',
          icon: 'book',
          countToProcess: 3,
          countLate: 0,
          countWaiting: 3,
          actionText: '3 DRAFT JURNAL',
          color: '#7C3AED',
          note: 'Pencatatan penyesuaian & depresiasi aset',
        },
      ];

    case 'PRC':
      return [
        {
          title: 'Permintaan Pembelian (Purchase Requisitions)',
          menuCode: 'PRC-02',
          icon: 'clipList',
          countToProcess: queue.filter((q) => q.menuCode?.startsWith('PRC-02')).length || 5,
          countLate: 1,
          countWaiting: 4,
          actionText: '5 TO APPROVE',
          color: '#9333EA',
          note: 'Permintaan barang dari pabrik & lab RnD',
        },
        {
          title: 'Pesanan Pembelian (Purchase Orders)',
          menuCode: 'PRC-07',
          icon: 'cart',
          countToProcess: 3,
          countLate: 0,
          countWaiting: 3,
          actionText: '3 TO SEND',
          color: '#0891B2',
          note: 'PO rilis ke vendor bahan baku & kemasan',
        },
        {
          title: 'Evaluasi & Kualifikasi Vendor',
          menuCode: 'PRC-13',
          icon: 'star',
          countToProcess: 2,
          countLate: 0,
          countWaiting: 2,
          actionText: '2 TO REVIEW',
          color: '#D97706',
          note: 'Audit berkala performa pemasok CPOTB',
        },
      ];

    case 'QMS':
      return [
        {
          title: 'Inspeksi Bahan Baku Masuk (Incoming QC)',
          menuCode: 'QMS-02',
          icon: 'flask',
          countToProcess: 4,
          countLate: 1,
          countWaiting: 3,
          actionText: '4 SAMPEL UJI',
          color: '#0284C7',
          note: 'Sampling simplisia, ekstrak, dan bahan kemas',
        },
        {
          title: 'In-Process Control (IPC Manufaktur)',
          menuCode: 'QMS-04',
          icon: 'flask',
          countToProcess: 2,
          countLate: 0,
          countWaiting: 2,
          actionText: '2 LINI AKTIF',
          color: '#16A34A',
          note: 'Pemeriksaan kadar air, bobot, dan kebocoran',
        },
        {
          title: 'Pelulusan Produk Jadi (CoA & Batch Release)',
          menuCode: 'QMS-08',
          icon: 'badge',
          countToProcess: 3,
          countLate: 0,
          countWaiting: 3,
          actionText: '3 SIAP RILIS',
          color: '#7C3AED',
          note: 'Verifikasi CoA dan pelulusan QA batch herbal',
        },
        {
          title: 'Deviasi & Tindakan Korektif (CAPA)',
          menuCode: 'QMS-12',
          icon: 'alert',
          countToProcess: 1,
          countLate: 0,
          countWaiting: 1,
          actionText: '1 OPEN CAPA',
          color: '#DC2626',
          note: 'Penyelidikan akar masalah & pencegahan',
        },
      ];

    case 'RND':
      return [
        {
          title: 'Proyek Formulasi Herbal Baru',
          menuCode: 'RND-02',
          icon: 'beaker',
          countToProcess: 4,
          countLate: 0,
          countWaiting: 4,
          actionText: '4 PROYEK AKTIF',
          color: '#6366F1',
          note: 'Pengembangan ekstrak terstandar & sediaan baru',
        },
        {
          title: 'Uji Stabilitas Obat Tradisional',
          menuCode: 'RND-06',
          icon: 'thermo',
          countToProcess: 3,
          countLate: 0,
          countWaiting: 3,
          actionText: '3 TITIK UJI',
          color: '#EC4899',
          note: 'Uji stabilitas dipercepat & jangka panjang (real-time)',
        },
      ];

    case 'HC':
      return [
        {
          title: 'Pengajuan Cuti & Izin Menunggu Approval',
          menuCode: 'HC-08',
          icon: 'calendar',
          countToProcess: 3,
          countLate: 0,
          countWaiting: 3,
          actionText: '3 TO APPROVE',
          color: '#EC4899',
          note: 'Persetujuan cuti tahunan, sakit, dan lembur',
        },
        {
          title: 'Presensi & Shift Hari Ini',
          menuCode: 'HC-07',
          icon: 'clock',
          countToProcess: 0,
          countLate: 0,
          countWaiting: 0,
          actionText: '142 HADIR',
          color: '#10B981',
          note: 'Monitoring kehadiran shift 1, 2, dan 3',
        },
      ];

    case 'GA':
      return [
        {
          title: 'Work Order Pemeliharaan Fasilitas',
          menuCode: 'GA-04',
          icon: 'wrench',
          countToProcess: 3,
          countLate: 0,
          countWaiting: 3,
          actionText: '3 TO PROCESS',
          color: '#0284C7',
          note: 'Perbaikan HVAC, tata udara steril, dan sarana umum',
        },
        {
          title: 'Peminjaman Kendaraan Operasional',
          menuCode: 'GA-02',
          icon: 'car',
          countToProcess: 2,
          countLate: 0,
          countWaiting: 2,
          actionText: '2 AKTIF',
          color: '#F97316',
          note: 'Jadwal armada logistik & kendaraan dinas',
        },
      ];

    case 'SYS':
      return [
        {
          title: 'Manajemen Pengguna (Manage Users)',
          menuCode: 'SYS-03',
          icon: 'users',
          countToProcess: 0,
          countLate: 0,
          countWaiting: 0,
          actionText: 'KELOLA PENGGUNA',
          color: '#4F46E5',
          note: 'Tambah user, reset password, dan hak akses peran',
        },
        {
          title: 'Penomoran Dokumen ERP',
          menuCode: 'SYS-05',
          icon: 'hash',
          countToProcess: 0,
          countLate: 0,
          countWaiting: 0,
          actionText: 'KONFIGURASI',
          color: '#0284C7',
          note: 'Pengaturan prefix dan format nomor otomatis',
        },
        {
          title: 'Matriks Approval Bertingkat',
          menuCode: 'SYS-04',
          icon: 'sign',
          countToProcess: 0,
          countLate: 0,
          countWaiting: 0,
          actionText: 'ATUR APPROVER',
          color: '#059669',
          note: 'Batas nominal dan hak persetujuan manajerial',
        },
        {
          title: 'Audit Trail Transaksi',
          menuCode: 'SYS-14',
          icon: 'history',
          countToProcess: 0,
          countLate: 0,
          countWaiting: 0,
          actionText: 'LIHAT LOG',
          color: '#7C3AED',
          note: 'Catatan aktivitas pengguna dan kepatuhan 21 CFR Part 11',
        },
      ];

    default:
      return [
        {
          title: 'Antrean Operasional',
          menuCode: `${app}-01`,
          icon: 'clipList',
          countToProcess: queue.length || 1,
          countLate: 0,
          countWaiting: 0,
          actionText: `${queue.length || 1} TO PROCESS`,
          color: '#4F46E5',
          note: `Dokumen & transaksi ${app} yang memerlukan tindakan`,
        },
      ];
  }
}
