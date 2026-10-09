import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Preferences } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { DataTable } from '../components/DataTable';
import { errorText, useToast } from '../components/ui';
import { fmtRp } from '../lib/format';
import { menuPath, useApps } from '../lib/meta';
import { MASTER_MENUS } from '../modules/master/configs';
import { ManageUsersModal } from '../components/ManageUsersModal';
import { TotpSecurityModal } from '../components/TotpSecurityModal';
import { DataMigrationModal } from '../modules/master/DataMigrationModal';

interface DocType { id: number; code: string; name: string; prefix: string; resetPeriod: string; menuCode: string; requiresEsign: boolean }
interface Rule { id: number; docTypeCode: string; level: number; minAmount: number; label: string; approverType: string }
interface AppSettings {
  values: Record<string, string>;
  docTypes: DocType[];
  approvalRules: Rule[];
  access: { role: string; roleName: string; viewScope: string; actions: string[] }[];
  canEdit: boolean;
}

const CATS = [
  ['umum', 'Umum'],
  ['nomor', 'Penomoran dokumen'],
  ['approval', 'Matriks approval'],
  ['master', 'Master khusus'],
  ['notif', 'Notifikasi'],
  ['akses', 'Hak akses aplikasi'],
  ['users', 'Kelola Pengguna (Users)'],
  ['migrasi', 'Migrasi Data Awal (Go-Live)'],
  ['pref', 'Preferensi saya'],
  ['password', 'Kata sandi'],
] as const;
const SCOPE: Record<string, string> = { OWN: 'Milik sendiri', SECTION: 'Seksi', DEPARTMENT: 'Departemen', ALL: 'Semua' };
const NOTIF = [
  ['approval', 'Dokumen menunggu approval saya'],
  ['deadline', 'Tenggat tinggal 3 hari'],
  ['rejected', 'Dokumen saya ditolak atau dikembalikan'],
  ['digest', 'Ringkasan harian lewat email pukul 07.00'],
  ['escalation', 'Eskalasi bila approval > 4 hari kerja'],
];

/** Pengaturan aplikasi (gear, PRD §15.4) + preferensi pengguna. */
export function SettingsPage() {
  const { app = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const cat = params.get('cat') ?? 'umum';
  const { byApp } = useApps();
  const cur = byApp.get(app);
  const q = useQuery({ queryKey: ['settings', app], queryFn: () => api.get<AppSettings>(`/settings/${app}`) });
  if (!cur) return null;
  return (
    <div className="page">
      <div>
        <div className="crumb">{cur.name} / Pengaturan Modul</div>
        <h1 style={{ marginTop: 4 }}>Pengaturan {cur.shortName}</h1>
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 20, alignItems: 'flex-start' }}>
        <div style={{ flex: '1 1 220px', maxWidth: 260, display: 'flex', flexDirection: 'column', gap: 2 }}>
          {CATS.map(([k, label]) => (
            <button
              key={k}
              type="button"
              className={`catbtn${cat === k ? ' on' : ''}`}
              style={cat === k ? { boxShadow: `inset 2px 0 0 ${cur.color}` } : undefined}
              onClick={() => setParams({ cat: k })}
            >
              {k === 'master' ? `Master khusus ${cur.shortName}` : label}
            </button>
          ))}
        </div>
        <div className="card" style={{ flex: '999 1 520px', minWidth: 0, padding: '20px 24px' }}>
          {q.error && <div className="alert alert-err">{errorText(q.error)}</div>}
          {cat === 'umum' && q.data && <General app={app} data={q.data} />}
          {cat === 'nomor' && q.data && <Numbering data={q.data} />}
          {cat === 'approval' && q.data && <Approval data={q.data} />}
          {cat === 'master' && <Masters app={app} masters={cur.masters} />}
          {cat === 'notif' && <Notifications />}
          {cat === 'akses' && q.data && <Access data={q.data} />}
          {cat === 'users' && <UsersSettingsSection />}
          {cat === 'migrasi' && <MigrationSettingsSection />}
          {cat === 'pref' && <Prefs />}
          {cat === 'password' && <ChangePassword />}
        </div>
      </div>
    </div>
  );
}

