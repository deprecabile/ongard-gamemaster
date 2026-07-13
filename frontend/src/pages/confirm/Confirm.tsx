import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';

import { authService } from '@/api/authService';
import { ROUTES } from '@/routes/routes';

import styles from './Confirm.module.scss';

type ConfirmState = 'loading' | 'success' | 'error';

const Confirm = () => {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const [state, setState] = useState<ConfirmState>(token ? 'loading' : 'error');
  const [noToken] = useState(!token);

  useEffect(() => {
    if (!token) return;

    authService
      .confirmEmail(token)
      .then(() => {
        setState('success');
      })
      .catch(() => {
        setState('error');
      });
  }, [token]);

  return (
    <div className={styles.confirmPage}>
      <div className='auth-card'>
        {state === 'loading' && <p>{t('confirm.verifying')}</p>}

        {state === 'success' && <div className={styles.success}>{t('confirm.success')}</div>}

        {state === 'error' && (
          <div className={styles.error}>{noToken ? t('confirm.noToken') : t('confirm.error')}</div>
        )}

        {state !== 'loading' && (
          <p className={styles.linkText}>
            <Link to={ROUTES.LOGIN}>{t('confirm.loginLink')}</Link>
          </p>
        )}
      </div>
    </div>
  );
};

export default Confirm;
