import mockRaces from '@fixtures/gameRaceResponse.json';

import { gameRaceService } from '@/api/gameRaceService';
import type { GameRace } from '@/contract/gameRace';
import { useConfigStore } from '@/store/useConfigStore';

vi.mock('@/api/gameRaceService', () => ({
  gameRaceService: {
    getRaces: vi.fn(),
  },
}));

describe('useConfigStore', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useConfigStore.setState({ races: new Map() });
  });

  it('has empty map as initial state', () => {
    const races = useConfigStore.getState().races;
    expect(races).toBeInstanceOf(Map);
    expect(races.size).toBe(0);
  });

  const mockRacesMap = new Map<string, GameRace>(
    (mockRaces as unknown as GameRace[]).map((r) => [r.code, r]),
  );

  it('fetches races on first loadRaces call', async () => {
    vi.mocked(gameRaceService.getRaces).mockResolvedValue(mockRacesMap);

    const result = await useConfigStore.getState().loadRaces();

    expect(gameRaceService.getRaces).toHaveBeenCalledOnce();
    expect(result).toEqual(mockRacesMap);
    expect(useConfigStore.getState().races).toEqual(mockRacesMap);
  });

  it('returns cached races without fetching on subsequent calls', async () => {
    vi.mocked(gameRaceService.getRaces).mockResolvedValue(mockRacesMap);

    await useConfigStore.getState().loadRaces();
    const result = await useConfigStore.getState().loadRaces();

    expect(gameRaceService.getRaces).toHaveBeenCalledOnce();
    expect(result).toEqual(mockRacesMap);
  });

  it('returns races with correct structure from real backend response', async () => {
    vi.mocked(gameRaceService.getRaces).mockResolvedValue(mockRacesMap);

    const races = await useConfigStore.getState().loadRaces();

    expect(races.size).toBe(9);

    const troll = races.get('TRO');
    if (!troll) throw new Error('Expected first race');
    expect(troll.code).toBe('TRO');
    expect(troll.name).toBe('Troll');
    expect(troll.description).toContain('Imponenti e rigenerativi');
    expect(troll.attributes.minHeight).toBe(200.0);
    expect(troll.attributes.maxHeight).toBe(280.0);
    expect(troll.attributes.favoriteBiomes).toEqual(['paludi', 'caverne', 'foreste oscure']);

    const codes = Array.from(races.keys());
    expect(codes).toEqual(['TRO', 'UMN', 'XER', 'ORC', 'NAN', 'ELF', 'RET', 'GOB', 'GNO']);
  });
});
