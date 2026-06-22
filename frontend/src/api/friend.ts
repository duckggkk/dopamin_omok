import apiClient from './client';
import { ApiResponse, FriendRequest, FriendSummary, RelationInfo } from '@/types';

export const friendApi = {
  // 내 친구 목록(상대 전적 포함)
  getFriends: () => apiClient.get<ApiResponse<FriendSummary[]>>('/friends'),

  // 내가 받은 친구 요청
  getRequests: () => apiClient.get<ApiResponse<FriendRequest[]>>('/friends/requests'),

  // 닉네임으로 친구 요청 보내기
  sendRequest: (nickname: string) =>
    apiClient.post<ApiResponse<void>>('/friends/requests', { nickname }),

  // 그 사람(요청자)이 보낸 요청 수락
  accept: (publicId: string) =>
    apiClient.post<ApiResponse<void>>(`/friends/requests/${publicId}/accept`),

  // 관계 제거 — 거절/취소/끊기 공용
  remove: (publicId: string) =>
    apiClient.delete<ApiResponse<void>>(`/friends/${publicId}`),

  // 나와 그 사람의 관계 + 상대 전적(프로필용)
  getRelation: (publicId: string) =>
    apiClient.get<ApiResponse<RelationInfo>>(`/friends/relation/${publicId}`),
};
