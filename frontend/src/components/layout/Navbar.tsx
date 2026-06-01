import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { authApi } from '@/api/auth';
import styles from './Navbar.module.css';

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

  return (
    <nav className={styles.navbar}>
      <div className={styles.container}>
        <Link to="/" className={styles.logo}>
          🎮 도파민 오목
        </Link>
        <div className={styles.menu}>
          {isAuthenticated ? (
            <>
              <Link to="/lobby" className={styles.link}>로비</Link>
              <Link to="/shop" className={styles.shopLink}>상점</Link>
              {user?.currency !== undefined && (
                <span className={styles.currencyBadge}>🪨 {user.currency.toLocaleString()}</span>
              )}
              <Link to="/profile" className={styles.link}>
                {user?.nickname}
              </Link>
              <button onClick={handleLogout} className={styles.logoutBtn}>
                로그아웃
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className={styles.link}>로그인</Link>
              <Link to="/register" className={styles.registerBtn}>회원가입</Link>
            </>
          )}
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
