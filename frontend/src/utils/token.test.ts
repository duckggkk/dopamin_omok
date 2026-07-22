import { describe, it, expect, beforeEach } from 'vitest';
import { tokenStorage, authPersistStorage } from './token';

/**
 * '로그인 상태 유지' 여부에 따라 저장 위치가 localStorage ↔ sessionStorage 로 갈린다.
 * 위치를 바꿀 때 반대편에 값이 남으면 로그아웃했는데 브라우저를 다시 열면
 * 살아나는 식의 버그가 되므로, "쓴 곳"뿐 아니라 "지운 곳"까지 함께 검증한다.
 */

const ACCESS = 'omok_access_token';
const REFRESH = 'omok_refresh_token';

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});

describe('tokenStorage.isRemembered', () => {
  it('설정한 적이 없으면 유지 ON 으로 본다(기존 사용자 동작 보존)', () => {
    expect(tokenStorage.isRemembered()).toBe(true);
  });

  it('명시적으로 끈 경우에만 false', () => {
    tokenStorage.setRemember(false);
    expect(tokenStorage.isRemembered()).toBe(false);

    tokenStorage.setRemember(true);
    expect(tokenStorage.isRemembered()).toBe(true);
  });
});

describe('tokenStorage.setTokens', () => {
  it('유지 ON 이면 localStorage 에 저장한다', () => {
    tokenStorage.setRemember(true);
    tokenStorage.setTokens('access-1');

    expect(localStorage.getItem(ACCESS)).toBe('access-1');
    expect(sessionStorage.getItem(ACCESS)).toBeNull();
  });

  it('유지 OFF 면 sessionStorage 에 저장한다', () => {
    tokenStorage.setRemember(false);
    tokenStorage.setTokens('access-1');

    expect(sessionStorage.getItem(ACCESS)).toBe('access-1');
    expect(localStorage.getItem(ACCESS)).toBeNull();
  });

  it('유지 설정을 바꿔 다시 로그인하면 반대편에 남은 토큰을 지운다', () => {
    tokenStorage.setRemember(true);
    tokenStorage.setTokens('old-access');
    expect(localStorage.getItem(ACCESS)).toBe('old-access');

    tokenStorage.setRemember(false);
    tokenStorage.setTokens('new-access');

    expect(sessionStorage.getItem(ACCESS)).toBe('new-access');
    expect(localStorage.getItem(ACCESS)).toBeNull(); // 잔여물이 남으면 안 된다
  });

  it('리프레시 토큰을 생략하면(웹 = HttpOnly 쿠키 모드) 양쪽 잔여 토큰을 모두 제거한다', () => {
    localStorage.setItem(REFRESH, 'stale-local');
    sessionStorage.setItem(REFRESH, 'stale-session');

    tokenStorage.setTokens('access-1');

    expect(localStorage.getItem(REFRESH)).toBeNull();
    expect(sessionStorage.getItem(REFRESH)).toBeNull();
  });

  it('리프레시 토큰을 넘기면(앱 = body 응답) 함께 저장한다', () => {
    tokenStorage.setRemember(true);
    tokenStorage.setTokens('access-1', 'refresh-1');

    expect(localStorage.getItem(REFRESH)).toBe('refresh-1');
    expect(sessionStorage.getItem(REFRESH)).toBeNull();
  });
});

describe('tokenStorage 읽기 / 삭제', () => {
  it('어느 저장소에 있든 찾아 읽는다', () => {
    sessionStorage.setItem(ACCESS, 'from-session');
    expect(tokenStorage.getAccessToken()).toBe('from-session');

    localStorage.setItem(ACCESS, 'from-local');
    expect(tokenStorage.getAccessToken()).toBe('from-local'); // localStorage 우선
  });

  it('저장된 토큰이 없으면 null', () => {
    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(tokenStorage.getRefreshToken()).toBeNull();
  });

  it('clearTokens 는 양쪽 저장소를 모두 비운다', () => {
    localStorage.setItem(ACCESS, 'a');
    localStorage.setItem(REFRESH, 'b');
    sessionStorage.setItem(ACCESS, 'c');
    sessionStorage.setItem(REFRESH, 'd');

    tokenStorage.clearTokens();

    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(tokenStorage.getRefreshToken()).toBeNull();
  });
});

describe('authPersistStorage (zustand persist)', () => {
  it('토큰과 같은 규칙으로 저장 위치를 고른다', () => {
    tokenStorage.setRemember(false);
    authPersistStorage.setItem('auth', '{"user":1}');

    expect(sessionStorage.getItem('auth')).toBe('{"user":1}');
    expect(localStorage.getItem('auth')).toBeNull();
    expect(authPersistStorage.getItem('auth')).toBe('{"user":1}');
  });

  it('removeItem 은 양쪽에서 지운다', () => {
    localStorage.setItem('auth', 'x');
    sessionStorage.setItem('auth', 'y');

    authPersistStorage.removeItem('auth');

    expect(authPersistStorage.getItem('auth')).toBeNull();
  });
});
