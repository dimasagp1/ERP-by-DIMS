import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { errorText } from '../components/ui';
import { ThemeToggle } from '../components/ThemeToggle';

export function LoginPage() {
  const { me, login } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const [username, setUsername] = useState('dimas');
  const [password, setPassword] = useState('Demo#2026');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const from = (loc.state as { from?: string } | null)?.from ?? '/';
  if (me) return <Navigate to={from} replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      await login(username || 'dimas', password || 'Demo#2026');
      nav(from, { replace: true });
    } catch (ex) {
      setErr(errorText(ex));
    } finally {
      setBusy(false);
    }
  };

  const quickLogin = async (user: string, pass = 'Demo#2026') => {
    setUsername(user);
    setPassword(pass);
    setBusy(true);
    setErr(null);
    try {
      await login(user, pass);
      nav(from, { replace: true });
    } catch (ex) {
      setErr(errorText(ex));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        background: 'var(--launcher-bg)',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 20,
        position: 'relative',
      }}
    >
      {/* Top right Theme Toggle */}
      <div style={{ position: 'absolute', top: 20, right: 24 }}>
        <ThemeToggle size={22} />
      </div>

      <div
        className="card"
        style={{
          width: '100%',
          maxWidth: 420,
          padding: '32px 28px',
          display: 'flex',
          flexDirection: 'column',
          gap: 18,
          boxShadow: 'var(--launcher-card-shadow)',
          borderRadius: 14,
        }}
      >
        {/* Logo & Brand */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div
            className="mono"
            style={{
              width: 36,
              height: 36,
              borderRadius: 8,
              background: '#4F46E5',
              color: '#FFFFFF',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 16,
              fontWeight: 700,
              boxShadow: '0 2px 6px rgba(79, 70, 229, 0.4)',
            }}
          >
            H
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: 18 }}>
              Herbatech <span style={{ fontWeight: 400, color: 'var(--text-3)' }}>ERP</span>
            </div>
            <div className="small muted" style={{ fontSize: 11.5 }}>
              Enterprise Edition v2.0
            </div>
          </div>
        </div>

        <div className="small muted">
          Sistem ERP Manufaktur Terintegrasi — Masuk dengan akun Anda untuk mengakses seluruh modul.
        </div>

        {/* Form Login */}
        <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <label className="field">
            <span style={{ fontWeight: 500 }}>Nama Pengguna (Username)</span>
            <input
              autoFocus
              autoComplete="username"
              placeholder="mis. dimas atau admin"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </label>

          <label className="field">
            <span style={{ fontWeight: 500 }}>Kata Sandi (Password)</span>
            <input
              type="password"
              autoComplete="current-password"
              placeholder="Masukkan kata sandi"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>

          {err && <div className="alert alert-err">{err}</div>}

          <button
            className="btn btn-dark"
            type="submit"
            disabled={busy}
            style={{ height: 40, justifyContent: 'center', fontSize: 14, fontWeight: 600, marginTop: 4 }}
          >
            {busy ? 'Memproses Masuk…' : 'Masuk ke Sistem'}
          </button>
        </form>

        {/* Quick Demo Login Buttons */}
        <div style={{ borderTop: '1px solid var(--line-soft)', paddingTop: 16, marginTop: 4 }}>
          <div className="lbl" style={{ marginBottom: 10, fontSize: 11 }}>
            Masuk Cepat Akun Demo (Satu Klik):
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
            <button
              type="button"
              className="btn btn-sm"
              disabled={busy}
              onClick={() => quickLogin('dimas')}
              style={{ justifyContent: 'center', fontWeight: 600 }}
            >
              👤 Dimas (Superuser)
            </button>
            <button
              type="button"
              className="btn btn-sm"
              disabled={busy}
              onClick={() => quickLogin('admin')}
              style={{ justifyContent: 'center', fontWeight: 600 }}
            >
              🛡️ Admin Sistem
            </button>
            <button
              type="button"
              className="btn btn-sm"
              disabled={busy}
              onClick={() => quickLogin('scm.manager')}
              style={{ justifyContent: 'center' }}
            >
              📦 SCM Manager
            </button>
            <button
              type="button"
              className="btn btn-sm"
              disabled={busy}
              onClick={() => quickLogin('pre.manager')}
              style={{ justifyContent: 'center' }}
            >
              🏭 PRE Manager
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
