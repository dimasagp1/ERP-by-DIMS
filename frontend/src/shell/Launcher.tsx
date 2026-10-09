import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { LauncherData, QueueItem } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { DeptIcon, Icon } from '../components/icons';
import { MenuIcon } from '../components/MenuIcon';
import { StatusChip } from '../components/StatusChip';
import { fmtAge, greeting, longDate } from '../lib/format';
import { menuPath, recentMenus, useApps, useAllApps } from '../lib/meta';
import { NotificationBell, PlantSwitcher, ProfileMenu } from './TopbarParts';
import { useOpenPalette } from './Shell';

/** Level 0 — Launcher (PRD §15.2): ikon departemen, Layanan Saya, Administrasi, Perlu tindakan Anda. */
export function Launcher() {
  const { me } = useAuth();
  const { apps, byMenu } = useApps();
  const all = useAllApps();
  const nav = useNavigate();
  const openPalette = useOpenPalette();
  const data = useQuery({ queryKey: ['launcher'], queryFn: () => api.get<LauncherData>('/dashboard/launcher'), refetchInterval: 60_000 });

  const depts = apps.filter((a) => !a.self);
  const ess = apps.find((a) => a.code === 'ESS');
  const sys = apps.find((a) => a.code === 'SYS');
  const plant = me?.plants.find((p) => p.id === me.plantId);
  const recent = recentMenus().map((c) => byMenu.get(c)).filter(Boolean);

  const openItem = (q: QueueItem) => nav(menuPath(q.menuCode, q.docId));

  return (
    <div style={{ minHeight: '100%' }}>
      <header style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 12, padding: '8px 24px', minHeight: 56, background: 'var(--surface)', borderBottom: '1px solid var(--line)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginRight: 8 }}>
          <div className="mono" style={{ width: 28, height: 28, borderRadius: 6, background: 'var(--accent)', color: 'var(--text-on-dark)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 500 }}>H</div>
          <div style={{ fontWeight: 600, fontSize: 15 }}>Herbatech <span style={{ fontWeight: 400, color: 'var(--text-3)' }}>ERP</span></div>
        </div>
        <PlantSwitcher />
        <button type="button" onClick={openPalette} style={{ flex: '1 1 280px', maxWidth: 520, position: 'relative', height: 36, border: '1px solid var(--line-input)', borderRadius: 6, background: 'var(--surface-2)', textAlign: 'left', padding: '0 64px 0 12px', color: 'var(--text-3)', cursor: 'text', fontSize: 13.5 }}>
          Cari menu, dokumen, nomor lot…
          <span className="mono" style={{ position: 'absolute', right: 8, top: 8, fontSize: 11, border: '1px solid var(--line-input)', borderRadius: 4, padding: '1px 5px', background: 'var(--surface)' }}>Ctrl K</span>
        </button>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginLeft: 'auto' }}>
          <button className="ghost" type="button" onClick={() => nav(menuPath('ESS-10'))}>
            Approval <span className="badge">{data.data?.approvalCount ?? 0}</span>
          </button>
          <NotificationBell />
          <ProfileMenu />
        </div>
      </header>

      <main style={{ display: 'flex', flexWrap: 'wrap', gap: 32, padding: '32px 24px 48px', maxWidth: 1360, margin: '0 auto' }}>
        <section style={{ flex: '999 1 600px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 28 }}>
          <div>
            <div className="small muted" style={{ textTransform: 'capitalize' }}>{longDate()} · {plant?.name}</div>
            <h1 style={{ marginTop: 4, fontSize: 22 }}>{greeting()}, {me?.fullName.split(' (')[0]}</h1>
          </div>

          {depts.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <div className="lbl">Departemen</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(118px, 1fr))', gap: 4 }}>
                {depts.map((a) => {
                  const badge = data.data?.badges[a.code] ?? 0;
                  return (
                    <button key={a.code} className="tile" type="button" onClick={() => nav(`/app/${a.code}`)}>
                      <span className="tile-icon" style={{ color: a.color }}><DeptIcon app={a.code} /></span>
                      <span style={{ fontSize: 13, lineHeight: 1.3, maxWidth: 112 }}>{a.name}</span>
                      {badge > 0 && <span className="tile-badge">{badge}</span>}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {ess && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <div className="lbl">Layanan Saya</div>
              <div className="row">
                {ess.groups.flatMap((g) => g.items).filter((i) => i.code !== 'ESS-10').map((i) => (
                  <button key={i.code} className="svc" type="button" onClick={() => nav(menuPath(i.code))}>
                    <MenuIcon code={i.code} name={i.name} color={ess.color} size={16} />
                    <span className="mono" style={{ fontSize: 11, color: 'var(--text-3)' }}>{i.code}</span>
                    {i.name}
                  </button>
                ))}
              </div>
            </div>
          )}

          {sys && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <div className="lbl">Administrasi</div>
              <div className="row">
                <button className="svc" type="button" onClick={() => nav('/app/SYS')}><DeptIcon app="SYS" size={16} />Pengaturan Sistem</button>
                <button className="svc" type="button" onClick={() => nav(menuPath('SYS-14'))}><Icon name="clock" />Audit Trail</button>
              </div>
            </div>
          )}
        </section>

        <aside style={{ flex: '1 1 340px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div className="card" style={{ overflow: 'hidden' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 16px' }}>
              <div style={{ fontWeight: 600 }}>Perlu tindakan Anda</div>
              <span className="mono small muted">{data.data?.approvalCount ?? 0} dokumen</span>
            </div>
            {(data.data?.tasks ?? []).length === 0 && <div className="small muted" style={{ padding: '4px 16px 16px' }}>Tidak ada dokumen yang menunggu keputusan Anda.</div>}
            {data.data?.tasks.map((t) => (
              <button key={`${t.docType}-${t.docId}`} className="rowbtn" type="button" onClick={() => openItem(t)}>
                <span style={{ width: 8, height: 8, borderRadius: 2, marginTop: 6, flex: 'none', background: all.byApp.get(t.menuCode?.split('-')[0] ?? '')?.color ?? 'var(--text-3)' }} />
                <span style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <span className="mono" style={{ fontSize: 12, color: 'var(--text-3)' }}>{t.docNo}</span>
                  <span style={{ fontSize: 13.5 }}>{t.summary}</span>
                </span>
                <span className="mono small muted" style={{ whiteSpace: 'nowrap' }}>{fmtAge(t.since)}</span>
              </button>
            ))}
          </div>

          {(data.data?.myDocuments ?? []).length > 0 && (
            <div className="card" style={{ overflow: 'hidden' }}>
              <div style={{ padding: '12px 16px', fontWeight: 600 }}>Dokumen saya yang masih terbuka</div>
              {data.data?.myDocuments.map((d) => (
                <button key={`${d.docType}-${d.docId}`} className="rowbtn" type="button" onClick={() => openItem(d)}>
                  <span style={{ flex: 1, minWidth: 0 }}>
                    <span className="mono" style={{ display: 'block', fontSize: 12, color: 'var(--text-3)' }}>{d.docNo}</span>
                    <span style={{ fontSize: 13.5 }}>{d.summary}</span>
                  </span>
                  <StatusChip status={d.status} />
                </button>
              ))}
            </div>
          )}

          <div className="card" style={{ overflow: 'hidden' }}>
            <div style={{ padding: '12px 16px', fontWeight: 600 }}>Terakhir dibuka</div>
            {recent.length === 0 && <div className="small muted" style={{ padding: '4px 16px 16px' }}>Menu yang Anda buka akan muncul di sini.</div>}
            {recent.map((r) => r && (
              <button key={r.item.code} className="rowbtn" type="button" style={{ alignItems: 'center' }} onClick={() => nav(menuPath(r.item.code))}>
                <MenuIcon code={r.item.code} name={r.item.name} color={r.app.color} size={24} />
                <span className="mono" style={{ fontSize: 12, color: 'var(--text-3)', width: 56, flex: 'none' }}>{r.item.code}</span>
                <span style={{ fontSize: 13.5 }}>{r.item.name}</span>
              </button>
            ))}
          </div>
        </aside>
      </main>
    </div>
  );
}
