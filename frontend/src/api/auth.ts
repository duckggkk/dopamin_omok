import apiClient from './client';
import { ApiResponse, TokenResponse, User } from '@/types';

export const authApi = {
  register: (data: { email: string; password: string; nickname: string }) =>
    apiClient.post<ApiResponse<void>>('/auth/register', data),

  verifyEmail: (email: string, code: string) =>
    apiClient.post<ApiResponse<void>>('/auth/verify-email', { email, code }),

  resendVerification: (email: string) =>
    apiClient.post<ApiResponse<void>>('/auth/resend-verification', { email }),

  login: (data: { email: string; password: string }) =>
    apiClient.post<ApiResponse<TokenResponse>>('/auth/login', data),

  logout: () =>
    apiClient.post<ApiResponse<void>>('/auth/logout'),

  refresh: (refreshToken: string) =>
    apiClient.post<ApiResponse<TokenResponse>>('/auth/refresh', { refreshToken }),
};

export const userApi = {
  getMe: () =>
    apiClient.get<ApiResponse<User>>('/users/me'),

  getUser: (userId: number) =>
    apiClient.get<ApiResponse<User>>(`/users/${userId}`),

  updateProfile: (data: { nickname?: string; profileImageUrl?: string }) =>
    apiClient.patch<ApiResponse<User>>('/users/me', data),
};
