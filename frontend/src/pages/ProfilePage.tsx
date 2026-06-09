import { useState, useEffect, FormEvent } from 'react';
import { useAuthStore } from '@/store/authStore';
import { userApi } from '@/api/auth';
import { gameApi } from '@/api/game';
import { GameInfo } from '@/types';
import styles from './ProfilePage.module.css';

const fmtDate = (iso?: string | null) => {
  if (!iso) return '';
  const d = new Date(iso);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
};

const PROVIDER_LABELS: Record<string, string> = {
  LOCAL: '이메일',
  GOOGLE: 'Google',
  KAKAO: '카카오',
  NAVER: '네이버',
};

const ProfilePage = () => {
  const { user, setUser } = useAuthStore();
  const [nickname, setNickname] = useState(user?.nickname ?? '');
  const [isEditing, setIsEditing] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [recent, setRecent] = useState<GameInfo[] | null>(null);

  useEffect(() => {
    gameApi
      .getMyGames(0, 8)
      .then((res) => setRecent(res.data.data?.content ?? []))
      .catch(() => setRecent([]));
  }, []);

  if (!user) return null;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setIsLoading(true);
    try {
      const res = await userApi.updateProfile({ nickname });
      if (res.data.data) {
        setUser(res.data.data);
        setSuccess('프로필이 업데이트되었습니다.');
        setIsEditing(false);
      }
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      setError(axiosErr.response?.data?.message ?? '업데이트에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const winRate = user.totalGames > 0 ? Math.round((user.wins / user.totalGames) * 100) : 0;

  const resultOf = (g: GameInfo): { label: string; cls: string } => {
    if (g.status === 'IN_PROGRESS') return { label: '진행 중', cls: styles.rOngoing };
    if (g.status === 'DRAW') return { label: '무', cls: styles.rDraw };
    if (g.winner?.id === user.id) return { label: '승', cls: styles.rWin };
    return { label: '패', cls: styles.rLoss };
  };
  const opponentOf = (g: GameInfo) =>
    (g.blackPlayer?.id === user.id ? g.whitePlayer : g.blackPlayer)?.nickname ?? '상대';

  return (
    <div className={styles.container}>
      {/* ---- 프로필 헤더 카드 ---- */}
      <div className={styles.card}>
        <div className={styles.profileTop}>
          <div className={styles.avatar}>
            {user.profileImageUrl ? <img src={user.profileImageUrl} alt={user.nickname} />
              : <span>{user.nickname[0].toUpperCase()}</span>}
          </div>
          <div className={styles.profileMain}>
            {isEditing ? (
              <form onSubmit={handleSubmit} className={styles.editForm}>
                <input value={nickname} onChange={(e) => setNickname(e.target.value)}
                  className={styles.nicknameInput} minLength={2} maxLength={15} autoFocus />
                {error && <p className={styles.error}>{error}</p>}
                <div className={styles.editActions}>
                  <button type="submit" disabled={isLoading} className={styles.saveBtn}>저장</button>
                  <button type="button" onClick={() => { setIsEditing(false); setNickname(user.nickname); setError(''); }}
                    className={styles.cancelBtn}>취소</button>
                </div>
              </form>
            ) : (
              <>
                <div className={styles.nicknameRow}>
                  <h1 className={styles.nickname}>{user.nickname}</h1>
                  <button onClick={() => setIsEditing(true)} className={styles.editBtn}>수정</button>
                </div>
                <p className={styles.email}>{user.email}</p>
                <div className={styles.metaRow}>
                  <span className={styles.provider}>{PROVIDER_LABELS[user.provider] ?? user.provider} 로그인</span>
                  <span className={styles.memberSince}>가입일 {fmtDate(user.createdAt)}</span>
                  <span className={styles.currencyTag}>🪙 {user.currency.toLocaleString()}</span>
                </div>
              </>
            )}
            {success && <p className={styles.success}>{success}</p>}
          </div>
        </div>

        <div className={styles.stats}>
          <div className={styles.statItem}><span className={styles.statValue}>{user.totalGames}</span><span className={styles.statLabel}>총 대국</span></div>
          <div className={styles.statItem}><span className={`${styles.statValue} ${styles.win}`}>{user.wins}</span><span className={styles.statLabel}>승</span></div>
          <div className={styles.statItem}><span className={`${styles.statValue} ${styles.loss}`}>{user.losses}</span><span className={styles.statLabel}>패</span></div>
          <div className={styles.statItem}><span className={styles.statValue}>{user.draws}</span><span className={styles.statLabel}>무</span></div>
          <div className={styles.statItem}><span className={`${styles.statValue} ${styles.rate}`}>{winRate}%</span><span className={styles.statLabel}>승률</span></div>
        </div>

        {/* 승률 바 */}
        {user.totalGames > 0 && (
          <div className={styles.rateBar} title={`${user.wins}승 ${user.losses}패 ${user.draws}무`}>
            <div className={styles.rateWin} style={{ flex: user.wins }} />
            <div className={styles.rateDraw} style={{ flex: user.draws }} />
            <div className={styles.rateLoss} style={{ flex: user.losses }} />
          </div>
        )}
      </div>

      {/* ---- 최근 전적 ---- */}
      <div className={styles.card}>
        <h2 className={styles.sectionTitle}>최근 전적</h2>
        <div className={styles.recentList}>
          {recent === null ? (
            <p className={styles.recentEmpty}>불러오는 중...</p>
          ) : recent.length === 0 ? (
            <p className={styles.recentEmpty}>아직 대국 기록이 없습니다.</p>
          ) : (
            recent.map((g) => {
              const r = resultOf(g);
              return (
                <div key={g.id} className={styles.recentItem}>
                  <span className={`${styles.resultChip} ${r.cls}`}>{r.label}</span>
                  <span className={styles.recentOpp}>vs {opponentOf(g)}</span>
                  <span className={styles.recentDate}>{fmtDate(g.finishedAt ?? g.startedAt)}</span>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};

export default ProfilePage;
