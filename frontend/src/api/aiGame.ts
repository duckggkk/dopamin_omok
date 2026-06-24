import apiClient from './client';
import { AiProgress, ApiResponse } from '@/types';

/**
 * 싱글플레이 AI 사다리 진척 API.
 * 진척("어디까지 깼는지")만 서버(계정)에 저장한다 — AI 대국 자체는 전부 브라우저에서
 * 계산되며 레이팅과 무관하다. 그래서 기기를 바꿔도 진행도가 계정을 따라온다.
 */
export const aiGameApi = {
  // 내 진척 조회(클리어 단계 + 총 단계 수)
  getProgress: () => apiClient.get<ApiResponse<AiProgress>>('/ai/progress'),

  // 한 단계 클리어 보고 → 갱신된 진척 반환(서버가 '다음 단계만 전진' 무결성 적용)
  reportClear: (level: number) =>
    apiClient.post<ApiResponse<AiProgress>>('/ai/clear', { level }),
};
