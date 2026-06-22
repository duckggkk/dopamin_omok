import { useState, useEffect, FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { userApi } from '@/api/auth';
import { gameApi } from '@/api/game';
import { friendApi } from '@/api/friend';
import { GameInfo, PublicUser, RelationInfo, User } from '@/types';
import GameRecordViewer from '@/components/game/GameRecordViewer';
import styles from './ProfilePage.module.css';

type ProfileView = User | PublicUser;

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

const isUser = (profile: ProfileView): profile is User => 'email' in profile;

const ProfilePage = () => {
  const { userId } = useParams<{ userId?: string }>();
  const { user, setUser } = useAuthStore();
  const isOwnProfile = !userId || userId === user?.id;

  const [profile, setProfile] = useState<ProfileView | null>(isOwnProfile ? user : null);
  const [nickname, setNickname] = useState(user?.nickname ?? '');
  const [profilePrivate, setProfilePrivate] = useState(user?.profilePrivate ?? false);
  const [isEditing, setIsEditing] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [profileError, setProfileError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [recent, setRecent] = useState<GameInfo[] | null>(null);
  const [kifuGame, setKifuGame] = useState<GameInfo | null>(null);
  const [relation, setRelation] = useState<RelationInfo | null>(null);
  const [relBusy, setRelBusy] = useState(false);

  useEffect(() => {
    if (!user) return;

    setRecent(null);
    setKifuGame(null);
    setRelation(null);
    setProfileError('');
    setError('');
    setSuccess('');

    if (isOwnProfile) {
      setProfile(user);
      setNickname(user.nickname);
      setProfilePrivate(user.profilePrivate);
      gameApi
        .getMyGames(0, 10)
        .then((res) => setRecent(res.data.data?.content ?? []))
        .catch(() => setRecent([]));
      return;
    }

    if (!userId) return;
    userApi
      .getUser(userId)
      .then((res) => {
        const publicProfile = res.data.data;
        setProfile(publicProfile ?? null);
      })
      .catch((err: unknown) => {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setProfile(null);
        setProfileError(axiosErr.response?.data?.message ?? '프로필을 불러오지 못했습니다.');
      });
    gameApi
      .getUserGames(userId, 0, 10)
      .then((res) => setRecent(res.data.data?.content ?? []))
      .catch(() => setRecent([]));
    friendApi
      .getRelation(userId)
      .then((res) => setRelation(res.data.data ?? null))
      .catch(() => setRelation(null));
  }, [isOwnProfile, user, userId]);

  if (!user) return null;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setIsLoading(true);
    try {
      const res = await userApi.updateProfile({ nickname, profilePrivate });
      if (res.data.data) {
        setUser(res.data.data);
        setProfile(res.data.data);
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

  if (profileError) {
    return (
      <div className={styles.container}>
        <div className={styles.card}>
          <div className={styles.privateView}>
            <h1 className={styles.nickname}>비공개 프로필</h1>
            <p className={styles.sectionHint}>{profileError}</p>
          </div>
        </div>
      </div>
    );
  }

  if (!profile) {
    return (
      <div className={styles.container}>
        <div className={styles.card}>
          <p className={styles.recentEmpty}>프로필을 불러오는 중...</p>
        </div>
      </div>
    );
  }

  const canEdit = isOwnProfile && isUser(profile);
  const ownProfile = canEdit ? profile : null;
  const winRate = profile.totalGames > 0 ? Math.round((profile.wins / profile.totalGames) * 100) : 0;

  const doFriendAction = async (action: 'add' | 'cancel' | 'accept' | 'reject' | 'unfriend') => {
    if (!userId || relBusy) return;
    if (action === 'unfriend' && !window.confirm(`'${profile.nickname}'님을 친구에서 삭제할까요?`)) return;
    setRelBusy(true);
    try {
      if (action === 'add') await friendApi.sendRequest(profile.nickname);
      else if (action === 'accept') await friendApi.accept(userId);
      else await friendApi.remove(userId); // cancel / reject / unfriend 공용
      const res = await friendApi.getRelation(userId);
      setRelation(res.data.data ?? null);
    } catch {
      /* 무시 — 관계는 다음 조회 때 반영 */
    } finally {
      setRelBusy(false);
    }
  };

  const resultOf = (g: GameInfo): { label: string; cls: string } => {
    if (g.status === 'IN_PROGRESS') return { label: '진행 중', cls: styles.rOngoing };
    if (g.status === 'DRAW') return { label: '무', cls: styles.rDraw };
    if (g.winner?.id === profile.id) return { label: '승', cls: styles.rWin };
    return { label: '패', cls: styles.rLoss };
  };
  const opponentOf = (g: GameInfo) =>
    (g.blackPlayer?.id === profile.id ? g.whitePlayer : g.blackPlayer)?.nickname ?? '상대';

  const recordViewer = kifuGame && (
    <GameRecordViewer
      game={kifuGame}
      onClose={() => setKifuGame(null)}
      loadMoves={!isOwnProfile ? (gameId) => gameApi.getUserGameMoves(profile.id, gameId) : undefined}
      loadReplay={!isOwnProfile ? (gameId) => gameApi.getUserPhysicalReplay(profile.id, gameId) : undefined}
    />
  );

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <div className={styles.profileTop}>
          <div className={styles.avatar}>
            {profile.profileImageUrl ? <img src={profile.profileImageUrl} alt={profile.nickname} />
              : <span>{profile.nickname[0].toUpperCase()}</span>}
          </div>
          <div className={styles.profileMain}>
            {canEdit && isEditing ? (
              <form onSubmit={handleSubmit} className={styles.editForm}>
                <input value={nickname} onChange={(e) => setNickname(e.target.value)}
                  className={styles.nicknameInput} minLength={2} maxLength={15} autoFocus />
                <label className={styles.privacyToggle}>
                  <input
                    type="checkbox"
                    checked={profilePrivate}
                    onChange={(e) => setProfilePrivate(e.target.checked)}
                  />
                  <span>프로필 비공개</span>
                </label>
                {error && <p className={styles.error}>{error}</p>}
                <div className={styles.editActions}>
                  <button type="submit" disabled={isLoading} className={styles.saveBtn}>저장</button>
                  <button
                    type="button"
                    onClick={() => {
                      setIsEditing(false);
                      setNickname(profile.nickname);
                      setProfilePrivate(profile.profilePrivate);
                      setError('');
                    }}
                    className={styles.cancelBtn}
                  >
                    취소
                  </button>
                </div>
              </form>
            ) : (
              <>
                <div className={styles.nicknameRow}>
                  <h1 className={styles.nickname}>{profile.nickname}</h1>
                  {canEdit && <button onClick={() => setIsEditing(true)} className={styles.editBtn}>수정</button>}
                </div>
                {ownProfile && (
                  <p className={styles.email}>{ownProfile.email}</p>
                )}
                <div className={styles.metaRow}>
                  {ownProfile && <span className={styles.provider}>{PROVIDER_LABELS[ownProfile.provider] ?? ownProfile.provider} 로그인</span>}
                  <span className={styles.memberSince}>가입일 {fmtDate(profile.createdAt)}</span>
                  {ownProfile && <span className={styles.currencyTag}>🪙 {ownProfile.currency.toLocaleString()}</span>}
                  {profile.profilePrivate && <span className={styles.privacyBadge}>비공개</span>}
                </div>
              </>
            )}
            {success && <p className={styles.success}>{success}</p>}
          </div>
        </div>

        <div className={styles.ratingRow}>
          <div className={styles.ratingCard}>
            <span className={styles.ratingIcon}>📈</span>
            <span className={styles.ratingBody}>
              <span className={styles.ratingValue}>{profile.classicRating}</span>
              <span className={styles.ratingLabel}>일반 오목 레이팅</span>
            </span>
          </div>
          <div className={styles.ratingCard}>
            <span className={styles.ratingIcon}>⚔️</span>
            <span className={styles.ratingBody}>
              <span className={styles.ratingValue}>{profile.physicalRating}</span>
              <span className={styles.ratingLabel}>피지컬 오목 레이팅</span>
            </span>
          </div>
        </div>

        <div className={styles.stats}>
          <div className={styles.statItem}><span className={styles.statValue}>{profile.totalGames}</span><span className={styles.statLabel}>총 대국</span></div>
          <div className={styles.statItem}><span className={`${styles.statValue} ${styles.win}`}>{profile.wins}</span><span className={styles.statLabel}>승</span></div>
          <div className={styles.statItem}><span className={`${styles.statValue} ${styles.loss}`}>{profile.losses}</span><span className={styles.statLabel}>패</span></div>
          <div className={styles.statItem}><span className={styles.statValue}>{profile.draws}</span><span className={styles.statLabel}>무</span></div>
          <div className={styles.statItem}><span className={`${styles.statValue} ${styles.rate}`}>{winRate}%</span><span className={styles.statLabel}>승률</span></div>
        </div>

        {profile.totalGames > 0 && (
          <div className={styles.rateBar} title={`${profile.wins}승 ${profile.losses}패 ${profile.draws}무`}>
            <div className={styles.rateWin} style={{ flex: profile.wins }} />
            <div className={styles.rateDraw} style={{ flex: profile.draws }} />
            <div className={styles.rateLoss} style={{ flex: profile.losses }} />
          </div>
        )}

        {!isOwnProfile && relation && relation.relation !== 'SELF' && (
          <div className={styles.friendPanel}>
            <div className={styles.h2hRow}>
              <span className={styles.h2hLabel}>나와의 상대전적</span>
              <span className={styles.h2hValue}>
                {relation.headToHead.wins}승 {relation.headToHead.losses}패 {relation.headToHead.draws}무
              </span>
            </div>
            <div className={styles.friendActions}>
              {relation.relation === 'NONE' && (
                <button className={styles.friendAddBtn} disabled={relBusy} onClick={() => doFriendAction('add')}>
                  + 친구 추가
                </button>
              )}
              {relation.relation === 'REQUEST_SENT' && (
                <button className={styles.friendGhostBtn} disabled={relBusy} onClick={() => doFriendAction('cancel')}>
                  요청 보냄 · 취소
                </button>
              )}
              {relation.relation === 'REQUEST_RECEIVED' && (
                <>
                  <button className={styles.friendAddBtn} disabled={relBusy} onClick={() => doFriendAction('accept')}>
                    요청 수락
                  </button>
                  <button className={styles.friendGhostBtn} disabled={relBusy} onClick={() => doFriendAction('reject')}>
                    거절
                  </button>
                </>
              )}
              {relation.relation === 'FRIENDS' && (
                <button className={styles.friendGhostBtn} disabled={relBusy} onClick={() => doFriendAction('unfriend')}>
                  ✓ 친구 · 끊기
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      <div className={styles.card}>
        <h2 className={styles.sectionTitle}>{isOwnProfile ? '최근 10경기 다시보기' : '최근 10경기'}</h2>
        <p className={styles.sectionHint}>대국을 클릭하면 기보(일반)·리플레이(피지컬)를 한 수씩 다시 볼 수 있어요.</p>
        <div className={styles.recentList}>
          {recent === null ? (
            <p className={styles.recentEmpty}>불러오는 중...</p>
          ) : recent.length === 0 ? (
            <p className={styles.recentEmpty}>아직 대국 기록이 없습니다.</p>
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
      </div>

      {recordViewer}
    </div>
  );
};

export default ProfilePage;
