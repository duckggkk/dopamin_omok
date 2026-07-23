import { StrictMode, useEffect, useState } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, cleanup } from '@testing-library/react';
import { createMemoryRouter, RouterProvider, useNavigate } from 'react-router-dom';
import { useLeaveGuard } from './useLeaveGuard';

const leaveRoomApi = vi.fn((_roomCode: string) => Promise.resolve({}));
vi.mock('@/api/game', () => ({ gameApi: { leaveRoom: (code: string) => leaveRoomApi(code) } }));

/**
 * 방 화면 — 실제 게임 페이지를 흉내낸다.
 * 방 정보를 불러오기 전까지는 가드가 꺼져 있다가(blockActive=false) 로드 후 켜진다.
 */
const RoomScreen = () => {
  const navigate = useNavigate();
  const [loaded, setLoaded] = useState(false);
  useEffect(() => {
    let alive = true;
    Promise.resolve().then(() => alive && setLoaded(true));
    return () => {
      alive = false;
    };
  }, []);
  const { leaveRoom } = useLeaveGuard({
    blockActive: loaded,
    warnActive: loaded,
    isHost: false,
    roomCode: 'ABC',
  });
  return (
    <div>
      <span>방 화면</span>
      <button onClick={() => leaveRoom(true)}>방 나가기</button>
      {/* Navbar 의 방 나가기 — 가드가 알아서 확인창을 띄우도록 이동만 시킨다 */}
      <button onClick={() => navigate('/lobby')}>내비 나가기</button>
    </div>
  );
};

const LobbyScreen = () => {
  const navigate = useNavigate();
  return (
    <div>
      <span>로비</span>
      <button onClick={() => navigate('/game/ABC')}>방 입장</button>
    </div>
  );
};

// 앱과 동일하게 StrictMode 로 감싼다 — 개발 모드의 이펙트 이중 실행까지 그대로 재현하기 위함.
const renderApp = async () => {
  const result = render(
    <StrictMode>
      <RouterProvider
        router={createMemoryRouter(
          [
            { path: '/lobby', element: <LobbyScreen /> },
            { path: '/game/:gameId', element: <RoomScreen /> },
          ],
          { initialEntries: ['/game/ABC'] },
        )}
      />
    </StrictMode>,
  );
  await act(async () => {}); // 방 로드 완료(가드 활성화)까지 기다린다
  return result;
};

const click = async (name: string) => {
  await act(async () => {
    screen.getByText(name).click();
  });
};

describe('useLeaveGuard', () => {
  let confirmSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    leaveRoomApi.mockClear();
    confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  afterEach(() => {
    cleanup(); // vitest globals 를 안 쓰므로 자동 정리가 안 걸린다 — 직접 언마운트
    confirmSpy.mockRestore();
  });

  it('방 나가기는 확인창을 한 번만 띄운다', async () => {
    await renderApp();

    await click('방 나가기');

    expect(confirmSpy).toHaveBeenCalledTimes(1);
    expect(screen.getByText('로비')).toBeTruthy();
  });

  it('나갔던 방에 다시 들어와도 확인창이 뜨지 않는다', async () => {
    await renderApp();

    await click('방 나가기');
    confirmSpy.mockClear();

    await click('방 입장');

    expect(screen.getByText('방 화면')).toBeTruthy();
    expect(confirmSpy).not.toHaveBeenCalled();
  });

  it('가드가 막아서 나간 방에 다시 들어와도 확인창이 뜨지 않는다', async () => {
    await renderApp();

    await click('내비 나가기'); // 이동 → 가드가 차단 → 확인창 → proceed
    expect(confirmSpy).toHaveBeenCalledTimes(1);
    expect(screen.getByText('로비')).toBeTruthy();
    confirmSpy.mockClear();

    await click('방 입장');

    expect(screen.getByText('방 화면')).toBeTruthy();
    expect(confirmSpy).not.toHaveBeenCalled();
  });
});
