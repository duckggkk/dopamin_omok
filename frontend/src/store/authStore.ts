import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { User } from '@/types';
import { tokenStorage, authPersistStorage } from '@/utils/token';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  setUser: (user: User) => void;
  setLoading: (loading: boolean) => void;
  login: (user: User, accessToken: string, refreshToken: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      isAuthenticated: false,
      isLoading: true,

      setUser: (user) => set({ user, isAuthenticated: true }),
      setLoading: (isLoading) => set({ isLoading }),

      login: (user, accessToken, refreshToken) => {
        tokenStorage.setTokens(accessToken, refreshToken);
        set({ user, isAuthenticated: true, isLoading: false });
      },

      logout: () => {
        tokenStorage.clearTokens();
        set({ user: null, isAuthenticated: false, isLoading: false });
      },
    }),
    {
      name: 'omok-auth',
      // '로그인 유지' 플래그에 따라 localStorage/sessionStorage 로 자동 분기(토큰과 동일 정책)
      storage: createJSONStorage(() => authPersistStorage),
      partialize: (state) => ({ user: state.user, isAuthenticated: state.isAuthenticated }),
    },
  ),
);
