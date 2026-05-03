import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface AuthStore {
  token: string | null;
  userId: string | null;
  email: string | null;
  isAuthenticated: boolean;
  login: (token: string, userId: string, email: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      token: null,
      userId: null,
      email: null,
      isAuthenticated: false,
      login: (token: string, userId: string, email: string) =>
        set({
          token,
          userId,
          email,
          isAuthenticated: true,
        }),
      logout: () =>
        set({
          token: null,
          userId: null,
          email: null,
          isAuthenticated: false,
        }),
    }),
    {
      name: 'auth-store',
    }
  )
);

