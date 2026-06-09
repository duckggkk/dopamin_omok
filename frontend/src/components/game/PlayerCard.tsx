import { GameInfo, GamePlayer, RoomStatus } from '@/types';
import styles from '@/pages/GamePage.module.css';

interface PlayerCardProps {
  color: 'BLACK' | 'WHITE';
  player: GamePlayer | null;
  myUserId?: string;
  roomStatus: RoomStatus;
  currentGame: GameInfo | null;
}

const PlayerCard = ({ color, player, myUserId, roomStatus, currentGame }: PlayerCardProps) => {
  const label = color === 'BLACK' ? '흑 (선)' : '백';
  const stoneClass = color === 'BLACK' ? styles.stoneBlack : styles.stoneWhite;
  const isMe = player?.userId === myUserId;
  const isHostPlayer = player?.role === 'HOST';
  const isPlayerReady = player?.role === 'PLAYER' && player.ready;

  return (
    <div className={styles.playerCard}>
      <div className={stoneClass} />
      <div className={styles.playerInfo}>
        <p className={styles.playerName}>
          {player?.nickname ?? '대기 중'}
          {isMe && <span className={styles.badgeMe}>나</span>}
          {isHostPlayer && <span className={styles.badgeHost}>방장</span>}
        </p>
        <p className={styles.playerLabel}>{label}</p>
      </div>
      {roomStatus === 'WAITING' && player?.role === 'PLAYER' && (
        <span className={isPlayerReady ? styles.readyOn : styles.readyOff}>
          {isPlayerReady ? '준비완료' : '준비중'}
        </span>
      )}
      {currentGame?.currentTurn === color && currentGame?.status === 'IN_PROGRESS' && (
        <span className={styles.turnIndicator}>●</span>
      )}
    </div>
  );
};

export default PlayerCard;
