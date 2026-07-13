import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { authService } from '@/api/authService';
import { useDebounce } from '@/hooks/useDebounce';
import type { LoginResponse } from '@/types/api';

import styles from './GoogleUsernameModal.module.scss';

interface GoogleUsernameModalProps {
  credential: string;
  email: string;
  suggestedUsername: string;
  onClose: () => void;
  onSuccess: (response: LoginResponse) => void;
}

type UsernameStatus = 'idle' | 'checking' | 'available' | 'taken';

const GoogleUsernameModal = ({
  credential,
  email,
  suggestedUsername,
  onClose,
  onSuccess,
}: GoogleUsernameModalProps) => {
  const { t } = useTranslation();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [username, setUsername] = useState(suggestedUsername);
  const [loading, setLoading] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [checkResult, setCheckResult] = useState<{
    username: string;
    available: boolean;
  } | null>(null);

  const debouncedUsername = useDebounce(username, 500);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog && !dialog.open) {
      dialog.showModal();
    }

    const handleCancel = (e: Event) => {
      e.preventDefault();
      onClose();
    };

    dialog?.addEventListener('cancel', handleCancel);
    return () => {
      dialog?.removeEventListener('cancel', handleCancel);
    };
  }, [onClose]);

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
    if (username.length < 3 || usernameStatus === 'taken' || usernameStatus === 'checking') return;

    setServerError(null);
    setLoading(true);

    authService
      .googleRegister(credential, username)
      .then((response) => {
        onSuccess(response);
      })
      .catch((err: unknown) => {
        interface AxiosErrorShape {
          response?: { status: number; data?: { messages?: { code: string }[] } };
        }
        const axiosErr = err as AxiosErrorShape;
        const code = axiosErr.response?.data?.messages?.[0]?.code;
        if (axiosErr.response?.status === 400 && code === 'AU_400_01') {
          setServerError(t('register.usernameTaken'));
        } else {
          setServerError(t('register.registrationFailed'));
        }
      })
      .finally(() => {
        setLoading(false);
      });
  };

  const handleBackdropClick = (e: React.MouseEvent<HTMLDialogElement>) => {
    if (e.target === dialogRef.current) onClose();
  };

  const canSubmit = username.length >= 3 && usernameStatus === 'available' && !loading;

  return (
    // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-noninteractive-element-interactions -- showModal() dialog; Escape handled via cancel event
    <dialog ref={dialogRef} className={styles.dialog} onClick={handleBackdropClick}>
      <div className={styles.modal}>
        <button type='button' className={styles.closeButton} onClick={onClose}>
          &times;
        </button>
        <h2 className={styles.title}>{t('googleModal.title')}</h2>
        <div className={styles.emailInfo}>{t('googleModal.emailInfo', { email })}</div>
        <form className='auth-form' onSubmit={handleSubmit}>
          <div className='form-field'>
            <label htmlFor='google-username'>{t('googleModal.chooseUsername')}</label>
            <input
              id='google-username'
              type='text'
              value={username}
              onChange={(e) => {
                setUsername(e.target.value);
              }}
              autoComplete='username'
              required
            />
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
          {serverError != null && <div className='error-banner'>{serverError}</div>}
          <button type='submit' className='btn-submit' disabled={!canSubmit}>
            {loading ? t('googleModal.completing') : t('googleModal.completeRegistration')}
          </button>
        </form>
      </div>
    </dialog>
  );
};

export default GoogleUsernameModal;
