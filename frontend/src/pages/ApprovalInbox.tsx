import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { TaskView } from '../api/types';
import { DataTable } from '../components/DataTable';
import { ReasonDialog, useToast } from '../components/ui';
import { fmtAge, fmtRp } from '../lib/format';
import { useAllApps, useDocLink } from '../lib/meta';

/** ESS-10 Kotak Approval Saya: semua dokumen yang menunggu keputusan pengguna (SYS-04). */
export function ApprovalInbox() {
  const nav = useNavigate();
  const docLink = useDocLink();
  const qc = useQueryClient();
  const toast = useToast();
  const apps = useAllApps();
  const [app, setApp] = useState('');
  const [dialog, setDialog] = useState<{ task: TaskView; mode: 'approve' | 'reject' } | null>(null);
  const q = useQuery({ queryKey: ['inbox', app], queryFn: () => api.get<TaskView[]>('/approvals/inbox', { app }) });
  const done = () => {
    qc.invalidateQueries({ queryKey: ['inbox'] });
    qc.invalidateQueries({ queryKey: ['launcher'] });
  };
  const quickApprove = async (t: TaskView) => {
    if (t.requiresEsign) return setDialog({ task: t, mode: 'approve' });
    try {
      await api.post(`/approvals/${t.id}/approve`, {});
      toast.ok(`${t.docNo} disetujui`);
      done();
    } catch (e) {
      toast.error(e);
    }
  };
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <div className="toolbar">
        <select className="input" style={{ width: 'auto', height: 32 }} value={app} onChange={(e) => setApp(e.target.value)} aria-label="Aplikasi">
          <option value="">Aplikasi: Semua</option>
          {apps.apps.filter((a) => !a.self).map((a) => <option key={a.code} value={a.code}>{a.name}</option>)}
        </select>
        <span className="small muted">Dokumen menunggu &gt; 2 hari kerja diingatkan; &gt; 4 hari kerja dieskalasi ke atasan approver.</span>
      </div>
      <DataTable rows={q.data ?? []} rowKey={(t) => t.id} onRowClick={(t) => nav(docLink(t.docType, t.docId, t.menuCode))}
        empty={q.isLoading ? 'Memuat…' : 'Tidak ada dokumen yang menunggu keputusan Anda.'}
        columns={[
          { key: 'app', label: '', width: 12, render: (t) => <span style={{ display: 'inline-block', width: 8, height: 8, borderRadius: 2, background: apps.byApp.get(t.appCode)?.color }} /> },
          { key: 'docNo', label: 'Dokumen', mono: true },
          { key: 'docSummary', label: 'Uraian' },
          { key: 'docAmount', label: 'Nilai', align: 'right', render: (t) => t.docAmount != null ? fmtRp(t.docAmount) : '' },
          { key: 'level', label: 'Level', render: (t) => `L${t.level} · ${t.assigneeLabel}` },
          { key: 'requestedByName', label: 'Diajukan oleh' },
          { key: 'activatedAt', label: 'Menunggu', mono: true, render: (t) => fmtAge(t.activatedAt) },
          { key: 'act', label: '', render: (t) => (
            <span className="row" onClick={(e) => e.stopPropagation()}>
              <button className="btn btn-sm btn-dark" type="button" onClick={() => quickApprove(t)}>Setujui</button>
              <button className="btn btn-sm" type="button" onClick={() => setDialog({ task: t, mode: 'reject' })}>Tolak</button>
            </span>
          ) },
        ]} />
      {dialog && (
        <ReasonDialog
          title={`${dialog.mode === 'approve' ? 'Setujui' : 'Tolak'} ${dialog.task.docNo}`}
          confirmLabel={dialog.mode === 'approve' ? 'Setujui' : 'Tolak'}
          danger={dialog.mode === 'reject'}
          needReason={dialog.mode === 'reject'}
          needPassword={dialog.task.requiresEsign}
          onClose={() => setDialog(null)}
          onConfirm={async ({ reason, password }) => {
            await api.post(`/approvals/${dialog.task.id}/${dialog.mode}`, { reason, password });
            toast.ok(dialog.mode === 'approve' ? 'Disetujui' : 'Ditolak');
            done();
          }} />
      )}
    </div>
  );
}
