import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { errorText } from '../components/ui';

export function LoginPage() {
  const { me, login } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const from = (loc.state as { from?: string } | null)?.from ?? '/';
  if (me) return <Navigate to={from} replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      await login(username, password);
      nav(from, { replace: true });
    } catch (ex) {
      setErr(errorText(ex));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ minHeight: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16 }}>
      <form onSubmit={submit} className="card" style={{ width: '100%', maxWidth: 380, padding: 28, display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div className="mono" style={{ width: 32, height: 32, borderRadius: 7, background: 'var(--accent)', color: 'var(--text-on-dark)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 500 }}>H</div>
          <div style={{ fontWeight: 600, fontSize: 17 }}>Herbatech <span style={{ fontWeight: 400, color: 'var(--text-3)' }}>ERP</span></div>
        </div>
        <div className="small muted">ERP Manufaktur Terintegrasi — masuk dengan akun perusahaan Anda.</div>
        <label className="field"><span>Nama pengguna</span>
          <input autoFocus autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label className="field"><span>Kata sandi</span>
          <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {err && <div className="alert alert-err">{err}</div>}
        <button className="btn btn-dark" type="submit" disabled={busy} style={{ height: 38, justifyContent: 'center' }}>{busy ? 'Memproses…' : 'Masuk'}</button>
      </form>
    </div>
  );
}
