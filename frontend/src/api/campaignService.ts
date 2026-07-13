import apiClient from '@/api/apiClient';
import { fetchWithAuth, handleSseHttpError } from '@/api/sseUtils';
import type { AdvisorLogEntry } from '@/contract/advisorLog';
import type { CampaignListItem, CampaignTurnResponse, ChatEntry } from '@/contract/campaign';
import type { ChatModeType } from '@/contract/chatMode';
import { SseEventType } from '@/contract/sseEventType';
import type { SseProgressCodeValue } from '@/contract/sseProgressCode';
import { getSseMessage } from '@/utils/sseMessages';

export const campaignService = {
  hasCampaigns: async (): Promise<boolean> => {
    const { data } = await apiClient.get<boolean>('/chat/campaign/exists');
    return data;
  },

  fetchTurn: async (characterHash: string): Promise<CampaignTurnResponse | null> => {
    const response = await apiClient.get<CampaignTurnResponse>('/chat/campaign/turn', {
      params: { characterHash },
    });
    return response.status === 204 ? null : response.data;
  },

  fetchCampaignList: async (): Promise<CampaignListItem[]> => {
    const { data } = await apiClient.get<CampaignListItem[]>('/chat/campaign/list');
    return data;
  },

  fetchHistory: async (characterHash: string): Promise<ChatEntry[]> => {
    const { data } = await apiClient.get<ChatEntry[]>('/chat/campaign/history', {
      params: { characterHash },
    });
    return data;
  },

  fetchQuestActive: async (characterHash: string): Promise<string | null> => {
    const { data } = await apiClient.get<string>('/chat/campaign/quest-active', {
      params: { characterHash },
    });
    return data || null;
  },

  deleteCampaign: async (characterHash: string): Promise<void> => {
    await apiClient.delete(`/chat/campaign/${characterHash}`);
  },

  fetchAdvisorLog: async (characterHash: string): Promise<AdvisorLogEntry[]> => {
    const { data } = await apiClient.get<AdvisorLogEntry[]>('/chat/campaign/advisor-log', {
      params: { characterHash },
    });
    return data;
  },

  endSession: async (characterHash: string): Promise<void> => {
    await apiClient.post('/chat/campaign/session/end', null, {
      params: { characterHash },
    });
  },

  fetchPlayerNotes: async (characterHash: string): Promise<string> => {
    const { data } = await apiClient.get<string>('/chat/campaign/playerNotes', {
      params: { characterHash },
    });
    return data;
  },

  updatePlayerNotes: async (characterHash: string, newNotesSnapshot: string): Promise<void> => {
    await apiClient.put('/chat/campaign/playerNotes', { characterHash, newNotesSnapshot });
  },
};

export interface CampaignSseCallbacks {
  onStarted: () => void;
  onProgress: (message: string) => void;
  onCompleted: (gmOutput: string, inconsistencyDetected: boolean) => void;
  onError: (errorCode: string, description: string) => void;
  onAdvisorCompleted?: (advisorOutput: string) => void;
}

function parseSseEvents(chunk: string, buffer: string, callbacks: CampaignSseCallbacks): string {
  buffer += chunk;
  const parts = buffer.split('\n\n');
  buffer = parts.pop() ?? '';

  for (const part of parts) {
    let eventName = '';
    let data = '';

    for (const line of part.split('\n')) {
      if (line.startsWith('event:')) {
        eventName = line.slice('event:'.length).trim();
      } else if (line.startsWith('data:')) {
        data = line.slice('data:'.length).trim();
      }
    }

    if (!eventName || !data) continue;

    const parsed = JSON.parse(data) as Record<string, unknown>;

    switch (eventName) {
      case SseEventType.STARTED:
        callbacks.onStarted();
        break;
      case SseEventType.PROGRESS:
        callbacks.onProgress(getSseMessage(parsed.code as SseProgressCodeValue));
        break;
      case SseEventType.COMPLETED:
        callbacks.onCompleted(parsed.gmOutput as string, parsed.inconsistencyDetected === true);
        break;
      case SseEventType.ERROR:
        callbacks.onError(parsed.errorCode as string, parsed.description as string);
        break;
      case SseEventType.ADVISOR_STARTED:
        callbacks.onStarted();
        break;
      case SseEventType.ADVISOR_THINKING:
        callbacks.onProgress(getSseMessage(parsed.code as SseProgressCodeValue));
        break;
      case SseEventType.ADVISOR_COMPLETED:
        callbacks.onAdvisorCompleted?.(parsed.advisorOutput as string);
        break;
      case SseEventType.ADVISOR_ERROR:
        callbacks.onError(parsed.errorCode as string, parsed.description as string);
        break;
    }
  }

  return buffer;
}

export function streamInteraction(
  characterHash: string,
  message: string,
  mode: ChatModeType,
  callbacks: CampaignSseCallbacks,
): AbortController {
  const controller = new AbortController();

  fetchWithAuth('/api/chat/sse/interaction', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ characterHash, message, mode }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        handleSseHttpError(response.status);
        callbacks.onError(`HTTP_${String(response.status)}`, response.statusText);
        return;
      }

      const reader = response.body?.getReader();
      if (!reader) {
        callbacks.onError('NO_STREAM', 'Response body is not readable');
        return;
      }

      const decoder = new TextDecoder();
      let buffer = '';

      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer = parseSseEvents(decoder.decode(value, { stream: true }), buffer, callbacks);
      }
    })
    .catch((err: unknown) => {
      if (err instanceof DOMException && err.name === 'AbortError') return;
      const msg = err instanceof Error ? err.message : 'Unknown error';
      callbacks.onError('FETCH_ERROR', msg);
    });

  return controller;
}
