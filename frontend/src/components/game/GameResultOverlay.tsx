import styles from '@/pages/GamePage.module.css';

export type GameResult = 'WIN' | 'LOSS' | 'DRAW';

// 이펙트 키(승자가 장착한 1개) → 화면 연출 클래스.
// 같은 테마를 승자는 '승리 연출', 패자는 '패배 연출'로 렌더한다(세트). 백엔드 화이트리스트와 합의.
const WIN_EFFECT_CLASS: Record<string, string> = {
  flame: styles.fxWinFlame,
  shatter: styles.fxWinShatter,
  storm: styles.fxWinStorm,
  tears: styles.fxWinTears,
};
const LOSS_EFFECT_CLASS: Record<string, string> = {
  flame: styles.fxFlame,
  shatter: styles.fxShatter,
  storm: styles.fxStorm,
  tears: styles.fxTears,
};

interface GameResultOverlayProps {
  result: GameResult | null;
  displayText: string;
  // 승자가 장착한 이펙트 키(승/패 공용). 없으면(기본) 이펙트 없이 문구만 표시한다.
  effect?: string | null;
}

const GameResultOverlay = ({ result, displayText, effect }: GameResultOverlayProps) => {
  if (!result) return null;

  const effectClass = !effect
    ? null
    : result === 'WIN'
      ? WIN_EFFECT_CLASS[effect]
      : result === 'LOSS'
        ? LOSS_EFFECT_CLASS[effect]
        : null;

  // 승/패 문구는 같은 크기·자리에 두어 '3초 후 종료' 안내가 항상 동일한 위치에 오도록 한다.
  const textClass =
    result === 'WIN' ? styles.gameResultWinText
    : result === 'LOSS' ? styles.gameResultLossText
    : styles.gameResultDrawText;

  return (
    <div className={styles.gameResultOverlay}>
      {/* 이펙트는 장착했을 때만 화면 전체에 깔린다(기본은 문구만). */}
      {effectClass && <div className={`${styles.resultEffect} ${effectClass}`} aria-hidden="true" />}

      <div className={styles.gameResultContent}>
        <p className={textClass}>{displayText}</p>
        <p className={styles.gameResultSubtext}>3초 후 게임이 종료됩니다...</p>
      </div>
    </div>
  );
};

export default GameResultOverlay;
