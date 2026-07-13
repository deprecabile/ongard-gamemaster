import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import MDEditor from '@uiw/react-md-editor';

import type { AdvisorLogEntry } from '@/contract/advisorLog';
import { formatTime } from '@/utils/formatTime';

import styles from './AskChatPanel.module.scss';

interface AskChatPanelProps {
  messages: AdvisorLogEntry[];
  pendingQuestion: string | null;
  loading: boolean;
  currentTurn: number;
}

const AskChatPanel = ({ messages, pendingQuestion, loading, currentTurn }: AskChatPanelProps) => {
  const { t } = useTranslation();
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, pendingQuestion]);

  if (messages.length === 0 && !pendingQuestion) {
    return (
      <div className={styles.container}>
        <div className={styles.emptyState}>{t('campaign.askEmptyState')}</div>
      </div>
    );
  }

  const lastMessageTurn = messages.at(-1)?.turnNumber ?? -1;

  return (
    <div className={styles.container} ref={scrollRef} data-color-mode='dark'>
      {messages.map((entry, i) => {
        const showTurnSep = i === 0 || entry.turnNumber !== messages.at(i - 1)?.turnNumber;

        return (
          <div key={i} className={styles.messageGroup}>
            {showTurnSep && (
              <div className={styles.turnSeparator}>
                <span className={styles.turnLabel}>
                  {t('campaign.askTurnLabel', { turn: entry.turnNumber })}
                </span>
              </div>
            )}

            <div className={`${styles.bubble} ${styles.bubblePlayer}`}>
              <div className={styles.bubbleAuthor}>{t('campaign.askYou')}</div>
              {entry.userMessage}
              <div className={styles.bubbleMeta}>{formatTime(entry.created)}</div>
            </div>

            <div className={`${styles.bubble} ${styles.bubbleAdvisor}`}>
              <div className={styles.bubbleAuthor}>{t('campaign.askAdvisor')}</div>
              <MDEditor.Markdown source={entry.advisorResponse} />
              <div className={styles.bubbleMeta}>{formatTime(entry.created)}</div>
            </div>
          </div>
        );
      })}

      {pendingQuestion && (
        <div className={styles.messageGroup}>
          {currentTurn !== lastMessageTurn && (
            <div className={styles.turnSeparator}>
              <span className={styles.turnLabel}>
                {t('campaign.askTurnLabel', { turn: currentTurn })}
              </span>
            </div>
          )}

          <div className={`${styles.bubble} ${styles.bubblePlayer}`}>
            <div className={styles.bubbleAuthor}>{t('campaign.askYou')}</div>
            {pendingQuestion}
          </div>

          {loading && (
            <div className={`${styles.bubble} ${styles.bubbleAdvisor}`}>
              <div className={styles.bubbleAuthor}>{t('campaign.askAdvisor')}</div>
              <div className={styles.typingIndicator}>
                <span className={styles.typingDot} />
                <span className={styles.typingDot} />
                <span className={styles.typingDot} />
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default AskChatPanel;
