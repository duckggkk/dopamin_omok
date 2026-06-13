import { Link, NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { authApi } from '@/api/auth';
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

const Navbar = () => {
  const { user, isAuthenticated, logout } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = async () => {
    const inGame = location.pathname.startsWith('/game/');
    if (inGame) {
      if (!window.confirm('로그아웃하면 방에서 퇴장됩니다.\n(방장이라면 방이 폭파됩니다)\n정말 로그아웃하시겠습니까?')) {
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

        {isAuthenticated && (
          <div className={styles.nav}>
            <NavLink to="/" end className={navClass}><IconHome /><span>홈</span></NavLink>
            <NavLink to="/lobby" className={navClass}><IconPlay /><span>대국</span></NavLink>
            <NavLink to="/plaza" className={navClass}><IconPlaza /><span>광장</span></NavLink>
            <NavLink to="/ranking" className={navClass}><IconRank /><span>랭킹</span></NavLink>
            <NavLink to="/shop" className={navClass}><IconShop /><span>상점</span></NavLink>
          </div>
        )}

        <div className={styles.right}>
          {isAuthenticated ? (
            <>
              <Link to="/shop" className={styles.currencyBadge} title="보유 재화">
                🪙 {user?.currency?.toLocaleString() ?? 0}
              </Link>
              <Link to="/profile" className={styles.userChip}>
                <span className={styles.avatar}>
                  {user?.profileImageUrl
                    ? <img src={user.profileImageUrl} alt="" />
                    : (user?.nickname?.[0]?.toUpperCase() ?? '?')}
                </span>
                <span className={styles.userName}>{user?.nickname}</span>
              </Link>
              <button onClick={handleLogout} className={styles.logoutBtn} title="로그아웃" aria-label="로그아웃">
                <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" strokeWidth="2"
                  strokeLinecap="round" strokeLinejoin="round"><path d="M16 17l5-5-5-5" /><path d="M21 12H9" /><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /></svg>
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
    </nav>
  );
};

export default Navbar;
