import { useTranslation } from 'react-i18next';

import styles from './AuthDivider.module.scss';

const AuthDivider = () => {
  const { t } = useTranslation();
  return (
    <div className={styles.divider}>
      <span className={styles.line} />
      <span className={styles.text}>{t('auth.or')}</span>
      <span className={styles.line} />
    </div>
  );
};

export default AuthDivider;
