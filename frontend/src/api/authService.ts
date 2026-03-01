import axios from 'axios';

import type {
  CheckUsernameResponse,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
} from '@/types/api';

const authClient = axios.create({
  baseURL: '/api',
});

export const authService = {
  login: async (username: string, password: string): Promise<LoginResponse> => {
    const { data } = await authClient.post<LoginResponse>('/auth/login', {
      username,
      password,
    } satisfies LoginRequest);
    return data;
  },

  register: async (
    username: string,
    email: string,
    password: string,
  ): Promise<RegisterResponse> => {
    const { data } = await authClient.post<RegisterResponse>('/auth/register', {
      username,
      email,
      password,
    } satisfies RegisterRequest);
    return data;
  },

  checkUsername: async (username: string, signal?: AbortSignal): Promise<CheckUsernameResponse> => {
    const { data } = await authClient.get<CheckUsernameResponse>('/auth/check-username', {
      params: { username },
      signal,
    });
    return data;
  },

  refreshToken: async (username: string, refreshToken: string): Promise<LoginResponse> => {
    const { data } = await authClient.post<LoginResponse>('/auth/login/refresh', {
      username,
      refreshToken,
    });
    return data;
  },
};
