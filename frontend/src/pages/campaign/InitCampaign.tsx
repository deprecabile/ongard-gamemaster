import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import MDEditor from '@uiw/react-md-editor';

import { streamInitCampaign } from '@/api/campaignInitSseService';
import { campaignSetupService } from '@/api/campaignSetupService';
import { characterService } from '@/api/characterService';
import { healthService } from '@/api/healthService';
import { streamSetupGenerate } from '@/api/setupGenerateSseService';
import ArchetypeModal from '@/components/campaign/ArchetypeModal';
import SetupAdvisorChat, { type SetupAdvisorMessage } from '@/components/campaign/SetupAdvisorChat';
import StatusPanel from '@/components/campaign/StatusPanel';
import { useFieldPersistence } from '@/hooks/useFieldPersistence';
import { useConfigStore } from '@/store/useConfigStore';
import { useServiceReadyStore } from '@/store/useServiceReadyStore';

import styles from './InitCampaign.module.scss';

const InitCampaign = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const loadRaces = useConfigStore((s) => s.loadRaces);
  const races = useConfigStore((s) => s.races);

  const [raceCode, setRaceCode] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [context, setContext] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [archetypeCode, setArchetypeCode] = useState<string | null>(null);
  const [showArchetypeModal, setShowArchetypeModal] = useState(false);
  const [hasGenerated, setHasGenerated] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [showAdvisorChat, setShowAdvisorChat] = useState(false);
  const [advisorMessages, setAdvisorMessages] = useState<SetupAdvisorMessage[]>([]);
  const [restoring, setRestoring] = useState(true);

  const abortRef = useRef<AbortController | null>(null);
  const generateAbortRef = useRef<AbortController | null>(null);
  const restoredRef = useRef(false);
  const flushRegistryRef = useRef(new Map<string, () => void>());

  useEffect(() => {
    void loadRaces();
  }, [loadRaces]);

  // Restore session from Redis on mount
  useEffect(() => {
    if (restoredRef.current) return;
    restoredRef.current = true;

    const restore = async () => {
      try {
        const { active } = await campaignSetupService.getSessionStatus();
        if (!active) {
          setRestoring(false);
          return;
        }

        const formPromise = campaignSetupService.getSession();
        const historyPromise = campaignSetupService.getSessionHistory();

        try {
          const form = await formPromise;
          if (form) {
            if (form.raceCode) setRaceCode(form.raceCode);
            if (form.characterName) setName(form.characterName);
            if (form.characterPrompt) setDescription(form.characterPrompt);
            if (form.startingSituation) setContext(form.startingSituation);
            if (form.archetypeCode) {
              setArchetypeCode(form.archetypeCode);
              setHasGenerated(true);
            }
          }
        } catch {
          // form restore failed — continue with empty form
        } finally {
          setRestoring(false);
        }

        try {
          const history = await historyPromise;
          if (history.length > 0) {
            const msgs: SetupAdvisorMessage[] = history.flatMap((entry) => [
              { role: 'user' as const, content: entry.userMessage, timestamp: '' },
              { role: 'advisor' as const, content: entry.gmResponse, timestamp: '' },
            ]);
            setAdvisorMessages(msgs);
          }
        } catch {
          // history restore failed — ignore
        }
      } catch {
        setRestoring(false);
      }
    };

    void restore();
  }, []);

  // beforeunload: flush all dirty fields
  useEffect(() => {
    const handler = () => {
      flushRegistryRef.current.forEach((flush) => {
        flush();
      });
    };
    window.addEventListener('beforeunload', handler);
    return () => {
      window.removeEventListener('beforeunload', handler);
      handler();
    };
  }, []);

  useEffect(() => {
    return () => {
      abortRef.current?.abort();
      generateAbortRef.current?.abort();
    };
  }, []);

  const { onBlur: onBlurDescription } = useFieldPersistence({
    value: description,
    apiUrl: '/chat/campaign/setup/session/character-prompt',
    delayMs: 5000,
    flushRegistry: flushRegistryRef,
    registryKey: 'characterPrompt',
    enabled: !restoring,
  });

  const { onBlur: onBlurContext } = useFieldPersistence({
    value: context,
    apiUrl: '/chat/campaign/setup/session/starting-situation',
    delayMs: 5000,
    flushRegistry: flushRegistryRef,
    registryKey: 'startingSituation',
    enabled: !restoring,
  });

  const { onBlur: onBlurName } = useFieldPersistence({
    value: name,
    apiUrl: '/chat/campaign/setup/session/character-name',
    delayMs: 3000,
    flushRegistry: flushRegistryRef,
    registryKey: 'characterName',
    enabled: !restoring,
  });

  const isValid =
    raceCode.trim() !== '' &&
    name.trim() !== '' &&
    description.trim() !== '' &&
    context.trim() !== '';

  const startGeneration = (
    mode: 'FULL' | 'CHARACTER' | 'SCENE',
    code: string,
    reqRaceCode?: string,
    reqCharacterName?: string,
    characterPrompt?: string,
  ) => {
    generateAbortRef.current?.abort();
    setError(null);
    setGenerating(true);
    setStatusMessage(t('initCampaign.generatingContent'));

    generateAbortRef.current = streamSetupGenerate(
      {
        mode,
        archetypeCode: code,
        raceCode: reqRaceCode,
        characterName: reqCharacterName,
        characterPrompt,
      },
      {
        onStarted: () => {
          setStatusMessage(t('initCampaign.generatingContent'));
        },
        onProgress: (message) => {
          setStatusMessage(message);
        },
        onNamePicked: (rc, cn) => {
          setRaceCode(rc);
          setName(cn);
        },
        onCharacterGenerated: (cp) => {
          setDescription(cp);
        },
        onCompleted: (payload) => {
          if (payload.characterPrompt != null) setDescription(payload.characterPrompt);
          if (payload.startingSituation != null) setContext(payload.startingSituation);
          if (payload.raceCode != null) setRaceCode(payload.raceCode);
          if (payload.characterName != null) setName(payload.characterName);
          setHasGenerated(true);
          setGenerating(false);
          setStatusMessage(null);
        },
        onError: (errorCode, desc) => {
          if (errorCode === 'HTTP_429') {
            setError(t('initCampaign.tokenLimitReached'));
          } else {
            setError(t('campaign.errorMessage', { code: errorCode, description: desc }));
          }
          setGenerating(false);
          setStatusMessage(null);
        },
      },
    );
  };

  const handleArchetypeSelect = (code: string) => {
    setArchetypeCode(code);
    setShowArchetypeModal(false);
    startGeneration('FULL', code);
  };

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    if (!isValid) return;

    const race = races.get(raceCode);
    if (!race) return;

    setError(null);
    setLoading(true);
    setStatusMessage(t('initCampaign.checkingService'));

    void healthService.checkRagReady().then((ready) => {
      if (!ready) {
        useServiceReadyStore.getState().markUnavailable();
        setLoading(false);
        setStatusMessage(null);
        return;
      }

      setStatusMessage(t('initCampaign.creatingCharacter'));
      characterService
        .create({ race, name: name.trim(), description: description.trim() })
        .then((created) => {
          abortRef.current = streamInitCampaign(created.characterHash, context.trim(), {
            onStarted: () => {
              setStatusMessage(t('initCampaign.initializingCampaign'));
            },
            onProgress: (message) => {
              setStatusMessage(message);
            },
            onCompleted: () => {
              setLoading(false);
              setStatusMessage(null);
              void navigate(`/campaign/${created.characterHash}`, {
                state: { initialContext: context.trim() },
              });
            },
            onError: (errorCode, desc) => {
              if (errorCode === 'HTTP_429') {
                setError(t('initCampaign.tokenLimitReached'));
              } else {
                setError(t('campaign.errorMessage', { code: errorCode, description: desc }));
              }
              setLoading(false);
              setStatusMessage(null);
            },
          });
        })
        .catch(() => {
          setError(t('initCampaign.errorGeneric'));
          setLoading(false);
          setStatusMessage(null);
        });
    });
  };

  return (
    <div className={styles.page}>
      {restoring && (
        <div className={styles.restoreOverlay}>
          <div className={styles.restoreSpinner} />
          <span>{t('initCampaign.restoring')}</span>
        </div>
      )}

      <h1 className={styles.title}>{t('initCampaign.title')}</h1>

      <button
        type='button'
        className={styles.btnGenerate}
        disabled={loading || generating}
        onClick={() => {
          setShowArchetypeModal(true);
        }}
      >
        <svg viewBox='0 0 24 24' fill='none' stroke='currentColor' strokeWidth='2'>
          <path d='M12 2l2.09 6.26L20 10l-5.91 1.74L12 18l-2.09-6.26L4 10l5.91-1.74L12 2z' />
          <path d='M5 19l1.04 3.12L9 21l-2.96-.88L5 17l-1.04 3.12L1 21l2.96.88L5 19z' />
          <path d='M19 5l.7 2.1L22 8l-2.3.9L19 11l-.7-2.1L16 8l2.3-.9L19 5z' />
        </svg>
        {t('initCampaign.helpMeCreate')}
      </button>

      <form className={styles.form} onSubmit={handleSubmit}>
        <section className={styles.section}>
          <div className={styles.sectionTitleRow}>
            <h2 className={styles.sectionTitle}>{t('initCampaign.createCharacter')}</h2>
            {hasGenerated && (
              <button
                type='button'
                className={styles.btnRegenerate}
                disabled={generating}
                onClick={() => {
                  if (archetypeCode) startGeneration('CHARACTER', archetypeCode, raceCode, name);
                }}
              >
                {t('initCampaign.regenerateCharacter')}
              </button>
            )}
          </div>

          <div className='form-field'>
            <label htmlFor='race'>{t('initCampaign.race')}</label>
            <select
              id='race'
              className={styles.select}
              value={raceCode}
              onChange={(e) => {
                const newCode = e.target.value;
                setRaceCode(newCode);
                if (!restoring && newCode) {
                  void campaignSetupService.updateRaceCode(newCode);
                }
              }}
            >
              <option value=''>{t('initCampaign.selectRace')}</option>
              {Array.from(races.values()).map((r) => (
                <option key={r.code} value={r.code}>
                  {t(`races.${r.code}.name`)}
                </option>
              ))}
            </select>
            {raceCode && races.has(raceCode) && (
              <div className={styles.raceDescription}>
                <p>{t(`races.${raceCode}.description`)}</p>
              </div>
            )}
          </div>

          <div className='form-field'>
            <label htmlFor='charName'>{t('initCampaign.name')}</label>
            <input
              id='charName'
              type='text'
              value={name}
              onChange={(e) => {
                setName(e.target.value);
              }}
              onBlur={onBlurName}
              placeholder={t('initCampaign.namePlaceholder')}
            />
          </div>

          <div className='form-field'>
            <label htmlFor='charDesc'>{t('initCampaign.description')}</label>
            <textarea
              id='charDesc'
              className={styles.textarea}
              value={description}
              maxLength={5000}
              onChange={(e) => {
                setDescription(e.target.value);
              }}
              onBlur={onBlurDescription}
              placeholder={t('initCampaign.descriptionPlaceholder')}
              rows={4}
            />
          </div>
        </section>

        <section className={styles.section}>
          <div className={styles.sectionTitleRow}>
            <h2 className={styles.sectionTitle}>{t('initCampaign.initialContext')}</h2>
            {hasGenerated && (
              <button
                type='button'
                className={styles.btnRegenerate}
                disabled={generating}
                onClick={() => {
                  if (archetypeCode)
                    startGeneration('SCENE', archetypeCode, raceCode, name, description);
                }}
              >
                {t('initCampaign.regenerateScene')}
              </button>
            )}
          </div>

          <div className='form-field' style={{ height: '100%' }}>
            <label htmlFor='context'>{t('initCampaign.contextLabel')}</label>
            <div data-color-mode='dark' style={{ flexGrow: 1 }}>
              <MDEditor
                value={context}
                onChange={(val) => {
                  setContext(val ?? '');
                }}
                height={400}
                textareaProps={{
                  placeholder: t('initCampaign.contextPlaceholder'),
                  onBlur: onBlurContext,
                }}
                preview='edit'
              />
            </div>
          </div>
        </section>

        {error != null && <div className='error-banner'>{error}</div>}

        <button type='submit' className='btn-submit' disabled={!isValid || loading || generating}>
          {loading ? t('initCampaign.creating') : t('initCampaign.startAdventure')}
        </button>

        <StatusPanel message={statusMessage} loading={loading || generating} />
      </form>

      {showArchetypeModal && (
        <ArchetypeModal
          onClose={() => {
            setShowArchetypeModal(false);
          }}
          onSelect={handleArchetypeSelect}
        />
      )}

      {!showAdvisorChat && (
        <button
          type='button'
          className={styles.advisorToggle}
          onClick={() => {
            setShowAdvisorChat(true);
          }}
          title={t('setupAdvisor.toggleTitle')}
        >
          <svg viewBox='0 0 24 24' fill='none' stroke='currentColor' strokeWidth='2'>
            <path d='M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z' />
          </svg>
        </button>
      )}

      {showAdvisorChat && (
        <SetupAdvisorChat
          characterPrompt={description}
          startingSituation={context}
          raceCode={raceCode}
          characterName={name}
          messages={advisorMessages}
          onMessagesChange={setAdvisorMessages}
          onClearConversation={() => {
            setAdvisorMessages([]);
            void campaignSetupService.deleteSessionHistory();
          }}
          onClose={() => {
            setShowAdvisorChat(false);
          }}
        />
      )}
    </div>
  );
};

export default InitCampaign;
