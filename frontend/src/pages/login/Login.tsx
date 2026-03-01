import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { ROUTES } from '@/routes/routes';
import { useAuthStore } from '@/store/useAuthStore';

import styles from './Login.module.scss';

const Login = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const login = useAuthStore((s) => s.login);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const successMessage = (location.state as { message?: string } | null)?.message;

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    login(username, password)
      .then(() => {
        void navigate(ROUTES.DASHBOARD);
      })
      .catch(() => {
        setError('Username o password non validi');
      })
      .finally(() => {
        setLoading(false);
      });
  };

  return (
    <div className={styles.loginPage}>
      <div className='auth-card'>
        <h1>Accedi</h1>
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
          {successMessage != null && <div className={styles.success}>{successMessage}</div>}
          {error != null && <div className='error-banner'>{error}</div>}
          <button type='submit' className='btn-submit' disabled={loading}>
            {loading ? 'Accesso in corso...' : 'Accedi'}
          </button>
        </form>
        <p className={styles.linkText}>
          Non hai un account? <Link to={ROUTES.REGISTER}>Registrati</Link>
        </p>
      </div>
    </div>
  );
};

export default Login;
