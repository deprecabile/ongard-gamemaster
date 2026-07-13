import axios from 'axios';
import i18n from 'i18next';

import { refreshAccessToken } from '@/api/tokenRefresh';
import { useAuthStore } from '@/store/useAuthStore';
import { useServiceReadyStore } from '@/store/useServiceReadyStore';

const apiClient = axios.create({
  baseURL: '/api',
});

apiClient.interceptors.request.use((config) => {
  config.headers['Accept-Language'] = i18n.language;
  const { accessToken } = useAuthStore.getState();
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 503) {
      useServiceReadyStore.getState().markUnavailable();
    }

    if (!axios.isAxiosError(error) || error.response?.status !== 401) {
      return Promise.reject(error instanceof Error ? error : new Error('Request failed'));
    }

    const originalRequest = error.config;
    if (!originalRequest) {
      return Promise.reject(error);
    }

    try {
      const newToken = await refreshAccessToken();
      originalRequest.headers.Authorization = `Bearer ${newToken}`;
      return await apiClient(originalRequest);
    } catch (refreshError) {
      return Promise.reject(
        refreshError instanceof Error ? refreshError : new Error('Refresh failed'),
      );
    }
  },
);

export default apiClient;
