import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';

import { campaignService } from '@/api/campaignService';
import LanguageSelector from '@/components/language-selector/LanguageSelector';
import ServiceLoadingOverlay from '@/components/service-loading-overlay/ServiceLoadingOverlay';
import UserMenu from '@/components/user-menu/UserMenu';
import { ROUTES } from '@/routes/routes';
import { useAuthStore } from '@/store/useAuthStore';

import styles from './MainLayout.module.scss';

const MainLayout = () => {
  const username = useAuthStore((s) => s.username);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    const match = /\/campaign\/(.+)/.exec(location.pathname);
    if (match?.[1]) {
      void campaignService.endSession(match[1]).catch(() => undefined);
    }
    logout();
    void navigate(ROUTES.HOME);
  };

  return (
    <div className={styles.mainLayout}>
      <ServiceLoadingOverlay />
      <header className={styles.topbar}>
        <Link to={ROUTES.DASHBOARD} className={styles.logo}>
          Ondgard
        </Link>
        <div className={styles.topbarRight}>
          <LanguageSelector />
          <UserMenu username={username ?? ''} onLogout={handleLogout} />
        </div>
      </header>
      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  );
};

export default MainLayout;
