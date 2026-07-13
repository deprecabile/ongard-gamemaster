import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { accountService } from '@/api/accountService';
import { characterService } from '@/api/characterService';
import type { PlayerCharacter } from '@/contract/playerCharacter';
import type { TokenUsageOverview } from '@/contract/tokenUsage';
import { calcUsagePercentage, formatTokens, getUsageLevel } from '@/utils/formatTokens';

import styles from './Account.module.scss';

interface FieldErrors {
  limitMonth?: string;
  limitTotal?: string;
}

const validateLimits = (limitMonth: number, limitTotal: number): FieldErrors => {
  const errors: FieldErrors = {};
  if (limitMonth <= 0) errors.limitMonth = 'account.monthlyLimitMustBePositive';
  if (limitTotal <= 0) errors.limitTotal = 'account.totalLimitMustBePositive';
  if (limitMonth > 0 && limitTotal > 0 && limitMonth > limitTotal) {
    errors.limitMonth = 'account.monthlyCannotExceedTotal';
  }
  return errors;
};

const Account = () => {
  const { t } = useTranslation();
  const [overview, setOverview] = useState<TokenUsageOverview | null>(null);
  const [characters, setCharacters] = useState<PlayerCharacter[]>([]);
  const [pageLoading, setPageLoading] = useState(true);

  const [limitMonth, setLimitMonth] = useState('');
  const [limitTotal, setLimitTotal] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void Promise.all([accountService.fetchTokenUsage(), characterService.getAll()])
      .then(([tokenData, chars]) => {
        setOverview(tokenData);
        setCharacters(chars);
        if (tokenData.limits) {
          setLimitMonth(String(tokenData.limits.limitMonth));
          setLimitTotal(String(tokenData.limits.limitTotal));
        }
      })
      .finally(() => {
        setPageLoading(false);
      });
  }, []);

  const handleSave = (e: React.SyntheticEvent) => {
    e.preventDefault();

    const month = Number(limitMonth);
    const total = Number(limitTotal);
    const errors = validateLimits(month, total);
    setFieldErrors(errors);

    if (Object.keys(errors).length > 0) return;

    setServerError(null);
    setSuccessMessage(null);
    setSaving(true);

    accountService
      .updateTokenLimits({ limitMonth: month, limitTotal: total })
      .then(() => {
        setSuccessMessage(t('account.limitsUpdated'));
        return accountService.fetchTokenUsage();
      })
      .then((refreshed) => {
        setOverview(refreshed);
        if (refreshed.limits) {
          setLimitMonth(String(refreshed.limits.limitMonth));
          setLimitTotal(String(refreshed.limits.limitTotal));
        }
      })
      .catch((err: unknown) => {
        interface AxiosErrorShape {
          response?: { status: number; data?: { messages?: { message: string }[] } };
        }
        const axiosErr = err as AxiosErrorShape;
        if (axiosErr.response?.status === 400 && axiosErr.response.data?.messages) {
          const msgs = axiosErr.response.data.messages.map((m) => m.message).join('. ');
          setServerError(msgs);
        } else {
          setServerError(t('account.saveError'));
        }
      })
      .finally(() => {
        setSaving(false);
      });
  };

  const characterNameMap = new Map(characters.map((c) => [c.characterHash, c.name]));

  if (pageLoading) {
    return (
      <div className={styles.page}>
        <div className={styles.loading}>{t('common.loading')}</div>
      </div>
    );
  }

  const limits = overview?.limits;
  const usage = overview?.usage;

  const monthPercentage =
    limits && usage ? calcUsagePercentage(usage.monthTokens, limits.limitMonth) : 0;
  const totalPercentage =
    limits && usage ? calcUsagePercentage(usage.totalTokens, limits.limitTotal) : 0;
  const monthLevel = getUsageLevel(monthPercentage);
  const totalLevel = getUsageLevel(totalPercentage);

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>{t('account.title')}</h1>

      <div className={styles.infoBanner}>{t('account.infoBanner')}</div>

      <form className={styles.panel} onSubmit={handleSave}>
        <h2 className={styles.panelTitle}>{t('account.tokenLimits')}</h2>

        <div className={styles.formRow}>
          <div className={styles.formField}>
            <label htmlFor='limitMonth'>{t('account.monthlyLimit')}</label>
            <input
              id='limitMonth'
              type='number'
              value={limitMonth}
              onChange={(e) => {
                setLimitMonth(e.target.value);
                setFieldErrors({});
                setServerError(null);
                setSuccessMessage(null);
              }}
            />
            {fieldErrors.limitMonth != null && (
              <span className={styles.fieldError}>{t(fieldErrors.limitMonth)}</span>
            )}
          </div>
          <div className={styles.formField}>
            <label htmlFor='limitTotal'>{t('account.totalLimit')}</label>
            <input
              id='limitTotal'
              type='number'
              value={limitTotal}
              onChange={(e) => {
                setLimitTotal(e.target.value);
                setFieldErrors({});
                setServerError(null);
                setSuccessMessage(null);
              }}
            />
            {fieldErrors.limitTotal != null && (
              <span className={styles.fieldError}>{t(fieldErrors.limitTotal)}</span>
            )}
          </div>
        </div>

        {serverError != null && <div className={styles.errorBanner}>{serverError}</div>}
        {successMessage != null && <div className={styles.successBanner}>{successMessage}</div>}

        <button type='submit' className={styles.saveButton} disabled={saving}>
          {saving ? t('account.saving') : t('account.saveLimits')}
        </button>
      </form>

      {limits && usage && (
        <div className={styles.panel}>
          <h2 className={styles.panelTitle}>{t('account.tokenUsage')}</h2>

          <div className={styles.statsGrid}>
            <div className={styles.statCard}>
              <span className={styles.statLabel}>{t('account.currentMonth')}</span>
              <span className={styles.statValue}>
                {formatTokens(usage.monthTokens)} / {formatTokens(limits.limitMonth)}
              </span>
              <div className={styles.progressBar}>
                <div
                  className={`${styles.progressFill} ${styles[monthLevel]}`}
                  style={{ width: `${String(monthPercentage)}%` }}
                />
              </div>
            </div>

            <div className={styles.statCard}>
              <span className={styles.statLabel}>{t('account.total')}</span>
              <span className={styles.statValue}>
                {formatTokens(usage.totalTokens)} / {formatTokens(limits.limitTotal)}
              </span>
              <div className={styles.progressBar}>
                <div
                  className={`${styles.progressFill} ${styles[totalLevel]}`}
                  style={{ width: `${String(totalPercentage)}%` }}
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {overview && overview.characters.length > 0 && (
        <div className={styles.panel}>
          <h2 className={styles.panelTitle}>{t('account.usageByCharacter')}</h2>

          <table className={styles.table}>
            <thead>
              <tr>
                <th>{t('account.characterColumn')}</th>
                <th>{t('account.monthTokensColumn')}</th>
                <th>{t('account.totalTokensColumn')}</th>
              </tr>
            </thead>
            <tbody>
              {overview.characters.map((c) => (
                <tr key={c.characterHash}>
                  <td>{characterNameMap.get(c.characterHash) ?? c.characterHash}</td>
                  <td>{formatTokens(c.monthTokens)}</td>
                  <td>{formatTokens(c.totalTokens)}</td>
                </tr>
              ))}
              {overview.characters.length > 1 && (
                <tr className={styles.totalRow}>
                  <td>{t('account.total')}</td>
                  <td>{formatTokens(usage?.monthTokens ?? 0)}</td>
                  <td>{formatTokens(usage?.totalTokens ?? 0)}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default Account;
