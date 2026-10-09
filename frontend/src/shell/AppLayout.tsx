import { useEffect, useState } from 'react';
import { Navigate, Outlet, useLocation, useNavigate, useParams } from 'react-router-dom';
import { GridIcon, Icon } from '../components/icons';
import { MenuIcon } from '../components/MenuIcon';
import { OdooAppIcon } from '../components/OdooAppIcon';
import { ThemeToggle } from '../components/ThemeToggle';
import { ManageUsersModal } from '../components/ManageUsersModal';
import { Spinner } from '../components/ui';
import { menuPath, useApps } from '../lib/meta';
import { NotificationBell, ProfileMenu } from './TopbarParts';
import { useOpenPalette } from './Shell';
import { NetworkStatusBadge } from '../components/NetworkStatusBadge';
import { SUB_SUB_TAXONOMY, findSubSubCategory } from '../lib/subSubMenus';

/**
 * Level 1 — Aplikasi Departemen & Navbar Modul Odoo-Style (Gambar Referensi User 2)
 * - Tanda Hijau: Ikon dan nama menu modul + tombol grid kembali ke launcher
 * - Tanda Merah: Sub-menu modul navigasi horizontal & dropdown kategori
 * - Baris 2: Judul aktif + Search bar Odoo-style dengan filter pill ("★ Overview ✕ Search...")
 * - Action bar: Theme toggle (Gelap/Terang), Notifikasi, Manage Users, Pengaturan Modul
 */
