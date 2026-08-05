import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { User } from '@/types';
import { tokenStorage, authPersistStorage } from '@/utils/token';


//토큰으로 인증된 사람이 누군지, 인증된 상태인지 컴포넌트가 리렌더로 반응하게 해줌
interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  setUser: (user: User) => void;
  setLoading: (loading: boolean) => void;
  login: (user: User, accessToken: string, refreshToken?: string | null) => void;
  logout: () => void;
}

//위 인터페이스의 구현체
// create는 zustand의 함수로, 상태 생성 함수(set...)을 받아서 그 상태를 저장하고 구독 가능하게하는 커스텀 React 훅을 만들어 반환
export const useAuthStore = create<AuthState>()(
  persist( // 실질적 함수
    //AuthState 세팅, 스토어 객체 초기 상태
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
    //두번째 인자인 옵션객체
    {
      name: 'omok-auth',
      // '로그인 유지' 플래그에 따라 localStorage/sessionStorage 로 자동 분기(토큰과 동일 정책)
      storage: createJSONStorage(() => authPersistStorage),
      partialize: (state) => ({ user: state.user, isAuthenticated: state.isAuthenticated }),
    },
  ),
);
