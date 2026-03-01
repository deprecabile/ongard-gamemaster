import { NavLink, Outlet, useNavigate } from 'react-router-dom';

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
      <aside className={styles.sidebar}>
        <div className={styles.logo}>Ongard</div>

        <div className={styles.userSection}>
          <div className={styles.userName}>{username}</div>
          <div className={styles.userRole}>Avventuriero</div>
        </div>

        <nav className={styles.nav}>
          <NavLink
            to={ROUTES.DASHBOARD}
            className={({ isActive }) => (isActive ? styles.active : '')}
          >
            Dashboard
          </NavLink>
        </nav>

        <button type='button' className={styles.logoutButton} onClick={handleLogout}>
          Logout
        </button>
      </aside>
      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  );
};

export default MainLayout;
