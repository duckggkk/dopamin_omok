import { Link } from 'react-router-dom';
// 약관 페이지와 동일한 "법적 고지 페이지" 스타일을 공유한다.
import styles from './TermsPage.module.css';

// 개인정보 보호책임자 문의처.
const CONTACT_EMAIL = 'dopaminomok@gmail.com';
// 방침 시행일 — 내용을 개정하면 이 날짜도 함께 갱신하세요.
const EFFECTIVE_DATE = '2026년 6월 18일';

const PrivacyPolicyPage = () => (
  <div className={styles.page}>
    <header className={styles.header}>
      <span className={styles.badge}>개인정보처리방침</span>
      <h1>도파민 오목 개인정보처리방침</h1>
      <p className={styles.meta}>시행일: {EFFECTIVE_DATE}</p>
    </header>

    {/* 오픈베타 고지 */}
    <section className={styles.notice}>
      <h2>⚠️ 오픈베타(시범 운영) 안내</h2>
      <ul>
        <li>
          본 서비스는 정식 출시 이전의 오픈베타 단계로, 운영상 필요에 따라{' '}
          <strong>회원의 계정 및 개인정보를 포함한 모든 데이터가 예고 없이 초기화(전체 삭제)될 수
          있습니다.</strong>
        </li>
        <li>
          자세한 서비스 이용 조건은 <Link to="/terms">이용약관</Link>을 함께 확인해 주세요.
        </li>
      </ul>
    </section>

    <article className={styles.body}>
      <section>
        <p>
          도파민 오목(이하 "서비스")의 운영자(이하 "운영자")는 「개인정보 보호법」 등 관련 법령을
          준수하며, 정보주체(회원)의 개인정보를 보호하기 위해 다음과 같이 개인정보처리방침을 수립·공개합니다.
        </p>
      </section>

      <section>
        <h2>제1조 (수집하는 개인정보 항목 및 수집 방법)</h2>
        <p>운영자는 서비스 제공을 위해 필요한 최소한의 개인정보만을 수집합니다.</p>
        <ul className={styles.list}>
          <li>
            <strong>회원가입 시(필수)</strong>: 이메일 주소, 비밀번호, 닉네임
          </li>
          <li>
            <strong>소셜 로그인 이용 시(선택)</strong>: 카카오·구글 등 제공자로부터 받은 이메일, 닉네임,
            프로필 이미지, 제공자 식별자 (해당 기능 이용 시에만)
          </li>
          <li>
            <strong>서비스 이용 과정에서 생성·수집</strong>: 게임 전적(승·패·무), 레이팅, 보유 가상 재화
            및 아이템, 게임 수순 기록, 계정 식별자
          </li>
          <li>
            <strong>자동 수집 항목</strong>: 접속 IP 주소, 접속 일시, 서비스 이용 기록, 기기·브라우저
            정보 (서버 접속 로그를 통해 자동 생성)
          </li>
        </ul>
        <p>수집 방법: 회원가입·서비스 이용 화면에서의 입력, 서비스 이용 중 자동 생성·기록</p>
      </section>

      <section>
        <h2>제2조 (개인정보의 수집 및 이용 목적)</h2>
        <ul className={styles.list}>
          <li>회원 식별 및 본인 인증(이메일 인증), 계정 관리</li>
          <li>게임 매칭·진행, 전적·랭킹·프로필 등 서비스 핵심 기능 제공</li>
          <li>부정 이용(무차별 대입·자동화 프로그램 등) 방지 및 서비스 안정성 확보</li>
          <li>공지·문의 응대 등 고객 지원</li>
        </ul>
      </section>

      <section>
        <h2>제3조 (개인정보의 보유 및 이용 기간)</h2>
        <ul className={styles.list}>
          <li>원칙적으로 회원 탈퇴 시 또는 수집·이용 목적 달성 시 지체 없이 파기합니다.</li>
          <li>
            <strong>
              오픈베타 특성상 회원의 개인정보는 테스트 종료·데이터 초기화 시 별도 통지 없이 파기될 수
              있습니다.
            </strong>
          </li>
          <li>이메일 인증 코드는 발급 후 단시간(수 분) 내 만료·삭제되며, 자동 로그인 토큰은 일정 기간 경과 시 만료됩니다.</li>
          <li>관련 법령에서 별도의 보존 의무를 정한 경우 해당 기간 동안 보관합니다.</li>
        </ul>
      </section>

      <section>
        <h2>제4조 (개인정보의 제3자 제공)</h2>
        <p>
          운영자는 회원의 개인정보를 본 방침에 명시한 범위를 넘어 외부에 제공하지 않습니다. 다만 법령에
          근거가 있거나 수사기관의 적법한 요청이 있는 경우는 예외로 합니다.
        </p>
      </section>

      <section>
        <h2>제5조 (개인정보 처리의 위탁)</h2>
        <p>운영자는 서비스 제공을 위해 다음과 같이 일부 업무를 외부에 위탁할 수 있습니다.</p>
        <ul className={styles.list}>
          <li>
            <strong>Google(Gmail SMTP)</strong>: 회원 인증 메일 발송 — 이메일 주소가 메일 발송 과정에서 처리됩니다.
          </li>
          <li>
            <strong>카카오·구글</strong>: 소셜 로그인 인증 (해당 기능 이용 시에만)
          </li>
          <li>
            <strong>클라우드/호스팅 사업자</strong>: 서버 운영 및 데이터 보관을 위한 인프라 제공
          </li>
        </ul>
        <p>위탁 업무 내용이나 수탁자가 변경되는 경우 본 방침을 통해 공개합니다.</p>
      </section>

      <section>
        <h2>제6조 (정보주체의 권리·의무 및 행사 방법)</h2>
        <ul className={styles.list}>
          <li>회원은 언제든지 자신의 개인정보 열람·정정·삭제·처리정지를 요청할 수 있습니다.</li>
          <li>프로필 정보(닉네임 등)는 서비스 내 프로필 화면에서 직접 수정할 수 있습니다.</li>
          <li>
            회원 탈퇴(계정 삭제) 또는 기타 권리 행사를 원하는 경우 아래 보호책임자 연락처로 요청하실 수
            있으며, 운영자는 지체 없이 조치합니다.
          </li>
        </ul>
      </section>

      <section>
        <h2>제7조 (개인정보의 파기 절차 및 방법)</h2>
        <ul className={styles.list}>
          <li>보유 기간이 경과하거나 처리 목적이 달성된 개인정보는 지체 없이 파기합니다.</li>
          <li>전자적 파일 형태의 정보는 복구·재생이 불가능한 방법으로 영구 삭제합니다.</li>
        </ul>
      </section>

      <section className={styles.highlight}>
        <h2>제8조 (개인정보의 안전성 확보 조치)</h2>
        <ul className={styles.list}>
          <li>
            <strong>비밀번호 암호화</strong>: 회원 비밀번호는 복호화가 불가능한 일방향 해시(BCrypt)로
            저장되어, 운영자도 원문을 알 수 없습니다.
          </li>
          <li>
            <strong>전송 구간 암호화</strong>: 모든 통신은 HTTPS(TLS)로 암호화되어 전송됩니다.
          </li>
          <li>
            <strong>접근 통제</strong>: 데이터베이스 등 내부 시스템은 외부에 직접 노출되지 않으며, 인증된
            요청만 처리됩니다.
          </li>
        </ul>
      </section>

      <section>
        <h2>제9조 (개인정보 자동 수집 장치의 설치·운영 및 거부)</h2>
        <ul className={styles.list}>
          <li>
            본 서비스는 광고성 추적 쿠키를 사용하지 않습니다. 로그인 상태 유지를 위해 인증 토큰을 이용자
            브라우저의 로컬 저장소(localStorage)에 저장하며, 이는 로그아웃 시 삭제됩니다.
          </li>
          <li>
            서비스 운영·보안을 위해 접속 로그(IP·접속 일시 등)가 자동으로 기록됩니다.
          </li>
        </ul>
      </section>

      <section>
        <h2>제10조 (개인정보 보호책임자)</h2>
        <p>
          개인정보 처리에 관한 문의·불만·피해 구제 등은 아래로 연락해 주시기 바랍니다. 운영자는 지체
          없이 답변·처리하겠습니다.
        </p>
        <ul className={styles.list}>
          <li>개인정보 보호책임자: 도파민 오목 운영자</li>
          <li>연락처: <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a></li>
        </ul>
      </section>

      <section>
        <h2>제11조 (권익침해 구제 방법)</h2>
        <p>개인정보 침해로 인한 상담·신고가 필요한 경우 아래 기관에 문의하실 수 있습니다.</p>
        <ul className={styles.list}>
          <li>개인정보분쟁조정위원회: 1833-6972 (www.kopico.go.kr)</li>
          <li>개인정보침해신고센터: 118 (privacy.kisa.or.kr)</li>
          <li>대검찰청 사이버수사과: 1301 (www.spo.go.kr)</li>
          <li>경찰청 사이버수사국: 182 (ecrm.cyber.go.kr)</li>
        </ul>
      </section>

      <section>
        <h2>제12조 (고지의 의무)</h2>
        <p>
          본 방침의 내용이 추가·삭제·수정되는 경우 개정 최소 7일 전(중대한 변경은 30일 전)에 서비스 내
          공지를 통해 고지합니다.
        </p>
      </section>

      <section className={styles.addendum}>
        <h2>부칙</h2>
        <p>본 방침은 {EFFECTIVE_DATE}부터 시행합니다.</p>
      </section>
    </article>
  </div>
);

export default PrivacyPolicyPage;
