import i18n from '@/i18n';

export function formatRelativeDate(isoDate: string): string {
  const diffMs = Date.now() - new Date(isoDate).getTime();
  const mins = Math.floor(diffMs / 60000);
  const hours = Math.floor(diffMs / 3600000);
  const days = Math.floor(diffMs / 86400000);
  if (mins < 1) return i18n.t('relativeDate.now');
  if (mins < 60) return i18n.t('relativeDate.minutesAgo', { count: mins });
  if (hours < 24) return i18n.t('relativeDate.hoursAgo', { count: hours });
  if (days < 30) return i18n.t('relativeDate.daysAgo', { count: days });
  return new Date(isoDate).toLocaleDateString(i18n.language);
}
