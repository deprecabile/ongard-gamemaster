import apiClient from '@/api/apiClient';
import type { PlayerCharacter, PlayerCharacterSaveRequest } from '@/contract/playerCharacter';

export const characterService = {
  getAll: async (): Promise<PlayerCharacter[]> => {
    const { data } = await apiClient.get<PlayerCharacter[]>('/chat/character/all');
    return data;
  },

  getByHash: async (characterHash: string): Promise<PlayerCharacter | null> => {
    const response = await apiClient.get<PlayerCharacter>(`/chat/character/${characterHash}`);
    return response.status === 204 ? null : response.data;
  },

  create: async (request: PlayerCharacterSaveRequest): Promise<PlayerCharacter> => {
    const { data } = await apiClient.post<PlayerCharacter>('/chat/character', request);
    return data;
  },
};
