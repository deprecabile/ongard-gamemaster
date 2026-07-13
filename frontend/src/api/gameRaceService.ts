import apiClient from '@/api/apiClient';
import type { GameRace } from '@/contract/gameRace';

export const gameRaceService = {
  getRaces: async (): Promise<Map<string, GameRace>> => {
    const { data } = await apiClient.get<GameRace[]>('/chat/config/races');
    const racesMap = new Map<string, GameRace>();
    data.forEach((r) => racesMap.set(r.code, r));
    return racesMap;
  },
};
