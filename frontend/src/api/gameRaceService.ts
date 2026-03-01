import apiClient from '@/api/apiClient';
import type { GameRace } from '@/contract/gameRace';

export const gameRaceService = {
  getRaces: async (): Promise<GameRace[]> => {
    const { data } = await apiClient.get<GameRace[]>('/chat/config/races');
    return data;
  },
};
