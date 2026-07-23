import { useEffect, useState, useMemo, useCallback } from 'react';
import { gameApi } from '@/api/game';
import { ApiResponse, Board, GameSummary, GameMove } from '@/types';
import { AxiosResponse } from 'axios';
import { createEmptyBoard } from '@/constants/board';
import OmokBoard from './OmokBoard';
import styles from './KifuViewer.module.css';

interface Props {
  game: GameSummary;
  onClose: () => void;
  loadMoves?: (gameId: number) => Promise<AxiosResponse<ApiResponse<GameMove[]>>>;
}

const fmtDate = (iso?: string | null) => {
  if (!iso) return '';
  const d = new Date(iso);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
};

const KifuViewer = ({ game, onClose, loadMoves }: Props) => {
  const [moves, setMoves] = useState<GameMove[] | null>(null);
  const [step, setStep] = useState(0);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const fetchMoves = loadMoves ?? gameApi.getGameMovesById;
    fetchMoves(game.id)
      .then((res) => {
        if (cancelled) return;
        const ms = res.data.data ?? [];
        setMoves(ms);
        setStep(ms.length); // 처음엔 최종 국면부터 보여준다
      })
      .catch(() => { if (!cancelled) setError(true); });
    return () => { cancelled = true; };
  }, [game.id, loadMoves]);

  const total = moves?.length ?? 0;

  const board: Board = useMemo(() => {
    const b = createEmptyBoard();
    if (moves) {
      for (let i = 0; i < step; i++) {
        const m = moves[i];
        if (m.row >= 0 && m.col >= 0) b[m.row][m.col] = m.color;
      }
    }
    return b;
  }, [moves, step]);

  const lastMove = step > 0 && moves ? { row: moves[step - 1].row, col: moves[step - 1].col } : null;

  const go = useCallback((next: number) => setStep((s) => {
    const v = typeof next === 'number' ? next : s;
    return Math.max(0, Math.min(total, v));
  }), [total]);

  // ←/→ 키로 한 수씩 이동
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') { e.preventDefault(); setStep((s) => Math.max(0, s - 1)); }
      else if (e.key === 'ArrowRight') { e.preventDefault(); setStep((s) => Math.min(total, s + 1)); }
      else if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [total, onClose]);

  const black = game.blackPlayer?.nickname ?? '흑';
  const white = game.whitePlayer?.nickname ?? '백';
  const result =
    game.status === 'DRAW' ? '무승부'
      : game.winner ? `${game.winner.nickname} 승`
        : game.status === 'IN_PROGRESS' ? '진행 중' : '미완료';

  return (
    <div className={styles.backdrop} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <div>
            <h2 className={styles.title}>
              기보 다시보기 <span className={styles.typeBadge}>일반 오목</span>
            </h2>
            <p className={styles.subtitle}>
              <span className={styles.stoneB} />{black}
              <span className={styles.vs}>vs</span>
              <span className={styles.stoneW} />{white}
              <span className={styles.resultChip}>{result}</span>
              <span className={styles.date}>{fmtDate(game.finishedAt ?? game.startedAt)}</span>
            </p>
          </div>
          <button className={styles.close} onClick={onClose} aria-label="닫기">✕</button>
        </div>

        {error ? (
          <p className={styles.state}>기보를 불러오지 못했습니다.</p>
        ) : moves === null ? (
          <p className={styles.state}>불러오는 중...</p>
        ) : total === 0 ? (
          <p className={styles.state}>이 대국의 기보 기록이 없습니다. (피지컬 대국 등은 기보가 저장되지 않아요)</p>
        ) : (
          <>
            <div className={styles.boardWrap}>
              <OmokBoard
                board={board}
                currentTurn={null}
                myColor={null}
                onPlaceStone={() => {}}
                lastMove={lastMove}
                disabled
              />
            </div>
            <div className={styles.controls}>
              <button className={styles.ctrlBtn} onClick={() => go(0)} disabled={step === 0} title="처음">⏮</button>
              <button className={styles.ctrlBtn} onClick={() => go(step - 1)} disabled={step === 0} title="이전 수">◀</button>
              <input
                className={styles.slider}
                type="range" min={0} max={total} value={step}
                onChange={(e) => setStep(Number(e.target.value))}
              />
              <button className={styles.ctrlBtn} onClick={() => go(step + 1)} disabled={step === total} title="다음 수">▶</button>
              <button className={styles.ctrlBtn} onClick={() => go(total)} disabled={step === total} title="마지막">⏭</button>
            </div>
            <div className={styles.counter}>
              {step} / {total} 수 <span className={styles.counterHint}>· ← → 키로 이동</span>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default KifuViewer;
