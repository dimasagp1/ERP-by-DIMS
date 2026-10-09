import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { LookupOption } from '../api/types';
import { useClickOutside, useDebounced } from './ui';

/** Label untuk kolom FK di tabel: satu permintaan per halaman per jenis lookup. */
export function useLookupLabels(lookup: string | undefined, ids: (number | null | undefined)[]) {
  const unique = useMemo(() => [...new Set(ids.filter((x): x is number => x != null))].sort((a, b) => a - b), [ids]);
  const q = useQuery({
    queryKey: ['lookup-ids', lookup, unique.join(',')],
    queryFn: () => api.get<LookupOption[]>(`/lookup/${lookup}`, { ids: unique.join(','), all: true, limit: 500 }),
    enabled: Boolean(lookup) && unique.length > 0,
    staleTime: 60_000,
  });
  return useMemo(() => {
    const m = new Map<number, LookupOption>();
    q.data?.forEach((o) => m.set(o.id, o));
    return m;
  }, [q.data]);
}

/**
 * Pilihan dari master data (autocomplete). Menampilkan kode + nama, mencari di server.
 */
export function LookupSelect({ lookup, value, onChange, filters, disabled, placeholder, id, allowClear = true }: {
  lookup: string;
  value: number | null | undefined;
  onChange: (id: number | null, option?: LookupOption) => void;
  filters?: Record<string, string | number | boolean | undefined>;
  disabled?: boolean;
  placeholder?: string;
  id?: string;
  allowClear?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const term = useDebounced(text, 200);
  const ref = useClickOutside<HTMLDivElement>(() => setOpen(false));
  const labels = useLookupLabels(lookup, [value]);
  const selected = value != null ? labels.get(value) : undefined;
  const [active, setActive] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const [rect, setRect] = useState<{ top: number; left: number; width: number; up: boolean } | null>(null);

  // Dropdown mengambang (fixed) agar tidak terpotong tabel/drawer yang bisa di-scroll.
  useLayoutEffect(() => {
    if (!open) return;
    const place = () => {
      const r = inputRef.current?.getBoundingClientRect();
      if (!r) return;
      const up = window.innerHeight - r.bottom < 300 && r.top > 300;
      setRect({ top: up ? r.top - 4 : r.bottom + 4, left: r.left, width: Math.max(r.width, 300), up });
    };
    place();
    window.addEventListener('scroll', place, true);
    window.addEventListener('resize', place);
    return () => {
      window.removeEventListener('scroll', place, true);
      window.removeEventListener('resize', place);
    };
  }, [open]);

  const options = useQuery({
    queryKey: ['lookup', lookup, term, JSON.stringify(filters ?? {})],
    queryFn: () => api.get<LookupOption[]>(`/lookup/${lookup}`, { q: term, limit: 30, ...filters }),
    enabled: open,
    staleTime: 30_000,
  });
  useEffect(() => setActive(0), [term]);

  const pick = (o: LookupOption | null) => {
    onChange(o ? o.id : null, o ?? undefined);
    setOpen(false);
    setText('');
  };
  const list = options.data ?? [];

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <input
        ref={inputRef}
        id={id}
        className="input"
        disabled={disabled}
        placeholder={placeholder ?? 'Cari kode atau nama…'}
        value={open ? text : selected ? `${selected.code} · ${selected.name}` : ''}
        onFocus={() => setOpen(true)}
        onChange={(e) => { setText(e.target.value); setOpen(true); }}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, list.length - 1)); }
          if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)); }
          if (e.key === 'Enter' && open && list[active]) { e.preventDefault(); pick(list[active]); }
          if (e.key === 'Escape') setOpen(false);
        }}
        role="combobox"
        aria-expanded={open}
        autoComplete="off"
      />
      {open && !disabled && (
        <div className="pop" role="listbox" style={{
          position: 'fixed', zIndex: 300, maxHeight: 280, overflow: 'auto',
          left: rect?.left ?? 0, width: rect?.width ?? 300,
          ...(rect?.up ? { bottom: window.innerHeight - (rect?.top ?? 0) } : { top: rect?.top ?? 0 }),
          visibility: rect ? 'visible' : 'hidden',
        }}>
          {allowClear && value != null && (
            <button type="button" className="ddi muted" onMouseDown={(e) => e.preventDefault()} onClick={() => pick(null)}>— Kosongkan —</button>
          )}
          {options.isLoading && <div className="ddi muted">Memuat…</div>}
          {!options.isLoading && list.length === 0 && <div className="ddi muted">Tidak ada hasil</div>}
          {list.map((o, i) => (
            <button key={o.id} type="button" className={`ddi${i === active ? ' active' : ''}`} role="option" aria-selected={o.id === value}
              onMouseDown={(e) => e.preventDefault()} onClick={() => pick(o)}>
              <span className="mono" style={{ fontSize: 12, color: 'var(--text-3)', minWidth: 72 }}>{o.code}</span>
              <span style={{ flex: 1 }}>{o.name}</span>
              {o.extra && <span className="mono small muted">{o.extra}</span>}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
