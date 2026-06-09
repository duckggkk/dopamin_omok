import styles from '@/pages/GamePage.module.css';

export type GameResult = 'WIN' | 'LOSS' | 'DRAW';

interface GameResultOverlayProps {
  result: GameResult | null;
  displayText: string;
}

const GameResultOverlay = ({ result, displayText }: GameResultOverlayProps) => {
  if (!result) return null;

  return (
    <div className={styles.gameResultOverlay}>
      <div className={styles.gameResultContent}>
        {result === 'WIN' ? (
          <img
            src="/images/result-win.svg"
            alt="승리"
            className={styles.gameResultWinImage}
            draggable={false}
          />
        ) : result === 'LOSS' ? (
          <div className={styles.gameResultLossBox}>
            <img
              src="/images/result-loss-bg.svg"
              alt=""
              className={styles.gameResultLossBg}
              draggable={false}
            />
            <p className={styles.gameResultLossText}>{displayText}</p>
          </div>
        ) : (
          <p className={styles.gameResultDrawText}>{displayText}</p>
        )}
        <p className={styles.gameResultSubtext}>3초 후 게임이 종료됩니다...</p>
      </div>
    </div>
  );
};

export default GameResultOverlay;
