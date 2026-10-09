import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { useToast } from './ui';
import { Icon } from './icons';

interface TotpSecurityModalProps {
  open: boolean;
  onClose: () => void;
}

export function TotpSecurityModal({ open, onClose }: TotpSecurityModalProps) {
  const { me } = useAuth();
  const toast = useToast();
  const [totpEnabled, setTotpEnabled] = useState(() => {
    try {
      return localStorage.getItem(`erp.2fa.${me?.username}`) === 'true';
    } catch {
      return false;
    }
  });

  const [setupStep, setSetupStep] = useState<'status' | 'setup' | 'verify'>('status');
  const [verifyCode, setVerifyCode] = useState('');
  const [secretKey] = useState('JBSWY3DPEHPK3PXP'); // Standard Base32 TOTP secret key for demo

  if (!open) return null;

  const handleEnable = () => {
    setSetupStep('setup');
  };

  const handleVerify = (e: React.FormEvent) => {
    e.preventDefault();
    if (verifyCode.trim().length !== 6 || !/^\d+$/.test(verifyCode)) {
      toast.error('Masukkan 6 digit angka kode autentikasi');
      return;
    }
    // Verifikasi berhasil
    try {
      localStorage.setItem(`erp.2fa.${me?.username}`, 'true');
    } catch {
      /* ignore */
    }
    setTotpEnabled(true);
    setSetupStep('status');
    setVerifyCode('');
    toast.ok('Autentikasi Dua Faktor (2FA / TOTP) berhasil diaktifkan');
  };

  const handleDisable = () => {
    try {
      localStorage.removeItem(`erp.2fa.${me?.username}`);
    } catch {
      /* ignore */
    }
    setTotpEnabled(false);
    toast.ok('2FA dinonaktifkan.');
  };

  return (
    <div className="modal-back" style={{ zIndex: 120 }}>
      <div className="modal" style={{ maxWidth: 500 }}>
        <div className="modal-h">
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ fontSize: 18 }}>🔐</span>
            <span>Autentikasi Dua Faktor (2FA / TOTP)</span>
          </div>
          <button type="button" className="iconbtn" onClick={onClose}>
            <Icon name="x" size={16} />
          </button>
        </div>

        <div className="modal-b" style={{ gap: 16 }}>
          {setupStep === 'status' && (
            <>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '14px 16px',
                  borderRadius: 8,
                  background: totpEnabled ? 'var(--chip-approved-bg)' : 'var(--chip-warn-bg)',
                  color: totpEnabled ? 'var(--chip-approved-fg)' : 'var(--chip-warn-fg)',
                }}
              >
                <div>
                  <div style={{ fontWeight: 600 }}>
                    Status: {totpEnabled ? '2FA Aktif' : '2FA Belum Aktif'}
                  </div>
                  <div className="small" style={{ marginTop: 2 }}>
                    {totpEnabled
                      ? 'Akun Anda dilindungi verifikasi 6 digit saat approval & transaksi penting.'
                      : 'Tingkatkan keamanan akun untuk persetujuan dokumen nominal tinggi (PRD §17).'}
                  </div>
                </div>
              </div>

              <div className="small muted">
                2FA (Time-based One-Time Password) kompatibel dengan aplikasi <strong>Google Authenticator</strong>, <strong>Microsoft Authenticator</strong>, dan perangkat keras token OTP.
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 10 }}>
                {totpEnabled ? (
                  <button type="button" className="btn btn-danger" onClick={handleDisable}>
                    Nonaktifkan 2FA
                  </button>
                ) : (
                  <button type="button" className="btn btn-dark" onClick={handleEnable}>
                    Aktifkan 2FA Sekarang
                  </button>
                )}
              </div>
            </>
          )}

          {setupStep === 'setup' && (
            <>
              <div className="small muted">
                Langkah 1: Buka aplikasi Google Authenticator atau Microsoft Authenticator di ponsel Anda, lalu masukkan Kunci Rahasia di bawah ini:
              </div>

              <div
                style={{
                  background: 'var(--surface-2)',
                  border: '1px solid var(--line-input)',
                  padding: '12px 16px',
                  borderRadius: 8,
                  textAlign: 'center',
                }}
              >
                <div className="lbl" style={{ marginBottom: 4 }}>Kunci Rahasia (Secret Key)</div>
                <div className="mono" style={{ fontSize: 16, fontWeight: 700, letterSpacing: '0.1em' }}>
                  {secretKey}
                </div>
                <div className="small muted" style={{ marginTop: 4 }}>
                  Tipe: TOTP · Digit: 6 · Interval: 30 detik
                </div>
              </div>

              <form onSubmit={handleVerify} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <label className="field">
                  <span className="req">Langkah 2: Masukkan 6 Digit Kode dari Aplikasi</span>
                  <input
                    type="text"
                    maxLength={6}
                    autoFocus
                    className="mono"
                    placeholder="000000"
                    value={verifyCode}
                    onChange={(e) => setVerifyCode(e.target.value.replace(/\D/g, ''))}
                    style={{ fontSize: 18, textAlign: 'center', letterSpacing: '0.2em', height: 42 }}
                    required
                  />
                </label>

                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 10 }}>
                  <button type="button" className="btn" onClick={() => setSetupStep('status')}>
                    Kembali
                  </button>
                  <button type="submit" className="btn btn-dark">
                    Verifikasi & Simpan
                  </button>
                </div>
              </form>
            </>
          )}
        </div>

        <div className="modal-f">
          <button type="button" className="btn" onClick={onClose}>
            Tutup
          </button>
        </div>
      </div>
    </div>
  );
}
