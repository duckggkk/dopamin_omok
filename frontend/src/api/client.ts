import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import { tokenStorage } from '@/utils/token';

const BASE_URL = '/api';

const apiClient: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
    // 이 프론트는 웹 클라이언트 — 서버가 리프레시 토큰을 HttpOnly 쿠키로 내려주도록 알린다.
    'X-Client-Type': 'web',
  },
  // 리프레시 토큰 HttpOnly 쿠키를 주고받으려면 필요(로그인 시 저장, /auth/refresh 시 자동 전송).
  withCredentials: true,
});

apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = tokenStorage.getAccessToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error), //요청 준비 과정에서 에러시 현재 에러 반환 (호출부에서 에러 처리)
);

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: string) => void;
  reject: (reason?: unknown) => void;
}> = [];

//토큰 만료 시 동시요청 방지용 큐
const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error);
    else resolve(token!);
  });
  failedQueue = [];
};

//함수 구현이 아니라 인자 구현임
apiClient.interceptors.response.use(
  (response) => response, //정상 리스폰, 정상 응답이면 통과
  async (error) => { //에러 리스폰
    const originalRequest = error.config;

    //토큰 필요없는 api주소
    const isAuthEndpoint = originalRequest.url?.includes('/auth/login') ||
      originalRequest.url?.includes('/auth/register') ||
      originalRequest.url?.includes('/auth/refresh');

    //토큰 만료시
    if (error.response?.status === 401 && !originalRequest._retry && !isAuthEndpoint) {
      //토큰 발급 진행중이면
      if (isRefreshing) {
        return new Promise((resolve, reject) => { // Promise = java의 CompletableFuture // resolve,reject는 약속된 인자, 호출부는 대기
          failedQueue.push({ resolve, reject });  // 여기서 resolve,  reject를 사용x
        }).then((token) => { // resolve되면 콜백함수
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return apiClient(originalRequest);      
        });
        //reject의 경우는 실패 콜백 없으면 실패 자동 전파
      }

      //토큰 발급 진행중 아니면
      originalRequest._retry = true;
      isRefreshing = true;

      try {
        // 리프레시 토큰은 HttpOnly 쿠키에 있어 JS 가 읽지 못하므로 body 에 싣지 않는다.
        // withCredentials 로 쿠키가 자동 전송되고, 서버가 새 액세스 토큰을 body 로 돌려준다(쿠키도 회전).
        const response = await axios.post(`${BASE_URL}/auth/refresh`, {}, {
          withCredentials: true,
          headers: { 'X-Client-Type': 'web' },
        });
        const { accessToken } = response.data.data;
        tokenStorage.setTokens(accessToken);
        processQueue(null, accessToken);
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return apiClient(originalRequest);
        //에러 발생시
      } catch (refreshError) {
        processQueue(refreshError, null);
        tokenStorage.clearTokens();
        window.location.href = '/login?reason=session_expired';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  },
);

export default apiClient;
