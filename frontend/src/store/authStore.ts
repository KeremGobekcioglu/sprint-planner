import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface AuthStore {
  token: string | null;
  userId: string | null;
  email: string | null;
  name: string | null;
  role: 'ADMIN' | 'USER' | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (token: string, userId: string, email: string, name: string, role: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      token: null,
      userId: null,
      email: null,
      name: null,
      role: null,
      isAuthenticated: false,
      isAdmin: false,
      login: (token: string, userId: string, email: string, name: string, role: string) =>
        set({
          token,
          userId,
          email,
          name,
          role: role === 'ADMIN' ? 'ADMIN' : 'USER',
          isAuthenticated: true,
          isAdmin: role === 'ADMIN',
        }),
      logout: () =>
        set({
          token: null,
          userId: null,
          email: null,
          name: null,
          role: null,
          isAuthenticated: false,
          isAdmin: false,
        }),
    }),
    {
      name: 'auth-store',
    }
  )
);

