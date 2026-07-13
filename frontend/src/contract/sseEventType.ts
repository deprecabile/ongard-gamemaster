export const SseEventType = {
  STARTED: 'started',
  PROGRESS: 'progress',
  COMPLETED: 'completed',
  ERROR: 'error',
  ADVISOR_STARTED: 'advisor_started',
  ADVISOR_THINKING: 'advisor_thinking',
  ADVISOR_COMPLETED: 'advisor_completed',
  ADVISOR_ERROR: 'advisor_error',
} as const;

export type SseEventTypeValue = (typeof SseEventType)[keyof typeof SseEventType];
