import mockRaces from '@fixtures/gameRaceResponse.json';
import type { AxiosResponse } from 'axios';

import type { GameRace } from '@/contract/gameRace';

const { mockGet } = vi.hoisted(() => ({
  mockGet: vi.fn<(url: string) => Promise<AxiosResponse<GameRace[]>>>(),
}));

vi.mock('@/api/apiClient', () => ({
  default: {
    get: mockGet,
  },
}));

import { gameRaceService } from '@/api/gameRaceService';

describe('gameRaceService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls GET /chat/config/races and returns the races array', async () => {
    mockGet.mockResolvedValue({ data: mockRaces } as Awaited<ReturnType<typeof mockGet>>);

    const result = await gameRaceService.getRaces();

    expect(mockGet).toHaveBeenCalledWith('/chat/config/races');
    const expectedMap = new Map<string, GameRace>(
      (mockRaces as unknown as GameRace[]).map((r) => [r.code, r])
    );
    expect(result).toEqual(expectedMap);
  });
});
