import styles from './GoogleLoginButton.module.css';

/**
 * 구글 공식 로고(4색 G)를 단 "Google 계정으로 로그인" 버튼.
 * 클릭하면 백엔드 진입점(/api/auth/oauth2/google)으로 전체 페이지 이동한다.
 *
 * 로그인과 가입이 같은 흐름이라(처음 온 구글 계정이면 서버가 계정을 만든다) 회원가입 화면에서도
 * 같은 버튼을 쓰고, 문구만 label 로 바꿔 단다.
 */
const startGoogleLogin = () => {
  window.location.href = '/api/auth/oauth2/google';
};

const GoogleLoginButton = ({ label = 'Google 계정으로 로그인' }: { label?: string }) => (
  <button
    type="button"
    onClick={startGoogleLogin}
    className={styles.googleBtn}
    aria-label={label}
  >
    <svg className={styles.icon} width="18" height="18" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg">
      <path
        fill="#4285F4"
        d="M17.64 9.205c0-.639-.057-1.252-.164-1.841H9v3.481h4.844c-.209 1.125-.843 2.078-1.796 2.717v2.258h2.908c1.702-1.567 2.684-3.875 2.684-6.615z"
      />
      <path
        fill="#34A853"
        d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332C2.438 15.983 5.482 18 9 18z"
      />
      <path
        fill="#FBBC05"
        d="M3.964 10.71c-.18-.54-.282-1.117-.282-1.71s.102-1.17.282-1.71V4.958H.957A8.996 8.996 0 000 9c0 1.452.348 2.827.957 4.042l3.007-2.332z"
      />
      <path
        fill="#EA4335"
        d="M9 3.58c1.321 0 2.508.454 3.44 1.346l2.582-2.581C13.463.891 11.426 0 9 0 5.482 0 2.438 2.017.957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58z"
      />
    </svg>
    <span>{label}</span>
  </button>
);

export default GoogleLoginButton;
