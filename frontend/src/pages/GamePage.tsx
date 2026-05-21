import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { gameApi } from '@/api/game';
import { useAuthStore } from '@/store/authStore';
import { useWebSocket } from '@/hooks/useWebSocket';
import OmokBoard from '@/components/game/OmokBoard';
import { GameRoom, GameMove, Board, StoneColor } from '@/types';
import styles from './GamePage.module.css';

const BOARD_SIZE = 15;

const createEmptyBoard = (): Board =>
  Array.from({ length: BOARD_SIZE }, () => Array(BOARD_SIZE).fill(null));

const GamePage = () => {
  const { gameId } = useParams<{ gameId: string }>();
  const navigate = useNavigate();
  const { user } = useAuthStore();

  const [room, setRoom] = useState<GameRoom | null>(null);
  const [board, setBoard] = useState<Board>(createEmptyBoard());
  const [moves, setMoves] = useState<GameMove[]>([]);
  const [lastMove, setLastMove] = useState<{ row: number; col: number } | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [statusMessage, setStatusMessage] = useState('');

  const numericGameId = Number(gameId);

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
    }
  }, []);

  const handleGameStatus = useCallback((data: unknown) => {
    const res = data as { success: boolean; data?: GameRoom };
    if (res.success && res.data) {
      setRoom(res.data);
    }
  }, []);

  const { sendMove, sendSurrender } = useWebSocket({
    gameId: numericGameId,
    onMove: handleMove,
    onGameStatus: handleGameStatus,
  });

  useEffect(() => {
    const load = async () => {
      try {
        const [roomRes, movesRes] = await Promise.all([
          gameApi.getGame(numericGameId),
          gameApi.getGameMoves(numericGameId),
        ]);

        if (roomRes.data.data) setRoom(roomRes.data.data);
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
      } catch {
        navigate('/lobby');
      } finally {
        setIsLoading(false);
      }
    };
    load();
  }, [numericGameId, navigate]);

  useEffect(() => {
    if (!room || !user) return;
    const color = room.blackPlayer?.id === user.id ? 'BLACK'
                : room.whitePlayer?.id === user.id ? 'WHITE'
                : null;

    switch (room.status) {
      case 'WAITING':
        setStatusMessage('상대방을 기다리는 중...');
        break;
      case 'IN_PROGRESS':
        setStatusMessage(room.currentTurn === color ? '당신의 차례입니다!' : '상대방 차례입니다...');
        break;
      case 'FINISHED':
        setStatusMessage(room.winner?.id === user.id ? '승리했습니다!' : '패배했습니다.');
        break;
      case 'DRAW':
        setStatusMessage('무승부입니다!');
        break;
      case 'ABANDONED':
        setStatusMessage('상대방이 게임을 포기했습니다.');
        break;
    }
  }, [room, user]);

  if (isLoading) {
    return <div className={styles.loading}>게임을 불러오는 중...</div>;
  }

  if (!room) return null;

  const myColor: StoneColor | null = user
    ? (room.blackPlayer?.id === user.id ? 'BLACK' : room.whitePlayer?.id === user.id ? 'WHITE' : null)
    : null;

  const handlePlaceStone = (row: number, col: number) => {
    sendMove(row, col);
  };

  const handleSurrender = () => {
    if (window.confirm('정말 기권하시겠습니까?')) {
      sendSurrender();
    }
  };

  const opponent = myColor === 'BLACK' ? room.whitePlayer : room.blackPlayer;
  const isGameOver = ['FINISHED', 'DRAW', 'ABANDONED'].includes(room.status);

  return (
    <div className={styles.container}>
      <div className={styles.gameArea}>
        <div className={styles.playerCard}>
          <div className={styles.stoneBlack} />
          <div>
            <p className={styles.playerName}>{room.blackPlayer?.nickname ?? '대기 중'}</p>
            <p className={styles.playerLabel}>흑 (선)</p>
          </div>
          {room.currentTurn === 'BLACK' && room.status === 'IN_PROGRESS' && (
            <span className={styles.turnIndicator}>●</span>
          )}
        </div>

        <div className={styles.boardWrapper}>
          <p className={styles.status}>{statusMessage}</p>
          <OmokBoard
            board={board}
            currentTurn={room.currentTurn}
            myColor={myColor}
            onPlaceStone={handlePlaceStone}
            lastMove={lastMove}
            disabled={isGameOver || room.status === 'WAITING'}
          />
        </div>

        <div className={styles.playerCard}>
          <div className={styles.stoneWhite} />
          <div>
            <p className={styles.playerName}>{room.whitePlayer?.nickname ?? '대기 중'}</p>
            <p className={styles.playerLabel}>백</p>
          </div>
          {room.currentTurn === 'WHITE' && room.status === 'IN_PROGRESS' && (
            <span className={styles.turnIndicator}>●</span>
          )}
        </div>
      </div>

      <div className={styles.sidebar}>
        <div className={styles.roomInfo}>
          <p className={styles.roomCode}>방 코드: <strong>{room.roomCode}</strong></p>
          <p className={styles.moveCount}>수: {moves.length}</p>
        </div>

        {room.status === 'WAITING' && (
          <div className={styles.waitingInfo}>
            <p>방 코드를 공유하여 상대방을 초대하세요</p>
            <button
              onClick={() => navigator.clipboard.writeText(room.roomCode)}
              className={styles.copyBtn}
            >
              코드 복사
            </button>
          </div>
        )}

        {room.status === 'IN_PROGRESS' && myColor && (
          <button onClick={handleSurrender} className={styles.surrenderBtn}>
            기권
          </button>
        )}

        {isGameOver && (
          <button onClick={() => navigate('/lobby')} className={styles.lobbyBtn}>
            로비로 돌아가기
          </button>
        )}

        <div className={styles.moveHistory}>
          <h3>기보</h3>
          <div className={styles.moveList}>
            {[...moves].reverse().slice(0, 20).map((m) => (
              <div key={m.id} className={styles.moveItem}>
                <span className={m.color === 'BLACK' ? styles.blackDot : styles.whiteDot} />
                <span>{m.moveNumber}. {m.playerNickname}</span>
                <span className={styles.movePos}>({m.row + 1}, {m.col + 1})</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default GamePage;
