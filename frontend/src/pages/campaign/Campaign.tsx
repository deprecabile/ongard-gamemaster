import styles from './Campaign.module.scss';

const Campaign = () => {
  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Campagna</h1>
      <p className={styles.placeholder}>La tua avventura sta per iniziare...</p>
    </div>
  );
};

export default Campaign;
