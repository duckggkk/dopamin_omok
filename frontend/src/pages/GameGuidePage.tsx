import styles from './GameGuidePage.module.css';

const GameGuidePage = () => (
  <div className={styles.page}>
    <section className={styles.hero}>
      <span className={styles.badge}>게임 방법</span>
      <h1>도파민 오목 가이드</h1>
      <p>
        일반 오목의 기본 규칙과 렌주룰, 실시간 액션 모드인 피지컬 오목의 조작법을 한 곳에서 확인합니다.
      </p>
    </section>

    <section className={styles.grid}>
      <article className={styles.card}>
        <div className={styles.cardHead}>
          <span className={styles.icon}>⚫</span>
          <div>
            <h2>일반 오목 · 일반룰</h2>
            <p>가장 직관적인 오목 규칙입니다.</p>
          </div>
        </div>
        <ul className={styles.list}>
          <li>흑이 선공이며, 흑과 백이 한 수씩 번갈아 둡니다.</li>
          <li>가로·세로·대각선 중 한 방향으로 돌 5개를 먼저 연결하면 승리합니다.</li>
          <li>이미 돌이 있는 교차점에는 둘 수 없습니다.</li>
          <li>금수 제한이 없어 초보자도 바로 플레이하기 쉽습니다.</li>
        </ul>
      </article>

      <article className={styles.card}>
        <div className={styles.cardHead}>
          <span className={styles.icon}>♟️</span>
          <div>
            <h2>일반 오목 · 렌주룰</h2>
            <p>선공 흑의 유리함을 줄이는 경쟁 규칙입니다.</p>
          </div>
        </div>
        <ul className={styles.list}>
          <li>기본 승리 조건은 동일하게 5개 연결입니다.</li>
          <li>흑은 3-3, 4-4, 장목 금수 자리에 둘 수 없습니다.</li>
          <li>백은 금수 제한을 받지 않습니다.</li>
        </ul>
      </article>

      <article className={styles.card}>
        <div className={styles.cardHead}>
          <span className={styles.icon}>⚔️</span>
          <div>
            <h2>피지컬 오목</h2>
            <p>캐릭터를 움직이며 실시간으로 돌을 두는 액션 모드입니다.</p>
          </div>
        </div>
        <ul className={styles.list}>
          <li><b>방향키</b>로 캐릭터를 이동하고, <b>Space</b>로 현재 위치에 착수합니다.</li>
          <li><b>Ctrl</b>로 상대 돌을 파괴하고, <b>Shift</b>로 획득한 아이템을 사용합니다.</li>
          <li>아이템은 이동 부스트, 바둑판 붕괴, 광역 폭탄처럼 판세를 흔드는 효과를 가집니다.</li>
          <li>오목을 만들면 즉시 끝나지 않고, 확정 시간 동안 연결을 유지해야 승리합니다.</li>
          <li>피지컬 오목에는 제한시간과 초읽기가 없습니다.</li>
        </ul>
      </article>
    </section>

    <section className={styles.note}>
      <h2>방 시작 전 체크</h2>
      <div className={styles.noteGrid}>
        <p><b>준비 상태</b><br />참가자가 준비 완료해야 방장이 시작할 수 있습니다.</p>
        <p><b>바둑알 스킨</b><br />서로의 스킨은 방에서 확인할 수 있고, 같은 스킨이면 혼동 방지를 위해 시작할 수 없습니다.</p>
        <p><b>기보</b><br />종료된 일반 오목은 기보로, 피지컬 오목은 리플레이로 다시 볼 수 있습니다.</p>
      </div>
    </section>
  </div>
);

export default GameGuidePage;