export function AppLayout() {
  const { app = '' } = useParams();
  const { byApp, byMenu, isLoading } = useApps();
  const nav = useNavigate();
  const loc = useLocation();
  const openPalette = useOpenPalette();
  const [open, setOpen] = useState<number | null>(null);
  const [usersModalOpen, setUsersModalOpen] = useState(false);
  const [searchVal, setSearchVal] = useState('');

  useEffect(() => setOpen(null), [loc.pathname]);

  if (isLoading) return <Spinner />;
  const cur = byApp.get(app);
  if (!cur) return <Navigate to="/" replace />;

  const menuCode = loc.pathname.match(/\/m\/([A-Z]+-\d+)/)?.[1];
  const activeMenu = menuCode ? byMenu.get(menuCode) : null;
  const activeGroup = activeMenu?.group;
  const isDash = loc.pathname === `/app/${app}` || loc.pathname === `/app/${app}/`;
  const isSettings = loc.pathname.startsWith(`/app/${app}/settings`);

  // Nama judul yang tampil di baris kedua navbar
  const pageTitle = isDash
    ? `Overview ${cur.name}`
    : isSettings
    ? `Pengaturan ${cur.name}`
    : activeMenu?.item.name ?? cur.name;

  const subSubInfo = menuCode ? findSubSubCategory(app, menuCode) : null;

  const renderItemButton = (it: { code: string; name: string; phase: string }) => (
    <button
      key={it.code}
      type="button"
      className={`ddi${it.code === menuCode ? ' active' : ''}`}
      style={{ alignItems: 'center', padding: '6px 14px' }}
      onClick={() => {
        setOpen(null);
        nav(menuPath(it.code));
      }}
    >
      <MenuIcon code={it.code} name={it.name} color={cur.color} size={20} />
      <span
        className="mono"
        style={{ fontSize: 11.5, color: 'var(--text-3)', width: 56, flex: 'none' }}
      >
        {it.code}
      </span>
      <span style={{ flex: 1, fontWeight: it.code === menuCode ? 600 : 400, textAlign: 'left' }}>
        {it.name}
      </span>
      {it.phase !== 'M0' && (
        <span className="mono" style={{ fontSize: 10.5, color: 'var(--text-3)' }}>
          {it.phase}
        </span>
      )}
    </button>
  );

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* ---------------- STICKY HEADER CONTAINER (TETAP STAYED DI ATAS SAAT DI-SCROLL) ---------------- */}
      <header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 100,
          background: 'var(--surface)',
          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06)',
        }}
      >
        {/* Garis aksen warna modul di bagian teratas */}
        <div style={{ height: 3, background: cur.color }} />

        {/* ---------------- BARIS 1: Top Navigation Bar (Gambar 2: Tanda Hijau & Merah) ---------------- */}
        <nav
          style={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 4,
            padding: '4px 14px',
            minHeight: 50,
            background: 'var(--surface)',
            borderBottom: '1px solid var(--line)',
            position: 'relative',
            zIndex: 30,
          }}
        >
          {/* === TANDA HIJAU: Tombol Grid Launcher + Icon & Nama Menu Modul === */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              paddingRight: 12,
              marginRight: 6,
              borderRight: '1px solid var(--line)',
            }}
          >
            {/* Tombol kembali ke launcher (Odoo app switcher 9 dots) */}
            <button
              className="iconbtn"
              type="button"
              aria-label="Kembali ke Dashboard Utama"
              title="Dashboard Awal Modul (Launcher)"
              onClick={() => nav('/')}
              style={{
                width: 34,
                height: 34,
                borderRadius: 6,
                color: 'var(--text-2)',
              }}
            >
              <GridIcon />
            </button>

            {/* Icon Modul & Nama Modul (Tanda Hijau) */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                cursor: 'pointer',
                userSelect: 'none',
              }}
              onClick={() => nav(`/app/${app}`)}
              title={`Buka Beranda ${cur.name}`}
            >
              <div
                style={{
                  width: 28,
                  height: 28,
                  borderRadius: 7,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flex: 'none',
                }}
              >
                <OdooAppIcon app={cur.code} size={28} />
              </div>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
                <span
                  style={{
                    fontWeight: 700,
                    fontSize: 15,
                    letterSpacing: '-0.01em',
                    color: 'var(--text)',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {cur.name}
                </span>
                <span
                  className="mono"
                  style={{
                    height: 18,
                    padding: '0 5px',
                    borderRadius: 3,
                    color: '#FFFFFF',
                    fontSize: 10,
                    fontWeight: 600,
                    display: 'inline-flex',
                    alignItems: 'center',
                    background: cur.color,
                  }}
                >
                  {cur.code}
                </span>
              </div>
            </div>
          </div>

          {/* === TANDA MERAH: Sub Menu Modul Navigasi Horizontal & Dropdown Ber-Sub-Sub Menu === */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
            {/* Sub menu: Overview / Ringkasan Dashboard Modul */}
            <button
              className={`ghost${isDash && open === null ? ' on' : ''}`}
              type="button"
              onClick={() => nav(`/app/${app}`)}
              style={{ fontWeight: isDash ? 600 : 500 }}
            >
              Overview
            </button>

            {/* Sub menu grup-grup modul dengan dropdown ber-sub-sub menu */}
            {cur.groups.map((g, i) => (
              <div key={g.name} style={{ position: 'relative' }}>
                <button
                  className={`ghost${open === i || (open === null && activeGroup === g.name) ? ' on' : ''}`}
                  type="button"
                  aria-expanded={open === i}
                  onClick={() => setOpen(open === i ? null : i)}
                  style={{ fontWeight: activeGroup === g.name ? 600 : 500 }}
                >
                  {g.name}
                  <Icon name="chevron" size={13} />
                </button>

                {open === i && (
                  <div
                    className="pop"
                    style={{
                      top: 42,
                      left: 0,
                      minWidth: 360,
                      maxWidth: 520,
                      maxHeight: '75vh',
                      overflow: 'auto',
                      borderRadius: 8,
                      boxShadow: 'var(--shadow-pop)',
                      animation: 'fadeIn 0.15s ease',
                      zIndex: 120,
                      padding: '6px 0',
                    }}
                  >
                    <div
                      style={{
                        padding: '6px 14px 4px',
                        fontSize: 11.5,
                        fontWeight: 700,
                        color: 'var(--text-3)',
                        textTransform: 'uppercase',
                        letterSpacing: '0.05em',
                        borderBottom: '1px solid var(--line-soft)',
                        marginBottom: 4,
                      }}
                    >
                      Sub Menu: {g.name}
                    </div>

                    {(() => {
                      const subSubs = SUB_SUB_TAXONOMY[app]?.[g.name];
                      if (!subSubs) {
                        return g.items.map((it) => renderItemButton(it));
                      }

                      const handledCodes = new Set(subSubs.flatMap((s) => s.codes));
                      const remainingItems = g.items.filter((it) => !handledCodes.has(it.code));

                      return (
                        <>
                          {subSubs.map((sub, sIdx) => {
                            const subItems = g.items.filter((it) => sub.codes.includes(it.code));
                            if (subItems.length === 0) return null;
                            return (
                              <div key={sub.title} style={{ marginBottom: 4 }}>
                                <div
                                  style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 6,
                                    padding: '7px 14px 3px',
                                    fontSize: 11,
                                    fontWeight: 700,
                                    color: 'var(--text-2)',
                                    background: 'var(--surface-sunken)',
                                    borderTop: sIdx > 0 ? '1px solid var(--line-soft)' : 'none',
                                    marginTop: sIdx > 0 ? 4 : 0,
                                  }}
                                >
                                  <span style={{ fontSize: 13 }}>{sub.icon}</span>
                                  <span>{sub.title}</span>
                                </div>
                                {subItems.map((it) => renderItemButton(it))}
                              </div>
                            );
                          })}

                          {remainingItems.length > 0 && (
                            <div style={{ marginBottom: 4 }}>
                              <div
                                style={{
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: 6,
                                  padding: '7px 14px 3px',
                                  fontSize: 11,
                                  fontWeight: 700,
                                  color: 'var(--text-2)',
                                  background: 'var(--surface-sunken)',
                                  borderTop: '1px solid var(--line-soft)',
                                }}
                              >
                                <span>📋</span>
                                <span>Menu Lainnya</span>
                              </div>
                              {remainingItems.map((it) => renderItemButton(it))}
                            </div>
                          )}
                        </>
                      );
                    })()}
                  </div>
                )}
              </div>
            ))}

            {/* Sub menu: Konfigurasi / Pengaturan Modul */}
            <button
              className={`ghost${isSettings ? ' on' : ''}`}
              type="button"
              onClick={() => nav(`/app/${app}/settings`)}
              style={{ fontWeight: isSettings ? 600 : 500 }}
            >
              Konfigurasi
            </button>
          </div>

          {/* Spacer */}
          <div style={{ flex: 1 }} />

          {/* === Kanan: Action bar (Theme toggle, notif, manage users, settings, profile) === */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            {/* Offline/Online Network Status Badge */}
            <NetworkStatusBadge />

            {/* Theme Switcher (Gelap / Terang) */}
            <ThemeToggle />

            {/* Quick search shortcut trigger */}
            <button
              className="iconbtn"
              type="button"
              aria-label="Cari Cepat (Ctrl+K)"
              title="Cari Cepat (Ctrl+K)"
              onClick={openPalette}
            >
              <Icon name="search" size={18} />
            </button>

            {/* Notifikasi Lonceng */}
            <NotificationBell />

            {/* Quick Manage Users button */}
            <button
              className="iconbtn"
              type="button"
              aria-label="Kelola Pengguna (Manage Users)"
              title="Kelola Pengguna (Manage Users)"
              onClick={() => setUsersModalOpen(true)}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
            </button>

            {/* Pengaturan Modul */}
            <button
              className={`iconbtn${isSettings ? ' on' : ''}`}
              type="button"
              aria-label="Pengaturan Modul Ini"
              title="Pengaturan Modul Ini"
              onClick={() => nav(`/app/${app}/settings`)}
            >
              <Icon name="gear" size={18} />
            </button>

            {/* Profile User */}
            <ProfileMenu />
          </div>
        </nav>

        {/* ---------------- BARIS 2: Odoo-Style Breadcrumb & Search Filter Bar (Gambar 2) ---------------- */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 12,
            padding: '8px 18px',
            background: 'var(--surface-2)',
            borderBottom: '1px solid var(--line)',
            minHeight: 46,
          }}
        >
          {/* Title & Status Breadcrumb di Kiri dengan Hirarki Sub-Sub Menu */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <span
              style={{
                color: '#F59E0B',
                fontSize: 16,
                lineHeight: 1,
                userSelect: 'none',
              }}
              title="Menu Favorit"
            >
              ★
            </span>
            <span
              style={{
                fontWeight: 600,
                fontSize: 14,
                color: 'var(--text-2)',
                cursor: 'pointer',
              }}
              onClick={() => nav(`/app/${app}`)}
              title={`Kembali ke Overview ${cur.name}`}
            >
              {cur.shortName}
            </span>
            {activeGroup && (
              <>
                <span style={{ color: 'var(--text-3)', fontSize: 12 }}>/</span>
                <span style={{ color: 'var(--text-2)', fontSize: 13.5, fontWeight: 500 }}>
                  {activeGroup}
                </span>
              </>
            )}
            {subSubInfo && (
              <>
                <span style={{ color: 'var(--text-3)', fontSize: 12 }}>/</span>
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 4,
                    fontSize: 12,
                    fontWeight: 600,
                    padding: '2px 8px',
                    borderRadius: 12,
                    background: 'var(--surface-sunken)',
                    border: '1px solid var(--line)',
                    color: 'var(--text)',
                  }}
                >
                  <span>{subSubInfo.icon}</span>
                  <span>{subSubInfo.subSubTitle}</span>
                </span>
              </>
            )}
            <span style={{ color: 'var(--text-3)', fontSize: 12 }}>/</span>
            <span style={{ fontWeight: 600, fontSize: 14, color: 'var(--text)' }}>
              {pageTitle}
            </span>
            {activeMenu && (
              <span
                className="mono"
                style={{
                  fontSize: 11,
                  color: 'var(--text-3)',
                  padding: '1px 6px',
                  borderRadius: 4,
                  background: 'var(--surface)',
                  border: '1px solid var(--line)',
                }}
              >
                {activeMenu.item.code}
              </span>
            )}
          </div>

          {/* Center/Right: Odoo-Style Search Filter Box */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flex: '0 1 480px' }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                width: '100%',
                height: 32,
                background: 'var(--surface)',
                border: '1px solid var(--line-input)',
                borderRadius: 6,
                padding: '0 8px',
                gap: 6,
                boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
              }}
            >
              <span style={{ color: 'var(--text-3)', flex: 'none', display: 'flex' }}>
                <Icon name="search" size={14} />
              </span>

              {/* Filter Tag Pill */}
              <div
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 5,
                  background: 'var(--search-pill-bg)',
                  color: 'var(--search-pill-fg)',
                  padding: '2px 8px',
                  borderRadius: 4,
                  fontSize: 12,
                  fontWeight: 500,
                  whiteSpace: 'nowrap',
                  flex: 'none',
                }}
              >
                <span style={{ color: '#F59E0B' }}>★</span>
                <span>{isDash ? 'Overview' : activeMenu?.item.name ?? cur.shortName}</span>
                <button
                  type="button"
                  onClick={() => nav(`/app/${app}`)}
                  style={{
                    background: 'transparent',
                    border: 0,
                    cursor: 'pointer',
                    padding: 0,
                    color: 'inherit',
                    display: 'flex',
                    alignItems: 'center',
                    opacity: 0.7,
                  }}
                  title="Reset ke Overview"
                >
                  ✕
                </button>
              </div>

              {/* Input pencarian Odoo-style */}
              <input
                type="text"
                placeholder="Search atau filter..."
                value={searchVal}
                onChange={(e) => setSearchVal(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') openPalette();
                }}
                style={{
                  border: 0,
                  outline: 'none',
                  background: 'transparent',
                  fontSize: 12.5,
                  color: 'var(--text)',
                  width: '100%',
                  padding: 0,
                }}
              />

              <button
                type="button"
                className="iconbtn"
                onClick={openPalette}
                style={{ width: 22, height: 22, flex: 'none', color: 'var(--text-3)' }}
                title="Filter Lanjutan (Ctrl+K)"
              >
                <Icon name="chevron" size={12} />
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Backdrop penutup dropdown */}
      {open !== null && (
        <div
          onClick={() => setOpen(null)}
          style={{ position: 'fixed', inset: 0, zIndex: 90 }}
        />
      )}

      {/* Konten Halaman (Dashboard Modul / Menu Page) */}
      <div style={{ flex: 1 }}>
        <Outlet />
      </div>

      {/* Modal Manage Users */}
      <ManageUsersModal open={usersModalOpen} onClose={() => setUsersModalOpen(false)} />
    </div>
  );
}
