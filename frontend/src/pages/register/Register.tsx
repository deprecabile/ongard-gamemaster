import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { authService } from '@/api/authService';
import { useDebounce } from '@/hooks/useDebounce';
import { ROUTES } from '@/routes/routes';

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
    errors.username = 'Lo username deve avere almeno 3 caratteri';
  }

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    errors.email = 'Inserisci un indirizzo email valido';
  }

  if (password.length < PSW_MIN_LEN) {
    errors.password = `La password deve avere almeno ${String(PSW_MIN_LEN)} caratteri`;
  } else if (!/[a-zA-Z]/.test(password) || !/\d/.test(password)) {
    errors.password = 'La password deve contenere almeno una lettera e un numero';
  }

  return errors;
};

const Register = () => {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [checkResult, setCheckResult] = useState<{
    username: string;
    available: boolean;
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
          state: { message: 'Account creato! Accedi per iniziare la tua avventura.' },
        });
      })
      .catch((err: unknown) => {
        interface AxiosErrorShape {
          response?: { status: number; data?: { message?: string } };
        }
        const axiosErr = err as AxiosErrorShape;
        if (axiosErr.response?.status === 409) {
          setServerError(axiosErr.response.data?.message ?? 'Username o email già in uso');
        } else {
          setServerError('Registrazione fallita. Riprova.');
        }
      })
      .finally(() => {
        setLoading(false);
      });
  };

  return (
    <div className={styles.registerPage}>
      <div className='auth-card'>
        <h1>Registrati</h1>
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
              <span className={styles.fieldError}>{fieldErrors.username}</span>
            )}
            {usernameStatus === 'checking' && (
              <span className={styles.fieldChecking}>Verifica in corso...</span>
            )}
            {usernameStatus === 'available' && (
              <span className={styles.fieldSuccess}>Username disponibile</span>
            )}
            {usernameStatus === 'taken' && (
              <span className={styles.fieldError}>Username già in uso</span>
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
              <span className={styles.fieldError}>{fieldErrors.email}</span>
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
              <span className={styles.fieldError}>{fieldErrors.password}</span>
            )}
            {password.length > 0 && (
              <ul className={styles.passwordRules}>
                <li className={password.length >= PSW_MIN_LEN ? styles.ruleMet : styles.ruleUnmet}>
                  Almeno {PSW_MIN_LEN} caratteri
                </li>
                <li className={/[a-zA-Z]/.test(password) ? styles.ruleMet : styles.ruleUnmet}>
                  Almeno una lettera
                </li>
                <li className={/\d/.test(password) ? styles.ruleMet : styles.ruleUnmet}>
                  Almeno un numero
                </li>
              </ul>
            )}
          </div>
          {serverError != null && <div className='error-banner'>{serverError}</div>}
          <button type='submit' className='btn-submit' disabled={loading}>
            {loading ? 'Creazione account...' : 'Crea Account'}
          </button>
        </form>
        <p className={styles.linkText}>
          Hai già un account? <Link to={ROUTES.LOGIN}>Accedi</Link>
        </p>
      </div>
    </div>
  );
};

export default Register;
