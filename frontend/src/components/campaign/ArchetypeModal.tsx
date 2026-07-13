import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { campaignSetupService } from '@/api/campaignSetupService';
import type { CampaignArchetype } from '@/contract/campaignArchetype';

import styles from './ArchetypeModal.module.scss';

interface ArchetypeModalProps {
  onClose: () => void;
  onSelect: (archetypeCode: string) => void;
}

const ArchetypeModal = ({ onClose, onSelect }: ArchetypeModalProps) => {
  const { t } = useTranslation();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [archetypes, setArchetypes] = useState<CampaignArchetype[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchArchetypes = () => {
    setLoading(true);
    setError(null);
    campaignSetupService
      .getArchetypes()
      .then((data) => {
        setArchetypes(data);
      })
      .catch(() => {
        setError(t('archetypeModal.loadError'));
      })
      .finally(() => {
        setLoading(false);
      });
  };

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
    fetchArchetypes();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- fetch only on mount
  }, []);

  const handleBackdropClick = (e: React.MouseEvent<HTMLDialogElement>) => {
    if (e.target === dialogRef.current) onClose();
  };

  return (
    // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-noninteractive-element-interactions -- showModal() dialog; Escape handled via cancel event
    <dialog ref={dialogRef} className={styles.dialog} onClick={handleBackdropClick}>
      <div className={styles.modal}>
        <button type='button' className={styles.closeButton} onClick={onClose}>
          &times;
        </button>
        <h2 className={styles.title}>{t('archetypeModal.title')}</h2>

        {loading && (
          <div className={styles.loadingContainer}>
            <svg className={styles.spinner} width='32' height='32' viewBox='0 0 16 16'>
              <circle
                cx='8'
                cy='8'
                r='6'
                fill='none'
                stroke='var(--color-border)'
                strokeWidth='2'
              />
              <path
                d='M8 2a6 6 0 0 1 6 6'
                fill='none'
                stroke='var(--color-accent)'
                strokeWidth='2'
                strokeLinecap='round'
              />
            </svg>
          </div>
        )}

        {error != null && !loading && (
          <div className={styles.errorContainer}>
            <p className={styles.errorText}>{error}</p>
            <button type='button' className={styles.btnRetry} onClick={fetchArchetypes}>
              {t('archetypeModal.retry')}
            </button>
          </div>
        )}

        {!loading && error == null && (
          <>
            <div className={styles.cards}>
              {archetypes.map((archetype) => (
                <div key={archetype.code} className={styles.card}>
                  <p className={styles.cardDescription}>{archetype.description}</p>
                  <button
                    type='button'
                    className={styles.btnSelect}
                    onClick={() => {
                      onSelect(archetype.code);
                    }}
                  >
                    {t('archetypeModal.select')}
                  </button>
                </div>
              ))}
            </div>

            <button
              type='button'
              className={styles.btnRefresh}
              disabled={loading}
              onClick={fetchArchetypes}
            >
              {t('archetypeModal.otherOptions')}
            </button>
          </>
        )}
      </div>
    </dialog>
  );
};

export default ArchetypeModal;
