import { fetchWithAuth, handleSseHttpError } from '@/api/sseUtils';
import type {
  SetupGenerateCompletedPayload,
  SetupGenerateRequest,
  SetupGenerateSseCallbacks,
} from '@/contract/setupGenerateSse';
import { SseSetupGenerateEventType } from '@/contract/sseSetupGenerateEventType';
import type { SseSetupGenerateProgressCodeValue } from '@/contract/sseSetupGenerateProgressCode';
import { getSseMessage } from '@/utils/sseMessages';

function parseSseEvents(
  chunk: string,
  buffer: string,
  callbacks: SetupGenerateSseCallbacks,
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
      case SseSetupGenerateEventType.STARTED:
        callbacks.onStarted();
        break;
      case SseSetupGenerateEventType.PROGRESS:
        callbacks.onProgress(getSseMessage(parsed.code as SseSetupGenerateProgressCodeValue));
        break;
      case SseSetupGenerateEventType.NAME_PICKED:
        callbacks.onNamePicked(parsed.raceCode as string, parsed.characterName as string);
        break;
      case SseSetupGenerateEventType.CHARACTER_GENERATED:
        callbacks.onCharacterGenerated(parsed.characterPrompt as string);
        break;
      case SseSetupGenerateEventType.COMPLETED:
        callbacks.onCompleted(parsed as unknown as SetupGenerateCompletedPayload);
        break;
      case SseSetupGenerateEventType.ERROR:
        callbacks.onError(parsed.errorCode as string, parsed.description as string);
        break;
    }
  }

  return buffer;
}

export function streamSetupGenerate(
  request: SetupGenerateRequest,
  callbacks: SetupGenerateSseCallbacks,
): AbortController {
  const controller = new AbortController();

  fetchWithAuth('/api/chat/sse/campaign/setup/generate', {
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
