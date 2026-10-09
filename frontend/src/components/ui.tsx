import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { ApiError } from '../api/client';
import { Icon } from './icons';

// ------------------------------------------------------------------ Toast

interface Toast { id: number; text: string; error?: boolean }
const ToastCtx = createContext<(text: string, error?: boolean) => void>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Toast[]>([]);
  const push = useCallback((text: string, error?: boolean) => {
    const id = Date.now() + Math.random();
    setItems((s) => [...s, { id, text, error }]);
    setTimeout(() => setItems((s) => s.filter((t) => t.id !== id)), error ? 7000 : 3500);
  }, []);
  return (
    <ToastCtx.Provider value={push}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {items.map((t) => <div key={t.id} className={`toast${t.error ? ' err' : ''}`}>{t.text}</div>)}
      </div>
    </ToastCtx.Provider>
  );
}

export function useToast() {
  const push = useContext(ToastCtx);
  return {
    ok: (text: string) => push(text),
    error: (e: unknown) => push(errorText(e), true),
  };
}

export function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.fields && Object.keys(e.fields).length) {
      return `${e.message}: ${Object.entries(e.fields).map(([k, v]) => `${k} ${v}`).join('; ')}`;
    }
    return e.message;
  }
  return e instanceof Error ? e.message : String(e);
}

// ------------------------------------------------------------------ Modal

export function Modal({ title, onClose, children, footer, width }: {
  title: ReactNode; onClose: () => void; children: ReactNode; footer?: ReactNode; width?: number;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="modal-back" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" style={width ? { maxWidth: width } : undefined}>
        <div className="modal-h">
          <span>{title}</span>
          <button className="iconbtn" type="button" aria-label="Tutup" onClick={onClose}><Icon name="x" /></button>
        </div>
        <div className="modal-b">{children}</div>
        {footer && <div className="modal-f">{footer}</div>}
      </div>
    </div>
  );
}

/**
 * Dialog alasan + (opsional) tanda tangan elektronik. Dipakai untuk tolak, batal, reversal, posting GMP.
 */
export function ReasonDialog({ title, description, confirmLabel, needReason, needPassword, needDate, danger, onConfirm, onClose }: {
  title: string; description?: string; confirmLabel: string; needReason?: boolean; needPassword?: boolean; needDate?: boolean;
  danger?: boolean; onConfirm: (v: { reason: string; password: string; date: string }) => Promise<void>; onClose: () => void;
}) {
  const [reason, setReason] = useState('');
  const [password, setPassword] = useState('');
  const [date, setDate] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const submit = async () => {
    if (needReason && !reason.trim()) return setErr('Alasan wajib diisi');
    if (needPassword && !password) return setErr('Masukkan ulang kata sandi untuk tanda tangan elektronik');
    setBusy(true);
    setErr(null);
    try {
      await onConfirm({ reason: reason.trim(), password, date });
      onClose();
    } catch (e) {
      setErr(errorText(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal title={title} onClose={onClose} footer={<>
      <button className="btn" type="button" onClick={onClose}>Batal</button>
      <button className={`btn btn-dark${danger ? ' btn-danger' : ''}`} type="button" disabled={busy} onClick={submit}>{confirmLabel}</button>
    </>}>
      {description && <p style={{ margin: 0, color: 'var(--text-2)' }}>{description}</p>}
      {needReason && (
        <label className="field"><span className="req">Alasan</span>
          <textarea autoFocus value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
        </label>
      )}
      {needDate && (
        <label className="field"><span>Tanggal dokumen balik (kosong = hari ini)</span>
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
      )}
      {needPassword && (
        <label className="field"><span className="req">Tanda tangan elektronik — kata sandi</span>
          <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && submit()} />
          <span className="muted small">Kata sandi diverifikasi ulang dan dicatat dengan waktu server (21 CFR Part 11).</span>
        </label>
      )}
      {err && <div className="alert alert-err">{err}</div>}
    </Modal>
  );
}

// ------------------------------------------------------------------ Pipeline status

const FLOW = ['DRAFT', 'SUBMITTED', 'APPROVED', 'POSTED'] as const;
const LABEL: Record<string, string> = { DRAFT: 'Draft', SUBMITTED: 'Diajukan', APPROVED: 'Disetujui', POSTED: 'Diposting' };

/** Pipeline status di kanan atas form dokumen (PRD §15.2). */
export function StatusPipeline({ status }: { status: string }) {
  const branch = status === 'REJECTED' ? 'Ditolak' : status === 'CANCELLED' ? 'Dibatalkan' : null;
  const curIdx = status === 'REJECTED' ? 0 : FLOW.indexOf(status as (typeof FLOW)[number]);
  return (
    <div className="pipeline" aria-label={`Status: ${branch ?? LABEL[status] ?? status}`}>
      {FLOW.map((s, i) => (
        <span key={s} style={{ display: 'contents' }}>
          {i > 0 && <span className="sep" />}
          <span className={`step${branch === 'Dibatalkan' ? '' : i < curIdx ? ' done' : i === curIdx && !branch ? ' cur' : ''}`}>
            <span className="b" />{LABEL[s]}
          </span>
        </span>
      ))}
      {branch && <><span className="sep" /><span className="step cur" style={{ color: 'var(--chip-rejected-fg)' }}><span className="b" style={{ background: 'var(--danger)', borderColor: 'var(--danger)' }} />{branch}</span></>}
    </div>
  );
}

// ------------------------------------------------------------------ Lain-lain

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}

export function Spinner() {
  return <div className="empty">Memuat…</div>;
}

export function useClickOutside<T extends HTMLElement>(onOutside: () => void) {
  const ref = useRef<T>(null);
  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onOutside();
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, [onOutside]);
  return ref;
}

export function useDebounced<T>(value: T, ms = 250): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}
