import { ChatMode } from '@/contract/chatMode';

const { mockGetState } = vi.hoisted(() => ({
  mockGetState: vi.fn(() => ({ accessToken: 'test-token' })),
}));

vi.mock('@/store/useAuthStore', () => ({
  useAuthStore: { getState: mockGetState },
}));

import { streamInteraction } from '@/api/campaignService';

function mockFetchWithSse(events: { event: string; data: object }[]) {
  const encoder = new TextEncoder();
  const text = events.map((e) => `event:${e.event}\ndata:${JSON.stringify(e.data)}\n\n`).join('');
  const encoded = encoder.encode(text);

  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: true,
      body: {
        getReader: () => {
          let done = false;
          return {
            read: () => {
              if (done) return Promise.resolve({ done: true, value: undefined });
              done = true;
              return Promise.resolve({ done: false, value: encoded });
            },
          };
        },
      },
    } as unknown as Response),
  );
}

function createCallbacks() {
  return {
    onStarted: vi.fn(),
    onProgress: vi.fn(),
    onCompleted: vi.fn(),
    onError: vi.fn(),
  };
}

describe('campaignService', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('streamInteraction — completed event', () => {
    it('passes inconsistencyDetected=false when validation succeeded', async () => {
      mockFetchWithSse([
        { event: 'started', data: { active: true } },
        {
          event: 'completed',
          data: { gmOutput: 'The orc charges!', inconsistencyDetected: false },
        },
      ]);

      const cb = createCallbacks();
      const done = new Promise<void>((resolve) => {
        cb.onCompleted.mockImplementation(() => {
          resolve();
        });
      });

      streamInteraction('hash123', 'I attack', ChatMode.ACTION, cb);
      await done;

      expect(cb.onCompleted).toHaveBeenCalledWith('The orc charges!', false);
    });

    it('passes inconsistencyDetected=true when validation failed', async () => {
      mockFetchWithSse([
        { event: 'started', data: { active: true } },
        {
          event: 'completed',
          data: { gmOutput: 'Draft with issues', inconsistencyDetected: true },
        },
      ]);

      const cb = createCallbacks();
      const done = new Promise<void>((resolve) => {
        cb.onCompleted.mockImplementation(() => {
          resolve();
        });
      });

      streamInteraction('hash123', 'I attack', ChatMode.ACTION, cb);
      await done;

      expect(cb.onCompleted).toHaveBeenCalledWith('Draft with issues', true);
    });

    it('defaults inconsistencyDetected to undefined when field is missing', async () => {
      mockFetchWithSse([
        { event: 'started', data: { active: true } },
        { event: 'completed', data: { gmOutput: 'Normal response' } },
      ]);

      const cb = createCallbacks();
      const done = new Promise<void>((resolve) => {
        cb.onCompleted.mockImplementation(() => {
          resolve();
        });
      });

      streamInteraction('hash123', 'I attack', ChatMode.ACTION, cb);
      await done;

      expect(cb.onCompleted).toHaveBeenCalledWith('Normal response', false);
    });
  });

  describe('streamInteraction — other events', () => {
    it('calls onStarted when started event is received', async () => {
      mockFetchWithSse([{ event: 'started', data: { active: true } }]);

      const cb = createCallbacks();
      const done = new Promise<void>((resolve) => {
        cb.onStarted.mockImplementation(() => {
          resolve();
        });
      });

      streamInteraction('hash123', 'I attack', ChatMode.ACTION, cb);
      await done;

      expect(cb.onStarted).toHaveBeenCalledOnce();
    });

    it('calls onProgress with translated message from code', async () => {
      mockFetchWithSse([
        { event: 'started', data: { active: true } },
        { event: 'progress', data: { code: 'CAMPAIGN_PREPARING' } },
      ]);

      const cb = createCallbacks();
      const done = new Promise<void>((resolve) => {
        cb.onProgress.mockImplementation(() => {
          resolve();
        });
      });

      streamInteraction('hash123', 'I attack', ChatMode.ACTION, cb);
      await done;

      expect(cb.onProgress).toHaveBeenCalledWith('Preparing the campaign...');
    });

    it('calls onError on HTTP error response', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue({
          ok: false,
          status: 500,
          statusText: 'Internal Server Error',
        } as Response),
      );

      const cb = createCallbacks();
      const done = new Promise<void>((resolve) => {
        cb.onError.mockImplementation(() => {
          resolve();
        });
      });

      streamInteraction('hash123', 'I attack', ChatMode.ACTION, cb);
      await done;

      expect(cb.onError).toHaveBeenCalledWith('HTTP_500', 'Internal Server Error');
    });
  });

  describe('streamInteraction — request', () => {
    it('sends correct headers and body', async () => {
      mockFetchWithSse([{ event: 'started', data: { active: true } }]);

      const cb = createCallbacks();
      const done = new Promise<void>((resolve) => {
        cb.onStarted.mockImplementation(() => {
          resolve();
        });
      });

      streamInteraction('myHash', 'I cast a spell', ChatMode.ASK, cb);
      await done;

      const fetchMock = vi.mocked(globalThis.fetch);
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/chat/sse/interaction',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ characterHash: 'myHash', message: 'I cast a spell', mode: 'ask' }),
        }),
      );

      const headers = fetchMock.mock.calls[0]?.[1]?.headers as Headers;
      expect(headers.get('Content-Type')).toBe('application/json');
      expect(headers.get('Authorization')).toBe('Bearer test-token');
    });

    it('sends Accept-Language header', async () => {
      mockFetchWithSse([{ event: 'started', data: { active: true } }]);

      const cb = createCallbacks();
      const done = new Promise<void>((resolve) => {
        cb.onStarted.mockImplementation(() => {
          resolve();
        });
      });

      streamInteraction('hash123', 'I attack', ChatMode.ACTION, cb);
      await done;

      const fetchMock = vi.mocked(globalThis.fetch);
      const headers = fetchMock.mock.calls[0]?.[1]?.headers as Headers;
      expect(headers.get('Accept-Language')).toBe('en');
    });
  });
});
