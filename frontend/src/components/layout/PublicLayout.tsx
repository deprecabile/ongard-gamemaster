import { useTranslation } from 'react-i18next';
import { Link, Outlet } from 'react-router-dom';
import { GoogleOAuthProvider } from '@react-oauth/google';

import LanguageSelector from '@/components/language-selector/LanguageSelector';
import { ROUTES } from '@/routes/routes';

import styles from './PublicLayout.module.scss';

const PublicLayout = () => {
  const { t } = useTranslation();

  return (
    <GoogleOAuthProvider clientId={__GOOGLE_CLIENT_ID__}>
      <div className={styles.publicLayout}>
        <header className={styles.header}>
          <Link to={ROUTES.HOME} className={styles.logo}>
            Ondgard
          </Link>
          <nav className={styles.nav}>
            <Link to={ROUTES.LOGIN}>{t('common.login')}</Link>
            <Link to={ROUTES.REGISTER}>{t('common.register')}</Link>
            <LanguageSelector />
          </nav>
        </header>
        <main className={styles.main}>
          <Outlet />
        </main>
      </div>
    </GoogleOAuthProvider>
  );
};

export default PublicLayout;
