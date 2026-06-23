import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { userApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import { tokenStorage } from '@/utils/token';
import styles from './AuthPage.module.css';

/**
 * 소셜 로그인(구글) 콜백 처리 페이지.
 *
 * 백엔드가 토큰을 URL 프래그먼트(#accessToken=...&refreshToken=...)에 실어 이 경로로 보낸다.
 * 프래그먼트는 서버로 전송되지 않아(로그/리퍼러에 안 남음) 토큰 전달에 비교적 안전하다.
 * 여기서 토큰을 저장하고 내 정보를 받아 로그인 상태를 세운 뒤 홈으로 이동한다.
 */
const OAuthCallbackPage = () => {
  const navigate = useNavigate();
  const { login } = useAuthStore();
  const [error, setError] = useState(false);
  const ran = useRef(false); // StrictMode 중복 실행 방지

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;

    const params = new URLSearchParams(window.location.hash.slice(1));
    const accessToken = params.get('accessToken');
    const refreshToken = params.get('refreshToken');
    const isNewUser = params.get('newUser') === 'true';
    // URL에서 토큰 흔적 즉시 제거(주소창/히스토리 노출 최소화)
    window.history.replaceState(null, '', window.location.pathname);

    if (!accessToken || !refreshToken) {
      setError(true);
      return;
    }

    (async () => {
      try {
        tokenStorage.setRemember(true); // 소셜 로그인은 로그인 유지 기본 ON
        tokenStorage.setTokens(accessToken, refreshToken);
        const me = await userApi.getMe();
        if (!me.data.data) throw new Error('no user');
        login(me.data.data, accessToken, refreshToken);
        // 신규 가입자는 닉네임 설정 화면으로 한 번 안내, 기존 사용자는 바로 홈으로.
        navigate(isNewUser ? '/welcome' : '/', { replace: true });
      } catch {
        tokenStorage.clearTokens();
        setError(true);
      }
    })();
  }, [login, navigate]);

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        {error ? (
          <>
            <h1 className={styles.title}>로그인 실패</h1>
            <p className={styles.subtitle}>소셜 로그인 처리 중 문제가 발생했습니다.</p>
            <p className={styles.footer}>
              <Link to="/login">로그인 화면으로 돌아가기</Link>
            </p>
          </>
        ) : (
          <>
            <h1 className={styles.title}>로그인 중...</h1>
            <p className={styles.subtitle}>잠시만 기다려 주세요.</p>
          </>
        )}
      </div>
    </div>
  );
};

export default OAuthCallbackPage;
