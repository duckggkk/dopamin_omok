import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate, useBlocker } from 'react-router-dom';
import { gameApi } from '@/api/game';
import { shopApi } from '@/api/shop';
import { useAuthStore } from '@/store/authStore';
import { useWebSocket } from '@/hooks/useWebSocket';
import OmokBoard from '@/components/game/OmokBoard';
import { useStoneSound } from '@/hooks/useStoneSound';
import { Room, GameMove, Board, StoneColor, ChatMessage, ItemConfig } from '@/types';
import styles from './GamePage.module.css';

const BOARD_SIZE = 15;

const createEmptyBoard = (): Board =>
  Array.from({ length: BOARD_SIZE }, () => Array(BOARD_SIZE).fill(null));

const GamePage = () => {
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
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatInput, setChatInput] = useState('');

  const chatBottomRef = useRef<HTMLDivElement>(null);
  const prevGameIdRef = useRef<number | undefined>(undefined);
  const prevRoomStatusRef = useRef<string | undefined>(undefined);
  const intentionalLeaveRef = useRef(false);
  const [graceNotice, setGraceNotice] = useState<string | null>(null);
  const [gameResult, setGameResult] = useState<'WIN' | 'LOSS' | 'DRAW' | null>(null);
  const [gameResultDisplayText, setGameResultDisplayText] = useState<string>('');
  const gameResultTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [boardSkinConfig, setBoardSkinConfig] = useState<ItemConfig | null>(null);
  const [stoneSoundKey, setStoneSoundKey] = useState<string | null>(null);

  // 장착 착수음 재생 함수 (미장착 시 무음)
  const playStoneSound = useStoneSound(stoneSoundKey);

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

  // 방에 참가 중일 때 실수로 나가지 않도록 네비게이션 차단
  const blocker = useBlocker(() => {
    if (intentionalLeaveRef.current) return false;
    return !!(myPlayer && room && room.status !== 'CLOSED' && !closedMessage);
  });

  // blocker가 걸렸을 때 확인 다이얼로그
  useEffect(() => {
    if (blocker.state !== 'blocked') return;
    const message = isHost
      ? '방을 나가면 방이 폭파됩니다.\n정말 나가시겠습니까?'
      : currentGame?.status === 'IN_PROGRESS'
        ? '게임 진행 중 나가면 패배 처리됩니다.\n정말 나가시겠습니까?'
        : '방을 나가시겠습니까?';
    if (window.confirm(message)) {
      gameApi.leaveRoom(roomCode!).catch(() => {}).finally(() => blocker.proceed?.());
    } else {
      blocker.reset?.();
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [blocker.state]);

  // 브라우저 새로고침/탭 닫기 경고
  useEffect(() => {
    if (!myPlayer || !room || closedMessage) return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [myPlayer, room, closedMessage]);

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
        setGameResultDisplayText(
          isWin ? '승리!' : (game.winnerDefeatMessage ?? '패배'),
        );
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

  // 언마운트 시 타이머 정리
  useEffect(() => {
    return () => {
      if (gameResultTimerRef.current) clearTimeout(gameResultTimerRef.current);
    };
  }, []);

  // 장착된 코스메틱(바둑판 스킨/착수음) 로드 (미장착 시 각각 기본값/무음으로 폴백)
  useEffect(() => {
    shopApi.getInventory().then((res) => {
      const active = res.data.data?.activeItems;
      setBoardSkinConfig(active?.['BOARD_SKIN']?.itemConfig ?? null);
      setStoneSoundKey(active?.['STONE_SOUND']?.itemConfig?.assetKey ?? null);
    }).catch(() => {});
  }, []);

  // 채팅 자동 스크롤
  useEffect(() => {
    chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [chatMessages]);

  const handleMove = useCallback((data: unknown) => {
    const res = data as { success: boolean; data?: GameMove };
    if (res.success && res.data) {
      const move = res.data;
      setBoard((prev) => {
        const next = prev.map((r) => [...r]);
        next[move.row][move.col] = move.color;
        return next;
      });
      setMoves((prev) => [...prev, move]);
      setLastMove({ row: move.row, col: move.col });
      playStoneSound();
    }
  }, [playStoneSound]);

  const handleRoomStatus = useCallback((data: unknown) => {
    const res = data as { success: boolean; data?: Room };
    if (res.success && res.data) {
      setRoom(res.data);
      setGraceNotice(null);
    }
  }, []);

  const handleNotice = useCallback((data: unknown) => {
    const res = data as { message?: string };
    if (res.message) setGraceNotice(res.message);
  }, []);

  const handleRoomClosed = useCallback(
    (data: unknown) => {
      const res = data as { message?: string };
      setClosedMessage(res.message ?? '방장이 방을 나갔습니다.');
      setTimeout(() => navigate('/lobby'), 3000);
    },
    [navigate],
  );

  const handleChat = useCallback((data: unknown) => {
    const res = data as { success: boolean; data?: ChatMessage };
    if (res.success && res.data) {
      setChatMessages((prev) => [...prev, res.data!]);
    }
  }, []);

  const { sendMove, sendSurrender, sendReady, sendStart, sendChat } = useWebSocket({
    roomCode: roomCode!,
    onMove: handleMove,
    onRoomStatus: handleRoomStatus,
    onRoomClosed: handleRoomClosed,
    onChat: handleChat,
    onNotice: handleNotice,
  });

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

  const handleLeave = async () => {
    if (myPlayer) {
      const message = isHost
        ? '방을 나가면 방이 폭파됩니다.\n정말 나가시겠습니까?'
        : currentGame?.status === 'IN_PROGRESS'
          ? '게임 진행 중 나가면 패배 처리됩니다.\n정말 나가시겠습니까?'
          : '방을 나가시겠습니까?';
      if (!window.confirm(message)) return;
    }
    intentionalLeaveRef.current = true;
    try {
      await gameApi.leaveRoom(roomCode!);
    } catch {
      // ignore
    }
    navigate('/lobby');
  };

  const handleSendChat = () => {
    const trimmed = chatInput.trim();
    if (!trimmed) return;
    sendChat(trimmed);
    setChatInput('');
  };

  const handleChatKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendChat();
    }
  };

  const renderPlayerCard = (color: 'BLACK' | 'WHITE') => {
    const player = color === 'BLACK' ? blackPlayer : whitePlayer;
    const label = color === 'BLACK' ? '흑 (선)' : '백';
    const stoneClass = color === 'BLACK' ? styles.stoneBlack : styles.stoneWhite;
    const isMe = player?.userId === user?.id;
    const isHostPlayer = player?.role === 'HOST';
    const isPlayerReady = player?.role === 'PLAYER' && player.ready;

    return (
      <div className={styles.playerCard}>
        <div className={stoneClass} />
        <div className={styles.playerInfo}>
          <p className={styles.playerName}>
            {player?.nickname ?? '대기 중'}
            {isMe && <span className={styles.badgeMe}>나</span>}
            {isHostPlayer && <span className={styles.badgeHost}>방장</span>}
          </p>
          <p className={styles.playerLabel}>{label}</p>
        </div>
        {room.status === 'WAITING' && player?.role === 'PLAYER' && (
          <span className={isPlayerReady ? styles.readyOn : styles.readyOff}>
            {isPlayerReady ? '준비완료' : '준비중'}
          </span>
        )}
        {currentGame?.currentTurn === color && currentGame?.status === 'IN_PROGRESS' && (
          <span className={styles.turnIndicator}>●</span>
        )}
      </div>
    );
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

      {gameResult && (
        <div className={styles.gameResultOverlay}>
          <div className={styles.gameResultContent}>
            {gameResult === 'WIN' ? (
              <img
                src="/images/result-win.svg"
                alt="승리"
                className={styles.gameResultWinImage}
                draggable={false}
              />
            ) : gameResult === 'LOSS' ? (
              <div className={styles.gameResultLossBox}>
                <img
                  src="/images/result-loss-bg.svg"
                  alt=""
                  className={styles.gameResultLossBg}
                  draggable={false}
                />
                <p className={styles.gameResultLossText}>{gameResultDisplayText}</p>
              </div>
            ) : (
              <p className={styles.gameResultDrawText}>{gameResultDisplayText}</p>
            )}
            <p className={styles.gameResultSubtext}>3초 후 게임이 종료됩니다...</p>
          </div>
        </div>
      )}

      <div className={styles.gameArea}>
        {renderPlayerCard('BLACK')}

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
          />
        </div>

        {renderPlayerCard('WHITE')}
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
        {currentGame?.status === 'IN_PROGRESS' && myColor && (
          <button onClick={handleSurrender} className={styles.surrenderBtn}>
            기권
          </button>
        )}

        <button onClick={handleLeave} className={styles.leaveBtn}>
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

        {/* 채팅 */}
        <div className={styles.chatPanel}>
          <h3 className={styles.panelTitle}>채팅</h3>
          <div className={styles.chatMessages}>
            {chatMessages.map((msg, i) => {
              const isMyMsg = msg.senderNickname === user?.nickname;
              return (
                <div key={i} className={`${styles.chatMessage} ${isMyMsg ? styles.chatMine : styles.chatOther}`}>
                  <span
                    className={styles.chatSender}
                    style={{
                      color: msg.senderColor === 'BLACK' ? '#aaa' : msg.senderColor === 'WHITE' ? '#e0c97f' : '#888',
                    }}
                  >
                    {msg.spectator ? '👁 ' : ''}{msg.senderNickname}
                  </span>
                  <span className={styles.chatContent}>{msg.content}</span>
                </div>
              );
            })}
            <div ref={chatBottomRef} />
          </div>
          <div className={styles.chatInputRow}>
            <input
              className={styles.chatInput}
              value={chatInput}
              onChange={(e) => setChatInput(e.target.value)}
              onKeyDown={handleChatKeyDown}
              placeholder="메시지 입력..."
              maxLength={200}
            />
            <button className={styles.chatSendBtn} onClick={handleSendChat}>
              전송
            </button>
          </div>
        </div>

        {/* 기보 */}
        <div className={styles.moveHistory}>
          <h3>기보</h3>
          <div className={styles.moveList}>
            {[...moves]
              .reverse()
              .slice(0, 20)
              .map((m) => (
                <div key={m.id} className={styles.moveItem}>
                  <span className={m.color === 'BLACK' ? styles.blackDot : styles.whiteDot} />
                  <span>
                    {m.moveNumber}. {m.playerNickname}
                  </span>
                  <span className={styles.movePos}>
                    ({m.col + 1}, {m.row + 1})
                  </span>
                </div>
              ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default GamePage;
