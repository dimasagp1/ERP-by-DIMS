import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { DocPanel } from '../api/types';
import { fmtBytes, fmtDateTime, fmtRp } from '../lib/format';
import { useDocLink } from '../lib/meta';
import { Icon } from './icons';
import { StatusChip, statusLabel } from './StatusChip';
import { ReasonDialog, useToast } from './ui';

export function useDocPanel(docType: string, id: number | undefined) {
  return useQuery({
    queryKey: ['doc-panel', docType, id],
    queryFn: () => api.get<DocPanel>(`/documents/${docType}/${id}/panel`),
    enabled: id != null,
  });
}

/**
 * Bar aksi dokumen sesuai status & hak akses (PRD §15.2 level 3): Ajukan, Setujui/Tolak, Posting, Batal, Reversal.
 * `actions` datang dari server sehingga tombol yang tampil sama dengan aturan yang ditegakkan.
 */
export function DocumentActions({ docType, id, actions, panel, requiresEsign, onChanged, onEdit, editing, onSave, saving }: {
  docType: string;
  id: number;
  actions: string[];
  panel?: DocPanel;
  requiresEsign?: boolean;
  onChanged: (reversalId?: number) => void;
  onEdit?: () => void;
  editing?: boolean;
  onSave?: () => void;
  saving?: boolean;
}) {
  const [dialog, setDialog] = useState<null | 'cancel' | 'reverse' | 'reject' | 'approve' | 'post'>(null);
  const toast = useToast();
  const qc = useQueryClient();
  const run = async (fn: () => Promise<unknown>, msg: string) => {
    try {
      const r = await fn();
      toast.ok(msg);
      qc.invalidateQueries({ queryKey: ['doc-panel', docType, id] });
      qc.invalidateQueries({ queryKey: ['launcher'] });
      onChanged((r as { id?: number } | undefined)?.id);
    } catch (e) {
      toast.error(e);
    }
  };
  const taskId = panel?.decidableTaskId;
  const esignDecision = panel?.decisionRequiresEsign;

  return (
    <div className="row">
      {editing && onSave && <button className="btn btn-dark" type="button" disabled={saving} onClick={onSave} title="Alt+S">Simpan</button>}
      {!editing && actions.includes('EDIT') && onEdit && <button className="btn" type="button" onClick={onEdit}>Ubah</button>}
      {!editing && actions.includes('SUBMIT') && (
        <button className="btn btn-dark" type="button" onClick={() => run(() => api.post(`/documents/${docType}/${id}/submit`), 'Dokumen diajukan')}>Ajukan</button>
      )}
      {!editing && taskId && <>
        <button className="btn btn-dark" type="button" onClick={() => esignDecision ? setDialog('approve') : run(() => api.post(`/approvals/${taskId}/approve`, {}), 'Disetujui')}>Setujui</button>
        <button className="btn" type="button" onClick={() => setDialog('reject')}>Tolak</button>
      </>}
      {!editing && actions.includes('POST') && (
        <button className="btn btn-dark" type="button" onClick={() => requiresEsign ? setDialog('post') : run(() => api.post(`/documents/${docType}/${id}/post`, {}), 'Dokumen diposting')}>Posting</button>
      )}
      {!editing && actions.includes('CANCEL') && <button className="btn" type="button" onClick={() => setDialog('cancel')}>Batalkan</button>}
      {!editing && actions.includes('REVERSE') && <button className="btn" type="button" onClick={() => setDialog('reverse')}>Reversal</button>}

      {dialog === 'cancel' && <ReasonDialog title="Batalkan dokumen" confirmLabel="Batalkan dokumen" danger needReason
        description="Nomor dokumen tidak akan dipakai ulang. Alasan dicatat di audit trail." onClose={() => setDialog(null)}
        onConfirm={async ({ reason }) => { await api.post(`/documents/${docType}/${id}/cancel`, { reason }); toast.ok('Dokumen dibatalkan'); onChanged(); }} />}
      {dialog === 'reverse' && <ReasonDialog title="Reversal dokumen terposting" confirmLabel="Buat reversal" danger needReason needDate
        description="Sistem membuat dokumen balik yang merujuk dokumen ini; dokumen ini menjadi Dibatalkan." onClose={() => setDialog(null)}
        onConfirm={async ({ reason, date }) => {
          const r = await api.post<{ id: number; docNo: string }>(`/documents/${docType}/${id}/reverse`, { reason, date: date || null });
          toast.ok(`Reversal ${r.docNo} dibuat`);
          onChanged(r.id);
        }} />}
      {dialog === 'reject' && taskId && <ReasonDialog title="Tolak dokumen" confirmLabel="Tolak" danger needReason needPassword={esignDecision}
        description="Dokumen kembali ke pembuat untuk diperbaiki." onClose={() => setDialog(null)}
        onConfirm={async ({ reason, password }) => { await api.post(`/approvals/${taskId}/reject`, { reason, password }); toast.ok('Dokumen ditolak'); onChanged(); }} />}
      {dialog === 'approve' && taskId && <ReasonDialog title="Setujui dengan tanda tangan elektronik" confirmLabel="Setujui" needPassword
        onClose={() => setDialog(null)}
        onConfirm={async ({ password }) => { await api.post(`/approvals/${taskId}/approve`, { password }); toast.ok('Disetujui'); onChanged(); }} />}
      {dialog === 'post' && <ReasonDialog title="Posting dengan tanda tangan elektronik" confirmLabel="Posting" needPassword
        onClose={() => setDialog(null)}
        onConfirm={async ({ password }) => { await api.post(`/documents/${docType}/${id}/post`, { password }); toast.ok('Dokumen diposting'); onChanged(); }} />}
    </div>
  );
}

