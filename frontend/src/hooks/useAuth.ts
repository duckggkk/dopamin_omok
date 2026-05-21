import { useEffect } from 'react';
import { useAuthStore } from '@/store/authStore';
import { userApi } from '@/api/auth';
import { tokenStorage } from '@/utils/token';

export const useAuth = () => {
  const { user, isAuthenticated, isLoading, setUser, setLoading, login, logout } = useAuthStore();

  useEffect(() => {
    const initAuth = async () => {
      const token = tokenStorage.getAccessToken();
      if (!token) {
        setLoading(false);
        return;
      }

      try {
        const response = await userApi.getMe();
        if (response.data.data) {
          setUser(response.data.data);
        }
      } catch {
        logout();
      } finally {
        setLoading(false);
      }
    };

    if (isLoading) {
      initAuth();
    }
  }, [isLoading, setUser, setLoading, logout]);

  return { user, isAuthenticated, isLoading, login, logout };
};
