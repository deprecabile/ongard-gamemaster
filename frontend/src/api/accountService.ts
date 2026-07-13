import apiClient from '@/api/apiClient';
import type { TokenUsageOverview, UpdateLimitsRequest } from '@/contract/tokenUsage';

export const accountService = {
  fetchTokenUsage: async (): Promise<TokenUsageOverview> => {
    const { data } = await apiClient.get<TokenUsageOverview>('/account/token-usage');
    return data;
  },

  updateTokenLimits: async (request: UpdateLimitsRequest): Promise<void> => {
    await apiClient.put('/account/token-usage/limits', request);
  },
};
