import { useEffect, useState } from 'react';
import { userApi } from '@/api/auth';
import { PublicUser } from '@/types';
import styles from '@/pages/GamePage.module.css';

interface PlayerProfileModalProps {
  userId: string;
  onClose: () => void;
}

/**
 * 방 안에서 플레이어 이름을 누르면 뜨는 간략 프로필.
 * 페이지 이동 없이 모달로 보여줘 이탈 가드(나가기 확인창)가 뜨지 않는다.
 */
const PlayerProfileModal = ({ userId, onClose }: PlayerProfileModalProps) => {
  const [profile, setProfile] = useState<PublicUser | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    setProfile(null);
    userApi
      .getUser(userId)
      .then((res) => {
        if (active) setProfile(res.data.data ?? null);
      })
      .catch((e) => {
        if (active) setError(e?.response?.data?.message ?? '프로필을 불러오지 못했습니다.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [userId]);

  const total = profile?.totalGames ?? 0;
  const winRate = profile && total > 0 ? Math.round((profile.wins / total) * 100) : 0;

  return (
    <div className={styles.profileModalBackdrop} onClick={onClose}>
      <div className={styles.profileModal} onClick={(e) => e.stopPropagation()}>
        <button className={styles.profileModalClose} onClick={onClose} aria-label="닫기">
          ✕
        </button>

        {loading && <p className={styles.profileModalMsg}>불러오는 중...</p>}
        {!loading && error && <p className={styles.profileModalMsg}>{error}</p>}

        {profile && (
          <>
            <div className={styles.profileModalHeader}>
              {profile.profileImageUrl ? (
                <img src={profile.profileImageUrl} alt="" className={styles.profileModalAvatar} />
              ) : (
                <div className={styles.profileModalAvatar}>{profile.nickname.charAt(0)}</div>
              )}
              <h3 className={styles.profileModalName}>{profile.nickname}</h3>
            </div>

            <div className={styles.profileModalStats}>
              <div className={styles.profileModalStat}>
                <span>클래식</span>
                <strong>{profile.classicRating}</strong>
              </div>
              <div className={styles.profileModalStat}>
                <span>피지컬</span>
                <strong>{profile.physicalRating}</strong>
              </div>
              <div className={styles.profileModalStat}>
                <span>전적</span>
                <strong>
                  {profile.wins}승 {profile.losses}패 {profile.draws}무
                </strong>
              </div>
              <div className={styles.profileModalStat}>
                <span>승률</span>
                <strong>{winRate}%</strong>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default PlayerProfileModal;
