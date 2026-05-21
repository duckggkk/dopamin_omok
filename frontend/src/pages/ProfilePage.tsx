import { useState, FormEvent } from 'react';
import { useAuthStore } from '@/store/authStore';
import { userApi } from '@/api/auth';
import styles from './ProfilePage.module.css';

const ProfilePage = () => {
  const { user, setUser } = useAuthStore();
  const [nickname, setNickname] = useState(user?.nickname ?? '');
  const [isEditing, setIsEditing] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isLoading, setIsLoading] = useState(false);

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

  const winRate = user.totalGames > 0
    ? Math.round((user.wins / user.totalGames) * 100)
    : 0;

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <div className={styles.avatar}>
          {user.profileImageUrl ? (
            <img src={user.profileImageUrl} alt={user.nickname} />
          ) : (
            <span>{user.nickname[0].toUpperCase()}</span>
          )}
        </div>

        {isEditing ? (
          <form onSubmit={handleSubmit} className={styles.editForm}>
            <input
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              className={styles.nicknameInput}
              minLength={2}
              maxLength={15}
            />
            {error && <p className={styles.error}>{error}</p>}
            <div className={styles.editActions}>
              <button type="submit" disabled={isLoading} className={styles.saveBtn}>
                저장
              </button>
              <button type="button" onClick={() => { setIsEditing(false); setNickname(user.nickname); }} className={styles.cancelBtn}>
                취소
              </button>
            </div>
          </form>
        ) : (
          <div className={styles.nicknameRow}>
            <h1 className={styles.nickname}>{user.nickname}</h1>
            <button onClick={() => setIsEditing(true)} className={styles.editBtn}>수정</button>
          </div>
        )}

        {success && <p className={styles.success}>{success}</p>}

        <p className={styles.email}>{user.email}</p>
        <span className={styles.provider}>{user.provider}</span>

        <div className={styles.stats}>
          <div className={styles.statItem}>
            <span className={styles.statValue}>{user.totalGames}</span>
            <span className={styles.statLabel}>총 경기</span>
          </div>
          <div className={styles.statItem}>
            <span className={`${styles.statValue} ${styles.win}`}>{user.wins}</span>
            <span className={styles.statLabel}>승</span>
          </div>
          <div className={styles.statItem}>
            <span className={`${styles.statValue} ${styles.loss}`}>{user.losses}</span>
            <span className={styles.statLabel}>패</span>
          </div>
          <div className={styles.statItem}>
            <span className={styles.statValue}>{user.draws}</span>
            <span className={styles.statLabel}>무</span>
          </div>
          <div className={styles.statItem}>
            <span className={`${styles.statValue} ${styles.rate}`}>{winRate}%</span>
            <span className={styles.statLabel}>승률</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProfilePage;
