import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import MDEditor from '@uiw/react-md-editor';

import { characterService } from '@/api/characterService';
import { ROUTES } from '@/routes/routes';
import { useConfigStore } from '@/store/useConfigStore';

import styles from './InitCampaign.module.scss';

const InitCampaign = () => {
  const navigate = useNavigate();
  const loadRaces = useConfigStore((s) => s.loadRaces);
  const races = useConfigStore((s) => s.races);

  const [raceCode, setRaceCode] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [context, setContext] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    void loadRaces();
  }, [loadRaces]);

  const isValid =
    raceCode.trim() !== '' &&
    name.trim() !== '' &&
    description.trim() !== '' &&
    context.trim() !== '';

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    if (!isValid) return;

    const race = races.get(raceCode);
    if (!race) return;

    setError(null);
    setLoading(true);

    characterService
      .create({ race, name: name.trim(), description: description.trim() })
      .then(() => {
        void navigate(ROUTES.CAMPAIGN);
      })
      .catch(() => {
        setError('Si è verificato un errore. Riprova più tardi.');
      })
      .finally(() => {
        setLoading(false);
      });
  };

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Nuova Campagna</h1>

      <form className={styles.form} onSubmit={handleSubmit}>
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Crea il tuo personaggio</h2>

          <div className='form-field'>
            <label htmlFor='race'>Razza</label>
            <select
              id='race'
              className={styles.select}
              value={raceCode}
              onChange={(e) => {
                setRaceCode(e.target.value);
              }}
            >
              <option value=''>Seleziona una razza</option>
              {Array.from(races.values()).map((r) => (
                <option key={r.code} value={r.code}>
                  {r.name}
                </option>
              ))}
            </select>
            {raceCode && races.has(raceCode) && (
              <div className={styles.raceDescription}>
                <p>{races.get(raceCode)?.description}</p>
              </div>
            )}
          </div>

          <div className='form-field'>
            <label htmlFor='charName'>Nome</label>
            <input
              id='charName'
              type='text'
              value={name}
              onChange={(e) => {
                setName(e.target.value);
              }}
              placeholder='Il nome del tuo personaggio'
            />
          </div>

          <div className='form-field'>
            <label htmlFor='charDesc'>Descrizione</label>
            <textarea
              id='charDesc'
              className={styles.textarea}
              value={description}
              onChange={(e) => {
                setDescription(e.target.value);
              }}
              placeholder='Descrivi il tuo personaggio...'
              rows={4}
            />
          </div>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Contesto iniziale</h2>

          <div className='form-field' style={{ height: '100%' }}>
            <label htmlFor='context'>Descrivi il contesto di partenza (Markdown supportato)</label>
            <div data-color-mode='dark' style={{ flexGrow: 1 }}>
              <MDEditor
                value={context}
                onChange={(val) => {
                  setContext(val ?? '');
                }}
                height={400}
                textareaProps={{
                  placeholder:
                    'Dove inizia la tua avventura? Qual è la situazione di partenza? Puoi usare il formato Markdown.',
                }}
                preview='edit'
              />
            </div>
          </div>
        </section>

        {error != null && <div className='error-banner'>{error}</div>}

        <button type='submit' className='btn-submit' disabled={!isValid || loading}>
          {loading ? 'Creazione in corso...' : "Inizia l'avventura"}
        </button>
      </form>
    </div>
  );
};

export default InitCampaign;
