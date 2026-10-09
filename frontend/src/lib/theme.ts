import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Me } from '../api/types';

export type ThemeMode = 'light' | 'dark';

const THEME_KEY = 'erp.theme';

/** Mendapatkan tema awal dari localStorage atau preferensi sistem */
export function getInitialTheme(): ThemeMode {
  try {
    const saved = localStorage.getItem(THEME_KEY);
    if (saved === 'dark' || saved === 'light') return saved;
  } catch {
    /* ignore */
  }
  if (typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    return 'dark';
  }
  return 'light';
}

/** Terapkan tema ke document root dataset */
export function applyTheme(theme: ThemeMode) {
  if (typeof document === 'undefined') return;
  document.documentElement.dataset.theme = theme;
  try {
    localStorage.setItem(THEME_KEY, theme);
  } catch {
    /* ignore */
  }
}

/** Hook untuk membaca & mengubah tema Gelap / Terang */
export function useTheme() {
  const [theme, setThemeState] = useState<ThemeMode>(() => {
    if (typeof document !== 'undefined' && document.documentElement.dataset.theme) {
      return (document.documentElement.dataset.theme as ThemeMode) || 'light';
    }
    return getInitialTheme();
  });

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  const toggleTheme = () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    setThemeState(next);
    applyTheme(next);
    // Simpan ke preferensi pengguna bila sedang login (non-blocking)
    api.put<Me>('/auth/me/preferences', { theme: next }).catch(() => {
      /* ignore bila belum login */
    });
  };

  const setTheme = (next: ThemeMode) => {
    setThemeState(next);
    applyTheme(next);
    api.put<Me>('/auth/me/preferences', { theme: next }).catch(() => {
      /* ignore */
    });
  };

  return { theme, isDark: theme === 'dark', toggleTheme, setTheme };
}
