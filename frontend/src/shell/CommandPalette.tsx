import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { SearchHit } from '../api/types';
import { useDebounced } from '../components/ui';
import { StatusChip } from '../components/StatusChip';
import { menuPath } from '../lib/meta';
import { MenuIcon } from '../components/MenuIcon';

const KIND: Record<string, string> = { MENU: 'Menu', DOC: 'Dokumen', ITEM: 'Item', LOT: 'Lot' };

/** Pencarian global Ctrl+K (PRD §15.6): menu, nomor dokumen, kode item, nomor lot. */
export function CommandPalette({ onClose }: { onClose: () => void }) {
  const [text, setText] = useState('');
  const term = useDebounced(text.trim(), 180);
  const [active, setActive] = useState(0);
  const nav = useNavigate();
  const q = useQuery({
    queryKey: ['search', term],
    queryFn: () => api.get<SearchHit[]>('/search', { q: term }),
    enabled: term.length >= 2,
  });
  const hits = q.data ?? [];
  useEffect(() => setActive(0), [term]);

  const go = (h: SearchHit) => {
    onClose();
    if (h.kind === 'DOC' && h.menuCode && h.id) nav(menuPath(h.menuCode, h.id));
    else if (h.kind === 'ITEM' && h.id) nav(`${menuPath('SYS-06')}?q=${encodeURIComponent(h.code)}`);
    else if (h.menuCode) nav(menuPath(h.menuCode));
  };

  return (
    <div className="modal-back" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={{ maxWidth: 640 }} role="dialog" aria-label="Pencarian global">
        <input
          autoFocus
          className="input"
          style={{ height: 52, border: 0, borderBottom: '1px solid var(--line)', borderRadius: '10px 10px 0 0', fontSize: 15, padding: '0 18px' }}
          placeholder="Cari menu, nomor dokumen, kode item, nomor lot…"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Escape') onClose();
            if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, hits.length - 1)); }
            if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)); }
            if (e.key === 'Enter' && hits[active]) go(hits[active]);
          }}
        />
        <div style={{ maxHeight: 420, overflow: 'auto', padding: 6 }}>
          {term.length < 2 && <div className="empty small">Ketik minimal 2 karakter. Contoh: <span className="mono">FIN-03</span>, <span className="mono">JV/P1</span>, <span className="mono">kunyit</span></div>}
          {term.length >= 2 && !q.isLoading && hits.length === 0 && <div className="empty small">Tidak ditemukan</div>}
          {hits.map((h, i) => (
            <button key={`${h.kind}-${h.code}-${i}`} type="button" className={`ddi${i === active ? ' active' : ''}`} onClick={() => go(h)} onMouseEnter={() => setActive(i)}>
              {h.kind === 'MENU' ? <MenuIcon code={h.code} name={h.title ?? undefined} size={22} /> : <span style={{ width: 22, flex: 'none' }} />}
              <span className="lbl" style={{ width: 64, flex: 'none' }}>{KIND[h.kind]}</span>
              <span className="mono" style={{ fontSize: 12.5, flex: 'none' }}>{h.code}</span>
              <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{h.title}</span>
              {h.kind === 'DOC' && h.subtitle ? <StatusChip status={h.subtitle} /> : <span className="small muted">{h.subtitle}</span>}
            </button>
          ))}
        </div>
        <div className="row small muted" style={{ padding: '8px 14px', borderTop: '1px solid var(--line)' }}>
          <span className="mono">↑↓</span> pilih <span className="mono">Enter</span> buka <span className="mono">Esc</span> tutup
        </div>
      </div>
    </div>
  );
}

/** Pintasan global: Ctrl+K cari, G lalu H ke launcher (PRD §15.6). */
export function useGlobalShortcuts(openPalette: () => void) {
  const nav = useNavigate();
  useEffect(() => {
    let lastG = 0;
    const h = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      const typing = ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || target.isContentEditable;
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        openPalette();
        return;
      }
      if (typing || e.ctrlKey || e.metaKey || e.altKey) return;
      if (e.key.toLowerCase() === 'g') lastG = Date.now();
      else if (e.key.toLowerCase() === 'h' && Date.now() - lastG < 1200) nav('/');
    };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [nav, openPalette]);
}
