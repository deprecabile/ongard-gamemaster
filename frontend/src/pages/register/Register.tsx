import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';

import { authService } from '@/api/authService';
import AuthDivider from '@/components/auth/AuthDivider';
import GoogleLoginButton from '@/components/auth/GoogleLoginButton';
import GoogleUsernameModal from '@/components/auth/GoogleUsernameModal';
import { useDebounce } from '@/hooks/useDebounce';
import { ROUTES } from '@/routes/routes';
import { useAuthStore } from '@/store/useAuthStore';
import type { LoginResponse } from '@/types/api';

import styles from './Register.module.scss';

interface FieldErrors {
  username?: string;
  email?: string;
  password?: string;
}

type UsernameStatus = 'idle' | 'checking' | 'available' | 'taken';

const PSW_MIN_LEN = 8;

const validateForm = (username: string, email: string, password: string): FieldErrors => {
  const errors: FieldErrors = {};

  if (username.length < 3) {
    errors.username = 'register.usernameTooShort';
  }

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    errors.email = 'register.emailInvalid';
  }

  if (password.length < PSW_MIN_LEN) {
    errors.password = 'register.passwordRuleMinChars';
  } else if (!/[a-zA-Z]/.test(password) || !/\d/.test(password)) {
    errors.password = 'register.passwordRequirements';
  }

  return errors;
};

const Register = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const loginWithTokens = useAuthStore((s) => s.loginWithTokens);
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [checkResult, setCheckResult] = useState<{
    username: string;
    available: boolean;
  } | null>(null);
  const [googleModal, setGoogleModal] = useState<{
    credential: string;
    email: string;
    suggestedUsername: string;
  } | null>(null);

  const debouncedUsername = useDebounce(username, 500);

  useEffect(() => {
    if (debouncedUsername.length < 3) return;

    const abortController = new AbortController();

    authService
      .checkUsername(debouncedUsername, abortController.signal)
      .then((result) => {
        setCheckResult({ username: debouncedUsername, available: result.available });
      })
      .catch(() => {
        // On abort or network error, leave status as 'checking' until next attempt.
      });

    return () => {
      abortController.abort();
    };
  }, [debouncedUsername]);

  const usernameStatus: UsernameStatus = (() => {
    if (debouncedUsername.length < 3) return 'idle';
    if (checkResult?.username !== debouncedUsername) return 'checking';
    return checkResult.available ? 'available' : 'taken';
  })();

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();

    const errors = validateForm(username, email, password);
    setFieldErrors(errors);

    if (Object.keys(errors).length > 0) {
      return;
    }

    if (usernameStatus === 'taken') {
      return;
    }

    setServerError(null);
    setLoading(true);

    authService
      .register(username, email, password)
      .then(() => {
        void navigate(ROUTES.LOGIN, {
          state: { messageKey: 'register.accountCreated' },
        });
      })
      .catch((err: unknown) => {
        interface AxiosErrorShape {
          response?: { status: number; data?: { messages?: { code: string }[] } };
        }
        const axiosErr = err as AxiosErrorShape;
        if (axiosErr.response?.status === 400) {
          const code = axiosErr.response.data?.messages?.[0]?.code;
          if (code === 'AU_400_01') {
            setServerError(t('register.usernameTaken'));
          } else {
            setServerError(t('register.invalidInput'));
          }
        } else {
          setServerError(t('register.registrationFailed'));
        }
      })
      .finally(() => {
        setLoading(false);
      });
  };

  const handleGoogleSuccess = (credential: string) => {
    setServerError(null);
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
        setServerError(t('auth.googleError'));
      })
      .finally(() => {
        setGoogleLoading(false);
      });
  };

  const handleGoogleError = () => {
    setServerError(t('auth.googleError'));
  };

  const handleModalSuccess = (response: LoginResponse) => {
    loginWithTokens(response);
    void navigate(ROUTES.DASHBOARD);
  };

  return (
    <div className={styles.registerPage}>
      <div className='auth-card'>
        <h1>{t('register.title')}</h1>
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
            {fieldErrors.username != null && (
              <span className={styles.fieldError}>{t(fieldErrors.username)}</span>
            )}
            {usernameStatus === 'checking' && (
              <span className={styles.fieldChecking}>{t('register.checking')}</span>
            )}
            {usernameStatus === 'available' && (
              <span className={styles.fieldSuccess}>{t('register.usernameAvailable')}</span>
            )}
            {usernameStatus === 'taken' && (
              <span className={styles.fieldError}>{t('register.usernameTaken')}</span>
            )}
          </div>
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
            {fieldErrors.email != null && (
              <span className={styles.fieldError}>{t(fieldErrors.email)}</span>
            )}
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
              autoComplete='new-password'
              required
            />
            {fieldErrors.password != null && (
              <span className={styles.fieldError}>
                {t(fieldErrors.password, { min: PSW_MIN_LEN })}
              </span>
            )}
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
          {serverError != null && <div className='error-banner'>{serverError}</div>}
          <button type='submit' className='btn-submit' disabled={loading || googleLoading}>
            {loading ? t('register.creatingAccount') : t('register.createAccount')}
          </button>
        </form>
        <AuthDivider />
        <GoogleLoginButton onCredential={handleGoogleSuccess} onError={handleGoogleError} />
        <p className={styles.linkText}>
          {t('register.hasAccount')} <Link to={ROUTES.LOGIN}>{t('register.loginLink')}</Link>
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

export default Register;
