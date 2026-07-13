import { useEffect } from 'react';

import styles from './Toast.module.scss';

export interface ToastItem {
  id: number;
  message: string;
}

interface ToastProps {
  toasts: ToastItem[];
  onDismiss: (id: number) => void;
}

const DISMISS_MS = 4000;

const Toast = ({ toasts, onDismiss }: ToastProps) => {
  useEffect(() => {
    if (toasts.length === 0) return;

    const latest = toasts.at(-1);
    if (!latest) return;
    const timer = setTimeout(() => {
      onDismiss(latest.id);
    }, DISMISS_MS);
    return () => {
      clearTimeout(timer);
    };
  }, [toasts, onDismiss]);

  if (toasts.length === 0) return null;

  return (
    <div className={styles.container}>
      {toasts.map((t) => (
        <div key={t.id} className={styles.toast}>
          {t.message}
        </div>
      ))}
    </div>
  );
};

export default Toast;
