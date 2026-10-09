/** Klien HTTP: token Bearer, plant aktif (header X-Plant), dan error API yang seragam.
 * Dilengkapi Smart Offline Fallback jika backend Spring Boot belum dijalankan.
 */

import {
  MOCK_APPS,
  MOCK_NOTIFICATIONS,
  MOCK_ROLES,
  createMockMe,
  getMockAppDashboard,
  getMockLauncherData,
  getMockUsers,
  saveMockUsers,
} from './mockData';
import {
  MOCK_CUSTOM_PAGES,
  MOCK_LOOKUPS,
  generateMockDocs,
  generateMockDocEnvelope,
  generateMockMaster,
  wrapPage,
} from './mockEngine';

const TOKEN_KEY = 'erp.token';
const PLANT_KEY = 'erp.plant';
const USERNAME_KEY = 'erp.username';

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
let memUsername: string | null = read(USERNAME_KEY) || 'dimas';
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
  get username() {
    return memUsername;
  },
  set username(v: string | null) {
    memUsername = v;
    write(USERNAME_KEY, v);
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
    throw new ApiError(
      res.status,
      b.code ?? String(res.status),
      b.message ?? `Permintaan gagal (${res.status})`,
      b.fields,
    );
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

/**
 * Handler mock cerdas saat backend belum aktif (offline/demo mode).
 */
function handleFallback<T>(method: string, path: string, body?: any): T {
  console.info(`[ERP Mock Fallback] ${method} ${path}`, body);

  // 1. Auth Login
  if (path === '/auth/login') {
    const username = body?.username?.trim() || 'dimas';
    session.username = username;
    const user = createMockMe(username);
    return { token: `demo-token-${Date.now()}`, user } as T;
  }

  // 2. Auth Me
  if (path === '/auth/me') {
    return createMockMe(session.username || 'dimas') as T;
  }

  // 3. Update Preferences
  if (path === '/auth/me/preferences') {
    const me = createMockMe(session.username || 'dimas');
    if (body) {
      me.preferences = { ...me.preferences, ...body };
    }
    return me as T;
  }

  // 4. Reset User Password
  if (path === '/auth/me/password') {
    return { success: true } as T;
  }

  // 5. Meta Apps
  if (path === '/meta/apps' || path === '/meta/apps/all') {
    return MOCK_APPS as T;
  }

  // 6. Meta Doc Types
  if (path === '/meta/doc-types') {
    const docTypes: any[] = [];
    MOCK_APPS.forEach((a) => {
      a.docs.forEach((d) => {
        docTypes.push({
          code: d.prefix,
          name: d.name,
          appCode: a.code,
          menuCode: `${a.code}-02`,
          essMenuCode: 'ESS-01',
          requiresEsign: false,
        });
      });
    });
    return docTypes as T;
  }

  // 7. Launcher Dashboard
  if (path === '/dashboard/launcher') {
    return getMockLauncherData() as T;
  }

  // 8. Module App Dashboard (/dashboard/:app)
  if (path.startsWith('/dashboard/')) {
    const app = path.replace('/dashboard/', '');
    return getMockAppDashboard(app) as T;
  }

  // 9. Notifications
  if (path === '/notifications') {
    return { unread: 2, items: MOCK_NOTIFICATIONS } as T;
  }
  if (path === '/notifications/read-all' || path.includes('/read')) {
    return { success: true } as T;
  }

  // 10. Module Settings (/settings/:app)
  if (path.startsWith('/settings/')) {
    return {
      values: { displayName: 'Herbatech Plant 1' },
      docTypes: [
        { id: 1, code: 'WO', name: 'Work Order', prefix: 'WO', resetPeriod: 'MONTHLY', menuCode: 'PRE-02', requiresEsign: false },
        { id: 2, code: 'PO', name: 'Purchase Order', prefix: 'PO', resetPeriod: 'MONTHLY', menuCode: 'PRC-07', requiresEsign: true },
        { id: 3, code: 'INV', name: 'Sales Invoice', prefix: 'INV', resetPeriod: 'YEARLY', menuCode: 'FIN-10', requiresEsign: false },
      ],
      approvalRules: [
        { id: 1, docTypeCode: 'PO', level: 1, minAmount: 10000000, label: 'Manager Pengadaan', approverType: 'ROLE' },
        { id: 2, docTypeCode: 'PO', level: 2, minAmount: 50000000, label: 'Direktur Operasional', approverType: 'ROLE' },
      ],
      access: [
        { role: 'ADMIN', roleName: 'Admin Sistem', viewScope: 'ALL', actions: ['CREATE', 'APPROVE', 'POST', 'CANCEL', 'RELEASE'] },
        { role: 'MANAGER', roleName: 'Manager Departemen', viewScope: 'DEPARTMENT', actions: ['CREATE', 'APPROVE', 'POST', 'CANCEL'] },
        { role: 'SUPERVISOR', roleName: 'Supervisor', viewScope: 'SECTION', actions: ['CREATE', 'APPROVE'] },
        { role: 'OPERATOR', roleName: 'Operator', viewScope: 'OWN', actions: ['CREATE'] },
      ],
      canEdit: true,
    } as T;
  }

  // 11. Sys Users (Manage Users)
  if (path === '/sys/users') {
    if (method === 'POST') {
      const users = getMockUsers();
      const newUser = {
        id: users.length + 1,
        username: body.username,
        fullName: body.fullName,
        email: body.email || null,
        active: true,
      };
      users.unshift(newUser);
      saveMockUsers(users);
      return newUser as T;
    }
    return getMockUsers() as T;
  }

  if (path.startsWith('/sys/users/')) {
    const parts = path.split('/');
    const userId = Number(parts[3]);
    const sub = parts[4];

    if (sub === 'roles') {
      if (method === 'PUT') return body as T;
      return [{ roleId: 1, appCode: '*', plantId: null }] as T;
    }

    if (sub === 'password') {
      return { success: true } as T;
    }

    if (method === 'PUT') {
      const users = getMockUsers();
      const idx = users.findIndex((u: any) => u.id === userId);
      if (idx !== -1) {
        users[idx] = { ...users[idx], ...body };
        saveMockUsers(users);
        return users[idx] as T;
      }
    }
  }

  // 12. Sys Roles
  if (path === '/sys/roles') {
    return MOCK_ROLES as T;
  }

  // 13. Lookups (/lookup/:name)
  if (path.startsWith('/lookup/')) {
    const name = path.replace('/lookup/', '').split('?')[0];
    return (MOCK_LOOKUPS[name] || MOCK_LOOKUPS.items) as T;
  }

  // 14. Direct match atau clean path di MOCK_CUSTOM_PAGES
  const cleanPath = path.split('?')[0];
  if (MOCK_CUSTOM_PAGES[path]) {
    return MOCK_CUSTOM_PAGES[path] as T;
  }
  if (MOCK_CUSTOM_PAGES[cleanPath]) {
    return MOCK_CUSTOM_PAGES[cleanPath] as T;
  }

  if ((path.startsWith('/documents/') && path.endsWith('/panel')) || path.startsWith('/docs/panel/')) {
    return {
      activity: [
        { id: 1, kind: 'STATUS', ts: '2026-10-09T08:00:00Z', username: 'dimas', fullName: 'Dimas Pratama', toStatus: 'DRAFT', message: 'Dokumen dibuat' },
        { id: 2, kind: 'APPROVAL', ts: '2026-10-09T09:30:00Z', username: 'hendro.pre', fullName: 'Hendro Wijaya', message: 'Disetujui untuk proses produksi bets' },
      ],
      approvals: [
        {
          id: 1,
          level: 1,
          assigneeLabel: 'Supervisor Produksi / QA',
          status: 'APPROVED',
          decidedByName: 'Hendro Wijaya',
          decidedAt: '2026-10-09T09:30:00Z',
          decisionReason: 'Sesuai spesifikasi BMR & validasi lini',
          docAmount: 45000000,
        },
      ],
      signatures: [
        {
          id: 1,
          fullName: 'Apt. Dewi Lestari, S.Farm',
          meaning: 'Pemastian Mutu (QA Release)',
          signedAt: '2026-10-09T09:30:00Z',
          reason: 'Verifikasi kepatuhan CPOB & integritas data',
        },
      ],
      attachments: [
        {
          id: 1,
          filename: 'Lampiran_CoA_Bahan_Baku.pdf',
          sizeBytes: 245000,
          uploadedByName: 'Hendro Wijaya',
          uploadedAt: '2026-10-09T08:15:00Z',
        },
      ],
      related: [
        {
          docType: 'WO',
          docId: 1,
          docNo: 'WO/P1/2610/00001',
          menuCode: 'PRE-02',
          status: 'APPROVED',
          relation: 'Work Order Induk',
        },
      ],
      decidableTaskId: null,
      decisionRequiresEsign: false,
    } as T;
  }

  // 16. Master Resources Handler (/master/:res, /sys/:res, /hc/:res, /fin/:res, dll)
  const MASTER_RESOURCES = new Set([
    'companies', 'plants', 'departments', 'cost-centers', 'users', 'roles',
    'approval-rules', 'doc-types', 'items', 'uoms', 'uom-conversions', 'partners',
    'warehouses', 'locations', 'holidays', 'shifts', 'currencies', 'exchange-rates',
    'tax-codes', 'notification-templates', 'templates',
    'positions', 'employees', 'leave-types', 'leave-entitlements', 'salary-components',
    'payroll-params', 'ptkp', 'pph21-ter', 'qualifications', 'sarmut-kpis', 'contracts',
    'accounts', 'account-mappings', 'bank-accounts', 'asset-categories',
    'lines', 'product-params', 'reject-reasons', 'machines',
    'supplier-profiles', 'asls', 'price-lists', 'boms', 'stock-params',
    'inventory-assets'
  ]);

  const pathParts = cleanPath.split('/').filter(Boolean);
  const resourceCandidate = pathParts[0] === 'master' ? pathParts[1] : pathParts[1];

  if (resourceCandidate && MASTER_RESOURCES.has(resourceCandidate)) {
    const isDetail = pathParts.length >= 3 && !isNaN(Number(pathParts[2]));
    const itemId = isDetail ? Number(pathParts[2]) : undefined;
    const masterItems = generateMockMaster(resourceCandidate);

    if (method === 'GET') {
      if (isDetail && itemId != null) {
        return (masterItems.find((m) => m.id === itemId) || { ...masterItems[0], id: itemId }) as T;
      }
      return wrapPage(masterItems) as T;
    }
    if (method === 'POST') {
      const newItem = { ...(body || {}), id: Date.now() };
      masterItems.unshift(newItem);
      return newItem as T;
    }
    if (method === 'PUT' && itemId != null) {
      const idx = masterItems.findIndex((m) => m.id === itemId);
      if (idx !== -1) masterItems[idx] = { ...masterItems[idx], ...(body || {}) };
      return { ...(body || {}), id: itemId } as T;
    }
    if (method === 'DELETE') {
      return { success: true } as T;
    }
  }

  // 17. Single Document Envelope (/docs/:docType/:id atau /modul/:docType/:id)
  const isDocEnvelope = Boolean(path.match(/\/docs\/[A-Za-z0-9_-]+\/\d+$/) || path.match(/\/[a-z]+\/[A-Za-z0-9_-]+\/\d+$/));
  if (isDocEnvelope) {
    const parts = cleanPath.split('/').filter(Boolean);
    const docType = parts[parts.length - 2] || 'DOC';
    const docId = Number(parts[parts.length - 1]) || 1;

    if (method === 'GET') {
      return generateMockDocEnvelope(docType, docId) as T;
    }
    if (method === 'PUT') {
      return {
        doc: { ...(body || {}), id: docId },
        meta: {
          docNo: body?.docNo || `${docType.toUpperCase()}/P1/2610/${String(docId).padStart(5, '0')}`,
          status: body?.status || 'APPROVED',
          canEdit: true,
          canSubmit: true,
          canApprove: true,
          version: (body?.version || 1) + 1,
        },
      } as T;
    }
  }

  // 18. Document Action endpoints (/modul/:docType/:id/:action)
  if (pathParts.length >= 4 && !isNaN(Number(pathParts[2]))) {
    const docType = pathParts[1];
    const docId = Number(pathParts[2]);
    const action = pathParts[3];
    return {
      success: true,
      action,
      ...generateMockDocEnvelope(docType, docId),
    } as T;
  }

  // 19. Document Create / POST (/docs/:docType atau /modul/:docType)
  if (method === 'POST' && (cleanPath.startsWith('/docs/') || pathParts.length === 2)) {
    const docType = pathParts[pathParts.length - 1] || 'DOC';
    const newId = Math.floor(Math.random() * 90000 + 10000);
    const docNo = `${docType.toUpperCase()}/P1/2610/${newId}`;
    return {
      doc: { ...(body || {}), id: newId, docNo, status: 'DRAFT' },
      meta: {
        docNo,
        status: 'DRAFT',
        canEdit: true,
        canSubmit: true,
        canApprove: true,
        version: 1,
      },
    } as T;
  }

  // 20. Document List (/docs/:docType atau /modul/:docType)
  if (cleanPath.startsWith('/docs/') || pathParts.length === 2) {
    const docType = pathParts[pathParts.length - 1] || 'DOC';
    return wrapPage(generateMockDocs(docType)) as T;
  }

  // Fallback default: empty array or object
  return [] as T;
}

export const api = {
  get: async <T>(path: string, params?: Query): Promise<T> => {
    try {
      const res = await fetch(`/api${path}${qs(params)}`, { headers: headers() });
      if (res.status === 502 || res.status === 504 || res.status === 503) {
        return handleFallback<T>('GET', path);
      }
      return await handle<T>(res);
    } catch {
      return handleFallback<T>('GET', path);
    }
  },

  post: async <T>(path: string, body?: unknown): Promise<T> => {
    try {
      const res = await fetch(`/api${path}`, {
        method: 'POST',
        headers: headers({ 'Content-Type': 'application/json' }),
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      if (res.status === 502 || res.status === 504 || res.status === 503) {
        return handleFallback<T>('POST', path, body);
      }
      return await handle<T>(res);
    } catch {
      return handleFallback<T>('POST', path, body);
    }
  },

  put: async <T>(path: string, body: unknown, params?: Query): Promise<T> => {
    try {
      const res = await fetch(`/api${path}${qs(params)}`, {
        method: 'PUT',
        headers: headers({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(body),
      });
      if (res.status === 502 || res.status === 504 || res.status === 503) {
        return handleFallback<T>('PUT', path, body);
      }
      return await handle<T>(res);
    } catch {
      return handleFallback<T>('PUT', path, body);
    }
  },

  del: async <T>(path: string, params?: Query): Promise<T> => {
    try {
      const res = await fetch(`/api${path}${qs(params)}`, { method: 'DELETE', headers: headers() });
      if (res.status === 502 || res.status === 504 || res.status === 503) {
        return handleFallback<T>('DELETE', path);
      }
      return await handle<T>(res);
    } catch {
      return handleFallback<T>('DELETE', path);
    }
  },

  upload: async <T>(path: string, file: File): Promise<T> => {
    const fd = new FormData();
    fd.append('file', file);
    try {
      const res = await fetch(`/api${path}`, { method: 'POST', headers: headers(), body: fd });
      return await handle<T>(res);
    } catch {
      return { filename: file.name, size: file.size } as T;
    }
  },

  download: async (path: string, filename: string) => {
    try {
      const res = await fetch(`/api${path}`, { headers: headers() });
      if (!res.ok) await handle(res);
      const url = URL.createObjectURL(await res.blob());
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch {
      /* ignore */
    }
  },
};
