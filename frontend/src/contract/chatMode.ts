export const ChatMode = {
  ASK: 'ask',
  ACTION: 'action',
} as const;

export type ChatModeType = (typeof ChatMode)[keyof typeof ChatMode];
