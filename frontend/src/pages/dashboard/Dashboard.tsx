import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { campaignService } from '@/api/campaignService';
import { ROUTES } from '@/routes/routes';
import { useAuthStore } from '@/store/useAuthStore';

import styles from './Dashboard.module.scss';

const Dashboard = () => {
  const { t } = useTranslation();
  const username = useAuthStore((s) => s.username);
  const [hasCampaigns, setHasCampaigns] = useState(false);

  useEffect(() => {
    void campaignService.hasCampaigns().then(setHasCampaigns);
  }, []);

  return (
    <div className={styles.dashboard}>
      <h1 className={styles.title}>{t('dashboard.greeting', { username })}</h1>
      <p className={styles.subtitle}>{t('dashboard.subtitle')}</p>

      <div className={styles.cards}>
        <div className={styles.card}>
          <h3>{t('dashboard.newCampaign')}</h3>
          <p>{t('dashboard.newCampaignDesc')}</p>
          <Link to={ROUTES.INIT_CAMPAIGN} className={styles.startButton}>
            {t('common.start')}
          </Link>
        </div>
        <div className={styles.card}>
          <h3>{t('dashboard.continueCampaign')}</h3>
          <p>{t('dashboard.continueCampaignDesc')}</p>
          {hasCampaigns ? (
            <Link to={ROUTES.LOAD_CAMPAIGN} className={styles.startButton}>
              {t('common.continue')}
            </Link>
          ) : (
            <span className={styles.disabledButton}>{t('common.continue')}</span>
          )}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
