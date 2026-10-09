import { useEffect, useState } from 'react';
import { Navigate, Outlet, useLocation, useNavigate, useParams } from 'react-router-dom';
import { GridIcon, Icon } from '../components/icons';
import { MenuIcon } from '../components/MenuIcon';
import { Spinner } from '../components/ui';
import { menuPath, useApps } from '../lib/meta';
import { NotificationBell, ProfileMenu } from './TopbarParts';
import { useOpenPalette } from './Shell';

/** Level 1 — aplikasi departemen (PRD §15.2): garis aksen, navbar grup menu dropdown, gear pengaturan. */
export function AppLayout() {
  const { app = '' } = useParams();
  const { byApp, byMenu, isLoading } = useApps();
  const nav = useNavigate();
  const loc = useLocation();
  const openPalette = useOpenPalette();
  const [open, setOpen] = useState<number | null>(null);
  useEffect(() => setOpen(null), [loc.pathname]);

  if (isLoading) return <Spinner />;
  const cur = byApp.get(app);
  if (!cur) return <Navigate to="/" replace />;

  const menuCode = loc.pathname.match(/\/m\/([A-Z]+-\d+)/)?.[1];
  const activeGroup = menuCode ? byMenu.get(menuCode)?.group : undefined;
  const isDash = loc.pathname === `/app/${app}` || loc.pathname === `/app/${app}/`;
  const isSettings = loc.pathname.startsWith(`/app/${app}/settings`);

  return (
    <div style={{ minHeight: '100%' }}>
      <div style={{ height: 3, background: cur.color }} />
      <nav style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 2, padding: '6px 12px', minHeight: 52, background: 'var(--surface)', borderBottom: '1px solid var(--line)', position: 'relative', zIndex: 20 }}>
        <button className="iconbtn" type="button" aria-label="Kembali ke launcher" title="Launcher (G lalu H)" onClick={() => nav('/')}><GridIcon /></button>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '0 12px 0 6px', marginRight: 6, borderRight: '1px solid var(--line)' }}>
          <span className="mono" style={{ height: 22, padding: '0 6px', borderRadius: 4, color: '#FFFFFF', fontSize: 11, fontWeight: 500, display: 'flex', alignItems: 'center', background: cur.color }}>{cur.code}</span>
          <span style={{ fontWeight: 600, fontSize: 14.5, whiteSpace: 'nowrap' }}>{cur.name}</span>
        </div>
        <button className={`ghost${isDash && open === null ? ' on' : ''}`} type="button" onClick={() => nav(`/app/${app}`)}>Beranda</button>
        {cur.groups.map((g, i) => (
          <div key={g.name} style={{ position: 'relative' }}>
            <button className={`ghost${open === i || (open === null && activeGroup === g.name) ? ' on' : ''}`} type="button"
              aria-expanded={open === i} onClick={() => setOpen(open === i ? null : i)}>
              {g.name}<Icon name="chevron" size={14} />
            </button>
            {open === i && (
              <div className="pop" style={{ top: 42, left: 0, minWidth: 320, maxWidth: 440, maxHeight: '70vh', overflow: 'auto' }}>
                <div className="lbl" style={{ padding: '6px 10px 4px' }}>{g.name}</div>
                {g.items.map((it) => (
                  <button key={it.code} type="button" className={`ddi${it.code === menuCode ? ' active' : ''}`} style={{ alignItems: 'center' }}
                    onClick={() => nav(menuPath(it.code))}>
                    <MenuIcon code={it.code} name={it.name} color={cur.color} size={24} />
                    <span className="mono" style={{ fontSize: 11.5, color: 'var(--text-3)', width: 52, flex: 'none' }}>{it.code}</span>
                    <span style={{ flex: 1 }}>{it.name}</span>
                    {it.phase !== 'M0' && <span className="mono" style={{ fontSize: 10.5, color: 'var(--text-3)' }}>{it.phase}</span>}
                  </button>
                ))}
              </div>
            )}
          </div>
        ))}
        <div style={{ flex: 1 }} />
        <button className="iconbtn" type="button" aria-label="Cari (Ctrl+K)" title="Cari (Ctrl+K)" onClick={openPalette}><Icon name="search" size={19} /></button>
        <NotificationBell />
        <button className={`iconbtn${isSettings ? ' on' : ''}`} type="button" aria-label="Pengaturan aplikasi" title="Pengaturan aplikasi" onClick={() => nav(`/app/${app}/settings`)}>
          <Icon name="gear" size={19} />
        </button>
        <ProfileMenu />
      </nav>
      {open !== null && <div onClick={() => setOpen(null)} style={{ position: 'fixed', inset: 0, zIndex: 10 }} />}
      <Outlet />
    </div>
  );
}
