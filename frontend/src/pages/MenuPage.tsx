import { useEffect } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { DocForm } from '../modules/docs/DocForm';
import { DocList } from '../modules/docs/DocList';
import type { MenuTab } from '../modules/docs/types';
import { MENU_IMPL } from '../modules/registry';
import { MenuIcon } from '../components/MenuIcon';
import { menuPath, PHASE_LABEL, rememberMenu, useAllApps, useApps } from '../lib/meta';

/** Level 2/3 — halaman menu (daftar) dan formulir dokumen (PRD §15.2). */
export function MenuPage() {
  const { menu = '', id } = useParams();
  const [params, setParams] = useSearchParams();
  const { byMenu, byApp } = useApps();
  const all = useAllApps();
  const nav = useNavigate();
  const entry = byMenu.get(menu);
  useEffect(() => { if (entry) rememberMenu(menu); }, [entry, menu]);

  if (!entry) {
    return <div className="page"><div className="alert alert-err">Menu {menu} tidak ditemukan atau Anda tidak memiliki hak akses.</div></div>;
  }
  const impl = MENU_IMPL[menu];
  const app = byApp.get(entry.app.code)!;
  const links = entry.item.links.filter((c) => c !== menu);

  const tabs = impl?.tabs ?? [];
  const docTabs = tabs.filter((t) => t.doc);
  if (id && (impl?.form || docTabs.length)) {
    const Form = impl?.form;
    const tab = docTabs.find((t) => t.doc!.docType === params.get('type')) ?? docTabs[0];
    const listPath = tab ? `${menuPath(menu)}?tab=${tab.key}` : menuPath(menu);
    return (
      <div className="page">
        <div className="crumb">
          {app.name} / {entry.group} / <a href={listPath} onClick={(e) => { e.preventDefault(); nav(listPath); }}>{entry.item.name}</a>
        </div>
        {Form ? <Form /> : (
          <DocForm key={`${tab.key}-${id}`} cfg={tab.doc!} id={id} listPath={listPath}
            docPath={(docId) => `${menuPath(menu, docId)}?type=${tab.doc!.docType}`} />
        )}
      </div>
    );
  }

  const active: MenuTab | undefined = tabs.find((t) => t.key === params.get('tab')) ?? tabs[0];
  const List = impl?.list;
  return (
    <div className="page">
      <div className="crumb">{app.name} / {entry.group} / {entry.item.name}</div>
      <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16 }}>
        <div style={{ minWidth: 0, flex: '1 1 420px' }}>
          <div className="row" style={{ gap: 10 }}>
            <MenuIcon code={menu} name={entry.item.name} color={app.color} size={36} />
            <span className="mono" style={{ fontSize: 12, border: '1px solid var(--line-input)', borderRadius: 4, padding: '2px 6px', background: 'var(--surface)' }}>{menu}</span>
            <h1>{entry.item.name}</h1>
          </div>
          <p style={{ margin: '6px 0 0', color: 'var(--text-2)', maxWidth: 760 }}>{entry.item.fn}</p>
        </div>
      </div>
      {links.length > 0 && (
        <div className="row">
          <span className="small muted" style={{ marginRight: 2 }}>Terhubung ke</span>
          {links.map((c) => {
            const target = all.byMenu.get(c);
            const color = all.byApp.get(c.split('-')[0])?.color ?? 'var(--text-3)';
            const reachable = byMenu.has(c);
            return (
              <button key={c} className="link" type="button" disabled={!reachable} title={reachable ? undefined : 'Di luar hak akses Anda'}
                style={reachable ? undefined : { opacity: 0.55, cursor: 'default' }}
                onClick={() => reachable && nav(menuPath(c))}>
                <span style={{ width: 8, height: 8, borderRadius: 2, background: color }} />
                <span className="mono" style={{ fontSize: 11.5, color: 'var(--text-3)' }}>{c}</span>{target?.item.name ?? c}
              </button>
            );
          })}
        </div>
      )}
      {tabs.length > 1 && (
        <div className="tabs page-tabs" role="tablist">
          {tabs.map((t) => (
            <button key={t.key} type="button" role="tab" aria-selected={t === active} className={t === active ? 'on' : ''}
              onClick={() => setParams({ tab: t.key })}>{t.label}</button>
          ))}
        </div>
      )}
      {active ? <TabBody key={active.key} menu={menu} tab={active} /> : List ? <List /> : <Planned phase={entry.item.phase} />}
    </div>
  );
}

function TabBody({ menu, tab }: { menu: string; tab: MenuTab }) {
  const nav = useNavigate();
  if (tab.doc) {
    const type = tab.doc.docType;
    return (
      <div className="card" style={{ overflow: 'hidden' }}>
        <DocList cfg={tab.doc} onOpen={(docId) => nav(`${menuPath(menu, docId)}?type=${type}`)} onNew={() => nav(`${menuPath(menu, 'new')}?type=${type}`)} />
      </div>
    );
  }
  const C = tab.component!;
  return <C />;
}

const PHASE_SCOPE: Record<string, string> = {
  M1: 'Fase 1 PRD: FIN inti (hutang, piutang, kas, anggaran, aset, laporan keuangan), HC (absensi, payroll, SARMUT), PRE dasar (WO, hasil produksi), umpan BSC.',
  M2: 'Fase 2 PRD: Supply Chain (PPIC/MRP, gudang, inventory control, penelusuran lot), Procurement (PR, RFQ, PO, ASL), costing per batch & pajak.',
  M3: 'Fase 3 PRD: Quality (QA & QC), RnD, eBMR penuh di tablet, Engineering & maintenance, General Affairs.',
};

function Planned({ phase }: { phase: string }) {
  return (
    <div className="card" style={{ padding: '28px 24px', display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div className="row"><span className="chip" style={{ background: 'var(--chip-submitted-bg)', color: 'var(--chip-submitted-fg)' }}>Dalam pengembangan · {PHASE_LABEL[phase] ?? phase}</span></div>
      <p style={{ margin: 0, maxWidth: 760, color: 'var(--text-2)' }}>
        Menu ini dijadwalkan pada milestone <b>{phase}</b>. {PHASE_SCOPE[phase] ?? ''}
      </p>
      <p className="small muted" style={{ margin: 0 }}>Fondasinya sudah tersedia: penomoran, approval berjenjang, audit trail, tanda tangan elektronik, kunci periode, lampiran, dan mesin inventory per lot.</p>
    </div>
  );
}
