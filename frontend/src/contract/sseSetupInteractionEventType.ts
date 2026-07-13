export const SseSetupInteractionEventType = {
  STARTED: 'setup_interaction_started',
  THINKING: 'setup_interaction_thinking',
  COMPLETED: 'setup_interaction_completed',
  ERROR: 'setup_interaction_error',
} as const;

export type SseSetupInteractionEventTypeValue =
  (typeof SseSetupInteractionEventType)[keyof typeof SseSetupInteractionEventType];
