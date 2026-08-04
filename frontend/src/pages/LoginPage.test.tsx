import { StrictMode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, cleanup, fireEvent } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import LoginPage from './LoginPage';

/**
 * 게스트 로그인은 항상 localStorage 에 토큰을 저장한다(§LoginPage.tsx 새로고침·재방문에도
 * 게스트 신원 유지). 그래서 같은 브라우저의 다른 탭이 이미 로그인돼 있는데 "게스트로 시작"을
 * 또 누르면, 새 게스트가 그 탭의 토큰을 조용히 덮어써 두 탭이 같은 사람으로 인식되는 문제가
 * 있었다. 이 테스트는 "이미 로그인된 세션이 있으면 새로 만들지 않고 물어본다" 분기를 검증한다.
 */

const loginApi = vi.fn();
const guestLoginApi = vi.fn();
const getMeApi = vi.fn();
vi.mock('@/api/auth', () => ({
  authApi: {
    login: (...args: unknown[]) => loginApi(...args),
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

const fillAndSubmitLoginForm = async (email: string, password: string) => {
  fireEvent.change(screen.getByLabelText('이메일'), { target: { value: email } });
  fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: password } });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));
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

describe('LoginPage - 이메일 로그인', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    loginApi.mockReset();
    getMeApi.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it('로그인에 성공하면 토큰을 저장하고 홈으로 이동한다', async () => {
    loginApi.mockResolvedValue({ data: { data: { accessToken: 'access-1', refreshToken: 'refresh-1' } } });
    getMeApi.mockResolvedValue({ data: { data: { id: '1', nickname: '유저1' } } });

    renderPage();
    await fillAndSubmitLoginForm('test@test.com', 'password123');

    expect(loginApi).toHaveBeenCalledWith({ email: 'test@test.com', password: 'password123' });
    expect(screen.getByText('홈 화면')).toBeTruthy();
    expect(localStorage.getItem(ACCESS)).toBe('access-1');
  });

  it('비밀번호가 틀리면(401) 아이디/비밀번호 불일치가 아닌 서버 상세 메시지를 노출하지 않는다', async () => {
    loginApi.mockRejectedValue({ response: { status: 401, data: { message: '서버가 준 상세 사유' } } });

    renderPage();
    await fillAndSubmitLoginForm('test@test.com', 'wrong-password');

    // 보안상 어느 쪽이 틀렸는지 노출하지 않고 항상 같은 문구로 안내한다 (LoginPage.tsx handleSubmit 참고)
    expect(screen.getByText('아이디 또는 비밀번호가 틀렸습니다.')).toBeTruthy();
    expect(screen.queryByText('서버가 준 상세 사유')).toBeNull();
    expect(getMeApi).not.toHaveBeenCalled();
  });

  it('이메일 미인증(403)이면 인증 메일 재발송 링크를 보여준다', async () => {
    loginApi.mockRejectedValue({ response: { status: 403, data: { message: '이메일 인증이 필요합니다.' } } });

    renderPage();
    await fillAndSubmitLoginForm('unverified@test.com', 'password123');

    expect(screen.getByText('이메일 인증이 필요합니다.')).toBeTruthy();
    expect(screen.getByText('인증 메일 재발송하기')).toBeTruthy();
  });

  it('그 외 에러(예: 서버 오류)는 서버 메시지가 없으면 기본 문구를 보여준다', async () => {
    loginApi.mockRejectedValue({ response: { status: 500, data: {} } });

    renderPage();
    await fillAndSubmitLoginForm('test@test.com', 'password123');

    expect(screen.getByText('로그인에 실패했습니다.')).toBeTruthy();
  });

  it('로그인 상태 유지를 해제하면 액세스 토큰이 sessionStorage 에만 저장된다', async () => {
    loginApi.mockResolvedValue({ data: { data: { accessToken: 'access-2', refreshToken: null } } });
    getMeApi.mockResolvedValue({ data: { data: { id: '1', nickname: '유저1' } } });

    renderPage();
    fireEvent.click(screen.getByLabelText('로그인 상태 유지')); // 기본 ON → 해제
    await fillAndSubmitLoginForm('test@test.com', 'password123');

    expect(sessionStorage.getItem(ACCESS)).toBe('access-2');
    expect(localStorage.getItem(ACCESS)).toBeNull();
  });
});
