import i18n from 'i18next';

import { refreshAccessToken } from '@/api/tokenRefresh';
import { useAuthStore } from '@/store/useAuthStore';
import { useServiceReadyStore } from '@/store/useServiceReadyStore';

export function handleSseHttpError(status: number): void {
  if (status === 503) {
    useServiceReadyStore.getState().markUnavailable();
  }
}

export async function fetchWithAuth(url: string, init: RequestInit): Promise<Response> {
  const { accessToken } = useAuthStore.getState();
  const headers = new Headers(init.headers);
  headers.set('Accept-Language', i18n.language);
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  const response = await fetch(url, { ...init, headers });

  if (response.status !== 401) {
    return response;
  }

  try {
    const newToken = await refreshAccessToken();
    headers.set('Authorization', `Bearer ${newToken}`);
    return await fetch(url, { ...init, headers });
  } catch {
    return response;
  }
}
