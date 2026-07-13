export const SseSetupGenerateEventType = {
  STARTED: 'setup_generate_started',
  PROGRESS: 'setup_generate_progress',
  NAME_PICKED: 'setup_generate_name_picked',
  CHARACTER_GENERATED: 'setup_generate_character_generated',
  COMPLETED: 'setup_generate_completed',
  ERROR: 'setup_generate_error',
} as const;

export type SseSetupGenerateEventTypeValue =
  (typeof SseSetupGenerateEventType)[keyof typeof SseSetupGenerateEventType];
