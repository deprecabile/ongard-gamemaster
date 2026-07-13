import apiClient from '@/api/apiClient';
import type { CampaignArchetype } from '@/contract/campaignArchetype';
import type { ChatEntryDto, SetupSessionFormData } from '@/contract/setupSession';

const SESSION_BASE = '/chat/campaign/setup/session';

export const campaignSetupService = {
  getArchetypes: async (): Promise<CampaignArchetype[]> => {
    const { data } = await apiClient.get<CampaignArchetype[]>('/chat/campaign/setup/archetypes');
    return data;
  },

  getSessionStatus: async (): Promise<{ active: boolean }> => {
    const { data } = await apiClient.get<{ active: boolean }>(`${SESSION_BASE}/status`);
    return data;
  },

  getSession: async (): Promise<SetupSessionFormData | null> => {
    const { data, status } = await apiClient.get<SetupSessionFormData>(SESSION_BASE, {
      validateStatus: (s) => s === 200 || s === 204,
    });
    return status === 204 ? null : data;
  },

  getSessionHistory: async (): Promise<ChatEntryDto[]> => {
    const { data } = await apiClient.get<ChatEntryDto[]>(`${SESSION_BASE}/history`);
    return data;
  },

  deleteSessionHistory: async (): Promise<void> => {
    await apiClient.delete(`${SESSION_BASE}/history`);
  },

  updateCharacterPrompt: async (value: string): Promise<void> => {
    await apiClient.put(`${SESSION_BASE}/character-prompt`, { value });
  },

  updateStartingSituation: async (value: string): Promise<void> => {
    await apiClient.put(`${SESSION_BASE}/starting-situation`, { value });
  },

  updateRaceCode: async (value: string): Promise<void> => {
    await apiClient.put(`${SESSION_BASE}/race-code`, { value });
  },

  updateCharacterName: async (value: string): Promise<void> => {
    await apiClient.put(`${SESSION_BASE}/character-name`, { value });
  },
};
