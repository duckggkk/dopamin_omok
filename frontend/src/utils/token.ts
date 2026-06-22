const ACCESS_TOKEN_KEY = 'omok_access_token';
const REFRESH_TOKEN_KEY = 'omok_refresh_token';
const REMEMBER_KEY = 'omok_remember';

// '로그인 상태 유지' 여부. 미설정(기존 사용자)은 유지=true 로 본다(기존 동작 보존). 명시적으로 '0' 일 때만 세션 한정.
const isRemembered = (): boolean => localStorage.getItem(REMEMBER_KEY) !== '0';
// 유지 ON → localStorage(브라우저 닫아도 보존), OFF → sessionStorage(닫으면 소멸)
const primary = (): Storage => (isRemembered() ? localStorage : sessionStorage);
const secondary = (): Storage => (isRemembered() ? sessionStorage : localStorage);

export const tokenStorage = {
  isRemembered,
  /** 로그인 시 setTokens/login 보다 먼저 호출해 저장 위치(localStorage/sessionStorage)를 결정한다. */
  setRemember: (remember: boolean): void => {
    localStorage.setItem(REMEMBER_KEY, remember ? '1' : '0');
  },
  // 읽기는 어느 쪽에 있든 찾는다(localStorage 우선).
  getAccessToken: (): string | null =>
    localStorage.getItem(ACCESS_TOKEN_KEY) ?? sessionStorage.getItem(ACCESS_TOKEN_KEY),
  getRefreshToken: (): string | null =>
    localStorage.getItem(REFRESH_TOKEN_KEY) ?? sessionStorage.getItem(REFRESH_TOKEN_KEY),
  setTokens: (accessToken: string, refreshToken: string): void => {
    primary().setItem(ACCESS_TOKEN_KEY, accessToken);
    primary().setItem(REFRESH_TOKEN_KEY, refreshToken);
    // 반대편에 남아있을 수 있는 잔여 토큰 제거(저장 위치 일원화)
    secondary().removeItem(ACCESS_TOKEN_KEY);
    secondary().removeItem(REFRESH_TOKEN_KEY);
  },
  clearTokens: (): void => {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};

/**
 * zustand persist 용 동적 스토리지 — 토큰과 동일하게 '로그인 유지' 플래그를 따른다.
 * 유지 OFF 면 user/isAuthenticated 도 sessionStorage 에 담겨 브라우저를 닫으면 함께 사라진다.
 */
export const authPersistStorage = {
  getItem: (name: string): string | null =>
    localStorage.getItem(name) ?? sessionStorage.getItem(name),
  setItem: (name: string, value: string): void => {
    primary().setItem(name, value);
    secondary().removeItem(name);
  },
  removeItem: (name: string): void => {
    localStorage.removeItem(name);
    sessionStorage.removeItem(name);
  },
};
