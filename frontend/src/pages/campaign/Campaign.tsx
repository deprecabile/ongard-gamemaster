import { useState } from 'react';

import { ChatMode, type ChatModeType } from '@/contract/chatMode';

import styles from './Campaign.module.scss';

const Campaign = () => {
  const [mode, setMode] = useState<ChatModeType>(ChatMode.ACTION);
  const [askText, setAskText] = useState('');
  const [actionText, setActionText] = useState('');
  const [storyLog, setStoryLog] = useState('');
  const [notes, setNotes] = useState('');

  const currentText = mode === ChatMode.ASK ? askText : actionText;
  const setCurrentText = mode === ChatMode.ASK ? setAskText : setActionText;

  const handleSend = () => {
    if (!currentText.trim()) return;
    setStoryLog((prev) => (prev ? prev + '\n\n' : '') + `[${mode}] ${currentText}`);
    setCurrentText('');
  };

  return (
    <div className={styles.page}>
      <div className={styles.panelLeft}>
        <div className={styles.toggle}>
          <button
            className={`${styles.toggleBtn} ${mode === ChatMode.ASK ? styles.active : ''}`}
            onClick={() => {
              setMode(ChatMode.ASK);
            }}
          >
            Ask
          </button>
          <button
            className={`${styles.toggleBtn} ${mode === ChatMode.ACTION ? styles.active : ''}`}
            onClick={() => {
              setMode(ChatMode.ACTION);
            }}
          >
            Action
          </button>
        </div>

        <textarea
          className={styles.chatInput}
          placeholder={
            mode === ChatMode.ASK ? 'Fai una domanda al GM...' : 'Descrivi la tua azione...'
          }
          value={currentText}
          onChange={(e) => {
            setCurrentText(e.target.value);
          }}
        />

        <button className={styles.sendButton} onClick={handleSend} disabled={!currentText.trim()}>
          Invia
        </button>
      </div>

      <div className={styles.panelCenter}>
        <textarea className={styles.storyOutput} readOnly value={storyLog} />
      </div>

      <div className={styles.panelRight}>
        <div className={styles.inventory}>Inventario</div>

        <textarea
          className={styles.notesArea}
          placeholder='Appunti...'
          value={notes}
          onChange={(e) => {
            setNotes(e.target.value);
          }}
        />
      </div>
    </div>
  );
};

export default Campaign;
