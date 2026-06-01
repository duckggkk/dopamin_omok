import { useState, useEffect, useRef, FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '@/api/auth';
import styles from './AuthPage.module.css';
import pageStyles from './VerifyEmailPage.module.css';

const EXPIRE_SECONDS = 180; // 3분

const VerifyEmailPage = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const email = searchParams.get('email') ?? '';

  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [resendMessage, setResendMessage] = useState('');
  const [secondsLeft, setSecondsLeft] = useState(EXPIRE_SECONDS);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const startTimer = () => {
    if (timerRef.current) clearInterval(timerRef.current);
    setSecondsLeft(EXPIRE_SECONDS);
    timerRef.current = setInterval(() => {
      setSecondsLeft((s) => {
        if (s <= 1) {
          clearInterval(timerRef.current!);
          return 0;
        }
        return s - 1;
      });
    }, 1000);
  };

  useEffect(() => {
    startTimer();
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, []);

  const formatTime = (s: number) => {
    const m = Math.floor(s / 60);
    const sec = s % 60;
    return `${m}:${sec.toString().padStart(2, '0')}`;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (secondsLeft === 0) {
      setError('인증 코드가 만료되었습니다. 재발송을 요청해주세요.');
      return;
    }
    setError('');
    setIsLoading(true);

    try {
      await authApi.verifyEmail(email, code);
      navigate('/login?verified=1');
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      setError(axiosErr.response?.data?.message ?? '인증에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleResend = async () => {
    setIsResending(true);
    setResendMessage('');
    setError('');

    try {
      await authApi.resendVerification(email);
      setResendMessage('새 인증 코드를 발송했습니다.');
      setCode('');
      startTimer();
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      setError(axiosErr.response?.data?.message ?? '재발송에 실패했습니다.');
    } finally {
      setIsResending(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <h1 className={styles.title}>이메일 인증</h1>
        <p className={styles.subtitle}>
          <span className={pageStyles.emailHighlight}>{email}</span><br />
          으로 발송된 6자리 코드를 입력해주세요
        </p>

        <div className={pageStyles.timerWrap}>
          <span className={secondsLeft <= 30 ? pageStyles.timerDanger : pageStyles.timer}>
            ⏱ {formatTime(secondsLeft)}
          </span>
        </div>

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.field}>
            <label htmlFor="code">인증 코드</label>
            <input
              id="code"
              type="text"
              inputMode="numeric"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder="6자리 숫자 입력"
              required
              className={`${styles.input} ${pageStyles.codeInput}`}
              autoComplete="one-time-code"
            />
          </div>

          {error && <p className={styles.error}>{error}</p>}
          {resendMessage && <p className={pageStyles.successMsg}>{resendMessage}</p>}

          <button
            type="submit"
            disabled={isLoading || code.length !== 6}
            className={styles.submitBtn}
          >
            {isLoading ? '확인 중...' : '인증하기'}
          </button>
        </form>

        <button
          onClick={handleResend}
          disabled={isResending}
          className={pageStyles.resendBtn}
        >
          {isResending ? '발송 중...' : '코드 재발송'}
        </button>

        <p className={styles.footer}>
          <Link to="/login">로그인 페이지로 돌아가기</Link>
        </p>
      </div>
    </div>
  );
};

export default VerifyEmailPage;
