import { useTranslation } from 'react-i18next';

import type { CampaignTurnResponse } from '@/contract/campaign';

import styles from './CampaignHeader.module.scss';

interface CampaignHeaderProps {
  turnData: CampaignTurnResponse;
}

const CampaignHeader = ({ turnData }: CampaignHeaderProps) => {
  const { t } = useTranslation();
  const { scene } = turnData;

  return (
    <div className={styles.header}>
      <div className={styles.group}>
        <span className={styles.label}>{t('campaignHeader.turn')}</span>
        <span className={styles.value}>{turnData.currentTurn}</span>
      </div>

      {scene && (
        <div className={styles.right}>
          <div className={styles.group}>
            <span className={styles.label}>{t('campaignHeader.location')}</span>
            <span className={styles.value}>{scene.currentLocation}</span>
          </div>

          <div className={styles.separator} />
          <div className={styles.group}>
            <span className={styles.label}>{t('campaignHeader.date')}</span>
            <span className={styles.value}>
              {scene.gameDate} &mdash; {scene.gameTime}
            </span>
          </div>

          <div className={styles.separator} />
          <div className={styles.group}>
            <span className={styles.label}>{t('campaignHeader.weather')}</span>
            <span className={styles.value}>
              {scene.meteo} {scene.temperature}
            </span>
          </div>
        </div>
      )}
    </div>
  );
};

export default CampaignHeader;