function General({ app, data }: { app: string; data: AppSettings }) {
  const [v, setV] = useState(data.values);
  const toast = useToast();
  const qc = useQueryClient();
  useEffect(() => setV(data.values), [data.values]);
  const save = async () => {
    try {
      await api.put(`/settings/${app}`, v);
      qc.invalidateQueries({ queryKey: ['settings', app] });
      toast.ok('Pengaturan disimpan');
    } catch (e) {
      toast.error(e);
    }
  };
  const box = (key: string, label: string) => (
    <label className="check"><input type="checkbox" disabled={!data.canEdit} checked={v[key] === 'true'} onChange={(e) => setV({ ...v, [key]: String(e.target.checked) })} />{label}</label>
  );
  return (
    <>
      <h2 style={{ marginBottom: 16 }}>Umum</h2>
      {!data.canEdit && <div className="alert alert-info" style={{ marginBottom: 12 }}>Pengaturan aplikasi hanya bisa diubah Manager atau Admin.</div>}
      <div className="grid-form">
        <label className="field"><span>Nama tampilan aplikasi</span><input disabled={!data.canEdit} value={v.displayName ?? ''} onChange={(e) => setV({ ...v, displayName: e.target.value })} /></label>
        <label className="field"><span>Zona waktu</span><select disabled><option>WIB (UTC+07:00)</option></select></label>
        <label className="field"><span>Mata uang dasar</span><select disabled><option>IDR — Rupiah</option></select></label>
      </div>
      <div style={{ marginTop: 16 }}>
        {box('attachmentRequiredOnSubmit', 'Wajib lampiran saat dokumen diajukan')}
        {box('reasonRequiredOnCancel', 'Minta alasan setiap pembatalan atau reversal')}
        {box('allowNextDayDate', 'Izinkan tanggal dokumen 1 hari ke depan')}
      </div>
      {data.canEdit && <div className="row" style={{ justifyContent: 'flex-end', marginTop: 20 }}><button className="btn btn-dark" type="button" onClick={save}>Simpan</button></div>}
    </>
  );
}

function Numbering({ data }: { data: AppSettings }) {
  const nav = useNavigate();
  const { byMenu } = useApps();
  return (
    <>
      <h2>Penomoran dokumen</h2>
      <p className="small muted">Format <span className="mono">[KODE]/[PLANT]/[YYMM]/[00001]</span>. Nomor tidak pernah dipakai ulang walau dokumen dibatalkan.</p>
      <DataTable rows={data.docTypes} rowKey={(d) => d.id} empty="Belum ada jenis dokumen untuk aplikasi ini"
        columns={[
          { key: 'prefix', label: 'Kode', mono: true },
          { key: 'name', label: 'Dokumen' },
          { key: 'fmt', label: 'Format', mono: true, render: (d) => `${d.prefix}/[PLANT]/${d.resetPeriod === 'YEARLY' ? '[YY]' : '[YYMM]'}/[00001]` },
          { key: 'ex', label: 'Contoh', mono: true, render: (d) => `${d.prefix}/P1/2610/00042` },
          { key: 'reset', label: 'Reset', render: (d) => ({ MONTHLY: 'Bulanan', YEARLY: 'Tahunan', NEVER: 'Tidak pernah' } as Record<string, string>)[d.resetPeriod] },
          { key: 'esign', label: 'TTE', render: (d) => d.requiresEsign ? 'Wajib' : '–' },
        ]} />
      {byMenu.has('SYS-05') && <div style={{ marginTop: 12 }}><button className="btn" type="button" onClick={() => nav(menuPath('SYS-05'))}>Kelola di SYS-05</button></div>}
    </>
  );
}

function Approval({ data }: { data: AppSettings }) {
  const nav = useNavigate();
  const { byMenu } = useApps();
  return (
    <>
      <h2>Matriks approval</h2>
      <p className="small muted">Approver mengikuti struktur organisasi di HC-02. Batas nilai bisa diubah per dokumen (SYS-04).</p>
      <DataTable rows={data.approvalRules} rowKey={(r) => r.id} empty="Dokumen aplikasi ini belum memakai approval"
        columns={[
          { key: 'docTypeCode', label: 'Dokumen', mono: true },
          { key: 'level', label: 'Level', render: (r) => `Level ${r.level}` },
          { key: 'label', label: 'Approver' },
          { key: 'minAmount', label: 'Berlaku mulai', align: 'right', render: (r) => r.minAmount > 0 ? `≥ ${fmtRp(r.minAmount)}` : 'Semua nilai' },
        ]} />
      {byMenu.has('SYS-04') && <div style={{ marginTop: 12 }}><button className="btn" type="button" onClick={() => nav(menuPath('SYS-04'))}>Kelola di SYS-04</button></div>}
    </>
  );
}

