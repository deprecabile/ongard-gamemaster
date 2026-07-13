import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';

import { campaignService } from '@/api/campaignService';
import type { CampaignListItem } from '@/contract/campaign';
import { ROUTES } from '@/routes/routes';
import { useConfigStore } from '@/store/useConfigStore';
import { formatRelativeDate } from '@/utils/formatRelativeDate';

import styles from './LoadCampaign.module.scss';

const LoadCampaign = () => {
  const { t, i18n } = useTranslation();
  const [campaigns, setCampaigns] = useState<CampaignListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedHash, setSelectedHash] = useState<string | null>(null);
  const [questCache, setQuestCache] = useState<Record<string, string | null>>({});
  const [questLoading, setQuestLoading] = useState<string | null>(null);
  const loadRaces = useConfigStore((s) => s.loadRaces);
  const navigate = useNavigate();

  const handleSelect = useCallback(
    (characterHash: string) => {
      setSelectedHash(characterHash);

      if (characterHash in questCache) return;

      setQuestLoading(characterHash);
      void campaignService.fetchQuestActive(characterHash).then((quest) => {
        setQuestCache((prev) => ({ ...prev, [characterHash]: quest }));
        setQuestLoading((prev) => (prev === characterHash ? null : prev));
      });
    },
    [questCache],
  );

  useEffect(() => {
    void Promise.all([loadRaces(), campaignService.fetchCampaignList()])
      .then(([, list]) => {
        setCampaigns(list);
        const first = list[0];
        if (first) {
          handleSelect(first.characterHash);
        }
      })
      .catch(() => {
        setError(t('loadCampaign.loadError'));
      })
      .finally(() => {
        setLoading(false);
      });
  }, [loadRaces, handleSelect, t]);

  const handleDelete = async (characterHash: string, characterName: string) => {
    if (!window.confirm(t('loadCampaign.confirmDelete', { name: characterName }))) return;
    try {
      await campaignService.deleteCampaign(characterHash);
      const remaining = campaigns.filter((c) => c.characterHash !== characterHash);
      setCampaigns(remaining);
      if (selectedHash === characterHash) {
        setSelectedHash(remaining[0]?.characterHash ?? null);
      }
    } catch {
      setError(t('loadCampaign.deleteError'));
    }
  };

  if (loading) return <div className={styles.loading}>{t('loadCampaign.loadingCampaigns')}</div>;
  if (error) return <div className={styles.emptyState}>{error}</div>;
  if (campaigns.length === 0) {
    return (
      <div className={styles.emptyState}>
        {t('loadCampaign.emptyCampaigns')}{' '}
        <Link to={ROUTES.INIT_CAMPAIGN}>{t('loadCampaign.startNewAdventure')}</Link>
      </div>
    );
  }

  const selected = campaigns.find((c) => c.characterHash === selectedHash);
  const quest = selectedHash ? questCache[selectedHash] : undefined;

  return (
    <div className={styles.page}>
      <div className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <h1 className={styles.title}>{t('loadCampaign.title')}</h1>
          <p className={styles.subtitle}>{t('loadCampaign.subtitle')}</p>
        </div>

        <div className={styles.campaignList}>
          {campaigns.map((item) => (
            <div
              key={item.characterHash}
              className={`${styles.card} ${selectedHash === item.characterHash ? styles.active : ''}`}
              onClick={() => {
                handleSelect(item.characterHash);
              }}
              role='button'
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') handleSelect(item.characterHash);
              }}
            >
              <div className={styles.cardName}>{item.characterName}</div>
              <div className={styles.cardMeta}>
                <span>{t('loadCampaign.turnLabel', { turn: item.currentTurn })}</span>
                {item.currentLocation && (
                  <>
                    <span className={styles.dot} />
                    <span>{item.currentLocation}</span>
                  </>
                )}
              </div>
              <div className={styles.cardTime}>
                {formatRelativeDate(item.lastUpdate)} &middot;{' '}
                {new Date(item.lastUpdate).toLocaleString(i18n.language)}
              </div>
            </div>
          ))}
        </div>
      </div>

      {selected ? (
        <div className={styles.detail}>
          <div className={styles.heroPanel}>
            <div className={styles.heroInfo}>
              <h2 className={styles.heroName}>{selected.characterName}</h2>
              <span className={styles.heroRace}>
                {t(`races.${selected.raceCode}.name`, { defaultValue: selected.raceCode })}
              </span>
            </div>
            <div className={styles.heroActions}>
              <button
                className={styles.continueButton}
                onClick={() => {
                  void navigate(`/campaign/${selected.characterHash}`);
                }}
              >
                {t('common.continue')}
              </button>
              <button
                className={styles.deleteButton}
                onClick={() => {
                  void handleDelete(selected.characterHash, selected.characterName);
                }}
              >
                {t('common.delete')}
              </button>
            </div>
          </div>

          <div className={styles.statsRow}>
            <div className={styles.stat}>
              <div className={styles.statValue}>{String(selected.currentTurn)}</div>
              <div className={styles.statLabel}>{t('campaignHeader.turn')}</div>
            </div>
            <div className={styles.stat}>
              <div className={styles.statValue}>{selected.currentLocation ?? '--'}</div>
              <div className={styles.statLabel}>{t('campaignHeader.location')}</div>
            </div>
            <div className={styles.stat}>
              <div className={styles.statValue}>{formatRelativeDate(selected.lastUpdate)}</div>
              <div className={styles.statLabel}>{t('loadCampaign.lastSave')}</div>
            </div>
          </div>

          <div className={styles.contentRow}>
            <div className={styles.narrativePanel}>
              <div className={styles.panelLabel}>{t('loadCampaign.narrative')}</div>
              <div className={styles.narrativeBody}>
                {selected.narrativePreview || t('loadCampaign.noNarrative')}
              </div>
            </div>

            <div className={styles.questPanel}>
              <div className={styles.panelLabel}>{t('loadCampaign.activeQuests')}</div>
              {questLoading === selected.characterHash ? (
                <div className={styles.questLoading}>{t('common.loading')}</div>
              ) : quest ? (
                <div className={styles.questBody}>{quest}</div>
              ) : (
                <div className={styles.questEmpty}>{t('loadCampaign.noActiveQuests')}</div>
              )}
            </div>
          </div>
        </div>
      ) : (
        <div className={styles.emptyDetail}>{t('loadCampaign.selectCampaign')}</div>
      )}
    </div>
  );
};

export default LoadCampaign;
