import axios, { isAxiosError } from 'axios';
import i18n from 'i18next';

import type {
  CheckUsernameResponse,
  ForgotPasswordRequest,
  GoogleAuthRequest,
  GoogleRegistrationRequired,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
  ResetPasswordRequest,
} from '@/types/api';

const authClient = axios.create({
  baseURL: '/api',
});

authClient.interceptors.request.use((config) => {
  config.headers['Accept-Language'] = i18n.language;
  return config;
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

  confirmEmail: async (token: string): Promise<void> => {
    await authClient.post('/auth/confirm', { token });
  },

  resendConfirmation: async (username: string): Promise<void> => {
    await authClient.post('/auth/resend-confirmation', { username });
  },

  forgotPassword: async (email: string): Promise<void> => {
    await authClient.post('/auth/forgot-password', { email } satisfies ForgotPasswordRequest);
  },

  resetPassword: async (token: string, newPassword: string): Promise<void> => {
    await authClient.post('/auth/reset-password', {
      token,
      newPassword,
    } satisfies ResetPasswordRequest);
  },

  googleLogin: async (credential: string): Promise<LoginResponse | GoogleRegistrationRequired> => {
    try {
      const { data } = await authClient.post<LoginResponse>('/auth/google', {
        credential,
      } satisfies GoogleAuthRequest);
      return data;
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 404) {
        return err.response.data as GoogleRegistrationRequired;
      }
      throw err;
    }
  },

  googleRegister: async (credential: string, username: string): Promise<LoginResponse> => {
    const { data } = await authClient.post<LoginResponse>('/auth/google', {
      credential,
      username,
    } satisfies GoogleAuthRequest);
    return data;
  },
};
