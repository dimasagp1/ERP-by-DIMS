import rawApps from '../meta/apps.json';
import type { AppDef, LauncherData, Me, NotificationView, AppDashboard } from './types';

// Normalisasi apps dari apps.json (mapping 'short' -> 'shortName')
export const MOCK_APPS: AppDef[] = (rawApps as any[]).map((a) => ({
  code: a.code,
  name: a.name,
  shortName: a.short || a.name,
  color: a.color || '#4F46E5',
  self: Boolean(a.self),
  canSettings: true,
  masters: a.masters || [],
  docs: a.docs || [],
  sends: a.sends || {},
  groups: a.groups || [],
}));

export const DEFAULT_PLANTS = [
  { id: 1, code: 'P1', name: 'Plant 1 Cikarang' },
  { id: 2, code: 'P2', name: 'Plant 2 Semarang' },
];

export const MOCK_ROLES = [
  { id: 1, code: 'ADMIN', name: 'Administrator Sistem', appCode: 'SYS' },
  { id: 2, code: 'DIRECTOR', name: 'Direktur', appCode: '*' },
  { id: 3, code: 'MANAGER', name: 'Manager Departemen', appCode: '*' },
  { id: 4, code: 'SUPERVISOR', name: 'Supervisor', appCode: '*' },
  { id: 5, code: 'OPERATOR', name: 'Operator & Staf', appCode: '*' },
  { id: 6, code: 'AUDITOR', name: 'Auditor', appCode: '*' },
];

const INITIAL_USERS = [
  { id: 1, username: 'dimas', fullName: 'Dimas Pratama', email: 'dimas@herbatech.co.id', active: true },
  { id: 2, username: 'admin', fullName: 'Administrator Sistem', email: 'admin@herbatech.co.id', active: true },
  { id: 3, username: 'scm.manager', fullName: 'Budi Santoso', email: 'budi.scm@herbatech.co.id', active: true },
  { id: 4, username: 'pre.manager', fullName: 'Hendro Wijaya', email: 'hendro.pre@herbatech.co.id', active: true },
  { id: 5, username: 'fin.manager', fullName: 'Sri Wahyuni', email: 'sri.fin@herbatech.co.id', active: true },
  { id: 6, username: 'qa.manager', fullName: 'Dewi Lestari, Apt.', email: 'dewi.qa@herbatech.co.id', active: true },
];

const USERS_STORAGE_KEY = 'erp.mock.users';

export function getMockUsers() {
  try {
    const raw = localStorage.getItem(USERS_STORAGE_KEY);
    if (raw) return JSON.parse(raw);
  } catch {
    /* ignore */
  }
  return INITIAL_USERS;
}

export function saveMockUsers(users: any[]) {
  try {
    localStorage.setItem(USERS_STORAGE_KEY, JSON.stringify(users));
  } catch {
    /* ignore */
  }
}

export function createMockMe(username: string): Me {
  const users = getMockUsers();
  const existing = users.find((u: any) => u.username.toLowerCase() === username.toLowerCase());
  const fullName = existing ? existing.fullName : username.charAt(0).toUpperCase() + username.slice(1);

  return {
    id: existing ? existing.id : 1,
    username: username.toLowerCase(),
    fullName,
    email: existing?.email ?? `${username}@herbatech.co.id`,
    employeeId: 1,
    plantId: 1,
    plants: DEFAULT_PLANTS,
    apps: MOCK_APPS.map((a) => a.code),
    grants: [
      { role: 'ADMIN', roleName: 'Admin Sistem', app: '*', plantId: null },
      { role: 'MANAGER', roleName: 'Manager Operasional', app: '*', plantId: null },
    ],
    settingsApps: MOCK_APPS.map((a) => a.code),
    preferences: {
      locale: 'id',
      theme: (localStorage.getItem('erp.theme') as any) || 'light',
      density: 'comfortable',
      startPage: 'launcher',
      defaultPlantId: 1,
      notifyPrefs: 'approval,deadline,rejected,digest',
    },
  };
}

