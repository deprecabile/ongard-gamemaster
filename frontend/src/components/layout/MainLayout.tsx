import { Link, Outlet, useNavigate } from 'react-router-dom';

import UserMenu from '@/components/user-menu/UserMenu';
import { ROUTES } from '@/routes/routes';
import { useAuthStore } from '@/store/useAuthStore';

import styles from './MainLayout.module.scss';

const MainLayout = () => {
  const username = useAuthStore((s) => s.username);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    void navigate(ROUTES.HOME);
  };

  return (
    <div className={styles.mainLayout}>
      <header className={styles.topbar}>
        <Link to={ROUTES.DASHBOARD} className={styles.logo}>
          Ongard
        </Link>
        <UserMenu username={username ?? ''} onLogout={handleLogout} />
      </header>
      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  );
};

export default MainLayout;
