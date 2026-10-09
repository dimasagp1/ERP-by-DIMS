import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { NotificationView } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Icon } from '../components/icons';
import { useClickOutside, useToast } from '../components/ui';
import { fmtDateTime } from '../lib/format';
import { ManageUsersModal } from '../components/ManageUsersModal';

/** Lonceng navbar (PRD §13): notifikasi approval, penolakan, pengingat, eskalasi. */
export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const ref = useClickOutside<HTMLDivElement>(() => setOpen(false));
  const qc = useQueryClient();
  const nav = useNavigate();
  const q = useQuery({
    queryKey: ['notifications'],
    queryFn: () => api.get<{ unread: number; items: NotificationView[] }>('/notifications'),
    refetchInterval: 30_000,
  });
  const readAll = useMutation({
    mutationFn: () => api.post('/notifications/read-all'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });
  const openItem = async (n: NotificationView) => {
    if (!n.readAt) await api.post(`/notifications/${n.id}/read`);
    qc.invalidateQueries({ queryKey: ['notifications'] });
    setOpen(false);
    if (n.link) nav(n.link);
  };
  const unread = q.data?.unread ?? 0;
  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button className={`iconbtn${open ? ' on' : ''}`} type="button" aria-label={`Notifikasi, ${unread} belum dibaca`} onClick={() => setOpen(!open)}>
        <Icon name="bell" size={19} />
        {unread > 0 && <span className="dot" />}
      </button>
      {open && (
        <div className="pop" style={{ right: 0, top: 42, width: 380, maxHeight: 460, overflow: 'auto', padding: 0 }}>
          <div className="card-h" style={{ fontSize: 13.5 }}>
            Notifikasi
            {unread > 0 && <button className="btn btn-sm" type="button" onClick={() => readAll.mutate()}>Tandai semua dibaca</button>}
          </div>
          {(q.data?.items ?? []).length === 0 && <div className="empty">Tidak ada notifikasi</div>}
          {q.data?.items.map((n) => (
            <button key={n.id} type="button" className="rowbtn" onClick={() => openItem(n)} style={{ borderTop: 0, borderBottom: '1px solid var(--line-soft)' }}>
              <span style={{ width: 7, height: 7, borderRadius: 4, marginTop: 7, flex: 'none', background: n.readAt ? 'transparent' : 'var(--danger)' }} />
              <span style={{ flex: 1, minWidth: 0 }}>
                <span style={{ display: 'block', fontSize: 13.5, fontWeight: n.readAt ? 400 : 600 }}>{n.title}</span>
                {n.body && <span style={{ display: 'block', fontSize: 12.5, color: 'var(--text-3)' }}>{n.body}</span>}
                <span className="mono" style={{ fontSize: 11.5, color: 'var(--text-3)' }}>{fmtDateTime(n.createdAt)}</span>
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

/** Pemilih plant (PRD §15.2 level 0). Semua data & penomoran mengikuti plant aktif. */
export function PlantSwitcher() {
  const { me, setPlant } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useClickOutside<HTMLDivElement>(() => setOpen(false));
  if (!me) return null;
  const cur = me.plants.find((p) => p.id === me.plantId);
  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button className="ghost" type="button" onClick={() => setOpen(!open)} disabled={me.plants.length < 2}>
        {cur?.name ?? 'Plant'}{me.plants.length > 1 && <Icon name="chevron" size={14} />}
      </button>
      {open && (
        <div className="pop" style={{ top: 42, left: 0, minWidth: 200 }}>
          {me.plants.map((p) => (
            <button key={p.id} type="button" className={`ddi${p.id === me.plantId ? ' active' : ''}`}
              onClick={() => { setOpen(false); setPlant(p.id); }}>
              <span className="mono small muted" style={{ width: 28 }}>{p.code}</span>{p.name}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export function ProfileMenu() {
  const { me, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [usersOpen, setUsersOpen] = useState(false);
  const ref = useClickOutside<HTMLDivElement>(() => setOpen(false));
  const nav = useNavigate();
  const toast = useToast();
  if (!me) return null;
  const initial = me.fullName.trim().charAt(0).toUpperCase();

  const isDark = typeof document !== 'undefined' && document.documentElement.dataset.theme === 'dark';
  const toggleTheme = () => {
    const next = isDark ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    try {
      localStorage.setItem('erp.theme', next);
    } catch {
      /* ignore */
    }
    setOpen(false);
  };

  return (
    <>
      <div ref={ref} style={{ position: 'relative' }}>
        <button
          className="iconbtn"
          type="button"
          aria-label="Profil Pengguna"
          title={`Profil: ${me.fullName}`}
          onClick={() => setOpen(!open)}
          style={{ padding: 2 }}
        >
          <span
            style={{
              width: 30,
              height: 30,
              borderRadius: '50%',
              background: '#DC2626',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 13,
              fontWeight: 700,
              color: '#FFFFFF',
              boxShadow: '0 2px 5px rgba(220, 38, 38, 0.35)',
            }}
          >
            {initial}
          </span>
        </button>
        {open && (
          <div className="pop" style={{ right: 0, top: 42, width: 280 }}>
            <div style={{ padding: '8px 10px 10px', borderBottom: '1px solid var(--line-soft)', marginBottom: 4 }}>
              <div style={{ fontWeight: 600 }}>{me.fullName}</div>
              <div className="mono small muted">{me.username}</div>
              <div className="small muted" style={{ marginTop: 4 }}>
                {me.grants.map((g) => `${g.roleName} · ${g.app === '*' ? 'Semua aplikasi' : g.app}`).join(', ') || 'Karyawan'}
              </div>
            </div>

            <button
              type="button"
              className="ddi"
              onClick={toggleTheme}
            >
              {isDark ? '☀️ Beralih ke Mode Terang' : '🌙 Beralih ke Mode Gelap'}
            </button>

            <button
              type="button"
              className="ddi"
              onClick={() => {
                setOpen(false);
                setUsersOpen(true);
              }}
            >
              👥 Kelola Pengguna (Manage Users)
            </button>

            <button
              type="button"
              className="ddi"
              onClick={() => {
                setOpen(false);
                nav('/app/ESS/settings?cat=pref');
              }}
            >
              ⚙️ Preferensi saya
            </button>
            <button
              type="button"
              className="ddi"
              onClick={() => {
                setOpen(false);
                nav('/app/ESS/settings?cat=password');
              }}
            >
              🔑 Ganti kata sandi
            </button>
            <div style={{ borderTop: '1px solid var(--line-soft)', marginTop: 4, paddingTop: 4 }}>
              <button
                type="button"
                className="ddi"
                style={{ color: 'var(--danger)' }}
                onClick={() => {
                  setOpen(false);
                  logout();
                  toast.ok('Anda telah keluar');
                  nav('/login');
                }}
              >
                🚪 Keluar
              </button>
            </div>
          </div>
        )}
      </div>

      {usersOpen && <ManageUsersModal open={usersOpen} onClose={() => setUsersOpen(false)} />}
    </>
  );
}
