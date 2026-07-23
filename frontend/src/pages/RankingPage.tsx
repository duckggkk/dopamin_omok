import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { userApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import { RankingEntry, StatMode } from '@/types';
import ModeTabs from '@/components/common/ModeTabs';
import styles from './RankingPage.module.css';

const SUBTITLES: Record<StatMode, string> = {
  TOTAL: '통합 승수 기준 상위 플레이어 — 회원 간 대국은 랭크·캐주얼 구분 없이 모두 집계됩니다.',
  CLASSIC: '일반 오목 레이팅 기준 상위 플레이어',
  PHYSICAL: '피지컬 오목 레이팅 기준 상위 플레이어',
};

const RankingPage = () => {
  const { user } = useAuthStore();
  const isGuest = !!user?.guest;
  const [mode, setMode] = useState<StatMode>('TOTAL');
  const [ranking, setRanking] = useState<RankingEntry[] | null>(null);

  useEffect(() => {
    setRanking(null);
    userApi
      .getRanking(50, mode)
      .then((res) => setRanking(res.data.data ?? []))
      .catch(() => setRanking([]));
  }, [mode]);

  const medalClass = (rank: number) =>
    rank === 1 ? styles.gold : rank === 2 ? styles.silver : rank === 3 ? styles.bronze : '';

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1 className={styles.title}>랭킹</h1>
        <p className={styles.subtitle}>{SUBTITLES[mode]}</p>
        {isGuest && (
          <p className={styles.guestHint}>
            랭킹에 이름을 올리려면 <Link to="/register">회원가입</Link>이 필요합니다.
          </p>
        )}
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: 12 }}>
          <ModeTabs value={mode} onChange={setMode} />
        </div>
      </div>

      {ranking === null ? (
        <div className={styles.state}>불러오는 중...</div>
      ) : ranking.length === 0 ? (
        <div className={styles.state}>
          <div className={styles.stateIcon}>🏆</div>
          <p>아직 랭킹이 없습니다.</p>
          <p className={styles.stateHint}>첫 승리의 주인공이 되어보세요!</p>
        </div>
      ) : (
        <div className={styles.board}>
          <div className={styles.headRow}>
            <span className={styles.cRank}>순위</span>
            <span className={styles.cPlayer}>플레이어</span>
            <span className={styles.cRecord}>전적</span>
            <span className={styles.cRating}>일반</span>
            <span className={styles.cRatingP}>피지컬</span>
            <span className={styles.cRate}>승률</span>
          </div>
          {ranking.map((r) => {
            const isMe = r.userId === user?.id;
            // 게스트는 순위표 열람만 — 프로필은 멤버 전용이라 링크를 걸면 눌러도 홈으로 튕긴다.
            const player = (
              <>
                <span className={styles.avatar}>
                  {r.profileImageUrl ? <img src={r.profileImageUrl} alt="" /> : r.nickname[0].toUpperCase()}
                </span>
                <span className={styles.name}>{r.nickname}</span>
                {isMe && <span className={styles.meBadge}>나</span>}
              </>
            );
            return (
              <div key={r.userId} className={`${styles.row} ${isMe ? styles.me : ''}`}>
                <span className={styles.cRank}>
                  <span className={`${styles.rankNum} ${medalClass(r.rank)}`}>{r.rank}</span>
                </span>
                {isGuest ? (
                  <span className={styles.cPlayer}>{player}</span>
                ) : (
                  <Link to={isMe ? '/profile' : `/profile/${r.userId}`} className={styles.cPlayer}>{player}</Link>
                )}
                <span className={styles.cRecord}>
                  <b className={styles.w}>{r.wins}</b>승 <b className={styles.l}>{r.losses}</b>패 {r.draws}무
                </span>
                <span className={styles.cRating} style={mode === 'CLASSIC' ? { fontWeight: 700, color: '#e0c97f' } : undefined}>{r.classicRating}</span>
                <span className={styles.cRatingP} style={mode === 'PHYSICAL' ? { fontWeight: 700, color: '#e0c97f' } : undefined}>{r.physicalRating}</span>
                <span className={styles.cRate}>{r.winRate}%</span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default RankingPage;
