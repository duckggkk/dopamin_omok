import { GameMove } from '@/types';
import styles from '@/pages/GamePage.module.css';

interface MoveHistoryProps {
  moves: GameMove[];
}

const MoveHistory = ({ moves }: MoveHistoryProps) => (
  <div className={styles.moveHistory}>
    <h3>기보</h3>
    <div className={styles.moveList}>
      {[...moves]
        .reverse()
        .slice(0, 20)
        .map((m) => (
          <div key={m.moveNumber} className={styles.moveItem}>
            <span className={m.color === 'BLACK' ? styles.blackDot : styles.whiteDot} />
            <span>
              {m.moveNumber}. {m.playerNickname}
            </span>
            <span className={styles.movePos}>
              ({m.col + 1}, {m.row + 1})
            </span>
          </div>
        ))}
    </div>
  </div>
);

export default MoveHistory;
