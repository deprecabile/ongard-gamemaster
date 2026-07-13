import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import MDEditor from '@uiw/react-md-editor';

import { streamSetupInteraction } from '@/api/setupInteractionSseService';
import { formatTime } from '@/utils/formatTime';

import styles from './SetupAdvisorChat.module.scss';

export interface SetupAdvisorMessage {
  role: 'user' | 'advisor';
  content: string;
  timestamp: string;
}

interface SetupAdvisorChatProps {
  characterPrompt: string;
  startingSituation: string;
  raceCode: string;
  characterName: string;
  messages: SetupAdvisorMessage[];
  onMessagesChange: (msgs: SetupAdvisorMessage[]) => void;
  onClearConversation: () => void;
  onClose: () => void;
}

const SetupAdvisorChat = ({
  characterPrompt,
  startingSituation,
  raceCode,
  characterName,
  messages,
  onMessagesChange,
  onClearConversation,
  onClose,
}: SetupAdvisorChatProps) => {
  const { t } = useTranslation();
  const [inputText, setInputText] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, loading]);

  const handleSend = () => {
    const text = inputText.trim();
    if (!text || loading) return;

    const userMsg: SetupAdvisorMessage = {
      role: 'user',
      content: text,
      timestamp: new Date().toISOString(),
    };
    const updated = [...messages, userMsg];
    onMessagesChange(updated);
    setInputText('');
    setError(null);
    setLoading(true);

    abortRef.current?.abort();
    abortRef.current = streamSetupInteraction(
      {
        message: text,
        characterPrompt: characterPrompt || undefined,
        startingSituation: startingSituation || undefined,
        raceCode: raceCode || undefined,
        characterName: characterName || undefined,
      },
      {
        onStarted: () => undefined,
        onThinking: () => undefined,
        onCompleted: (payload) => {
          const advisorMsg: SetupAdvisorMessage = {
            role: 'advisor',
            content: payload.advisorOutput,
            timestamp: payload.tms,
          };
          onMessagesChange([...updated, advisorMsg]);
          setLoading(false);
        },
        onError: (errorCode, description) => {
          if (errorCode === 'HTTP_429') {
            setError(t('campaign.tokenLimitReached'));
          } else {
            setError(t('campaign.errorMessage', { code: errorCode, description }));
          }
          setLoading(false);
        },
      },
    );
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const isEmpty = messages.length === 0 && !loading;

  return (
    <div className={styles.overlay}>
      <div className={styles.header}>
        <button
          type='button'
          className={styles.clearBtn}
          onClick={onClearConversation}
          disabled={messages.length === 0}
          title={t('setupAdvisor.clearConversation')}
        >
          <svg viewBox='0 0 24 24' fill='none' stroke='currentColor' strokeWidth='2'>
            <polyline points='3 6 5 6 21 6' />
            <path d='M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2' />
          </svg>
        </button>
        <span className={styles.headerTitle}>{t('setupAdvisor.title')}</span>
        <button type='button' className={styles.closeBtn} onClick={onClose}>
          <svg viewBox='0 0 24 24' fill='none' stroke='currentColor' strokeWidth='2'>
            <path d='M18 6L6 18M6 6l12 12' />
          </svg>
        </button>
      </div>

      <div className={styles.messagesArea} ref={scrollRef} data-color-mode='dark'>
        {isEmpty && <div className={styles.emptyState}>{t('setupAdvisor.emptyState')}</div>}

        {messages.map((msg, i) => (
          <div key={i} className={styles.messageGroup}>
            <div
              className={`${styles.bubble} ${msg.role === 'user' ? styles.bubblePlayer : styles.bubbleAdvisor}`}
            >
              <div className={styles.bubbleAuthor}>
                {msg.role === 'user' ? t('setupAdvisor.you') : t('setupAdvisor.advisor')}
              </div>
              {msg.role === 'advisor' ? <MDEditor.Markdown source={msg.content} /> : msg.content}
              <div className={styles.bubbleMeta}>{formatTime(msg.timestamp)}</div>
            </div>
          </div>
        ))}

        {loading && (
          <div className={styles.messageGroup}>
            <div className={`${styles.bubble} ${styles.bubbleAdvisor}`}>
              <div className={styles.bubbleAuthor}>{t('setupAdvisor.advisor')}</div>
              <div className={styles.typingIndicator}>
                <span className={styles.typingDot} />
                <span className={styles.typingDot} />
                <span className={styles.typingDot} />
              </div>
            </div>
          </div>
        )}
      </div>

      {error != null && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.inputArea}>
        <textarea
          className={styles.inputField}
          value={inputText}
          onChange={(e) => {
            setInputText(e.target.value);
          }}
          onKeyDown={handleKeyDown}
          placeholder={t('setupAdvisor.placeholder')}
          disabled={loading}
          rows={1}
        />
        <button
          type='button'
          className={styles.sendBtn}
          onClick={handleSend}
          disabled={loading || inputText.trim() === ''}
          title={t('setupAdvisor.send')}
        >
          <svg viewBox='0 0 24 24' fill='currentColor'>
            <path d='M2.01 21L23 12 2.01 3 2 10l15 2-15 2z' />
          </svg>
        </button>
      </div>
    </div>
  );
};

export default SetupAdvisorChat;
