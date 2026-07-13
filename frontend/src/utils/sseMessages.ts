import i18n from 'i18next';

import type { SseProgressCodeValue } from '@/contract/sseProgressCode';
import type { SseSetupGenerateProgressCodeValue } from '@/contract/sseSetupGenerateProgressCode';

export function getSseMessage(
  code: SseProgressCodeValue | SseSetupGenerateProgressCodeValue,
): string {
  const variants = i18n.t(`sse.${code}`, { returnObjects: true });
  if (Array.isArray(variants)) {
    return variants[Math.floor(Math.random() * variants.length)] as string;
  }
  return typeof variants === 'string' ? variants : code;
}