/** Master khusus departemen (menu -99). Yang sudah tersedia ditautkan; lainnya mengikuti fase modulnya. */
function Masters({ app, masters }: { app: string; masters: string[] }) {
  const nav = useNavigate();
  const available: Record<string, string | undefined> = {
    'Mapping akun otomatis': MASTER_MENUS['FIN-02'] ? `${menuPath('FIN-02')}?tab=mappings` : undefined,
    'Periode akuntansi': menuPath('FIN-70'),
    'Parameter global': menuPath('SYS-05'),
  };
  return (
    <>
      <h2>Master khusus</h2>
      <p className="small muted">Master bersama (item, supplier, karyawan) dikelola di Pengaturan Sistem.</p>
      {masters.map((m) => (
        <div key={m} className="row" style={{ justifyContent: 'space-between', padding: '12px 0', borderBottom: '1px solid var(--line-soft)' }}>
          <span>{m}</span>
          {available[m] && (app === 'FIN' || app === 'SYS')
            ? <button className="btn" type="button" onClick={() => nav(available[m]!)}>Kelola</button>
            : <span className="small muted">Tersedia bersama menu transaksinya</span>}
        </div>
      ))}
    </>
  );
}

function Notifications() {
  const { me, savePreferences } = useAuth();
  const toast = useToast();
  const set = new Set((me?.preferences.notifyPrefs ?? '').split(',').filter(Boolean));
  const toggle = async (k: string, on: boolean) => {
    on ? set.add(k) : set.delete(k);
    try {
      await savePreferences({ notifyPrefs: [...set].join(',') });
    } catch (e) {
      toast.error(e);
    }
  };
  return (
    <>
      <h2 style={{ marginBottom: 8 }}>Notifikasi</h2>
      {NOTIF.map(([k, label]) => (
        <label key={k} className="check"><input type="checkbox" checked={set.has(k)} onChange={(e) => toggle(k, e.target.checked)} />{label}</label>
      ))}
    </>
  );
}

function Access({ data }: { data: AppSettings }) {
  const has = (a: string[], x: string) => a.includes(x) ? 'Ya' : '–';
  return (
    <>
      <h2>Hak akses aplikasi</h2>
      <p className="small muted">Per menu × aksi × plant. Pengguna diatur di Pengaturan Sistem (SYS-03).</p>
      <DataTable rows={data.access} rowKey={(r) => r.role}
        columns={[
          { key: 'roleName', label: 'Peran' },
          { key: 'scope', label: 'Lihat', render: (r) => SCOPE[r.viewScope] },
          { key: 'create', label: 'Buat', render: (r) => has(r.actions, 'CREATE') },
          { key: 'approve', label: 'Approve', render: (r) => has(r.actions, 'APPROVE') },
          { key: 'post', label: 'Posting', render: (r) => has(r.actions, 'POST') },
          { key: 'cancel', label: 'Batal', render: (r) => r.actions.includes('CANCEL') ? 'Dengan alasan' : '–' },
          { key: 'release', label: 'Release', render: (r) => has(r.actions, 'RELEASE') },
        ]} />
    </>
  );
}

function UsersSettingsSection() {
  const [modalOpen, setModalOpen] = useState(false);
  const nav = useNavigate();
  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
        <div>
          <h2>Manajemen Pengguna (Manage Users)</h2>
          <p className="small muted" style={{ margin: '4px 0 0' }}>
            Kelola akun karyawan, peran (roles), izin akses per modul, dan kata sandi.
          </p>
        </div>
        <button
          type="button"
          className="btn btn-dark"
          onClick={() => setModalOpen(true)}
          style={{ display: 'flex', alignItems: 'center', gap: 6 }}
        >
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
            <circle cx="9" cy="7" r="4" />
          </svg>
          Buka Kelola Pengguna
        </button>
      </div>

      <div
        style={{
          background: 'var(--surface-2)',
          padding: 20,
          borderRadius: 8,
          border: '1px solid var(--line-soft)',
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
        }}
      >
        <div style={{ fontWeight: 600 }}>Tindakan Cepat Pengguna:</div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
          <button type="button" className="btn" onClick={() => setModalOpen(true)}>
            + Tambah Pengguna Baru
          </button>
          <button type="button" className="btn" onClick={() => setModalOpen(true)}>
            Reset Password Karyawan
          </button>
          <button type="button" className="btn" onClick={() => nav(menuPath('SYS-03'))}>
            Buka Tabel Master SYS-03 (Users & Roles)
          </button>
        </div>
      </div>

      <ManageUsersModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </>
  );
}

