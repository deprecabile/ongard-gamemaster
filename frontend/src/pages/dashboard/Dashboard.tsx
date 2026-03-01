import { Link } from 'react-router-dom';

import { ROUTES } from '@/routes/routes';
import { useAuthStore } from '@/store/useAuthStore';

import styles from './Dashboard.module.scss';

const Dashboard = () => {
  const username = useAuthStore((s) => s.username);

  return (
    <div className={styles.dashboard}>
      <h1 className={styles.title}>Benvenuto, {username}</h1>
      <p className={styles.subtitle}>Il Game Master sta preparando la tua avventura...</p>

      <div className={styles.cards}>
        <div className={styles.card}>
          <h3>Nuova Campagna</h3>
          <p>Inizia una nuova avventura epica</p>
          <Link to={ROUTES.INIT_CAMPAIGN} className={styles.startButton}>
            Inizia
          </Link>
        </div>
        <div className={styles.card}>
          <h3>Continua Avventura</h3>
          <p>Riprendi da dove avevi lasciato</p>
          <span className={styles.comingSoon}>Prossimamente</span>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
