import mockRaces from '@fixtures/gameRaceResponse.json';

import { gameRaceService } from '@/api/gameRaceService';
import { useConfigStore } from '@/store/useConfigStore';

vi.mock('@/api/gameRaceService', () => ({
  gameRaceService: {
    getRaces: vi.fn(),
  },
}));

describe('useConfigStore', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useConfigStore.setState({ races: null });
  });

  it('has null races as initial state', () => {
    expect(useConfigStore.getState().races).toBeNull();
  });

  it('fetches races on first loadRaces call', async () => {
    vi.mocked(gameRaceService.getRaces).mockResolvedValue(mockRaces);

    const result = await useConfigStore.getState().loadRaces();

    expect(gameRaceService.getRaces).toHaveBeenCalledOnce();
    expect(result).toEqual(mockRaces);
    expect(useConfigStore.getState().races).toEqual(mockRaces);
  });

  it('returns cached races without fetching on subsequent calls', async () => {
    vi.mocked(gameRaceService.getRaces).mockResolvedValue(mockRaces);

    await useConfigStore.getState().loadRaces();
    const result = await useConfigStore.getState().loadRaces();

    expect(gameRaceService.getRaces).toHaveBeenCalledOnce();
    expect(result).toEqual(mockRaces);
  });

  it('returns races with correct structure from real backend response', async () => {
    vi.mocked(gameRaceService.getRaces).mockResolvedValue(mockRaces);

    const races = await useConfigStore.getState().loadRaces();

    expect(races).toHaveLength(9);

    const troll = races[0];
    if (!troll) throw new Error('Expected first race');
    expect(troll.code).toBe('TRO');
    expect(troll.name).toBe('Troll');
    expect(troll.description).toContain('Imponenti e rigenerativi');
    expect(troll.attributes.minHeight).toBe(200.0);
    expect(troll.attributes.maxHeight).toBe(280.0);
    expect(troll.attributes.favoriteBiomes).toEqual(['paludi', 'caverne', 'foreste oscure']);

    const codes = races.map((r) => r.code);
    expect(codes).toEqual(['TRO', 'UMN', 'XER', 'ORC', 'NAN', 'ELF', 'RET', 'GOB', 'GNO']);
  });
});
