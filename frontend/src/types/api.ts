export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface RegisterResponse {
  ok: boolean;
}

export interface CheckUsernameResponse {
  available: boolean;
}

export interface ApiMessage {
  level: string;
  code: string;
  message: string;
}

export interface ApiError {
  messages: ApiMessage[];
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface GoogleAuthRequest {
  credential: string;
  username?: string;
}

export interface GoogleRegistrationRequired {
  email: string;
  suggestedUsername: string;
}
