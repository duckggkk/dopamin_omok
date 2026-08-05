import { useState, FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi, userApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import { tokenStorage } from '@/utils/token';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/error';
import GoogleLoginButton from '@/components/auth/GoogleLoginButton';
import styles from './AuthPage.module.css';
import pageStyles from './LoginPage.module.css';

const LoginPage = () => {
  const navigate = useNavigate();
  //객체 구조 분해 타입, useAuthStore의 형태중 특정필드?함수?를 집어올 수 있음
  const { login } = useAuthStore();
  const [searchParams] = useSearchParams();
  const isJustVerified = searchParams.get('verified') === '1';
  const isSessionExpired = searchParams.get('reason') === 'session_expired';
  const isWithdrawn = searchParams.get('reason') === 'withdrawn';
  const isOAuthError = searchParams.get('error') === 'oauth';
  // 구글 로그인 버튼 노출 여부 — 구글 콘솔 설정을 마친 뒤 VITE_GOOGLE_LOGIN_ENABLED=true 로 켠다.
  // (설정 전 운영에서 깨진 버튼이 보이지 않도록 기본은 숨김)
  const googleLoginEnabled = import.meta.env.VITE_GOOGLE_LOGIN_ENABLED === 'true';
  const [form, setForm] = useState({ email: '', password: '' });
  const [keepLogin, setKeepLogin] = useState(true); // 로그인 상태 유지(기본 ON — 기존 동작)
  const [error, setError] = useState('');
  const [isEmailNotVerified, setIsEmailNotVerified] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isGuestLoading, setIsGuestLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault(); //브라우저 기본동작 막기 (폼제출)
    setError('');
    setIsEmailNotVerified(false);
    setIsLoading(true);

    try {
      const tokenRes = await authApi.login(form); //api 호출 
      const { accessToken, refreshToken } = tokenRes.data.data!;

      tokenStorage.setRemember(keepLogin); // 저장 위치(영구/세션) 결정 — setTokens·login 보다 먼저
      tokenStorage.setTokens(accessToken, refreshToken);

      const userRes = await userApi.getMe();
      if (userRes.data.data) {
        login(userRes.data.data, accessToken, refreshToken);
        navigate('/');
      }
    } catch (err: unknown) {
      const status = getApiErrorStatus(err);
      if (status === 403) {
        setIsEmailNotVerified(true);
        setError(getApiErrorMessage(err, '로그인에 실패했습니다.'));
      } else if (status === 401) {
        // 아이디/비밀번호 불일치는 항상 동일한 문구로 안내 (보안상 어느 쪽이 틀렸는지 노출하지 않음)
        setError('아이디 또는 비밀번호가 틀렸습니다.');
      } else {
        setError(getApiErrorMessage(err, '로그인에 실패했습니다.'));
      }
    } finally {
      setIsLoading(false);
    }
  };

  // 비회원으로 바로 시작 — 익명 계정을 발급받아 싱글 AI 등 게스트 허용 기능만 이용한다.
  const handleGuestStart = async () => {
    setError('');
    setIsEmailNotVerified(false);

    // 이미 이 브라우저에 로그인된 세션이 있으면(다른 탭에서 로그인했을 수 있음) 새 게스트를
    // 만들지 않는다 — 게스트 토큰은 아래에서 항상 localStorage 에 저장하므로, 새 게스트를
    // 또 만들면 기존 탭이 들고 있던 계정을 조용히 덮어써 버린다(같은 브라우저 두 탭이 같은
    // 사람으로 인식되는 원인). 대신 물어보고, 계속하면 기존 계정으로 이동시킨다.
    if (tokenStorage.getAccessToken()) {
      if (window.confirm('이미 로그인되어 있는 계정이 있습니다.\n새 게스트를 만들지 않고 기존 계정으로 이동할까요?')) {
        navigate('/');
      }
      return;
    }

    setIsGuestLoading(true);

    try {
      const tokenRes = await authApi.guestLogin();
      const { accessToken, refreshToken } = tokenRes.data.data!;

      tokenStorage.setRemember(true); // 게스트 신원을 새로고침·재방문에도 유지(매번 새 계정 생성 방지)
      tokenStorage.setTokens(accessToken, refreshToken);

      const userRes = await userApi.getMe();
      if (userRes.data.data) {
        login(userRes.data.data, accessToken, refreshToken);
        navigate('/'); // 로그인 후 홈으로 (게스트도 회원과 동일)
      }
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, '게스트 시작에 실패했습니다. 잠시 후 다시 시도해주세요.'));
    } finally {
      setIsGuestLoading(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <h1 className={styles.title}>로그인</h1>
        <p className={styles.subtitle}>도파민 오목에 오신 것을 환영합니다</p>
        {isJustVerified && (
          <p className={styles.notice}>이메일 인증이 완료되었습니다! 로그인해주세요.</p>
        )}
        {isWithdrawn && (
          <p className={styles.notice}>
            회원 탈퇴가 완료되었습니다.<br />
            그동안 도파민 오목을 이용해주셔서 감사합니다.
          </p>
        )}
        {isSessionExpired && (
          <p className={`${styles.notice} ${styles.noticeWarn}`}>
            다른 기기에서 로그인되어 자동 로그아웃되었습니다.
          </p>
        )}
        {isOAuthError && (
          <p className={`${styles.notice} ${styles.noticeWarn}`}>
            구글 로그인에 실패했습니다. 다시 시도해주세요.
          </p>
        )}

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.field}>
            <label htmlFor="email">이메일</label>
            <input
              id="email"
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              placeholder="example@email.com"
              required
              className={styles.input}
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="password">비밀번호</label>
            <input
              id="password"
              type="password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              placeholder="비밀번호를 입력하세요"
              required
              className={styles.input}
            />
          </div>

          <label className={pageStyles.rememberRow}>
            <input
              type="checkbox"
              checked={keepLogin}
              onChange={(e) => setKeepLogin(e.target.checked)}
            />
            로그인 상태 유지
          </label>

          {error && (
            <div>
              <p className={styles.error}>{error}</p>
              {isEmailNotVerified && (
                <p className={pageStyles.resendHint}>
                  <Link to={`/email-sent?email=${encodeURIComponent(form.email)}`}>
                    인증 메일 재발송하기
                  </Link>
                </p>
              )}
            </div>
          )}

          <button type="submit" disabled={isLoading || isGuestLoading} className={styles.submitBtn}>
            {isLoading ? '로그인 중...' : '로그인'}
          </button>
        </form>

        <div className={styles.altDivider}>또는</div>
        <button
          type="button"
          onClick={handleGuestStart}
          disabled={isLoading || isGuestLoading}
          className={styles.guestBtn}
        >
          {isGuestLoading ? '시작하는 중...' : '🎮 비회원으로 바로 시작'}
        </button>
        <p className={styles.guestHint}>
          가입 없이 AI 대국을 즐겨보세요. 온라인 대국·랭킹·상점은 회원만 이용할 수 있어요.
        </p>

        {googleLoginEnabled && (
          <>
            <div className={styles.altDivider}>또는</div>
            <GoogleLoginButton />
            <p className={styles.guestHint}>
              계속하면 <Link to="/terms">이용약관</Link> 및 <Link to="/privacy">개인정보처리방침</Link>에<br />동의하는 것으로 간주됩니다.
            </p>
          </>
        )}

        <p className={styles.footer}>
          계정이 없으신가요? <Link to="/register">회원가입</Link>
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
