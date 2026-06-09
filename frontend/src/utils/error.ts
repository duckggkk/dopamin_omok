import { AxiosError } from 'axios';

interface ApiErrorBody {
  message?: string;
  // 필드 단위 검증 오류 맵 (백엔드 GlobalExceptionHandler 의 validation 응답)
  data?: Record<string, string>;
}

/** HTTP 상태 코드 (없으면 undefined) */
export const getApiErrorStatus = (err: unknown): number | undefined =>
  (err as AxiosError).response?.status;

/** 서버가 내려준 사용자용 메시지, 없으면 fallback */
export const getApiErrorMessage = (err: unknown, fallback: string): string =>
  (err as AxiosError<ApiErrorBody>).response?.data?.message ?? fallback;

/** 필드 단위 검증 오류 맵 (없으면 null) */
export const getApiFieldErrors = (err: unknown): Record<string, string> | null =>
  (err as AxiosError<ApiErrorBody>).response?.data?.data ?? null;
