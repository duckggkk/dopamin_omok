import { useEffect, useState } from 'react';
import { gameApi } from '@/api/game';
import { GameInfo, PhysicalReplay } from '@/types';
import KifuViewer from './KifuViewer';
import PhysicalReplayViewer from './PhysicalReplayViewer';
import styles from './PhysicalReplayViewer.module.css';

/**
 * 대국 기록 뷰어 디스패처. 피지컬 리플레이가 있으면 피지컬 리플레이를, 없으면(일반 오목) 기보(KifuViewer)를 연다.
 */
const GameRecordViewer = ({ game, onClose }: { game: GameInfo; onClose: () => void }) => {
  const [replay, setReplay] = useState<PhysicalReplay | null | undefined>(undefined); // undefined=판별 중

  useEffect(() => {
    let cancelled = false;
    gameApi
      .getPhysicalReplay(game.id)
      .then((res) => { if (!cancelled) setReplay(res.data.data ?? null); })
      .catch(() => { if (!cancelled) setReplay(null); }); // 실패 시 기보로 폴백
    return () => { cancelled = true; };
  }, [game.id]);

  if (replay === undefined) {
    return (
      <div className={styles.backdrop} onClick={onClose}>
        <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
          <p className={styles.state}>기록 불러오는 중...</p>
        </div>
      </div>
    );
  }
  if (replay) return <PhysicalReplayViewer game={game} replay={replay} onClose={onClose} />;
  return <KifuViewer game={game} onClose={onClose} />;
};

export default GameRecordViewer;
