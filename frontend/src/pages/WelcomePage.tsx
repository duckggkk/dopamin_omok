import { useState, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { userApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import { getApiErrorMessage, getApiFieldErrors } from '@/utils/error';
import styles from './AuthPage.module.css';

/**
 * 신규 소셜(구글) 가입자에게 처음 한 번 보여주는 닉네임 설정 화면.
 * 가입 시 자동 생성된 닉네임이 미리 채워져 있고, 원하면 바꾼 뒤 시작한다(건너뛰기 가능).
 */
const WelcomePage = () => {
  const navigate = useNavigate();
  const { user, setUser } = useAuthStore();
  const [nickname, setNickname] = useState(user?.nickname ?? '');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);
    try {
      const res = await userApi.updateProfile({ nickname: nickname.trim() });
      if (res.data.data) setUser(res.data.data);
      navigate('/', { replace: true });
    } catch (err: unknown) {
      const fieldErrors = getApiFieldErrors(err);
      setError(fieldErrors?.nickname ?? getApiErrorMessage(err, '닉네임 설정에 실패했습니다.'));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <h1 className={styles.title}>환영합니다!</h1>
        <p className={styles.subtitle}>사용할 닉네임을 정해주세요. (나중에 프로필에서 변경할 수 있어요)</p>

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.field}>
            <label htmlFor="nickname">닉네임</label>
            <input
              id="nickname"
              type="text"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder="2~15자 (한글, 영문, 숫자)"
              maxLength={15}
              required
              className={`${styles.input} ${error ? styles.inputError : ''}`}
            />
            {error && <span className={styles.fieldError}>{error}</span>}
          </div>

          <button type="submit" disabled={isLoading} className={styles.submitBtn}>
            {isLoading ? '저장 중...' : '이 닉네임으로 시작하기'}
          </button>
        </form>

        <p className={styles.footer}>
          <button
            type="button"
            onClick={() => navigate('/', { replace: true })}
            style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer', textDecoration: 'underline', font: 'inherit' }}
          >
            다음에 하기
          </button>
        </p>
      </div>
    </div>
  );
};

export default WelcomePage;
