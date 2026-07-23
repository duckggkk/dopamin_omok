import apiClient from './client';
import { ApiResponse, TokenResponse, User, PublicUser, RankingEntry, StatMode } from '@/types';

export const authApi = {
  register: (data: { email: string; password: string; nickname: string }) =>
    apiClient.post<ApiResponse<void>>('/auth/register', data),

  verifyEmail: (email: string, code: string) =>
    apiClient.post<ApiResponse<void>>('/auth/verify-email', { email, code }),

  resendVerification: (email: string) =>
    apiClient.post<ApiResponse<void>>('/auth/resend-verification', { email }),

  login: (data: { email: string; password: string }) =>
    apiClient.post<ApiResponse<TokenResponse>>('/auth/login', data),

  // 비회원(게스트) 시작 — 회원가입 없이 익명 계정 토큰을 발급받는다.
  guestLogin: () =>
    apiClient.post<ApiResponse<TokenResponse>>('/auth/guest'),

  logout: () =>
    apiClient.post<ApiResponse<void>>('/auth/logout'),

  refresh: (refreshToken: string) =>
    apiClient.post<ApiResponse<TokenResponse>>('/auth/refresh', { refreshToken }),
};

export const userApi = {
  getMe: () =>
    apiClient.get<ApiResponse<User>>('/users/me'),

  getUser: (userId: string) =>
    apiClient.get<ApiResponse<PublicUser>>(`/users/${userId}`),

  updateProfile: (data: { nickname?: string; profileImageUrl?: string; profilePrivate?: boolean }) =>
    apiClient.patch<ApiResponse<User>>('/users/me', data),

  getRanking: (limit = 20, mode: StatMode = 'TOTAL') =>
    apiClient.get<ApiResponse<RankingEntry[]>>('/users/ranking', { params: { limit, mode } }),

  // 회원 탈퇴 — 계정은 익명화되고 대국 기록은 남는다(되돌릴 수 없음).
  // 일반 가입 계정은 비밀번호 확인이 필요하고, 구글 로그인 계정은 생략한다.
  withdraw: (password?: string) =>
    apiClient.delete<ApiResponse<void>>('/users/me', {
      data: password ? { password } : {},
    }),
};
