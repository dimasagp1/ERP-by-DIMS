/** Format lokal Indonesia (PRD §17): Rp, angka id-ID, tanggal DD/MM/YYYY, zona WIB. */

const TZ = 'Asia/Jakarta';
const num = new Intl.NumberFormat('id-ID', { maximumFractionDigits: 2 });
const money = new Intl.NumberFormat('id-ID', { minimumFractionDigits: 0, maximumFractionDigits: 2 });

export function fmtNumber(v: number | string | null | undefined): string {
  if (v === null || v === undefined || v === '') return '';
  return num.format(Number(v));
}

export function fmtRp(v: number | string | null | undefined): string {
  if (v === null || v === undefined || v === '') return '';
  const n = Number(v);
  return (n < 0 ? '−Rp ' : 'Rp ') + money.format(Math.abs(n));
}

/** Nilai ringkas untuk kartu: Rp 64,2 jt / Rp 1,82 M. */
export function fmtRpShort(v: number | null | undefined): string {
  if (v == null) return '';
  const a = Math.abs(v);
  if (a >= 1e9) return `Rp ${(v / 1e9).toLocaleString('id-ID', { maximumFractionDigits: 2 })} M`;
  if (a >= 1e6) return `Rp ${(v / 1e6).toLocaleString('id-ID', { maximumFractionDigits: 1 })} jt`;
  return fmtRp(v);
}

/** "2026-10-09" → "09/10/2026". */
export function fmtDate(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = iso.length <= 10 ? iso : new Date(iso).toLocaleDateString('en-CA', { timeZone: TZ });
  const [y, m, day] = d.split('-');
  return `${day}/${m}/${y}`;
}

export function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Date(iso).toLocaleString('id-ID', {
    timeZone: TZ,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Tanggal hari ini (WIB) dalam format ISO. */
export function todayIso(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: TZ });
}

export function fmtAge(iso: string | null | undefined): string {
  if (!iso) return '';
  const ms = Date.now() - new Date(iso).getTime();
  const days = Math.floor(ms / 86_400_000);
  if (days <= 0) return 'hari ini';
  return `${days} hari`;
}

export function fmtBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

export function longDate(): string {
  return new Date().toLocaleDateString('id-ID', { timeZone: TZ, weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
}

export function greeting(): string {
  const h = Number(new Date().toLocaleString('en-GB', { timeZone: TZ, hour: '2-digit', hour12: false }));
  if (h < 11) return 'Selamat pagi';
  if (h < 15) return 'Selamat siang';
  if (h < 18) return 'Selamat sore';
  return 'Selamat malam';
}
