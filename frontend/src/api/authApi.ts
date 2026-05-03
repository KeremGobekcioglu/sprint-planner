import api from './axios';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
}

export const login = (email: string, password: string): Promise<AuthResponse> =>
  api.post<AuthResponse>('/api/auth/login', { email, password }).then((r) => r.data);

export const register = (email: string, password: string): Promise<AuthResponse> =>
  api.post<AuthResponse>('/api/auth/register', { email, password }).then((r) => r.data);

