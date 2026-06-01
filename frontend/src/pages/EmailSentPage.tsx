import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { authApi } from '@/api/auth';
import styles from './AuthPage.module.css';
import pageStyles from './EmailSentPage.module.css';

const EmailSentPage = () => {
  const [searchParams] = useSearchParams();
  const email = searchParams.get('email') ?? '';
  const [isResending, setIsResending] = useState(false);
  const [resendMessage, setResendMessage] = useState('');
  const [resendError, setResendError] = useState('');

  const handleResend = async () => {
    if (!email) return;
    setIsResending(true);
    setResendMessage('');
    setResendError('');

    try {
      await authApi.resendVerification(email);
      setResendMessage('인증 이메일을 재발송했습니다. 메일함을 확인해주세요.');
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      setResendError(axiosErr.response?.data?.message ?? '재발송에 실패했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setIsResending(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <div className={pageStyles.iconWrap}>✉️</div>
        <h1 className={styles.title}>이메일을 확인해주세요</h1>
        <p className={styles.subtitle}>인증 메일을 발송했습니다</p>

        <div className={pageStyles.infoBox}>
          <p className={pageStyles.emailText}>{email}</p>
          <p className={pageStyles.desc}>
            위 이메일 주소로 인증 링크를 보냈습니다.<br />
            링크를 클릭하면 가입이 완료됩니다.
          </p>
          <p className={pageStyles.expire}>링크는 <strong>24시간</strong> 동안 유효합니다.</p>
        </div>

        {resendMessage && <p className={pageStyles.successMsg}>{resendMessage}</p>}
        {resendError && <p className={styles.error}>{resendError}</p>}

        <button
          onClick={handleResend}
          disabled={isResending}
          className={pageStyles.resendBtn}
        >
          {isResending ? '발송 중...' : '인증 메일 재발송'}
        </button>

        <p className={styles.footer}>
          <Link to="/login">로그인 페이지로 돌아가기</Link>
        </p>
      </div>
    </div>
  );
};

export default EmailSentPage;