function Prefs() {
  const { me, savePreferences } = useAuth();
  const toast = useToast();
  const [p, setP] = useState<Preferences | undefined>(me?.preferences);
  useEffect(() => setP(me?.preferences), [me?.preferences]);
  if (!p || !me) return null;
  const save = async () => {
    try {
      await savePreferences(p);
      if (p.theme === 'dark' || p.theme === 'light') {
        document.documentElement.dataset.theme = p.theme;
        localStorage.setItem('erp.theme', p.theme);
      }
      toast.ok('Preferensi disimpan');
    } catch (e) {
      toast.error(e);
    }
  };
  return (
    <>
      <h2 style={{ marginBottom: 16 }}>Preferensi saya</h2>
      <div className="grid-form">
        <label className="field"><span>Bahasa</span>
          <select value={p.locale} onChange={(e) => setP({ ...p, locale: e.target.value as Preferences['locale'] })}><option value="id">Bahasa Indonesia</option><option value="en">English</option></select>
        </label>
        <label className="field"><span>Tema</span>
          <select value={p.theme} onChange={(e) => {
            const next = e.target.value as Preferences['theme'];
            setP({ ...p, theme: next });
            if (next === 'dark' || next === 'light') {
              document.documentElement.dataset.theme = next;
              localStorage.setItem('erp.theme', next);
            }
          }}><option value="light">Terang</option><option value="dark">Gelap</option><option value="system">Ikuti sistem</option></select>
        </label>
        <label className="field"><span>Kepadatan tabel</span>
          <select value={p.density} onChange={(e) => setP({ ...p, density: e.target.value as Preferences['density'] })}><option value="comfortable">Nyaman (44 px)</option><option value="compact">Padat (32 px)</option></select>
        </label>
        <label className="field"><span>Halaman awal</span>
          <select value={p.startPage} onChange={(e) => setP({ ...p, startPage: e.target.value as Preferences['startPage'] })}><option value="launcher">Launcher</option><option value="last">Aplikasi terakhir</option></select>
        </label>
        <label className="field"><span>Plant default</span>
          <select value={p.defaultPlantId ?? ''} onChange={(e) => setP({ ...p, defaultPlantId: e.target.value ? Number(e.target.value) : null })}>
            {me.plants.map((pl) => <option key={pl.id} value={pl.id}>{pl.name}</option>)}
          </select>
        </label>
      </div>
      {p.locale === 'en' && <div className="alert alert-info" style={{ marginTop: 12 }}>Terjemahan bahasa Inggris untuk antarmuka sedang disiapkan; label menu mengikuti PRD (Bahasa Indonesia).</div>}
      <div className="row" style={{ justifyContent: 'flex-end', marginTop: 20 }}><button className="btn btn-dark" type="button" onClick={save}>Simpan</button></div>
    </>
  );
}

function ChangePassword() {
  const [oldPassword, setOld] = useState('');
  const [newPassword, setNew] = useState('');
  const [confirm, setConfirm] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [totpOpen, setTotpOpen] = useState(false);
  const toast = useToast();
  const save = async () => {
    if (newPassword !== confirm) return setErr('Konfirmasi kata sandi tidak sama');
    try {
      await api.post('/auth/me/password', { oldPassword, newPassword });
      setOld(''); setNew(''); setConfirm(''); setErr(null);
      toast.ok('Kata sandi diganti');
    } catch (e) {
      setErr(errorText(e));
    }
  };
  return (
    <>
      <h2 style={{ marginBottom: 16 }}>Ganti kata sandi & Keamanan Akun</h2>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 440 }}>
        <label className="field"><span>Kata sandi lama</span><input type="password" autoComplete="current-password" value={oldPassword} onChange={(e) => setOld(e.target.value)} /></label>
        <label className="field"><span>Kata sandi baru (min. 8 karakter)</span><input type="password" autoComplete="new-password" value={newPassword} onChange={(e) => setNew(e.target.value)} /></label>
        <label className="field"><span>Ulangi kata sandi baru</span><input type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} /></label>
        {err && <div className="alert alert-err">{err}</div>}
        <div><button className="btn btn-dark" type="button" onClick={save}>Simpan Kata Sandi</button></div>

        <div style={{ borderTop: '1px solid var(--line-soft)', marginTop: 12, paddingTop: 16 }}>
          <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 4 }}>Autentikasi Dua Faktor (2FA / TOTP)</div>
          <p className="small muted" style={{ margin: '0 0 10px' }}>
            Wajib untuk Approver Dokumen Berjenjang & Verifikator Keuangan (PRD §17).
          </p>
          <button
            type="button"
            className="btn"
            onClick={() => setTotpOpen(true)}
            style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
          >
            <span>🔐</span> Kelola Autentikasi 2FA
          </button>
        </div>
      </div>

      <TotpSecurityModal open={totpOpen} onClose={() => setTotpOpen(false)} />
    </>
  );
}

