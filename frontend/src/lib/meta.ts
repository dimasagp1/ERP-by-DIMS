import { useCallback, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { AppDef, DocTypeRef, MenuItem } from '../api/types';

export interface MenuEntry { app: AppDef; group: string; item: MenuItem }

/** Aplikasi & menu yang boleh dilihat pengguna (sudah difilter hak akses di server). */
export function useApps() {
  const q = useQuery({ queryKey: ['meta-apps'], queryFn: () => api.get<AppDef[]>('/meta/apps'), staleTime: 5 * 60_000 });
  const index = useMemo(() => {
    const byApp = new Map<string, AppDef>();
    const byMenu = new Map<string, MenuEntry>();
    q.data?.forEach((a) => {
      byApp.set(a.code, a);
      a.groups.forEach((g) => g.items.forEach((item) => byMenu.set(item.code, { app: a, group: g.name, item })));
    });
    return { byApp, byMenu };
  }, [q.data]);
  return { apps: q.data ?? [], ...index, isLoading: q.isLoading };
}

/** Semua aplikasi (untuk warna & nama departemen di peta hubungan). */
export function useAllApps() {
  const q = useQuery({ queryKey: ['meta-apps-all'], queryFn: () => api.get<AppDef[]>('/meta/apps/all'), staleTime: 30 * 60_000 });
  return useMemo(() => {
    const byApp = new Map<string, AppDef>();
    const byMenu = new Map<string, MenuEntry>();
    q.data?.forEach((a) => {
      byApp.set(a.code, a);
      a.groups.forEach((g) => g.items.forEach((item) => byMenu.set(item.code, { app: a, group: g.name, item })));
    });
    return { byApp, byMenu, apps: q.data ?? [] };
  }, [q.data]);
}

export function useDocTypes() {
  const q = useQuery({ queryKey: ['meta-doc-types'], queryFn: () => api.get<DocTypeRef[]>('/meta/doc-types'), staleTime: 30 * 60_000 });
  return useMemo(() => new Map((q.data ?? []).map((d) => [d.code, d])), [q.data]);
}

export function appOf(menuCode: string): string {
  return menuCode.split('-')[0];
}

export function menuPath(menuCode: string, sub?: string | number): string {
  return `/app/${appOf(menuCode)}/m/${menuCode}${sub !== undefined ? `/${sub}` : ''}`;
}

/**
 * Rute ke satu dokumen: menu pemilik bila dapat diakses, selain itu menu Layanan Saya (ESS) untuk jenis itu,
 * mis. karyawan membuka cutinya sendiri dari notifikasi.
 */
export function useDocLink() {
  const { byMenu } = useApps();
  const types = useDocTypes();
  return useCallback((docType: string, id: number | string, menuCode?: string | null) => {
    const t = types.get(docType);
    const owner = menuCode ?? t?.menuCode;
    const ess = t?.essMenuCode;
    const target = owner && byMenu.has(owner) ? owner : ess && byMenu.has(ess) ? ess : owner;
    return target ? `${menuPath(target, id)}?type=${docType}` : '/';
  }, [byMenu, types]);
}

export const PHASE_LABEL: Record<string, string> = {
  M0: 'Fondasi', M1: 'Fase 1', M2: 'Fase 2', M3: 'Fase 3',
};

// ------------------------------------------------------------------ Terakhir dibuka (per pengguna, lokal)

const RECENT_KEY = 'erp.recent';

export function rememberMenu(code: string) {
  try {
    const list: string[] = JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]');
    localStorage.setItem(RECENT_KEY, JSON.stringify([code, ...list.filter((c) => c !== code)].slice(0, 6)));
  } catch {
    /* abaikan */
  }
}

export function recentMenus(): string[] {
  try {
    return JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]');
  } catch {
    return [];
  }
}
