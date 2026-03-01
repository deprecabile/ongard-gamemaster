import { create } from 'zustand';

import { gameRaceService } from '@/api/gameRaceService';
import type { GameRace } from '@/contract/gameRace';

interface ConfigState {
  races: GameRace[] | null;
  loadRaces: () => Promise<GameRace[]>;
}

export const useConfigStore = create<ConfigState>()((set, get) => ({
  races: null,

  loadRaces: async () => {
    const cached = get().races;
    if (cached) return cached;

    const races = await gameRaceService.getRaces();
    set({ races });
    return races;
  },
}));
