import mockCharacters from '@fixtures/playerCharacterResponse.json';
import type { AxiosResponse } from 'axios';

import type { PlayerCharacter } from '@/contract/playerCharacter';

const { mockGet, mockPost } = vi.hoisted(() => ({
  mockGet: vi.fn<(url: string) => Promise<AxiosResponse>>(),
  mockPost: vi.fn<(url: string, data: unknown) => Promise<AxiosResponse>>(),
}));

vi.mock('@/api/apiClient', () => ({
  default: {
    get: mockGet,
    post: mockPost,
  },
}));

import { characterService } from '@/api/characterService';

describe('characterService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getAll', () => {
    it('calls GET /chat/character/all and returns the characters array', async () => {
      mockGet.mockResolvedValue({ data: mockCharacters } as AxiosResponse<PlayerCharacter[]>);

      const result = await characterService.getAll();

      expect(mockGet).toHaveBeenCalledWith('/chat/character/all');
      expect(result).toEqual(mockCharacters);
    });
  });

  describe('getByHash', () => {
    it('calls GET /chat/character/{hash} and returns the character', async () => {
      const character = mockCharacters[0]!;
      mockGet.mockResolvedValue({
        status: 200,
        data: character,
      } as AxiosResponse<PlayerCharacter>);

      const result = await characterService.getByHash(character.characterHash);

      expect(mockGet).toHaveBeenCalledWith(`/chat/character/${character.characterHash}`);
      expect(result).toEqual(character);
    });

    it('returns null when backend responds with 204', async () => {
      mockGet.mockResolvedValue({
        status: 204,
        data: '',
      } as AxiosResponse);

      const result = await characterService.getByHash('nonexistent');

      expect(mockGet).toHaveBeenCalledWith('/chat/character/nonexistent');
      expect(result).toBeNull();
    });
  });

  describe('create', () => {
    it('calls POST /chat/character and returns the created character', async () => {
      const character = mockCharacters[0]!;
      const request = {
        race: character.race,
        name: character.name,
        description: character.description,
      };

      mockPost.mockResolvedValue({ data: character } as AxiosResponse<PlayerCharacter>);

      const result = await characterService.create(request);

      expect(mockPost).toHaveBeenCalledWith('/chat/character', request);
      expect(result).toEqual(character);
    });
  });
});
