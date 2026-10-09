/** Klien HTTP: token Bearer, plant aktif (header X-Plant), dan error API yang seragam. */

const TOKEN_KEY = 'erp.token';
const PLANT_KEY = 'erp.plant';

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fields?: Record<string, string> | null,
  ) {
    super(message);
  }
}

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null) {
  try {
    if (value == null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    /* penyimpanan tidak tersedia: sesi hanya di memori */
  }
}

let memToken: string | null = read(TOKEN_KEY);
let memPlant: string | null = read(PLANT_KEY);
let onUnauthorized: (() => void) | null = null;

export const session = {
  get token() {
    return memToken;
  },
  set token(v: string | null) {
    memToken = v;
    write(TOKEN_KEY, v);
  },
  get plant() {
    return memPlant ? Number(memPlant) : null;
  },
  set plant(v: number | null) {
    memPlant = v == null ? null : String(v);
    write(PLANT_KEY, memPlant);
  },
  onUnauthorized(cb: () => void) {
    onUnauthorized = cb;
  },
};

function headers(extra?: HeadersInit): Headers {
  const h = new Headers(extra);
  if (memToken) h.set('Authorization', `Bearer ${memToken}`);
  if (memPlant) h.set('X-Plant', memPlant);
  return h;
}

async function handle<T>(res: Response): Promise<T> {
  if (res.status === 401) {
    onUnauthorized?.();
    throw new ApiError(401, 'UNAUTHORIZED', 'Sesi berakhir, silakan login ulang');
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const body = text ? safeJson(text) : null;
  if (!res.ok) {
    const b = (body ?? {}) as { code?: string; message?: string; fields?: Record<string, string> };
    throw new ApiError(res.status, b.code ?? String(res.status), b.message ?? `Permintaan gagal (${res.status})`, b.fields);
  }
  return body as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export type Query = Record<string, string | number | boolean | null | undefined>;

export function qs(params?: Query): string {
  if (!params) return '';
  const u = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') u.set(k, String(v));
  });
  const s = u.toString();
  return s ? `?${s}` : '';
}

export const api = {
  get: <T>(path: string, params?: Query) => fetch(`/api${path}${qs(params)}`, { headers: headers() }).then((r) => handle<T>(r)),
  post: <T>(path: string, body?: unknown) =>
    fetch(`/api${path}`, {
      method: 'POST',
      headers: headers({ 'Content-Type': 'application/json' }),
      body: body === undefined ? undefined : JSON.stringify(body),
    }).then((r) => handle<T>(r)),
  put: <T>(path: string, body: unknown, params?: Query) =>
    fetch(`/api${path}${qs(params)}`, {
      method: 'PUT',
      headers: headers({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(body),
    }).then((r) => handle<T>(r)),
  del: <T>(path: string, params?: Query) =>
    fetch(`/api${path}${qs(params)}`, { method: 'DELETE', headers: headers() }).then((r) => handle<T>(r)),
  upload: <T>(path: string, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return fetch(`/api${path}`, { method: 'POST', headers: headers(), body: fd }).then((r) => handle<T>(r));
  },
  /** Unduh file terautentikasi (lampiran) lewat blob. */
  download: async (path: string, filename: string) => {
    const res = await fetch(`/api${path}`, { headers: headers() });
    if (!res.ok) await handle(res);
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
};
