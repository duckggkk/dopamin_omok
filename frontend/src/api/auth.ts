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
};
