import { useState, useEffect, useCallback, FormEvent } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { userApi } from '@/api/auth';
import { gameApi } from '@/api/game';
import { friendApi } from '@/api/friend';
import { shopApi } from '@/api/shop';
import { GameInfo, PublicUser, RelationInfo, StatMode, User, Inventory, ShopItem, ItemType } from '@/types';
import GameRecordViewer from '@/components/game/GameRecordViewer';
import ItemPreview from '@/components/shop/ItemPreview';
import { ITEM_TYPE_META } from '@/constants/itemMeta';
import ModeTabs from '@/components/common/ModeTabs';
import { pickStats, totalStats } from '@/utils/stats';
import styles from './ProfilePage.module.css';

const RECENT_PAGE_SIZE = 10;

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
  const [recentPage, setRecentPage] = useState(0);
  const [recentTotalPages, setRecentTotalPages] = useState(1);
  const [recentLast, setRecentLast] = useState(true);
  const [inventory, setInventory] = useState<Inventory | null>(null);
  const [kifuGame, setKifuGame] = useState<GameInfo | null>(null);
  const [relation, setRelation] = useState<RelationInfo | null>(null);
  const [relBusy, setRelBusy] = useState(false);
  const [statMode, setStatMode] = useState<StatMode>('TOTAL');

  // 다시보기 목록 페이지 로드(본인=내 게임 / 타인=공개 게임). 페이징으로 예전 기록까지 넘겨볼 수 있다.
  const loadRecent = useCallback((page: number) => {
    const req = isOwnProfile
      ? gameApi.getMyGames(page, RECENT_PAGE_SIZE)
      : userId
        ? gameApi.getUserGames(userId, page, RECENT_PAGE_SIZE)
        : null;
    if (!req) return;
    setRecent(null);
    req
      .then((res) => {
        const data = res.data.data;
        setRecent(data?.content ?? []);
        setRecentPage(data?.number ?? page);
        setRecentTotalPages(Math.max(1, data?.totalPages ?? 1));
        setRecentLast(data?.last ?? true);
      })
      .catch(() => {
        setRecent([]);
        setRecentPage(0);
        setRecentTotalPages(1);
        setRecentLast(true);
      });
  }, [isOwnProfile, userId]);

  useEffect(() => {
    if (!user) return;

    setRecent(null);
    setRecentPage(0);
    setInventory(null);
    setKifuGame(null);
    setRelation(null);
    setProfileError('');
    setError('');
    setSuccess('');
    loadRecent(0);

    if (isOwnProfile) {
      setProfile(user);
      setNickname(user.nickname);
      setProfilePrivate(user.profilePrivate);
      // 내 보유 아이템(코스메틱) — 프로필에서 컬렉션을 보여준다(본인만, 인벤토리는 비공개).
      shopApi
        .getInventory()
        .then((res) => setInventory(res.data.data ?? null))
        .catch(() => setInventory(null));
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
    friendApi
      .getRelation(userId)
      .then((res) => setRelation(res.data.data ?? null))
      .catch(() => setRelation(null));
  }, [isOwnProfile, user, userId, loadRecent]);

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
  // 칭호(업적 파생) — 첫 항목이 대표 칭호. 구버전 캐시 대비 ?? [] 로 방어.
  const titles = profile.titles ?? [];
  const mainTitle = titles[0];
  // 선택한 탭(통합/일반/피지컬)의 전적
  const stats = pickStats(
    statMode,
    totalStats(profile.wins, profile.losses, profile.draws),
    profile.classic,
    profile.physical,
  );

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
                  {mainTitle && (
                    <span className={styles.titleChip} title={mainTitle.description}>🏅 {mainTitle.name}</span>
                  )}
                  {canEdit && <button onClick={() => setIsEditing(true)} className={styles.editBtn}>수정</button>}
                </div>
                {ownProfile && (
                  <p className={styles.email}>{ownProfile.email}</p>
                )}
                {titles.length > 0 && (
                  <div className={styles.titleRow}>
                    {titles.map((t) => (
                      <span key={t.key} className={styles.titleBadge} title={t.description}>🏅 {t.name}</span>
                    ))}
                  </div>
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

        <div style={{ display: 'flex', justifyContent: 'center', margin: '4px 0 12px' }}>
          <ModeTabs value={statMode} onChange={setStatMode} />
        </div>

        <div className={styles.stats}>
          <div className={styles.statItem}><span className={styles.statValue}>{stats.totalGames}</span><span className={styles.statLabel}>총 대국</span></div>
          <div className={styles.statItem}><span className={`${styles.statValue} ${styles.win}`}>{stats.wins}</span><span className={styles.statLabel}>승</span></div>
          <div className={styles.statItem}><span className={`${styles.statValue} ${styles.loss}`}>{stats.losses}</span><span className={styles.statLabel}>패</span></div>
          <div className={styles.statItem}><span className={styles.statValue}>{stats.draws}</span><span className={styles.statLabel}>무</span></div>
          <div className={styles.statItem}><span className={`${styles.statValue} ${styles.rate}`}>{stats.winRate}%</span><span className={styles.statLabel}>승률</span></div>
        </div>

        {stats.totalGames > 0 && (
          <div className={styles.rateBar} title={`${stats.wins}승 ${stats.losses}패 ${stats.draws}무`}>
            <div className={styles.rateWin} style={{ flex: stats.wins }} />
            <div className={styles.rateDraw} style={{ flex: stats.draws }} />
            <div className={styles.rateLoss} style={{ flex: stats.losses }} />
          </div>
        )}

        {/* 캐주얼(일반) 전적 — 위 탭 전적은 랭크전 기준, 캐주얼은 레이팅과 무관하게 따로 집계 */}
        {profile.casual && profile.casual.totalGames > 0 && (
          <p style={{ textAlign: 'center', color: 'var(--text-faint)', fontSize: '0.82rem', marginTop: 10 }}>
            😌 캐주얼 전적 {profile.casual.wins}승 {profile.casual.losses}패 {profile.casual.draws}무
          </p>
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

      {isOwnProfile && (
        <div className={styles.card}>
          <div className={styles.itemsHeader}>
            <h2 className={styles.sectionTitle}>내 아이템{inventory ? ` (${inventory.items.length})` : ''}</h2>
            <Link to="/shop" className={styles.shopLink}>상점에서 관리 ▶</Link>
          </div>
          {!inventory ? (
            <p className={styles.recentEmpty}>불러오는 중...</p>
          ) : inventory.items.length === 0 ? (
            <p className={styles.recentEmpty}>아직 보유한 아이템이 없습니다. 상점에서 뽑기를 해보세요!</p>
          ) : (
            // 종류별로 한 줄씩 묶어 보여주고, 한 종류가 많으면 그 줄만 가로로 스크롤한다.
            <div className={styles.itemTypeList}>
              {(Object.keys(ITEM_TYPE_META) as ItemType[])
                .map((type) => ({ type, list: inventory.items.filter((it) => it.itemType === type) }))
                .filter(({ list }) => list.length > 0)
                .map(({ type, list }) => (
                  <div key={type} className={styles.itemTypeRow}>
                    <div className={styles.itemTypeLabel}>
                      <span className={styles.itemTypeIcon}>{ITEM_TYPE_META[type].icon}</span>
                      <span>{ITEM_TYPE_META[type].label}</span>
                      <span className={styles.itemTypeCount}>{list.length}</span>
                    </div>
                    <div className={styles.itemTrack}>
                      {list.map((item: ShopItem) => {
                        const equipped = inventory.activeItems[item.itemType]?.id === item.id;
                        return (
                          <div key={item.id} className={`${styles.itemCard} ${equipped ? styles.itemCardEquipped : ''}`}>
                            <div className={styles.itemThumb}><ItemPreview item={item} /></div>
                            <p className={styles.itemName}>{item.displayName || item.name}</p>
                            {equipped && <span className={styles.itemEquipped}>장착 중</span>}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                ))}
            </div>
          )}
        </div>
      )}

      <div className={styles.card}>
        <h2 className={styles.sectionTitle}>{isOwnProfile ? '대국 다시보기' : '대국 기록'}</h2>
        <p className={styles.sectionHint}>대국을 클릭하면 기보(일반)·리플레이(피지컬)를 한 수씩 다시 볼 수 있어요. (AI 연습 대국 제외)</p>
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
        {recent !== null && recentTotalPages > 1 && (
          <div className={styles.recentPager}>
            <button
              className={styles.pagerBtn}
              disabled={recentPage <= 0}
              onClick={() => loadRecent(recentPage - 1)}
            >
              ← 이전
            </button>
            <span className={styles.pagerInfo}>{recentPage + 1} / {recentTotalPages}</span>
            <button
              className={styles.pagerBtn}
              disabled={recentLast}
              onClick={() => loadRecent(recentPage + 1)}
            >
              다음 →
            </button>
          </div>
        )}
      </div>

      {recordViewer}
    </div>
  );
};

export default ProfilePage;
