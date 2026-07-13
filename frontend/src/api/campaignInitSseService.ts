import { fetchWithAuth, handleSseHttpError } from '@/api/sseUtils';
import type { InitCampaignSseCallbacks } from '@/contract/campaignInitSse';
import { SseEventType } from '@/contract/sseEventType';
import type { SseProgressCodeValue } from '@/contract/sseProgressCode';
import { getSseMessage } from '@/utils/sseMessages';

function parseSseEvents(
  chunk: string,
  buffer: string,
  callbacks: InitCampaignSseCallbacks,
): string {
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
        callbacks.onCompleted();
        break;
      case SseEventType.ERROR:
        callbacks.onError(parsed.errorCode as string, parsed.description as string);
        break;
    }
  }

  return buffer;
}

export function streamInitCampaign(
  characterHash: string,
  initialContext: string,
  callbacks: InitCampaignSseCallbacks,
): AbortController {
  const controller = new AbortController();

  fetchWithAuth('/api/chat/sse/campaign/init', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ characterHash, initialContext }),
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
