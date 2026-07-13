import { useServiceReadyStore } from '@/store/useServiceReadyStore';

vi.mock('@/api/authService', () => ({
  authService: { refreshToken: vi.fn() },
}));

vi.mock('@/store/useAuthStore', () => ({
  useAuthStore: {
    getState: vi.fn(() => ({
      accessToken: 'test-token',
      username: null,
      refreshToken: null,
      logout: vi.fn(),
    })),
  },
}));

import apiClient from '@/api/apiClient';

describe('apiClient 503 interceptor', () => {
  beforeEach(() => {
    useServiceReadyStore.setState({ serviceUnavailable: false });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('marks store unavailable on 503 response', async () => {
    const scope = await import('axios');
    const mockAdapter = vi.fn().mockRejectedValue(
      new scope.AxiosError('Service Unavailable', 'ERR_BAD_RESPONSE', undefined, undefined, {
        status: 503,
        data: {},
        statusText: 'Service Unavailable',
        headers: {},
        config: {} as never,
      }),
    );
    apiClient.defaults.adapter = mockAdapter;

    await expect(apiClient.get('/test')).rejects.toThrow();
    expect(useServiceReadyStore.getState().serviceUnavailable).toBe(true);
  });

  it('does not mark unavailable on 500 response', async () => {
    const scope = await import('axios');
    const mockAdapter = vi.fn().mockRejectedValue(
      new scope.AxiosError('Internal Server Error', 'ERR_BAD_RESPONSE', undefined, undefined, {
        status: 500,
        data: {},
        statusText: 'Internal Server Error',
        headers: {},
        config: {} as never,
      }),
    );
    apiClient.defaults.adapter = mockAdapter;

    await expect(apiClient.get('/test')).rejects.toThrow();
    expect(useServiceReadyStore.getState().serviceUnavailable).toBe(false);
  });
});
