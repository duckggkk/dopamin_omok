import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { gameApi } from '@/api/game';
import { useAuthStore } from '@/store/authStore';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useChat } from '@/hooks/useChat';
import { useCosmetics } from '@/hooks/useCosmetics';
import { useStoneSoundPlayer } from '@/hooks/useStoneSoundPlayer';
import { useLeaveGuard } from '@/hooks/useLeaveGuard';
import OmokBoard from '@/components/game/OmokBoard';
import PlayerCard from '@/components/game/PlayerCard';
import ChatPanel from '@/components/game/ChatPanel';
import MoveHistory from '@/components/game/MoveHistory';
import GameResultOverlay, { GameResult } from '@/components/game/GameResultOverlay';
import { Room, GameMove, Board, StoneColor, ApiResponse, NoticePayload } from '@/types';
import { createEmptyBoard } from '@/constants/board';
import styles from './GamePage.module.css';

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
  const [gameResult, setGameResult] = useState<GameResult | null>(null);
  const [gameResultDisplayText, setGameResultDisplayText] = useState<string>('');

  const prevGameIdRef = useRef<number | undefined>(undefined);
  const prevRoomStatusRef = useRef<string | undefined>(undefined);
  const gameResultTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const closedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 코스메틱(스킨)과 채팅은 자기완결적 훅으로 분리.
  // 착수음은 "둔 사람의 소리"를 착수 메시지(soundAssetKey)로 받아 재생한다.
  const { boardSkinConfig } = useCosmetics();
  const playStoneSound = useStoneSoundPlayer();
  const chat = useChat();

  // 플레이어 파생
  const myPlayer = room?.players.find((p) => p.userId === user?.id) ?? null;
  const blackPlayer = room?.players.find((p) => p.color === 'BLACK' && p.role !== 'SPECTATOR') ?? null;
  const whitePlayer = room?.players.find((p) => p.color === 'WHITE' && p.role !== 'SPECTATOR') ?? null;
  const playerRolePlayer = room?.players.find((p) => p.role === 'PLAYER') ?? null;
  const spectators = room?.players.filter((p) => p.role === 'SPECTATOR') ?? [];
  const currentGame = room?.currentGame ?? null;

  const isHost = myPlayer?.role === 'HOST';
  const isPlayerRole = myPlayer?.role === 'PLAYER';
  const myColor: StoneColor | null = myPlayer?.color ?? null;
  const isInProgress = currentGame?.status === 'IN_PROGRESS';

  // 이탈 가드(라우터 차단 + 새로고침 경고 + 의도적 이탈)
  const { leaveRoom } = useLeaveGuard({
    blockActive: !!(myPlayer && room && room.status !== 'CLOSED' && !closedMessage),
    warnActive: !!(myPlayer && room && !closedMessage),
    isHost,
    isInProgress,
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

  const { sendMove, sendSurrender, sendReady, sendStart, sendChat } = useWebSocket({
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
    };
  }, []);

  // 초기 방/기보 로드
  useEffect(() => {
    const load = async () => {
      try {
        const roomRes = await gameApi.getRoom(roomCode!);
        if (roomRes.data.data) {
          setRoom(roomRes.data.data);
          if (roomRes.data.data.currentGame) {
            const movesRes = await gameApi.getGameMoves(roomCode!);
            if (movesRes.data.data) {
              const existingMoves = movesRes.data.data;
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
          }
        }
      } catch {
        navigate('/lobby');
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
      if (!playerRolePlayer) {
        setStatusMessage('상대방을 기다리는 중...');
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
  }, [room, user, myPlayer, playerRolePlayer, currentGame, isHost, myColor]);

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

      <GameResultOverlay result={gameResult} displayText={gameResultDisplayText} />

      <div className={styles.gameArea}>
        <PlayerCard
          color="BLACK"
          player={blackPlayer}
          myUserId={user?.id}
          roomStatus={room.status}
          currentGame={currentGame}
        />

        <div className={styles.boardWrapper}>
          <p className={styles.status}>{statusMessage}</p>
          {graceNotice && <p className={styles.graceNotice}>{graceNotice}</p>}
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

        <PlayerCard
          color="WHITE"
          player={whitePlayer}
          myUserId={user?.id}
          roomStatus={room.status}
          currentGame={currentGame}
        />
      </div>

      <div className={styles.sidebar}>
        <div className={styles.roomInfo}>
          <p className={styles.roomCode}>
            방 코드: <strong>{room.roomCode}</strong>
          </p>
          <p className={styles.moveCount}>수: {moves.length}</p>
        </div>

        {/* 초대 + 준비/시작 버튼 (WAITING 상태) */}
        {room.status === 'WAITING' && (
          <div className={styles.waitingPanel}>
            {!playerRolePlayer && (
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
              <button
                onClick={() => sendStart()}
                disabled={!playerRolePlayer.ready}
                className={styles.startBtn}
              >
                시작
              </button>
            )}
          </div>
        )}

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
