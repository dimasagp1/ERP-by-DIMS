/** Bentuk data dari backend (lihat controller di backend/src/main/java). */

export type DocStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'POSTED' | 'DONE' | 'REJECTED' | 'CANCELLED';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PlantRef { id: number; code: string; name: string }
export interface GrantRef { role: string; roleName: string; app: string; plantId: number | null }
export interface Preferences {
  locale: 'id' | 'en';
  theme: 'light' | 'dark' | 'system';
  density: 'comfortable' | 'compact';
  startPage: 'launcher' | 'last';
  defaultPlantId: number | null;
  notifyPrefs: string;
}
export interface Me {
  id: number;
  username: string;
  fullName: string;
  email: string | null;
  employeeId: number | null;
  plantId: number | null;
  plants: PlantRef[];
  apps: string[];
  grants: GrantRef[];
  settingsApps: string[];
  preferences: Preferences;
}

export interface MenuItem { code: string; name: string; fn: string; links: string[]; phase: 'M0' | 'M1' | 'M2' | 'M3' }
export interface MenuGroup { name: string; items: MenuItem[] }
export interface AppDef {
  code: string;
  name: string;
  shortName: string;
  color: string;
  self: boolean;
  canSettings?: boolean;
  masters: string[];
  docs: { prefix: string; name: string }[];
  sends: Record<string, string>;
  groups: MenuGroup[];
}
export interface DocTypeRef { code: string; name: string; appCode: string; menuCode: string; essMenuCode: string | null; requiresEsign: boolean }

export interface Kpi { label: string; value: string; note: string; menuCode: string | null }
export interface QueueItem {
  docType: string;
  docId: number;
  docNo: string;
  menuCode: string;
  summary: string | null;
  status: DocStatus;
  info: string;
  amount: number | null;
  since: string;
  taskId: number | null;
}
export interface AppDashboard { app: string; kpis: Kpi[]; kpiAvailable: boolean; queue: QueueItem[] }
export interface LauncherData { approvalCount: number; tasks: QueueItem[]; badges: Record<string, number>; myDocuments: QueueItem[] }

export interface TaskView {
  id: number;
  docType: string;
  docId: number;
  docNo: string;
  docSummary: string | null;
  docAmount: number | null;
  appCode: string;
  menuCode: string | null;
  level: number;
  status: 'WAITING' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  assigneeLabel: string;
  requestedByName: string | null;
  activatedAt: string | null;
  decidedByName: string | null;
  decidedAt: string | null;
  decisionReason: string | null;
  requiresEsign: boolean;
}
export interface ActivityView {
  id: number;
  kind: string;
  fromStatus: string | null;
  toStatus: string | null;
  message: string | null;
  username: string;
  fullName: string | null;
  ts: string;
}
export interface AttachmentView { id: number; filename: string; contentType: string | null; sizeBytes: number; sha256: string; uploadedByName: string | null; uploadedAt: string }
export interface SignatureView { id: number; meaning: string; fullName: string; username: string; reason: string | null; signedAt: string }
export interface RelatedDoc { docType: string; docId: number; docNo: string; menuCode: string; status: string; relation: string }
export interface DocPanel {
  activity: ActivityView[];
  approvals: TaskView[];
  signatures: SignatureView[];
  attachments: AttachmentView[];
  related: RelatedDoc[];
  decidableTaskId: number | null;
  decisionRequiresEsign: boolean;
}

export interface LookupOption { id: number; code: string; name: string; extra: string | null }

export interface NotificationView { id: number; kind: string; title: string; body: string | null; link: string | null; readAt: string | null; createdAt: string }

export interface SearchHit { kind: 'MENU' | 'DOC' | 'ITEM' | 'LOT'; code: string; title: string | null; subtitle: string | null; menuCode: string | null; docType: string | null; id: number | null }