function MigrationSettingsSection() {
  const [modalOpen, setModalOpen] = useState(false);

  const templates = [
    { title: 'Saldo Awal GL & Buku Besar', file: 'template_migrasi_saldo_awal_gl.csv', mod: 'FIN', desc: 'Neraca cut-off, kas/bank, piutang, hutang' },
    { title: 'Saldo Stok per Lot & Lokasi', file: 'template_migrasi_saldo_stok_lot.csv', mod: 'SCM', desc: 'Opname fisik lot/batch simplisia, ekstrak, FG' },
    { title: 'Master Karyawan & Penugasan', file: 'template_migrasi_karyawan.csv', mod: 'HC', desc: 'NIK, jabatan, departemen, plant, gaji pokok' },
    { title: 'Master Item Simplisia & Produk', file: 'template_migrasi_master_item.csv', mod: 'SYS', desc: 'Kode bahan, kategori, spesifikasi, izin edar' },
  ];

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
        <div>
          <h2>Migrasi Data Awal (Go-Live Cutover)</h2>
          <p className="small muted" style={{ margin: '4px 0 0' }}>
            Alat bantu impor data saldo awal, master data batch/lot, dan cutover operasional sesuai kaidah GMP & ALCOA+.
          </p>
        </div>
        <button
          type="button"
          className="btn btn-dark"
          style={{ display: 'inline-flex', alignItems: 'center', gap: 8, whiteSpace: 'nowrap' }}
          onClick={() => setModalOpen(true)}
        >
          <span>📥</span> Buka Importer Data
        </button>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 14, marginBottom: 24 }}>
        {templates.map((t) => (
          <div key={t.file} style={{ border: '1px solid var(--line)', borderRadius: 8, padding: 14, background: 'var(--surface-sunken)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
              <span style={{ fontWeight: 600, fontSize: 13 }}>{t.title}</span>
              <span className="badge" style={{ fontSize: 11 }}>{t.mod}</span>
            </div>
            <p className="small muted" style={{ margin: '0 0 10px', minHeight: 34 }}>{t.desc}</p>
            <div style={{ display: 'flex', gap: 8 }}>
              <a
                href={`/templates/${t.file}`}
                download
                className="btn btn-sm"
                style={{ textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: 4 }}
              >
                <span>💾</span> Unduh CSV
              </a>
            </div>
          </div>
        ))}
      </div>

      <div style={{ borderTop: '1px solid var(--line-soft)', paddingTop: 16 }}>
        <h3 style={{ fontSize: 15, marginBottom: 10 }}>Checklist Kesiapan Cutover Go-Live</h3>
        <ul style={{ paddingLeft: 20, margin: 0, fontSize: 13, lineHeight: 1.8, color: 'var(--ink-secondary)' }}>
          <li><strong>Tanggal Cut-off Resmi:</strong> Seluruh transaksi di sistem legacy dibekukan pada tanggal H-1 Cutover.</li>
          <li><strong>Stock Opname Fisik Bersama:</strong> SCM, Gudang, dan QA menandatangani Berita Acara Opname Lot & Karantina.</li>
          <li><strong>Verifikasi Neraca Saldo GL:</strong> Akun debet/kredit balance 100% dan telah disahkan oleh Finance Manager.</li>
          <li><strong>Integritas Data (ALCOA+):</strong> Setiap baris impor tercatat dalam Audit Trail dengan identitas operator pengunggah.</li>
          <li><strong>Uji Kualifikasi (IQ/OQ):</strong> Protokol validasi sistem terkomputerisasi telah disetujui tim QA.</li>
        </ul>
      </div>

      <DataMigrationModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </>
  );
}

