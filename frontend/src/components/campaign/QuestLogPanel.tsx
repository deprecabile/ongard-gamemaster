import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import MDEditor from '@uiw/react-md-editor';

import type { CampaignQuestLog } from '@/contract/campaign';

import styles from './QuestLogPanel.module.scss';

interface QuestLogPanelProps {
  questLog: CampaignQuestLog | null;
}

const QuestLogPanel = ({ questLog }: QuestLogPanelProps) => {
  const { t } = useTranslation();
  const [showCompleted, setShowCompleted] = useState(false);

  const content = showCompleted ? questLog?.questCompleted : questLog?.questActive;

  return (
    <div className={styles.panel}>
      <div className={styles.toggle}>
        <button
          className={`${styles.toggleBtn} ${showCompleted ? styles.active : ''}`}
          onClick={() => {
            setShowCompleted(true);
          }}
        >
          {t('questLog.completed')}
        </button>
        <button
          className={`${styles.toggleBtn} ${!showCompleted ? styles.active : ''}`}
          onClick={() => {
            setShowCompleted(false);
          }}
        >
          {t('questLog.active')}
        </button>
      </div>

      <div className={styles.content} data-color-mode='dark'>
        {content ? (
          <MDEditor.Markdown source={content} />
        ) : (
          <p className={styles.empty}>{t('questLog.empty')}</p>
        )}
      </div>
    </div>
  );
};

export default QuestLogPanel;
