import { GameInfo, GamePlayer, RoomStatus } from '@/types';
import { stonePreviewStyle } from '@/utils/stoneSkin';
import styles from '@/pages/GamePage.module.css';

interface PlayerCardProps {
  color: 'BLACK' | 'WHITE';
  player: GamePlayer | null;
  myUserId?: string;
  roomStatus: RoomStatus;
  currentGame: GameInfo | null;
  // 이름 클릭 → 간략 프로필 모달(페이지 이동 없음)
  onShowProfile?: (userId: string) => void;
  // 대기 중 내 바둑알 클릭 → 스킨 선택 모달
  onChangeSkin?: () => void;
}

const PlayerCard = ({
  color,
  player,
  myUserId,
  roomStatus,
  currentGame,
  onShowProfile,
  onChangeSkin,
}: PlayerCardProps) => {
  const label = color === 'BLACK' ? '흑 (선)' : '백';
  const stoneClass = color === 'BLACK' ? styles.stoneBlack : styles.stoneWhite;
  const isMe = player?.userId === myUserId;
  const isHostPlayer = player?.role === 'HOST';
  const isPlayerReady = player?.role === 'PLAYER' && player.ready;
  // 대기 중이고 내 카드이며 참가자일 때만 스킨 변경 가능
  const canChangeSkin =
    roomStatus === 'WAITING' && isMe && player?.role !== 'SPECTATOR' && !!onChangeSkin;

  return (
    <div className={styles.playerCard}>
      {canChangeSkin ? (
        <button
          type="button"
          className={`${stoneClass} ${styles.stoneEditable}`}
          style={stonePreviewStyle(player?.stoneSkin)}
          onClick={onChangeSkin}
          title="바둑알 스킨 바꾸기"
          aria-label="바둑알 스킨 바꾸기"
        />
      ) : (
        <div className={stoneClass} style={stonePreviewStyle(player?.stoneSkin)} />
      )}
      <div className={styles.playerInfo}>
        <p className={styles.playerName}>
          {player ? (
            <button
              type="button"
              className={styles.playerProfileLink}
              onClick={() => onShowProfile?.(player.userId)}
            >
              {player.nickname}
            </button>
          ) : (
            '대기 중'
          )}
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
