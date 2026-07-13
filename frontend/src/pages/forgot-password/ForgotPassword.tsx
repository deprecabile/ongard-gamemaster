import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { authService } from '@/api/authService';
import { ROUTES } from '@/routes/routes';

import styles from './ForgotPassword.module.scss';

const ForgotPassword = () => {
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    setLoading(true);

    authService
      .forgotPassword(email)
      .catch(() => {
        /* silent — anti-enumeration */
      })
      .finally(() => {
        setLoading(false);
        setSent(true);
      });
  };

  return (
    <div className={styles.forgotPage}>
      <div className='auth-card'>
        <h1>{t('forgotPassword.title')}</h1>

        {sent ? (
          <>
            <div className={styles.success}>{t('forgotPassword.success')}</div>
            <p className={styles.linkText}>
              <Link to={ROUTES.LOGIN}>{t('forgotPassword.loginLink')}</Link>
            </p>
          </>
        ) : (
          <>
            <p className={styles.description}>{t('forgotPassword.description')}</p>
            <form className='auth-form' onSubmit={handleSubmit}>
              <div className='form-field'>
                <label htmlFor='email'>Email</label>
                <input
                  id='email'
                  type='email'
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                  }}
                  autoComplete='email'
                  required
                />
              </div>
              <button type='submit' className='btn-submit' disabled={loading}>
                {loading ? t('forgotPassword.sending') : t('forgotPassword.sendButton')}
              </button>
            </form>
            <p className={styles.linkText}>
              <Link to={ROUTES.LOGIN}>{t('forgotPassword.loginLink')}</Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
};

export default ForgotPassword;
