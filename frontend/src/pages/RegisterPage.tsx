import { useState, FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '@/api/auth';
import styles from './AuthPage.module.css';

const RegisterPage = () => {
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: '', password: '', nickname: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setErrors({});
    setIsLoading(true);

    try {
      await authApi.register(form);
      navigate(`/verify-email?email=${encodeURIComponent(form.email)}`);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string; data?: Record<string, string> } } };
      const data = axiosErr.response?.data;
      if (data?.data) {
        setErrors(data.data);
      } else {
        setErrors({ general: data?.message ?? '회원가입에 실패했습니다.' });
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <h1 className={styles.title}>회원가입</h1>
        <p className={styles.subtitle}>오목 실력을 뽐내보세요!</p>

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
              className={`${styles.input} ${errors.email ? styles.inputError : ''}`}
            />
            {errors.email && <span className={styles.fieldError}>{errors.email}</span>}
          </div>

          <div className={styles.field}>
            <label htmlFor="password">비밀번호</label>
            <input
              id="password"
              type="password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              placeholder="영문+숫자 8~20자"
              required
              className={`${styles.input} ${errors.password ? styles.inputError : ''}`}
            />
            {errors.password && <span className={styles.fieldError}>{errors.password}</span>}
          </div>

          <div className={styles.field}>
            <label htmlFor="nickname">닉네임</label>
            <input
              id="nickname"
              type="text"
              value={form.nickname}
              onChange={(e) => setForm({ ...form, nickname: e.target.value })}
              placeholder="2~15자 (한글, 영문, 숫자)"
              required
              className={`${styles.input} ${errors.nickname ? styles.inputError : ''}`}
            />
            {errors.nickname && <span className={styles.fieldError}>{errors.nickname}</span>}
          </div>

          {errors.general && <p className={styles.error}>{errors.general}</p>}

          <button type="submit" disabled={isLoading} className={styles.submitBtn}>
            {isLoading ? '가입 중...' : '회원가입'}
          </button>
        </form>

        <p className={styles.footer}>
          이미 계정이 있으신가요? <Link to="/login">로그인</Link>
        </p>
      </div>
    </div>
  );
};

export default RegisterPage;
