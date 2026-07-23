import { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { gameApi, CreateRoomOptions, WaitingRoomFilter } from '@/api/game';
import { useAuthStore } from '@/store/authStore';
import { useToast } from '@/contexts/ToastContext';
import { getApiErrorMessage } from '@/utils/error';
import { Room, PageResponse, GameType, TimeLimit, ByoyomiOption, StatMode } from '@/types';
import CreateRoomModal from '@/components/game/CreateRoomModal';
import ModeTabs from '@/components/common/ModeTabs';
import { pickStats, totalStats } from '@/utils/stats';
import styles from './LobbyPage.module.css';

const GAME_TYPE_LABELS: Record<GameType, string> = {
  CLASSIC: '일반 오목',
  PHYSICAL: '피지컬 오목',
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

type LobbyTab = 'waiting' | 'live';

/** 대기 탭의 방 필터. RECOMMENDED는 내 레이팅대(±밴드) 방장 방만 서버가 골라준다. */
type RoomFilter = 'ALL' | 'RANKED' | 'CASUAL' | 'CLASSIC' | 'PHYSICAL' | 'RECOMMENDED';

const ROOM_FILTERS: { key: RoomFilter; label: string }[] = [
  { key: 'ALL', label: '전체' },
  // 랭크전은 추후 추가 예정이라 '🏆 랭크' 필터는 노출하지 않는다(랭크 방이 생성되지 않음).
  { key: 'CASUAL', label: '😌 캐주얼' },
  { key: 'CLASSIC', label: '일반' },
  { key: 'PHYSICAL', label: '피지컬' },
  { key: 'RECOMMENDED', label: '내 레이팅대 추천' },
];

const toFilterParams = (filter: RoomFilter): WaitingRoomFilter => {
  if (filter === 'RECOMMENDED') return { recommended: true };
  if (filter === 'RANKED') return { ranked: true };
  if (filter === 'CASUAL') return { ranked: false };
  if (filter === 'CLASSIC' || filter === 'PHYSICAL') return { gameType: filter };
  return {};
};

const findColor = (room: Room, color: 'BLACK' | 'WHITE') =>
  room.players.find((p) => p.color === color && p.role !== 'SPECTATOR')?.nickname ?? '대기';

const LobbyPage = () => {
  const navigate = useNavigate();
  const showToast = useToast();
  const { user } = useAuthStore();
  const [tab, setTab] = useState<LobbyTab>('waiting');
  const [filter, setFilter] = useState<RoomFilter>('ALL');
  const [rooms, setRooms] = useState<PageResponse<Room> | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [joinCode, setJoinCode] = useState('');
  const [page, setPage] = useState(0);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [statMode, setStatMode] = useState<StatMode>('TOTAL');
  // 사이드바 방 현황 — 필터/탭과 무관한 전체 집계(대기 중 / 진행 중)
  const [counts, setCounts] = useState<{ waiting: number; live: number } | null>(null);

  const loadRooms = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = tab === 'live'
        ? await gameApi.getLiveRooms(page)
        : await gameApi.getWaitingRooms(page, 10, toFilterParams(filter));
      setRooms(res.data.data);
    } catch {
      /* ignore */
    } finally {
      setIsLoading(false);
    }
  }, [page, tab, filter]);

  const loadCounts = useCallback(async () => {
    try {
      const [waitingRes, liveRes] = await Promise.all([
        gameApi.getWaitingRooms(0, 1),
        gameApi.getLiveRooms(0, 1),
      ]);
      setCounts({
        waiting: waitingRes.data.data?.totalElements ?? 0,
        live: liveRes.data.data?.totalElements ?? 0,
      });
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    loadRooms();
    loadCounts();
    const interval = setInterval(() => { loadRooms(); loadCounts(); }, 5000);
    return () => clearInterval(interval);
  }, [loadRooms, loadCounts]);

  const switchTab = (next: LobbyTab) => {
    if (next === tab) return;
    setTab(next);
    setPage(0);
    setRooms(null);
  };

  const switchFilter = (next: RoomFilter) => {
    if (next === filter) return;
    setFilter(next);
    setPage(0);
    setRooms(null);
  };

  const handleCreateRoom = async (options: CreateRoomOptions) => {
    setIsCreating(true);
    try {
      const res = await gameApi.createRoom(options);
      if (res.data.data) {
        setShowCreateModal(false);
        navigate(`/game/${res.data.data.roomCode}`);
      }
    } catch {
      showToast('방 생성에 실패했습니다.', 'error');
    } finally {
      setIsCreating(false);
    }
  };

  const handleJoinRoom = async (roomCode: string) => {
    try {
      const res = await gameApi.joinRoom(roomCode);
      if (res.data.data) navigate(`/game/${res.data.data.roomCode}`);
    } catch (err) {
      showToast(getApiErrorMessage(err, '방 참가에 실패했습니다.'), 'error');
    }
  };

  const handleSpectate = async (roomCode: string) => {
    try {
      const res = await gameApi.spectateRoom(roomCode);
      if (res.data.data) navigate(`/game/${res.data.data.roomCode}`);
    } catch (err) {
      showToast(getApiErrorMessage(err, '관전할 수 없습니다.'), 'error');
    }
  };

  // 선택한 탭(통합/일반/피지컬)의 내 전적
  const myStats = user
    ? pickStats(statMode, totalStats(user.wins, user.losses, user.draws), user.classic, user.physical)
    : totalStats(0, 0, 0);
  const roomCount = rooms?.totalElements ?? 0;

  return (
    <div className={styles.container}>
      <div className={styles.layout}>
        {/* ===== 메인: 방 목록 ===== */}
        <main className={styles.main}>
          <div className={styles.header}>
            <h1 className={styles.title}>대국 로비</h1>
            <button onClick={loadRooms} className={styles.refreshBtn} title="새로고침">↻</button>
          </div>

          <div className={styles.tabs}>
            <button className={tab === 'waiting' ? styles.tabActive : styles.tab} onClick={() => switchTab('waiting')}>
              대기 중{tab === 'waiting' && ` (${roomCount})`}
            </button>
            <button className={tab === 'live' ? styles.tabActive : styles.tab} onClick={() => switchTab('live')}>
              <span className={styles.liveDot} /> 관전{tab === 'live' && ` (${roomCount})`}
            </button>
          </div>

          {tab === 'waiting' && (
            <div className={styles.filterBar}>
              {ROOM_FILTERS.map((f) => (
                <button
                  key={f.key}
                  className={filter === f.key ? styles.filterChipActive : styles.filterChip}
                  onClick={() => switchFilter(f.key)}
                >
                  {f.key === 'RECOMMENDED' && '📈 '}{f.label}
                </button>
              ))}
            </div>
          )}

          <div className={styles.roomList}>
            {isLoading && rooms === null ? (
              <div className={styles.loading}>로딩 중...</div>
            ) : rooms?.content.length === 0 ? (
              <div className={styles.empty}>
                <div className={styles.emptyIcon}>{tab === 'live' ? '👀' : '🪟'}</div>
                {tab === 'live' ? (
                  <p>진행 중인 대국이 없습니다.</p>
                ) : filter === 'RECOMMENDED' ? (
                  <>
                    <p>내 레이팅대의 대기 중인 방이 없습니다.</p>
                    <p><b>전체</b>를 보거나 직접 <b>방 만들기</b>로 시작하세요!</p>
                  </>
                ) : (
                  <>
                    <p>대기 중인 방이 없습니다.</p>
                    <p>오른쪽에서 <b>빠른 대국</b>이나 <b>방 만들기</b>로 시작하세요!</p>
                  </>
                )}
              </div>
            ) : tab === 'live' ? (
              rooms?.content.map((room) => (
                <div key={room.id} className={styles.roomCard}>
                  <div className={styles.roomInfo}>
                    <span className={styles.roomCode}>{room.roomCode}</span>
                    <span className={styles.matchup}>
                      <span className={styles.stoneDotB} />{findColor(room, 'BLACK')}
                      <span className={styles.vs}>vs</span>
                      <span className={styles.stoneDotW} />{findColor(room, 'WHITE')}
                    </span>
                    <span className={styles.roomMeta}>
                      {room.ranked ? '🏆 랭크' : '😌 캐주얼'} · {GAME_TYPE_LABELS[room.gameType]}{room.omokRule === 'RENJU' && ' · 렌주룰'} · {TIME_LIMIT_LABELS[room.timeLimit]}
                      {room.currentGame && ` · ${room.currentGame.gameNumber}번째 판`}
                    </span>
                  </div>
                  <div className={styles.roomStatus}>
                    <span className={styles.liveBadge}><span className={styles.liveDot} /> LIVE</span>
                    <button onClick={() => handleSpectate(room.roomCode)} className={styles.spectateBtn}>
                      관전
                    </button>
                  </div>
                </div>
              ))
            ) : (
              rooms?.content.map((room) => (
                <div key={room.id} className={styles.roomCard}>
                  <div className={styles.roomInfo}>
                    <span className={styles.roomCode}>{room.roomCode}</span>
                    <Link
                      className={styles.roomHost}
                      to={room.host.id === user?.id ? '/profile' : `/profile/${room.host.id}`}
                    >
                      {room.host.nickname} 의 방
                      <span className={styles.ratingTag}>
                        📈 {room.gameType === 'PHYSICAL' ? room.host.physicalRating : room.host.classicRating}
                      </span>
                    </Link>
                    <span className={styles.roomMeta}>
                      {room.ranked ? '🏆 랭크' : '😌 캐주얼'} · {GAME_TYPE_LABELS[room.gameType]}{room.omokRule === 'RENJU' && ' · 렌주룰'} · {TIME_LIMIT_LABELS[room.timeLimit]}
                      {room.byoyomiOption !== 'NONE' && ` · 초읽기 ${BYOYOMI_LABELS[room.byoyomiOption]}`}
                    </span>
                  </div>
                  <div className={styles.roomStatus}>
                    <span className={styles.waitingBadge}>대기 중</span>
                    <button onClick={() => handleSpectate(room.roomCode)} className={styles.spectateBtn}>
                      관전
                    </button>
                    <button onClick={() => handleJoinRoom(room.roomCode)} className={styles.enterBtn}>
                      입장
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>

          {rooms && rooms.totalPages > 1 && (
            <div className={styles.pagination}>
              <button disabled={rooms.first} onClick={() => setPage((p) => p - 1)} className={styles.pageBtn}>
                이전
              </button>
              <span className={styles.pageInfo}>{page + 1} / {rooms.totalPages}</span>
              <button disabled={rooms.last} onClick={() => setPage((p) => p + 1)} className={styles.pageBtn}>
                다음
              </button>
            </div>
          )}
        </main>

        {/* ===== 사이드바 ===== */}
        <aside className={styles.sidebar}>
          <div className={styles.sideCard}>
            <h3 className={styles.sideTitle}>방 만들기</h3>
            <button onClick={() => setShowCreateModal(true)} disabled={isCreating} className={styles.quickBtn}>
              ＋ {isCreating ? '생성 중...' : '새 방 만들기'}
            </button>
            <div className={styles.divider}><span>또는 코드로 참가</span></div>
            <div className={styles.joinByCode}>
              <input
                type="text"
                value={joinCode}
                onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                placeholder="방 코드"
                maxLength={8}
                className={styles.codeInput}
              />
              <button onClick={() => joinCode && handleJoinRoom(joinCode)} disabled={!joinCode} className={styles.joinBtn}>
                참가
              </button>
            </div>
          </div>

          <div className={styles.sideCard}>
            <h3 className={styles.sideTitle}>방 현황</h3>
            <div className={styles.roomStatGrid}>
              <div className={styles.roomStatItem}>
                <b>{counts ? counts.waiting + counts.live : '–'}</b><span>전체 방</span>
              </div>
              <div className={styles.roomStatItem}>
                <b className={styles.statWaiting}>{counts?.waiting ?? '–'}</b><span>대기 중</span>
              </div>
              <div className={styles.roomStatItem}>
                <b className={styles.statLive}>{counts?.live ?? '–'}</b><span>진행 중</span>
              </div>
            </div>
          </div>

          <div className={styles.sideCard}>
            <h3 className={styles.sideTitle}>내 전적</h3>
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 10 }}>
              <ModeTabs value={statMode} onChange={setStatMode} />
            </div>
            <div className={styles.statGrid}>
              <div className={styles.statItem}><b className={styles.win}>{myStats.wins}</b><span>승</span></div>
              <div className={styles.statItem}><b className={styles.loss}>{myStats.losses}</b><span>패</span></div>
              <div className={styles.statItem}><b>{myStats.draws}</b><span>무</span></div>
              <div className={styles.statItem}><b className={styles.rate}>{myStats.winRate}%</b><span>승률</span></div>
            </div>
          </div>

        </aside>
      </div>

      {showCreateModal && (
        <CreateRoomModal
          busy={isCreating}
          onConfirm={handleCreateRoom}
          onClose={() => setShowCreateModal(false)}
        />
      )}
    </div>
  );
};

export default LobbyPage;
