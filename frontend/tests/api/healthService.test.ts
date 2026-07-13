import { healthService } from '@/api/healthService';

describe('healthService.checkRagReady', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns true when backend reports available: true', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ available: true }),
      }),
    );

    expect(await healthService.checkRagReady()).toBe(true);
    expect(fetch).toHaveBeenCalledWith('/api/chat/health');
  });

  it('returns false when backend reports available: false', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ available: false }),
      }),
    );

    expect(await healthService.checkRagReady()).toBe(false);
  });

  it('returns false on non-ok response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }));

    expect(await healthService.checkRagReady()).toBe(false);
  });

  it('returns false on network error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    expect(await healthService.checkRagReady()).toBe(false);
  });
});
