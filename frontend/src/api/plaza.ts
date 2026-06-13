import apiClient from './client';
import { ApiResponse, PlazaJoinResponse } from '@/types';

export const plazaApi = {
  // 채널 배정(채우기 우선). 받은 channelId 토픽 구독 후 WS join 으로 실제 입장한다.
  join: () => apiClient.post<ApiResponse<PlazaJoinResponse>>('/plaza/join'),
};
