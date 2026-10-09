import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { errorText, useToast, Spinner } from './ui';
import { Icon } from './icons';

interface UserRecord {
  id: number;
  username: string;
  fullName: string;
  email?: string;
  active: boolean;
  lastLoginAt?: string;
  lockedUntil?: string;
}

interface RoleRecord {
  id: number;
  code: string;
  name: string;
  appCode: string;
}

interface UserRoleAssignment {
  roleId: number;
  appCode: string;
  plantId: number | null;
}

interface ManageUsersModalProps {
  open: boolean;
  onClose: () => void;
}

export function ManageUsersModal({ open, onClose }: ManageUsersModalProps) {
  const { me } = useAuth();
  const toast = useToast();
  const qc = useQueryClient();

  const [search, setSearch] = useState('');
  const [filterActive, setFilterActive] = useState<'all' | 'active' | 'inactive'>('all');

  // Modal sub-state
  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState<UserRecord | null>(null);
  const [resetPwdUser, setResetPwdUser] = useState<UserRecord | null>(null);
  const [roleUser, setRoleUser] = useState<UserRecord | null>(null);

  // Queries
  const usersQ = useQuery({
    queryKey: ['sys-users-manage'],
    queryFn: () => api.get<UserRecord[]>('/sys/users'),
    enabled: open,
  });

  const rolesQ = useQuery({
    queryKey: ['sys-roles-list'],
    queryFn: () => api.get<RoleRecord[]>('/sys/roles'),
    enabled: open,
  });

  // Toggle user active status
  const toggleActive = useMutation({
    mutationFn: async ({ id, active }: { id: number; active: boolean }) => {
      await api.put(`/sys/users/${id}`, { active });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sys-users-manage'] });
      toast.ok('Status pengguna berhasil diperbarui');
    },
    onError: (err) => toast.error(err),
  });

  // Filtered users
  const filteredUsers = useMemo(() => {
    const list = usersQ.data ?? [];
    return list.filter((u) => {
      const matchSearch =
        u.username.toLowerCase().includes(search.toLowerCase()) ||
        u.fullName.toLowerCase().includes(search.toLowerCase()) ||
        (u.email ?? '').toLowerCase().includes(search.toLowerCase());
      const matchActive =
        filterActive === 'all' ||
        (filterActive === 'active' && u.active) ||
        (filterActive === 'inactive' && !u.active);
      return matchSearch && matchActive;
    });
  }, [usersQ.data, search, filterActive]);

  if (!open) return null;

  return (
    <div className="modal-back" style={{ zIndex: 120 }}>
      <div
        className="modal"
        style={{
          maxWidth: 960,
          width: '95vw',
          maxHeight: '88vh',
          display: 'flex',
          flexDirection: 'column',
          background: 'var(--surface)',
        }}
      >
        {/* Header */}
        <div
          className="modal-h"
          style={{
            background: 'var(--surface-2)',
            borderBottom: '1px solid var(--line)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '16px 20px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: 8,
                background: '#4F46E5',
                color: '#FFFFFF',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
            </div>
            <div>
              <div style={{ fontSize: 16, fontWeight: 600 }}>Manajemen Pengguna (Manage Users)</div>
              <div className="small muted">
                Kelola akun pengguna, izin peran (roles), hak akses modul, dan status keamanan.
              </div>
            </div>
          </div>
          <button
            type="button"
            className="iconbtn"
            onClick={onClose}
            aria-label="Tutup"
            style={{ borderRadius: '50%' }}
          >
            <Icon name="x" size={18} />
          </button>
        </div>

        {/* Toolbar filter & action */}
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            gap: 12,
            padding: '12px 20px',
            background: 'var(--surface)',
            borderBottom: '1px solid var(--line-soft)',
          }}
        >
          {/* Search box */}
          <div style={{ flex: '1 1 260px', position: 'relative' }}>
            <input
              type="text"
              placeholder="Cari nama pengguna, nama lengkap, atau email..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="input"
              style={{ paddingLeft: 34, height: 36, fontSize: 13 }}
            />
            <span
              style={{
                position: 'absolute',
                left: 10,
                top: 9,
                color: 'var(--text-3)',
                pointerEvents: 'none',
              }}
            >
              <Icon name="search" size={16} />
            </span>
          </div>

          {/* Status filter */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <button
              type="button"
              className={`btn btn-sm ${filterActive === 'all' ? 'btn-dark' : ''}`}
              onClick={() => setFilterActive('all')}
            >
              Semua ({usersQ.data?.length ?? 0})
            </button>
            <button
              type="button"
              className={`btn btn-sm ${filterActive === 'active' ? 'btn-dark' : ''}`}
              onClick={() => setFilterActive('active')}
            >
              Aktif ({usersQ.data?.filter((u) => u.active).length ?? 0})
            </button>
            <button
              type="button"
              className={`btn btn-sm ${filterActive === 'inactive' ? 'btn-dark' : ''}`}
              onClick={() => setFilterActive('inactive')}
            >
              Non-aktif ({usersQ.data?.filter((u) => !u.active).length ?? 0})
            </button>
          </div>

          {/* Create user button */}
          <button
            type="button"
            className="btn btn-dark"
            onClick={() => setCreateOpen(true)}
            style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 6 }}
          >
            <Icon name="plus" size={15} /> Tambah Pengguna
          </button>
        </div>

        {/* Body Table */}
        <div className="modal-b" style={{ padding: 0, flex: 1, overflowY: 'auto' }}>
          {usersQ.isLoading ? (
            <div style={{ padding: 48, textAlign: 'center' }}>
              <Spinner />
              <div className="small muted" style={{ marginTop: 12 }}>
                Memuat data pengguna...
              </div>
            </div>
          ) : usersQ.error ? (
            <div className="alert alert-err" style={{ margin: 20 }}>
              {errorText(usersQ.error)}
            </div>
          ) : filteredUsers.length === 0 ? (
            <div style={{ padding: 48, textAlign: 'center', color: 'var(--text-3)' }}>
              Tidak ada pengguna yang cocok dengan filter pencarian.
            </div>
          ) : (
            <div className="table-wrap">
              <table className="t">
                <thead>
                  <tr>
                    <th style={{ width: 44 }}>#</th>
                    <th>Pengguna</th>
                    <th>Nama Lengkap</th>
                    <th>Email</th>
                    <th style={{ textAlign: 'center', width: 100 }}>Status</th>
                    <th style={{ textAlign: 'right', width: 220 }}>Tindakan</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredUsers.map((u, idx) => {
                    const initial = u.fullName.trim().charAt(0).toUpperCase();
                    const isSelf = u.username === me?.username;
                    return (
                      <tr key={u.id}>
                        <td className="mono small muted">{idx + 1}</td>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                            <div
                              style={{
                                width: 32,
                                height: 32,
                                borderRadius: '50%',
                                background: u.active ? '#3B82F6' : 'var(--line-strong)',
                                color: '#FFFFFF',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                fontSize: 13,
                                fontWeight: 600,
                                flex: 'none',
                              }}
                            >
                              {initial}
                            </div>
                            <div>
                              <div className="mono" style={{ fontWeight: 600, fontSize: 13 }}>
                                {u.username}
                                {isSelf && (
                                  <span
                                    className="badge"
                                    style={{
                                      marginLeft: 6,
                                      fontSize: 10,
                                      background: 'var(--link)',
                                    }}
                                  >
                                    Anda
                                  </span>
                                )}
                              </div>
                            </div>
                          </div>
                        </td>
                        <td>
                          <span style={{ fontWeight: 500 }}>{u.fullName}</span>
                        </td>
                        <td className="mono small muted">{u.email || '–'}</td>
                        <td style={{ textAlign: 'center' }}>
                          <span
                            className="chip"
                            style={{
                              background: u.active ? 'var(--chip-approved-bg)' : 'var(--chip-rejected-bg)',
                              color: u.active ? 'var(--chip-approved-fg)' : 'var(--chip-rejected-fg)',
                              fontSize: 11.5,
                            }}
                          >
                            {u.active ? 'Aktif' : 'Non-aktif'}
                          </span>
                        </td>
                        <td>
                          <div
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'flex-end',
                              gap: 6,
                            }}
                          >
                            <button
                              type="button"
                              className="btn btn-sm"
                              onClick={() => setRoleUser(u)}
                              title="Kelola Peran & Akses Modul"
                            >
                              Peran
                            </button>
                            <button
                              type="button"
                              className="btn btn-sm"
                              onClick={() => setResetPwdUser(u)}
                              title="Reset Kata Sandi"
                            >
                              Password
                            </button>
                            <button
                              type="button"
                              className="btn btn-sm"
                              onClick={() => setEditUser(u)}
                              title="Edit Profil Pengguna"
                            >
                              Edit
                            </button>
                            {!isSelf && (
                              <button
                                type="button"
                                className={`btn btn-sm ${u.active ? 'btn-danger' : ''}`}
                                onClick={() =>
                                  toggleActive.mutate({ id: u.id, active: !u.active })
                                }
                                title={u.active ? 'Non-aktifkan akun' : 'Aktifkan akun'}
                              >
                                {u.active ? 'Matikan' : 'Aktifkan'}
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Footer */}
        <div
          className="modal-f"
          style={{
            background: 'var(--surface-2)',
            borderTop: '1px solid var(--line)',
            padding: '12px 20px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <div className="small muted">
            Total {filteredUsers.length} pengguna terdaftar di sistem.
          </div>
          <button type="button" className="btn" onClick={onClose}>
            Tutup
          </button>
        </div>
      </div>

      {/* Sub-modal: Buat Pengguna Baru */}
      {createOpen && (
        <CreateUserModal
          roles={rolesQ.data ?? []}
          onClose={() => setCreateOpen(false)}
          onSuccess={() => {
            setCreateOpen(false);
            qc.invalidateQueries({ queryKey: ['sys-users-manage'] });
          }}
        />
      )}

      {/* Sub-modal: Edit Pengguna */}
      {editUser && (
        <EditUserModal
          user={editUser}
          onClose={() => setEditUser(null)}
          onSuccess={() => {
            setEditUser(null);
            qc.invalidateQueries({ queryKey: ['sys-users-manage'] });
          }}
        />
      )}

      {/* Sub-modal: Reset Password */}
      {resetPwdUser && (
        <ResetPasswordModal
          user={resetPwdUser}
          onClose={() => setResetPwdUser(null)}
          onSuccess={() => {
            setResetPwdUser(null);
            qc.invalidateQueries({ queryKey: ['sys-users-manage'] });
          }}
        />
      )}

      {/* Sub-modal: Kelola Peran Pengguna */}
      {roleUser && (
        <UserRolesModal
          user={roleUser}
          roles={rolesQ.data ?? []}
          onClose={() => setRoleUser(null)}
          onSuccess={() => {
            setRoleUser(null);
            qc.invalidateQueries({ queryKey: ['sys-users-manage'] });
          }}
        />
      )}
    </div>
  );
}

// ------------------------------------------------------------------ Modal Buat Pengguna Baru
function CreateUserModal({
  roles,
  onClose,
  onSuccess,
}: {
  roles: RoleRecord[];
  onClose: () => void;
  onSuccess: () => void;
}) {
  const toast = useToast();
  const [username, setUsername] = useState('');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [initialRole, setInitialRole] = useState(roles[0]?.id ?? 1);
  const [appCode, setAppCode] = useState('*');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !fullName.trim() || !password) {
      toast.error('Semua kolom wajib diisi');
      return;
    }
    setSubmitting(true);
    try {
      // 1. Buat pengguna baru
      const newUser = await api.post<UserRecord>('/sys/users', {
        username: username.trim().toLowerCase(),
        fullName: fullName.trim(),
        email: email.trim() || null,
        active: true,
      });

      // 2. Set password awal
      await api.post(`/sys/users/${newUser.id}/password`, {
        newPassword: password,
      });

      // 3. Tetapkan peran awal bila dipilih
      if (initialRole) {
        await api.put(`/sys/users/${newUser.id}/roles`, [
          {
            roleId: initialRole,
            appCode: appCode,
            plantId: null,
          },
        ]);
      }

      toast.ok(`Pengguna ${username} berhasil dibuat`);
      onSuccess();
    } catch (err) {
      toast.error(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-back" style={{ zIndex: 130 }}>
      <div className="modal" style={{ maxWidth: 480 }}>
        <div className="modal-h">
          <span>Tambah Pengguna Baru</span>
          <button type="button" className="iconbtn" onClick={onClose}>
            <Icon name="x" size={16} />
          </button>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="modal-b" style={{ gap: 14 }}>
            <label className="field">
              <span className="req">Nama Pengguna (Username)</span>
              <input
                required
                className="mono"
                placeholder="mis. andi.saputra"
                value={username}
                onChange={(e) => setUsername(e.target.value.toLowerCase())}
              />
              <span className="small muted">Huruf kecil, angka, titik, atau garis bawah.</span>
            </label>

            <label className="field">
              <span className="req">Nama Lengkap</span>
              <input
                required
                placeholder="mis. Andi Saputra, S.Farm"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
              />
            </label>

            <label className="field">
              <span>Alamat Email</span>
              <input
                type="email"
                placeholder="andi@herbatech.co.id"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </label>

            <label className="field">
              <span className="req">Kata Sandi Awal (min. 8 karakter)</span>
              <input
                type="password"
                required
                minLength={8}
                placeholder="Minimal 8 karakter"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>

            <div className="grid-form" style={{ gridTemplateColumns: '1fr 1fr' }}>
              <label className="field">
                <span>Peran Awal</span>
                <select
                  value={initialRole}
                  onChange={(e) => setInitialRole(Number(e.target.value))}
                >
                  {roles.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name}
                    </option>
                  ))}
                </select>
              </label>

              <label className="field">
                <span>Cakupan Modul</span>
                <select value={appCode} onChange={(e) => setAppCode(e.target.value)}>
                  <option value="*">* Semua Modul</option>
                  <option value="SCM">SCM (Gudang)</option>
                  <option value="PRE">PRE (Manufaktur)</option>
                  <option value="FIN">FIN (Keuangan)</option>
                  <option value="PRC">PRC (Pengadaan)</option>
                  <option value="QMS">QMS (Mutu)</option>
                  <option value="RND">RND (Litbang)</option>
                  <option value="HC">HC (Personalia)</option>
                  <option value="GA">GA (Umum/Aset)</option>
                  <option value="SYS">SYS (Sistem)</option>
                </select>
              </label>
            </div>
          </div>
          <div className="modal-f">
            <button type="button" className="btn" onClick={onClose} disabled={submitting}>
              Batal
            </button>
            <button type="submit" className="btn btn-dark" disabled={submitting}>
              {submitting ? 'Menyimpan...' : 'Simpan Pengguna'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ Modal Edit Pengguna
function EditUserModal({
  user,
  onClose,
  onSuccess,
}: {
  user: UserRecord;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const toast = useToast();
  const [fullName, setFullName] = useState(user.fullName);
  const [email, setEmail] = useState(user.email ?? '');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.put(`/sys/users/${user.id}`, {
        fullName: fullName.trim(),
        email: email.trim() || null,
      });
      toast.ok('Profil pengguna berhasil diperbarui');
      onSuccess();
    } catch (err) {
      toast.error(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-back" style={{ zIndex: 130 }}>
      <div className="modal" style={{ maxWidth: 440 }}>
        <div className="modal-h">
          <span>Edit Pengguna: {user.username}</span>
          <button type="button" className="iconbtn" onClick={onClose}>
            <Icon name="x" size={16} />
          </button>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="modal-b" style={{ gap: 14 }}>
            <label className="field">
              <span>Username (Read-only)</span>
              <input disabled value={user.username} className="mono" />
            </label>
            <label className="field">
              <span className="req">Nama Lengkap</span>
              <input
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
              />
            </label>
            <label className="field">
              <span>Email</span>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </label>
          </div>
          <div className="modal-f">
            <button type="button" className="btn" onClick={onClose} disabled={submitting}>
              Batal
            </button>
            <button type="submit" className="btn btn-dark" disabled={submitting}>
              {submitting ? 'Menyimpan...' : 'Simpan Perubahan'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ Modal Reset Password
function ResetPasswordModal({
  user,
  onClose,
  onSuccess,
}: {
  user: UserRecord;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const toast = useToast();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password.length < 8) {
      toast.error('Kata sandi minimal 8 karakter');
      return;
    }
    if (password !== confirm) {
      toast.error('Konfirmasi kata sandi tidak cocok');
      return;
    }
    setSubmitting(true);
    try {
      await api.post(`/sys/users/${user.id}/password`, {
        newPassword: password,
      });
      toast.ok(`Kata sandi untuk ${user.username} berhasil direset`);
      onSuccess();
    } catch (err) {
      toast.error(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-back" style={{ zIndex: 130 }}>
      <div className="modal" style={{ maxWidth: 420 }}>
        <div className="modal-h">
          <span>Reset Kata Sandi: {user.username}</span>
          <button type="button" className="iconbtn" onClick={onClose}>
            <Icon name="x" size={16} />
          </button>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="modal-b" style={{ gap: 14 }}>
            <div className="alert alert-info">
              Admin dapat menetapkan kata sandi baru untuk pengguna ini. Pengguna dapat mengubahnya
              kembali di Preferensi Akun.
            </div>
            <label className="field">
              <span className="req">Kata Sandi Baru (min. 8 karakter)</span>
              <input
                type="password"
                required
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>
            <label className="field">
              <span className="req">Konfirmasi Kata Sandi Baru</span>
              <input
                type="password"
                required
                minLength={8}
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
              />
            </label>
          </div>
          <div className="modal-f">
            <button type="button" className="btn" onClick={onClose} disabled={submitting}>
              Batal
            </button>
            <button type="submit" className="btn btn-dark" disabled={submitting}>
              {submitting ? 'Memproses...' : 'Reset Kata Sandi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ Modal Kelola Peran Pengguna
function UserRolesModal({
  user,
  roles,
  onClose,
  onSuccess,
}: {
  user: UserRecord;
  roles: RoleRecord[];
  onClose: () => void;
  onSuccess: () => void;
}) {
  const toast = useToast();
  const q = useQuery({
    queryKey: ['user-roles-detail', user.id],
    queryFn: () => api.get<UserRoleAssignment[]>(`/sys/users/${user.id}/roles`),
  });

  const [list, setList] = useState<UserRoleAssignment[]>([]);
  const [submitting, setSubmitting] = useState(false);

  // Inisialisasi list peran
  useMemo(() => {
    if (q.data) setList([...q.data]);
  }, [q.data]);

  const addRole = () => {
    setList([...list, { roleId: roles[0]?.id ?? 1, appCode: '*', plantId: null }]);
  };

  const removeRole = (index: number) => {
    setList(list.filter((_, i) => i !== index));
  };

  const handleSave = async () => {
    setSubmitting(true);
    try {
      await api.put(`/sys/users/${user.id}/roles`, list);
      toast.ok('Penugasan peran berhasil disimpan');
      onSuccess();
    } catch (err) {
      toast.error(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-back" style={{ zIndex: 130 }}>
      <div className="modal" style={{ maxWidth: 560 }}>
        <div className="modal-h">
          <span>Peran & Hak Akses: {user.fullName}</span>
          <button type="button" className="iconbtn" onClick={onClose}>
            <Icon name="x" size={16} />
          </button>
        </div>
        <div className="modal-b" style={{ gap: 14 }}>
          <div className="small muted">
            Peran menentukan wewenang pengguna (Lihat, Buat, Approve, Posting). Peran dapat berlaku
            untuk seluruh modul (*) atau khusus modul tertentu.
          </div>

          {q.isLoading ? (
            <Spinner />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {list.length === 0 ? (
                <div className="alert alert-warn">
                  Pengguna ini belum memiliki peran aktif. Klik "Tambah Peran" di bawah.
                </div>
              ) : (
                list.map((r, i) => (
                  <div
                    key={i}
                    style={{
                      display: 'flex',
                      gap: 8,
                      alignItems: 'center',
                      background: 'var(--surface-2)',
                      padding: '8px 12px',
                      borderRadius: 6,
                      border: '1px solid var(--line-soft)',
                    }}
                  >
                    <select
                      style={{ flex: '1.2 1 140px', height: 32, fontSize: 13 }}
                      value={r.roleId}
                      onChange={(e) =>
                        setList(
                          list.map((x, j) => (j === i ? { ...x, roleId: Number(e.target.value) } : x)),
                        )
                      }
                    >
                      {roles.map((role) => (
                        <option key={role.id} value={role.id}>
                          {role.name}
                        </option>
                      ))}
                    </select>

                    <select
                      style={{ flex: '1 1 120px', height: 32, fontSize: 13 }}
                      value={r.appCode}
                      onChange={(e) =>
                        setList(
                          list.map((x, j) => (j === i ? { ...x, appCode: e.target.value } : x)),
                        )
                      }
                    >
                      <option value="*">* Semua Modul</option>
                      <option value="SCM">SCM (Gudang)</option>
                      <option value="PRE">PRE (Manufaktur)</option>
                      <option value="FIN">FIN (Keuangan)</option>
                      <option value="PRC">PRC (Pengadaan)</option>
                      <option value="QMS">QMS (Mutu)</option>
                      <option value="RND">RND (Litbang)</option>
                      <option value="HC">HC (Personalia)</option>
                      <option value="GA">GA (Umum/Aset)</option>
                      <option value="SYS">SYS (Sistem)</option>
                    </select>

                    <button
                      type="button"
                      className="iconbtn"
                      onClick={() => removeRole(i)}
                      title="Hapus peran ini"
                      style={{ color: 'var(--danger)' }}
                    >
                      <Icon name="trash" size={16} />
                    </button>
                  </div>
                ))
              )}

              <button
                type="button"
                className="btn btn-sm"
                onClick={addRole}
                style={{ alignSelf: 'flex-start', marginTop: 4 }}
              >
                <Icon name="plus" size={14} /> Tambah Peran
              </button>
            </div>
          )}
        </div>
        <div className="modal-f">
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Batal
          </button>
          <button
            type="button"
            className="btn btn-dark"
            onClick={handleSave}
            disabled={submitting || q.isLoading}
          >
            {submitting ? 'Menyimpan...' : 'Simpan Peran'}
          </button>
        </div>
      </div>
    </div>
  );
}
