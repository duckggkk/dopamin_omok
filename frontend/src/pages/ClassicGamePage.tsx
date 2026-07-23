import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { gameApi } from '@/api/game';
import { useAuthStore } from '@/store/authStore';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useChat } from '@/hooks/useChat';
import { useCosmetics } from '@/hooks/useCosmetics';
import { useStoneSoundPlayer } from '@/hooks/useStoneSoundPlayer';
import { useLeaveGuard } from '@/hooks/useLeaveGuard';
import { useBackgroundMusic } from '@/hooks/useBackgroundMusic';
import { useStackedGameLayout } from '@/hooks/useTouchUI';
import { areSameStoneSkin } from '@/utils/stoneSkin';
import OmokBoard from '@/components/game/OmokBoard';
import PlayerCard from '@/components/game/PlayerCard';
import PlayerProfileModal from '@/components/game/PlayerProfileModal';
import StoneSkinPicker from '@/components/game/StoneSkinPicker';
import ChatPanel from '@/components/game/ChatPanel';
import MoveHistory from '@/components/game/MoveHistory';
import GameResultOverlay, { GameResult } from '@/components/game/GameResultOverlay';
import { Room, GameMove, Board, StoneColor, ApiResponse, NoticePayload } from '@/types';
import { createEmptyBoard } from '@/constants/board';
import styles from './GamePage.module.css';

const CLASSIC_BGM_SRC = '/audio/classic-omok-bgm.mp3';

