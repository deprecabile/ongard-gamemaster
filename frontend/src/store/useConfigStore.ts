import { create } from 'zustand';

import { gameRaceService } from '@/api/gameRaceService';
import type { GameRace } from '@/contract/gameRace';

interface ConfigState {
  races: Map<string, GameRace>;
  loadRaces: () => Promise<Map<string, GameRace>>;
}

export const useConfigStore = create<ConfigState>()((set, get) => ({
  races: new Map(),

  loadRaces: async () => {
    const cached = get().races;
    if (cached.size > 0) return cached;

    const races = await gameRaceService.getRaces();
    set({ races });
    return races;
  },
}));
