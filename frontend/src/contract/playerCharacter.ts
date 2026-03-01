import type { GameRace } from '@/contract/gameRace';

export interface PlayerCharacter {
  characterHash: string;
  userHash: string;
  race: GameRace;
  name: string;
  description: string;
  created: string;
}

export interface PlayerCharacterSaveRequest {
  race: GameRace;
  name: string;
  description: string;
}
