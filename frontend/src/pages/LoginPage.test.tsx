import { StrictMode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, cleanup } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import LoginPage from './LoginPage';

/**
 * 게스트 로그인은 항상 localStorage 에 토큰을 저장한다(§LoginPage.tsx 새로고침·재방문에도
 * 게스트 신원 유지). 그래서 같은 브라우저의 다른 탭이 이미 로그인돼 있는데 "게스트로 시작"을
 * 또 누르면, 새 게스트가 그 탭의 토큰을 조용히 덮어써 두 탭이 같은 사람으로 인식되는 문제가
 * 있었다. 이 테스트는 "이미 로그인된 세션이 있으면 새로 만들지 않고 물어본다" 분기를 검증한다.
 */

const guestLoginApi = vi.fn();
const getMeApi = vi.fn();
vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    guestLogin: (...args: unknown[]) => guestLoginApi(...args),
  },
  userApi: {
    getMe: (...args: unknown[]) => getMeApi(...args),
  },
}));

const ACCESS = 'omok_access_token';
const GUEST_BUTTON = '🎮 비회원으로 바로 시작';

const renderPage = () =>
  render(
    <StrictMode>
      <RouterProvider
        router={createMemoryRouter(
          [
            { path: '/login', element: <LoginPage /> },
            { path: '/', element: <div>홈 화면</div> },
          ],
          { initialEntries: ['/login'] },
        )}
      />
    </StrictMode>,
  );

const clickGuestStart = async () => {
  await act(async () => {
    screen.getByText(GUEST_BUTTON).click();
  });
};

describe('LoginPage - 게스트로 시작', () => {
  let confirmSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    guestLoginApi.mockReset();
    getMeApi.mockReset();
    confirmSpy = vi.spyOn(window, 'confirm');
  });

  afterEach(() => {
    cleanup();
    confirmSpy.mockRestore();
  });

  it('로그인된 세션이 없으면 확인창 없이 새 게스트를 발급한다', async () => {
    guestLoginApi.mockResolvedValue({ data: { data: { accessToken: 'new-token', refreshToken: null } } });
    getMeApi.mockResolvedValue({ data: { data: { id: '1', nickname: '게스트1' } } });

    renderPage();
    await clickGuestStart();

    expect(confirmSpy).not.toHaveBeenCalled();
    expect(guestLoginApi).toHaveBeenCalledTimes(1);
    expect(screen.getByText('홈 화면')).toBeTruthy();
    expect(localStorage.getItem(ACCESS)).toBe('new-token');
  });

  it('이미 로그인된 세션이 있고 확인을 누르면 새 게스트를 만들지 않고 기존 계정으로 이동한다', async () => {
    localStorage.setItem(ACCESS, 'existing-token');
    confirmSpy.mockReturnValue(true);

    renderPage();
    await clickGuestStart();

    expect(confirmSpy).toHaveBeenCalledTimes(1);
    expect(guestLoginApi).not.toHaveBeenCalled();
    expect(screen.getByText('홈 화면')).toBeTruthy();
    expect(localStorage.getItem(ACCESS)).toBe('existing-token'); // 덮어써지지 않았다
  });

  it('이미 로그인된 세션이 있고 취소를 누르면 로그인 화면에 남고 새 게스트도 만들지 않는다', async () => {
    localStorage.setItem(ACCESS, 'existing-token');
    confirmSpy.mockReturnValue(false);

    renderPage();
    await clickGuestStart();

    expect(confirmSpy).toHaveBeenCalledTimes(1);
    expect(guestLoginApi).not.toHaveBeenCalled();
    expect(screen.getByText(GUEST_BUTTON)).toBeTruthy(); // 로그인 페이지에 그대로
    expect(localStorage.getItem(ACCESS)).toBe('existing-token');
  });
});
