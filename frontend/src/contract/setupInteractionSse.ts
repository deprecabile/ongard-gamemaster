export interface SetupInteractionRequest {
  message: string;
  characterPrompt?: string;
  startingSituation?: string;
  raceCode?: string;
  characterName?: string;
}

export interface SetupInteractionCompletedPayload {
  tms: string;
  advisorOutput: string;
}

export interface SetupInteractionSseCallbacks {
  onStarted: () => void;
  onThinking: () => void;
  onCompleted: (payload: SetupInteractionCompletedPayload) => void;
  onError: (errorCode: string, description: string) => void;
}
