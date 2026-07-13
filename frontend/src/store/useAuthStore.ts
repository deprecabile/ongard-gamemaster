import { create } from 'zustand';

import { authService } from '@/api/authService';
import type { LoginResponse } from '@/types/api';
import { parseJwtUsername } from '@/utils/jwtUtils';

const STORAGE_KEYS = {
  ACCESS_TOKEN: 'accessToken',
  REFRESH_TOKEN: 'refreshToken',
  USERNAME: 'username',
} as const;

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  username: string | null;
  expiresIn: number | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  loginWithTokens: (response: LoginResponse) => void;
  logout: () => void;
  setTokens: (accessToken: string, refreshToken: string, expiresIn: number) => void;
  initialize: () => void;
}

export const useAuthStore = create<AuthState>()((set, get) => ({
  accessToken: null,
  refreshToken: null,
  username: null,
  expiresIn: null,
  isAuthenticated: false,

  login: async (username: string, password: string) => {
    const response = await authService.login(username, password);
    get().loginWithTokens(response);
  },

  loginWithTokens: (response: LoginResponse) => {
    const username = parseJwtUsername(response.accessToken);
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, response.accessToken);
    localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, response.refreshToken);
    localStorage.setItem(STORAGE_KEYS.USERNAME, username);
    set({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      username,
      expiresIn: response.expiresIn,
      isAuthenticated: true,
    });
  },

  logout: () => {
    localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN);
    localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
    localStorage.removeItem(STORAGE_KEYS.USERNAME);
    set({
      accessToken: null,
      refreshToken: null,
      username: null,
      expiresIn: null,
      isAuthenticated: false,
    });
  },

  setTokens: (accessToken: string, refreshToken: string, expiresIn: number) => {
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken);
    localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken);
    set({ accessToken, refreshToken, expiresIn, isAuthenticated: true });
  },

  initialize: () => {
    const accessToken = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
    const refreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN);
    const username = localStorage.getItem(STORAGE_KEYS.USERNAME);
    if (accessToken && refreshToken && username) {
      set({
        accessToken,
        refreshToken,
        username,
        isAuthenticated: true,
      });
    }
  },
}));
