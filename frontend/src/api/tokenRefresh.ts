import { authService } from '@/api/authService';
import { useAuthStore } from '@/store/useAuthStore';

let pendingRefresh: Promise<string> | null = null;

export function refreshAccessToken(): Promise<string> {
  if (pendingRefresh) {
    return pendingRefresh;
  }

  pendingRefresh = performRefresh().finally(() => {
    pendingRefresh = null;
  });

  return pendingRefresh;
}

async function performRefresh(): Promise<string> {
  const { username, refreshToken, setTokens, logout } = useAuthStore.getState();

  if (!username || !refreshToken) {
    logout();
    throw new Error('No refresh credentials');
  }

  try {
    const response = await authService.refreshToken(username, refreshToken);
    setTokens(response.accessToken, response.refreshToken, response.expiresIn);
    return response.accessToken;
  } catch (error) {
    logout();
    throw error;
  }
}
