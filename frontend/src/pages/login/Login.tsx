import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { authService } from '@/api/authService';
import AuthDivider from '@/components/auth/AuthDivider';
import GoogleLoginButton from '@/components/auth/GoogleLoginButton';
import GoogleUsernameModal from '@/components/auth/GoogleUsernameModal';
import { ROUTES } from '@/routes/routes';
import { useAuthStore } from '@/store/useAuthStore';
import type { LoginResponse } from '@/types/api';

import styles from './Login.module.scss';

const Login = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const login = useAuthStore((s) => s.login);
  const loginWithTokens = useAuthStore((s) => s.loginWithTokens);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [notConfirmed, setNotConfirmed] = useState(false);
  const [resending, setResending] = useState(false);
  const [resendDone, setResendDone] = useState(false);
  const [googleModal, setGoogleModal] = useState<{
    credential: string;
    email: string;
    suggestedUsername: string;
  } | null>(null);
  const messageKey = (location.state as { messageKey?: string } | null)?.messageKey;

  const handleResend = () => {
    setResending(true);
    authService
      .resendConfirmation(username)
      .then(() => {
        setResendDone(true);
      })
      .catch(() => {
        /* silent — anti-enumeration */
      })
      .finally(() => {
        setResending(false);
      });
  };

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    setError(null);
    setNotConfirmed(false);
    setResendDone(false);
    setLoading(true);

    login(username, password)
      .then(() => {
        void navigate(ROUTES.DASHBOARD);
      })
      .catch((err: unknown) => {
        interface AxiosErrorShape {
          response?: { status: number; data?: { messages?: { code: string }[] } };
        }
        const axiosErr = err as AxiosErrorShape;
        const code = axiosErr.response?.data?.messages?.[0]?.code;
        if (axiosErr.response?.status === 403 && code === 'AU_403_01') {
          setNotConfirmed(true);
        } else {
          setError('login.invalidCredentials');
        }
      })
      .finally(() => {
        setLoading(false);
      });
  };

  const handleGoogleSuccess = (credential: string) => {
    setError(null);
    setGoogleLoading(true);

    authService
      .googleLogin(credential)
      .then((response) => {
        if ('accessToken' in response) {
          loginWithTokens(response);
          void navigate(ROUTES.DASHBOARD);
        } else {
          setGoogleModal({
            credential,
            email: response.email,
            suggestedUsername: response.suggestedUsername,
          });
        }
      })
      .catch(() => {
        setError('auth.googleError');
      })
      .finally(() => {
        setGoogleLoading(false);
      });
  };

  const handleGoogleError = () => {
    setError('auth.googleError');
  };

  const handleModalSuccess = (response: LoginResponse) => {
    loginWithTokens(response);
    void navigate(ROUTES.DASHBOARD);
  };

  return (
    <div className={styles.loginPage}>
      <div className='auth-card'>
        <h1>{t('login.title')}</h1>
        <form className='auth-form' onSubmit={handleSubmit}>
          <div className='form-field'>
            <label htmlFor='username'>Username</label>
            <input
              id='username'
              type='text'
              value={username}
              onChange={(e) => {
                setUsername(e.target.value);
              }}
              autoComplete='username'
              required
            />
          </div>
          <div className='form-field'>
            <label htmlFor='password'>Password</label>
            <input
              id='password'
              type='password'
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
              }}
              autoComplete='current-password'
              required
            />
          </div>
          <div className={styles.forgotLink}>
            <Link to={ROUTES.FORGOT_PASSWORD}>{t('login.forgotPassword')}</Link>
          </div>
          {messageKey != null && <div className={styles.success}>{t(messageKey)}</div>}
          {notConfirmed && (
            <div className={styles.notConfirmed}>
              {resendDone ? (
                <span className={styles.resendSuccess}>{t('login.resendSuccess')}</span>
              ) : (
                <>
                  <span>{t('login.accountNotConfirmed')}</span>
                  <button
                    type='button'
                    onClick={handleResend}
                    disabled={resending}
                    className={styles.resendLink}
                  >
                    {resending ? t('login.resendSending') : t('login.resendLink')}
                  </button>
                </>
              )}
            </div>
          )}
          {error != null && <div className='error-banner'>{t(error)}</div>}
          <button type='submit' className='btn-submit' disabled={loading || googleLoading}>
            {loading ? t('login.loggingIn') : t('login.title')}
          </button>
        </form>
        <AuthDivider />
        <GoogleLoginButton onCredential={handleGoogleSuccess} onError={handleGoogleError} />
        <p className={styles.linkText}>
          {t('login.noAccount')} <Link to={ROUTES.REGISTER}>{t('login.registerLink')}</Link>
        </p>
      </div>
      {googleModal && (
        <GoogleUsernameModal
          credential={googleModal.credential}
          email={googleModal.email}
          suggestedUsername={googleModal.suggestedUsername}
          onClose={() => {
            setGoogleModal(null);
          }}
          onSuccess={handleModalSuccess}
        />
      )}
    </div>
  );
};

export default Login;
