import { useEffect, useState } from 'react';
import { gameApi } from '@/api/game';
import { ApiResponse, GameSummary, GameMove, PhysicalReplay } from '@/types';
import { AxiosResponse } from 'axios';
import KifuViewer from './KifuViewer';
import PhysicalReplayViewer from './PhysicalReplayViewer';
import PhysicalVideoReplayViewer from './PhysicalVideoReplayViewer';
import styles from './PhysicalReplayViewer.module.css';

interface Props {
  game: GameSummary;
  onClose: () => void;
  loadReplay?: (gameId: number) => Promise<AxiosResponse<ApiResponse<PhysicalReplay | null>>>;
  loadMoves?: (gameId: number) => Promise<AxiosResponse<ApiResponse<GameMove[]>>>;
}

/**
 * 대국 기록 뷰어 디스패처. 피지컬 리플레이가 있으면 피지컬 리플레이를, 없으면(일반 오목) 기보(KifuViewer)를 연다.
 */
const GameRecordViewer = ({ game, onClose, loadReplay, loadMoves }: Props) => {
  const [replay, setReplay] = useState<PhysicalReplay | null | undefined>(undefined); // undefined=판별 중

  useEffect(() => {
    let cancelled = false;
    const fetchReplay = loadReplay ?? gameApi.getPhysicalReplay;
    fetchReplay(game.id)
      .then((res) => { if (!cancelled) setReplay(res.data.data ?? null); })
      .catch(() => { if (!cancelled) setReplay(null); }); // 실패 시 기보로 폴백
    return () => { cancelled = true; };
  }, [game.id, loadReplay]);

  if (replay === undefined) {
    return (
      <div className={styles.backdrop} onClick={onClose}>
        <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
          <p className={styles.state}>기록 불러오는 중...</p>
        </div>
      </div>
    );
  }
  // 위치 트랙이 있으면 '영상 리플레이'(연속 재생), 없으면(구버전) 단계별 뷰어로 폴백.
  if (replay) {
    return replay.motionFrames && replay.motionFrames.length > 0
      ? <PhysicalVideoReplayViewer game={game} replay={replay} onClose={onClose} />
      : <PhysicalReplayViewer game={game} replay={replay} onClose={onClose} />;
  }
  return <KifuViewer game={game} onClose={onClose} loadMoves={loadMoves} />;
};

export default GameRecordViewer;
