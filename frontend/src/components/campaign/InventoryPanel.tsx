import { useTranslation } from 'react-i18next';

import type { Contenitore, Inventory, Oggetto } from '@/contract/inventory';

import styles from './InventoryPanel.module.scss';

interface InventoryPanelProps {
  inventory: Inventory | null;
  loading: boolean;
}

const InventoryPanel = ({ inventory, loading }: InventoryPanelProps) => {
  const { t } = useTranslation();

  if (loading && !inventory) {
    return <div className={styles.loading}>{t('inventory.loading')}</div>;
  }

  if (!inventory) {
    return <div className={styles.empty}>{t('inventory.empty')}</div>;
  }

  const { money, mount, contenitori } = inventory;

  return (
    <div className={styles.panel}>
      <h3 className={styles.sectionTitle}>{t('inventory.currency')}</h3>
      <div className={styles.valutaRow}>
        <span className={styles.valutaItem}>
          {money} <span className={styles.valutaLabel}>{t('inventory.denante')}</span>
        </span>
      </div>

      {mount.length > 0 && (
        <>
          <h3 className={styles.sectionTitle}>{t('inventory.transport')}</h3>
          {mount.map((m) => (
            <div key={m.nome} className={styles.transportItem}>
              <span>{m.nome}</span>
              <span className={styles.transportMeta}>
                {m.tipo} &middot; {m.stato}
              </span>
            </div>
          ))}
        </>
      )}

      {contenitori.length > 0 && (
        <>
          <h3 className={styles.sectionTitle}>{t('inventory.containers')}</h3>
          {contenitori.map((c: Contenitore) => (
            <div key={c.nome} className={styles.container}>
              <span className={styles.containerName}>{c.nome}</span>
              {c.oggetti.map((item: Oggetto) => (
                <div key={item.nome}>
                  <div className={styles.itemRow}>
                    <span className={styles.itemName}>{item.nome}</span>
                    <span className={styles.itemQty}>
                      x{item.quantita} &middot; {item.peso}kg
                    </span>
                  </div>
                  {item.extra && item.extra.length > 0 && (
                    <div className={styles.itemExtra}>
                      {item.extra.map((e) => `${e.chiave}: ${e.valore}`).join(', ')}
                    </div>
                  )}
                </div>
              ))}
            </div>
          ))}
        </>
      )}
    </div>
  );
};

export default InventoryPanel;
