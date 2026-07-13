import { useTranslation } from 'react-i18next';

import styles from './LanguageSelector.module.scss';

const LanguageSelector = () => {
  const { t, i18n } = useTranslation();

  const supportedLngs = ((i18n.options.supportedLngs as string[] | false) || []).filter(
    (lng) => lng !== 'cimode',
  );

  return (
    <select
      className={styles.langSelect}
      value={i18n.language}
      onChange={(e) => void i18n.changeLanguage(e.target.value)}
      aria-label='Language'
    >
      {supportedLngs.map((lng) => (
        <option key={lng} value={lng}>
          {t(`language.${lng}`)}
        </option>
      ))}
    </select>
  );
};

export default LanguageSelector;
