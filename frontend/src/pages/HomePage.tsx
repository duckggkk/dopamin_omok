import { useState, useEffect, FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { gameApi, CreateRoomOptions } from '@/api/game';
import { userApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import { useToast } from '@/contexts/ToastContext';
import { getApiErrorMessage } from '@/utils/error';
import { GameInfo, RankingEntry } from '@/types';
import CreateRoomModal from '@/components/game/CreateRoomModal';
import GameRecordViewer from '@/components/game/GameRecordViewer';
import styles from './HomePage.module.css';

/** 히어로 장식용 정적 오목판 (대각 오목 완성 직전 형세) */
const DecoBoard = () => {
  const N = 7, CELL = 27, PAD = 18;
  const px = PAD * 2 + CELL * (N - 1);
  const black = [[1, 1], [2, 2], [3, 3], [4, 4], [5, 5]];
  const white = [[1, 4], [4, 2], [2, 5], [5, 3]];
  const winStone = '5,5';
  return (
    <svg className={styles.decoBoard} width={px} height={px} viewBox={`0 0 ${px} ${px}`} aria-hidden="true">
      <rect x="0" y="0" width={px} height={px} rx="10" fill="#e3bd72" />
      {Array.from({ length: N }, (_, i) => (
        <g key={i} stroke="#9c7322" strokeWidth="1">
          <line x1={PAD + i * CELL} y1={PAD} x2={PAD + i * CELL} y2={PAD + (N - 1) * CELL} />
          <line x1={PAD} y1={PAD + i * CELL} x2={PAD + (N - 1) * CELL} y2={PAD + i * CELL} />
        </g>
      ))}
      {white.map(([r, c]) => (
        <circle key={`w${r}-${c}`} cx={PAD + c * CELL} cy={PAD + r * CELL} r={CELL / 2 - 3}
          fill="url(#dw)" stroke="#cdc6b6" strokeWidth="0.5" />
      ))}
      {black.map(([r, c]) => (
        <circle key={`b${r}-${c}`} cx={PAD + c * CELL} cy={PAD + r * CELL} r={CELL / 2 - 3}
          fill="url(#db)"
          style={`${r},${c}` === winStone ? { filter: 'drop-shadow(0 0 7px rgba(224,168,63,0.95))' } : undefined} />
      ))}
      <defs>
        <radialGradient id="db" cx="35%" cy="30%" r="65%">
          <stop offset="0%" stopColor="#55504a" /><stop offset="100%" stopColor="#16140f" />
        </radialGradient>
        <radialGradient id="dw" cx="35%" cy="30%" r="70%">
          <stop offset="0%" stopColor="#ffffff" /><stop offset="100%" stopColor="#d6cfbe" />
        </radialGradient>
      </defs>
    </svg>
  );
};

const fmtDate = (iso?: string | null) => {
  if (!iso) return '';
  const d = new Date(iso);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
};

const HomePage = () => {
  const navigate = useNavigate();
  const showToast = useToast();
  const { user } = useAuthStore();
  const [joinCode, setJoinCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [recent, setRecent] = useState<GameInfo[] | null>(null);
  const [topRanking, setTopRanking] = useState<RankingEntry[] | null>(null);
  // 최근 전적에서 선택한 대국 — 기보(일반)/리플레이(피지컬) 다시보기 모달
  const [kifuGame, setKifuGame] = useState<GameInfo | null>(null);

  useEffect(() => {
    gameApi.getMyGames(0, 5).then((res) => setRecent(res.data.data?.content ?? [])).catch(() => setRecent([]));
    userApi.getRanking(3).then((res) => setTopRanking(res.data.data ?? [])).catch(() => setTopRanking([]));
  }, []);

  if (!user) return null;

  const winRate = user.totalGames > 0 ? Math.round((user.wins / user.totalGames) * 100) : 0;

  const createRoom = async (options: CreateRoomOptions) => {
    setBusy(true);
    try {
      const res = await gameApi.createRoom(options);
      if (res.data.data) navigate(`/game/${res.data.data.roomCode}`);
    } catch {
      showToast('방 생성에 실패했습니다.', 'error');
    } finally {
      setBusy(false);
    }
  };

  const startPhysicalPractice = async () => {
    setBusy(true);
    try {
      const res = await gameApi.startAiPractice();
      if (res.data.data) navigate(`/game/${res.data.data.roomCode}`);
    } catch (err) {
      showToast(getApiErrorMessage(err, 'AI 연습을 시작하지 못했습니다.'), 'error');
    } finally {
      setBusy(false);
    }
  };

  // 로컬 개발 전용 — 상대 없이 나 혼자 아레나에 들어가 움직임을 확인한다(prod 빌드에선 버튼 자체가 사라짐).
  const startPhysicalSandbox = async () => {
    setBusy(true);
    try {
      const res = await gameApi.startPhysicalSandbox();
      if (res.data.data) navigate(`/game/${res.data.data.roomCode}`);
    } catch (err) {
      showToast(getApiErrorMessage(err, '혼자 두기 샌드박스를 시작하지 못했습니다.'), 'error');
    } finally {
      setBusy(false);
    }
  };

  const joinByCode = async (e: FormEvent) => {
    e.preventDefault();
    if (!joinCode) return;
    try {
      const res = await gameApi.joinRoom(joinCode);
      if (res.data.data) navigate(`/game/${res.data.data.roomCode}`);
    } catch (err) {
      showToast(getApiErrorMessage(err, '방 참가에 실패했습니다. 코드를 확인하세요.'), 'error');
    }
  };

  const resultOf = (g: GameInfo): { label: string; cls: string } => {
    if (g.status === 'IN_PROGRESS') return { label: '진행 중', cls: styles.rOngoing };
    if (g.status === 'DRAW') return { label: '무', cls: styles.rDraw };
    if (g.winner?.id === user.id) return { label: '승', cls: styles.rWin };
    return { label: '패', cls: styles.rLoss };
  };
  const opponentOf = (g: GameInfo) =>
    (g.blackPlayer?.id === user.id ? g.whitePlayer : g.blackPlayer)?.nickname ?? '상대';

  return (
    <div className={styles.page}>
      {/* ---- 히어로 ---- */}
      <section className={styles.hero}>
        <div className={styles.heroText}>
          <span className={styles.badge}>실시간 · 오목 대국</span>
          <h1 className={styles.heroTitle}>
            도파민 <span className={styles.accent}>오목</span>
          </h1>
          <p className={styles.heroSub}>
            {user.nickname}님, 한 수의 쾌감을 다시. 가로·세로·대각 오목을 먼저 완성하세요.
          </p>
          {/* 모바일에선 2×2 그리드: [방 만들기|대국 로비] / [광장|코드 참가] */}
          <div className={styles.heroActions}>
            <button className={styles.ctaPrimary} onClick={() => setShowCreateModal(true)} disabled={busy}>
              ✚ 방 만들기
            </button>
            <button className={styles.ctaGhost} onClick={() => navigate('/lobby')}>
              🎯 대국 로비
            </button>
            <button className={styles.ctaGhost} onClick={() => navigate('/plaza')}>
              🏛️ 광장
            </button>
            <form className={styles.joinRow} onSubmit={joinByCode}>
              <input
                className={styles.joinInput}
                value={joinCode}
                onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                placeholder="방 코드로 바로 참가"
                maxLength={8}
              />
              <button type="submit" className={styles.joinBtn} disabled={!joinCode}>
                참가
              </button>
            </form>
          </div>
        </div>
        <div className={styles.heroArt}>
          <DecoBoard />
        </div>
      </section>

      {/* ---- 혼자 연습(AI 대국) — 레이팅 대국과 분리된 별도 진입 ---- */}
      <section className={styles.aiBanner}>
        <div className={styles.aiBannerMain}>
          <span className={styles.aiIcon}>🤖</span>
          <div className={styles.aiText}>
            <span className={styles.aiTitle}>
              혼자 연습 <span className={styles.aiTag}>AI 대국</span>
            </span>
            <span className={styles.aiDesc}>
              상대가 없어도 바로 한 판 · <b>레이팅 미반영</b>
            </span>
          </div>
        </div>
        <div className={styles.aiBtns}>
          <button className={styles.aiBtn} onClick={() => navigate('/ai')}>
            🪵 AI 클래식 오목 대전 →
          </button>
          <button className={styles.aiBtn} onClick={startPhysicalPractice} disabled={busy}>
            ⚔️ AI 피지컬 오목 대전 →
          </button>
          {import.meta.env.DEV && (
            <button className={styles.aiBtn} onClick={startPhysicalSandbox} disabled={busy}>
              🧪 혼자 두기(dev) →
            </button>
          )}
        </div>
      </section>

      {/* ---- 내 현황 ---- */}
      <section className={styles.statRow}>
        <div className={styles.statCard}>
          <span className={styles.statIcon}>🪙</span>
          <span className={styles.statValue}>{user.currency.toLocaleString()}</span>
          <span className={styles.statLabel}>보유 재화</span>
        </div>
        <div className={styles.statCard}>
          <span className={styles.statIcon}>📈</span>
          <span className={`${styles.statValue} ${styles.gold}`}>{user.classicRating}</span>
          <span className={styles.statLabel}>일반 레이팅</span>
        </div>
        <div className={styles.statCard}>
          <span className={styles.statIcon}>⚔️</span>
          <span className={`${styles.statValue} ${styles.gold}`}>{user.physicalRating}</span>
          <span className={styles.statLabel}>피지컬 레이팅</span>
        </div>
        <div className={styles.statCard}>
          <span className={styles.statIcon}>🏆</span>
          <span className={`${styles.statValue} ${styles.gold}`}>{winRate}%</span>
          <span className={styles.statLabel}>승률 ({user.wins}승 {user.losses}패)</span>
        </div>
        <Link to="/profile" className={`${styles.statCard} ${styles.statLink}`}>
          <span className={styles.statIcon}>👤</span>
          <span className={styles.statValue}>프로필</span>
          <span className={styles.statLabel}>전적·계정 관리 →</span>
        </Link>
      </section>

      {/* ---- 최근 전적 ---- */}
      <section className={styles.recentCol}>
        <div className={styles.rankHead}>
          <h2 className={styles.colTitle}>최근 전적</h2>
          <button className={styles.rankMore} onClick={() => navigate('/profile')}>전체 보기 →</button>
        </div>
        <div className={styles.recentList}>
          {recent === null ? (
            <p className={styles.recentEmpty}>불러오는 중...</p>
          ) : recent.length === 0 ? (
            <p className={styles.recentEmpty}>아직 대국 기록이 없어요. 첫 대국을 시작해보세요!</p>
          ) : (
            recent.map((g) => {
              const r = resultOf(g);
              return (
                <button key={g.id} className={styles.recentItem} onClick={() => setKifuGame(g)}>
                  <span className={`${styles.resultChip} ${r.cls}`}>{r.label}</span>
                  <span className={styles.recentOpp}>vs {opponentOf(g)}</span>
                  <span className={styles.recentDate}>{fmtDate(g.finishedAt ?? g.startedAt)}</span>
                  <span className={styles.recentKifu}>다시보기 ▶</span>
                </button>
              );
            })
          )}
        </div>
      </section>

      {/* ---- 랭킹 TOP 3 ---- */}
      <section className={styles.rankPreview}>
        <div className={styles.rankHead}>
          <h2 className={styles.colTitle}>랭킹 TOP 3</h2>
          <button className={styles.rankMore} onClick={() => navigate('/ranking')}>전체 보기 →</button>
        </div>
        {topRanking && topRanking.length > 0 ? (
          <div className={styles.podium}>
            {topRanking.map((r) => (
              <div key={r.userId} className={styles.podiumItem}>
                <span className={`${styles.podiumRank} ${r.rank === 1 ? styles.g : r.rank === 2 ? styles.s : styles.b}`}>
                  {r.rank}
                </span>
                <span className={styles.podiumName}>{r.nickname}</span>
                <span className={styles.podiumStat}>{r.wins}승 · 승률 {r.winRate}%</span>
              </div>
            ))}
          </div>
        ) : (
          <p className={styles.recentEmpty}>아직 랭킹이 없어요. 첫 승리에 도전하세요!</p>
        )}
      </section>

      {showCreateModal && (
        <CreateRoomModal
          busy={busy}
          onConfirm={createRoom}
          onClose={() => setShowCreateModal(false)}
        />
      )}

      {/* 최근 전적 다시보기 — 기보(일반)/리플레이(피지컬) 자동 판별 */}
      {kifuGame && <GameRecordViewer game={kifuGame} onClose={() => setKifuGame(null)} />}
    </div>
  );
};

export default HomePage;
