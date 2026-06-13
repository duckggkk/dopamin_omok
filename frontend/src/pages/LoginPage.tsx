import { useState, FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi, userApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import { tokenStorage } from '@/utils/token';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/error';
import styles from './AuthPage.module.css';
import pageStyles from './LoginPage.module.css';

const LoginPage = () => {
  const navigate = useNavigate();
  const { login } = useAuthStore();
  const [searchParams] = useSearchParams();
  const isJustVerified = searchParams.get('verified') === '1';
  const isSessionExpired = searchParams.get('reason') === 'session_expired';
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [isEmailNotVerified, setIsEmailNotVerified] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setIsEmailNotVerified(false);
    setIsLoading(true);

    try {
      const tokenRes = await authApi.login(form);
      const { accessToken, refreshToken } = tokenRes.data.data!;

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

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <h1 className={styles.title}>로그인</h1>
        <p className={styles.subtitle}>도파민 오목에 오신 것을 환영합니다</p>
        {isJustVerified && (
          <p className={styles.notice}>이메일 인증이 완료되었습니다! 로그인해주세요.</p>
        )}
        {isSessionExpired && (
          <p className={`${styles.notice} ${styles.noticeWarn}`}>
            다른 기기에서 로그인되어 자동 로그아웃되었습니다.
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

          <button type="submit" disabled={isLoading} className={styles.submitBtn}>
            {isLoading ? '로그인 중...' : '로그인'}
          </button>
        </form>

        <p className={styles.footer}>
          계정이 없으신가요? <Link to="/register">회원가입</Link>
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
