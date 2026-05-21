import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { gameApi } from '@/api/game';
import { GameRoom, PageResponse } from '@/types';
import styles from './LobbyPage.module.css';

const LobbyPage = () => {
  const navigate = useNavigate();
  const [rooms, setRooms] = useState<PageResponse<GameRoom> | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [joinCode, setJoinCode] = useState('');
  const [page, setPage] = useState(0);

  const loadRooms = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await gameApi.getWaitingRooms(page);
      setRooms(res.data.data);
    } catch {
      /* ignore */
    } finally {
      setIsLoading(false);
    }
  }, [page]);

  useEffect(() => {
    loadRooms();
    const interval = setInterval(loadRooms, 5000);
    return () => clearInterval(interval);
  }, [loadRooms]);

  const handleCreateRoom = async () => {
    setIsCreating(true);
    try {
      const res = await gameApi.createRoom();
      if (res.data.data) {
        navigate(`/game/${res.data.data.id}`);
      }
    } catch {
      alert('방 생성에 실패했습니다.');
    } finally {
      setIsCreating(false);
    }
  };

  const handleJoinRoom = async (roomCode: string) => {
    try {
      const res = await gameApi.joinRoom(roomCode);
      if (res.data.data) {
        navigate(`/game/${res.data.data.id}`);
      }
    } catch {
      alert('방 참가에 실패했습니다.');
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1 className={styles.title}>게임 로비</h1>
        <div className={styles.actions}>
          <div className={styles.joinByCode}>
            <input
              type="text"
              value={joinCode}
              onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
              placeholder="방 코드 입력"
              maxLength={8}
              className={styles.codeInput}
            />
            <button
              onClick={() => joinCode && handleJoinRoom(joinCode)}
              disabled={!joinCode}
              className={styles.joinBtn}
            >
              참가
            </button>
          </div>
          <button onClick={handleCreateRoom} disabled={isCreating} className={styles.createBtn}>
            {isCreating ? '생성 중...' : '+ 방 만들기'}
          </button>
        </div>
      </div>

      <div className={styles.roomList}>
        {isLoading && rooms === null ? (
          <div className={styles.loading}>로딩 중...</div>
        ) : rooms?.content.length === 0 ? (
          <div className={styles.empty}>
            <p>대기 중인 방이 없습니다.</p>
            <p>새 방을 만들어보세요!</p>
          </div>
        ) : (
          rooms?.content.map((room) => (
            <div key={room.id} className={styles.roomCard}>
              <div className={styles.roomInfo}>
                <span className={styles.roomCode}>{room.roomCode}</span>
                <span className={styles.roomHost}>
                  {room.blackPlayer?.nickname ?? '알 수 없음'} 의 방
                </span>
              </div>
              <div className={styles.roomStatus}>
                <span className={styles.waitingBadge}>대기 중</span>
                <button
                  onClick={() => handleJoinRoom(room.roomCode)}
                  className={styles.enterBtn}
                >
                  입장
                </button>
              </div>
            </div>
          ))
        )}
      </div>

      {rooms && rooms.totalPages > 1 && (
        <div className={styles.pagination}>
          <button
            disabled={rooms.first}
            onClick={() => setPage((p) => p - 1)}
            className={styles.pageBtn}
          >
            이전
          </button>
          <span className={styles.pageInfo}>
            {page + 1} / {rooms.totalPages}
          </span>
          <button
            disabled={rooms.last}
            onClick={() => setPage((p) => p + 1)}
            className={styles.pageBtn}
          >
            다음
          </button>
        </div>
      )}
    </div>
  );
};

export default LobbyPage;
