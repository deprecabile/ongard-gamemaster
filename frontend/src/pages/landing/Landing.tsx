import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { ROUTES } from '@/routes/routes';

import styles from './Landing.module.scss';

const Landing = () => {
  const { t } = useTranslation();

  return (
    <div className={styles.landing}>
      <section className={styles.hero}>
        <h1 className={styles.title}>Ondgard Gamemaster</h1>
        <p className={styles.subtitle}>{t('landing.subtitle')}</p>
        <Link to={ROUTES.REGISTER} className={styles.cta}>
          {t('landing.cta')}
        </Link>
      </section>

      <section className={styles.features}>
        <div className={styles.featureCard}>
          <h3>{t('landing.featureGmTitle')}</h3>
          <p>{t('landing.featureGmDesc')}</p>
        </div>
        <div className={styles.featureCard}>
          <h3>{t('landing.featureWorldTitle')}</h3>
          <p>{t('landing.featureWorldDesc')}</p>
        </div>
        <div className={styles.featureCard}>
          <h3>{t('landing.featureStoryTitle')}</h3>
          <p>{t('landing.featureStoryDesc')}</p>
        </div>
      </section>

      <section className={styles.ctaSection}>
        <h2>{t('landing.readyTitle')}</h2>
        <div className={styles.ctaLinks}>
          <Link to={ROUTES.LOGIN}>{t('common.login')}</Link>
          <Link to={ROUTES.REGISTER} className={styles.ctaPrimary}>
            {t('common.register')}
          </Link>
        </div>
      </section>
    </div>
  );
};

export default Landing;
