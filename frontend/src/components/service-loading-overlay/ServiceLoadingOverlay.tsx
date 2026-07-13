import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';

import { healthService } from '@/api/healthService';
import { useServiceReadyStore } from '@/store/useServiceReadyStore';

import styles from './ServiceLoadingOverlay.module.scss';

const POLL_INTERVAL_MS = 10_000;

const ServiceLoadingOverlay = () => {
  const { t } = useTranslation();
  const serviceUnavailable = useServiceReadyStore((s) => s.serviceUnavailable);
  const markAvailable = useServiceReadyStore((s) => s.markAvailable);

  useEffect(() => {
    if (!serviceUnavailable) return;

    const id = setInterval(() => {
      void healthService.checkRagReady().then((ready) => {
        if (ready) {
          markAvailable();
        }
      });
    }, POLL_INTERVAL_MS);

    return () => {
      clearInterval(id);
    };
  }, [serviceUnavailable, markAvailable]);

  if (!serviceUnavailable) return null;

  return (
    <div className={styles.overlay}>
      <div className={styles.card}>
        <div className={styles.spinner} />
        <h2 className={styles.title}>{t('serviceOverlay.title')}</h2>
        <p className={styles.subtitle}>{t('serviceOverlay.subtitle')}</p>
      </div>
    </div>
  );
};

export default ServiceLoadingOverlay;
