import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, session } from '../api/client';
import type { Me, Preferences } from '../api/types';

interface AuthState {
  me: Me | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  setPlant: (plantId: number) => Promise<void>;
  savePreferences: (p: Partial<Preferences>) => Promise<void>;
  refresh: () => Promise<void>;
}

const Ctx = createContext<AuthState | null>(null);

/** Menerapkan tema & kepadatan dari preferensi pengguna (PRD §15.4). */
function applyPreferences(p: Preferences | undefined) {
  const root = document.documentElement;
  const theme = p?.theme ?? 'light';
  const dark = theme === 'dark' || (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  root.dataset.theme = dark ? 'dark' : 'light';
  root.dataset.density = p?.density === 'compact' ? 'compact' : 'comfortable';
  root.lang = p?.locale ?? 'id';
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(Boolean(session.token));
  const qc = useQueryClient();

  const refresh = useCallback(async () => {
    const m = await api.get<Me>('/auth/me');
    if (m.plantId && session.plant !== m.plantId) session.plant = m.plantId;
    setMe(m);
    applyPreferences(m.preferences);
  }, []);

  const logout = useCallback(() => {
    session.token = null;
    setMe(null);
    qc.clear();
  }, [qc]);

  useEffect(() => {
    session.onUnauthorized(() => {
      session.token = null;
      setMe(null);
    });
    if (session.token) {
      refresh()
        .catch(() => logout())
        .finally(() => setLoading(false));
    }
  }, [refresh, logout]);

  const login = useCallback(async (username: string, password: string) => {
    const res = await api.post<{ token: string; user: Me }>('/auth/login', { username, password });
    session.token = res.token;
    const plant = session.plant && res.user.plants.some((p) => p.id === session.plant) ? session.plant : res.user.plantId;
    session.plant = plant;
    await refresh();
  }, [refresh]);

  const setPlant = useCallback(async (plantId: number) => {
    session.plant = plantId;
    qc.clear();
    await refresh();
  }, [qc, refresh]);

  const savePreferences = useCallback(async (p: Partial<Preferences>) => {
    const m = await api.put<Me>('/auth/me/preferences', p);
    setMe(m);
    applyPreferences(m.preferences);
  }, []);

  const value = useMemo(() => ({ me, loading, login, logout, setPlant, savePreferences, refresh }),
    [me, loading, login, logout, setPlant, savePreferences, refresh]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth di luar AuthProvider');
  return v;
}
