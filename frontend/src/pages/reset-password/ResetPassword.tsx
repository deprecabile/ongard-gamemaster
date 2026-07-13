import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import { authService } from '@/api/authService';
import { ROUTES } from '@/routes/routes';

import styles from './ResetPassword.module.scss';

const PSW_MIN_LEN = 8;

const ResetPassword = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(token ? null : 'resetPassword.noToken');
  const [loading, setLoading] = useState(false);

  const passwordValid =
    password.length >= PSW_MIN_LEN && /[a-zA-Z]/.test(password) && /\d/.test(password);
  const passwordsMatch = password === confirmPassword;

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    if (!token || !passwordValid || !passwordsMatch) return;

    setError(null);
    setLoading(true);

    authService
      .resetPassword(token, password)
      .then(() => {
        void navigate(ROUTES.LOGIN, {
          state: { messageKey: 'resetPassword.success' },
        });
      })
      .catch((err: unknown) => {
        interface AxiosErrorShape {
          response?: { status: number; data?: { messages?: { code: string }[] } };
        }
        const axiosErr = err as AxiosErrorShape;
        const code = axiosErr.response?.data?.messages?.[0]?.code;
        if (code === 'AU_400_04') {
          setError('resetPassword.invalidToken');
        } else {
          setError('resetPassword.genericError');
        }
      })
      .finally(() => {
        setLoading(false);
      });
  };

  if (!token) {
    return (
      <div className={styles.resetPage}>
        <div className='auth-card'>
          <div className={styles.error}>{t('resetPassword.noToken')}</div>
          <p className={styles.linkText}>
            <Link to={ROUTES.LOGIN}>{t('resetPassword.loginLink')}</Link>
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.resetPage}>
      <div className='auth-card'>
        <h1>{t('resetPassword.title')}</h1>
        <form className='auth-form' onSubmit={handleSubmit}>
          <div className='form-field'>
            <label htmlFor='password'>{t('resetPassword.newPasswordLabel')}</label>
            <input
              id='password'
              type='password'
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
              }}
              autoComplete='new-password'
              required
            />
            {password.length > 0 && (
              <ul className={styles.passwordRules}>
                <li className={password.length >= PSW_MIN_LEN ? styles.ruleMet : styles.ruleUnmet}>
                  {t('register.passwordRuleMinChars', { min: PSW_MIN_LEN })}
                </li>
                <li className={/[a-zA-Z]/.test(password) ? styles.ruleMet : styles.ruleUnmet}>
                  {t('register.passwordRuleLetter')}
                </li>
                <li className={/\d/.test(password) ? styles.ruleMet : styles.ruleUnmet}>
                  {t('register.passwordRuleNumber')}
                </li>
              </ul>
            )}
          </div>
          <div className='form-field'>
            <label htmlFor='confirmPassword'>{t('resetPassword.confirmPasswordLabel')}</label>
            <input
              id='confirmPassword'
              type='password'
              value={confirmPassword}
              onChange={(e) => {
                setConfirmPassword(e.target.value);
              }}
              autoComplete='new-password'
              required
            />
            {confirmPassword.length > 0 && !passwordsMatch && (
              <span className={styles.fieldError}>{t('resetPassword.passwordsDontMatch')}</span>
            )}
          </div>
          {error != null && <div className='error-banner'>{t(error)}</div>}
          <button
            type='submit'
            className='btn-submit'
            disabled={loading || !passwordValid || !passwordsMatch}
          >
            {loading ? t('resetPassword.resetting') : t('resetPassword.resetButton')}
          </button>
        </form>
        <p className={styles.linkText}>
          <Link to={ROUTES.LOGIN}>{t('resetPassword.loginLink')}</Link>
        </p>
      </div>
    </div>
  );
};

export default ResetPassword;
