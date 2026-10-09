import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DocumentActions, DocumentSidePanel, useDocPanel } from '../../components/DocumentPanel';
import { Icon } from '../../components/icons';
import { errorText, Spinner, StatusPipeline, useToast } from '../../components/ui';
import { useDocTypes } from '../../lib/meta';
import { FieldInput } from '../master/FieldInput';
import { formatCell } from './DocList';
import type { Doc, DocConfig, DocField, Envelope, LineSection } from './types';

/** Formulir dokumen generik (PRD §15.2 level 3): header + pipeline status, tombol aksi, baris, ringkasan, panel kanan. */
export function DocForm({ cfg, id, listPath, docPath }: {
  cfg: DocConfig; id: string; listPath: string; docPath: (id: number) => string;
}) {
  const isNew = id === 'new';
  const numId = isNew ? undefined : Number(id);
  const nav = useNavigate();
  const qc = useQueryClient();
  const toast = useToast();
  const docTypes = useDocTypes();
  const { me } = useAuth();
  const env = useQuery({ queryKey: ['doc', cfg.endpoint, numId], queryFn: () => api.get<Envelope>(`${cfg.endpoint}/${numId}`), enabled: numId != null });
  const panel = useDocPanel(cfg.docType, numId);
  const [editing, setEditing] = useState(isNew);
  const [doc, setDoc] = useState<Doc>(() => ({ ...structuredClone(cfg.defaults ?? {}), ...emptyLines(cfg) }));
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    if (env.data && !editing) setDoc(structuredClone(env.data.doc));
  }, [env.data, editing]);

  const meta = env.data?.meta;
  const staff = isNew ? isStaff(me?.apps, cfg) : Boolean(meta?.canEditAll);

  const save = async () => {
    setSaving(true);
    setErr(null);
    try {
      const body = { ...doc, version: meta?.version };
      const r = isNew ? await api.post<Envelope>(cfg.endpoint, body) : await api.put<Envelope>(`${cfg.endpoint}/${numId}`, body);
      qc.setQueryData(['doc', cfg.endpoint, r.doc.id], r);
      qc.invalidateQueries({ queryKey: ['docs', cfg.endpoint] });
      qc.invalidateQueries({ queryKey: ['doc-panel', cfg.docType, r.doc.id] });
      setEditing(false);
      toast.ok(isNew ? `Draft ${r.meta.docNo} dibuat` : 'Perubahan disimpan');
      if (isNew) nav(docPath(r.doc.id as number), { replace: true });
    } catch (e) {
      setErr(errorText(e));
    } finally {
      setSaving(false);
    }
  };

  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      if (e.altKey && e.key.toLowerCase() === 's' && editing) {
        e.preventDefault();
        void save();
      }
    };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  });

  if (!isNew && env.isLoading) return <Spinner />;
  if (!isNew && env.error) return <div className="alert alert-err">{errorText(env.error)}</div>;

  const reload = () => {
    qc.invalidateQueries({ queryKey: ['doc', cfg.endpoint, numId] });
    qc.invalidateQueries({ queryKey: ['docs', cfg.endpoint] });
    panel.refetch();
  };
  const set = (key: string, v: unknown) => setDoc((d) => ({ ...d, [key]: v }));
  const visible = (f: DocField) => (!f.show || f.show(doc)) && (!f.staffOnly || staff);
  const withFilters = (f: DocField): DocField => (f.lookupFiltersFrom ? { ...f, lookupFilters: f.lookupFiltersFrom(doc) } : f);
  const Extra = cfg.extra;
  const subtitle = cfg.subtitle?.(doc);

  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'flex-start' }}>
      <div style={{ flex: '999 1 620px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div className="card">
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, justifyContent: 'space-between', alignItems: 'center', padding: '14px 16px', borderBottom: '1px solid var(--line)' }}>
            <div>
              <div className="lbl">{cfg.title}</div>
              <div className="mono" style={{ fontSize: 18, fontWeight: 500 }}>{meta?.docNo ?? 'Draft baru'}</div>
              {subtitle && <div className="small muted">{subtitle}</div>}
            </div>
            <StatusPipeline status={meta?.status ?? 'DRAFT'} />
          </div>
          <div className="row" style={{ padding: '10px 16px', borderBottom: '1px solid var(--line)' }}>
            {meta && numId != null ? (
              <DocumentActions docType={cfg.docType} id={numId} actions={meta.actions} panel={panel.data}
                requiresEsign={docTypes.get(cfg.docType)?.requiresEsign} editing={editing} saving={saving}
                onSave={save} onEdit={() => setEditing(true)}
                onChanged={() => reload()} />
            ) : <button className="btn btn-dark" type="button" disabled={saving} onClick={save} title="Alt+S">Simpan draft</button>}
            {editing && !isNew && <button className="btn" type="button" onClick={() => { setEditing(false); reload(); }}>Batal ubah</button>}
            {!editing && meta && cfg.actions?.filter((a) => a.show(doc, meta)).map((a) => (
              <button key={a.label} className={`btn${a.primary ? ' btn-dark' : ''}`} type="button" onClick={async () => {
                try {
                  await a.run(doc);
                  toast.ok(`${a.label}: berhasil`);
                  reload();
                } catch (e) {
                  toast.error(e);
                }
              }}>{a.label}</button>
            ))}
            <span className="spacer" />
            <button className="btn" type="button" onClick={() => nav(listPath)}>Kembali ke daftar</button>
          </div>
          {meta?.status === 'REJECTED' && <div className="alert alert-err" style={{ margin: 12 }}>Dokumen ditolak. Perbaiki lalu ajukan kembali — alasannya ada di riwayat aktivitas.</div>}
          {meta?.cancelReason && <div className="alert alert-warn" style={{ margin: 12 }}>{meta.cancelReason}</div>}

          <div className="grid-form" style={{ padding: 16 }}>
            {cfg.header.filter(visible).map(withFilters).map((f) => {
              const fid = `h-${cfg.docType}-${f.key}`;
              const disabled = !editing || f.readOnly;
              if (f.type === 'bool') {
                return <div key={f.key} style={{ gridColumn: '1 / -1' }}><FieldInput def={f} id={fid} value={doc[f.key]} disabled={disabled} onChange={(v) => set(f.key, v)} /></div>;
              }
              return (
                <div key={f.key} className="field" style={f.full || f.type === 'textarea' ? { gridColumn: '1 / -1' } : undefined}>
                  <label htmlFor={fid} className={f.required && editing ? 'req' : undefined}>{f.label}</label>
                  {f.readOnly && f.type !== 'lookup'
                    ? <input id={fid} disabled value={formatCell(f.type, doc[f.key])} style={{ textAlign: f.type === 'money' || f.type === 'number' ? 'right' : undefined }} />
                    : <FieldInput def={f} id={fid} value={doc[f.key]} disabled={disabled} onChange={(v) => set(f.key, v)} />}
                  {f.help && editing && <span className="small muted">{f.help}</span>}
                </div>
              );
            })}
          </div>

          {cfg.lines?.filter((s) => !s.show || s.show(doc)).map((s) => (
            <LinesEditor key={s.key} section={{ ...s, fields: s.fields.filter((f) => !f.staffOnly || staff).map(withFilters) }} doc={doc}
              rows={(doc[s.key] as Doc[]) ?? []} editing={editing} onChange={(rows) => set(s.key, rows)} />
          ))}

          {cfg.summary && (
            <div style={{ display: 'flex', justifyContent: 'flex-end', padding: 16, borderTop: '1px solid var(--line)' }}>
              <dl style={{ display: 'grid', gridTemplateColumns: 'auto auto', gap: '6px 24px', margin: 0, fontSize: 13.5 }}>
                {cfg.summary.filter((x) => !x.show || x.show(doc)).map((x) => (
                  <div key={x.key} style={{ display: 'contents' }}>
                    <dt className="muted">{x.label}</dt>
                    <dd className="mono" style={{ margin: 0, textAlign: 'right', fontWeight: 500 }}>{formatCell(x.type, doc[x.key]) || '–'}</dd>
                  </div>
                ))}
              </dl>
            </div>
          )}
          {editing && cfg.summary && <div className="small muted" style={{ padding: '0 16px 12px', textAlign: 'right' }}>Nilai dihitung ulang server saat disimpan.</div>}
          {err && <div className="alert alert-err" style={{ margin: 12, whiteSpace: 'pre-wrap' }}>{err}</div>}
        </div>
        {Extra && <Extra doc={doc} meta={meta} editing={editing} reload={reload} setDoc={setDoc} />}
      </div>
      <div style={{ flex: '1 1 320px', minWidth: 0, maxWidth: 420 }}>
        {numId != null ? <DocumentSidePanel docType={cfg.docType} id={numId} panel={panel.data} /> :
          <div className="card"><div className="empty small">Riwayat, approval, dan lampiran tersedia setelah draft disimpan.</div></div>}
      </div>
    </div>
  );
}

