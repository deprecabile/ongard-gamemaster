import { fetchWithAuth, handleSseHttpError } from '@/api/sseUtils';
import type {
  SetupInteractionCompletedPayload,
  SetupInteractionRequest,
  SetupInteractionSseCallbacks,
} from '@/contract/setupInteractionSse';
import { SseSetupInteractionEventType } from '@/contract/sseSetupInteractionEventType';

function parseSseEvents(
  chunk: string,
  buffer: string,
  callbacks: SetupInteractionSseCallbacks,
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
      case SseSetupInteractionEventType.STARTED:
        callbacks.onStarted();
        break;
      case SseSetupInteractionEventType.THINKING:
        callbacks.onThinking();
        break;
      case SseSetupInteractionEventType.COMPLETED:
        callbacks.onCompleted(parsed as unknown as SetupInteractionCompletedPayload);
        break;
      case SseSetupInteractionEventType.ERROR:
        callbacks.onError(parsed.errorCode as string, parsed.description as string);
        break;
    }
  }

  return buffer;
}

export function streamSetupInteraction(
  request: SetupInteractionRequest,
  callbacks: SetupInteractionSseCallbacks,
): AbortController {
  const controller = new AbortController();

  fetchWithAuth('/api/chat/sse/campaign/setup/interaction', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
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
