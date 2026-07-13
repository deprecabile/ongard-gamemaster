import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import MDEditor from '@uiw/react-md-editor';

import { accountService } from '@/api/accountService';
import { campaignService, streamInteraction } from '@/api/campaignService';
import { characterService } from '@/api/characterService';
import AskChatPanel from '@/components/campaign/AskChatPanel';
import CampaignHeader from '@/components/campaign/CampaignHeader';
import InventoryPanel from '@/components/campaign/InventoryPanel';
import QuestLogPanel from '@/components/campaign/QuestLogPanel';
import StatusPanel from '@/components/campaign/StatusPanel';
import type { AdvisorLogEntry } from '@/contract/advisorLog';
import type { CampaignTurnResponse } from '@/contract/campaign';
import { ChatMode, type ChatModeType } from '@/contract/chatMode';
import { ROUTES } from '@/routes/routes';
import { useAuthStore } from '@/store/useAuthStore';
import { calcUsagePercentage } from '@/utils/formatTokens';

import styles from './Campaign.module.scss';

interface StoryMessage {
  author: string;
  content: string;
}

type LeftPanelTab = 'ask' | 'action' | 'notes';

const MIN_QUEST_HEIGHT = 60;
const DEFAULT_QUEST_HEIGHT = 160;

const Campaign = () => {
  const { t } = useTranslation();
  const { characterHash } = useParams() as { characterHash: string };
  const location = useLocation() as { state: { initialContext?: string } | null; pathname: string };
  const navigate = useNavigate();

  const [mode, setMode] = useState<ChatModeType>(ChatMode.ACTION);
  const [activeTab, setActiveTab] = useState<LeftPanelTab>('action');
  const [askText, setAskText] = useState('');
  const [actionText, setActionText] = useState('');
  const [messages, setMessages] = useState<StoryMessage[]>([]);
  const [characterName, setCharacterName] = useState<string | null>(null);
  const [notes, setNotes] = useState('');
  const [notesSaved, setNotesSaved] = useState(true);
  const [loading, setLoading] = useState(false);
  const [turnData, setTurnData] = useState<CampaignTurnResponse | null>(null);
  const [turnLoading, setTurnLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [questLogCollapsed, setQuestLogCollapsed] = useState(false);
  const [questLogHeight, setQuestLogHeight] = useState(DEFAULT_QUEST_HEIGHT);
  const [tokenStatus, setTokenStatus] = useState<
    { type: 'limit' } | { type: 'warning'; pct: number } | null
  >(null);

  const tokenBlocked = tokenStatus?.type === 'limit';

  const [askMessages, setAskMessages] = useState<AdvisorLogEntry[]>([]);
  const [pendingQuestion, setPendingQuestion] = useState<string | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const initialSentRef = useRef(false);
  const historyLoadedRef = useRef(false);
  const advisorHistoryLoadedRef = useRef(false);
  const pendingQuestionRef = useRef<string | null>(null);
  const dragRef = useRef<{ startY: number; startHeight: number } | null>(null);
  const panelLeftRef = useRef<HTMLDivElement>(null);
  const storyRef = useRef<HTMLDivElement>(null);
  const notesLoadedRef = useRef(false);
  const notesDirtyRef = useRef(false);
  const notesRef = useRef(notes);

  notesRef.current = notes;

  const currentText = mode === ChatMode.ASK ? askText : actionText;
  const setCurrentText = mode === ChatMode.ASK ? setAskText : setActionText;

  const handleTabChange = (tab: LeftPanelTab) => {
    setActiveTab(tab);
    if (tab === 'ask') setMode(ChatMode.ASK);
    if (tab === 'action') setMode(ChatMode.ACTION);
  };

  const refreshTurn = useCallback(async () => {
    setTurnLoading(true);
    try {
      const data = await campaignService.fetchTurn(characterHash);
      setTurnData(data);
    } finally {
      setTurnLoading(false);
    }
  }, [characterHash]);

  const checkTokenUsage = useCallback(async () => {
    try {
      const data = await accountService.fetchTokenUsage();
      if (!data.limits) {
        setTokenStatus(null);
        return;
      }
      const monthPct = calcUsagePercentage(data.usage.monthTokens, data.limits.limitMonth);
      const totalPct = calcUsagePercentage(data.usage.totalTokens, data.limits.limitTotal);
      const maxPct = Math.max(monthPct, totalPct);

      if (maxPct >= 100) {
        setTokenStatus({ type: 'limit' });
      } else if (maxPct >= 85) {
        setTokenStatus({ type: 'warning', pct: Math.round(maxPct) });
      } else {
        setTokenStatus(null);
      }
    } catch {
      // silently ignore — don't block gameplay if account service is down
    }
  }, []);

  const addMessage = (author: string, content: string) => {
    setMessages((prev) => [...prev, { author, content }]);
  };

  const sendMessage = useCallback(
    (message: string, chatMode: ChatModeType) => {
      if (!message.trim()) return;

      abortRef.current = streamInteraction(characterHash, message, chatMode, {
        onStarted: () => {
          setLoading(true);
        },
        onProgress: (msg) => {
          setStatusMessage(msg);
        },
        onCompleted: (gmOutput) => {
          addMessage('Game Master', gmOutput);
          setLoading(false);
          setStatusMessage(null);
          abortRef.current = null;
          void refreshTurn();
          void checkTokenUsage();
        },
        onAdvisorCompleted: (advisorOutput) => {
          const question = pendingQuestionRef.current;
          if (question) {
            setAskMessages((prev) => [
              ...prev,
              {
                turnNumber: turnData?.currentTurn ?? 0,
                userMessage: question,
                advisorResponse: advisorOutput,
                created: new Date().toISOString(),
              },
            ]);
            pendingQuestionRef.current = null;
            setPendingQuestion(null);
          }
          setLoading(false);
          setStatusMessage(null);
          abortRef.current = null;
          void checkTokenUsage();
        },
        onError: (code, description) => {
          if (code === 'HTTP_429') {
            setTokenStatus({ type: 'limit' });
          } else {
            addMessage('Game Master', t('campaign.errorMessage', { code, description }));
          }
          pendingQuestionRef.current = null;
          setPendingQuestion(null);
          setLoading(false);
          setStatusMessage(null);
          abortRef.current = null;
        },
      });
    },
    [characterHash, refreshTurn, checkTokenUsage, t, turnData],
  );

  const handleSend = () => {
    if (!currentText.trim() || loading || tokenBlocked) return;

    const message = currentText;
    setCurrentText('');

    if (mode === ChatMode.ASK) {
      pendingQuestionRef.current = message;
      setPendingQuestion(message);
    } else {
      addMessage(characterName ?? t('campaign.defaultCharacterName'), message);
    }

    sendMessage(message, mode);
  };

  const handleResizeStart = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      dragRef.current = { startY: e.clientY, startHeight: questLogHeight };

      const handleMouseMove = (ev: MouseEvent) => {
        if (!dragRef.current || !panelLeftRef.current) return;
        const delta = dragRef.current.startY - ev.clientY;
        const panelHeight = panelLeftRef.current.clientHeight;
        const maxHeight = Math.max(MIN_QUEST_HEIGHT, panelHeight - 200);
        const newHeight = Math.min(
          maxHeight,
          Math.max(MIN_QUEST_HEIGHT, dragRef.current.startHeight + delta),
        );
        setQuestLogHeight(newHeight);
      };

      const handleMouseUp = () => {
        dragRef.current = null;
        document.removeEventListener('mousemove', handleMouseMove);
        document.removeEventListener('mouseup', handleMouseUp);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
      };

      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = 'row-resize';
      document.body.style.userSelect = 'none';
    },
    [questLogHeight],
  );

  useEffect(() => {
    const initialContext = location.state?.initialContext;
    if (initialSentRef.current || !initialContext) return;
    initialSentRef.current = true;
    void navigate(location.pathname, { replace: true });
    sendMessage(initialContext, ChatMode.ACTION);
  }, [location, characterHash, navigate, sendMessage]);

  useEffect(() => {
    if (historyLoadedRef.current || !turnData || !characterName) return;
    const isResume = !location.state?.initialContext;
    if (isResume && turnData.currentTurn > 0) {
      historyLoadedRef.current = true;
      void campaignService.fetchHistory(characterHash).then((history) => {
        const msgs = history.flatMap((e) => [
          { author: characterName, content: e.userMessage },
          { author: 'Game Master', content: e.gmResponse },
        ]);
        setMessages(msgs);
      });
    } else {
      historyLoadedRef.current = true;
    }
  }, [turnData, characterHash, location.state, characterName]);

  useEffect(() => {
    if (advisorHistoryLoadedRef.current || !turnData) return;
    const isResume = !location.state?.initialContext;
    if (isResume && turnData.currentTurn > 0) {
      advisorHistoryLoadedRef.current = true;
      void campaignService.fetchAdvisorLog(characterHash).then((log) => {
        setAskMessages(log);
      });
    } else {
      advisorHistoryLoadedRef.current = true;
    }
  }, [turnData, characterHash, location.state]);

  useEffect(() => {
    void characterService.getByHash(characterHash).then((char) => {
      if (char) setCharacterName(char.name);
    });
  }, [characterHash]);

  useEffect(() => {
    void refreshTurn();
    void checkTokenUsage();
  }, [refreshTurn, checkTokenUsage]);

  useEffect(() => {
    if (storyRef.current) {
      storyRef.current.scrollTop = storyRef.current.scrollHeight;
    }
  }, [messages]);

  useEffect(() => {
    const handleBeforeUnload = () => {
      const token = useAuthStore.getState().accessToken;
      if (token) {
        void fetch(
          `/api/chat/campaign/session/end?characterHash=${encodeURIComponent(characterHash)}`,
          {
            method: 'POST',
            headers: { Authorization: `Bearer ${token}` },
            keepalive: true,
          },
        );
      }
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
      void campaignService.endSession(characterHash);
    };
  }, [characterHash]);

  useEffect(() => {
    const flushNotes = () => {
      if (!notesDirtyRef.current) return;
      const token = useAuthStore.getState().accessToken;
      if (!token) return;
      void fetch('/api/chat/campaign/playerNotes', {
        method: 'PUT',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ characterHash, newNotesSnapshot: notesRef.current }),
        keepalive: true,
      });
      notesDirtyRef.current = false;
    };

    void campaignService.fetchPlayerNotes(characterHash).then(
      (content) => {
        setNotes(content);
        notesLoadedRef.current = true;
      },
      () => {
        setNotes('');
        notesLoadedRef.current = true;
      },
    );

    const handleBeforeUnload = () => {
      flushNotes();
    };
    window.addEventListener('beforeunload', handleBeforeUnload);

    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
      flushNotes();
    };
  }, [characterHash]);

  useEffect(() => {
    if (!characterHash || !notesLoadedRef.current || !notesDirtyRef.current) return;

    const timer = setTimeout(() => {
      void campaignService.updatePlayerNotes(characterHash, notes).then(
        () => {
          notesDirtyRef.current = false;
          setNotesSaved(true);
        },
        () => {
          /* dirty flag stays — next cycle retries */
        },
      );
    }, 10_000);

    return () => {
      clearTimeout(timer);
    };
  }, [notes, characterHash]);

  if (!characterHash) {
    return <Navigate to={ROUTES.DASHBOARD} replace />;
  }

  return (
    <div className={styles.page}>
      <div className={styles.mainArea}>
        {turnData && <CampaignHeader turnData={turnData} />}

        {tokenStatus?.type === 'limit' && (
          <div className={styles.tokenBlocked}>{t('campaign.tokenLimitReached')}</div>
        )}
        {tokenStatus?.type === 'warning' && (
          <div className={styles.tokenWarning}>
            {t('campaign.tokenWarning', { percentage: tokenStatus.pct })}
          </div>
        )}

        <div className={styles.columns}>
          <div className={styles.panelLeft} ref={panelLeftRef}>
            <div className={styles.toggle}>
              <button
                className={`${styles.toggleBtn} ${activeTab === 'ask' ? styles.active : ''}`}
                onClick={() => {
                  handleTabChange('ask');
                }}
              >
                {t('campaign.tabAsk')}
              </button>
              <button
                className={`${styles.toggleBtn} ${activeTab === 'action' ? styles.active : ''}`}
                onClick={() => {
                  handleTabChange('action');
                }}
              >
                {t('campaign.tabAction')}
              </button>
              <button
                className={`${styles.toggleBtn} ${activeTab === 'notes' ? styles.active : ''}`}
                onClick={() => {
                  handleTabChange('notes');
                }}
              >
                {t('campaign.tabNotes')}
              </button>
            </div>

            {activeTab === 'notes' ? (
              <>
                <span className={styles.notesTitle}>{t('campaign.notesTitle')}</span>
                <div className={styles.notesWrapper}>
                  <textarea
                    className={styles.notesArea}
                    value={notes}
                    maxLength={50000}
                    onChange={(e) => {
                      setNotes(e.target.value);
                      notesDirtyRef.current = true;
                      setNotesSaved(false);
                    }}
                  />
                  {notesLoadedRef.current && (
                    <span
                      className={notesSaved ? styles.notesSaved : styles.notesUnsaved}
                      title={t(notesSaved ? 'campaign.notesSaved' : 'campaign.notesUnsaved')}
                    >
                      {notesSaved ? '\u2713' : '\u2717'}
                    </span>
                  )}
                </div>
              </>
            ) : activeTab === 'ask' ? (
              <>
                <AskChatPanel
                  messages={askMessages}
                  pendingQuestion={pendingQuestion}
                  loading={loading}
                  currentTurn={turnData?.currentTurn ?? 0}
                />

                <textarea
                  className={`${styles.chatInput} ${styles.chatInputCompact}`}
                  placeholder={
                    tokenBlocked
                      ? t('campaign.tokenLimitPlaceholder')
                      : t('campaign.askPlaceholder')
                  }
                  value={currentText}
                  disabled={loading || tokenBlocked}
                  onChange={(e) => {
                    setCurrentText(e.target.value);
                  }}
                />

                <button
                  className={styles.sendButton}
                  onClick={handleSend}
                  disabled={loading || !currentText.trim() || tokenBlocked}
                >
                  {tokenBlocked
                    ? t('campaign.blocked')
                    : loading
                      ? t('campaign.waiting')
                      : t('campaign.send')}
                </button>
              </>
            ) : (
              <>
                <textarea
                  className={styles.chatInput}
                  placeholder={
                    tokenBlocked
                      ? t('campaign.tokenLimitPlaceholder')
                      : t('campaign.actionPlaceholder')
                  }
                  value={currentText}
                  disabled={loading || tokenBlocked}
                  onChange={(e) => {
                    setCurrentText(e.target.value);
                  }}
                />

                <button
                  className={styles.sendButton}
                  onClick={handleSend}
                  disabled={loading || !currentText.trim() || tokenBlocked}
                >
                  {tokenBlocked
                    ? t('campaign.blocked')
                    : loading
                      ? t('campaign.waiting')
                      : t('campaign.send')}
                </button>

                {!questLogCollapsed && (
                  // eslint-disable-next-line jsx-a11y/no-static-element-interactions
                  <div className={styles.resizeHandle} onMouseDown={handleResizeStart}>
                    <div className={styles.resizeGrip} />
                  </div>
                )}

                <div
                  className={`${styles.questLogSection} ${questLogCollapsed ? styles.collapsed : ''}`}
                  style={questLogCollapsed ? undefined : { height: questLogHeight }}
                >
                  <div className={styles.questLogHeader}>
                    <span className={styles.questLogTitle}>{t('campaign.quests')}</span>
                    <button
                      className={styles.collapseBtn}
                      onClick={() => {
                        setQuestLogCollapsed(!questLogCollapsed);
                      }}
                      title={questLogCollapsed ? t('campaign.expand') : t('campaign.collapse')}
                    >
                      {questLogCollapsed ? '\u25B8' : '\u25BE'}
                    </button>
                  </div>
                  {!questLogCollapsed && <QuestLogPanel questLog={turnData?.questLog ?? null} />}
                </div>
              </>
            )}
          </div>

          <div className={styles.panelCenter}>
            <div className={styles.storyOutput} ref={storyRef} data-color-mode='dark'>
              {messages.map((msg, i) => (
                <div key={i} className={styles.storyEntry}>
                  <div className={styles.storySeparator}>
                    <span className={styles.storyAuthor}>{msg.author}</span>
                  </div>
                  <div className={styles.storyContent}>
                    <MDEditor.Markdown source={msg.content} />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className={styles.panelRight}>
        <InventoryPanel inventory={turnData?.inventory ?? null} loading={turnLoading} />
        <StatusPanel message={statusMessage} loading={loading} />
      </div>
    </div>
  );
};

export default Campaign;
