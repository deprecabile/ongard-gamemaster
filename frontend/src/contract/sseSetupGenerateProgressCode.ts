export const SseSetupGenerateProgressCode = {
  SETUP_SUMMARIZING: 'SETUP_SUMMARIZING',
  SETUP_GENERATING_CHARACTER: 'SETUP_GENERATING_CHARACTER',
  SETUP_PICKING_NAME: 'SETUP_PICKING_NAME',
  SETUP_GENERATING_SCENE: 'SETUP_GENERATING_SCENE',
} as const;

export type SseSetupGenerateProgressCodeValue =
  (typeof SseSetupGenerateProgressCode)[keyof typeof SseSetupGenerateProgressCode];
