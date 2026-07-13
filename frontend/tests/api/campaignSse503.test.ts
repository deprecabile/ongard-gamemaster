import { useServiceReadyStore } from '@/store/useServiceReadyStore';

const { mockGetState } = vi.hoisted(() => ({
  mockGetState: vi.fn(() => ({ accessToken: 'test-token' })),
}));

vi.mock('@/store/useAuthStore', () => ({
  useAuthStore: { getState: mockGetState },
}));

import { streamInitCampaign } from '@/api/campaignInitSseService';
import { streamInteraction } from '@/api/campaignService';
import { ChatMode } from '@/contract/chatMode';

function createCallbacks() {
  return {
    onStarted: vi.fn(),
    onProgress: vi.fn(),
    onCompleted: vi.fn(),
    onError: vi.fn(),
  };
}

describe('SSE 503 → service unavailable store', () => {
  beforeEach(() => {
    useServiceReadyStore.setState({ serviceUnavailable: false });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('streamInteraction marks store unavailable on 503', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 503,
        statusText: 'Service Unavailable',
      } as Response),
    );

    const cb = createCallbacks();
    const done = new Promise<void>((resolve) => {
      cb.onError.mockImplementation(() => {
        resolve();
      });
    });

    streamInteraction('hash', 'test', ChatMode.ACTION, cb);
    await done;

    expect(useServiceReadyStore.getState().serviceUnavailable).toBe(true);
    expect(cb.onError).toHaveBeenCalledWith('HTTP_503', 'Service Unavailable');
  });

  it('streamInteraction does not mark unavailable on 500', async () => {
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

    streamInteraction('hash', 'test', ChatMode.ACTION, cb);
    await done;

    expect(useServiceReadyStore.getState().serviceUnavailable).toBe(false);
  });

  it('streamInitCampaign marks store unavailable on 503', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 503,
        statusText: 'Service Unavailable',
      } as Response),
    );

    const cb = createCallbacks();
    const done = new Promise<void>((resolve) => {
      cb.onError.mockImplementation(() => {
        resolve();
      });
    });

    streamInitCampaign('hash', 'context', cb);
    await done;

    expect(useServiceReadyStore.getState().serviceUnavailable).toBe(true);
    expect(cb.onError).toHaveBeenCalledWith('HTTP_503', 'Service Unavailable');
  });
});
