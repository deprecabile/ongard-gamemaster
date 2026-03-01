import { Link, Outlet } from 'react-router-dom';

import { ROUTES } from '@/routes/routes';

import styles from './PublicLayout.module.scss';

const PublicLayout = () => {
  return (
    <div className={styles.publicLayout}>
      <header className={styles.header}>
        <Link to={ROUTES.HOME} className={styles.logo}>
          Ongard
        </Link>
        <nav className={styles.nav}>
          <Link to={ROUTES.LOGIN}>Login</Link>
          <Link to={ROUTES.REGISTER}>Registrati</Link>
        </nav>
      </header>
      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  );
};

export default PublicLayout;
