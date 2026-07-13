import type { Inventory } from '@/contract/inventory';

export interface GameScene {
  currentLocation: string;
  gameDate: string;
  gameTime: string;
  meteo: string;
  temperature: string;
}

export interface CampaignQuestLog {
  turnNumber: number;
  questActive: string;
  questCompleted: string;
}

export interface CampaignListItem {
  characterHash: string;
  characterName: string;
  raceCode: string;
  currentTurn: number;
  currentLocation: string | null;
  lastUpdate: string;
  narrativePreview: string;
}

export interface ChatEntry {
  turnNumber: number;
  userMessage: string;
  gmResponse: string;
}

export interface CampaignTurnResponse {
  characterHash: string;
  currentTurn: number;
  inventory: Inventory;
  questLog: CampaignQuestLog | null;
  scene: GameScene | null;
  lastUpdate: string;
}
