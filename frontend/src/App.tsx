import { Navigate, Route, Routes, useParams } from 'react-router-dom';
import { Spinner } from './components/ui';
import { menuPath, useApps, useDocLink, useDocTypes } from './lib/meta';
import { AppDashboard } from './pages/AppDashboard';
import { LoginPage } from './pages/LoginPage';
import { MenuPage } from './pages/MenuPage';
import { SettingsPage } from './pages/SettingsPage';
import { AppLayout } from './shell/AppLayout';
import { Launcher } from './shell/Launcher';
import { Shell } from './shell/Shell';

/** Tautan notifikasi /d/{jenis}/{id} → menu pemilik dokumen. */
function DocRedirect() {
  const { type = '', id = '' } = useParams();
  const types = useDocTypes();
  const { isLoading } = useApps();
  const docLink = useDocLink();
  if (types.size === 0 || isLoading) return <Spinner />;
  return <Navigate to={docLink(type, id)} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<Shell />}>
        <Route index element={<Launcher />} />
        <Route path="/approval" element={<Navigate to={menuPath('ESS-10')} replace />} />
        <Route path="/d/:type/:id" element={<DocRedirect />} />
        <Route path="/app/:app" element={<AppLayout />}>
          <Route index element={<AppDashboard />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="m/:menu" element={<MenuPage />} />
          <Route path="m/:menu/:id" element={<MenuPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
