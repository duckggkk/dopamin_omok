import styles from './TermsPage.module.css';

const CONTACT_EMAIL = 'dopaminomok@gmail.com';
const UPDATED_AT = '2026년 7월 22일';

const LicensesPage = () => (
  <div className={styles.page}>
    <header className={styles.header}>
      <span className={styles.badge}>라이선스</span>
      <h1>콘텐츠 출처 및 라이선스</h1>
      <p className={styles.meta}>최종 업데이트: {UPDATED_AT}</p>
    </header>

    <article className={styles.body}>
      <section>
        <h2>안내</h2>
        <p>
          도파민 오목에서 사용하는 제3자 콘텐츠의 권리는 각 원저작자에게 있습니다.
          아래 표기는 해당 콘텐츠의 이용 조건을 설명하며, 프로젝트 전체에 같은 라이선스를
          부여한다는 뜻은 아닙니다.
        </p>
      </section>

      <section>
        <h2>배경음악</h2>
        <ul className={styles.list}>
          <li>
            <strong>Country Background</strong> — Tunetank, 클래식 오목 배경음악
            {' · '}
            <a
              href="https://pixabay.com/music/traditional-country-country-background-349052/"
              target="_blank"
              rel="noreferrer"
            >
              원본 페이지
            </a>
          </li>
          <li>
            <strong>A Reason To Smile</strong> — JonasBlakewood, 피지컬 오목 배경음악
            {' · '}
            <a
              href="https://pixabay.com/music/upbeat-a-reason-to-smile-350631/"
              target="_blank"
              rel="noreferrer"
            >
              원본 페이지
            </a>
          </li>
        </ul>
        <p>두 음원은 Pixabay Content License에 따라 게임의 구성요소로 사용합니다.</p>
      </section>

      <section className={styles.highlight}>
        <h2>Pixabay Content License</h2>
        <p>
          음원은 게임 플레이 중 배경음악으로만 사용하며, 원본 음원 파일을 별도 콘텐츠로
          재배포할 권리를 사용자에게 부여하지 않습니다. 출처 표시는 라이선스 제한을
          대신하거나 원저작자의 권리를 이전하지 않습니다.
        </p>
        <ul className={styles.list}>
          <li>
            <a href="https://pixabay.com/service/license-summary/" target="_blank" rel="noreferrer">
              Pixabay 라이선스 요약
            </a>
          </li>
          <li>
            <a href="https://pixabay.com/service/terms/" target="_blank" rel="noreferrer">
              Pixabay 전체 이용약관
            </a>
          </li>
        </ul>
      </section>

      <section>
        <h2>소스 코드와 기타 콘텐츠</h2>
        <p>
          오픈소스 라이브러리는 각 라이브러리의 라이선스를 따릅니다. 프로젝트의 소스 코드,
          이미지, 효과음 및 기타 콘텐츠는 별도의 라이선스가 명시되지 않는 한 재사용이나
          재배포 허가가 부여되지 않습니다.
        </p>
      </section>

      <section>
        <h2>문의</h2>
        <p>
          콘텐츠 권리 또는 출처 관련 문의는{' '}
          <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>으로 보내 주세요.
        </p>
      </section>
    </article>
  </div>
);

export default LicensesPage;
