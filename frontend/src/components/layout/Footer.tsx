import { Link } from 'react-router-dom';
import styles from './Footer.module.css';

/**
 * 전역 푸터 — 약관·개인정보처리방침을 모든(몰입형 제외) 페이지 하단에 상시 노출한다.
 * 법적 고지의 접근성을 위해 회원가입 화면 외에도 항상 보이게 둔다(App.Layout 에서 마운트).
 */
const Footer = () => (
  <footer className={styles.footer}>
    <div className={styles.inner}>
      <div className={styles.brand}>
        <span className={styles.brandName}>도파민 오목</span>
      </div>

      <nav className={styles.links} aria-label="법적 고지">
        <Link to="/terms" className={styles.link}>이용약관</Link>
        <span className={styles.sep} aria-hidden="true">·</span>
        <Link to="/privacy" className={`${styles.link} ${styles.strong}`}>개인정보처리방침</Link>
      </nav>

      <p className={styles.copy}>© 2026 도파민 오목. All rights reserved.</p>
    </div>
  </footer>
);

export default Footer;
