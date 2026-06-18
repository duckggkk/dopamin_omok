import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { userApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import { RankingEntry } from '@/types';
import styles from './RankingPage.module.css';

const RankingPage = () => {
  const { user } = useAuthStore();
  const [ranking, setRanking] = useState<RankingEntry[] | null>(null);

  useEffect(() => {
    userApi
      .getRanking(50)
      .then((res) => setRanking(res.data.data ?? []))
      .catch(() => setRanking([]));
  }, []);

  const medalClass = (rank: number) =>
    rank === 1 ? styles.gold : rank === 2 ? styles.silver : rank === 3 ? styles.bronze : '';

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1 className={styles.title}>랭킹</h1>
        <p className={styles.subtitle}>승수 기준 상위 플레이어 — 한 판이라도 둔 플레이어만 집계됩니다.</p>
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
            return (
              <div key={r.userId} className={`${styles.row} ${isMe ? styles.me : ''}`}>
                <span className={styles.cRank}>
                  <span className={`${styles.rankNum} ${medalClass(r.rank)}`}>{r.rank}</span>
                </span>
                <Link to={isMe ? '/profile' : `/profile/${r.userId}`} className={styles.cPlayer}>
                  <span className={styles.avatar}>
                    {r.profileImageUrl ? <img src={r.profileImageUrl} alt="" /> : r.nickname[0].toUpperCase()}
                  </span>
                  <span className={styles.name}>{r.nickname}</span>
                  {isMe && <span className={styles.meBadge}>나</span>}
                </Link>
                <span className={styles.cRecord}>
                  <b className={styles.w}>{r.wins}</b>승 <b className={styles.l}>{r.losses}</b>패 {r.draws}무
                </span>
                <span className={styles.cRating}>{r.classicRating}</span>
                <span className={styles.cRatingP}>{r.physicalRating}</span>
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
