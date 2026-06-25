import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { authApi } from '@/api/auth';
import SettingsModal from './SettingsModal';
import NotificationBell from './NotificationBell';
import styles from './Navbar.module.css';

const IconHome = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2"
    strokeLinecap="round" strokeLinejoin="round"><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V21h14V9.5" /></svg>
);
const IconPlay = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2"
    strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="8.5" /><circle cx="12" cy="12" r="2.3" fill="currentColor" stroke="none" /></svg>
);
const IconRank = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" stroke="none">
    <rect x="3" y="12" width="4.4" height="8" rx="1" /><rect x="9.8" y="5" width="4.4" height="15" rx="1" /><rect x="16.6" y="9" width="4.4" height="11" rx="1" />
  </svg>
);
const IconShop = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2"
    strokeLinecap="round" strokeLinejoin="round"><path d="M5 8h14l-1 12H6L5 8Z" /><path d="M9 8V6a3 3 0 0 1 6 0v2" /></svg>
);
const IconPlaza = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2"
    strokeLinecap="round" strokeLinejoin="round"><circle cx="9" cy="8" r="3" /><circle cx="17" cy="9" r="2.2" /><path d="M3.2 19c0-3.1 2.6-5.2 5.8-5.2s5.8 2.1 5.8 5.2" /><path d="M15.5 19c0-2.3 1.3-3.9 3.1-3.9" /></svg>
);
const IconFriends = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2"
    strokeLinecap="round" strokeLinejoin="round"><circle cx="9" cy="8" r="3.2" /><path d="M3.5 19c0-3 2.5-5 5.5-5s5.5 2 5.5 5" /><path d="M16 7.5a2.6 2.6 0 0 1 0 5.2" /><path d="M17.5 14.2c2 .5 3.5 2.2 3.5 4.8" /></svg>
);
const IconGuide = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2"
    strokeLinecap="round" strokeLinejoin="round"><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H7a3 3 0 0 0-3 3V5.5Z" /><path d="M8 7h8" /><path d="M8 11h7" /></svg>
);
// 문 + 화살표 — 로그아웃/방 나가기 공용 아이콘
const IconDoor = () => (
  <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" strokeWidth="2"
    strokeLinecap="round" strokeLinejoin="round"><path d="M16 17l5-5-5-5" /><path d="M21 12H9" /><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /></svg>
);
const IconChevron = () => (
  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2"
    strokeLinecap="round" strokeLinejoin="round"><path d="M6 9l6 6 6-6" /></svg>
);

const Navbar = () => {
  const { user, isAuthenticated, logout } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const inRoom = location.pathname.startsWith('/game/');
  // 게스트(비회원)와 정식 회원을 구분 — 게스트는 멤버 전용 메뉴를 숨기고 가입 CTA를 보여준다.
  const isGuest = isAuthenticated && !!user?.guest;
  const isMember = isAuthenticated && !user?.guest;

  // 드롭다운 바깥 클릭 시 닫기
  useEffect(() => {
    if (!menuOpen) return;
    const onDocClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false);
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [menuOpen]);

  const handleLogout = async () => {
    if (inRoom) {
      if (!window.confirm('로그아웃하면 방에서 퇴장됩니다.\n(방장이라면 방이 사라집니다)\n정말 로그아웃하시겠습니까?')) {
        return;
      }
    }
    try {
      await authApi.logout();
    } finally {
      logout();
      navigate('/login');
    }
  };

  // 방 나가기 — /lobby 로 이동하면 게임 페이지의 이탈 가드가 확인창을 띄우고 퇴장 처리한다.
  const handleLeaveRoom = () => navigate('/lobby');

  const navClass = ({ isActive }: { isActive: boolean }) =>
    `${styles.navItem} ${isActive ? styles.navActive : ''}`;

  return (
    <nav className={styles.navbar}>
      <div className={styles.container}>
        <Link to="/" className={styles.logo}>
          <span className={styles.logoStones} aria-hidden="true">
            <span className={styles.stoneB} />
            <span className={styles.stoneW} />
          </span>
          <span className={styles.logoText}>도파민 오목</span>
        </Link>

        {isMember && (
          <div className={styles.nav}>
            <NavLink to="/" end className={navClass}><IconHome /><span>홈</span></NavLink>
            <NavLink to="/lobby" className={navClass}><IconPlay /><span>대국</span></NavLink>
            <NavLink to="/plaza" className={navClass}><IconPlaza /><span>광장</span></NavLink>
            <NavLink to="/friends" className={navClass}><IconFriends /><span>친구</span></NavLink>
            <NavLink to="/ranking" className={navClass}><IconRank /><span>랭킹</span></NavLink>
            <NavLink to="/shop" className={navClass}><IconShop /><span>상점</span></NavLink>
            <NavLink to="/guide" className={navClass}><IconGuide /><span>게임 방법</span></NavLink>
          </div>
        )}
        {isGuest && (
          <div className={styles.nav}>
            <NavLink to="/ai" className={navClass}><IconPlay /><span>AI 대국</span></NavLink>
            <NavLink to="/guide" className={navClass}><IconGuide /><span>게임 방법</span></NavLink>
          </div>
        )}

        <div className={styles.right}>
          {isMember ? (
            <>
              <Link to="/shop" className={styles.currencyBadge} title="보유 재화">
                🪙 {user?.currency?.toLocaleString() ?? 0}
              </Link>

              <NotificationBell />

              <div className={styles.userMenu} ref={menuRef}>
                <button
                  className={styles.userChip}
                  onClick={() => setMenuOpen((o) => !o)}
                  aria-haspopup="menu"
                  aria-expanded={menuOpen}
                >
                  <span className={styles.avatar}>
                    {user?.profileImageUrl
                      ? <img src={user.profileImageUrl} alt="" />
                      : (user?.nickname?.[0]?.toUpperCase() ?? '?')}
                  </span>
                  <span className={styles.userName}>{user?.nickname}</span>
                  <span className={styles.chevron}><IconChevron /></span>
                </button>

                {menuOpen && (
                  <div className={styles.dropdown} role="menu">
                    <button
                      className={styles.dropdownItem}
                      onClick={() => { setMenuOpen(false); setSettingsOpen(true); }}
                    >
                      설정
                    </button>
                    <button
                      className={styles.dropdownItem}
                      onClick={() => { setMenuOpen(false); navigate('/profile'); }}
                    >
                      내 프로필
                    </button>
                    <div className={styles.dropdownDivider} />
                    <button
                      className={`${styles.dropdownItem} ${styles.dropdownDanger}`}
                      onClick={() => { setMenuOpen(false); handleLogout(); }}
                    >
                      로그아웃
                    </button>
                  </div>
                )}
              </div>

              {inRoom && (
                <button onClick={handleLeaveRoom} className={styles.logoutBtn} title="방 나가기" aria-label="방 나가기">
                  <IconDoor />
                </button>
              )}
            </>
          ) : isGuest ? (
            <>
              <span className={styles.guestTag}>🎮 게스트</span>
              <Link to="/register" className={styles.registerBtn}>회원가입</Link>
              <button onClick={handleLogout} className={styles.logoutBtn} title="나가기" aria-label="나가기">
                <IconDoor />
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className={styles.navItem}>로그인</Link>
              <Link to="/register" className={styles.registerBtn}>회원가입</Link>
            </>
          )}
        </div>
      </div>

      {settingsOpen && <SettingsModal onClose={() => setSettingsOpen(false)} />}
    </nav>
  );
};

export default Navbar;
