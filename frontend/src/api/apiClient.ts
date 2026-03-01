import axios from 'axios';

import { authService } from '@/api/authService';
import { useAuthStore } from '@/store/useAuthStore';

const apiClient = axios.create({
  baseURL: '/api',
});

apiClient.interceptors.request.use((config) => {
  const { accessToken } = useAuthStore.getState();
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

let isRefreshing = false;
let failedQueue: {
  resolve: (token: string) => void;
  reject: (error: Error) => void;
}[] = [];

const processQueue = (error: Error | null, token: string | null) => {
  for (const promise of failedQueue) {
    if (token) {
      promise.resolve(token);
    } else if (error) {
      promise.reject(error);
    }
  }
  failedQueue = [];
};

apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error) || error.response?.status !== 401) {
      return Promise.reject(error instanceof Error ? error : new Error('Request failed'));
    }

    const originalRequest = error.config;
    if (!originalRequest) {
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise<string>((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      }).then((token) => {
        originalRequest.headers.Authorization = `Bearer ${token}`;
        return apiClient(originalRequest);
      });
    }

    isRefreshing = true;
    const { username, refreshToken, setTokens, logout } = useAuthStore.getState();

    if (!username || !refreshToken) {
      isRefreshing = false;
      logout();
      return Promise.reject(error);
    }

    try {
      const response = await authService.refreshToken(username, refreshToken);
      setTokens(response.accessToken, response.refreshToken, response.expiresIn);
      processQueue(null, response.accessToken);
      originalRequest.headers.Authorization = `Bearer ${response.accessToken}`;
      return await apiClient(originalRequest);
    } catch (refreshError) {
      const err = refreshError instanceof Error ? refreshError : new Error('Refresh failed');
      processQueue(err, null);
      logout();
      return await Promise.reject(err);
    } finally {
      isRefreshing = false;
    }
  },
);

export default apiClient;
