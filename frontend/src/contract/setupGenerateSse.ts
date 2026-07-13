export type SetupGenerateMode = 'FULL' | 'CHARACTER' | 'SCENE';

export interface SetupGenerateRequest {
  mode: SetupGenerateMode;
  archetypeCode: string;
  characterPrompt?: string;
  raceCode?: string;
  characterName?: string;
}

export interface SetupGenerateCompletedPayload {
  characterPrompt: string | null;
  startingSituation: string | null;
  raceCode: string | null;
  characterName: string | null;
}

export interface SetupGenerateSseCallbacks {
  onStarted: () => void;
  onProgress: (message: string) => void;
  onNamePicked: (raceCode: string, characterName: string) => void;
  onCharacterGenerated: (characterPrompt: string) => void;
  onCompleted: (payload: SetupGenerateCompletedPayload) => void;
  onError: (errorCode: string, description: string) => void;
}
