import styles from './StatusPanel.module.scss';

interface StatusPanelProps {
  message: string | null;
  loading: boolean;
}

const StatusPanel = ({ message, loading }: StatusPanelProps) => (
  <div className={styles.panel}>
    <span className={`${styles.text} ${loading ? styles.breathing : ''}`}>{message ?? ''}</span>
    {loading && (
      <svg className={styles.spinner} width='16' height='16' viewBox='0 0 16 16'>
        <circle cx='8' cy='8' r='6' fill='none' stroke='var(--color-border)' strokeWidth='2' />
        <path
          d='M8 2a6 6 0 0 1 6 6'
          fill='none'
          stroke='var(--color-accent)'
          strokeWidth='2'
          strokeLinecap='round'
        />
      </svg>
    )}
  </div>
);

export default StatusPanel;
