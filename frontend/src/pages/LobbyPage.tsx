import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { gameApi, CreateRoomOptions } from '@/api/game';
import { Room, PageResponse, GameType, TimeLimit, ByoyomiOption } from '@/types';
import styles from './LobbyPage.module.css';

const GAME_TYPE_LABELS: Record<GameType, string> = {
  CLASSIC: '클래식 (표준 오목)',
  CARD: '카드 오목',
};

const TIME_LIMIT_LABELS: Record<TimeLimit, string> = {
  UNLIMITED: '무제한',
  ONE_MIN: '1분',
  THREE_MIN: '3분',
  FIVE_MIN: '5분',
  TEN_MIN: '10분',
};

const BYOYOMI_LABELS: Record<ByoyomiOption, string> = {
  NONE: '없음',
  TEN_SEC: '10초',
  FIFTEEN_SEC: '15초',
  THIRTY_SEC: '30초',
};

const DEFAULT_OPTIONS: CreateRoomOptions = {
  gameType: 'CLASSIC',
  timeLimit: 'UNLIMITED',
  byoyomiOption: 'NONE',
};

const LobbyPage = () => {
  const navigate = useNavigate();
  const [rooms, setRooms] = useState<PageResponse<Room> | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [joinCode, setJoinCode] = useState('');
  const [page, setPage] = useState(0);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createOptions, setCreateOptions] = useState<CreateRoomOptions>(DEFAULT_OPTIONS);

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
      const res = await gameApi.createRoom(createOptions);
      if (res.data.data) {
        setShowCreateModal(false);
        navigate(`/game/${res.data.data.roomCode}`);
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
        navigate(`/game/${res.data.data.roomCode}`);
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
          <button
            onClick={() => {
              setCreateOptions(DEFAULT_OPTIONS);
              setShowCreateModal(true);
            }}
            className={styles.createBtn}
          >
            + 방 만들기
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
                  {room.host.nickname} 의 방
                </span>
                <span className={styles.roomMeta}>
                  {GAME_TYPE_LABELS[room.gameType]} · {TIME_LIMIT_LABELS[room.timeLimit]}
                  {room.byoyomiOption !== 'NONE' && ` · 초읽기 ${BYOYOMI_LABELS[room.byoyomiOption]}`}
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

      {/* 방 생성 모달 */}
      {showCreateModal && (
        <div className={styles.modalBackdrop} onClick={() => setShowCreateModal(false)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <h2 className={styles.modalTitle}>방 설정</h2>

            <div className={styles.formGroup}>
              <label className={styles.formLabel}>게임 타입</label>
              <div className={styles.radioGroup}>
                {(Object.keys(GAME_TYPE_LABELS) as GameType[]).map((key) => (
                  <label key={key} className={`${styles.radioOption} ${createOptions.gameType === key ? styles.radioSelected : ''}`}>
                    <input
                      type="radio"
                      name="gameType"
                      value={key}
                      checked={createOptions.gameType === key}
                      onChange={() => setCreateOptions((o) => ({ ...o, gameType: key }))}
                    />
                    {GAME_TYPE_LABELS[key]}
                  </label>
                ))}
              </div>
            </div>

            <div className={styles.formGroup}>
              <label className={styles.formLabel}>제한 시간 (1수당)</label>
              <div className={styles.radioGroup}>
                {(Object.keys(TIME_LIMIT_LABELS) as TimeLimit[]).map((key) => (
                  <label key={key} className={`${styles.radioOption} ${createOptions.timeLimit === key ? styles.radioSelected : ''}`}>
                    <input
                      type="radio"
                      name="timeLimit"
                      value={key}
                      checked={createOptions.timeLimit === key}
                      onChange={() => setCreateOptions((o) => ({ ...o, timeLimit: key }))}
                    />
                    {TIME_LIMIT_LABELS[key]}
                  </label>
                ))}
              </div>
            </div>

            <div className={styles.formGroup}>
              <label className={styles.formLabel}>초읽기</label>
              <div className={styles.radioGroup}>
                {(Object.keys(BYOYOMI_LABELS) as ByoyomiOption[]).map((key) => (
                  <label key={key} className={`${styles.radioOption} ${createOptions.byoyomiOption === key ? styles.radioSelected : ''}`}>
                    <input
                      type="radio"
                      name="byoyomiOption"
                      value={key}
                      checked={createOptions.byoyomiOption === key}
                      onChange={() => setCreateOptions((o) => ({ ...o, byoyomiOption: key }))}
                    />
                    {BYOYOMI_LABELS[key]}
                  </label>
                ))}
              </div>
            </div>

            <div className={styles.modalActions}>
              <button
                onClick={() => setShowCreateModal(false)}
                className={styles.cancelBtn}
              >
                취소
              </button>
              <button
                onClick={handleCreateRoom}
                disabled={isCreating}
                className={styles.confirmBtn}
              >
                {isCreating ? '생성 중...' : '방 만들기'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default LobbyPage;