export function getMockLauncherData(): LauncherData {
  return {
    approvalCount: 3,
    badges: {
      SCM: 5,
      PRE: 4,
      FIN: 6,
      PRC: 3,
      QMS: 4,
      HC: 2,
      GA: 3,
      RND: 1,
      ESS: 1,
      SYS: 0,
    },
    tasks: [
      {
        docType: 'PO',
        docId: 101,
        docNo: 'PO/P1/2610/00021',
        menuCode: 'PRC-07',
        summary: 'Pemesanan Bahan Baku Ekstrak Temulawak Standar',
        status: 'SUBMITTED',
        since: '2026-10-09T08:30:00Z',
        info: 'Menunggu persetujuan Manager Pengadaan',
        amount: 45000000,
        taskId: 1,
      },
      {
        docType: 'WO',
        docId: 204,
        docNo: 'WO/P1/2610/00052',
        menuCode: 'PRE-02',
        summary: 'Produksi Kapsul Curcuma 500mg Batch CR-2610-A',
        status: 'APPROVED',
        since: '2026-10-09T09:15:00Z',
        info: 'Jadwal penimbangan shift 1',
        amount: null,
        taskId: 2,
      },
      {
        docType: 'GR',
        docId: 308,
        docNo: 'GR/P1/2610/00084',
        menuCode: 'SCM-20',
        summary: 'Penerimaan Simplisia Meniran 250 kg PT Agro Herbal',
        status: 'SUBMITTED',
        since: '2026-10-09T10:00:00Z',
        info: 'Karantina menunggu sampling QC',
        amount: 18500000,
        taskId: 3,
      },
    ],
    myDocuments: [
      {
        docType: 'PR',
        docId: 12,
        docNo: 'PR/P1/2610/00003',
        menuCode: 'PRC-02',
        summary: 'Pengajuan Pengadaan Botol HDPE 100ml',
        status: 'SUBMITTED',
        since: '2026-10-09T07:45:00Z',
        info: 'Diajukan',
        amount: 8500000,
        taskId: null,
      },
      {
        docType: 'LEAVE',
        docId: 44,
        docNo: 'CUTI/2610/00015',
        menuCode: 'ESS-01',
        summary: 'Pengajuan Cuti Tahunan 2 Hari',
        status: 'APPROVED',
        since: '2026-10-08T14:20:00Z',
        info: 'Disetujui atasan',
        amount: null,
        taskId: null,
      },
    ],
  };
}

export function getMockAppDashboard(app: string): AppDashboard {
  return {
    app,
    kpiAvailable: true,
    kpis: [
      { label: 'Efisiensi Operasional', value: '98.4%', note: 'Target ≥ 95%', menuCode: null },
      { label: 'Transaksi Bulan Ini', value: '142 Dok', note: '+12% vs bulan lalu', menuCode: null },
      { label: 'Antrean Tindakan', value: '4 Perlu Diproses', note: 'Prioritas hari ini', menuCode: null },
      { label: 'SLA Penyelesaian', value: '4.2 Jam', note: 'Standar < 24 Jam', menuCode: null },
    ],
    queue: [
      {
        docType: `${app}-DOC`,
        docId: 501,
        docNo: `${app}/P1/2610/0001`,
        menuCode: `${app}-02`,
        summary: `Transaksi Utama Operasional ${app}`,
        status: 'SUBMITTED',
        amount: 25000000,
        since: '2026-10-09T08:00:00Z',
        info: 'Perlu validasi manajerial',
        taskId: 10,
      },
      {
        docType: `${app}-DOC`,
        docId: 502,
        docNo: `${app}/P1/2610/0002`,
        menuCode: `${app}-04`,
        summary: `Pemeriksaan & Verifikasi Dokumen ${app}`,
        status: 'APPROVED',
        amount: 8750000,
        since: '2026-10-09T09:30:00Z',
        info: 'Siap untuk posting',
        taskId: null,
      },
    ],
  };
}

export const MOCK_NOTIFICATIONS: NotificationView[] = [
  {
    id: 1,
    kind: 'TASK',
    title: 'Work Order Disetujui',
    body: 'WO Produksi Ekstrak Curcuma Batch CR-2610-A telah disetujui.',
    createdAt: new Date().toISOString(),
    readAt: null,
    link: '/app/PRE/m/PRE-02',
  },
  {
    id: 2,
    kind: 'INFO',
    title: 'Penerimaan Bahan Baku Baru',
    body: 'Simplisia Meniran 250 kg telah tiba di Gudang Bahan Baku.',
    createdAt: new Date(Date.now() - 3600000).toISOString(),
    readAt: null,
    link: '/app/SCM/m/SCM-20',
  },
  {
    id: 3,
    kind: 'TASK',
    title: 'Permintaan Approval Dokumen',
    body: 'Faktur Penjualan INV/2610/00042 menunggu validasi keuangan.',
    createdAt: new Date(Date.now() - 7200000).toISOString(),
    readAt: new Date().toISOString(),
    link: '/app/FIN/m/FIN-10',
  },
];
