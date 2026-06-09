import { useEffect, useRef } from 'react';
import { useBlocker, useNavigate } from 'react-router-dom';
import { gameApi } from '@/api/game';

interface LeaveGuardOptions {
  /** 라우터 네비게이션을 차단할지 (참가 중 & 방이 닫히지 않음) */
  blockActive: boolean;
  /** 새로고침/탭 닫기를 경고할지 */
  warnActive: boolean;
  isHost: boolean;
  isInProgress: boolean;
  roomCode: string;
}

const buildLeaveMessage = (isHost: boolean, isInProgress: boolean): string =>
  isHost
    ? '방을 나가면 방이 폭파됩니다.\n정말 나가시겠습니까?'
    : isInProgress
      ? '게임 진행 중 나가면 패배 처리됩니다.\n정말 나가시겠습니까?'
      : '방을 나가시겠습니까?';

/**
 * 게임 방 이탈 가드. 실수로 나가는 것을 막는다.
 *  - 라우터 네비게이션 차단(useBlocker) + 확인 다이얼로그
 *  - 브라우저 새로고침/탭 닫기 경고(beforeunload)
 *  - 의도적 이탈("방 나가기" 버튼)용 leaveRoom 제공
 */
export function useLeaveGuard({
  blockActive,
  warnActive,
  isHost,
  isInProgress,
  roomCode,
}: LeaveGuardOptions) {
  const navigate = useNavigate();
  const intentionalLeaveRef = useRef(false);

  const blocker = useBlocker(() => {
    if (intentionalLeaveRef.current) return false;
    return blockActive;
  });

  // blocker 발동 시 확인 다이얼로그
  useEffect(() => {
    if (blocker.state !== 'blocked') return;
    if (window.confirm(buildLeaveMessage(isHost, isInProgress))) {
      gameApi.leaveRoom(roomCode).catch(() => {}).finally(() => blocker.proceed?.());
    } else {
      blocker.reset?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [blocker.state]);

  // 브라우저 새로고침/탭 닫기 경고
  useEffect(() => {
    if (!warnActive) return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [warnActive]);

  // "방 나가기" 버튼 핸들러. confirmFirst=true 면 확인 다이얼로그 후 진행.
  const leaveRoom = async (confirmFirst: boolean) => {
    if (confirmFirst && !window.confirm(buildLeaveMessage(isHost, isInProgress))) return;
    intentionalLeaveRef.current = true;
    try {
      await gameApi.leaveRoom(roomCode);
    } catch {
      // ignore
    }
    navigate('/lobby');
  };

  return { leaveRoom };
}