const ClassicGamePage = () => {
  const { gameId: roomCode } = useParams<{ gameId: string }>();
  const navigate = useNavigate();
  const { user } = useAuthStore();

  const [room, setRoom] = useState<Room | null>(null);
  const [board, setBoard] = useState<Board>(createEmptyBoard());
  const [moves, setMoves] = useState<GameMove[]>([]);
  const [lastMove, setLastMove] = useState<{ row: number; col: number } | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [statusMessage, setStatusMessage] = useState('');
  const [closedMessage, setClosedMessage] = useState('');
  const [graceNotice, setGraceNotice] = useState<string | null>(null);
  const [moveNotice, setMoveNotice] = useState<string | null>(null);
  const [gameResult, setGameResult] = useState<GameResult | null>(null);
  const [gameResultDisplayText, setGameResultDisplayText] = useState<string>('');
  const [gameResultEffect, setGameResultEffect] = useState<string | null>(null);
  // 이름 클릭 시 보여줄 간략 프로필 대상(없으면 null), 바둑알 스킨 선택 모달 열림 여부
  const [profileUserId, setProfileUserId] = useState<string | null>(null);
  const [skinPickerOpen, setSkinPickerOpen] = useState(false);

  const prevGameIdRef = useRef<number | undefined>(undefined);
  const prevRoomStatusRef = useRef<string | undefined>(undefined);
  const gameResultTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const closedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const moveNoticeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 코스메틱(스킨)과 채팅은 자기완결적 훅으로 분리.
  // 착수음은 "둔 사람의 소리"를 착수 메시지(soundAssetKey)로 받아 재생한다.
  const { boardSkinConfig } = useCosmetics();
  const playStoneSound = useStoneSoundPlayer();
  const chat = useChat();
  // 좁은 화면에선 사이드바가 보드 아래로 내려가므로, 대기 중 조작 패널만 보드 위로 올려 붙인다.
  const stacked = useStackedGameLayout();

  // 플레이어 파생
  const myPlayer = room?.players.find((p) => p.userId === user?.id) ?? null;
  const blackPlayer = room?.players.find((p) => p.color === 'BLACK' && p.role !== 'SPECTATOR') ?? null;
  const whitePlayer = room?.players.find((p) => p.color === 'WHITE' && p.role !== 'SPECTATOR') ?? null;
  const sameStoneSkin = areSameStoneSkin(blackPlayer?.stoneSkin, whitePlayer?.stoneSkin);
  const playerRolePlayer = room?.players.find((p) => p.role === 'PLAYER') ?? null;
  const spectators = room?.players.filter((p) => p.role === 'SPECTATOR') ?? [];
  const currentGame = room?.currentGame ?? null;

  const isHost = myPlayer?.role === 'HOST';
  const isPlayerRole = myPlayer?.role === 'PLAYER';
  const myColor: StoneColor | null = myPlayer?.color ?? null;
  const isInProgress = currentGame?.status === 'IN_PROGRESS';

  // BGM은 게임이 진행 중일 때만 재생(대기 중엔 무음)
  useBackgroundMusic(CLASSIC_BGM_SRC, { enabled: isInProgress });

  // 이탈 가드(라우터 차단 + 새로고침 경고 + 의도적 이탈)
  const { leaveRoom } = useLeaveGuard({
    blockActive: !!(myPlayer && room && room.status !== 'CLOSED' && !closedMessage),
    warnActive: !!(myPlayer && room && !closedMessage),
    isHost,
    roomCode: roomCode!,
  });

  const handleMove = useCallback((res: ApiResponse<GameMove>) => {
    if (res.success && res.data) {
      const move = res.data;
      setBoard((prev) => {
        const next = prev.map((r) => [...r]);
        next[move.row][move.col] = move.color;
        return next;
      });
      setMoves((prev) => [...prev, move]);
      setLastMove({ row: move.row, col: move.col });
      playStoneSound(move.soundAssetKey); // 둔 사람의 착수음 (없으면 무음)
      return;
    }
    // 렌주룰 금수 등 착수가 거부된 경우 사유를 잠깐 안내(2.5초 후 사라짐).
    if (res.message && res.message.includes('금수')) {
      setMoveNotice(res.message);
      if (moveNoticeTimerRef.current) clearTimeout(moveNoticeTimerRef.current);
      moveNoticeTimerRef.current = setTimeout(() => setMoveNotice(null), 2500);
    }
  }, [playStoneSound]);

  const handleRoomStatus = useCallback((res: ApiResponse<Room>) => {
    if (res.success && res.data) {
      setRoom(res.data);
      setGraceNotice(null);
    }
  }, []);

  const handleNotice = useCallback((res: NoticePayload) => {
    if (res.message) setGraceNotice(res.message);
  }, []);

  const handleRoomClosed = useCallback(
    (res: NoticePayload) => {
      setClosedMessage(res.message ?? '방장이 방을 나갔습니다.');
      // 타이머를 ref에 보관 → 사용자가 직접 다른 페이지(상점 등)로 이동해
      // 컴포넌트가 언마운트되면 정리되어, 의도치 않게 로비로 끌려가지 않는다.
      if (closedTimerRef.current) clearTimeout(closedTimerRef.current);
      closedTimerRef.current = setTimeout(() => navigate('/lobby'), 3000);
    },
    [navigate],
  );

  const { sendMove, sendSurrender, sendReady, sendStart, sendChat, sendChangeSkin, sendSwapColors } = useWebSocket({
    roomCode: roomCode!,
    onMove: handleMove,
    onRoomStatus: handleRoomStatus,
    onRoomClosed: handleRoomClosed,
    onChat: chat.onChatMessage,
    onNotice: handleNotice,
  });

  // 새 게임 시작 시 보드 초기화
  useEffect(() => {
    if (currentGame?.id !== undefined) {
      if (prevGameIdRef.current !== undefined && currentGame.id !== prevGameIdRef.current) {
        setBoard(createEmptyBoard());
        setMoves([]);
        setLastMove(null);
        setGameResult(null);
        if (gameResultTimerRef.current) clearTimeout(gameResultTimerRef.current);
      }
      prevGameIdRef.current = currentGame.id;
    }
  }, [currentGame?.id]);

  // 게임 종료(IN_PROGRESS → WAITING) 시 결과 표시 후 3초 뒤 보드 초기화
  useEffect(() => {
    if (!room) return;
    const wasInProgress = prevRoomStatusRef.current === 'IN_PROGRESS';
    prevRoomStatusRef.current = room.status;

    if (!wasInProgress || room.status !== 'WAITING') return;

    const game = room.currentGame;
    const isActivePlayer = myPlayer?.role === 'HOST' || myPlayer?.role === 'PLAYER';

    if (isActivePlayer && game) {
      if (game.status === 'FINISHED' || game.status === 'ABANDONED') {
        const isWin = game.winner?.id === user?.id;
        setGameResult(isWin ? 'WIN' : 'LOSS');
        setGameResultDisplayText(isWin ? '승리!' : (game.winnerDefeatMessage ?? '패배'));
        // 승자가 장착한 이펙트(승/패 공용) — 승자는 승리 연출, 패자는 패배 연출. 미장착이면 문구만.
        setGameResultEffect(game.winnerDefeatEffect ?? null);
      } else if (game.status === 'DRAW') {
        setGameResult('DRAW');
        setGameResultDisplayText('무승부');
      }
    }

    if (gameResultTimerRef.current) clearTimeout(gameResultTimerRef.current);
    gameResultTimerRef.current = setTimeout(() => {
      setBoard(createEmptyBoard());
      setMoves([]);
      setLastMove(null);
      setGameResult(null);
      setGameResultDisplayText('');
    }, 3000);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [room?.status]);

  // 언마운트 시 타이머 정리 (상점 등 다른 페이지로 이동 시 잔여 로비 리다이렉트 방지)
  useEffect(() => {
    return () => {
      if (gameResultTimerRef.current) clearTimeout(gameResultTimerRef.current);
      if (closedTimerRef.current) clearTimeout(closedTimerRef.current);
      if (moveNoticeTimerRef.current) clearTimeout(moveNoticeTimerRef.current);
    };
  }, []);

  // 초기 방/기보 로드
  useEffect(() => {
    const load = async () => {
      let loadedRoom: Room | null = null;
      try {
        const roomRes = await gameApi.getRoom(roomCode!);
        loadedRoom = roomRes.data.data ?? null;
        if (loadedRoom) setRoom(loadedRoom);
      } catch {
        navigate('/lobby'); // 방 자체를 못 불러왔을 때만 로비로 — 이땐 room 이 없어 이탈 가드도 잠잠하다
        setIsLoading(false);
        return;
      }

      // 기보는 '진행 중인' 게임에만 남아 있다(서버는 IN_PROGRESS 만 조회 → 끝난 게임은 404).
      // 방 정보의 currentGame 은 끝난 게임도 들고 있으므로, 상태를 보고 골라 불러야 한다.
      // 이걸 안 가리면 한 판 둔 방에 다시 들어올 때마다 404 → 로비로 이동 → 이탈 가드가
      // 가로채 "방을 나가시겠습니까?" 가 뜬다(방에 들어오자마자).
      if (loadedRoom?.currentGame?.status !== 'IN_PROGRESS') {
        setIsLoading(false);
        return;
      }

      try {
        const movesRes = await gameApi.getGameMoves(roomCode!);
        const existingMoves = movesRes.data.data;
        if (existingMoves) {
          setMoves(existingMoves);
          const newBoard = createEmptyBoard();
          existingMoves.forEach((m) => {
            newBoard[m.row][m.col] = m.color;
          });
          setBoard(newBoard);
          if (existingMoves.length > 0) {
            const last = existingMoves[existingMoves.length - 1];
            setLastMove({ row: last.row, col: last.col });
          }
        }
      } catch {
        // 기보를 못 불러와도 방에는 남는다 — 착수는 웹소켓으로 계속 들어온다.
      } finally {
        setIsLoading(false);
      }
    };
    load();
  }, [roomCode, navigate]);

  // 상태 메시지 업데이트
  useEffect(() => {
    if (!room || !user) return;

    if (room.status === 'WAITING') {
      if (myPlayer?.role === 'SPECTATOR') {
        setStatusMessage(playerRolePlayer ? '게임 시작을 기다리는 중...' : '플레이어 입장을 기다리는 중...');
      } else if (!playerRolePlayer) {
        setStatusMessage('상대방을 기다리는 중...');
      } else if (sameStoneSkin) {
        setStatusMessage('두 플레이어가 같은 바둑알 스킨입니다. 한 명이 다른 스킨으로 바꿔야 시작할 수 있습니다.');
      } else if (isHost) {
        setStatusMessage(playerRolePlayer.ready ? '준비 완료! 시작 버튼을 누르세요.' : '상대방이 준비 중입니다...');
      } else {
        setStatusMessage(myPlayer?.ready ? '방장이 게임을 시작하기를 기다리는 중...' : '준비 버튼을 눌러주세요.');
      }
      return;
    }

    if (!currentGame) return;

    switch (currentGame.status) {
      case 'IN_PROGRESS':
        setStatusMessage(currentGame.currentTurn === myColor ? '당신의 차례입니다!' : '상대방 차례입니다...');
        break;
      case 'FINISHED':
        setStatusMessage(currentGame.winner?.id === user.id ? '승리했습니다!' : '패배했습니다.');
        break;
      case 'DRAW':
        setStatusMessage('무승부입니다!');
        break;
      case 'ABANDONED':
        setStatusMessage('상대방이 게임을 포기했습니다.');
        break;
    }
  }, [room, user, myPlayer, playerRolePlayer, currentGame, isHost, myColor, sameStoneSkin]);

  if (isLoading) {
    return <div className={styles.loading}>게임을 불러오는 중...</div>;
  }

  if (!room) return null;

  const isGameOver = currentGame
    ? ['FINISHED', 'DRAW', 'ABANDONED'].includes(currentGame.status)
    : false;

  const handlePlaceStone = (row: number, col: number) => {
    sendMove(row, col);
  };

  const handleSurrender = () => {
    if (window.confirm('정말 기권하시겠습니까?')) {
      sendSurrender();
    }
  };

  const handleSendChat = () => {
    const trimmed = chat.input.trim();
    if (!trimmed) return;
    sendChat(trimmed);
    chat.setInput('');
  };

  const handleChatKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendChat();
    }
  };

  // 대기 중 조작(초대 코드 / 흑백 교체 / 준비 / 시작).
  // 인스턴스는 하나만 만들고, 넓은 화면이면 사이드바에 / 좁은 화면이면 상태 메시지 바로 아래(보드 위)에 붙인다.
  const waitingPanel = room.status === 'WAITING' ? (
    <div className={styles.waitingPanel}>
      {!playerRolePlayer && isHost && (
        <>
          <p className={styles.inviteText}>방 코드를 공유하여 상대방을 초대하세요</p>
          <button
            onClick={() => navigator.clipboard.writeText(room.roomCode)}
            className={styles.copyBtn}
          >
            코드 복사
          </button>
        </>
      )}
      {isPlayerRole && (
        <button
          onClick={() => sendReady()}
          className={myPlayer?.ready ? styles.readyBtnOn : styles.readyBtnOff}
        >
          {myPlayer?.ready ? '준비 취소' : '준비'}
        </button>
      )}
      {isHost && playerRolePlayer && (
        <>
          <button
            onClick={() => sendSwapColors()}
            className={styles.swapBtn}
            title="두 사람의 흑/백을 맞바꿉니다 (대기 중에만 가능)"
          >
            ⇄ 흑백 바꾸기
          </button>
          {sameStoneSkin && (
            <p className={styles.skinConflict}>
              같은 바둑알 스킨은 혼동을 막기 위해 시작할 수 없습니다.
            </p>
          )}
          <button
            onClick={() => sendStart()}
            disabled={!playerRolePlayer.ready || sameStoneSkin}
            className={styles.startBtn}
            title={sameStoneSkin ? '상대와 다른 바둑알 스킨을 장착해야 시작할 수 있습니다.' : undefined}
          >
            시작
          </button>
        </>
      )}
    </div>
  ) : null;

  return (
    <div className={styles.container}>
      {closedMessage && (
        <div className={styles.overlay}>
          <div className={styles.overlayContent}>
            <p>{closedMessage}</p>
            <p className={styles.overlaySubtext}>3초 후 로비로 이동합니다...</p>
          </div>
        </div>
      )}

      <GameResultOverlay result={gameResult} displayText={gameResultDisplayText} effect={gameResultEffect} />

      {profileUserId && (
        <PlayerProfileModal userId={profileUserId} onClose={() => setProfileUserId(null)} />
      )}

      {skinPickerOpen && (
        <StoneSkinPicker
          currentSkin={myPlayer?.stoneSkin ?? null}
          myColor={myColor}
          onSelect={(itemId) => {
            sendChangeSkin(itemId);
            setSkinPickerOpen(false);
          }}
          onClose={() => setSkinPickerOpen(false)}
        />
      )}

      <div className={styles.gameArea}>
        {/* 두 플레이어 카드를 모두 바둑판 위에 나란히 배치(피지컬 오목 HUD와 동일한 형태) */}
        <div className={styles.playersRow}>
          <PlayerCard
            color="BLACK"
            player={blackPlayer}
            myUserId={user?.id}
            roomStatus={room.status}
            currentGame={currentGame}
            onShowProfile={setProfileUserId}
            onChangeSkin={() => setSkinPickerOpen(true)}
          />
          <PlayerCard
            color="WHITE"
            player={whitePlayer}
            myUserId={user?.id}
            roomStatus={room.status}
            currentGame={currentGame}
            onShowProfile={setProfileUserId}
            onChangeSkin={() => setSkinPickerOpen(true)}
          />
        </div>

        <div className={styles.boardWrapper}>
          <p className={styles.status}>{statusMessage}</p>
          {/* 좁은 화면: 준비/시작을 안내 문구 바로 아래(보드 위)에 붙여 한 덩어리로 보이게 한다 */}
          {stacked && waitingPanel}
          {graceNotice && <p className={styles.graceNotice}>{graceNotice}</p>}
          {moveNotice && <p className={styles.graceNotice}>⚠️ {moveNotice}</p>}
          <OmokBoard
            board={board}
            currentTurn={currentGame?.currentTurn ?? null}
            myColor={myColor}
            onPlaceStone={handlePlaceStone}
            lastMove={lastMove}
            disabled={isGameOver || room.status === 'WAITING'}
            skinConfig={boardSkinConfig}
            blackStoneSkin={blackPlayer?.stoneSkin ?? null}
            whiteStoneSkin={whitePlayer?.stoneSkin ?? null}
            blackStoneEffect={blackPlayer?.stoneEffect ?? null}
            whiteStoneEffect={whitePlayer?.stoneEffect ?? null}
          />
        </div>
      </div>

      <div className={styles.sidebar}>
        <div className={styles.roomInfo}>
          <p className={styles.roomCode}>
            방 코드: <strong>{room.roomCode}</strong>
          </p>
          <p className={styles.moveCount}>수: {moves.length}</p>
        </div>

        {/* 초대 + 흑백교체 + 준비/시작 (WAITING 상태) — 좁은 화면에선 위쪽 보드 옆으로 올라간다 */}
        {!stacked && waitingPanel}

        {/* 기권 버튼 */}
        {isInProgress && myColor && (
          <button onClick={handleSurrender} className={styles.surrenderBtn}>
            기권
          </button>
        )}

        <button onClick={() => leaveRoom(!!myPlayer)} className={styles.leaveBtn}>
          방 나가기
        </button>

        {/* 관전자 목록 */}
        {spectators.length > 0 && (
          <div className={styles.spectatorPanel}>
            <h3 className={styles.panelTitle}>관전자 ({spectators.length})</h3>
            <div className={styles.spectatorList}>
              {spectators.map((s) => (
                <div key={s.userId} className={styles.spectatorItem}>
                  <span className={styles.spectatorIcon}>👁</span>
                  <span className={styles.spectatorName}>
                    {s.nickname}
                    {s.userId === user?.id && <span className={styles.badgeMe}>나</span>}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        <ChatPanel
          messages={chat.messages}
          input={chat.input}
          setInput={chat.setInput}
          onSend={handleSendChat}
          onKeyDown={handleChatKeyDown}
          myNickname={user?.nickname}
          bottomRef={chat.bottomRef}
        />

        <MoveHistory moves={moves} />
      </div>
    </div>
  );
};

export default ClassicGamePage;
