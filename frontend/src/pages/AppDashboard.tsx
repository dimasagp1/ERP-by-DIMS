import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { AppDashboard as Dash } from '../api/types';
import { DataTable } from '../components/DataTable';
import { StatusChip } from '../components/StatusChip';
import { errorText } from '../components/ui';
import { fmtAge, fmtRpShort } from '../lib/format';
import { menuPath, PHASE_LABEL, useAllApps, useApps, useDocLink } from '../lib/meta';
import { MenuIcon } from '../components/MenuIcon';
import { MENU_IMPL } from '../modules/registry';

/** Beranda aplikasi (PRD §15.3): angka utama, antrean kerja, hubungan departemen. */
export function AppDashboard() {
  const { app = '' } = useParams();
  const { byApp } = useApps();
  const all = useAllApps();
  const nav = useNavigate();
  const docLink = useDocLink();
  const cur = byApp.get(app);
  const q = useQuery({ queryKey: ['dashboard', app], queryFn: () => api.get<Dash>(`/dashboard/${app}`), refetchInterval: 60_000 });
  if (!cur) return null;

  const def = all.byApp.get(app);
  const relOut = Object.entries(def?.sends ?? {});
  const relIn = all.apps.filter((a) => a.sends?.[app]).map((a) => [a.code, a.sends[app]] as const);
  const phases = [...new Set(cur.groups.flatMap((g) => g.items.map((i) => i.phase)))].sort();
  const nextPhase = phases.find((p) => p !== 'M0');

  return (
    <div className="page">
      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-end', justifyContent: 'space-between', gap: 12 }}>
        <div>
          <div className="crumb">{cur.name} / Beranda</div>
          <h1 style={{ marginTop: 4 }}>Beranda {cur.shortName}</h1>
        </div>
      </div>

      {q.error && <div className="alert alert-err">{errorText(q.error)}</div>}
      {q.data && (q.data.kpiAvailable ? (
        <div className="kpis">
          {q.data.kpis.map((k) => (
            <button key={k.label} type="button" className="card kpi" style={{ textAlign: 'left', cursor: k.menuCode ? 'pointer' : 'default', color: 'inherit', font: 'inherit' }}
              onClick={() => k.menuCode && byApp.get(k.menuCode.split('-')[0]) && nav(menuPath(k.menuCode))}>
              <span className="small muted">{k.label}</span>
              <span className="v">{k.value}</span>
              <span className="small muted">{k.note}</span>
            </button>
          ))}
        </div>
      ) : (
        <div className="card" style={{ padding: '14px 16px' }}>
          <div className="small muted">
            Angka utama aplikasi ini dihitung dari transaksinya sendiri (bukan input manual) dan muncul begitu menu transaksinya aktif
            {nextPhase ? ` di ${PHASE_LABEL[nextPhase]} (${nextPhase})` : ''}.
          </div>
        </div>
      ))}

      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">Menu {cur.shortName}</div>
        <div className="menu-grid">
          {cur.groups.map((g) => (
            <div key={g.name} className="grp">
              <div className="lbl" style={{ padding: '2px 8px 6px' }}>{g.name}</div>
              {g.items.map((it) => {
                const built = Boolean(MENU_IMPL[it.code]);
                return (
                  <button key={it.code} type="button" className={`menu-tile${built ? '' : ' planned'}`} title={it.fn} onClick={() => nav(menuPath(it.code))}>
                    <MenuIcon code={it.code} name={it.name} color={built ? cur.color : undefined} size={28} />
                    <span className="nm">{it.name}</span>
                    <span className="mono" style={{ fontSize: 10.5, color: 'var(--text-3)' }}>{built ? it.code : it.phase}</span>
                  </button>
                );
              })}
            </div>
          ))}
        </div>
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'flex-start' }}>
        <div className="card" style={{ flex: '999 1 560px', minWidth: 0, overflow: 'hidden' }}>
          <div className="card-h">Antrean kerja <span className="small muted" style={{ fontWeight: 400 }}>Klik baris untuk membuka dokumen</span></div>
          <DataTable rows={q.data?.queue ?? []} rowKey={(r) => `${r.docType}-${r.docId}-${r.taskId ?? ''}`}
            onRowClick={(r) => nav(docLink(r.docType, r.docId, r.menuCode))}
            empty={q.isLoading ? 'Memuat…' : 'Tidak ada dokumen yang perlu ditindaklanjuti'}
            columns={[
              { key: 'docNo', label: 'Dokumen', mono: true },
              { key: 'summary', label: 'Uraian', render: (r) => <>{r.summary}{r.amount != null && <span className="muted"> · {fmtRpShort(r.amount)}</span>}</> },
              { key: 'status', label: 'Status', render: (r) => <StatusChip status={r.status} /> },
              { key: 'info', label: 'Info', render: (r) => <span className="muted">{r.info} · {fmtAge(r.since)}</span> },
            ]} />
        </div>
        {(relOut.length > 0 || relIn.length > 0) && (
          <div className="card" style={{ flex: '1 1 320px', minWidth: 0, overflow: 'hidden' }}>
            <div className="card-h">Hubungan departemen</div>
            <div className="lbl" style={{ padding: '12px 16px 4px' }}>Mengirim ke</div>
            {relOut.map(([k, text]) => <Rel key={k} k={k} text={text} color={all.byApp.get(k)?.color} onClick={byApp.has(k) ? () => nav(`/app/${k}`) : undefined} />)}
            <div className="lbl" style={{ padding: '14px 16px 4px', borderTop: '1px solid var(--line-soft)', marginTop: 6 }}>Menerima dari</div>
            {relIn.map(([k, text]) => <Rel key={k} k={k} text={text} color={all.byApp.get(k)?.color} onClick={byApp.has(k) ? () => nav(`/app/${k}`) : undefined} />)}
            <div style={{ height: 8 }} />
          </div>
        )}
      </div>
    </div>
  );
}

function Rel({ k, text, color, onClick }: { k: string; text: string; color?: string; onClick?: () => void }) {
  return (
    <button className="rowbtn" type="button" style={{ borderTop: 0, padding: '7px 16px', cursor: onClick ? 'pointer' : 'default' }} onClick={onClick}>
      <span className="mono" style={{ fontSize: 11, color: '#FFFFFF', borderRadius: 3, padding: '1px 5px', flex: 'none', background: color }}>{k}</span>
      <span style={{ fontSize: 13, color: 'var(--text-2)' }}>{text}</span>
    </button>
  );
}
