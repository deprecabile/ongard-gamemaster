import { Link } from 'react-router-dom';

import { ROUTES } from '@/routes/routes';

import styles from './Landing.module.scss';

const Landing = () => {
  return (
    <div className={styles.landing}>
      <section className={styles.hero}>
        <h1 className={styles.title}>Ongard Gamemaster</h1>
        <p className={styles.subtitle}>
          Il tuo Game Master guidato dall&apos;Intelligenza Artificiale
        </p>
        <Link to={ROUTES.REGISTER} className={styles.cta}>
          Inizia l&apos;Avventura
        </Link>
      </section>

      <section className={styles.features}>
        <div className={styles.featureCard}>
          <h3>Game Master IA</h3>
          <p>Un narratore intelligente che crea avventure uniche</p>
        </div>
        <div className={styles.featureCard}>
          <h3>Mondo Vivente</h3>
          <p>Un mondo persistente che evolve con le tue scelte</p>
        </div>
        <div className={styles.featureCard}>
          <h3>Storie Epiche</h3>
          <p>Trame ramificate plasmate dalle tue decisioni</p>
        </div>
      </section>

      <section className={styles.ctaSection}>
        <h2>Pronto per iniziare?</h2>
        <div className={styles.ctaLinks}>
          <Link to={ROUTES.LOGIN}>Accedi</Link>
          <Link to={ROUTES.REGISTER} className={styles.ctaPrimary}>
            Registrati
          </Link>
        </div>
      </section>
    </div>
  );
};

export default Landing;
