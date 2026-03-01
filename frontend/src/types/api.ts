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
  userHash: string;
  username: string;
  email: string;
  created: string;
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