function emptyLines(cfg: DocConfig): Doc {
  const out: Doc = {};
  cfg.lines?.forEach((s) => { out[s.key] = s.newRow ? [s.newRow()] : []; });
  return out;
}

function isStaff(apps: string[] | undefined, cfg: DocConfig): boolean {
  const app = cfg.endpoint.split('/')[1]?.toUpperCase();
  return Boolean(apps?.includes(app === 'FIN' || app === 'HC' || app === 'PRE' ? app : ''));
}

/** Tabel baris yang bisa diedit (PRD §15.2 "isi dalam tab"). */
function LinesEditor({ section, rows, editing, onChange, doc }: {
  section: LineSection; rows: Doc[]; editing: boolean; onChange: (rows: Doc[]) => void; doc: Doc;
}) {
  const setCell = (i: number, key: string, v: unknown) => onChange(rows.map((r, j) => (j === i ? { ...r, [key]: v } : r)));
  return (
    <div style={{ borderTop: '1px solid var(--line)' }}>
      <div className="lbl" style={{ padding: '12px 16px 6px' }}>{section.title}</div>
      <div className="table-wrap">
        <table className="t">
          <thead>
            <tr>
              <th style={{ width: 34 }}>#</th>
              {section.fields.map((f) => <th key={f.key} style={{ textAlign: f.type === 'money' || f.type === 'number' ? 'right' : undefined, minWidth: f.type === 'lookup' ? 200 : undefined }}>{f.label}</th>)}
              {section.display?.map((d) => <th key={d.key} style={{ textAlign: d.type === 'money' || d.type === 'number' ? 'right' : undefined }}>{d.label}</th>)}
              {editing && <th style={{ width: 40 }} />}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && <tr><td colSpan={section.fields.length + (section.display?.length ?? 0) + 2} style={{ height: 'auto' }}><div className="empty small">Belum ada baris</div></td></tr>}
            {rows.map((r, i) => (
              <tr key={i}>
                <td className="mono muted">{i + 1}</td>
                {section.fields.map((col) => col.rowFilters ? { ...col, lookupFilters: col.rowFilters(r, doc) } : col).map((f) => (
                  <td key={f.key}>
                    <div className="field">
                      {f.readOnly && f.type !== 'lookup'
                        ? <input disabled value={formatCell(f.type, r[f.key])} style={{ textAlign: f.type === 'money' || f.type === 'number' ? 'right' : undefined }} aria-label={f.label} />
                        : <FieldInput def={{ ...f, label: '' }} id={`l-${section.key}-${i}-${f.key}`} value={r[f.key]} disabled={!editing || f.readOnly}
                          onChange={(v) => setCell(i, f.key, v)} />}
                    </div>
                  </td>
                ))}
                {section.display?.map((d) => <td key={d.key} className={d.type === 'money' || d.type === 'number' ? 'num mono' : undefined}>{formatCell(d.type, r[d.key])}</td>)}
                {editing && (
                  <td><button className="iconbtn" type="button" aria-label="Hapus baris" onClick={() => onChange(rows.filter((_, j) => j !== i))}><Icon name="trash" /></button></td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {editing && (
        <div style={{ padding: '8px 16px 12px' }}>
          <button className="btn btn-sm" type="button" onClick={() => onChange([...rows, section.newRow ? section.newRow() : {}])}>
            <Icon name="plus" size={13} />{section.addLabel ?? 'Tambah baris'}
          </button>
        </div>
      )}
    </div>
  );
}
