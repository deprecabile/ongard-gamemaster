export interface SetupSessionFormData {
  raceCode: string | null;
  characterName: string | null;
  characterPrompt: string | null;
  startingSituation: string | null;
  archetypeCode: string | null;
}

export interface ChatEntryDto {
  turnNumber: number;
  userMessage: string;
  gmResponse: string;
}
