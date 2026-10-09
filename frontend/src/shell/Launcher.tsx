import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { LauncherData, QueueItem } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Icon } from '../components/icons';
import { OdooAppIcon } from '../components/OdooAppIcon';
import { ThemeToggle } from '../components/ThemeToggle';
import { ManageUsersModal } from '../components/ManageUsersModal';
import { fmtAge, greeting, longDate } from '../lib/format';
import { menuPath, recentMenus, useApps, useAllApps } from '../lib/meta';
import { NotificationBell, PlantSwitcher, ProfileMenu } from './TopbarParts';
import { useOpenPalette } from './Shell';
import { NetworkStatusBadge } from '../components/NetworkStatusBadge';

interface CustomAppTile {
  id: string;
  code: string;
  name: string;
  path: string;
  badge?: number;
  iconCode: string;
  subtext?: string;
}

/**
 * Level 0 — Dashboard Awal Menu Modul bergaya Odoo (Gambar Referensi User 1)
 * Grid ikon aplikasi modern, latar gradien elegan, banner informasi sistem,
 * dan antrean tindakan kerja cepat.
 */
export function Launcher() {
  const { me } = useAuth();
  const { byMenu } = useApps();
  const all = useAllApps();
  const nav = useNavigate();
  const openPalette = useOpenPalette();
  const [usersModalOpen, setUsersModalOpen] = useState(false);
  const [showQueue, setShowQueue] = useState(false);

  const data = useQuery({
    queryKey: ['launcher'],
    queryFn: () => api.get<LauncherData>('/dashboard/launcher'),
    refetchInterval: 60_000,
  });

  const plant = me?.plants.find((p) => p.id === me.plantId);
  const recent = recentMenus().map((c) => byMenu.get(c)).filter(Boolean);
  const approvalCount = data.data?.approvalCount ?? 0;
  const badges = data.data?.badges ?? {};

  // Daftar aplikasi utama yang ditampilkan di grid bergaya Odoo (seperti Gambar 1)
  const appTiles: CustomAppTile[] = [
    {
      id: 'dash',
      code: 'DASH',
      name: 'Dashboards',
      path: '/app/FIN',
      iconCode: 'DASH',
      subtext: 'Ringkasan Eksekutif',
    },
    {
      id: 'scm',
      code: 'SCM',
      name: 'Inventory',
      path: '/app/SCM',
      badge: badges.SCM,
      iconCode: 'SCM',
      subtext: 'Gudang & Logistik',
    },
    {
      id: 'pre',
      code: 'PRE',
      name: 'Manufacturing',
      path: '/app/PRE',
      badge: badges.PRE,
      iconCode: 'PRE',
      subtext: 'Produksi & Pabrik',
    },
    {
      id: 'prc',
      code: 'PRC',
      name: 'Purchase',
      path: '/app/PRC',
      badge: badges.PRC,
      iconCode: 'PRC',
      subtext: 'Pengadaan Barang',
    },
    {
      id: 'fin',
      code: 'FIN',
      name: 'Accounting',
      path: '/app/FIN',
      badge: badges.FIN,
      iconCode: 'FIN',
      subtext: 'Keuangan & Akuntansi',
    },
    {
      id: 'qms',
      code: 'QMS',
      name: 'Quality',
      path: '/app/QMS',
      badge: badges.QMS,
      iconCode: 'QMS',
      subtext: 'Penjaminan Mutu & QC',
    },
    {
      id: 'rnd',
      code: 'RND',
      name: 'R & D',
      path: '/app/RND',
      badge: badges.RND,
      iconCode: 'RND',
      subtext: 'Riset & Formulasi',
    },
    {
      id: 'hc',
      code: 'HC',
      name: 'Employees',
      path: '/app/HC',
      badge: badges.HC,
      iconCode: 'HC',
      subtext: 'SDM & Personalia',
    },
    {
      id: 'ga',
      code: 'GA',
      name: 'Maintenance',
      path: '/app/GA',
      badge: badges.GA,
      iconCode: 'GA',
      subtext: 'Umum & Fasilitas',
    },
    {
      id: 'ess',
      code: 'ESS',
      name: 'To-do / ESS',
      path: '/app/ESS',
      badge: badges.ESS,
      iconCode: 'ESS',
      subtext: 'Layanan Mandiri',
    },
    {
      id: 'appr',
      code: 'APPR',
      name: 'Approval Sign',
      path: menuPath('ESS-10'),
      badge: approvalCount,
      iconCode: 'APPROVAL',
      subtext: 'Persetujuan Dokumen',
    },
    {
      id: 'users',
      code: 'USERS',
      name: 'Manage Users',
      path: '#users',
      iconCode: 'USERS',
      subtext: 'Pengguna & Hak Akses',
    },
    {
      id: 'sys',
      code: 'SYS',
      name: 'Settings',
      path: '/app/SYS',
      iconCode: 'SYS',
      subtext: 'Pengaturan Sistem',
    },
  ];

  const handleTileClick = (tile: CustomAppTile) => {
    if (tile.id === 'users') {
      setUsersModalOpen(true);
      return;
    }
    nav(tile.path);
  };

  const openItem = (q: QueueItem) => nav(menuPath(q.menuCode, q.docId));

  return (
    <div
      style={{
        minHeight: '100vh',
        background: 'var(--launcher-bg)',
        display: 'flex',
        flexDirection: 'column',
        position: 'relative',
        transition: 'background 0.3s ease',
      }}
    >
      {/* ---------------- Topbar Header (Bergaya Odoo Header) ---------------- */}
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
          padding: '10px 24px',
          minHeight: 56,
          background: 'transparent',
          position: 'relative',
          zIndex: 10,
        }}
      >
        {/* Brand & Left items */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          {/* Chevron drawer button */}
          <button
            type="button"
            className="iconbtn"
            style={{ color: 'var(--text-2)' }}
            onClick={() => setShowQueue(!showQueue)}
            title="Buka panel antrean kerja & aktivitas"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M9 18l6-6-6-6" />
            </svg>
          </button>

          {/* Logo brand Herbatech */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              cursor: 'pointer',
            }}
            onClick={() => nav('/')}
          >
            <div
              className="mono"
              style={{
                width: 28,
                height: 28,
                borderRadius: 7,
                background: '#4F46E5',
                color: '#FFFFFF',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 14,
                fontWeight: 700,
                boxShadow: '0 2px 6px rgba(79, 70, 229, 0.4)',
              }}
            >
              H
            </div>
            <div style={{ fontWeight: 700, fontSize: 16, letterSpacing: '-0.01em' }}>
              Herbatech <span style={{ fontWeight: 400, color: 'var(--text-3)' }}>ERP</span>
            </div>
          </div>

          <PlantSwitcher />
        </div>

        {/* Center Search Bar (Ctrl+K trigger) */}
        <button
          type="button"
          onClick={openPalette}
          style={{
            flex: '0 1 420px',
            position: 'relative',
            height: 38,
            border: '1px solid var(--line-input)',
            borderRadius: 8,
            background: 'var(--surface)',
            textAlign: 'left',
            padding: '0 68px 0 36px',
            color: 'var(--text-3)',
            cursor: 'text',
            fontSize: 13.5,
            boxShadow: '0 2px 5px rgba(0,0,0,0.03)',
            display: 'flex',
            alignItems: 'center',
          }}
        >
          <span style={{ position: 'absolute', left: 11, top: 10, color: 'var(--text-3)' }}>
            <Icon name="search" size={16} />
          </span>
          Cari modul, dokumen, batch, atau aksi…
          <span
            className="mono"
            style={{
              position: 'absolute',
              right: 8,
              top: 8,
              fontSize: 11,
              border: '1px solid var(--line-input)',
              borderRadius: 4,
              padding: '2px 6px',
              background: 'var(--surface-2)',
              color: 'var(--text-2)',
            }}
          >
            Ctrl K
          </span>
        </button>

        {/* Right Action Icons (Theme toggle, notif badges, settings, avatar) */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          {/* Offline/Online Network Status Badge */}
          <NetworkStatusBadge />

          {/* Theme Switcher (Gelap / Terang) */}
          <ThemeToggle />

          {/* Activity / Task counter (Clock icon with badge 1 seperti di gambar) */}
          <button
            type="button"
            className="iconbtn"
            title="Antrean Approval & Tugas"
            onClick={() => nav(menuPath('ESS-10'))}
            style={{ position: 'relative' }}
          >
            <Icon name="clock" size={19} />
            {approvalCount > 0 && (
              <span
                style={{
                  position: 'absolute',
                  top: 4,
                  right: 4,
                  minWidth: 16,
                  height: 16,
                  borderRadius: 8,
                  background: '#EF4444',
                  color: '#FFFFFF',
                  fontSize: 10,
                  fontWeight: 700,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: '0 4px',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.3)',
                }}
              >
                {approvalCount}
              </span>
            )}
          </button>

          {/* Notification bell (Bell dengan badge 4 seperti di gambar) */}
          <NotificationBell />

          {/* Quick Manage Users button */}
          <button
            type="button"
            className="iconbtn"
            title="Kelola Pengguna & Akses (Manage Users)"
            onClick={() => setUsersModalOpen(true)}
          >
            <Icon name="gear" size={19} />
          </button>

          {/* User Profile Avatar (Avatar bulat merah dengan inisial seperti di gambar 1) */}
          <ProfileMenu />
        </div>
      </header>

      {/* ---------------- Main Container: Banner & App Launcher Grid ---------------- */}
      <main
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '20px 24px 60px',
          maxWidth: 1180,
          width: '100%',
          margin: '0 auto',
        }}
      >
        {/* Banner Status Sistem / Lisensi (Persis seperti banner di Gambar 1) */}
        <div
          style={{
            width: '100%',
            maxWidth: 780,
            background: 'var(--launcher-banner-bg)',
            border: '1px solid var(--launcher-banner-border)',
            borderRadius: 10,
            padding: '12px 20px',
            marginBottom: 36,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 16,
            boxShadow: '0 2px 8px rgba(0,0,0,0.03)',
            color: 'var(--launcher-banner-fg)',
            fontSize: 13.5,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ fontSize: 18 }}>✨</span>
            <div>
              <strong>Herbatech ERP Enterprise Edition</strong> · Seluruh 10 modul aktif terintegrasi ·{' '}
              <span style={{ opacity: 0.9 }}>Plant Aktif: {plant?.name ?? 'Plant 1 Cikarang'}</span>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'nowrap' }}>
            <button
              type="button"
              className="btn btn-sm"
              onClick={() => setUsersModalOpen(true)}
              style={{
                background: 'var(--surface)',
                borderColor: 'var(--launcher-banner-border)',
                fontWeight: 600,
              }}
            >
              Kelola Pengguna
            </button>
            <button
              type="button"
              className="btn btn-sm btn-dark"
              onClick={() => nav('/app/SYS/settings')}
              style={{ fontWeight: 600 }}
            >
              Pengaturan Modul
            </button>
          </div>
        </div>

        {/* Grid Ikon Modul (Odoo-Style App Tile Grid - Mengikuti Gambar 1) */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(118px, 1fr))',
            gap: '24px 20px',
            width: '100%',
            maxWidth: 880,
            justifyItems: 'center',
          }}
        >
          {appTiles.map((tile) => (
            <button
              key={tile.id}
              type="button"
              onClick={() => handleTileClick(tile)}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 9,
                background: 'transparent',
                border: 0,
                cursor: 'pointer',
                padding: '6px 4px',
                borderRadius: 14,
                transition: 'transform 0.18s ease, filter 0.18s ease',
                position: 'relative',
                color: 'var(--text)',
                textAlign: 'center',
                width: 110,
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = 'translateY(-4px)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = 'translateY(0)';
              }}
            >
              {/* Box Ikon Rounded Square (Bergaya Odoo Card) */}
              <div
                style={{
                  width: 74,
                  height: 74,
                  borderRadius: 18,
                  background: 'var(--launcher-card-bg)',
                  border: '1px solid var(--launcher-card-border)',
                  boxShadow: 'var(--launcher-card-shadow)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  position: 'relative',
                  transition: 'box-shadow 0.2s ease, border-color 0.2s ease',
                }}
              >
                <OdooAppIcon app={tile.iconCode} size={64} />

                {/* Badge counter merah bila ada tindakan yang perlu diproses */}
                {tile.badge != null && tile.badge > 0 && (
                  <span
                    style={{
                      position: 'absolute',
                      top: -4,
                      right: -4,
                      minWidth: 20,
                      height: 20,
                      borderRadius: 10,
                      background: '#EF4444',
                      color: '#FFFFFF',
                      fontSize: 11,
                      fontWeight: 700,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      padding: '0 5px',
                      boxShadow: '0 2px 5px rgba(239, 68, 68, 0.4)',
                      border: '2px solid var(--launcher-card-bg)',
                    }}
                  >
                    {tile.badge}
                  </span>
                )}
              </div>

              {/* Nama Modul di bawah Ikon */}
              <div
                style={{
                  fontSize: 13,
                  fontWeight: 500,
                  color: 'var(--text)',
                  lineHeight: 1.25,
                  maxWidth: 104,
                  wordBreak: 'break-word',
                  textShadow: '0 1px 2px rgba(0,0,0,0.05)',
                }}
              >
                {tile.name}
              </div>
            </button>
          ))}
        </div>

        {/* Sub-greeting ringkas di bawah grid */}
        <div
          style={{
            marginTop: 48,
            textAlign: 'center',
            color: 'var(--text-3)',
            fontSize: 13,
            display: 'flex',
            alignItems: 'center',
            gap: 12,
          }}
        >
          <span>{longDate()}</span>
          <span>·</span>
          <span>{greeting()}, {me?.fullName.split(' (')[0]}</span>
          <span>·</span>
          <button
            type="button"
            className="btn btn-sm"
            style={{ fontSize: 12, height: 26 }}
            onClick={() => setShowQueue(!showQueue)}
          >
            {showQueue ? 'Sembunyikan Antrean' : 'Lihat Antrean Kerja'}
          </button>
        </div>
      </main>

      {/* ---------------- Drawer Antrean Tindakan / Perlu Tindakan Anda ---------------- */}
      {showQueue && (
        <aside
          style={{
            position: 'fixed',
            top: 56,
            left: 0,
            bottom: 0,
            width: 380,
            maxWidth: '90vw',
            background: 'var(--surface)',
            borderRight: '1px solid var(--line)',
            boxShadow: 'var(--shadow-pop)',
            zIndex: 30,
            display: 'flex',
            flexDirection: 'column',
            overflowY: 'auto',
            animation: 'slideInLeft 0.25s ease-out',
          }}
        >
          <div
            style={{
              padding: '16px 20px',
              borderBottom: '1px solid var(--line)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <div>
              <div style={{ fontWeight: 600, fontSize: 15 }}>Perlu Tindakan Anda</div>
              <div className="small muted">{approvalCount} dokumen menunggu keputusan</div>
            </div>
            <button type="button" className="iconbtn" onClick={() => setShowQueue(false)}>
              <Icon name="x" size={16} />
            </button>
          </div>

          <div style={{ padding: '12px 16px', display: 'flex', flexDirection: 'column', gap: 10 }}>
            {(data.data?.tasks ?? []).length === 0 ? (
              <div className="small muted" style={{ padding: '16px 0', textAlign: 'center' }}>
                Tidak ada dokumen yang menunggu tindakan Anda.
              </div>
            ) : (
              data.data?.tasks.map((t) => (
                <button
                  key={`${t.docType}-${t.docId}`}
                  type="button"
                  className="rowbtn"
                  onClick={() => openItem(t)}
                  style={{
                    borderRadius: 8,
                    background: 'var(--surface-2)',
                    border: '1px solid var(--line-soft)',
                    padding: '10px 12px',
                  }}
                >
                  <span
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: 2,
                      marginTop: 6,
                      flex: 'none',
                      background: all.byApp.get(t.menuCode?.split('-')[0] ?? '')?.color ?? 'var(--accent)',
                    }}
                  />
                  <span style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
                    <span className="mono" style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-3)' }}>
                      {t.docNo}
                    </span>
                    <span style={{ fontSize: 13, fontWeight: 500 }}>{t.summary}</span>
                  </span>
                  <span className="mono small muted" style={{ whiteSpace: 'nowrap' }}>
                    {fmtAge(t.since)}
                  </span>
                </button>
              ))
            )}
          </div>

          {/* Terakhir dibuka */}
          {recent.length > 0 && (
            <div style={{ marginTop: 'auto', borderTop: '1px solid var(--line)', padding: '12px 16px' }}>
              <div className="lbl" style={{ marginBottom: 8 }}>
                Terakhir Dibuka
              </div>
              {recent.slice(0, 4).map(
                (r) =>
                  r && (
                    <button
                      key={r.item.code}
                      className="rowbtn"
                      type="button"
                      style={{ alignItems: 'center', padding: '6px 8px' }}
                      onClick={() => nav(menuPath(r.item.code))}
                    >
                      <span className="mono" style={{ fontSize: 11, color: 'var(--text-3)', width: 56 }}>
                        {r.item.code}
                      </span>
                      <span style={{ fontSize: 13 }}>{r.item.name}</span>
                    </button>
                  ),
              )}
            </div>
          )}
        </aside>
      )}

      {/* Modal Manage Users */}
      <ManageUsersModal open={usersModalOpen} onClose={() => setUsersModalOpen(false)} />
    </div>
  );
}
