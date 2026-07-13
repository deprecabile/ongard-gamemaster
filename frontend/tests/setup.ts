import { initReactI18next } from 'react-i18next';
import i18n from 'i18next';

import en from '@/locales/en.json';

import '@testing-library/jest-dom/vitest';

void i18n.use(initReactI18next).init({
  lng: 'en',
  resources: { en: { translation: en } },
  interpolation: { escapeValue: false },
});