const KIND_LABEL: Record<string, string> = { STATUS: 'Status', COMMENT: 'Komentar', APPROVAL: 'Approval', ATTACHMENT: 'Lampiran', SIGNATURE: 'TTE' };

/** Panel kanan form dokumen: approval, riwayat aktivitas, lampiran, dokumen terkait (PRD §13, §15.2). */
export function DocumentSidePanel({ docType, id, panel }: { docType: string; id: number; panel?: DocPanel }) {
  const [comment, setComment] = useState('');
  const fileRef = useRef<HTMLInputElement>(null);
  const qc = useQueryClient();
  const toast = useToast();
  const nav = useNavigate();
  const docLink = useDocLink();
  const refresh = () => qc.invalidateQueries({ queryKey: ['doc-panel', docType, id] });

  const send = async () => {
    if (!comment.trim()) return;
    try {
      await api.post(`/documents/${docType}/${id}/comments`, { message: comment });
      setComment('');
      refresh();
    } catch (e) {
      toast.error(e);
    }
  };
  const upload = async (f: File | undefined) => {
    if (!f) return;
    try {
      await api.upload(`/documents/${docType}/${id}/attachments`, f);
      toast.ok('Lampiran diunggah');
      refresh();
    } catch (e) {
      toast.error(e);
    }
  };

  if (!panel) return <div className="card"><div className="empty">Memuat…</div></div>;
  const approvals = panel.approvals ?? [];
  const related = panel.related ?? [];
  const attachments = panel.attachments ?? [];
  const signatures = panel.signatures ?? [];
  const activity = panel.activity ?? [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {approvals.length > 0 && (
        <div className="card" style={{ overflow: 'hidden' }}>
          <div className="card-h">Approval</div>
          {approvals.map((t) => (
            <div key={t.id} className="ev" style={{ display: 'flex', gap: 10, padding: '9px 16px', borderTop: '1px solid var(--line-soft)', fontSize: 12.5 }}>
              <span className="mono muted" style={{ width: 22 }}>L{t.level}</span>
              <span style={{ flex: 1 }}>
                <span style={{ display: 'block' }}>{t.assigneeLabel}</span>
                {t.decidedByName && <span className="muted">{t.decidedByName} · {fmtDateTime(t.decidedAt)}</span>}
                {t.decisionReason && <span className="muted" style={{ display: 'block' }}>“{t.decisionReason}”</span>}
              </span>
              <StatusChip status={t.status} />
            </div>
          ))}
          {approvals[0]?.docAmount != null && <div className="small muted" style={{ padding: '6px 16px 10px' }}>Nilai dokumen {fmtRp(approvals[0].docAmount)}</div>}
        </div>
      )}

      {related.length > 0 && (
        <div className="card" style={{ overflow: 'hidden' }}>
          <div className="card-h">Dokumen terkait</div>
          {related.map((r) => (
            <button key={`${r.docType}-${r.docId}`} type="button" className="rowbtn" style={{ padding: '8px 16px' }} onClick={() => nav(docLink(r.docType, r.docId, r.menuCode))}>
              <span style={{ flex: 1 }}><span className="mono small">{r.docNo}</span> <span className="muted small">{r.relation}</span></span>
              <StatusChip status={r.status} />
            </button>
          ))}
        </div>
      )}

      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">
          Lampiran
          <button className="btn btn-sm" type="button" onClick={() => fileRef.current?.click()}><Icon name="clip" size={14} />Unggah</button>
          <input ref={fileRef} type="file" hidden onChange={(e) => { void upload(e.target.files?.[0]); e.target.value = ''; }} />
        </div>
        {attachments.length === 0 && <div className="small muted" style={{ padding: '10px 16px' }}>Belum ada lampiran</div>}
        {attachments.map((a) => (
          <button key={a.id} type="button" className="rowbtn" style={{ padding: '8px 16px' }}
            onClick={() => api.download(`/documents/${docType}/${id}/attachments/${a.id}`, a.filename).catch(toast.error)}>
            <Icon name="download" size={14} />
            <span style={{ flex: 1, minWidth: 0 }}>
              <span style={{ display: 'block', fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis' }}>{a.filename}</span>
              <span className="small muted">{fmtBytes(a.sizeBytes)} · {a.uploadedByName} · {fmtDateTime(a.uploadedAt)}</span>
            </span>
          </button>
        ))}
      </div>

      {signatures.length > 0 && (
        <div className="card" style={{ overflow: 'hidden' }}>
          <div className="card-h">Tanda tangan elektronik</div>
          {signatures.map((s) => (
            <div key={s.id} style={{ padding: '8px 16px', borderTop: '1px solid var(--line-soft)', fontSize: 12.5 }}>
              <b>{s.fullName}</b> <span className="mono muted">{s.meaning}</span>
              <div className="muted">{fmtDateTime(s.signedAt)}{s.reason ? ` · ${s.reason}` : ''}</div>
            </div>
          ))}
        </div>
      )}

      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="card-h">Riwayat aktivitas</div>
        <div className="timeline">
          {activity.map((a) => (
            <div key={a.id} className="ev">
              <span className="lbl" style={{ width: 62, flex: 'none', fontSize: 10.5, paddingTop: 1 }}>{KIND_LABEL[a.kind] ?? a.kind}</span>
              <span className="m">
                <span style={{ display: 'block' }}>{a.toStatus && a.kind === 'STATUS' ? <><b>{statusLabel(a.toStatus)}</b>{a.message && a.message !== statusLabel(a.toStatus) ? ` · ${a.message}` : ''}</> : a.message}</span>
                <span className="muted">{a.fullName ?? a.username} · {fmtDateTime(a.ts)}</span>
              </span>
            </div>
          ))}
        </div>
        <div style={{ display: 'flex', gap: 8, padding: 12, borderTop: '1px solid var(--line-soft)' }}>
          <label className="sr-only" htmlFor="doc-comment">Komentar</label>
          <input id="doc-comment" className="input" style={{ height: 32 }} placeholder="Tulis komentar…" value={comment}
            onChange={(e) => setComment(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && send()} />
          <button className="btn" type="button" onClick={send}>Kirim</button>
        </div>
      </div>
    </div>
  );
}
