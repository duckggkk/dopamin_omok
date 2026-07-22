import styles from '@/pages/GamePage.module.css';
import { WIN_EFFECT_CLASS, LOSS_EFFECT_CLASS } from './winEffect';

export type GameResult = 'WIN' | 'LOSS' | 'DRAW';

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
