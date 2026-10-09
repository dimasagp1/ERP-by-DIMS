import { createContext, useCallback, useContext, useState } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Spinner } from '../components/ui';
import { CommandPalette, useGlobalShortcuts } from './CommandPalette';

const PaletteCtx = createContext<() => void>(() => {});

export function useOpenPalette() {
  return useContext(PaletteCtx);
}

/** Pembungkus semua halaman setelah login: guard sesi, pintasan global, palet Ctrl+K. */
export function Shell() {
  const { me, loading } = useAuth();
  const [palette, setPalette] = useState(false);
  const open = useCallback(() => setPalette(true), []);
  const loc = useLocation();
  useGlobalShortcuts(open);
  if (loading) return <Spinner />;
  if (!me) return <Navigate to="/login" replace state={{ from: loc.pathname + loc.search }} />;
  return (
    <PaletteCtx.Provider value={open}>
      <Outlet />
      {palette && <CommandPalette onClose={() => setPalette(false)} />}
    </PaletteCtx.Provider>
  );
}
